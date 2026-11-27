package com.synccut.app.core.model

/**
 * Exact fractional representation of a number, used for frame rates (e.g. 30000/1001 for 29.97 fps)
 * to prevent floating point rounding drift over time.
 */
data class Rational(
    val numerator: Long,
    val denominator: Long
) {
    init {
        require(denominator != 0L) { "Denominator cannot be zero" }
    }

    val asDouble: Double
        get() = numerator.toDouble() / denominator.toDouble()

    fun framesToMicroseconds(frames: Long): Long {
        // durationUs = frames * 1_000_000 * denominator / numerator
        return (frames * 1_000_000L * denominator) / numerator
    }

    fun microsecondsToFrames(microseconds: Long): Long {
        // frames = (microseconds * numerator) / (1_000_000 * denominator)
        return (microseconds * numerator) / (1_000_000L * denominator)
    }

    companion object {
        val FPS_24 = Rational(24, 1)
        val FPS_25 = Rational(25, 1)
        val FPS_30 = Rational(30, 1)
        val FPS_29_97 = Rational(30000, 1001)
        val FPS_50 = Rational(50, 1)
        val FPS_60 = Rational(60, 1)
        val FPS_59_94 = Rational(60000, 1001)

        fun fromDouble(fps: Double): Rational {
            return when {
                kotlin.math.abs(fps - 23.976) < 0.01 -> Rational(24000, 1001)
                kotlin.math.abs(fps - 24.0) < 0.01 -> FPS_24
                kotlin.math.abs(fps - 25.0) < 0.01 -> FPS_25
                kotlin.math.abs(fps - 29.97) < 0.01 -> FPS_29_97
                kotlin.math.abs(fps - 30.0) < 0.01 -> FPS_30
                kotlin.math.abs(fps - 50.0) < 0.01 -> FPS_50
                kotlin.math.abs(fps - 59.94) < 0.01 -> FPS_59_94
                kotlin.math.abs(fps - 60.0) < 0.01 -> FPS_60
                else -> {
                    val rounded = kotlin.math.round(fps).toLong().coerceAtLeast(1L)
                    Rational(rounded, 1L)
                }
            }
        }
    }
}
