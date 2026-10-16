# ShutterSoundZero Development Rules

## 프로젝트 개요
- **기술 스택**: Android / Kotlin, Jetpack Compose / Material3, ADB Shell, Android KeyStore
- **목적**: 무선 ADB 통신을 활용하여 안드로이드 디바이스 카메라 셔터음을 안전하게 제어하는 애플리케이션

## 표준 빌드 및 검증 명령어
- **디버그 APK 빌드**: `./gradlew assembleDebug`
- **단위 테스트 실행**: `./gradlew test`
- **린트 검사**: `./gradlew lint`

## 아키텍처 및 코딩 규칙
- ADB 키 및 보안 저장소 연동은 `AdbKeyHelper` 등 보안 전용 모듈을 거쳐 수행합니다.
- Android Keystore API 규격을 준수하며 비밀키를 코드나 로그에 직접 노출하지 않습니다.
- 코드 및 주석, 식별자에 이모지(Emoji)를 일절 사용하지 않습니다.
- 불필요한 바로가기 파일 및 스크립트를 생성하지 않습니다.
