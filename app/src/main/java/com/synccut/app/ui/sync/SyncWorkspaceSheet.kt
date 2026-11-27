package com.synccut.app.ui.sync

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.synccut.app.core.model.MediaType
import com.synccut.app.core.model.Rational
import com.synccut.app.core.model.SourceMedia
import com.synccut.app.core.model.SyncOffset
import com.synccut.app.ui.theme.AccentBlue
import com.synccut.app.ui.theme.AccentGreen
import com.synccut.app.ui.theme.AccentRed
import com.synccut.app.ui.theme.BorderStroke
import com.synccut.app.ui.theme.DarkSurface
import com.synccut.app.ui.theme.DarkSurfaceElevated
import com.synccut.app.ui.theme.TextPrimary
import com.synccut.app.ui.theme.TextSecondary

@Composable
fun SyncWorkspaceSheet(
    mediaList: List<SourceMedia>,
    offsets: Map<String, SyncOffset>,
    referenceMediaId: String?,
    projectFps: Rational = Rational.FPS_30,
    onSelectReference: (String) -> Unit,
    onRunAudioSync: () -> Unit,
    onApplySyncToTimeline: () -> Unit,
    onAdjustOffsetFrames: (mediaId: String, frameDelta: Long) -> Unit,
    onAdjustOffsetMillis: (mediaId: String, millisDelta: Long) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(DarkSurface)
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Sync Workspace",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Text(
                    text = "Sub-millisecond waveform alignment across all cameras",
                    fontSize = 12.sp,
                    color = TextSecondary
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onRunAudioSync,
                    colors = ButtonDefaults.buttonColors(containerColor = AccentBlue),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Auto-Sync", fontSize = 12.sp)
                }

                if (offsets.isNotEmpty()) {
                    Button(
                        onClick = onApplySyncToTimeline,
                        colors = ButtonDefaults.buttonColors(containerColor = AccentGreen),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Apply", fontSize = 12.sp)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        if (mediaList.isEmpty()) {
            Text("Import camera clips to synchronize.", color = TextSecondary, fontSize = 13.sp)
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(mediaList, key = { it.id }) { media ->
                    val isRef = media.id == referenceMediaId
                    val isAudio = media.mediaType == MediaType.AUDIO
                    val offset = offsets[media.id]

                    Card(
                        colors = CardDefaults.cardColors(containerColor = DarkSurfaceElevated),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(
                                1.dp,
                                if (isRef) AccentGreen else BorderStroke,
                                RoundedCornerShape(8.dp)
                            )
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            // Header row: Icon, Name, Reference Tag
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        if (isAudio) Icons.Default.Audiotrack else Icons.Default.Videocam,
                                        contentDescription = null,
                                        tint = if (isRef) AccentGreen else if (isAudio) AccentBlue else TextSecondary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column {
                                        Text(
                                            text = media.displayName,
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 14.sp,
                                            color = TextPrimary
                                        )
                                        Text(
                                            text = if (isAudio) "Master External Audio" else "Camera Angle Video",
                                            fontSize = 10.sp,
                                            color = TextSecondary
                                        )
                                    }
                                }

                                if (isRef) {
                                    Box(
                                        modifier = Modifier
                                            .background(AccentGreen.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text("MASTER REF", fontSize = 9.sp, color = AccentGreen, fontWeight = FontWeight.Bold)
                                    }
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .clickable { onSelectReference(media.id) }
                                            .padding(4.dp)
                                    ) {
                                        Text("Set Reference", fontSize = 11.sp, color = AccentBlue)
                                    }
                                }
                            }

                            if (!isRef) {
                                Spacer(modifier = Modifier.height(8.dp))
                                val confidence = offset?.confidence ?: 0f
                                val confColor = when {
                                    confidence >= 0.70f -> AccentGreen
                                    confidence >= 0.45f -> AccentBlue
                                    else -> AccentRed
                                }

                                val offsetMs = (offset?.totalOffsetUs ?: 0L) / 1000L
                                val offsetFrames = projectFps.microsecondsToFrames(offset?.totalOffsetUs ?: 0L)

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Offset: ${offsetMs} ms ($offsetFrames frames)",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = TextPrimary
                                    )
                                    Text(
                                        text = "Sync: ${(confidence * 100).toInt()}% Confidence",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = confColor
                                    )
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                // Fine Manual Adjustment Buttons (-5, -1, +1, +5 frames & ms)
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    OutlinedButton(
                                        onClick = { onAdjustOffsetFrames(media.id, -5) },
                                        modifier = Modifier.weight(1f).height(30.dp),
                                        shape = RoundedCornerShape(4.dp)
                                    ) {
                                        Text("-5f", fontSize = 9.sp, color = TextPrimary)
                                    }
                                    OutlinedButton(
                                        onClick = { onAdjustOffsetFrames(media.id, -1) },
                                        modifier = Modifier.weight(1f).height(30.dp),
                                        shape = RoundedCornerShape(4.dp)
                                    ) {
                                        Text("-1f", fontSize = 9.sp, color = TextPrimary)
                                    }
                                    OutlinedButton(
                                        onClick = { onAdjustOffsetFrames(media.id, 1) },
                                        modifier = Modifier.weight(1f).height(30.dp),
                                        shape = RoundedCornerShape(4.dp)
                                    ) {
                                        Text("+1f", fontSize = 9.sp, color = TextPrimary)
                                    }
                                    OutlinedButton(
                                        onClick = { onAdjustOffsetFrames(media.id, 5) },
                                        modifier = Modifier.weight(1f).height(30.dp),
                                        shape = RoundedCornerShape(4.dp)
                                    ) {
                                        Text("+5f", fontSize = 9.sp, color = TextPrimary)
                                    }
                                }

                                Spacer(modifier = Modifier.height(4.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    OutlinedButton(
                                        onClick = { onAdjustOffsetMillis(media.id, -50) },
                                        modifier = Modifier.weight(1f).height(28.dp),
                                        shape = RoundedCornerShape(4.dp)
                                    ) {
                                        Text("-50ms", fontSize = 9.sp, color = TextSecondary)
                                    }
                                    OutlinedButton(
                                        onClick = { onAdjustOffsetMillis(media.id, 50) },
                                        modifier = Modifier.weight(1f).height(28.dp),
                                        shape = RoundedCornerShape(4.dp)
                                    ) {
                                        Text("+50ms", fontSize = 9.sp, color = TextSecondary)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
