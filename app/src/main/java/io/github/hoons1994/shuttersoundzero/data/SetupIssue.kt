package io.github.hoons1994.shuttersoundzero.data

/**
 * 1회 설정 흐름에서 사용자가 다시 확인해야 하는 단계 또는 연결 조건.
 *
 * 화면에서는 내부 오류 대신 사용자가 취할 다음 조치를 안내하는 데 사용한다.
 */
enum class SetupIssue {
    LOCAL_NETWORK_PERMISSION,
    PAIRING_DISCOVERY,
    PAIRING_CODE,
    PAIRING_CONNECTION,
    PAIRING_TIMEOUT,
    CAMERA_APPLY
}
