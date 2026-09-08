from pathlib import Path

path = Path('app/src/main/java/com/charmingcolor/shuttersoundzero/ui/main/MainScreen.kt')
text = path.read_text(encoding='utf-8')

old_status = '''                RowDivider()
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
new_status = '''                RowDivider()
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
if old_status not in text:
    raise SystemExit('current status block not found')
text = text.replace(old_status, new_status, 1)

marker = '@Composable\nprivate fun PermissionSetupSection('
start = text.find(marker)
if start < 0:
    raise SystemExit('PermissionSetupSection start not found')

new_setup = '''@Composable
private fun PermissionSetupSection(
    hasPermission: Boolean,
    onStartNotificationPairing: () -> Unit,
    onResetPermission: () -> Unit
) {
    StatusRow(
        title = "시스템 보안 설정 권한",
        valueText = if (hasPermission) "연동 완료" else "1회 설정 필요",
        valueColor = if (hasPermission) StatusGreen else BrandBlueLight,
        onClick = if (!hasPermission) onStartNotificationPairing else null
    )

    RowDivider()

    if (!hasPermission) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = CardPaddingH, vertical = 12.dp)
        ) {
            Button(
                onClick = onStartNotificationPairing,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = BrandBlueLight)
            ) {
                Text(
                    text = "1회 설정 시작",
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }

        RowDivider()

        InfoRow(
            title = "1회 설정 안내",
            subtitle = "개발자 옵션의 [페어링 코드로 기기 페어링] 화면에서 상단바를 내려 6자리 숫자만 입력하면 됩니다. 앱이 권한 연동과 무음 설정을 적용한 뒤 무선 디버깅 자동 종료를 시도합니다."
        )
    } else {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = CardPaddingH, vertical = 12.dp)
        ) {
            OutlinedButton(
                onClick = onResetPermission,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                )
            ) {
                Text(
                    text = "권한 재설정 (연동 해제)",
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }

        RowDivider()

        InfoRow(
            title = "평소에는 무선 디버깅을 꺼두세요",
            subtitle = "권한 연동은 완료되어 있습니다. 카메라 무음 상태를 다시 적용하거나 원래대로 복원할 때만 설정 → 카메라 설정에서 무선 디버깅을 잠시 켜면 됩니다."
        )
    }

    RowDivider()

    InfoRow(
        title = "재부팅·소프트웨어 업데이트 후",
        subtitle = "설정이 그대로면 아무 작업이 필요 없습니다. 셔터음이 다시 들리면 설정 → 카메라 설정에서 [카메라 무음 다시 적용]을 사용해 주세요. [1회 설정 필요]가 표시될 때만 권한 연동을 다시 진행하면 됩니다."
    )
}
'''

text = text[:start] + new_setup
path.write_text(text, encoding='utf-8')
