package com.synccut.app.engine.timeline

import com.google.common.truth.Truth.assertThat
import com.synccut.app.core.model.TimelineClip
import com.synccut.app.core.model.TimelineTrack
import com.synccut.app.core.model.TrackType
import org.junit.Before
import org.junit.Test
import java.util.UUID

class TimelineManagerTest {

    private lateinit var manager: TimelineManager
    private val projectId = "test-project"
    private val trackId = "video-track-1"

    @Before
    fun setup() {
        manager = TimelineManager(projectId)
        val track = TimelineTrack(id = trackId, projectId = projectId, type = TrackType.VIDEO, order = 0)
        val clip = TimelineClip(
            id = "clip-1",
            trackId = trackId,
            sourceMediaId = "media-1",
            sourceInUs = 0L,
            sourceOutUs = 10_000_000L, // 10s
            timelineStartUs = 0L
        )
        manager.initialize(listOf(track), listOf(clip))
    }

    @Test
    fun testSplitAtPlayhead() {
        // Position playhead at 4 seconds
        manager.setPlayhead(4_000_000L)
        val splitSuccess = manager.splitAtPlayhead()

        assertThat(splitSuccess).isTrue()
        val clips = manager.state.value.clips
        assertThat(clips).hasSize(2)

        val clipA = clips[0]
        val clipB = clips[1]

        assertThat(clipA.timelineStartUs).isEqualTo(0L)
        assertThat(clipA.sourceInUs).isEqualTo(0L)
        assertThat(clipA.sourceOutUs).isEqualTo(4_000_000L)
        assertThat(clipA.durationUs).isEqualTo(4_000_000L)

        assertThat(clipB.timelineStartUs).isEqualTo(4_000_000L)
        assertThat(clipB.sourceInUs).isEqualTo(4_000_000L)
        assertThat(clipB.sourceOutUs).isEqualTo(10_000_000L)
        assertThat(clipB.durationUs).isEqualTo(6_000_000L)
    }

    @Test
    fun testTrimStart() {
        // Trim 2 seconds off the start
        val trimmed = manager.trimStart("clip-1", newStartUs = 2_000_000L)
        assertThat(trimmed).isTrue()

        val clip = manager.state.value.clips[0]
        assertThat(clip.timelineStartUs).isEqualTo(2_000_000L)
        assertThat(clip.sourceInUs).isEqualTo(2_000_000L)
        assertThat(clip.durationUs).isEqualTo(8_000_000L)
    }

    @Test
    fun testDeleteClipWithRipple() {
        // Setup two clips
        val clip2 = TimelineClip(
            id = "clip-2",
            trackId = trackId,
            sourceMediaId = "media-2",
            sourceInUs = 0L,
            sourceOutUs = 5_000_000L,
            timelineStartUs = 10_000_000L
        )
        manager.initialize(
            manager.state.value.tracks,
            listOf(manager.state.value.clips[0], clip2)
        )

        // Delete clip-1 (10s long) -> clip-2 should ripple left by 10s to start at 0s
        val deleted = manager.deleteClip("clip-1")
        assertThat(deleted).isTrue()

        val clips = manager.state.value.clips
        assertThat(clips).hasSize(1)
        assertThat(clips[0].id).isEqualTo("clip-2")
        assertThat(clips[0].timelineStartUs).isEqualTo(0L)
    }

    @Test
    fun testUndoAndRedo() {
        manager.setPlayhead(5_000_000L)
        manager.splitAtPlayhead()
        assertThat(manager.state.value.clips).hasSize(2)
        assertThat(manager.state.value.canUndo).isTrue()

        // Undo split
        val undone = manager.undo()
        assertThat(undone).isTrue()
        assertThat(manager.state.value.clips).hasSize(1)
        assertThat(manager.state.value.canRedo).isTrue()

        // Redo split
        val redone = manager.redo()
        assertThat(redone).isTrue()
        assertThat(manager.state.value.clips).hasSize(2)
    }
}
