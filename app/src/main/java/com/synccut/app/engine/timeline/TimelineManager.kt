package com.synccut.app.engine.timeline

import com.synccut.app.core.model.TimelineClip
import com.synccut.app.core.model.TimelineTrack
import com.synccut.app.core.model.TrackType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

data class TimelineState(
    val tracks: List<TimelineTrack> = emptyList(),
    val clips: List<TimelineClip> = emptyList(),
    val playheadUs: Long = 0L,
    val selectedClipId: String? = null,
    val canUndo: Boolean = false,
    val canRedo: Boolean = false
)

private data class TimelineSnapshot(
    val tracks: List<TimelineTrack>,
    val clips: List<TimelineClip>
)

class TimelineManager(
    val projectId: String
) {
    private val _state = MutableStateFlow(TimelineState())
    val state: StateFlow<TimelineState> = _state.asStateFlow()

    private val undoStack = mutableListOf<TimelineSnapshot>()
    private val redoStack = mutableListOf<TimelineSnapshot>()
    private val maxHistory = 50

    fun initialize(tracks: List<TimelineTrack>, clips: List<TimelineClip>) {
        val initialTracks = if (tracks.isEmpty()) {
            listOf(
                TimelineTrack(id = UUID.randomUUID().toString(), projectId = projectId, type = TrackType.VIDEO, order = 0),
                TimelineTrack(id = UUID.randomUUID().toString(), projectId = projectId, type = TrackType.AUDIO, order = 1)
            )
        } else tracks

        _state.value = TimelineState(
            tracks = initialTracks,
            clips = clips.sortedBy { it.timelineStartUs },
            playheadUs = 0L
        )
        undoStack.clear()
        redoStack.clear()
        updateHistoryFlags()
    }

    private fun pushSnapshot() {
        val snapshot = TimelineSnapshot(
            tracks = _state.value.tracks.map { it.copy() },
            clips = _state.value.clips.map { it.copy() }
        )
        undoStack.add(snapshot)
        if (undoStack.size > maxHistory) {
            undoStack.removeAt(0)
        }
        redoStack.clear()
        updateHistoryFlags()
    }

    private fun updateHistoryFlags() {
        _state.value = _state.value.copy(
            canUndo = undoStack.isNotEmpty(),
            canRedo = redoStack.isNotEmpty()
        )
    }

    fun setPlayhead(positionUs: Long) {
        _state.value = _state.value.copy(playheadUs = positionUs.coerceAtLeast(0L))
    }

    fun selectClip(clipId: String?) {
        _state.value = _state.value.copy(selectedClipId = clipId)
    }

    /**
     * Splits the currently selected clip (or clip under playhead) at the current playhead position.
     */
    fun splitAtPlayhead(): Boolean {
        val playhead = _state.value.playheadUs
        val clips = _state.value.clips.toMutableList()

        val clipIndex = clips.indexOfFirst {
            it.timelineStartUs < playhead && it.timelineEndUs > playhead &&
                    (_state.value.selectedClipId == null || it.id == _state.value.selectedClipId)
        }

        if (clipIndex == -1) return false

        pushSnapshot()
        val clip = clips[clipIndex]

        val offsetInClipUs = playhead - clip.timelineStartUs
        val cutSourceUs = clip.sourceInUs + (offsetInClipUs * clip.speed).toLong()

        val clipA = clip.copy(
            id = UUID.randomUUID().toString(),
            sourceOutUs = cutSourceUs
        )

        val clipB = clip.copy(
            id = UUID.randomUUID().toString(),
            sourceInUs = cutSourceUs,
            timelineStartUs = playhead
        )

        clips.removeAt(clipIndex)
        clips.add(clipIndex, clipA)
        clips.add(clipIndex + 1, clipB)

        _state.value = _state.value.copy(
            clips = clips,
            selectedClipId = clipB.id
        )
        return true
    }

    /**
     * Trims the start edge of a clip, with minimum duration protection.
     */
    fun trimStart(clipId: String, newStartUs: Long, minDurationUs: Long = 200_000L): Boolean {
        val clips = _state.value.clips.toMutableList()
        val index = clips.indexOfFirst { it.id == clipId }
        if (index == -1) return false

        val clip = clips[index]
        val boundedStartUs = newStartUs.coerceAtMost(clip.timelineEndUs - minDurationUs)
        val deltaTimelineUs = boundedStartUs - clip.timelineStartUs
        val newSourceInUs = clip.sourceInUs + (deltaTimelineUs * clip.speed).toLong()

        if (newSourceInUs < 0L || newSourceInUs >= clip.sourceOutUs) return false

        pushSnapshot()
        val updated = clip.copy(
            sourceInUs = newSourceInUs,
            timelineStartUs = boundedStartUs
        )
        clips[index] = updated
        _state.value = _state.value.copy(clips = clips)
        return true
    }

    /**
     * Trims the end edge of a clip, with minimum duration protection.
     */
    fun trimEnd(clipId: String, newEndUs: Long, minDurationUs: Long = 200_000L): Boolean {
        val clips = _state.value.clips.toMutableList()
        val index = clips.indexOfFirst { it.id == clipId }
        if (index == -1) return false

        val clip = clips[index]
        val boundedEndUs = newEndUs.coerceAtLeast(clip.timelineStartUs + minDurationUs)
        val newDurationUs = boundedEndUs - clip.timelineStartUs
        val newSourceOutUs = clip.sourceInUs + (newDurationUs * clip.speed).toLong()

        pushSnapshot()
        val updated = clip.copy(
            sourceOutUs = newSourceOutUs
        )
        clips[index] = updated
        _state.value = _state.value.copy(clips = clips)
        return true
    }

    /**
     * Deletes a clip from the timeline without touching source media.
     * Closes the ripple gap on the same track.
     */
    fun deleteClip(clipId: String): Boolean {
        val clips = _state.value.clips.toMutableList()
        val index = clips.indexOfFirst { it.id == clipId }
        if (index == -1) return false

        pushSnapshot()
        val deletedClip = clips.removeAt(index)
        val deletedDuration = deletedClip.durationUs

        // Ripple forward clips on the same track
        for (i in index until clips.size) {
            val c = clips[i]
            if (c.trackId == deletedClip.trackId && c.timelineStartUs > deletedClip.timelineStartUs) {
                clips[i] = c.copy(timelineStartUs = (c.timelineStartUs - deletedDuration).coerceAtLeast(0L))
            }
        }

        _state.value = _state.value.copy(
            clips = clips,
            selectedClipId = null
        )
        return true
    }

    /**
     * Snaps a target timeline timestamp to the nearest clip edge within threshold.
     */
    fun snapToNearestEdge(timestampUs: Long, thresholdUs: Long = 150_000L): Long {
        var closest = timestampUs
        var minDiff = thresholdUs

        // Snap to zero
        if (kotlin.math.abs(timestampUs) < minDiff) {
            closest = 0L
            minDiff = kotlin.math.abs(timestampUs)
        }

        for (clip in _state.value.clips) {
            val startDiff = kotlin.math.abs(clip.timelineStartUs - timestampUs)
            if (startDiff < minDiff) {
                minDiff = startDiff
                closest = clip.timelineStartUs
            }
            val endDiff = kotlin.math.abs(clip.timelineEndUs - timestampUs)
            if (endDiff < minDiff) {
                minDiff = endDiff
                closest = clip.timelineEndUs
            }
        }

        return closest
    }

    fun undo(): Boolean {
        if (undoStack.isEmpty()) return false
        val current = TimelineSnapshot(
            tracks = _state.value.tracks.map { it.copy() },
            clips = _state.value.clips.map { it.copy() }
        )
        redoStack.add(current)
        val previous = undoStack.removeAt(undoStack.size - 1)

        _state.value = _state.value.copy(
            tracks = previous.tracks,
            clips = previous.clips
        )
        updateHistoryFlags()
        return true
    }

    fun redo(): Boolean {
        if (redoStack.isEmpty()) return false
        val current = TimelineSnapshot(
            tracks = _state.value.tracks.map { it.copy() },
            clips = _state.value.clips.map { it.copy() }
        )
        undoStack.add(current)
        val next = redoStack.removeAt(redoStack.size - 1)

        _state.value = _state.value.copy(
            tracks = next.tracks,
            clips = next.clips
        )
        updateHistoryFlags()
        return true
    }
}
