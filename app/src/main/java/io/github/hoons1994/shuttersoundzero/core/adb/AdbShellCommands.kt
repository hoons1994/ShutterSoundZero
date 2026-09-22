package io.github.hoons1994.shuttersoundzero.core.adb

internal object AdbShellCommands {
    private const val WRITE_SECURE_SETTINGS = "android.permission.WRITE_SECURE_SETTINGS"
    private const val CSC_KEY = "csc_pref_camera_forced_shuttersound_key"

    fun grantWriteSecureSettings(packageName: String, userId: Int): String =
        permissionCommand("grant", packageName, userId)

    fun revokeWriteSecureSettings(packageName: String, userId: Int): String =
        permissionCommand("revoke", packageName, userId)

    fun setCameraMute(mute: Boolean, userId: Int): String {
        requireValidUserId(userId)
        val value = if (mute) 0 else 1
        return "settings --user $userId put system $CSC_KEY $value"
    }

    private fun permissionCommand(action: String, packageName: String, userId: Int): String {
        require(packageName.matches(Regex("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+)+"))) {
            "Invalid package name"
        }
        requireValidUserId(userId)
        return "pm $action --user $userId $packageName $WRITE_SECURE_SETTINGS"
    }

    private fun requireValidUserId(userId: Int) {
        require(userId >= 0) { "Invalid Android user ID" }
    }
}
