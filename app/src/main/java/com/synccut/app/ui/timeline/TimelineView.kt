package com.synccut.app.ui.timeline

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CallSplit
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.synccut.app.core.model.MediaType
import com.synccut.app.core.model.Rational
import com.synccut.app.core.model.SourceMedia
import com.synccut.app.core.model.TimeCode
import com.synccut.app.core.model.TimelineClip
import com.synccut.app.core.model.TrackType
import com.synccut.app.engine.timeline.TimelineState
import com.synccut.app.ui.theme.AccentBlue
import com.synccut.app.ui.theme.AccentGreen
import com.synccut.app.ui.theme.AccentRed
import com.synccut.app.ui.theme.BorderStroke
import com.synccut.app.ui.theme.DarkBackground
import com.synccut.app.ui.theme.DarkSurface
import com.synccut.app.ui.theme.DarkSurfaceElevated
import com.synccut.app.ui.theme.PlayheadColor
import com.synccut.app.ui.theme.TextPrimary
import com.synccut.app.ui.theme.TextSecondary
import com.synccut.app.ui.theme.TrackAudioColor
import com.synccut.app.ui.theme.TrackVideoColor

@Composable
fun TimelineView(
    state: TimelineState,
    mediaMap: Map<String, SourceMedia>,
    projectFps: Rational,
    onSeekUs: (Long) -> Unit,
    onSelectClip: (String?) -> Unit,
    onSplit: () -> Unit,
    onDelete: (String) -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit
) {
    // Zoom factor: pixels per second
    var pxPerSecond by remember { mutableFloatStateOf(80f) }
    val scrollState = rememberScrollState()

    // Separate clips by track type: Video vs Audio to prevent duplicate rendering
    val videoTrackIds = remember(state.tracks) {
        state.tracks.filter { it.type == TrackType.VIDEO }.map { it.id }.toSet()
    }
    val audioTrackIds = remember(state.tracks) {
        state.tracks.filter { it.type == TrackType.AUDIO }.map { it.id }.toSet()
    }

    val videoClips = state.clips.filter { clip ->
        if (videoTrackIds.isNotEmpty()) clip.trackId in videoTrackIds
        else mediaMap[clip.sourceMediaId]?.mediaType != MediaType.AUDIO
    }

    val audioClips = state.clips.filter { clip ->
        if (audioTrackIds.isNotEmpty()) clip.trackId in audioTrackIds
        else mediaMap[clip.sourceMediaId]?.mediaType == MediaType.AUDIO
    }

    // Determine total timeline width
    val maxEndUs = state.clips.maxOfOrNull { it.timelineEndUs } ?: 10_000_000L
    val totalSeconds = (maxEndUs / 1_000_000L) + 5L
    val timelineWidthDp = (totalSeconds * pxPerSecond).dp

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(DarkSurface)
    ) {
        // Timeline Header: Timecode + Edit Toolbar (Split, Delete, Undo, Redo)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(DarkSurfaceElevated)
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Current Timecode
            Text(
                text = TimeCode.formatSmpte(state.playheadUs, projectFps),
                color = AccentBlue,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )

            // Toolbar Controls (48dp touch targets)
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = onUndo,
                    enabled = state.canUndo,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.Undo,
                        contentDescription = "Undo",
                        tint = if (state.canUndo) TextPrimary else TextSecondary.copy(alpha = 0.3f)
                    )
                }

                IconButton(
                    onClick = onRedo,
                    enabled = state.canRedo,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.Redo,
                        contentDescription = "Redo",
                        tint = if (state.canRedo) TextPrimary else TextSecondary.copy(alpha = 0.3f)
                    )
                }

                IconButton(
                    onClick = onSplit,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.CallSplit,
                        contentDescription = "Split at playhead",
                        tint = TextPrimary
                    )
                }

                IconButton(
                    onClick = { state.selectedClipId?.let { onDelete(it) } },
                    enabled = state.selectedClipId != null,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Delete Clip",
                        tint = if (state.selectedClipId != null) AccentRed else TextSecondary.copy(alpha = 0.3f)
                    )
                }
            }
        }

        // Horizontal Scrollable Tracks Canvas
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
                .background(DarkBackground)
                .horizontalScroll(scrollState)
                .pointerInput(Unit) {
                    detectTapGestures { offset ->
                        val clickedSec = offset.x / pxPerSecond
                        val clickedUs = (clickedSec * 1_000_000L).toLong().coerceAtLeast(0L)
                        onSeekUs(clickedUs)
                    }
                }
        ) {
            Box(
                modifier = Modifier
                    .width(timelineWidthDp)
                    .fillMaxHeight()
            ) {
                // Time Ruler (Top 24dp)
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(24.dp)
                ) {
                    val secWidth = pxPerSecond
                    for (s in 0..totalSeconds.toInt()) {
                        val x = s * secWidth
                        // Major tick per second
                        drawLine(
                            color = BorderStroke,
                            start = Offset(x, 10f),
                            end = Offset(x, 24f),
                            strokeWidth = 2f
                        )
                    }
                }

                // Video Track (Y: 28dp to 92dp) - ONLY contains video clips
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .offset { IntOffset(0, 32.dp.roundToPx()) }
                        .height(60.dp)
                        .background(DarkSurfaceElevated.copy(alpha = 0.4f))
                ) {
                    videoClips.forEach { clip ->
                        val clipStartSec = clip.timelineStartUs / 1_000_000f
                        val clipDurationSec = clip.durationUs / 1_000_000f
                        val leftDp = (clipStartSec * pxPerSecond).dp
                        val widthDp = (clipDurationSec * pxPerSecond).coerceAtLeast(20f).dp
                        val isSelected = clip.id == state.selectedClipId
                        val media = mediaMap[clip.sourceMediaId]

                        Box(
                            modifier = Modifier
                                .offset { IntOffset(leftDp.roundToPx(), 0) }
                                .width(widthDp)
                                .height(56.dp)
                                .background(TrackVideoColor, RoundedCornerShape(4.dp))
                                .border(
                                    2.dp,
                                    if (isSelected) AccentBlue else BorderStroke,
                                    RoundedCornerShape(4.dp)
                                )
                                .clickable { onSelectClip(clip.id) }
                                .padding(horizontal = 4.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = media?.displayName ?: "Video",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = TextPrimary,
                                maxLines = 1
                            )
                        }
                    }
                }

                // Audio Track (Y: 100dp to 156dp) - ONLY contains audio clips
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .offset { IntOffset(0, 100.dp.roundToPx()) }
                        .height(48.dp)
                        .background(DarkSurfaceElevated.copy(alpha = 0.2f))
                ) {
                    audioClips.forEach { clip ->
                        val clipStartSec = clip.timelineStartUs / 1_000_000f
                        val clipDurationSec = clip.durationUs / 1_000_000f
                        val leftDp = (clipStartSec * pxPerSecond).dp
                        val widthDp = (clipDurationSec * pxPerSecond).coerceAtLeast(20f).dp
                        val isSelected = clip.id == state.selectedClipId
                        val media = mediaMap[clip.sourceMediaId]

                        Box(
                            modifier = Modifier
                                .offset { IntOffset(leftDp.roundToPx(), 0) }
                                .width(widthDp)
                                .height(44.dp)
                                .background(TrackAudioColor, RoundedCornerShape(4.dp))
                                .border(
                                    2.dp,
                                    if (isSelected) AccentGreen else BorderStroke,
                                    RoundedCornerShape(4.dp)
                                )
                                .clickable { onSelectClip(clip.id) }
                                .padding(4.dp)
                        ) {
                            Text(
                                text = media?.displayName ?: "Audio",
                                fontSize = 10.sp,
                                color = TextPrimary,
                                maxLines = 1
                            )
                        }
                    }
                }

                // Playhead Vertical Line with Head Cursor
                val playheadSec = state.playheadUs / 1_000_000f
                val playheadXDp = (playheadSec * pxPerSecond).dp

                Box(
                    modifier = Modifier
                        .offset { IntOffset(playheadXDp.roundToPx() - 1.dp.roundToPx(), 0) }
                        .width(2.dp)
                        .fillMaxHeight()
                        .background(PlayheadColor)
                )
            }
        }
    }
}
