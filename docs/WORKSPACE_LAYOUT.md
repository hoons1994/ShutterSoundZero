# 작업 폴더 안내

프로젝트 루트는 Android 소스와 개발 도구의 기본 구조를 유지합니다. 로컬 APK와 검증 자료는 `build` 아래에서 작업별로 찾습니다.

| 경로 | 용도 |
| --- | --- |
| `app/src/main` | 앱 코드·리소스·네이티브 소스 |
| `app/src/test`, `app/src/androidTest` | JVM·Android 계측 테스트 |
| `.github`, `gradle`, `scripts` | CI·빌드 도구·정식 검증 스크립트 |
| `docs` | 개발·검증 문서 |
| `app/build` | Gradle 빌드 출력 |
| `build` | 작업별 설치용 APK·검증 로그·보고서 |
| `build/history` | 이전 감사·CI·기능·출시 검증 자료 |

## 로컬 산출물 찾기

로컬 작업 환경의 `build/README.md`에서 현재 설치용 APK와 검증 기록을 확인합니다. `build`는 Git 추적 대상이 아니므로 새로 복제한 저장소에는 이 파일과 과거 기록이 없을 수 있습니다.

과거 자료를 이동할 때는 원본 내용을 유지합니다. `build/history/relocations.json`에 이전 경로와 현재 경로, 이동 전후 무결성 확인 결과를 남깁니다. 과거 보고서에 적힌 경로는 이 대응표로 찾습니다.

## 보존할 파일

- `local.properties`와 로컬 서명·사용자 설정은 개발 환경에 필요하므로 정리 대상으로 삼지 않습니다.
- `.gradle`, `.kotlin`, `app/.cxx`는 개발 도구가 관리하는 캐시입니다. 이름만 보고 일괄 삭제하지 않습니다.
- 루트의 `app_icon_512.png`, `ShutterSoundZero_Infographic.png`는 검증 스크립트와 README가 참조하는 소스 자산입니다.
- 현재 설치용 APK, 마지막 성공 검증 기록, 미해결 오류의 재현 로그는 보존합니다.

`build`에는 빌드 중간 파일과 별도로 보관한 검증 기록이 함께 있습니다. 정리를 위해 `gradlew clean`이나 폴더 전체 삭제를 실행하기 전에 보존 범위를 확인하고, 사용이 끝난 임시 경로만 명시적으로 선택합니다.
