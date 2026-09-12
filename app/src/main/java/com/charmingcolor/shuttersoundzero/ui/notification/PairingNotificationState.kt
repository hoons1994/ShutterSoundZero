package com.charmingcolor.shuttersoundzero.ui.notification

/**
 * Pairing workflow states that can surface a user-facing notification.
 *
 * The service reports meaning only; all copy stays in [PairingNotificationHelper] so wording can
 * evolve without coupling workflow logic to presentation strings.
 */
enum class PairingNotificationState {
    DEVELOPER_OPTIONS_READY,
    DISCOVERY_START_FAILED,
    INVALID_PAIRING_CODE,
    DISCOVERY_WAITING,
    CAMERA_APPLY_FAILED,
    PAIRING_FAILED,
    PAIRING_TIMEOUT,
    PAIRING_ERROR
}
