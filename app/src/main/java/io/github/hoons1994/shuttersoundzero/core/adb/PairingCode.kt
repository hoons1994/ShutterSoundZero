package io.github.hoons1994.shuttersoundzero.core.adb

/** 무선 ADB 페어링 화면에 표시되는 코드는 ASCII 숫자 6자리다. */
internal object PairingCode {
    fun isValid(code: String): Boolean =
        code.length == 6 && code.all { it in '0'..'9' }
}
