from pathlib import Path

path = Path('app/src/main/java/com/charmingcolor/shuttersoundzero/ui/main/MainScreen.kt')
text = path.read_text(encoding='utf-8')

old = '''    var availableAppUpdateVersion by remember { mutableStateOf<String?>(null) }
    var cachedAppUpdateInfo by remember { mutableStateOf<AppUpdateManager.UpdateInfo?>(null) }
    var isAppUpdatePreparing by remember { mutableStateOf(false) }
'''
new = '''    var availableAppUpdateVersion by remember { mutableStateOf<String?>(null) }
    var isAppUpdatePreparing by remember { mutableStateOf(false) }
    var isSilentAppUpdateChecking by remember { mutableStateOf(false) }
'''
if old not in text:
    raise SystemExit('state block not found')
text = text.replace(old, new, 1)

old = '''                        val update = cachedAppUpdateInfo ?: when (val result = AppUpdateManager.checkForUpdate(context)) {
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
'''
new = '''                        val update = when (val result = AppUpdateManager.checkForUpdate(context)) {
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
                                result.update
                            }
                        }
'''
if old not in text:
    raise SystemExit('one-tap check block not found')
text = text.replace(old, new, 1)

start = text.index('    LaunchedEffect(Unit) {\n        refreshKnownAppUpdateVersion()')
end_marker = '''    LaunchedEffect(uiState.infoMessage, uiState.errorMessage) {'''
end = text.index(end_marker, start)
old_block = text[start:end]
new_block = '''    fun checkAppUpdateSilentlyIfDue() {
        if (isAppUpdatePreparing || isSilentAppUpdateChecking) return
        val now = System.currentTimeMillis()
        if (
            !AppUpdateManager.isAutomaticCheckDue(
                enabled = prefs.isAppUpdateAutoCheckEnabled,
                lastCheckAtMillis = prefs.lastAppUpdateCheckAtMillis,
                nowMillis = now
            )
        ) {
            return
        }

        // 네트워크가 끊겨 있어도 앱을 다시 열 때마다 반복 요청하지 않도록 시도 시점을 먼저 기록한다.
        prefs.lastAppUpdateCheckAtMillis = now
        isSilentAppUpdateChecking = true
        updateScope.launch {
            try {
                when (val result = AppUpdateManager.checkForUpdate(context)) {
                    is AppUpdateManager.UpdateCheckResult.UpToDate -> {
                        prefs.knownAvailableAppUpdateVersion = null
                        availableAppUpdateVersion = null
                    }
                    is AppUpdateManager.UpdateCheckResult.Available -> {
                        prefs.knownAvailableAppUpdateVersion = result.update.versionName
                        availableAppUpdateVersion = result.update.versionName
                    }
                }
            } catch (_: Throwable) {
                // 자동 확인은 알림/오류 팝업 없이 조용히 실패한다. 수동 확인은 설정 화면에서 항상 가능하다.
            } finally {
                isSilentAppUpdateChecking = false
            }
        }
    }

    LaunchedEffect(Unit) {
        refreshKnownAppUpdateVersion()
        checkAppUpdateSilentlyIfDue()
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshState()
                refreshKnownAppUpdateVersion()
                checkAppUpdateSilentlyIfDue()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

'''
text = text[:start] + new_block + text[end:]
path.write_text(text, encoding='utf-8')
print('semi-auto update resume/fresh-check flow refined')
