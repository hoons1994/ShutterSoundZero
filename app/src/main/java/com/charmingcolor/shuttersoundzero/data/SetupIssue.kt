package com.charmingcolor.shuttersoundzero.data

/**
 * 1회 설정 흐름에서 사용자가 다시 확인해야 하는 단계.
 *
 * 화면에서는 기술적인 실패 원인보다 사용자가 다시 진행할 위치를 알려주는 데 사용한다.
 */
enum class SetupIssue {
    PAIRING_DISCOVERY,
    PAIRING_CODE,
    CAMERA_APPLY
}
