package com.synccut.app.core

import com.google.common.truth.Truth.assertThat
import com.synccut.app.core.model.Rational
import org.junit.Test

class RationalTest {

    @Test
    fun testFramesToMicroseconds() {
        val fps30 = Rational.FPS_30
        assertThat(fps30.framesToMicroseconds(0)).isEqualTo(0L)
        assertThat(fps30.framesToMicroseconds(30)).isEqualTo(1_000_000L)
        assertThat(fps30.framesToMicroseconds(60)).isEqualTo(2_000_000L)

        val fps24 = Rational.FPS_24
        assertThat(fps24.framesToMicroseconds(24)).isEqualTo(1_000_000L)
    }

    @Test
    fun testMicrosecondsToFrames() {
        val fps30 = Rational.FPS_30
        assertThat(fps30.microsecondsToFrames(0L)).isEqualTo(0L)
        assertThat(fps30.microsecondsToFrames(1_000_000L)).isEqualTo(30L)
        assertThat(fps30.microsecondsToFrames(500_000L)).isEqualTo(15L)
    }

    @Test
    fun testFromDouble() {
        assertThat(Rational.fromDouble(29.97)).isEqualTo(Rational.FPS_29_97)
        assertThat(Rational.fromDouble(24.0)).isEqualTo(Rational.FPS_24)
        assertThat(Rational.fromDouble(60.0)).isEqualTo(Rational.FPS_60)
    }
}
