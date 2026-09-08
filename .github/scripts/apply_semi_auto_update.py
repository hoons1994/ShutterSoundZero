from pathlib import Path


def replace_once(path: str, old: str, new: str) -> None:
    file = Path(path)
    text = file.read_text(encoding="utf-8")
    if old not in text:
        raise SystemExit(f"target not found in {path}: {old[:120]!r}")
    file.write_text(text.replace(old, new, 1), encoding="utf-8")


# PreferencesRepository: automatic app-update check preference and small persisted state.
replace_once(
    "app/src/main/java/com/charmingcolor/shuttersoundzero/data/PreferencesRepository.kt",
    '''    var isAppLockEnabled: Boolean
        get() = prefs.getBoolean(KEY_APP_LOCK_ENABLED, false)
        set(value) = prefs.edit { putBoolean(KEY_APP_LOCK_ENABLED, value) }

    companion object {''',
    '''    var isAppLockEnabled: Boolean
        get() = prefs.getBoolean(KEY_APP_LOCK_ENABLED, false)
        set(value) = prefs.edit { putBoolean(KEY_APP_LOCK_ENABLED, value) }

    /**
     * 앱을 열었을 때 최신 정식 버전을 조용히 확인할지 여부.
     * APK 다운로드와 설치는 항상 사용자가 [업데이트]를 누른 뒤에만 시작한다.
     */
    var isAppUpdateAutoCheckEnabled: Boolean
        get() = prefs.getBoolean(KEY_APP_UPDATE_AUTO_CHECK, true)
        set(value) = prefs.edit { putBoolean(KEY_APP_UPDATE_AUTO_CHECK, value) }

    var lastAppUpdateCheckAtMillis: Long
        get() = prefs.getLong(KEY_LAST_APP_UPDATE_CHECK_AT, 0L)
        set(value) = prefs.edit { putLong(KEY_LAST_APP_UPDATE_CHECK_AT, value) }

    var knownAvailableAppUpdateVersion: String?
        get() = prefs.getString(KEY_KNOWN_AVAILABLE_APP_UPDATE_VERSION, null)
        set(value) = prefs.edit {
            if (value == null) remove(KEY_KNOWN_AVAILABLE_APP_UPDATE_VERSION)
            else putString(KEY_KNOWN_AVAILABLE_APP_UPDATE_VERSION, value)
        }

    companion object {'''
)
replace_once(
    "app/src/main/java/com/charmingcolor/shuttersoundzero/data/PreferencesRepository.kt",
    '''        private const val KEY_PERMISSION_REVOKED_BY_USER = "permission_revoked_by_user"
        private const val KEY_APP_LOCK_ENABLED = "app_lock_enabled"

        @Volatile''',
    '''        private const val KEY_PERMISSION_REVOKED_BY_USER = "permission_revoked_by_user"
        private const val KEY_APP_LOCK_ENABLED = "app_lock_enabled"
        private const val KEY_APP_UPDATE_AUTO_CHECK = "app_update_auto_check"
        private const val KEY_LAST_APP_UPDATE_CHECK_AT = "last_app_update_check_at"
        private const val KEY_KNOWN_AVAILABLE_APP_UPDATE_VERSION = "known_available_app_update_version"

        @Volatile'''
)

# AppUpdateManager: cadence helper used by the silent app-open check.
replace_once(
    "app/src/main/java/com/charmingcolor/shuttersoundzero/update/AppUpdateManager.kt",
    '''    private const val USER_AGENT = "ShutterSoundZero-UpdateChecker"

    sealed interface UpdateCheckResult {''',
    '''    private const val USER_AGENT = "ShutterSoundZero-UpdateChecker"
    internal const val AUTOMATIC_CHECK_INTERVAL_MILLIS = 24L * 60L * 60L * 1000L

    sealed interface UpdateCheckResult {'''
)
replace_once(
    "app/src/main/java/com/charmingcolor/shuttersoundzero/update/AppUpdateManager.kt",
    '''    internal fun isNewerVersion(candidate: String, current: String): Boolean {''',
    '''    internal fun isAutomaticCheckDue(
        enabled: Boolean,
        lastCheckAtMillis: Long,
        nowMillis: Long
    ): Boolean {
        if (!enabled) return false
        if (lastCheckAtMillis <= 0L) return true
        if (nowMillis < lastCheckAtMillis) return true
        return nowMillis - lastCheckAtMillis >= AUTOMATIC_CHECK_INTERVAL_MILLIS
    }

    internal fun installedVersionName(context: Context): String = currentVersionName(context)

    internal fun isNewerVersion(candidate: String, current: String): Boolean {'''
)

# MainScreen: silent app-open check + update action card + one-tap prepare/install flow.
replace_once(
    "app/src/main/java/com/charmingcolor/shuttersoundzero/ui/main/MainScreen.kt",
    '''import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue''',
    '''import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue'''
)
replace_once(
    "app/src/main/java/com/charmingcolor/shuttersoundzero/ui/main/MainScreen.kt",
    '''import com.charmingcolor.shuttersoundzero.theme.BrandBlueLight
import com.charmingcolor.shuttersoundzero.theme.StatusAmber''',
    '''import com.charmingcolor.shuttersoundzero.data.PreferencesRepository
import com.charmingcolor.shuttersoundzero.theme.BrandBlueLight
import com.charmingcolor.shuttersoundzero.theme.StatusAmber'''
)
replace_once(
    "app/src/main/java/com/charmingcolor/shuttersoundzero/ui/main/MainScreen.kt",
    '''import com.charmingcolor.shuttersoundzero.ui.notification.PairingNotificationHelper
''',
    '''import com.charmingcolor.shuttersoundzero.ui.notification.PairingNotificationHelper
import com.charmingcolor.shuttersoundzero.update.AppUpdateManager
import kotlinx.coroutines.launch
'''
)
replace_once(
    "app/src/main/java/com/charmingcolor/shuttersoundzero/ui/main/MainScreen.kt",
    '''    val snackbarHostState = remember { SnackbarHostState() }
    val lifecycleOwner = LocalLifecycleOwner.current

    var showWifiRequiredDialog by remember { mutableStateOf(false) }

    val startPairing = {''',
    '''    val snackbarHostState = remember { SnackbarHostState() }
    val lifecycleOwner = LocalLifecycleOwner.current
    val prefs = remember { PreferencesRepository.getInstance(context) }
    val updateScope = rememberCoroutineScope()

    var showWifiRequiredDialog by remember { mutableStateOf(false) }
    var availableAppUpdateVersion by remember { mutableStateOf<String?>(null) }
    var cachedAppUpdateInfo by remember { mutableStateOf<AppUpdateManager.UpdateInfo?>(null) }
    var isAppUpdatePreparing by remember { mutableStateOf(false) }
    var appUpdateProgress by remember { mutableStateOf(0) }
    var appUpdateStatusMessage by remember { mutableStateOf<String?>(null) }
    var appUpdateErrorMessage by remember { mutableStateOf<String?>(null) }

    fun refreshKnownAppUpdateVersion() {
        val knownVersion = prefs.knownAvailableAppUpdateVersion
        val currentVersion = AppUpdateManager.installedVersionName(context)
        if (knownVersion != null && AppUpdateManager.isNewerVersion(knownVersion, currentVersion)) {
            availableAppUpdateVersion = knownVersion
        } else {
            availableAppUpdateVersion = null
            if (knownVersion != null) prefs.knownAvailableAppUpdateVersion = null
        }
    }

    val startHomeAppUpdate: () -> Unit = {
        if (!isAppUpdatePreparing) {
            if (!AppUpdateManager.canRequestPackageInstalls(context)) {
                Toast.makeText(
                    context,
                    "최초 1회 [이 출처 허용]을 켠 뒤 앱으로 돌아와 [업데이트]를 다시 눌러 주세요.",
                    Toast.LENGTH_LONG
                ).show()
                try {
                    AppUpdateManager.openInstallPermissionSettings(context)
                } catch (error: Throwable) {
                    appUpdateErrorMessage = friendlyHomeUpdateError(error)
                }
            } else {
                updateScope.launch {
                    isAppUpdatePreparing = true
                    appUpdateProgress = 0
                    appUpdateErrorMessage = null
                    try {
                        val update = cachedAppUpdateInfo ?: when (val result = AppUpdateManager.checkForUpdate(context)) {
                            is AppUpdateManager.UpdateCheckResult.UpToDate -> {
                                prefs.lastAppUpdateCheckAtMillis = System.currentTimeMillis()
                                prefs.knownAvailableAppUpdateVersion = null
                                availableAppUpdateVersion = null
                                appUpdateStatusMessage = "이미 최신 버전을 사용하고 있습니다."
                                return@launch
                            }
                            is AppUpdateManager.UpdateCheckResult.Available -> {
                                prefs.lastAppUpdateCheckAtMillis = System.currentTimeMillis()
                                prefs.knownAvailableAppUpdateVersion = result.update.versionName
                                availableAppUpdateVersion = result.update.versionName
                                cachedAppUpdateInfo = result.update
                                result.update
                            }
                        }

                        val verified = AppUpdateManager.downloadAndVerify(
                            context = context,
                            update = update,
                            onProgress = { progress -> appUpdateProgress = progress }
                        )
                        if (!AppUpdateManager.launchInstaller(context, verified)) {
                            error("Android 설치 화면을 열 수 없습니다. 기기의 설치 권한 설정을 확인해 주세요.")
                        }
                    } catch (error: Throwable) {
                        appUpdateErrorMessage = friendlyHomeUpdateError(error)
                    } finally {
                        isAppUpdatePreparing = false
                    }
                }
            }
        }
    }

    val startPairing = {'''
)
replace_once(
    "app/src/main/java/com/charmingcolor/shuttersoundzero/ui/main/MainScreen.kt",
    '''    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.refreshState()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
''',
    '''    LaunchedEffect(Unit) {
        refreshKnownAppUpdateVersion()
        val now = System.currentTimeMillis()
        if (
            AppUpdateManager.isAutomaticCheckDue(
                enabled = prefs.isAppUpdateAutoCheckEnabled,
                lastCheckAtMillis = prefs.lastAppUpdateCheckAtMillis,
                nowMillis = now
            )
        ) {
            // 실패해도 사용자에게 오류를 띄우지 않고 다음 자동 확인 시점까지 조용히 대기한다.
            prefs.lastAppUpdateCheckAtMillis = now
            try {
                when (val result = AppUpdateManager.checkForUpdate(context)) {
                    is AppUpdateManager.UpdateCheckResult.UpToDate -> {
                        prefs.knownAvailableAppUpdateVersion = null
                        availableAppUpdateVersion = null
                        cachedAppUpdateInfo = null
                    }
                    is AppUpdateManager.UpdateCheckResult.Available -> {
                        prefs.knownAvailableAppUpdateVersion = result.update.versionName
                        availableAppUpdateVersion = result.update.versionName
                        cachedAppUpdateInfo = result.update
                    }
                }
            } catch (_: Throwable) {
                // 자동 확인은 알림/오류 팝업 없이 조용히 실패한다. 수동 확인은 설정 화면에서 항상 가능하다.
            }
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshState()
                refreshKnownAppUpdateVersion()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
'''
)
replace_once(
    "app/src/main/java/com/charmingcolor/shuttersoundzero/ui/main/MainScreen.kt",
    '''            Spacer(modifier = Modifier.height(24.dp))

            GroupLabel("초기 설정 및 복구")''',
    '''            availableAppUpdateVersion?.let { version ->
                Spacer(modifier = Modifier.height(24.dp))

                GroupLabel("앱 업데이트")
                SettingsCard {
                    ActionRow(
                        title = if (isAppUpdatePreparing) {
                            "v$version 업데이트 준비 중…"
                        } else {
                            "v$version 업데이트"
                        },
                        onClick = startHomeAppUpdate
                    )
                    RowDivider()
                    InfoRow(
                        title = "새 버전을 사용할 수 있습니다",
                        subtitle = "[업데이트]를 누른 뒤에만 APK를 다운로드하고 SHA-256·패키지·버전·서명을 검증합니다. 검증이 끝나면 Android 설치 화면을 열며, 최종 설치는 사용자가 직접 확인합니다."
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            GroupLabel("초기 설정 및 복구")'''
)
replace_once(
    "app/src/main/java/com/charmingcolor/shuttersoundzero/ui/main/MainScreen.kt",
    '''    if (showWifiRequiredDialog) {''',
    '''    if (isAppUpdatePreparing) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("앱 업데이트") },
            text = {
                Text(
                    if (appUpdateProgress > 0) {
                        "APK 다운로드 및 검증 중 · $appUpdateProgress%"
                    } else {
                        "최신 정식 버전을 확인하고 업데이트를 준비하고 있습니다."
                    }
                )
            },
            confirmButton = {
                TextButton(onClick = {}, enabled = false) {
                    Text(if (appUpdateProgress > 0) "$appUpdateProgress%" else "준비 중…")
                }
            },
            shape = RoundedCornerShape(20.dp),
            containerColor = MaterialTheme.colorScheme.surface
        )
    }

    appUpdateStatusMessage?.let { message ->
        AlertDialog(
            onDismissRequest = { appUpdateStatusMessage = null },
            title = { Text("앱 업데이트") },
            text = { Text(message) },
            confirmButton = {
                TextButton(onClick = { appUpdateStatusMessage = null }) { Text("확인") }
            },
            shape = RoundedCornerShape(20.dp),
            containerColor = MaterialTheme.colorScheme.surface
        )
    }

    appUpdateErrorMessage?.let { message ->
        AlertDialog(
            onDismissRequest = { appUpdateErrorMessage = null },
            title = { Text("앱 업데이트") },
            text = { Text(message) },
            confirmButton = {
                TextButton(onClick = { appUpdateErrorMessage = null }) { Text("확인") }
            },
            shape = RoundedCornerShape(20.dp),
            containerColor = MaterialTheme.colorScheme.surface
        )
    }

    if (showWifiRequiredDialog) {'''
)
replace_once(
    "app/src/main/java/com/charmingcolor/shuttersoundzero/ui/main/MainScreen.kt",
    '''// ── 헤더 ──────────────────────────────────────
''',
    '''private fun friendlyHomeUpdateError(error: Throwable): String {
    return error.message?.trim().takeUnless { it.isNullOrBlank() }
        ?: "업데이트를 준비하지 못했습니다. 네트워크 연결을 확인한 뒤 다시 시도해 주세요."
}

// ── 헤더 ──────────────────────────────────────
'''
)

# SettingsScreen: preference toggle, truthful copy, and one app tap to download+verify+open installer.
replace_once(
    "app/src/main/java/com/charmingcolor/shuttersoundzero/ui/settings/SettingsScreen.kt",
    '''    var isSoftwareUpdateCheck by remember {
        mutableStateOf(prefs.isSoftwareUpdateCheckEnabled)
    }
    var isAppLockEnabled''',
    '''    var isSoftwareUpdateCheck by remember {
        mutableStateOf(prefs.isSoftwareUpdateCheckEnabled)
    }
    var isAppUpdateAutoCheck by remember {
        mutableStateOf(prefs.isAppUpdateAutoCheckEnabled)
    }
    var isAppLockEnabled'''
)
replace_once(
    "app/src/main/java/com/charmingcolor/shuttersoundzero/ui/settings/SettingsScreen.kt",
    '''                    is AppUpdateManager.UpdateCheckResult.UpToDate -> {
                        updateInfo = null
                        verifiedUpdate = null
                        updateStatusMessage =
                            "현재 최신 버전을 사용하고 있습니다.\\n\\nv$versionName"
                    }
                    is AppUpdateManager.UpdateCheckResult.Available -> {
                        updateInfo = result.update
                        verifiedUpdate = null
                        updateDownloadProgress = 0
                    }''',
    '''                    is AppUpdateManager.UpdateCheckResult.UpToDate -> {
                        prefs.lastAppUpdateCheckAtMillis = System.currentTimeMillis()
                        prefs.knownAvailableAppUpdateVersion = null
                        updateInfo = null
                        verifiedUpdate = null
                        updateStatusMessage =
                            "현재 최신 버전을 사용하고 있습니다.\\n\\nv$versionName"
                    }
                    is AppUpdateManager.UpdateCheckResult.Available -> {
                        prefs.lastAppUpdateCheckAtMillis = System.currentTimeMillis()
                        prefs.knownAvailableAppUpdateVersion = result.update.versionName
                        updateInfo = result.update
                        verifiedUpdate = null
                        updateDownloadProgress = 0
                    }'''
)
replace_once(
    "app/src/main/java/com/charmingcolor/shuttersoundzero/ui/settings/SettingsScreen.kt",
    '''    fun downloadUpdate(update: AppUpdateManager.UpdateInfo) {
        if (isUpdateDownloading) return
        coroutineScope.launch {
            isUpdateDownloading = true
            updateDownloadProgress = 0
            updateErrorMessage = null
            installPermissionHint = false
            try {
                verifiedUpdate = AppUpdateManager.downloadAndVerify(
                    context = context,
                    update = update,
                    onProgress = { progress -> updateDownloadProgress = progress }
                )
            } catch (error: Throwable) {
                verifiedUpdate = null
                updateErrorMessage = friendlyUpdateError(error)
            } finally {
                isUpdateDownloading = false
            }
        }
    }''',
    '''    fun downloadUpdate(update: AppUpdateManager.UpdateInfo) {
        if (isUpdateDownloading) return
        if (!AppUpdateManager.canRequestPackageInstalls(context)) {
            installPermissionHint = true
            try {
                AppUpdateManager.openInstallPermissionSettings(context)
            } catch (error: Throwable) {
                updateErrorMessage = friendlyUpdateError(error)
            }
            return
        }
        coroutineScope.launch {
            isUpdateDownloading = true
            updateDownloadProgress = 0
            updateErrorMessage = null
            installPermissionHint = false
            try {
                val verified = AppUpdateManager.downloadAndVerify(
                    context = context,
                    update = update,
                    onProgress = { progress -> updateDownloadProgress = progress }
                )
                verifiedUpdate = verified
                if (!AppUpdateManager.launchInstaller(context, verified)) {
                    updateErrorMessage =
                        "Android 설치 화면을 열 수 없습니다. 기기의 설치 권한 설정을 확인해 주세요."
                }
            } catch (error: Throwable) {
                verifiedUpdate = null
                updateErrorMessage = friendlyUpdateError(error)
            } finally {
                isUpdateDownloading = false
            }
        }
    }'''
)
replace_once(
    "app/src/main/java/com/charmingcolor/shuttersoundzero/ui/settings/SettingsScreen.kt",
    '''                InfoRow(
                    title = "버전",
                    subtitle = versionName
                )
                RowDivider()
                ClickableRow(
                    title = "앱 업데이트",
                    subtitle = when {
                        isUpdateChecking -> "GitHub Releases에서 최신 버전 확인 중…"
                        updateInfo != null -> "v${updateInfo?.versionName} 사용 가능"
                        else -> "사용자가 확인할 때만 GitHub Releases에 연결"
                    },''',
    '''                InfoRow(
                    title = "버전",
                    subtitle = versionName
                )
                RowDivider()
                SwitchRow(
                    title = "앱 업데이트 자동 확인",
                    subtitle = "앱을 열 때 하루 한 번 이하로 최신 정식 버전만 확인 · APK는 자동 다운로드하지 않음",
                    checked = isAppUpdateAutoCheck,
                    onCheckedChange = { enabled ->
                        isAppUpdateAutoCheck = enabled
                        prefs.isAppUpdateAutoCheckEnabled = enabled
                        if (enabled) {
                            // 다음 홈 화면 진입 시 즉시 한 번 확인한 뒤 24시간 간격을 적용한다.
                            prefs.lastAppUpdateCheckAtMillis = 0L
                        }
                    }
                )
                RowDivider()
                ClickableRow(
                    title = "앱 업데이트",
                    subtitle = when {
                        isUpdateChecking -> "GitHub Releases에서 최신 버전 확인 중…"
                        updateInfo != null -> "v${updateInfo?.versionName} 사용 가능 · 눌러 변경사항 확인"
                        isAppUpdateAutoCheck -> "자동 확인 켜짐 · 눌러 지금 확인"
                        else -> "자동 확인 꺼짐 · 눌러 지금 확인"
                    },'''
)
replace_once(
    "app/src/main/java/com/charmingcolor/shuttersoundzero/ui/settings/SettingsScreen.kt",
    '''                                text = "Android의 [이 출처 허용]을 켠 뒤 이 화면으로 돌아와 [설치]를 다시 눌러 주세요.",''',
    '''                                text = "Android의 [이 출처 허용]을 켠 뒤 이 화면으로 돌아와 [업데이트]를 다시 눌러 주세요.",'''
)
replace_once(
    "app/src/main/java/com/charmingcolor/shuttersoundzero/ui/settings/SettingsScreen.kt",
    '''                                Text("업데이트 다운로드")''',
    '''                                Text("업데이트")'''
)

# Tests for the automatic-check cadence.
replace_once(
    "app/src/test/java/com/charmingcolor/shuttersoundzero/update/AppUpdateManagerTest.kt",
    '''    @Test
    fun invalidSha256_isRejected() {
        assertNull(AppUpdateManager.parseSha256("not-a-checksum"))
    }
}''',
    '''    @Test
    fun invalidSha256_isRejected() {
        assertNull(AppUpdateManager.parseSha256("not-a-checksum"))
    }

    @Test
    fun automaticCheck_disabledIsNeverDue() {
        assertFalse(
            AppUpdateManager.isAutomaticCheckDue(
                enabled = false,
                lastCheckAtMillis = 0L,
                nowMillis = 1_000L
            )
        )
    }

    @Test
    fun automaticCheck_firstRunIsDue() {
        assertTrue(
            AppUpdateManager.isAutomaticCheckDue(
                enabled = true,
                lastCheckAtMillis = 0L,
                nowMillis = 1_000L
            )
        )
    }

    @Test
    fun automaticCheck_waitsForTwentyFourHours() {
        val last = 10_000L
        val interval = AppUpdateManager.AUTOMATIC_CHECK_INTERVAL_MILLIS
        assertFalse(AppUpdateManager.isAutomaticCheckDue(true, last, last + interval - 1L))
        assertTrue(AppUpdateManager.isAutomaticCheckDue(true, last, last + interval))
    }

    @Test
    fun automaticCheck_clockRollbackAllowsFreshBaseline() {
        assertTrue(
            AppUpdateManager.isAutomaticCheckDue(
                enabled = true,
                lastCheckAtMillis = 20_000L,
                nowMillis = 10_000L
            )
        )
    }
}'''
)

# README: describe the semi-automatic behavior and keep button names aligned with the UI.
readme = Path("README.md")
text = readme.read_text(encoding="utf-8")
text = text.replace("메인 화면에서 [권한 요청]을 다시 진행해야 합니다.", "메인 화면에서 [1회 설정 시작]을 다시 진행해야 합니다.")
text = text.replace("앱 메인 화면의 [권한 요청] 버튼을 누릅니다.", "앱 메인 화면의 [1회 설정 시작] 버튼을 누릅니다.")
old_feature = '''8. **⬆️ 안전한 앱 업데이트 확인·설치**
   * 설정의 [앱 업데이트]를 사용자가 직접 눌렀을 때만 GitHub Releases에서 최신 정식 버전을 확인합니다.
   * 새 버전이 있으면 릴리즈 노트를 보여주고 APK를 직접 다운로드할 수 있습니다.
   * 설치 전에 **SHA-256, 패키지명, versionCode/versionName, 현재 설치 앱과의 서명 인증서 일치 여부**를 모두 검증합니다.
   * 모든 검증을 통과한 APK만 Android 패키지 설치 화면으로 전달합니다.
'''
new_feature = '''8. **⬆️ 반자동 앱 업데이트 확인·설치**
   * 기본값으로 앱을 열 때 **24시간에 한 번 이하**로 GitHub Releases의 최신 정식 버전만 조용히 확인합니다.
   * 새 버전이 있으면 홈 화면에 [업데이트] 동작을 표시하며, 사용자가 누르기 전에는 APK를 다운로드하지 않습니다.
   * [업데이트]를 누르면 APK 다운로드와 함께 **SHA-256, 패키지명, versionCode/versionName, 현재 설치 앱과의 서명 인증서 일치 여부**를 자동 검증합니다.
   * 모든 검증을 통과한 APK만 Android 패키지 설치 화면으로 전달하며, 최종 설치 여부는 사용자가 직접 확인합니다.
   * 자동 확인은 설정에서 끌 수 있고, [앱 업데이트]를 눌러 언제든 수동 확인할 수 있습니다.
'''
if old_feature not in text:
    raise SystemExit("README feature block not found")
text = text.replace(old_feature, new_feature, 1)
old_update = '''## ⬆️ 앱 업데이트

설정 화면의 **[앱 업데이트]**를 누르면 GitHub의 최신 정식 Release를 확인합니다. 앱 실행 시 또는 백그라운드에서 자동으로 업데이트 서버에 접속하지 않습니다.

새 버전이 발견되면 다음 순서로 처리합니다.

1. 최신 정식 Release의 버전과 릴리즈 노트 확인
2. 사용자가 [업데이트 다운로드]를 선택한 경우에만 APK 다운로드
3. GitHub Release의 `.sha256` 파일과 실제 APK SHA-256 비교
4. APK의 패키지명과 `versionCode` / `versionName` 확인
5. 현재 설치된 ShutterSoundZero와 APK의 서명 인증서 일치 여부 확인
6. 모든 검증을 통과한 경우에만 Android 설치 화면 실행

Android의 보안 정책에 따라 최초 1회 **[이 출처 허용]** 설정을 요구할 수 있습니다. 이 권한은 사용자가 직접 Android 설정에서 허용하며, 앱이 백그라운드에서 자동 설치를 수행하지 않습니다.
'''
new_update = '''## ⬆️ 앱 업데이트

ShutterSoundZero는 **반자동 업데이트** 방식을 사용합니다. 기본값으로 앱을 열 때 24시간에 한 번 이하로 GitHub의 최신 정식 Release 메타데이터만 조용히 확인하며, 확인 실패나 최신 버전 상태에서는 별도 팝업을 띄우지 않습니다. 백그라운드에서 주기적으로 실행되는 업데이트 서비스는 사용하지 않습니다.

새 버전이 발견되면 홈 화면에 업데이트 동작을 표시합니다. 사용자가 **[업데이트]**를 누른 뒤에만 다음 순서가 시작됩니다.

1. 최신 정식 Release 정보 재확인
2. APK 다운로드
3. GitHub Release의 `.sha256` 파일과 실제 APK SHA-256 비교
4. APK의 패키지명과 `versionCode` / `versionName` 확인
5. 현재 설치된 ShutterSoundZero와 APK의 서명 인증서 일치 여부 확인
6. 모든 검증을 통과한 경우 Android 설치 화면 실행
7. 사용자가 Android 설치 화면에서 최종 설치 여부 확인

설정의 **[앱 업데이트 자동 확인]**을 끄면 앱 실행 시 GitHub Releases에 자동으로 연결하지 않습니다. 자동 확인을 꺼도 **[앱 업데이트]**를 눌러 언제든 수동 확인할 수 있습니다.

Android의 보안 정책에 따라 최초 1회 **[이 출처 허용]** 설정을 요구할 수 있습니다. 이 권한 역시 사용자가 직접 Android 설정에서 허용해야 하며, 앱은 APK를 자동 다운로드하거나 백그라운드에서 자동 설치하지 않습니다.
'''
if old_update not in text:
    raise SystemExit("README update block not found")
text = text.replace(old_update, new_update, 1)
readme.write_text(text, encoding="utf-8")

# Manifest comment should no longer claim update checks are manual-only.
replace_once(
    "app/src/main/AndroidManifest.xml",
    '''    <!-- 로컬 무선 디버깅 소켓, Wi-Fi 연결 확인, mDNS 수신 및 수동 앱 업데이트 확인 -->''',
    '''    <!-- 로컬 무선 디버깅 소켓, Wi-Fi 연결 확인, mDNS 수신 및 앱 업데이트 자동·수동 확인 -->'''
)

print("semi-auto update patch applied")
