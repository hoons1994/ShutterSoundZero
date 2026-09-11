# 셔터음 제로 (Shutter Sound Zero)

> 삼성 갤럭시폰을 위한 카메라 셔터음 설정 도구

[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)](LICENSE)
[![Platform](https://img.shields.io/badge/Platform-Android%2011+-green.svg)](https://developer.android.com)
[![Samsung Galaxy](https://img.shields.io/badge/Device-Samsung%20Galaxy-0c7cd5.svg)](https://www.samsung.com)
[![Version](https://img.shields.io/badge/Version-1.4.10-orange.svg)](https://github.com/hoons1994/ShutterSoundZero/releases/latest)

<div align="center">
  <img src="ShutterSoundZero_Infographic.png" alt="셔터음 제로 안내 인포그래픽" width="720" />
</div>

---

## 📖 소개

**셔터음 제로(Shutter Sound Zero)**는 삼성 갤럭시의 순정 시스템(CSC) 설정을 이용해 카메라 셔터음 동작을 관리하는 오픈소스 안드로이드 앱입니다.

접근성 서비스를 이용해 화면이나 카메라 실행을 상시 감시하지 않습니다. 최초 1회 로컬 무선 ADB로 `WRITE_SECURE_SETTINGS` 권한을 연동한 뒤, 필요할 때만 무선 디버깅을 켜서 실제 CSC 값을 변경합니다.

현재 최신 안정 버전은 **v1.4.10**입니다.

## 📱 다운로드

- **최신 APK:** [GitHub Releases](https://github.com/hoons1994/ShutterSoundZero/releases/latest)
- **지원 환경:** 삼성 갤럭시 / Android 11 이상
- v1.4.8부터 루트의 완성형 앱 아이콘을 런처 리소스로 직접 사용해 동일한 디자인이 표시되도록 정리했습니다.

## ✨ 주요 기능

- **기기 단독 1회 설정** — PC, USB 케이블, 외부 Shizuku 앱 없이 무선 디버깅의 6자리 페어링 코드로 권한 연동과 초기 설정을 진행합니다.
- **벨소리 모드 연계** — 벨소리 모드에서는 기존 셔터음이 유지되고, 진동·무음 모드에서는 CSC 설정에 따라 셔터음이 나지 않도록 동작합니다. 단, 벨소리 모드여도 **설정 → 소리 및 진동 → 음량 → 시스템** 음량이 0이면 촬영음이 들리지 않을 수 있습니다.
- **실제 CSC 상태 확인** — 저장된 희망 상태가 아니라 `csc_pref_camera_forced_shuttersound_key`의 실제 값을 읽어 화면 상태를 표시합니다.
- **빠른 설정 타일** — 앱을 열지 않고도 Quick Settings 타일에서 현재 상태를 확인하고 설정 변경을 시작할 수 있습니다.
- **첫 실행 중요 안내 / 도움말** — 신규 설치에서는 핵심 안내를 먼저 보여주고, 자세한 설명은 설정의 질문형 도움말에서 확인할 수 있습니다.
- **앱 잠금** — 생체인증 또는 기기 PIN·패턴·비밀번호로 앱 접근을 보호할 수 있습니다.
- **반자동 앱 업데이트** — GitHub Releases에서 새 버전을 확인하고, APK의 SHA-256·패키지명·버전·서명 인증서를 검증한 뒤 Android 설치 화면으로 전달합니다.
- **상주 감시 서비스 없음** — 카메라 권한이나 접근성 기반 상주 서비스를 사용하지 않습니다.

## 🚀 처음 1회 설정

1. Wi-Fi에 연결한 상태에서 앱의 **[1회 설정 시작]**을 누릅니다.
2. Android **개발자 옵션 → 무선 디버깅**을 켭니다.
3. **[페어링 코드로 기기 페어링]**을 열어 6자리 코드를 확인합니다.
4. 페어링 화면을 유지한 채 상단바의 ShutterSoundZero 알림에서 코드를 입력합니다.
5. 권한 연동과 카메라 무음 설정이 완료되면 평소에는 무선 디버깅을 꺼두면 됩니다.

> 실제 카메라 셔터음 설정을 다시 변경할 때만 무선 디버깅을 잠시 켜면 됩니다.

## 🔄 셔터음 설정을 다시 바꿔야 할 때

앱은 실제 CSC 값과 권한 연동 상태를 기준으로 필요한 동작만 안내합니다.

- **셔터음 설정이 그대로 유지됨:** 아무 작업도 필요하지 않습니다.
- **셔터음이 다시 들리지만 권한 연동은 유지됨:** 무선 디버깅을 켠 뒤 **설정 → 카메라 설정 → [카메라 무음 다시 적용]**을 사용합니다.
- **[1회 설정 필요]가 표시됨:** 무선 디버깅을 켜고 **[1회 설정 시작]**으로 다시 연동합니다.
- **원래 셔터음으로 복원:** **설정 → 카메라 설정 → [카메라 셔터음 원래대로 복원]**을 사용합니다.

일반적인 재부팅만으로 CSC 셔터음 설정이 초기화되는 것을 전제로 하지 않습니다. **One UI 또는 Android 업데이트 후 실제 CSC 값이 초기화된 경우에만 재적용이 필요합니다.**

복원·재적용 후에는 이전 로컬 ADB 세션을 정리하고, CSC 반영이 잠시 늦는 경우를 고려해 실제 상태를 짧게 재확인합니다.

## ⬆️ 앱 업데이트

ShutterSoundZero는 백그라운드에서 APK를 자동 설치하지 않습니다. 기본값으로 앱 실행 시 24시간에 한 번 이하로 최신 정식 Release 메타데이터를 확인하고, 새 버전이 있을 때만 업데이트 동작을 표시합니다.

사용자가 업데이트를 시작하면 다음을 검증합니다.

1. 최신 정식 Release 확인
2. APK 다운로드
3. Release의 `.sha256`과 실제 APK SHA-256 비교
4. 패키지명 및 `versionCode` / `versionName` 확인
5. 현재 설치 앱과 APK의 서명 인증서 일치 여부 확인
6. 검증 성공 시 Android 설치 화면 실행

동일 패키지·동일 서명의 정상적인 앱 업데이트에서는 기존 앱 데이터와 권한 연동이 유지되는 것이 일반적입니다. 다만 업데이트 후 기존 연동 흔적이 있는데 실제 `WRITE_SECURE_SETTINGS` 권한이 예외적으로 사라진 경우에는 다시 연동하도록 안내합니다.

## 🧩 동작 방식

실제 CSC 변경은 앱 UID가 직접 private 시스템 설정을 수정하는 방식이 아니라, 로컬 무선 ADB 연결을 통해 다음과 같은 시스템 명령을 실행하는 구조입니다.

```text
settings put system csc_pref_camera_forced_shuttersound_key 0
settings put system csc_pref_camera_forced_shuttersound_key 1
```

최초 연동 시에는 다음 권한을 로컬 ADB를 통해 부여합니다.

```text
pm grant <package-name> android.permission.WRITE_SECURE_SETTINGS
```

앱은 변경 후 실제 CSC 값을 다시 읽어 UI 상태와 기기 상태가 일치하는지 확인합니다.

## 🔐 개인정보 및 보안

- 카메라 권한을 요구하지 않습니다.
- 화면·카메라 동작을 상시 감시하지 않습니다.
- 무선 ADB 연결은 기기 내부 로컬 연결 용도로 사용합니다.
- APK 업데이트 시 해시와 서명 연속성을 검증합니다.

## ⚠️ 사용 시 유의사항

촬영 가능 여부와 셔터음 관련 규정은 국가·지역·장소에 따라 다를 수 있습니다. 불법 촬영, 사생활 침해 또는 촬영이 금지된 장소에서의 사용을 위한 앱이 아닙니다. 사용자는 해당 지역의 법률과 시설 규정을 확인하고 준수해야 합니다.

## 📄 라이선스

이 프로젝트는 **GNU General Public License v3.0**으로 배포됩니다. 자세한 내용은 [LICENSE](LICENSE)을 확인해 주세요.
