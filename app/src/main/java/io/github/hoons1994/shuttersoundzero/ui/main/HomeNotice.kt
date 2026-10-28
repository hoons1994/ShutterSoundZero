package io.github.hoons1994.shuttersoundzero.ui.main

internal enum class HomeNotice { WIRELESS_DEBUGGING, REAPPLY, CHECK_CAMERA }

internal fun preferredHomeNotice(status: HomeStatus): HomeNotice = when (status) {
    HomeStatus.SETUP_REQUIRED, HomeStatus.STATE_UNKNOWN -> HomeNotice.WIRELESS_DEBUGGING
    HomeStatus.REAPPLY_REQUIRED -> HomeNotice.REAPPLY
    HomeStatus.READY, HomeStatus.APPLYING -> HomeNotice.CHECK_CAMERA
}
