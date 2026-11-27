package com.synccut.app.core

import com.google.common.truth.Truth.assertThat
import com.synccut.app.core.model.Rational
import com.synccut.app.core.model.TimeCode
import org.junit.Test

class TimeCodeTest {

    @Test
    fun testFormatSmpte() {
        val fps = Rational.FPS_30
        assertThat(TimeCode.formatSmpte(0L, fps)).isEqualTo("00:00:00:00")
        assertThat(TimeCode.formatSmpte(1_000_000L, fps)).isEqualTo("00:00:01:00")
        assertThat(TimeCode.formatSmpte(65_500_000L, fps)).isEqualTo("00:01:05:15")
    }

    @Test
    fun testFormatMinutesSeconds() {
        assertThat(TimeCode.formatMinutesSeconds(0L)).isEqualTo("00:00.000")
        assertThat(TimeCode.formatMinutesSeconds(1_500_000L)).isEqualTo("00:01.500")
        assertThat(TimeCode.formatMinutesSeconds(125_250_000L)).isEqualTo("02:05.250")
    }

    @Test
    fun testFormatDuration() {
        assertThat(TimeCode.formatDuration(500_000L)).isEqualTo("0.5s")
        assertThat(TimeCode.formatDuration(12_400_000L)).isEqualTo("12.4s")
        assertThat(TimeCode.formatDuration(75_000_000L)).isEqualTo("1m 15s")
    }
}
