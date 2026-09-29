package io.github.hoons1994.shuttersoundzero.core.adb

internal const val PAIRING_RECOVERY_LABEL = "6자리 코드로 다시 연결"

/** Actionable failure categories shared by home, settings and the quick-settings tile. */
enum class CameraMuteFailure(val title: String, val message: String) {
    LOCAL_NETWORK_PERMISSION(
        "앱 권한을 확인해 주세요",
        "카메라 설정을 바꾸려면 로컬 네트워크 권한이 필요합니다. 앱 설정에서 권한을 허용한 뒤 다시 시도해 주세요."
    ),
    DISCOVERY(
        "무선 디버깅 연결을 찾지 못했어요",
        "Wi-Fi 연결과 무선 디버깅 화면의 연결 허용 여부를 확인해 주세요. 무선 디버깅을 껐다 켠 뒤 다시 시도하고, 계속 실패하면 앱의 연결 오류 안내에서 [$PAIRING_RECOVERY_LABEL]을 눌러 새 코드로 연결해 주세요."
    ),
    CONNECTION(
        "기기에 연결하지 못했어요",
        "기존 페어링이 만료되거나 해제됐을 수 있습니다. 앱의 연결 오류 안내에서 [$PAIRING_RECOVERY_LABEL]을 눌러 새 코드로 연결해 주세요. 무선 디버깅을 켜는 것만으로는 연결이 복구되지 않을 수 있습니다."
    ),
    APPLY(
        "카메라 설정을 변경하지 못했어요",
        "설정 적용 또는 확인 과정에서 오류가 발생했습니다. 다시 시도해도 실패하면 설정 → 도움말의 오류 신고에 진단 정보를 포함해 주세요."
    );

    val canReconnect: Boolean get() = this == DISCOVERY || this == CONNECTION

    companion object {
        fun from(error: Throwable?): CameraMuteFailure = when (error) {
            is LocalNetworkPermissionRequiredException -> LOCAL_NETWORK_PERMISSION
            is AdbServiceNotFoundException -> DISCOVERY
            is AdbConnectionFailedException -> CONNECTION
            else -> APPLY
        }
    }
}
