package com.synccut.app.ui.setup

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.synccut.app.core.model.AspectRatio
import com.synccut.app.core.model.Project
import com.synccut.app.core.model.Rational
import com.synccut.app.core.model.Resolution
import com.synccut.app.ui.theme.AccentBlue
import com.synccut.app.ui.theme.BorderStroke
import com.synccut.app.ui.theme.DarkSurface
import com.synccut.app.ui.theme.DarkSurfaceElevated
import com.synccut.app.ui.theme.TextPrimary
import com.synccut.app.ui.theme.TextSecondary

@Composable
fun ProjectSetupDialog(
    onDismiss: () -> Unit,
    onCreateProject: (Project) -> Unit
) {
    var projectName by remember { mutableStateOf("New Project 1") }
    var selectedRatio by remember { mutableStateOf(AspectRatio.RATIO_16_9) }
    var selectedResolution by remember { mutableStateOf(Resolution.P1080) }
    var selectedFps by remember { mutableStateOf(Rational.FPS_30) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = DarkSurface,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(20.dp)
            ) {
                Text(
                    text = "New Project Setup",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = projectName,
                    onValueChange = { projectName = it },
                    label = { Text("Project Name") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AccentBlue,
                        unfocusedBorderColor = BorderStroke,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text("Aspect Ratio", fontSize = 13.sp, color = TextSecondary)
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(
                        AspectRatio.RATIO_16_9 to "16:9",
                        AspectRatio.RATIO_9_16 to "9:16",
                        AspectRatio.RATIO_1_1 to "1:1",
                        AspectRatio.RATIO_4_5 to "4:5"
                    ).forEach { (ratio, label) ->
                        val isSelected = selectedRatio == ratio
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(38.dp)
                                .background(
                                    if (isSelected) AccentBlue else DarkSurfaceElevated,
                                    RoundedCornerShape(6.dp)
                                )
                                .border(1.dp, if (isSelected) AccentBlue else BorderStroke, RoundedCornerShape(6.dp))
                                .clickable { selectedRatio = ratio },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text("Resolution", fontSize = 13.sp, color = TextSecondary)
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(
                        Resolution.P720 to "720p",
                        Resolution.P1080 to "1080p",
                        Resolution.P2160_4K to "4K"
                    ).forEach { (res, label) ->
                        val isSelected = selectedResolution == res
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(38.dp)
                                .background(
                                    if (isSelected) AccentBlue else DarkSurfaceElevated,
                                    RoundedCornerShape(6.dp)
                                )
                                .border(1.dp, if (isSelected) AccentBlue else BorderStroke, RoundedCornerShape(6.dp))
                                .clickable { selectedResolution = res },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel", color = TextSecondary)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val project = Project(
                                name = projectName.ifBlank { "Untitled Project" },
                                aspectRatio = selectedRatio,
                                resolution = selectedResolution,
                                frameRate = selectedFps
                            )
                            onCreateProject(project)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = AccentBlue),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Create Project")
                    }
                }
            }
        }
    }
}
