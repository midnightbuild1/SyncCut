package com.synccut.app.engine.playback

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.synccut.app.core.model.Rational
import com.synccut.app.core.model.SourceMedia
import com.synccut.app.core.model.TimelineClip
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class PlaybackState(
    val isPlaying: Boolean = false,
    val currentPositionUs: Long = 0L,
    val totalDurationUs: Long = 0L,
    val isBuffering: Boolean = false,
    val activeAngleMediaId: String? = null,
    val errorMessage: String? = null
)

@OptIn(UnstableApi::class)
class PlaybackEngine(
    private val context: Context
) {
    private var exoPlayer: ExoPlayer? = null
    private val _playbackState = MutableStateFlow(PlaybackState())
    val playbackState: StateFlow<PlaybackState> = _playbackState.asStateFlow()

    private var activeClips: List<TimelineClip> = emptyList()
    private var activeMediaMap: Map<String, SourceMedia> = emptyMap()
    private var currentActiveMediaId: String? = null
    private var projectFps: Rational = Rational.FPS_30

    private val scope = CoroutineScope(Dispatchers.Main)
    private var progressJob: Job? = null

    init {
        initialize()
    }

    fun initialize() {
        if (exoPlayer != null) return

        exoPlayer = ExoPlayer.Builder(context)
            .setSeekParameters(androidx.media3.exoplayer.SeekParameters.CLOSEST_SYNC)
            .build().apply {
                repeatMode = Player.REPEAT_MODE_OFF
                playWhenReady = false
                addListener(object : Player.Listener {
                    override fun onIsPlayingChanged(isPlaying: Boolean) {
                        updateState()
                        if (isPlaying) {
                            startProgressPolling()
                        } else {
                            stopProgressPolling()
                        }
                    }

                    override fun onPlaybackStateChanged(state: Int) {
                        updateState()
                    }

                    override fun onPlayerError(error: PlaybackException) {
                        Log.e("PlaybackEngine", "ExoPlayer Error: ${error.errorCodeName}", error)
                        _playbackState.value = _playbackState.value.copy(
                            isPlaying = false,
                            errorMessage = error.localizedMessage ?: "Playback error (${error.errorCodeName})"
                        )
                    }
                })
            }
    }

    private fun startProgressPolling() {
        progressJob?.cancel()
        progressJob = scope.launch {
            while (isActive) {
                updateState()
                delay(33) // ~30 fps updates
            }
        }
    }

    private fun stopProgressPolling() {
        progressJob?.cancel()
        progressJob = null
        updateState()
    }

    /**
     * Prepares multi-cam playback for the timeline.
     * Selects [targetMediaId] as the active preview camera angle, or the first available video clip.
     */
    fun prepareMultiCam(
        clips: List<TimelineClip>,
        mediaMap: Map<String, SourceMedia>,
        fps: Rational,
        targetMediaId: String? = null
    ) {
        initialize()
        val player = exoPlayer ?: return

        activeClips = clips.filter { it.isEnabled }.sortedBy { it.timelineStartUs }
        activeMediaMap = mediaMap
        projectFps = fps

        if (activeClips.isEmpty()) {
            player.clearMediaItems()
            _playbackState.value = PlaybackState()
            return
        }

        // Determine which media to preview
        val mediaIdToPlay = targetMediaId
            ?: currentActiveMediaId
            ?: activeClips.firstOrNull()?.sourceMediaId

        currentActiveMediaId = mediaIdToPlay
        val media = activeMediaMap[mediaIdToPlay]

        val maxEndUs = activeClips.maxOfOrNull { it.timelineEndUs } ?: 10_000_000L

        if (media != null) {
            val mediaItem = MediaItem.fromUri(Uri.parse(media.uri))
            player.setMediaItem(mediaItem)
            player.prepare()
            _playbackState.value = _playbackState.value.copy(
                totalDurationUs = maxEndUs,
                activeAngleMediaId = mediaIdToPlay,
                errorMessage = null
            )
        }
    }

    fun switchCameraAngle(mediaId: String) {
        if (currentActiveMediaId == mediaId) return
        val currentPosUs = _playbackState.value.currentPositionUs
        val wasPlaying = _playbackState.value.isPlaying

        currentActiveMediaId = mediaId
        val media = activeMediaMap[mediaId] ?: return
        val player = exoPlayer ?: return

        // Find the clip for this angle
        val clip = activeClips.firstOrNull { it.sourceMediaId == mediaId }
        val sourceTimeUs = if (clip != null) {
            (currentPosUs - clip.timelineStartUs + clip.sourceInUs).coerceIn(0L, clip.durationUs)
        } else {
            currentPosUs.coerceIn(0L, media.durationUs)
        }

        val mediaItem = MediaItem.fromUri(Uri.parse(media.uri))
        player.setMediaItem(mediaItem)
        player.prepare()
        player.seekTo(sourceTimeUs / 1000L)
        if (wasPlaying) {
            player.play()
        }

        _playbackState.value = _playbackState.value.copy(
            activeAngleMediaId = mediaId,
            currentPositionUs = currentPosUs
        )
    }

    fun play() {
        initialize()
        exoPlayer?.play()
    }

    fun pause() {
        exoPlayer?.pause()
    }

    fun seekToUs(positionUs: Long) {
        val player = exoPlayer ?: return
        val boundedUs = positionUs.coerceAtLeast(0L)

        val clip = activeClips.firstOrNull { it.sourceMediaId == currentActiveMediaId }
        val targetSeekMs = if (clip != null) {
            val sourceUs = boundedUs - clip.timelineStartUs + clip.sourceInUs
            (sourceUs / 1000L).coerceAtLeast(0L)
        } else {
            boundedUs / 1000L
        }

        player.seekTo(targetSeekMs)
        _playbackState.value = _playbackState.value.copy(currentPositionUs = boundedUs)
    }

    fun stepFrame(forward: Boolean) {
        val frameDurationUs = projectFps.framesToMicroseconds(1)
        val currentUs = _playbackState.value.currentPositionUs
        val targetUs = if (forward) currentUs + frameDurationUs else (currentUs - frameDurationUs).coerceAtLeast(0L)
        seekToUs(targetUs)
    }

    fun getPlayer(): ExoPlayer? = exoPlayer

    fun release() {
        stopProgressPolling()
        exoPlayer?.release()
        exoPlayer = null
    }

    private fun updateState() {
        val player = exoPlayer ?: return
        val currentMs = player.currentPosition

        val clip = activeClips.firstOrNull { it.sourceMediaId == currentActiveMediaId }
        val timelineUs = if (clip != null) {
            (currentMs * 1000L) + clip.timelineStartUs - clip.sourceInUs
        } else {
            currentMs * 1000L
        }

        val isBuffering = player.playbackState == Player.STATE_BUFFERING
        _playbackState.value = _playbackState.value.copy(
            isPlaying = player.isPlaying,
            currentPositionUs = timelineUs.coerceAtLeast(0L),
            isBuffering = isBuffering,
            activeAngleMediaId = currentActiveMediaId
        )
    }
}
