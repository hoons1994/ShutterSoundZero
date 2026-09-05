# Release 자동화 설정

Shutter Sound Zero는 `vX.Y.Z` 형식의 Git 태그가 `main` 브랜치의 커밋에 생성되면 GitHub Actions에서 서명된 Release APK를 자동으로 빌드하고 GitHub Release에 업로드하도록 구성합니다.

## 동작 흐름

1. `app/build.gradle.kts`의 `versionName`과 `versionCode`를 새 버전에 맞게 변경합니다.
2. 변경 사항을 PR로 병합하고 Android CI가 통과했는지 확인합니다.
3. 병합된 `main` 커밋에 `vX.Y.Z` 형식의 태그를 생성합니다.
4. `Android Release` workflow가 태그와 `versionName`의 일치 여부를 확인합니다.
5. Release keystore를 GitHub Actions Secret에서 제한된 파일 권한으로 복원합니다.
6. `assembleRelease`로 서명된 APK를 빌드한 뒤 임시 keystore 파일을 삭제합니다.
7. `apksigner`로 APK 자체의 서명 유효성을 검증합니다.
8. 직전 안정 GitHub Release의 APK를 내려받아 서명 인증서 SHA-256 지문이 동일한지 확인합니다.
9. APK provenance attestation을 생성하고 다시 검증합니다.
10. APK, SHA-256 체크섬, Sigstore attestation bundle을 GitHub Release에 업로드합니다.

태그가 `main`에 포함되지 않은 커밋을 가리키거나, 태그 버전과 앱의 `versionName`이 다르거나, 새 APK의 서명 인증서가 직전 공식 안정 릴리스와 다르면 릴리즈는 중단됩니다.

수동 Signed Release Preflight도 `main`에서만 실행되며 같은 서명 인증서 연속성 검사를 수행합니다. 따라서 잘못된 keystore가 등록되었거나 서명 키가 의도치 않게 바뀐 경우 실제 GitHub Release를 만들기 전에 탐지할 수 있습니다.

---

## 최초 1회: GitHub Actions Secrets 등록

저장소의 다음 메뉴에서 Repository Secret을 등록합니다.

`Settings` → `Secrets and variables` → `Actions` → `New repository secret`

필요한 Secret은 아래 4개입니다.

| Secret | 내용 |
|---|---|
| `RELEASE_KEYSTORE_BASE64` | `release.jks` 파일을 Base64로 인코딩한 전체 문자열 |
| `RELEASE_STORE_PASSWORD` | keystore 비밀번호 |
| `RELEASE_KEY_ALIAS` | 서명 키 alias |
| `RELEASE_KEY_PASSWORD` | 서명 키 비밀번호 |

> `release.jks` 파일이나 비밀번호를 저장소에 커밋하지 마세요. Secret 값도 Issue, PR, 문서 또는 채팅에 공유하지 않는 것을 권장합니다.

### Windows PowerShell에서 keystore를 Base64로 변환

`release.jks`가 있는 폴더에서 다음 명령을 실행합니다.

```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes("release.jks")) | Set-Content -NoNewline "release.jks.base64"
```

생성된 `release.jks.base64` 파일의 전체 내용을 `RELEASE_KEYSTORE_BASE64` Secret에 등록합니다.

등록이 끝나면 로컬의 `release.jks.base64` 파일은 삭제해도 됩니다.

---

## 새 버전 릴리즈 예시

예를 들어 `1.3.0`을 릴리즈할 경우:

### 1. 앱 버전 변경

`app/build.gradle.kts`:

```kotlin
versionCode = 130
versionName = "1.3.0"
```

### 2. PR 생성 및 병합

일반 개발과 동일하게 브랜치에서 버전을 변경하고 PR을 만든 뒤 `Build, Test & Lint`가 통과한 후 `main`에 병합합니다.

### 3. 태그 생성

병합된 최신 `main`에서:

```bash
git checkout main
git pull
git tag v1.3.0
git push origin v1.3.0
```

태그가 push되면 `Android Release`가 자동 실행됩니다.

### 4. 생성 결과

성공하면 GitHub Release에 다음 파일이 생성됩니다.

```text
ShutterSoundZero-v1.3.0.apk
ShutterSoundZero-v1.3.0.apk.sha256
ShutterSoundZero-v1.3.0.apk.sigstore.json
```

Release notes는 GitHub가 자동 생성합니다.

---

## 서명 인증서 연속성 검사

`verify-release-signer.sh`는 GitHub API에서 가장 최근의 draft/prerelease가 아닌 안정 릴리스를 찾고 해당 릴리스의 APK를 내려받습니다. 새로 빌드한 APK와 직전 공식 APK 각각에서 `apksigner --print-certs`로 첫 번째 signer의 SHA-256 인증서 지문을 추출한 뒤 두 값을 비교합니다.

현재 릴리스 태그를 다시 실행하는 경우에는 자기 자신을 비교 대상으로 사용하지 않고 그 이전 안정 릴리스를 선택합니다. 직전 릴리스에서 APK를 찾을 수 없거나 APK가 둘 이상이어서 기준 파일이 모호하거나 인증서 지문을 추출할 수 없거나 지문이 다르면 fail-closed 방식으로 릴리스를 중단합니다.

이 검사는 keystore 비밀번호나 개인키를 외부에 공개하지 않습니다. 비교되는 인증서 SHA-256 지문은 APK에 포함된 공개 인증서에서 계산되는 값입니다.

---

## 실패 시 확인할 항목

- GitHub Actions Secret 4개가 모두 등록되어 있는지
- `release.jks`가 기존 배포 APK와 동일한 서명 키인지
- 직전 안정 GitHub Release에 공식 APK가 정확히 1개 존재하는지
- Git 태그 `vX.Y.Z`와 `versionName`이 일치하는지
- 태그가 `main`에 포함된 커밋을 가리키는지
- Android SDK 또는 Gradle 빌드가 정상인지

기존에 배포한 앱을 업데이트하려면 반드시 기존 APK와 동일한 signing key를 사용해야 합니다. 이 저장소의 Release workflow는 이제 그 연속성을 자동으로 검증합니다.
