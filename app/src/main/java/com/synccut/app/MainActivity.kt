package com.synccut.app

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.ui.PlayerView
import com.synccut.app.core.model.AspectRatio
import com.synccut.app.core.model.EditSuggestion
import com.synccut.app.core.model.MediaType
import com.synccut.app.core.model.Project
import com.synccut.app.core.model.SourceMedia
import com.synccut.app.core.model.SyncOffset
import com.synccut.app.core.model.TimelineClip
import com.synccut.app.core.model.TimelineTrack
import com.synccut.app.core.model.TrackType
import com.synccut.app.engine.autocut.AutoCutEngine
import com.synccut.app.engine.export.ExportEngine
import com.synccut.app.engine.media.MediaMetadataExtractor
import com.synccut.app.engine.playback.PlaybackEngine
import com.synccut.app.engine.sync.AudioPcmExtractor
import com.synccut.app.engine.sync.CrossCorrelationSync
import com.synccut.app.engine.sync.SyncEngine
import com.synccut.app.engine.timeline.TimelineManager
import com.synccut.app.ui.autocut.AutoCutReviewSheet
import com.synccut.app.ui.export.ExportDialog
import com.synccut.app.ui.home.HomeScreen
import com.synccut.app.ui.media.MediaImportSheet
import com.synccut.app.ui.setup.ProjectSetupDialog
import com.synccut.app.ui.sync.SyncWorkspaceSheet
import com.synccut.app.ui.theme.AccentBlue
import com.synccut.app.ui.theme.AccentGreen
import com.synccut.app.ui.theme.DarkBackground
import com.synccut.app.ui.theme.DarkSurface
import com.synccut.app.ui.theme.DarkSurfaceElevated
import com.synccut.app.ui.theme.SyncCutTheme
import com.synccut.app.ui.theme.TextPrimary
import com.synccut.app.ui.theme.TextSecondary
import com.synccut.app.ui.timeline.TimelineView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

enum class EditorSheet {
    NONE,
    MEDIA_IMPORT,
    SYNC_WORKSPACE,
    AUTO_CUT,
    EXPORT
}

class MainActivity : ComponentActivity() {

    private lateinit var playbackEngine: PlaybackEngine
    private lateinit var mediaExtractor: MediaMetadataExtractor
    private lateinit var audioPcmExtractor: AudioPcmExtractor
    private lateinit var syncEngine: SyncEngine
    private lateinit var autoCutEngine: AutoCutEngine
    private lateinit var exportEngine: ExportEngine

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        playbackEngine = PlaybackEngine(this)
        mediaExtractor = MediaMetadataExtractor(this)
        audioPcmExtractor = AudioPcmExtractor(this)
        syncEngine = SyncEngine()
        autoCutEngine = AutoCutEngine()
        exportEngine = ExportEngine(this)

        val app = application as SyncCutApp
        val repository = app.repository

        setContent {
            SyncCutTheme {
                val coroutineScope = rememberCoroutineScope()
                val projects by repository.projectsFlow.collectAsState(initial = emptyList())

                var activeProject by remember { mutableStateOf<Project?>(null) }
                var showSetupDialog by remember { mutableStateOf(false) }

                if (activeProject == null) {
                    HomeScreen(
                        projects = projects,
                        onNewProjectClick = { showSetupDialog = true },
                        onProjectClick = { project -> activeProject = project },
                        onDeleteProject = { id ->
                            coroutineScope.launch { repository.deleteProject(id) }
                        }
                    )

                    if (showSetupDialog) {
                        ProjectSetupDialog(
                            onDismiss = { showSetupDialog = false },
                            onCreateProject = { newProject ->
                                coroutineScope.launch {
                                    repository.saveProject(newProject)
                                    showSetupDialog = false
                                    activeProject = newProject
                                }
                            }
                        )
                    }
                } else {
                    val currentProject = activeProject!!
                    EditorScreen(
                        project = currentProject,
                        repository = repository,
                        playbackEngine = playbackEngine,
                        mediaExtractor = mediaExtractor,
                        audioPcmExtractor = audioPcmExtractor,
                        syncEngine = syncEngine,
                        autoCutEngine = autoCutEngine,
                        exportEngine = exportEngine,
                        onBack = {
                            activeProject = null
                            playbackEngine.release()
                        }
                    )
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        playbackEngine.release()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    project: Project,
    repository: com.synccut.app.data.local.repository.ProjectRepository,
    playbackEngine: PlaybackEngine,
    mediaExtractor: MediaMetadataExtractor,
    audioPcmExtractor: AudioPcmExtractor,
    syncEngine: SyncEngine,
    autoCutEngine: AutoCutEngine,
    exportEngine: ExportEngine,
    onBack: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val mediaList by repository.getMediaForProjectFlow(project.id).collectAsState(initial = emptyList())
    val mediaMap = remember(mediaList) { mediaList.associateBy { it.id } }

    val timelineManager = remember(project.id) { TimelineManager(project.id) }
    val timelineState by timelineManager.state.collectAsState()
    val playbackState by playbackEngine.playbackState.collectAsState()
    val exportProgress by exportEngine.progressFlow.collectAsState()

    var activeSheet by remember { mutableStateOf(EditorSheet.NONE) }
    var syncOffsets by remember { mutableStateOf<Map<String, SyncOffset>>(emptyMap()) }
    var audioEnvelopes by remember { mutableStateOf<Map<String, FloatArray>>(emptyMap()) }
    var referenceMediaId by remember { mutableStateOf<String?>(null) }
    var autoCutAggressiveness by remember { mutableStateOf(com.synccut.app.engine.autocut.AutoCutAggressiveness.BALANCED) }
    var autoCutSuggestions by remember { mutableStateOf<List<EditSuggestion>>(emptyList()) }
    var isExporting by remember { mutableStateOf(false) }

    var statusMessage by remember { mutableStateOf<String?>(null) }
    var isProcessingAudio by remember { mutableStateOf(false) }

    // Initialize timeline from repository
    LaunchedEffect(project.id) {
        val tracks = repository.getTracks(project.id)
        val clips = repository.getClips(project.id)
        timelineManager.initialize(tracks, clips)
    }

    // Auto-save timeline on change & update playback engine
    LaunchedEffect(timelineState.clips, mediaMap) {
        repository.saveTimeline(project.id, timelineState.tracks, timelineState.clips)
        if (timelineState.clips.isNotEmpty() && mediaMap.isNotEmpty()) {
            playbackEngine.prepareMultiCam(
                clips = timelineState.clips,
                mediaMap = mediaMap,
                fps = project.frameRate,
                targetMediaId = playbackState.activeAngleMediaId
            )
        }
    }

    // Keep timeline playhead synchronized with ExoPlayer playback
    LaunchedEffect(playbackState.currentPositionUs) {
        if (playbackState.isPlaying) {
            timelineManager.setPlayhead(playbackState.currentPositionUs)
        }
    }

    // --- Automatic Sync Engine on Import ---
    val runAutoSync = {
        coroutineScope.launch {
            if (mediaList.size < 2) return@launch
            isProcessingAudio = true
            statusMessage = "⚡ Analyzing audio waveforms across all camera angles..."

            withContext(Dispatchers.Default) {
                // Determine Reference Track: prioritize pure audio, or first media
                val refMedia = mediaList.firstOrNull { it.mediaType == MediaType.AUDIO } ?: mediaList.first()
                referenceMediaId = refMedia.id

                val envelopes = mutableMapOf<String, FloatArray>()
                for (m in mediaList) {
                    val env = audioPcmExtractor.extractRmsEnvelope(Uri.parse(m.uri))
                    envelopes[m.id] = env
                }

                val refEnvelope = envelopes[refMedia.id] ?: FloatArray(0)
                val calculatedOffsets = mutableMapOf<String, SyncOffset>()

                for (m in mediaList) {
                    if (m.id == refMedia.id) {
                        calculatedOffsets[m.id] = SyncOffset(
                            syncGroupId = UUID.randomUUID().toString(),
                            mediaId = m.id,
                            offsetUs = 0L,
                            confidence = 1.0f
                        )
                    } else {
                        val targetEnv = envelopes[m.id] ?: FloatArray(0)
                        val corr = CrossCorrelationSync.computeCorrelation(
                            reference = refEnvelope,
                            target = targetEnv,
                            maxLagSamples = AudioPcmExtractor.TARGET_ENVELOPE_SAMPLE_RATE_HZ * 60
                        )
                        val offsetUs = CrossCorrelationSync.lagToMicroseconds(
                            corr.fractionalLag,
                            AudioPcmExtractor.TARGET_ENVELOPE_SAMPLE_RATE_HZ
                        )
                        calculatedOffsets[m.id] = SyncOffset(
                            syncGroupId = UUID.randomUUID().toString(),
                            mediaId = m.id,
                            offsetUs = offsetUs.coerceAtLeast(0L),
                            confidence = corr.confidence
                        )
                    }
                }

                syncOffsets = calculatedOffsets
                audioEnvelopes = envelopes

                // Instantly apply sync to all timeline clips so tracks slide into place
                val updatedClips = timelineState.clips.map { clip ->
                    val offset = calculatedOffsets[clip.sourceMediaId]
                    if (offset != null) {
                        clip.copy(timelineStartUs = offset.totalOffsetUs.coerceAtLeast(0L))
                    } else clip
                }

                timelineManager.initialize(timelineState.tracks, updatedClips)
            }

            isProcessingAudio = false
            statusMessage = "✓ Auto-Sync Completed! All camera angles aligned in timeline."
            delay(4000)
            statusMessage = null
        }
    }

    // --- 1-Tap Auto-Cut (Silence & Pause Remover) ---
    val runAutoCutSilences = {
        coroutineScope.launch {
            if (timelineState.clips.isEmpty()) return@launch
            isProcessingAudio = true
            statusMessage = "✂️ Scanning audio for awkward silences & pauses..."

            withContext(Dispatchers.Default) {
                // Find reference media or first media with audio
                val refMedia = mediaList.firstOrNull { it.mediaType == MediaType.AUDIO } ?: mediaList.firstOrNull()
                if (refMedia != null) {
                    val envelope = audioEnvelopes[refMedia.id] ?: audioPcmExtractor.extractRmsEnvelope(Uri.parse(refMedia.uri))
                    if (!audioEnvelopes.containsKey(refMedia.id)) {
                        audioEnvelopes = audioEnvelopes + (refMedia.id to envelope)
                    }
                    val suggestions = autoCutEngine.analyzeMedia(
                        sourceMediaId = refMedia.id,
                        durationUs = refMedia.durationUs,
                        audioRmsEnvelope = envelope,
                        envelopeSampleRateHz = AudioPcmExtractor.TARGET_ENVELOPE_SAMPLE_RATE_HZ,
                        aggressiveness = autoCutAggressiveness
                    )

                    autoCutSuggestions = suggestions

                    if (suggestions.isNotEmpty()) {
                        // Apply ripple cut across ALL tracks simultaneously so all angles stay in sync!
                        val cutClips = autoCutEngine.rippleCutSilencesAcrossAllTracks(
                            currentClips = timelineState.clips,
                            acceptedCuts = suggestions
                        )
                        timelineManager.initialize(timelineState.tracks, cutClips)
                        val totalCutDur = suggestions.sumOf { it.durationUs } / 1_000_000.0
                        statusMessage = "✓ Auto-Cut: Removed ${suggestions.size} silences (${String.format("%.1f", totalCutDur)}s cut). Angles stayed in sync!"
                    } else {
                        statusMessage = "No long silences detected. Timeline is tight!"
                    }
                }
            }

            isProcessingAudio = false
            delay(4000)
            statusMessage = null
        }
    }

    // --- Director Mode: Live Cut To Angle at Playhead ---
    val cutToAngleAtPlayhead: (String) -> Unit = { targetMediaId ->
        playbackEngine.switchCameraAngle(targetMediaId)
        val playheadUs = timelineState.playheadUs
        val activeVideoTrack = timelineState.tracks.firstOrNull { it.type == TrackType.VIDEO }
        if (activeVideoTrack != null) {
            val clipToCut = timelineState.clips.firstOrNull {
                it.trackId == activeVideoTrack.id && playheadUs > it.timelineStartUs && playheadUs < it.timelineEndUs
            }
            if (clipToCut != null && clipToCut.sourceMediaId != targetMediaId) {
                val cutOffsetUs = playheadUs - clipToCut.timelineStartUs
                val part1 = clipToCut.copy(sourceOutUs = clipToCut.sourceInUs + cutOffsetUs)
                val part2 = clipToCut.copy(
                    id = UUID.randomUUID().toString(),
                    sourceMediaId = targetMediaId,
                    sourceInUs = clipToCut.sourceInUs + cutOffsetUs,
                    timelineStartUs = playheadUs
                )
                val newClips = timelineState.clips.map { if (it.id == clipToCut.id) part1 else it } + part2
                timelineManager.initialize(timelineState.tracks, newClips)
            }
        }
    }

    Scaffold(
        bottomBar = {
            NavigationBar(
                containerColor = DarkSurfaceElevated,
                modifier = Modifier.height(56.dp)
            ) {
                NavigationBarItem(
                    selected = activeSheet == EditorSheet.MEDIA_IMPORT,
                    onClick = { activeSheet = EditorSheet.MEDIA_IMPORT },
                    icon = { Icon(Icons.Default.VideoLibrary, contentDescription = "Media") },
                    label = { Text("Media", fontSize = 10.sp) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = AccentBlue,
                        indicatorColor = DarkSurface
                    )
                )
                NavigationBarItem(
                    selected = activeSheet == EditorSheet.SYNC_WORKSPACE,
                    onClick = { activeSheet = EditorSheet.SYNC_WORKSPACE },
                    icon = { Icon(Icons.Default.GraphicEq, contentDescription = "Sync") },
                    label = { Text("Sync", fontSize = 10.sp) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = AccentBlue,
                        indicatorColor = DarkSurface
                    )
                )
                NavigationBarItem(
                    selected = activeSheet == EditorSheet.AUTO_CUT,
                    onClick = { activeSheet = EditorSheet.AUTO_CUT },
                    icon = { Icon(Icons.Default.AutoFixHigh, contentDescription = "Auto Cut") },
                    label = { Text("Auto Cut", fontSize = 10.sp) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = AccentBlue,
                        indicatorColor = DarkSurface
                    )
                )
                NavigationBarItem(
                    selected = activeSheet == EditorSheet.EXPORT,
                    onClick = { activeSheet = EditorSheet.EXPORT },
                    icon = { Icon(Icons.Default.FileDownload, contentDescription = "Export") },
                    label = { Text("Export", fontSize = 10.sp) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = AccentBlue,
                        indicatorColor = DarkSurface
                    )
                )
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(DarkBackground)
                .padding(paddingValues)
        ) {
            // Top Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(DarkSurfaceElevated)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack, modifier = Modifier.size(40.dp)) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextPrimary)
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = project.name,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    // Quick Action: 1-Tap Auto Cut
                    if (timelineState.clips.isNotEmpty()) {
                        Button(
                            onClick = { runAutoCutSilences() },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD97706)),
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier.height(34.dp)
                        ) {
                            Icon(Icons.Default.ContentCut, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Auto-Cut", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    Button(
                        onClick = { activeSheet = EditorSheet.EXPORT },
                        colors = ButtonDefaults.buttonColors(containerColor = AccentBlue),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.height(34.dp)
                    ) {
                        Text("Export", fontSize = 11.sp)
                    }
                }
            }

            // Status Banner (Shows real-time auto-sync and auto-cut progress)
            AnimatedVisibility(visible = statusMessage != null || isProcessingAudio) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(AccentBlue.copy(alpha = 0.9f))
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (isProcessingAudio) {
                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(8.dp))
                        } else {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Text(
                            text = statusMessage ?: "Processing...",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            // Central Video Preview Surface
            val aspect = when (project.aspectRatio) {
                AspectRatio.RATIO_16_9 -> 16f / 9f
                AspectRatio.RATIO_9_16 -> 9f / 16f
                AspectRatio.RATIO_1_1 -> 1f
                AspectRatio.RATIO_4_5 -> 4f / 5f
                AspectRatio.CUSTOM -> 16f / 9f
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(DarkBackground),
                contentAlignment = Alignment.Center
            ) {
                AndroidView(
                    factory = { ctx ->
                        PlayerView(ctx).apply {
                            useController = false
                            setShutterBackgroundColor(android.graphics.Color.TRANSPARENT)
                            player = playbackEngine.getPlayer()
                        }
                    },
                    update = { view ->
                        if (view.player != playbackEngine.getPlayer()) {
                            view.player = playbackEngine.getPlayer()
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth(0.95f)
                        .aspectRatio(aspect)
                )

                if (playbackState.isBuffering) {
                    CircularProgressIndicator(color = AccentBlue, modifier = Modifier.size(36.dp))
                }

                if (timelineState.clips.isEmpty()) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = DarkSurfaceElevated),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.padding(24.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "Start Multi-Camera Project",
                                color = TextPrimary,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Import 2+ video clips. SyncCut will auto-sync them by waveform.",
                                color = TextSecondary,
                                fontSize = 12.sp,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(14.dp))
                            Button(
                                onClick = { activeSheet = EditorSheet.MEDIA_IMPORT },
                                colors = ButtonDefaults.buttonColors(containerColor = AccentBlue),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Icon(Icons.Default.VideoLibrary, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Import Clips", fontSize = 13.sp)
                            }
                        }
                    }
                }

                if (playbackState.errorMessage != null) {
                    Text(
                        text = "Playback: ${playbackState.errorMessage}",
                        color = Color.Red,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }

            // Camera Angles Switcher Row (Real-time live multi-cam switching)
            val videoMediaList = mediaList.filter { it.mediaType == MediaType.VIDEO }
            if (videoMediaList.size > 1) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(DarkSurfaceElevated)
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("ACTIVE ANGLE:", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = TextSecondary)
                    Spacer(modifier = Modifier.width(8.dp))
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        items(videoMediaList) { vMedia ->
                            val isSelected = (playbackState.activeAngleMediaId ?: videoMediaList.first().id) == vMedia.id
                            FilterChip(
                                selected = isSelected,
                                onClick = { playbackEngine.switchCameraAngle(vMedia.id) },
                                label = { Text(vMedia.displayName, fontSize = 11.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = AccentBlue,
                                    selectedLabelColor = TextPrimary
                                )
                            )
                        }
                    }

                    Button(
                        onClick = {
                            val activeId = playbackState.activeAngleMediaId ?: videoMediaList.first().id
                            cutToAngleAtPlayhead(activeId)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = DarkSurface),
                        shape = RoundedCornerShape(4.dp),
                        modifier = Modifier.height(28.dp)
                    ) {
                        Text("Cut Angle", fontSize = 10.sp, color = AccentBlue, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // Transport Controls (Step Back, Play/Pause, Step Forward)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(DarkSurface)
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { playbackEngine.stepFrame(forward = false) },
                    modifier = Modifier.size(46.dp)
                ) {
                    Icon(Icons.Default.FastRewind, contentDescription = "Previous Frame", tint = TextPrimary)
                }

                IconButton(
                    onClick = {
                        if (playbackState.isPlaying) playbackEngine.pause() else playbackEngine.play()
                    },
                    modifier = Modifier.size(46.dp)
                ) {
                    Icon(
                        if (playbackState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = "Play/Pause",
                        tint = AccentBlue
                    )
                }

                IconButton(
                    onClick = { playbackEngine.stepFrame(forward = true) },
                    modifier = Modifier.size(46.dp)
                ) {
                    Icon(Icons.Default.FastForward, contentDescription = "Next Frame", tint = TextPrimary)
                }
            }

            // Stacked Multi-Track Timeline (V1, V2, V3, A1)
            TimelineView(
                state = timelineState,
                mediaMap = mediaMap,
                audioEnvelopes = audioEnvelopes,
                syncOffsets = syncOffsets,
                projectFps = project.frameRate,
                onSeekUs = { us ->
                    timelineManager.setPlayhead(us)
                    playbackEngine.seekToUs(us)
                },
                onSelectClip = { clipId -> timelineManager.selectClip(clipId) },
                onSplit = { timelineManager.splitAtPlayhead() },
                onDelete = { clipId -> timelineManager.deleteClip(clipId) },
                onUndo = { timelineManager.undo() },
                onRedo = { timelineManager.redo() }
            )
        }

        // --- Modals & Sheets (Interactive Drawer UI) ---

        if (activeSheet == EditorSheet.MEDIA_IMPORT) {
            ModalBottomSheet(
                onDismissRequest = { activeSheet = EditorSheet.NONE },
                containerColor = DarkSurface
            ) {
                MediaImportSheet(
                    mediaList = mediaList,
                    onVideoUrisSelected = { uris ->
                        coroutineScope.launch {
                            val newMedia = mutableListOf<SourceMedia>()
                            for (uri in uris) {
                                val result = mediaExtractor.extractMetadata(
                                    projectId = project.id,
                                    uri = uri,
                                    displayName = uri.lastPathSegment ?: "Camera Angle",
                                    fileSize = 0L,
                                    forcedMediaType = MediaType.VIDEO
                                )
                                result.onSuccess { newMedia.add(it) }
                            }
                            if (newMedia.isNotEmpty()) {
                                repository.addMedia(newMedia)

                                // Create a separate Stacked Track for each imported camera angle
                                val currentTracks = timelineState.tracks.toMutableList()
                                val currentClips = timelineState.clips.toMutableList()

                                for (m in newMedia) {
                                    val trackOrder = currentTracks.count { it.type == TrackType.VIDEO }
                                    val newTrack = TimelineTrack(
                                        id = UUID.randomUUID().toString(),
                                        projectId = project.id,
                                        type = TrackType.VIDEO,
                                        order = trackOrder
                                    )
                                    currentTracks.add(newTrack)

                                    val newClip = TimelineClip(
                                        id = UUID.randomUUID().toString(),
                                        trackId = newTrack.id,
                                        sourceMediaId = m.id,
                                        sourceInUs = 0L,
                                        sourceOutUs = m.durationUs,
                                        timelineStartUs = 0L
                                    )
                                    currentClips.add(newClip)
                                }

                                timelineManager.initialize(currentTracks, currentClips)
                                activeSheet = EditorSheet.NONE

                                // Automatically fire auto-sync on import if 2+ clips exist!
                                delay(300)
                                runAutoSync()
                            }
                        }
                    },
                    onAudioUrisSelected = { uris ->
                        coroutineScope.launch {
                            val newMedia = mutableListOf<SourceMedia>()
                            for (uri in uris) {
                                val result = mediaExtractor.extractMetadata(
                                    projectId = project.id,
                                    uri = uri,
                                    displayName = uri.lastPathSegment ?: "Master Audio",
                                    fileSize = 0L,
                                    forcedMediaType = MediaType.AUDIO
                                )
                                result.onSuccess { newMedia.add(it) }
                            }
                            if (newMedia.isNotEmpty()) {
                                repository.addMedia(newMedia)

                                val currentTracks = timelineState.tracks.toMutableList()
                                val currentClips = timelineState.clips.toMutableList()

                                var audioTrack = currentTracks.firstOrNull { it.type == TrackType.AUDIO }
                                if (audioTrack == null) {
                                    audioTrack = TimelineTrack(
                                        id = UUID.randomUUID().toString(),
                                        projectId = project.id,
                                        type = TrackType.AUDIO,
                                        order = currentTracks.size
                                    )
                                    currentTracks.add(audioTrack)
                                }

                                for (m in newMedia) {
                                    currentClips.add(
                                        TimelineClip(
                                            id = UUID.randomUUID().toString(),
                                            trackId = audioTrack.id,
                                            sourceMediaId = m.id,
                                            sourceInUs = 0L,
                                            sourceOutUs = m.durationUs,
                                            timelineStartUs = 0L
                                        )
                                    )
                                }

                                timelineManager.initialize(currentTracks, currentClips)
                                referenceMediaId = newMedia.first().id
                                activeSheet = EditorSheet.NONE

                                // Automatically fire auto-sync with master audio!
                                delay(300)
                                runAutoSync()
                            }
                        }
                    }
                )
            }
        }

        if (activeSheet == EditorSheet.SYNC_WORKSPACE) {
            ModalBottomSheet(
                onDismissRequest = { activeSheet = EditorSheet.NONE },
                containerColor = DarkSurface
            ) {
                SyncWorkspaceSheet(
                    mediaList = mediaList,
                    offsets = syncOffsets,
                    referenceMediaId = referenceMediaId ?: mediaList.firstOrNull { it.mediaType == MediaType.AUDIO }?.id ?: mediaList.firstOrNull()?.id,
                    projectFps = project.frameRate,
                    onSelectReference = { id -> referenceMediaId = id },
                    onRunAudioSync = { runAutoSync() },
                    onApplySyncToTimeline = {
                        val updatedClips = timelineState.clips.map { clip ->
                            val offset = syncOffsets[clip.sourceMediaId]
                            if (offset != null) {
                                clip.copy(timelineStartUs = offset.totalOffsetUs.coerceAtLeast(0L))
                            } else clip
                        }
                        timelineManager.initialize(timelineState.tracks, updatedClips)
                        activeSheet = EditorSheet.NONE
                    },
                    onAdjustOffsetFrames = { mediaId, delta ->
                        val current = syncOffsets[mediaId] ?: SyncOffset(syncGroupId = "", mediaId = mediaId)
                        val adjusted = syncEngine.adjustOffsetByFrames(current, delta, project.frameRate)
                        syncOffsets = syncOffsets.toMutableMap().apply { put(mediaId, adjusted) }
                        val updatedClips = timelineState.clips.map { clip ->
                            if (clip.sourceMediaId == mediaId) {
                                clip.copy(timelineStartUs = adjusted.totalOffsetUs.coerceAtLeast(0L))
                            } else clip
                        }
                        timelineManager.initialize(timelineState.tracks, updatedClips)
                    },
                    onAdjustOffsetMillis = { mediaId, millisDelta ->
                        val current = syncOffsets[mediaId] ?: SyncOffset(syncGroupId = "", mediaId = mediaId)
                        val newOffsetUs = (current.offsetUs + (millisDelta * 1000L)).coerceAtLeast(0L)
                        val adjusted = current.copy(offsetUs = newOffsetUs)
                        syncOffsets = syncOffsets.toMutableMap().apply { put(mediaId, adjusted) }
                        val updatedClips = timelineState.clips.map { clip ->
                            if (clip.sourceMediaId == mediaId) {
                                clip.copy(timelineStartUs = adjusted.totalOffsetUs.coerceAtLeast(0L))
                            } else clip
                        }
                        timelineManager.initialize(timelineState.tracks, updatedClips)
                    }
                )
            }
        }

        if (activeSheet == EditorSheet.AUTO_CUT) {
            ModalBottomSheet(
                onDismissRequest = { activeSheet = EditorSheet.NONE },
                containerColor = DarkSurface
            ) {
                AutoCutReviewSheet(
                    suggestions = autoCutSuggestions,
                    currentAggressiveness = autoCutAggressiveness,
                    onSelectAggressiveness = { autoCutAggressiveness = it },
                    onRunAutoCut = { runAutoCutSilences() },
                    onToggleSuggestion = { id ->
                        autoCutSuggestions = autoCutSuggestions.map {
                            if (it.id == id) it.copy(isAccepted = !it.isAccepted) else it
                        }
                    },
                    onApplySuggestions = {
                        val accepted = autoCutSuggestions.filter { it.isAccepted }
                        if (accepted.isNotEmpty()) {
                            val cutClips = autoCutEngine.rippleCutSilencesAcrossAllTracks(
                                currentClips = timelineState.clips,
                                acceptedCuts = accepted
                            )
                            timelineManager.initialize(timelineState.tracks, cutClips)
                        }
                        activeSheet = EditorSheet.NONE
                    }
                )
            }
        }

        if (activeSheet == EditorSheet.EXPORT) {
            val context = LocalContext.current
            val outputDir = File(context.cacheDir, "exports")
            outputDir.mkdirs()
            val targetFile = File(outputDir, "${project.name.replace(" ", "_")}.mp4")

            ExportDialog(
                project = project,
                progress = exportProgress,
                isExporting = isExporting,
                onStartExport = {
                    isExporting = true
                    coroutineScope.launch {
                        exportEngine.startExport(
                            project = project,
                            clips = timelineState.clips,
                            mediaMap = mediaMap,
                            outputFile = targetFile
                        )
                        isExporting = false
                    }
                },
                onCancelExport = {
                    exportEngine.cancelExport()
                    isExporting = false
                },
                onDismiss = { activeSheet = EditorSheet.NONE }
            )
        }
    }
}
