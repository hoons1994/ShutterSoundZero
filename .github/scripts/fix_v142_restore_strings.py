from pathlib import Path

path = Path("app/src/main/java/com/charmingcolor/shuttersoundzero/ui/settings/SettingsScreen.kt")
text = path.read_text(encoding="utf-8")
text = text.replace(
    '"설정 완료 후 무선 디버깅이 꺼져 있는 것은 정상입니다.\n\n" +',
    '"설정 완료 후 무선 디버깅이 꺼져 있는 것은 정상입니다.\\n\\n" +'
)
text = text.replace(
    '"진동·무음 모드에서도 카메라 셔터음이 나오는 기본 상태로 되돌립니다.\n\n" +',
    '"진동·무음 모드에서도 카메라 셔터음이 나오는 기본 상태로 되돌립니다.\\n\\n" +'
)
path.write_text(text, encoding="utf-8")
