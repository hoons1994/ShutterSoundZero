package io.github.hoons1994.shuttersoundzero.diagnostics

import org.junit.Assert.*
import org.junit.Test

class AudioDiagnosticSnapshotTest {
    @Test fun zeroSystemVolumeInSoundModeIsDistinctFromRingerMute() {
        val report = AudioDiagnosticSnapshot(current = 0, min = 0, max = 15,
            ringerMode = 2, streamMuted = false, fixed = false, interruptionFilter = 1).format()
        assertTrue(report.contains("시스템 음량: 0"))
        assertTrue(report.contains("소리 모드: 소리"))
        assertTrue(report.contains("시스템 스트림 음소거: 아니요"))
        assertTrue(report.contains("방해 금지: 아니요"))
    }

    @Test fun blockedStreamInSoundModeIsVisibleWithoutAssumingRingerMute() {
        val report = AudioDiagnosticSnapshot(current = 0, ringerMode = 2,
            streamMuted = true, interruptionFilter = 2).format()
        assertTrue(report.contains("소리 모드: 소리"))
        assertTrue(report.contains("시스템 스트림 음소거: 예"))
        assertTrue(report.contains("방해 금지: 예"))
    }

    @Test fun failedQueriesRemainUnknownInsteadOfReportingZeroOrOff() {
        val report = AudioDiagnosticSnapshot().format()
        assertTrue(report.contains("시스템 음량: 알 수 없음"))
        assertTrue(report.contains("음량 고정: 알 수 없음"))
        assertTrue(report.contains("방해 금지: 알 수 없음"))
        assertFalse(report.contains("아니요"))
    }
}
