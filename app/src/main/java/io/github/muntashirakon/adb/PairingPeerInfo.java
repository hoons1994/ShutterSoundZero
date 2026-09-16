// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.muntashirakon.adb;

import java.io.IOException;

/** The authenticated server peer-info is a type-1, nonempty NUL-terminated ADB device GUID. */
final class PairingPeerInfo {
    private PairingPeerInfo() { }
    static void requireDeviceGuid(byte[] info) throws IOException {
        if (info == null || info.length != 8192 || info[0] != 1) {
            throw new IOException("Invalid authenticated pairing peer type or size");
        }
        int end = 1;
        while (end < info.length && info[end] != 0) {
            int value = info[end] & 255;
            if (value < 0x21 || value > 0x7e) throw new IOException("Invalid pairing device GUID");
            end++;
        }
        if (end == 1 || end == info.length) throw new IOException("Invalid pairing device GUID terminator");
    }
}
