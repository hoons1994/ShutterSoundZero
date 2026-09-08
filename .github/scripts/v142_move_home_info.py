from pathlib import Path

path = Path('app/src/main/java/com/charmingcolor/shuttersoundzero/ui/main/MainScreen.kt')
text = path.read_text(encoding='utf-8')
old = '''                RowDivider()
                InfoRow(
                    title = when {
                        uiState.isCscMuted -> "이제 앱을 계속 열어둘 필요가 없습니다"
                        hasEffectivePermission -> "권한은 유지되어 있습니다"
                        else -> "처음 한 번만 설정하면 됩니다"
                    },
                    subtitle = when {
                        uiState.isCscMuted ->
                            "진동·무음 모드에서 카메라 셔터음이 나지 않도록 설정되어 있습니다. 평소에는 무선 디버깅을 꺼두는 것이 정상이며, 나중에 설정을 바꿀 때만 잠시 다시 켜면 됩니다."
                        hasEffectivePermission ->
                            "현재 카메라 무음 설정이 적용되어 있지 않습니다. 설정 → 카메라 설정에서 [카메라 무음 다시 적용]을 사용해 주세요. 상태를 바꾸는 동안에만 무선 디버깅을 잠시 켜면 됩니다."
                        else ->
                            "[1회 설정 시작]에서 6자리 페어링 코드만 입력하면 권한 연동과 무음 설정을 한 번에 적용합니다. 완료 후에는 무선 디버깅 자동 종료를 시도하고, 자동으로 끄지 못하면 바로 안내합니다."
                    }
                )
                RowDivider()
                ActionRow(
                    title = "카메라 열어서 테스트",
                    onClick = {
                        try {
                            val cameraIntent = Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            context.startActivity(cameraIntent)
                        } catch (e: Exception) {
                            Toast.makeText(context, "기본 카메라 앱을 실행할 수 없습니다.", Toast.LENGTH_SHORT).show()
                        }
                    }
                )
'''
new = '''                RowDivider()
                ActionRow(
                    title = "카메라 열어서 테스트",
                    onClick = {
                        try {
                            val cameraIntent = Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            context.startActivity(cameraIntent)
                        } catch (e: Exception) {
                            Toast.makeText(context, "기본 카메라 앱을 실행할 수 없습니다.", Toast.LENGTH_SHORT).show()
                        }
                    }
                )
                RowDivider()
                InfoRow(
                    title = when {
                        uiState.isCscMuted -> "이제 앱을 계속 열어둘 필요가 없습니다"
                        hasEffectivePermission -> "권한은 유지되어 있습니다"
                        else -> "처음 한 번만 설정하면 됩니다"
                    },
                    subtitle = when {
                        uiState.isCscMuted ->
                            "진동·무음 모드에서 카메라 셔터음이 나지 않도록 설정되어 있습니다. 평소에는 무선 디버깅을 꺼두는 것이 정상이며, 나중에 설정을 바꿀 때만 잠시 다시 켜면 됩니다."
                        hasEffectivePermission ->
                            "현재 카메라 무음 설정이 적용되어 있지 않습니다. 설정 → 카메라 설정에서 [카메라 무음 다시 적용]을 사용해 주세요. 상태를 바꾸는 동안에만 무선 디버깅을 잠시 켜면 됩니다."
                        else ->
                            "[1회 설정 시작]에서 6자리 페어링 코드만 입력하면 권한 연동과 무음 설정을 한 번에 적용합니다. 완료 후에는 무선 디버깅 자동 종료를 시도하고, 자동으로 끄지 못하면 바로 안내합니다."
                    }
                )
'''
if old not in text:
    raise SystemExit('target block not found')
path.write_text(text.replace(old, new, 1), encoding='utf-8')
