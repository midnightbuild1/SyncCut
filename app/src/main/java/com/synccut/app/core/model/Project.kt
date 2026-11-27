package com.synccut.app.core.model

import java.util.UUID

enum class AspectRatio(val widthRatio: Int, val heightRatio: Int, val label: String) {
    RATIO_16_9(16, 9, "16:9 (Landscape)"),
    RATIO_9_16(9, 16, "9:16 (Vertical)"),
    RATIO_1_1(1, 1, "1:1 (Square)"),
    RATIO_4_5(4, 5, "4:5 (Portrait)"),
    CUSTOM(0, 0, "Custom")
}

enum class Resolution(val width: Int, val height: Int, val label: String) {
    AUTO(0, 0, "Auto (Match Source)"),
    P720(1280, 720, "720p HD"),
    P1080(1920, 1080, "1080p Full HD"),
    P1440(2560, 1440, "1440p 2K"),
    P2160_4K(3840, 2160, "4K UHD")
}

data class AudioSettings(
    val sampleRate: Int = 48000,
    val channelCount: Int = 2
)

data class Project(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val aspectRatio: AspectRatio = AspectRatio.RATIO_16_9,
    val resolution: Resolution = Resolution.P1080,
    val frameRate: Rational = Rational.FPS_30,
    val audioSettings: AudioSettings = AudioSettings()
)
