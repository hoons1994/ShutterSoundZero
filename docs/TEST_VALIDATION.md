# 테스트 환경과 CI 결과 검증

## 데이터 변경 테스트

전체 계측 테스트는 `DisposableAndroidJUnitRunner`를 사용한다. 테스트 생성·Activity 규칙·
`@Before` 이전에 ranchu/goldfish 에뮬레이터, 기존 잠금 자격 증명 없음, 명시적인
`disposableTestToken`을 확인한다. 최초 실행은 files/no_backup/shared_prefs/databases에
기존 데이터가 없어야 한다. 거절 시 테스트나 정리를 실행하지 않는다.

토큰은 폐기용 AVD마다 새로 생성한 32자리 소문자 hex이다. 같은 토큰은 명시적으로 같은
폐기용 세션을 다시 실행할 때만 사용한다. 기존 환경을 강제로 테스트 환경으로 만들지 않는다.
일반 기기에서 데이터를 지워 통과시키는 방식은 금지한다. CI는 새 AVD에서 기존 인증 파일을
합성한 뒤 토큰 누락/기존 데이터 거절과 원본 보존을 검사하고, 자신이 설치한 패키지만 제거한
후 전체 테스트를 새로 설치한다. 일반 기기 거절 정책은 JVM 테스트로 검증한다.

## 계측 결과

`instrumented-test-policy.json`은 클래스별 테스트 메서드와 API 범위를 관리한다.
새 테스트를 추가·제거하면 목록도 검토해 갱신한다. 소스와 목록이 다르면 검증기 테스트가 실패한다.
API 37 로컬 네트워크 테스트, API 30~33 NSD 테스트는 범위 밖에서만 누락/건너뜀을 허용한다.
그 외 필수 테스트의 누락·건너뜀은 실패다. 결과 파일 없음, 0건 실행, 손상 XML, 실패/오류,
중복 결과, XML 집계 불일치도 실패다. 합계 숫자만 맞추어 통과할 수 없다.
API 30~37 모두 전체 계측 테스트를 한 번 실행하며, API 35 전용 추가 반복은 하지 않는다.

```sh
python3 -m unittest discover -s .github/scripts -p 'test_*.py' -v
python3 .github/scripts/verify-instrumented-results.py --results app/build/outputs/androidTest-results/connected --api 35
```

JVM XML/HTML, Lint XML/HTML은 작업 성공 여부와 관계없이 업로드한다. Gradle 성공 후 보고서
누락은 실패이며, Gradle이 먼저 실패해 보고서가 없으면 경고로 구분하고 원래 실패를 보존한다.
계측 보고서와 에뮬레이터/거절 검사 로그는 서로 다른 아티팩트로 보관한다.

## 출시 전 실제 실행

```sh
bash .github/scripts/validate-release-tests.sh
```

Windows에서는 동일한 명령을 사용한다:

```powershell
.\gradlew.bat testDebugUnitTest testReleaseUnitTest lintDebug lintRelease '-PreleaseValidation=true' '-Pandroid.onlyEnableUnitTestForTheTestedBuildType=false' --no-daemon --stacktrace --console=plain
```

AGP 기본값은 Debug 단위 테스트만 생성하므로 출시 전 명령에서만
`android.onlyEnableUnitTestForTheTestedBuildType=false`로 Release 테스트도 생성한다.

`releaseValidation=true`는 JVM Test 작업의 up-to-date 판정을 false로 설정해 이전 결과와
빌드 캐시 결과를 재사용하지 않는다. 의존성 다운로드와 컴파일 캐시는 유지하며 일반 PR CI의
캐시 정책은 바꾸지 않는다. 실제 실행/`FROM-CACHE`/`UP-TO-DATE`는 Gradle 로그로 구분한다.
Lint 캐시 재사용은 허용한다. 이 경로는 서명·APK 패키징·배포 작업을 호출하지 않는다.
기존 출시 workflow의 서명 키 복원 전에 이 검증을 실행하도록 연결했다.

현재 release는 minify=false이며 별도 release 소스가 없고, debug에는 UI tooling/test manifest와
versionNameSuffix가 추가된다. Release 단위 테스트·Lint는 variant별 classpath/manifest 구성이
계속 유효한지 확인한다. 일반 PR에 같은 전체 검증을 중복 추가하지 않고 출시 전 경로에 둔다.
비용은 두 variant의 JVM 테스트 실행과 release 테스트 컴파일/Lint이며, 기존 컴파일 캐시는 유지한다.
이는 R8 최적화나 실제 서명 APK 실행을 검증하는 것은 아니다.

설정 근거: [Gradle 출력 재사용 제어](https://docs.gradle.org/current/kotlin-dsl/gradle/org.gradle.api.tasks/-task-outputs/up-to-date-when.html),
[AGP 단위 테스트 기본값 변경](https://developer.android.com/build/releases/agp-9-0-0-release-notes).

Galaxy 실제 셔터음, 무선 페어링, 실기기 UI 확인은 별도 수동 검증이다. 데이터 변경 계측
테스트를 실기기에서 실행하지 않는다. 이 CI 보강은 앱 버전·의존성·기능을 변경하지 않는다.
