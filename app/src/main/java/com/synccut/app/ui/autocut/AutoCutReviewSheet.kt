package com.synccut.app.ui.autocut

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.synccut.app.core.model.EditSuggestion
import com.synccut.app.core.model.TimeCode
import com.synccut.app.engine.autocut.AutoCutAggressiveness
import com.synccut.app.ui.theme.AccentBlue
import com.synccut.app.ui.theme.DarkSurface
import com.synccut.app.ui.theme.DarkSurfaceElevated
import com.synccut.app.ui.theme.TextPrimary
import com.synccut.app.ui.theme.TextSecondary

@Composable
fun AutoCutReviewSheet(
    suggestions: List<EditSuggestion>,
    currentAggressiveness: AutoCutAggressiveness = AutoCutAggressiveness.BALANCED,
    onSelectAggressiveness: (AutoCutAggressiveness) -> Unit = {},
    onRunAutoCut: () -> Unit = {},
    onToggleSuggestion: (String) -> Unit,
    onApplySuggestions: () -> Unit
) {
    val acceptedCount = suggestions.count { it.isAccepted }
    val totalCutDurationUs = suggestions.filter { it.isAccepted }.sumOf { it.durationUs }

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
                    text = "Auto-Cut Silences",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Text(
                    text = if (acceptedCount > 0) "$acceptedCount cuts selected (${TimeCode.formatDuration(totalCutDurationUs)} dead air removed)" else "Detect and ripple-cut silent gaps across all cameras",
                    fontSize = 11.sp,
                    color = TextSecondary
                )
            }

            Button(
                onClick = onApplySuggestions,
                enabled = acceptedCount > 0,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD97706)),
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(Icons.Default.ContentCut, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Cut Now", fontSize = 12.sp)
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Sensitivity Filter Chips
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Mode:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextSecondary)

            AutoCutAggressiveness.entries.forEach { mode ->
                FilterChip(
                    selected = mode == currentAggressiveness,
                    onClick = {
                        onSelectAggressiveness(mode)
                        onRunAutoCut()
                    },
                    label = {
                        Text(
                            when (mode) {
                                AutoCutAggressiveness.CONSERVATIVE -> "Gentle (1.5s)"
                                AutoCutAggressiveness.BALANCED -> "Balanced (0.8s)"
                                AutoCutAggressiveness.AGGRESSIVE -> "Tight (0.5s)"
                            },
                            fontSize = 10.sp
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = AccentBlue,
                        selectedLabelColor = TextPrimary
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        if (suggestions.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
                    .background(DarkSurfaceElevated, RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.AutoFixHigh, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(32.dp))
                    Spacer(modifier = Modifier.height(6.dp))
                    Text("No pauses detected with current threshold.", color = TextSecondary, fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = onRunAutoCut,
                        colors = ButtonDefaults.buttonColors(containerColor = AccentBlue),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Text("Analyze Audio", fontSize = 11.sp)
                    }
                }
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(suggestions, key = { it.id }) { item ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = DarkSurfaceElevated),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = item.isAccepted,
                                onCheckedChange = { onToggleSuggestion(item.id) },
                                colors = CheckboxDefaults.colors(
                                    checkedColor = AccentBlue,
                                    uncheckedColor = TextSecondary
                                )
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = item.reason,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = TextPrimary
                                )
                                Text(
                                    text = "${TimeCode.formatMinutesSeconds(item.inUs)} - ${TimeCode.formatMinutesSeconds(item.outUs)} (Duration: ${TimeCode.formatDuration(item.durationUs)})",
                                    fontSize = 11.sp,
                                    color = TextSecondary
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
