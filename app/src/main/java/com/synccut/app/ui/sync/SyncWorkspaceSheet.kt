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
    onSelectReference: (String) -> Unit,
    onRunAudioSync: () -> Unit,
    onApplySyncToTimeline: () -> Unit,
    onAdjustOffsetFrames: (mediaId: String, frameDelta: Long) -> Unit
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
                    text = "Align all camera videos with master reference track",
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
                    Text("Sync All", fontSize = 12.sp)
                }

                if (offsets.isNotEmpty()) {
                    Button(
                        onClick = onApplySyncToTimeline,
                        colors = ButtonDefaults.buttonColors(containerColor = AccentGreen),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Apply Sync", fontSize = 12.sp)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (mediaList.isEmpty()) {
            Text("Import clips to synchronize.", color = TextSecondary, fontSize = 13.sp)
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
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        if (isAudio) Icons.Default.Audiotrack else Icons.Default.GraphicEq,
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
                                            text = if (isAudio) "Master Audio" else "Camera Video",
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
                                        Text("REFERENCE", fontSize = 10.sp, color = AccentGreen, fontWeight = FontWeight.Bold)
                                    }
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .clickable { onSelectReference(media.id) }
                                            .padding(4.dp)
                                    ) {
                                        Text("Set as Reference", fontSize = 11.sp, color = AccentBlue)
                                    }
                                }
                            }

                            if (!isRef) {
                                Spacer(modifier = Modifier.height(8.dp))
                                val confidence = offset?.confidence ?: 0f
                                val confColor = when {
                                    confidence >= 0.75f -> AccentGreen
                                    confidence >= 0.50f -> AccentBlue
                                    else -> AccentRed
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Sync Offset: ${(offset?.totalOffsetUs ?: 0L) / 1000L} ms",
                                        fontSize = 12.sp,
                                        color = TextPrimary
                                    )
                                    Text(
                                        text = "Confidence: ${(confidence * 100).toInt()}%",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = confColor
                                    )
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                // Fine Manual Adjustment Buttons (-1 frame, +1 frame)
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    OutlinedButton(
                                        onClick = { onAdjustOffsetFrames(media.id, -1) },
                                        modifier = Modifier.weight(1f).height(32.dp),
                                        shape = RoundedCornerShape(4.dp)
                                    ) {
                                        Text("-1 Frame", fontSize = 10.sp, color = TextPrimary)
                                    }
                                    OutlinedButton(
                                        onClick = { onAdjustOffsetFrames(media.id, 1) },
                                        modifier = Modifier.weight(1f).height(32.dp),
                                        shape = RoundedCornerShape(4.dp)
                                    ) {
                                        Text("+1 Frame", fontSize = 10.sp, color = TextPrimary)
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
