package com.charmingcolor.shuttersoundzero.core.adb

/**
 * 앱/소프트웨어 업데이트 경계에서 기존 ADB identity를 새로 만들어야 하는지 결정한다.
 *
 * 정상적으로 권한이 유지된 사용자는 기존 페어링 identity를 계속 사용하고,
 * 이전 연동 흔적이 있는데 권한만 예기치 않게 사라진 경우에만 재설치와 유사한 fresh pairing 상태를 만든다.
 */
object AdbStateCleanupPolicy {
    fun shouldResetIdentityAfterUnexpectedPermissionLoss(
        hadLinkageEvidence: Boolean,
        permissionRevokedByUser: Boolean,
        hasWritePermission: Boolean
    ): Boolean {
        return hadLinkageEvidence && !permissionRevokedByUser && !hasWritePermission
    }
}
