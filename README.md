# 셔터음 제로 (Shutter Sound Zero)

> 삼성 갤럭시폰을 위한 카메라 셔터음 설정 도구

[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)](LICENSE)
[![Platform](https://img.shields.io/badge/Platform-Android%2011+-green.svg)](https://developer.android.com)
[![Samsung Galaxy](https://img.shields.io/badge/Device-Samsung%20Galaxy-0c7cd5.svg)](https://www.samsung.com)
[![Version](https://img.shields.io/badge/Version-1.4.2-orange.svg)](https://github.com/hoons1994/ShutterSoundZero/releases/latest)

<div align="center">
  <img src="ShutterSoundZero_Infographic.png" alt="셔터음 제로 안내 인포그래픽" width="720" />
</div>

---

## 📖 소개

**셔터음 제로(Shutter Sound Zero)**는 도서관, 독서실, 세미나실, 강의실 등 정숙이 필요한 장소나 잠든 영유아 및 반려동물을 촬영할 때 주변에 소음으로 인한 불편을 주지 않도록 돕는 안드로이드 오픈소스 유틸리티입니다.

화면을 실시간 감시하는 접근성 기반 방식 대신 삼성 갤럭시의 **순정 시스템(CSC) 설정값**을 활용합니다. 최초 1회 권한 연동 후 앱이나 빠른 설정 타일에서 카메라 셔터음 상태를 제어할 수 있습니다. 실제 설정을 변경할 때는 Android 보안 정책상 무선 디버깅이 켜져 있어야 하며, 앱은 실제 CSC 값을 다시 읽어 현재 기기 상태와 화면 표시가 일치하도록 관리합니다.

---

## 📱 다운로드

* **최신 릴리즈 APK 다운로드**: [GitHub Releases 최신 버전 받기](https://github.com/hoons1994/ShutterSoundZero/releases/latest)
* 지원 기종: 삼성 갤럭시폰 (Android 11 이상)

---

## ✨ 주요 특징

1. **📲 PC 및 외부 앱 없는 기기 단독 1회 설정**
   * 컴퓨터, USB 케이블, 외부 Shizuku 설치가 필요 없습니다.
   * 기기의 [무선 디버깅] → [페어링 코드로 기기 페어링] 화면에서 **상단바 알림에 6자리 코드만 입력**하면 권한 연동을 진행할 수 있습니다.

2. **🔔 벨소리 / 진동·무음 모드 연계**
   * 카메라 소리를 항상 강제로 없애는 방식이 아닙니다.
   * **벨소리 모드에서는 기존처럼 셔터음이 나며, 진동 또는 무음 모드에서는 셔터음이 나지 않도록 CSC 설정을 제어합니다.**

3. **🎯 실제 CSC 상태와 화면 표시 동기화**
   * 메인 화면의 스위치와 상태 문구는 저장된 앱 설정이 아니라 실제 `csc_pref_camera_forced_shuttersound_key` 값을 기준으로 표시합니다.
   * 앱으로 돌아올 때 실제 상태를 다시 확인해 시스템 업데이트나 외부 변경 뒤에도 현재 상태를 정확하게 보여줍니다.

4. **🎛️ 빠른 설정(Quick Settings) 타일 지원**
   * 앱을 열지 않고도 알림창의 빠른 설정 패널에서 카메라 무음 상태를 빠르게 전환할 수 있습니다.

5. **🔄 재부팅 및 소프트웨어 업데이트 상태 확인**
   * 재부팅이나 소프트웨어 업데이트 이후 실제 CSC 상태를 확인합니다.
   * 무음 설정이 초기화된 경우 자동 복원으로 표시하지 않고, 무선 디버깅을 켠 뒤 다시 적용하도록 알림으로 안내합니다.
   * 시스템 보안 설정 권한까지 해제된 경우 메인 화면에서 [권한 요청]을 다시 진행해야 합니다.
   * 소프트웨어 업데이트 자동 감지는 앱 설정에서 켜거나 끌 수 있습니다.

6. **🔐 앱 잠금**
   * 앱 실행 시 지문 또는 기기의 PIN·패턴·비밀번호로 본인 확인을 요구할 수 있습니다.
   * 앱 잠금을 **켜거나 끌 때 모두 다시 인증**해야 하므로 설정 화면에 접근한 상태에서도 잠금을 임의로 해제하기 어렵게 구성했습니다.

7. **🧹 개발자 옵션 정리 지원**
   * 최초 권한 설정을 마친 뒤 개발자 옵션과 USB·무선 디버깅을 끄는 과정을 앱 설정에서 도와줍니다.
   * 이미 부여된 `WRITE_SECURE_SETTINGS` 권한은 개발자 옵션을 꺼도 유지됩니다. 다만 카메라 셔터음 설정을 실제로 변경할 때는 무선 디버깅을 다시 켜야 합니다.

8. **⬆️ 안전한 앱 업데이트 확인·설치**
   * 설정의 [앱 업데이트]를 사용자가 직접 눌렀을 때만 GitHub Releases에서 최신 정식 버전을 확인합니다.
   * 새 버전이 있으면 릴리즈 노트를 보여주고 APK를 직접 다운로드할 수 있습니다.
   * 설치 전에 **SHA-256, 패키지명, versionCode/versionName, 현재 설치 앱과의 서명 인증서 일치 여부**를 모두 검증합니다.
   * 모든 검증을 통과한 APK만 Android 패키지 설치 화면으로 전달합니다.

9. **🔋 상주 감시 서비스 없음**
   * 화면이나 카메라 동작을 계속 감시하는 접근성 기반 상주 서비스를 사용하지 않습니다.
   * 카메라 권한 없이 시스템 설정값을 제어하는 방식으로 동작합니다.

---

## 🚀 처음 1회 설정 방법 (약 1분 소요)

```text
[1단계: 권한 요청]
  ➔ Wi-Fi에 연결한 상태에서 앱 메인 화면의 [권한 요청] 버튼을 누릅니다.
  ➔ 안내에 따라 기기의 [무선 디버깅] 설정으로 이동합니다.

[2단계: 페어링 코드 확인]
  ➔ [무선 디버깅]을 켠 뒤 [페어링 코드로 기기 페어링]을 누릅니다.
  ➔ 화면에 표시되는 6자리 일회용 코드를 확인합니다.

[3단계: 상단바 알림에 입력]
  ➔ 페어링 코드 화면을 유지한 채 상단바를 내려 ShutterSoundZero 알림의 [코드 입력]을 사용합니다.
  ➔ 6자리 코드를 입력하면 권한 연동과 카메라 무음 설정이 진행됩니다.
```

> **💡 알림 팁**: One UI에서 ShutterSoundZero 알림을 [자세한 팝업]으로 설정하면 페어링 알림의 [코드 입력] 버튼을 바로 사용할 수 있습니다. 간략한 팝업에서도 알림을 펼치면 같은 기능을 사용할 수 있습니다.

> **💡 설정 완료 후**: 권한 연동이 끝나면 [무선 디버깅]을 꺼도 이미 적용된 카메라 무음 상태와 `WRITE_SECURE_SETTINGS` 권한은 유지됩니다. 이후 셔터음 상태를 다시 변경할 때만 무선 디버깅을 켜면 됩니다. 필요하면 앱의 **설정 → 개발자 옵션 끄기** 기능을 사용할 수 있습니다.

---

## ⬆️ 앱 업데이트

설정 화면의 **[앱 업데이트]**를 누르면 GitHub의 최신 정식 Release를 확인합니다. 앱 실행 시 또는 백그라운드에서 자동으로 업데이트 서버에 접속하지 않습니다.

새 버전이 발견되면 다음 순서로 처리합니다.

1. 최신 정식 Release의 버전과 릴리즈 노트 확인
2. 사용자가 [업데이트 다운로드]를 선택한 경우에만 APK 다운로드
3. GitHub Release의 `.sha256` 파일과 실제 APK SHA-256 비교
4. APK의 패키지명과 `versionCode` / `versionName` 확인
5. 현재 설치된 ShutterSoundZero와 APK의 서명 인증서 일치 여부 확인
6. 모든 검증을 통과한 경우에만 Android 설치 화면 실행

Android의 보안 정책에 따라 최초 1회 **[이 출처 허용]** 설정을 요구할 수 있습니다. 이 권한은 사용자가 직접 Android 설정에서 허용하며, 앱이 백그라운드에서 자동 설치를 수행하지 않습니다.

---

## 🔐 앱 잠금

설정 화면의 **[앱 잠금]**을 켜면 다음 앱 실행부터 기기의 보안 인증을 요구합니다.

* 지원 인증: 강력한 생체인증 또는 기기 PIN·패턴·비밀번호
* 잠금 활성화 시 본인 인증 필요
* 잠금 비활성화 시에도 다시 본인 인증 필요
* 인증을 취소하거나 실패하면 기존 잠금 상태 유지

---

## 🛡️ 안전성 및 보안

* **On-Device 중심 동작**: 무선 ADB 페어링과 시스템 설정 변경은 사용자 기기 내부에서 처리합니다.
* **명시적 업데이트 통신**: GitHub Releases 네트워크 연결은 사용자가 [앱 업데이트]를 눌렀을 때만 발생하며 자동·주기적 업데이트 확인은 하지 않습니다.
* **검증된 업데이트 APK**: 다운로드한 APK는 SHA-256뿐 아니라 현재 설치 앱과의 서명 인증서 일치까지 확인한 뒤 설치 화면으로 전달합니다.
* **카메라 권한 없음**: 앱은 `android.permission.CAMERA` 권한을 요청하지 않으며 사진이나 카메라 영상에 접근하지 않습니다.
* **독립 암호화 키**: 무선 디버깅에 사용하는 키는 각 기기의 앱 전용 저장 영역에서 생성·보관합니다.
* **실제 시스템 상태 확인**: 앱 화면의 무음 상태는 실제 CSC 값을 기준으로 표시해 저장된 선호값과 시스템 상태가 어긋나는 문제를 줄였습니다.

---

## ⚠️ 사용 시 참고

* 삼성 갤럭시의 시스템 설정 구조와 One UI 동작을 이용하는 앱이므로 향후 삼성 소프트웨어 업데이트에 따라 동작 방식이 달라질 수 있습니다.
* 셔터음 설정을 새로 변경할 때 Android 보안 정책상 무선 디버깅이 일시적으로 필요할 수 있습니다.
* 이미 적용된 CSC 무음 상태는 평소 무선 디버깅을 꺼두어도 유지됩니다.

---

## ⚖️ 올바른 사용 안내 및 법적 고지

* 본 프로그램은 도서관, 독서실, 세미나실, 학술 촬영, 수면 중인 영유아나 반려동물 촬영 등 **정숙이 필요한 합법적인 상황에서 주변 소음 피해를 방지하기 위한 목적**으로 제작되었습니다.
* 타인의 의사에 반하는 불법 촬영, 사생활 침해 등 관련 법률을 위반하는 모든 악용 행위는 엄격히 금지되며 촬영 행위에 대한 법적 책임은 사용자 본인에게 있습니다.
* 본 프로젝트는 개인이 제작한 독립 서드파티 오픈소스 소프트웨어이며 삼성전자(Samsung Electronics)와 공식적인 관련이 없습니다.

---

## 📄 라이선스 (License)

본 프로젝트는 **[GNU General Public License v3.0 (GPL-3.0)](LICENSE)** 라이선스에 따라 배포됩니다.

```text
Shutter Sound Zero (셔터음 제로)
Copyright (C) 2026 charmingcolor

This program is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.
```
