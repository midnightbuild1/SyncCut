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
    val currentClipIndex: Int = 0,
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

                    override fun onPositionDiscontinuity(
                        oldPosition: Player.PositionInfo,
                        newPosition: Player.PositionInfo,
                        reason: Int
                    ) {
                        updateState()
                    }

                    override fun onPlayerError(error: PlaybackException) {
                        Log.e("PlaybackEngine", "ExoPlayer Error: ${error.errorCodeName}", error)
                        _playbackState.value = _playbackState.value.copy(
                            isPlaying = false,
                            errorMessage = error.localizedMessage
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
                delay(33) // ~30 fps position updates
            }
        }
    }

    private fun stopProgressPolling() {
        progressJob?.cancel()
        progressJob = null
        updateState()
    }

    fun prepareTimeline(
        clips: List<TimelineClip>,
        mediaMap: Map<String, SourceMedia>,
        fps: Rational
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

        var totalUs = 0L
        val mediaItems = mutableListOf<MediaItem>()

        for (clip in activeClips) {
            val media = activeMediaMap[clip.sourceMediaId] ?: continue
            val clippingConfig = MediaItem.ClippingConfiguration.Builder()
                .setStartPositionUs(clip.sourceInUs)
                .setEndPositionUs(clip.sourceOutUs)
                .build()

            val mediaItem = MediaItem.Builder()
                .setUri(Uri.parse(media.uri))
                .setClippingConfiguration(clippingConfig)
                .build()

            mediaItems.add(mediaItem)
            totalUs += clip.durationUs
        }

        player.setMediaItems(mediaItems)
        player.prepare()
        _playbackState.value = _playbackState.value.copy(
            totalDurationUs = totalUs,
            errorMessage = null
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
        if (activeClips.isEmpty()) return

        var accumulatedUs = 0L
        for (index in activeClips.indices) {
            val clip = activeClips[index]
            val clipDurationUs = clip.durationUs
            if (positionUs >= accumulatedUs && positionUs < accumulatedUs + clipDurationUs) {
                val clipOffsetUs = positionUs - accumulatedUs
                player.seekTo(index, clipOffsetUs / 1000L)
                updateState()
                return
            }
            accumulatedUs += clipDurationUs
        }

        if (activeClips.isNotEmpty()) {
            val lastIdx = activeClips.size - 1
            player.seekTo(lastIdx, activeClips[lastIdx].durationUs / 1000L)
            updateState()
        }
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
        val currentWindowIndex = player.currentMediaItemIndex
        val currentWindowPositionMs = player.currentPosition

        var timelinePosUs = 0L
        for (i in 0 until currentWindowIndex.coerceAtMost(activeClips.size)) {
            timelinePosUs += activeClips[i].durationUs
        }
        timelinePosUs += currentWindowPositionMs * 1000L

        val isBuffering = player.playbackState == Player.STATE_BUFFERING
        _playbackState.value = _playbackState.value.copy(
            isPlaying = player.isPlaying,
            currentPositionUs = timelinePosUs,
            isBuffering = isBuffering,
            currentClipIndex = currentWindowIndex
        )
    }
}
