# 8차 감사 보완: ADB 작업과 mDNS 자원 수명

기준 커밋: `817171d8e7906c2d84ca6909285f9748cd3735d4`.

## 변경 사항

- 네 공개 ADB 작업은 `AdbOperationRunner`를 공유합니다. 권한 확인·멀티캐스트 lease 획득부터 `Result` 경계 안에서 처리하고, 취소는 다시 전파합니다. 연결 정리·lease 해제·작업 gate 해제는 성공, 실패, 조기 종료, 취소 모두에 적용됩니다.
- 페어링·연결·셸 작업은 네트워크 연결 이전부터 소켓을 소유합니다. 취소/전체 제한시간은 해당 raw socket을 닫고 작업 스레드를 interrupt합니다. TLS와 PAKE 상태의 최종 정리는 작업 스레드만 수행하여 부분 초기화 및 native destroy 경쟁을 피합니다.
- 연결 8초, 페어링 12초, 셸 전체 6초의 제한시간에 TCP 생성·TLS·쓰기·읽기가 포함됩니다. 정리 대기는 추가로 최대 1초입니다. 여전히 반환하지 않는 플랫폼/native 작업은 전역 단일 실행 슬롯을 유지하므로 반복 재시도로 스레드가 누적되지 않습니다. 이 경우 다음 요청은 정리 중 오류를 반환합니다.
- 연결 시도마다 독립 세션을 만들며, 실패한 세션을 닫은 뒤 다음 포트를 시도합니다. 각 공개 작업 종료 시 성공한 연결도 닫습니다. 권한/CSC 상태 확인과 확인 후 사용자 의도 저장은 유지합니다.
- 같은 `NsdManager`를 쓰는 탐색은 공통 주소 확인 대기열을 사용합니다. `FAILURE_ALREADY_ACTIVE`는 최대 3회 시도하며, 중복 발견·서비스 소실·취소·지연 콜백을 세션/시도 식별자로 분리합니다. 대기열은 최대 64개입니다.

## 플랫폼 한계

API 34 이상에서는 `stopServiceResolution()`으로 진행 중 주소 확인 중단을 요청합니다. API 30–33에서는 동일 API가 없으므로 결과 전달과 후속 재시도를 취소하고, 기존 플랫폼 요청의 종료 콜백까지 슬롯을 보유합니다. 플랫폼이 종료 콜백을 영구히 누락하면 새 주소 확인도 진행되지 않습니다. 5초 후 해당 요청의 실패는 전달하지만, 실제 요청이 종료되지 않았는데 종료된 것처럼 처리하지 않습니다.

취소 전에 adbd가 이미 처리한 페어링 또는 셸 명령은 되돌릴 수 없습니다. 이 변경은 취소 뒤 살아남은 클라이언트가 추가 I/O를 수행하는 것을 막으며, 원격 부작용의 트랜잭션 롤백을 주장하지 않습니다.

## libadb 결합 및 출처

암호화, ADB 패킷 코덱, TLS 컨텍스트와 RSA 인코딩은 기존 고정 의존성 `libadb-android 3.1.1`을 재사용합니다. 새로운 `io.github.muntashirakon.adb.Cancellable*` 클래스는 해당 라이브러리의 package-private API를 사용하므로 의존성을 올릴 때 함께 검증해야 합니다. 이들은 upstream 클래스의 중복 정의가 아닙니다.

연결/페어링 흐름은 Muntashir Al-Islam의 다음 upstream 구현을 참고하여 전송 자원 수명을 변경했습니다. 이 저장소의 GPL-3.0-or-later 조건으로 배포하며, 암호 알고리즘 자체를 새로 구현하지 않았습니다.

- https://github.com/MuntashirAkon/libadb-android/blob/3.1.1/libadb/src/main/java/io/github/muntashirakon/adb/AdbConnection.java
- https://github.com/MuntashirAkon/libadb-android/blob/3.1.1/libadb/src/main/java/io/github/muntashirakon/adb/PairingConnectionCtx.java
- https://developer.android.com/reference/android/net/nsd/NsdManager#stopServiceResolution(android.net.nsd.NsdManager.ResolveListener)

`CancellableAdbConnection`은 앱의 기존 직렬 작업 모델에 맞춘 단일 활성 셸 스트림 전송 계층입니다. 일반적인 다중 스트림 ADB 클라이언트가 아니며, 상시 reader thread를 만들지 않습니다. 연속 명령의 이전 스트림 종료 패킷을 분리하고, 패킷/출력 크기 제한 및 완료 marker 확인을 유지합니다.

## 회귀 검사

정식 실행 명령: `./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug`.

이 변경의 테스트는 lease 획득 실패, 코루틴 취소/동시 요청, 늦게 종료되는 worker, TCP 연결 중 취소, 실제 loopback 읽기/TLS handshake 중단, 실패 ADB 연결의 EOF, 연속 셸 명령, OPEN 제한시간, 잘못된 패킷 크기, mDNS 직렬화·재시도·세션 중단을 포함합니다.

일부 로컬 검사는 JDK 소켓/JSSE와 경량 assertion 실행기로 수행했습니다. Android SDK 빌드 및 실제 의존성 연계 검사는 GitHub Actions 결과로 구분하며, 실행하지 않은 검사에 통과를 표시하지 않습니다. 실제 Android Conscrypt + SPAKE2 페어링, 삼성 카메라의 최종 동작, 제조사별 NSD 종료 콜백은 실기기 확인 범위입니다.


## 추가 보안 점검: CodeQL 경고는 아직 미해결

첫 수정 커밋 `199bbf606fe33aac1e4fcb78861769eb3005746f`에서 Android CI 전체와 CodeQL 분석 작업 자체는 성공했지만, 별도 CodeQL 최종 보안 판정은 `java/unsafe-cert-trust` 2건(critical)으로 실패했습니다. 분석 실행 성공을 취약점 없음으로 해석하면 안 됩니다.

경고 위치는 연결/페어링 TLS 스트림입니다. ADB는 웹 HTTPS와 달리 임의로 생성되는 인증서를 사용하며, 페어링에서는 TLS exporter에 묶인 공유 코드 기반 PAKE로 신뢰를 설정합니다. 서버 인증서에 `HTTPS` 호스트명 검증을 단순히 켜면 기존 ADB 인증서와 호환되지 않을 수 있습니다. 그렇다고 이를 자동으로 오탐으로 처리하거나 검사를 끄지는 않았습니다.

방어를 강화하여 전송 계층에서 숫자 리터럴 `127.0.0.1` 또는 `::1`만 허용합니다. DNS 이름, 외부/사설 네트워크 주소, 임의 TLS 호스트 재지정은 연결 전에 거부합니다. 앱은 mDNS 주소의 기기 소속을 검증한 뒤 포트만 취하고, 실제 페어링/연결은 `127.0.0.1`로 수행합니다. 이 제한으로 외부 네트워크 연결 및 주소 변경에 따른 오연결을 막지만 **서버 인증서 검증 자체를 구현한 것은 아닙니다.** 같은 기기 안의 악성 프로세스를 인증서로 식별하는 보장도 추가하지 않습니다. 루프백을 제공하지 않는 제조사 구현에서는 외부 주소로 우회하지 않고 연결에 실패합니다.

이 PR은 해당 경고의 프로토콜별 위협 모델 검토와 실기기 호환성 확인이 끝나기 전까지 Draft로 유지하며 자동 병합하지 않습니다. CodeQL 규칙, 워크플로, 경고 상태를 숨기거나 비활성화하지 않았습니다.

보강된 집중 회귀 시나리오는 총 36개입니다. 기존 33개에 외부 주소/DNS 이름 차단, 잘못된 포트 차단, TLS 연결 대상 불일치 차단 검사를 추가했습니다.

참고:
- https://codeql.github.com/codeql-query-help/java/java-unsafe-cert-trust/
- https://android.googlesource.com/platform/packages/modules/adb/+/refs/heads/main/pairing_connection/pairing_connection.cpp
- https://android.googlesource.com/platform/packages/modules/adb/+/refs/heads/main/daemon/auth.cpp
