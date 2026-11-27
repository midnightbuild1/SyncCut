package com.synccut.app.core.model

import java.util.Locale

/**
 * Handles presentation time formatting and conversions between microseconds (us),
 * milliseconds (ms), and frame numbers using project frame rate.
 */
object TimeCode {

    /**
     * Formats microseconds into standard SMPTE timecode HH:MM:SS:FF
     */
    fun formatSmpte(microseconds: Long, fps: Rational): String {
        val nonNegativeUs = microseconds.coerceAtLeast(0L)
        val totalSeconds = nonNegativeUs / 1_000_000L
        val hours = totalSeconds / 3600L
        val minutes = (totalSeconds % 3600L) / 60L
        val seconds = totalSeconds % 60L

        val remUs = nonNegativeUs % 1_000_000L
        val frames = fps.microsecondsToFrames(remUs)

        return String.format(Locale.US, "%02d:%02d:%02d:%02d", hours, minutes, seconds, frames)
    }

    /**
     * Formats microseconds into MM:SS.mmm (useful for compact mobile timeline headers)
     */
    fun formatMinutesSeconds(microseconds: Long): String {
        val nonNegativeUs = microseconds.coerceAtLeast(0L)
        val totalSeconds = nonNegativeUs / 1_000_000L
        val minutes = totalSeconds / 60L
        val seconds = totalSeconds % 60L
        val millis = (nonNegativeUs % 1_000_000L) / 1_000L

        return String.format(Locale.US, "%02d:%02d.%03d", minutes, seconds, millis)
    }

    /**
     * Formats microseconds into human readable string e.g. "1m 24s" or "3.5s"
     */
    fun formatDuration(microseconds: Long): String {
        val nonNegativeUs = microseconds.coerceAtLeast(0L)
        val totalSeconds = nonNegativeUs.toDouble() / 1_000_000.0
        return if (totalSeconds < 60) {
            String.format(Locale.US, "%.1fs", totalSeconds)
        } else {
            val mins = (totalSeconds / 60).toInt()
            val secs = (totalSeconds % 60).toInt()
            "${mins}m ${secs}s"
        }
    }
}
