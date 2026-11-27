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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Remove
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.synccut.app.core.model.MediaType
import com.synccut.app.core.model.Rational
import com.synccut.app.core.model.SourceMedia
import com.synccut.app.core.model.SyncOffset
import com.synccut.app.core.model.TimeCode
import com.synccut.app.core.model.TimelineClip
import com.synccut.app.core.model.TimelineTrack
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
import kotlin.math.max
import kotlin.math.min

@Composable
fun TimelineView(
    state: TimelineState,
    mediaMap: Map<String, SourceMedia>,
    audioEnvelopes: Map<String, FloatArray> = emptyMap(),
    syncOffsets: Map<String, SyncOffset> = emptyMap(),
    projectFps: Rational,
    onSeekUs: (Long) -> Unit,
    onSelectClip: (String?) -> Unit,
    onSplit: () -> Unit,
    onDelete: (String) -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit
) {
    var pxPerSecond by remember { mutableFloatStateOf(75f) }
    val horizontalScrollState = rememberScrollState()

    // Determine total timeline length
    val maxEndUs = state.clips.maxOfOrNull { it.timelineEndUs } ?: 10_000_000L
    val totalSeconds = (maxEndUs / 1_000_000L) + 6L
    val timelineWidthDp = (totalSeconds * pxPerSecond).dp

    // Organize tracks: ensure tracks list has entries
    val displayTracks = if (state.tracks.isNotEmpty()) {
        state.tracks.sortedBy { it.order }
    } else {
        listOf(
            TimelineTrack(projectId = "", type = TrackType.VIDEO, order = 0),
            TimelineTrack(projectId = "", type = TrackType.AUDIO, order = 1)
        )
    }

    val rowHeightDp = 50.dp
    val totalTracksHeightDp = (rowHeightDp * displayTracks.size) + 26.dp

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(DarkSurface)
    ) {
        // Toolbar: Timecode + Zoom Controls + Actions (Split, Delete, Undo, Redo)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(DarkSurfaceElevated)
                .padding(horizontal = 8.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // SMPTE Timecode
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = TimeCode.formatSmpte(state.playheadUs, projectFps),
                    color = AccentBlue,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.width(8.dp))

                // Zoom Out Button
                IconButton(
                    onClick = { pxPerSecond = (pxPerSecond * 0.75f).coerceIn(25f, 300f) },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(Icons.Default.Remove, contentDescription = "Zoom Out", tint = TextSecondary, modifier = Modifier.size(16.dp))
                }

                Text(
                    text = "${(pxPerSecond / 75f * 100).toInt()}%",
                    fontSize = 10.sp,
                    color = TextSecondary
                )

                // Zoom In Button
                IconButton(
                    onClick = { pxPerSecond = (pxPerSecond * 1.33f).coerceIn(25f, 300f) },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Zoom In", tint = TextSecondary, modifier = Modifier.size(16.dp))
                }
            }

            // Edit Toolbar
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = onUndo,
                    enabled = state.canUndo,
                    modifier = Modifier.size(38.dp)
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.Undo,
                        contentDescription = "Undo",
                        tint = if (state.canUndo) TextPrimary else TextSecondary.copy(alpha = 0.3f),
                        modifier = Modifier.size(20.dp)
                    )
                }

                IconButton(
                    onClick = onRedo,
                    enabled = state.canRedo,
                    modifier = Modifier.size(38.dp)
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.Redo,
                        contentDescription = "Redo",
                        tint = if (state.canRedo) TextPrimary else TextSecondary.copy(alpha = 0.3f),
                        modifier = Modifier.size(20.dp)
                    )
                }

                IconButton(
                    onClick = onSplit,
                    modifier = Modifier.size(38.dp)
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.CallSplit,
                        contentDescription = "Split Clip",
                        tint = TextPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                }

                IconButton(
                    onClick = { state.selectedClipId?.let { onDelete(it) } },
                    enabled = state.selectedClipId != null,
                    modifier = Modifier.size(38.dp)
                ) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Delete Clip",
                        tint = if (state.selectedClipId != null) AccentRed else TextSecondary.copy(alpha = 0.3f),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        // Multi-Track Timeline: Left Headers + Right Horizontal Scroll Tracks
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(totalTracksHeightDp.coerceIn(160.dp, 260.dp))
                .background(DarkBackground)
        ) {
            // Pinned Left Track Headers (V1, V2, A1)
            Column(
                modifier = Modifier
                    .width(54.dp)
                    .fillMaxHeight()
                    .background(DarkSurfaceElevated)
                    .border(1.dp, BorderStroke)
            ) {
                // Header spacer for ruler
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(24.dp)
                        .background(DarkSurface),
                    contentAlignment = Alignment.Center
                ) {
                    Text("TRACK", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = TextSecondary)
                }

                displayTracks.forEachIndexed { index, track ->
                    val isVideo = track.type == TrackType.VIDEO
                    val label = if (isVideo) "V${index + 1}" else "A${index + 1 - displayTracks.count { it.type == TrackType.VIDEO }}"
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(rowHeightDp)
                            .border(0.5.dp, BorderStroke)
                            .background(if (isVideo) DarkSurfaceElevated else DarkSurface),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = label,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isVideo) AccentBlue else AccentGreen
                            )
                            Text(
                                text = if (isVideo) "CAM" else "MIC",
                                fontSize = 8.sp,
                                color = TextSecondary
                            )
                        }
                    }
                }
            }

            // Scrollable Timeline Tracks Canvas
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .horizontalScroll(horizontalScrollState)
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
                    // Time Ruler Canvas
                    Canvas(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(24.dp)
                    ) {
                        val secWidth = pxPerSecond
                        for (s in 0..totalSeconds.toInt()) {
                            val x = s * secWidth
                            val isMajor = (s % 5 == 0)
                            drawLine(
                                color = if (isMajor) AccentBlue.copy(alpha = 0.6f) else BorderStroke,
                                start = Offset(x, if (isMajor) 4f else 12f),
                                end = Offset(x, 24f),
                                strokeWidth = if (isMajor) 2f else 1f
                            )
                        }
                    }

                    // Stacked Track Rows
                    displayTracks.forEachIndexed { trackIndex, track ->
                        val topY = (24.dp + (rowHeightDp * trackIndex))
                        val isVideo = track.type == TrackType.VIDEO
                        val trackClips = state.clips.filter { it.trackId == track.id }

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .offset { IntOffset(0, topY.roundToPx()) }
                                .height(rowHeightDp)
                                .border(0.5.dp, BorderStroke)
                                .background(if (isVideo) DarkSurfaceElevated.copy(alpha = 0.25f) else DarkSurface.copy(alpha = 0.45f))
                        ) {
                            trackClips.forEach { clip ->
                                val clipStartSec = clip.timelineStartUs / 1_000_000f
                                val clipDurationSec = clip.durationUs / 1_000_000f
                                val leftDp = (clipStartSec * pxPerSecond).dp
                                val widthDp = (clipDurationSec * pxPerSecond).coerceAtLeast(24f).dp
                                val isSelected = clip.id == state.selectedClipId
                                val media = mediaMap[clip.sourceMediaId]
                                val envelope = audioEnvelopes[clip.sourceMediaId]
                                val offsetInfo = syncOffsets[clip.sourceMediaId]
                                val isSynced = offsetInfo != null && offsetInfo.confidence >= 0.5f

                                Box(
                                    modifier = Modifier
                                        .offset { IntOffset(leftDp.roundToPx(), 0) }
                                        .width(widthDp)
                                        .height(rowHeightDp - 4.dp)
                                        .background(
                                            if (isVideo) TrackVideoColor else TrackAudioColor,
                                            RoundedCornerShape(4.dp)
                                        )
                                        .border(
                                            2.dp,
                                            if (isSelected) AccentBlue else BorderStroke,
                                            RoundedCornerShape(4.dp)
                                        )
                                        .clickable { onSelectClip(clip.id) }
                                        .padding(horizontal = 4.dp, vertical = 2.dp)
                                ) {
                                    // Clip Title & Sync Status Badge
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = media?.displayName ?: if (isVideo) "Angle" else "Audio",
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = TextPrimary,
                                            maxLines = 1,
                                            modifier = Modifier.weight(1f, fill = false)
                                        )

                                        if (isSynced) {
                                            Box(
                                                modifier = Modifier
                                                    .background(AccentGreen.copy(alpha = 0.25f), RoundedCornerShape(2.dp))
                                                    .padding(horizontal = 3.dp, vertical = 1.dp)
                                            ) {
                                                Text("SYNCED", fontSize = 7.sp, fontWeight = FontWeight.Bold, color = AccentGreen)
                                            }
                                        }
                                    }

                                    // Real Audio Waveform Visualization
                                    if (envelope != null && envelope.isNotEmpty()) {
                                        Canvas(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(if (isVideo) 16.dp else 26.dp)
                                                .align(Alignment.BottomCenter)
                                        ) {
                                            val canvasW = size.width
                                            val canvasH = size.height
                                            val sampleRateHz = 200 // 5ms per sample
                                            val startSample = ((clip.sourceInUs * sampleRateHz) / 1_000_000L).toInt().coerceIn(0, envelope.size - 1)
                                            val endSample = ((clip.sourceOutUs * sampleRateHz) / 1_000_000L).toInt().coerceIn(0, envelope.size)
                                            val samplesInClip = max(1, endSample - startSample)

                                            val stepPx = 3f
                                            val barCount = (canvasW / stepPx).toInt().coerceAtLeast(1)
                                            val samplesPerBar = max(1, samplesInClip / barCount)

                                            for (b in 0 until barCount) {
                                                val sIdx = (startSample + (b * samplesPerBar)).coerceIn(0, envelope.size - 1)
                                                var peak = 0f
                                                for (k in 0 until min(samplesPerBar, envelope.size - sIdx)) {
                                                    peak = max(peak, envelope[sIdx + k])
                                                }
                                                // Normalized bar height
                                                val barH = (peak * canvasH * 2.5f).coerceIn(2f, canvasH)
                                                val x = b * stepPx
                                                val yTop = if (isVideo) canvasH - barH else (canvasH - barH) / 2f

                                                drawLine(
                                                    color = if (isVideo) Color.White.copy(alpha = 0.35f) else AccentGreen.copy(alpha = 0.9f),
                                                    start = Offset(x, yTop),
                                                    end = Offset(x, yTop + barH),
                                                    strokeWidth = 2f
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Vertical Playhead Line across all tracks
                    val playheadSec = state.playheadUs / 1_000_000f
                    val playheadXDp = (playheadSec * pxPerSecond).dp

                    Box(
                        modifier = Modifier
                            .offset { IntOffset(playheadXDp.roundToPx() - 1.dp.roundToPx(), 0) }
                            .width(2.5.dp)
                            .fillMaxHeight()
                            .background(PlayheadColor)
                    )
                }
            }
        }
    }
}
