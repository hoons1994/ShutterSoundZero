# 8차 감사 보완: 자원 수명과 로컬 ADB 상대 인증

최초 감사 기준: `817171d8e7906c2d84ca6909285f9748cd3735d4`. PR #151.

## 네 감사 항목

- `AdbOperationRunner`: 권한 확인과 멀티캐스트 lease 획득부터 Result 경계에 포함합니다. 코루틴 취소를 보존하고 연결·lease·작업 gate를 성공/실패/취소에 모두 정리합니다.
- `CancellableSocket`과 `BoundedBlockingOperation`: 연결 전에 raw socket을 소유합니다. 취소/시간 초과는 소켓을 닫으며 TLS/PAKE 최종 정리는 worker가 수행합니다. 종료되지 않은 worker는 단일 실행 슬롯을 유지하여 재시도 누적을 막습니다.
- 연결은 시도별로 소유하며 실패한 세션을 닫은 다음 재시도합니다. 연결 8초, 페어링 12초, 셸 전체 6초와 최대 1초의 정리 대기를 사용합니다. 각 공개 작업이 끝나면 성공한 연결도 닫습니다.
- 같은 NSD 클라이언트의 주소 확인은 공유 대기열에서 직렬화합니다. 최대 3회 충돌 재시도, 중복 제거, 취소/서비스 소실/지연 콜백 분리를 적용합니다.

## CodeQL 경고의 실제 의미와 보완

이전 커밋의 `java/unsafe-cert-trust` 2건은 `SSLSocket`에 명시적인 endpoint-identification 알고리즘이 없다는 경고입니다. CodeQL 분석 실행 성공은 최종 보안 검사 통과와 다릅니다. 경고의 critical 등급만으로 실제 악용 경로 2개가 확정됐다고 해석하지 않습니다.

ADB는 웹 HTTPS가 아닙니다. AOSP 페어링은 자체 서명 인증서 위에서 **입력 코드 + TLS exporter에 묶인 SPAKE2/인증 암호문**으로 상대를 인증합니다. 또한 framework 페어링 서버의 키와 adbd의 임시 TLS 키는 별개이므로, 페어링 인증서를 그대로 일반 연결에 pinning하면 안 됩니다. 임의 `HTTPS` 플래그를 켠 뒤 trust-all을 유지하는 변경도 하지 않습니다.

기존 `SslUtils.getSslContext()`의 전역 trust-all 컨텍스트 사용을 제거하고 `LocalAdbTls`의 두 정책을 적용합니다.

1. `SSZ-ADB-LOCAL-PAKE-v1`: TLS 1.3, literal loopback, 소켓에 맞는 정책, 단일 유효한 RSA >= 2048 자체 서명 인증서와 SHA-256 이상 서명을 확인합니다. **이 단계는 잠정 전송 승인이지 신원 인증 완료가 아닙니다.** 최종 성공은 TLS exporter에 결합된 PAKE와 인증된 type-1 device GUID 검증 후에만 반환합니다.
2. `SSZ-ADB-LOCAL-SHELL-UID-v1`: 동일한 TLS 전처리에 더해, 현재 연결에서 생성한 256-bit 일회용 challenge를 `/system/bin/content call`로 제출하게 합니다. `AdbIdentityProvider`는 `android.permission.DUMP`로 보호되고, 실제 `Binder.getCallingUid()`가 shell(2000) 또는 root(0)인지도 별도로 검사합니다. 토큰·stdout·Bundle에 적힌 UID는 신뢰하지 않습니다. 증명이 끝나기 전 일반 셸 명령을 열 수 없으며 실패하면 연결을 닫습니다. TLS를 건너뛰는 평문 CNXN/legacy AUTH도 거부합니다.

두 명칭은 `X509ExtendedTrustManager`와 연결 상태 기계가 실제로 구현하는 **비-HTTPS 상대 인증 정책**입니다. 비어 있지 않은 문자열만 추가해 검사를 우회하는 것이 아닙니다. 자체 서명 확인만으로 상대가 adbd임을 보장한다거나, 표준 CA/호스트명 검증·인증서 pinning·TOFU를 도입했다고 주장하지 않습니다. 상대에게 TLS 인증서만 제시하게 하고 일반 명령을 허용하는 경로는 없습니다.

### 위협 모델

외부/DNS 주소는 전송 계층에서 거부합니다. 같은 기기의 일반 앱이 TLS 서버와 ADB 성공 응답을 흉내 내고 probe 토큰을 알아도 shell/root Binder 호출을 만들 수 없으므로 일반 명령 단계로 진행하지 못합니다. 만료·취소·재사용 토큰과 일반 앱 UID, DUMP 권한만 가진 UID는 거부합니다. 검증 provider는 데이터를 반환하거나 설정/권한/명령을 변경하지 않습니다.

이미 shell/root 권한이 있는 공격자, 손상된 Android 커널/시스템, 앱 프로세스 자체의 코드 실행 권한을 얻은 공격자를 방어하는 모델은 아닙니다. 루프백, 자체 서명 RSA/TLS 1.3 또는 shell의 content 명령이 차단된 제조사 환경에서는 보안을 낮춰 우회하지 않고 오류로 종료합니다. 사용자별 provider를 찾도록 `--user`에 앱의 Android 사용자 ID를 지정합니다.

## 회귀 검사

기존/확장 집중 단위 시나리오 49개와 Android 통합 시나리오 7개를 둡니다. 전체 저장소 테스트 수는 아닙니다.

- JVM: 자원 경계, 취소/worker 누적, mDNS, 실제 TCP/TLS 취소, TLS 1.3 성공과 잘못된 인증서 거부, 토큰 수명/권한, TLS ADB 스트림, 가짜 성공 출력 및 평문 downgrade 거부.
- Android: 실제 shell Binder 성공, DUMP 권한을 얻은 앱 UID의 위조 거부, 재생 거부, 실제 Conscrypt + libadb SPAKE2 페어링 성공, 잘못된 코드·TLS exporter·인증된 peer type 거부.
- AndroidJUnitRunner를 명시하여 계측 테스트 실행 대상을 분명히 합니다.

정식 명령: `./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:connectedDebugAndroidTest`.

검증 결과는 PR의 **해당 커밋 CI 기록**과 별도의 로컬 실행 로그로 확인합니다. 로컬 JDK/경량 assertion 실행을 실제 Android 실행으로 대신 표기하지 않습니다. CodeQL 규칙/워크플로/경고 상태는 숨기거나 비활성화하지 않습니다.

## 남는 플랫폼 범위

API 34 이상은 `stopServiceResolution()`을 사용합니다. API 30–33에서는 플랫폼 종료 콜백까지 슬롯을 보유합니다. 플랫폼이 콜백을 영구 누락하면 새 resolve도 진행되지 않으며 이를 종료된 것처럼 처리하지 않습니다. 이미 adbd가 실행한 명령/페어링의 부작용은 취소로 되돌릴 수 없습니다.

단위/에뮬레이터 검사는 모든 삼성/제조사 실기기와 카메라 소리의 검증을 대신하지 않습니다. libadb 3.1.1의 package-private 패킷/PAKE API를 재사용하므로 라이브러리 갱신 시 함께 검증해야 합니다. 암호 알고리즘을 새로 구현하지 않았습니다. UI, 이메일 제보 및 버전 번호는 변경하지 않습니다.

## 출처

- https://codeql.github.com/codeql-query-help/java/java-unsafe-cert-trust/
- https://android.googlesource.com/platform/packages/modules/adb/+/refs/heads/main/pairing_connection/pairing_connection.cpp
- https://android.googlesource.com/platform/frameworks/base/+/master/services/core/jni/com_android_server_adb_AdbDebuggingManager.cpp
- https://android.googlesource.com/platform/packages/modules/adb/+/refs/heads/main/daemon/auth.cpp
- https://developer.android.com/reference/android/os/Binder#getCallingUid()
- https://github.com/MuntashirAkon/libadb-android/tree/3.1.1
