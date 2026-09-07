# Shutter Sound Zero 기여 가이드

Shutter Sound Zero에 관심을 가져주셔서 감사합니다.

이 프로젝트는 Samsung Galaxy 기기의 Android Wireless Debugging, 로컬 ADB 통신, 시스템 설정값 변경을 다루기 때문에 일반적인 UI 앱보다 작은 변경도 실제 기기 동작에 영향을 줄 수 있습니다. 변경 사항은 가능한 한 작게 나누고, 자동 검사와 실기기 확인을 함께 거치는 것을 권장합니다.

## 기여 전에 확인해 주세요

- 일반 버그는 GitHub의 **Bug report** Issue Form을 사용해 주세요.
- 새로운 Galaxy 기기나 One UI 버전의 동작 결과는 **Device compatibility report** Issue Form을 사용해 주세요.
- 보안 취약점은 공개 Issue에 자세한 재현 정보나 민감정보를 올리지 말고 [`SECURITY.md`](SECURITY.md)의 절차를 따라 주세요.
- IMEI, 일련번호, Wi-Fi 비밀번호, 무선 디버깅 페어링 코드, RSA 개인키, keystore, 서명 비밀번호 같은 민감정보를 Issue, PR, 로그, 스크린샷에 포함하지 마세요.

## 개발 환경

기본 개발 환경은 다음을 기준으로 합니다.

- JDK 17
- Android Studio 또는 동등한 Android 개발 환경
- Android SDK 37 / Android 17 빌드 환경
- Gradle Wrapper 사용
- Samsung Galaxy 실기기 권장

프로젝트에 포함된 Gradle Wrapper를 사용해 빌드해 주세요. 시스템에 별도 설치된 Gradle 버전에 의존하지 않는 것을 권장합니다.

## 브랜치 전략

`main` 브랜치에 직접 작업하지 말고 목적이 분명한 짧은 브랜치를 만들어 주세요.

권장 예시:

```text
feature/pairing-improvement
fix/android17-local-network
fix/reboot-restore
refactor/adb-manager
chore/update-compose
docs/update-compatibility
ci/android-validation
```

하나의 브랜치와 PR에는 가능한 한 하나의 목적만 담아 주세요. 큰 기능 변경과 대규모 의존성 업그레이드는 별도 PR로 분리하는 편이 좋습니다.

## 커밋 메시지

커밋과 PR 제목은 **Conventional Commit 접두어는 영어로 유지하고, 접두어 뒤 제목과 설명은 한국어로 작성**합니다.

기본 형식:

```text
<유형>: <한국어 제목>

<필요한 경우 한국어 본문 설명>
```

사용 가능한 유형과 의미:

```text
feat: 새로운 기능
fix: 버그 수정
refactor: 동작 변경 없는 코드 구조 개선
test: 테스트 추가 또는 수정
docs: 문서 변경
chore: 유지보수 작업
build: 빌드 시스템 또는 의존성 변경
ci: GitHub Actions 등 CI 변경
security: 보안 강화 또는 보안 관련 수정
release: 릴리즈 준비
```

좋은 예시:

```text
feat: 앱 잠금 기능 추가
fix: 페어링 성공 후 잘못된 오류 표시 방지
refactor: ADB 연결 상태 처리 단순화
docs: 소프트웨어 업데이트 후 권한 안내 추가
ci: 정식 릴리즈를 main 기준으로 실행
security: 명시적 권한 해제 후 자동 복원 차단
release: v1.4.1 릴리즈 준비
```

사용하지 않는 예시:

```text
feat: add app lock
fix: avoid false pairing error after success
Update README
버그 수정
```

### 작성 원칙

- `feat:`, `fix:` 같은 **유형 접두어만 영어로 사용**합니다.
- 접두어 뒤의 **제목은 한국어를 기본 언어로 사용**합니다.
- 본문이 필요하면 변경 이유, 영향, 주의사항을 **한국어로 작성**합니다.
- 클래스명, API명, 파일명, 명령어, Android·GitHub 고유 용어처럼 번역하면 오히려 의미가 불분명해지는 기술 용어는 영어 표기를 그대로 사용할 수 있습니다.
- 제목은 변경한 파일 목록보다 **무엇을 왜 변경했는지**가 드러나도록 간결하게 작성합니다.
- 여러 변경을 한 커밋에 섞기보다 목적별로 나누는 것을 권장합니다.
- Squash merge를 사용하므로 **PR 제목도 동일한 규칙을 따릅니다.** 최종 `main` 커밋 제목은 한국어로 남아야 합니다.
- Dependabot 등 자동 생성 PR도 병합 전 PR 제목을 가능한 한 이 규칙에 맞게 한국어로 정리합니다.

과거 영어 커밋 메시지는 릴리즈 태그와 서명 이력의 연속성을 보존하기 위해 수정하지 않습니다. 이 규칙은 새로 작성되는 커밋과 PR부터 적용합니다.

## 로컬 검증

PR을 만들기 전에 최소한 아래 명령이 성공하는지 확인해 주세요.

### Debug APK 빌드

```bash
./gradlew assembleDebug
```

Windows PowerShell 또는 명령 프롬프트에서는:

```powershell
.\gradlew.bat assembleDebug
```

### 단위 테스트

```bash
./gradlew testDebugUnitTest
```

Windows:

```powershell
.\gradlew.bat testDebugUnitTest
```

### Android Lint

```bash
./gradlew lintDebug
```

Windows:

```powershell
.\gradlew.bat lintDebug
```

가능하면 세 검사를 모두 실행한 뒤 PR을 생성해 주세요.

## 테스트 추가 원칙

버그를 수정하거나 핵심 로직을 변경할 때는 재현 가능한 경우 단위 테스트를 함께 추가해 주세요.

특히 다음 영역은 회귀 테스트의 우선순위가 높습니다.

- ADB 명령 및 셸 출력 파싱
- CSC 설정값 판정과 명령 생성
- Wireless Debugging 페어링 상태 처리
- 로컬 ADB 주소 및 IPv4/IPv6 판별
- 재부팅 또는 앱 업데이트 후 설정 복원 조건
- 입력값 및 오류 경계조건

Android 프레임워크와 분리 가능한 로직은 가능한 한 순수 Kotlin 형태로 유지하면 빠르고 안정적인 단위 테스트를 만들기 쉽습니다.

## 실기기 테스트

일반 에뮬레이터만으로는 Samsung CSC 동작, Wireless Debugging 페어링, Quick Settings 타일, 재부팅 후 복원 등을 완전히 검증할 수 없습니다.

관련 코드를 변경했다면 가능한 범위에서 Samsung Galaxy 실기기로 다음 항목을 확인해 주세요.

- 무선 디버깅 페어링 성공 여부
- 셔터음 설정 적용 및 해제
- 벨소리 / 진동 / 무음 모드 연계
- Quick Settings 타일 동작
- 기기 재부팅 후 설정 복원
- 앱 업데이트 후 기존 설정 유지 또는 복원

실기기 테스트 결과를 공유할 때는 다음 정보를 적어 주세요.

- Galaxy 기기명
- 모델 번호
- Android 버전
- One UI 버전
- Android 보안 패치 수준
- Shutter Sound Zero 버전
- 테스트한 기능과 결과

호환성 결과는 [`docs/COMPATIBILITY.md`](docs/COMPATIBILITY.md)에 반영될 수 있습니다.

## Pull Request 절차

1. 최신 `main`에서 작업 브랜치를 만듭니다.
2. 필요한 코드와 테스트를 변경합니다.
3. 로컬에서 Build, Unit Test, Lint를 확인합니다.
4. 변경 목적과 테스트 결과를 PR 템플릿에 작성합니다.
5. PR 제목을 `<유형>: <한국어 제목>` 형식으로 작성합니다.
6. PR을 생성합니다.
7. GitHub Actions의 필수 검사를 모두 통과시킵니다.
8. 검토가 끝나면 Squash merge를 권장합니다.
9. 병합된 작업 브랜치는 더 이상 필요하지 않으면 삭제합니다.

현재 `main` 병합 전 필수 자동 검사는 다음과 같습니다.

- **Build, Test & Lint**
  - Debug APK 빌드
  - Unit Test
  - Android Lint
  - Debug APK Artifact 업로드
- **CodeQL Java/Kotlin**
  - Java/Kotlin 보안 정적 분석

필수 체크가 실패했다면 우회해서 병합하기보다 원인을 먼저 해결해 주세요.

## 의존성 업데이트

Gradle 및 GitHub Actions 의존성은 Dependabot이 주기적으로 업데이트 PR을 생성합니다.

- minor / patch 업데이트는 가능한 경우 묶어서 검토합니다.
- major 업데이트는 개별적으로 검토하는 것을 원칙으로 합니다.
- AGP, Kotlin, Compose, AndroidX, libadb, BouncyCastle, Conscrypt 등 핵심 의존성은 자동 병합하지 않습니다.
- 의존성 업데이트 후에도 Build, Unit Test, Lint, CodeQL과 필요한 실기기 테스트를 확인해 주세요.

## 보안 관련 변경

다음 영역을 변경하는 PR은 특히 신중하게 검토해 주세요.

- Wireless Debugging / ADB 페어링 흐름
- RSA 키 생성, 저장, 사용
- 로컬 네트워크 접근
- Android exported component
- 시스템 설정 변경 권한과 명령
- 부팅 및 앱 업데이트 Receiver
- release signing 구성
- GitHub Actions Secrets 처리

보안 취약점 신고 절차는 [`SECURITY.md`](SECURITY.md)를 따릅니다.

## 릴리스

공식 릴리스는 프로젝트 유지관리자가 진행합니다.

현재 릴리스 흐름은 다음과 같습니다.

1. `versionName`과 `versionCode` 변경
2. PR 생성 및 필수 CI 통과
3. `main` 병합
4. 병합된 `main` 커밋에 `vX.Y.Z` 태그 생성
5. `Android Release` workflow에서 서명된 APK 빌드 및 서명 검증
6. GitHub Release 자동 생성

자세한 내용은 [`docs/RELEASE_AUTOMATION.md`](docs/RELEASE_AUTOMATION.md)를 참고해 주세요.

Release keystore, keystore 비밀번호, key alias 비밀번호, GitHub Actions Secret 값은 저장소나 PR에 절대 커밋하지 마세요.

## 라이선스

기여한 코드는 프로젝트와 동일하게 GNU General Public License v3.0 조건에 따라 배포됩니다. PR을 제출하면 해당 라이선스 조건으로 기여 내용을 배포하는 데 동의하는 것으로 간주합니다.

감사합니다.