# Release 자동화 설정

Shutter Sound Zero는 `vX.Y.Z` 형식의 Git 태그가 `main` 브랜치의 커밋에 생성되면 GitHub Actions에서 서명된 Release APK를 자동으로 빌드하고 GitHub Release에 업로드하도록 구성합니다.

## 동작 흐름

1. `app/build.gradle.kts`의 `versionName`과 `versionCode`를 새 버전에 맞게 변경합니다.
2. 변경 사항을 PR로 병합하고 Android CI가 통과했는지 확인합니다.
3. 병합된 `main` 커밋에 `vX.Y.Z` 형식의 태그를 생성합니다.
4. `Android Release` workflow가 태그와 `versionName`의 일치 여부를 확인합니다.
5. Release keystore를 GitHub Actions Secret에서 복원합니다.
6. `assembleRelease`로 서명된 APK를 빌드합니다.
7. `apksigner`로 APK 서명을 검증합니다.
8. APK와 SHA-256 체크섬을 GitHub Release에 업로드합니다.

태그가 `main`에 포함되지 않은 커밋을 가리키거나, 태그 버전과 앱의 `versionName`이 다르면 릴리즈는 중단됩니다.

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
```

Release notes는 GitHub가 자동 생성합니다.

---

## 실패 시 확인할 항목

- GitHub Actions Secret 4개가 모두 등록되어 있는지
- `release.jks`가 기존 배포 APK와 동일한 서명 키인지
- Git 태그 `vX.Y.Z`와 `versionName`이 일치하는지
- 태그가 `main`에 포함된 커밋을 가리키는지
- Android SDK 또는 Gradle 빌드가 정상인지

기존에 배포한 앱을 업데이트하려면 반드시 기존 APK와 동일한 signing key를 사용해야 합니다.
