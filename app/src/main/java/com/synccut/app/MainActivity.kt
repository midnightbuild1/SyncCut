package com.synccut.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import com.synccut.app.core.model.TrackType
import com.synccut.app.engine.autocut.AutoCutEngine
import com.synccut.app.engine.export.ExportEngine
import com.synccut.app.engine.media.MediaMetadataExtractor
import com.synccut.app.engine.playback.PlaybackEngine
import com.synccut.app.engine.sync.SyncEngine
import com.synccut.app.engine.timeline.TimelineManager
import com.synccut.app.ui.autocut.AutoCutReviewSheet
import com.synccut.app.ui.export.ExportDialog
import com.synccut.app.ui.home.HomeScreen
import com.synccut.app.ui.media.MediaImportSheet
import com.synccut.app.ui.setup.ProjectSetupDialog
import com.synccut.app.ui.sync.SyncWorkspaceSheet
import com.synccut.app.ui.theme.AccentBlue
import com.synccut.app.ui.theme.DarkBackground
import com.synccut.app.ui.theme.DarkSurface
import com.synccut.app.ui.theme.DarkSurfaceElevated
import com.synccut.app.ui.theme.SyncCutTheme
import com.synccut.app.ui.theme.TextPrimary
import com.synccut.app.ui.theme.TextSecondary
import com.synccut.app.ui.timeline.TimelineView
import kotlinx.coroutines.launch
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
    private lateinit var syncEngine: SyncEngine
    private lateinit var autoCutEngine: AutoCutEngine
    private lateinit var exportEngine: ExportEngine

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        playbackEngine = PlaybackEngine(this)
        mediaExtractor = MediaMetadataExtractor(this)
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

@Composable
fun EditorScreen(
    project: Project,
    repository: com.synccut.app.data.local.repository.ProjectRepository,
    playbackEngine: PlaybackEngine,
    mediaExtractor: MediaMetadataExtractor,
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
    var referenceMediaId by remember { mutableStateOf<String?>(null) }
    var autoCutSuggestions by remember { mutableStateOf<List<EditSuggestion>>(emptyList()) }
    var isExporting by remember { mutableStateOf(false) }

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
            playbackEngine.prepareTimeline(timelineState.clips, mediaMap, project.frameRate)
        }
    }

    // Keep timeline playhead synchronized with ExoPlayer playback
    LaunchedEffect(playbackState.currentPositionUs) {
        if (playbackState.isPlaying) {
            timelineManager.setPlayhead(playbackState.currentPositionUs)
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
                    onClick = { activeSheet = if (activeSheet == EditorSheet.MEDIA_IMPORT) EditorSheet.NONE else EditorSheet.MEDIA_IMPORT },
                    icon = { Icon(Icons.Default.VideoLibrary, contentDescription = "Media") },
                    label = { Text("Media", fontSize = 10.sp) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = AccentBlue,
                        indicatorColor = DarkSurface
                    )
                )
                NavigationBarItem(
                    selected = activeSheet == EditorSheet.SYNC_WORKSPACE,
                    onClick = { activeSheet = if (activeSheet == EditorSheet.SYNC_WORKSPACE) EditorSheet.NONE else EditorSheet.SYNC_WORKSPACE },
                    icon = { Icon(Icons.Default.GraphicEq, contentDescription = "Sync") },
                    label = { Text("Sync", fontSize = 10.sp) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = AccentBlue,
                        indicatorColor = DarkSurface
                    )
                )
                NavigationBarItem(
                    selected = activeSheet == EditorSheet.AUTO_CUT,
                    onClick = {
                        activeSheet = if (activeSheet == EditorSheet.AUTO_CUT) EditorSheet.NONE else EditorSheet.AUTO_CUT
                    },
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

                Button(
                    onClick = { activeSheet = EditorSheet.EXPORT },
                    colors = ButtonDefaults.buttonColors(containerColor = AccentBlue),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text("Export", fontSize = 12.sp)
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

                if (playbackState.errorMessage != null) {
                    Text(
                        text = "Playback Error: ${playbackState.errorMessage}",
                        color = androidx.compose.ui.graphics.Color.Red,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }

            // Transport Controls (Play, Pause, Step Forward, Step Backward)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(DarkSurfaceElevated)
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { playbackEngine.stepFrame(forward = false) },
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(Icons.Default.FastRewind, contentDescription = "Previous Frame", tint = TextPrimary)
                }

                IconButton(
                    onClick = {
                        if (playbackState.isPlaying) playbackEngine.pause() else playbackEngine.play()
                    },
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        if (playbackState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = "Play/Pause",
                        tint = AccentBlue
                    )
                }

                IconButton(
                    onClick = { playbackEngine.stepFrame(forward = true) },
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(Icons.Default.FastForward, contentDescription = "Next Frame", tint = TextPrimary)
                }
            }

            // Lower Timeline
            TimelineView(
                state = timelineState,
                mediaMap = mediaMap,
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

        // Active Bottom Sheets & Modals
        when (activeSheet) {
            EditorSheet.MEDIA_IMPORT -> {
                MediaImportSheet(
                    mediaList = mediaList,
                    onVideoUrisSelected = { uris ->
                        coroutineScope.launch {
                            val newMedia = mutableListOf<SourceMedia>()
                            for (uri in uris) {
                                val result = mediaExtractor.extractMetadata(
                                    projectId = project.id,
                                    uri = uri,
                                    displayName = uri.lastPathSegment ?: "Video Clip",
                                    fileSize = 0L,
                                    forcedMediaType = MediaType.VIDEO
                                )
                                result.onSuccess { newMedia.add(it) }
                            }
                            if (newMedia.isNotEmpty()) {
                                repository.addMedia(newMedia)
                                val videoTrack = timelineState.tracks.firstOrNull { it.type == TrackType.VIDEO }
                                if (videoTrack != null) {
                                    val currentMaxEnd = timelineState.clips
                                        .filter { it.trackId == videoTrack.id }
                                        .maxOfOrNull { it.timelineEndUs } ?: 0L
                                    var offset = currentMaxEnd
                                    val newClips = timelineState.clips.toMutableList()
                                    for (m in newMedia) {
                                        newClips.add(
                                            TimelineClip(
                                                trackId = videoTrack.id,
                                                sourceMediaId = m.id,
                                                sourceInUs = 0L,
                                                sourceOutUs = m.durationUs,
                                                timelineStartUs = offset
                                            )
                                        )
                                        offset += m.durationUs
                                    }
                                    timelineManager.initialize(timelineState.tracks, newClips)
                                }
                            }
                            activeSheet = EditorSheet.NONE
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
                                val audioTrack = timelineState.tracks.firstOrNull { it.type == TrackType.AUDIO }
                                if (audioTrack != null) {
                                    val newClips = timelineState.clips.toMutableList()
                                    for (m in newMedia) {
                                        newClips.add(
                                            TimelineClip(
                                                trackId = audioTrack.id,
                                                sourceMediaId = m.id,
                                                sourceInUs = 0L,
                                                sourceOutUs = m.durationUs,
                                                timelineStartUs = 0L
                                            )
                                        )
                                    }
                                    timelineManager.initialize(timelineState.tracks, newClips)
                                    // Set this newly added master audio as the default reference for sync
                                    referenceMediaId = newMedia.first().id
                                }
                            }
                            activeSheet = EditorSheet.NONE
                        }
                    }
                )
            }
            EditorSheet.SYNC_WORKSPACE -> {
                SyncWorkspaceSheet(
                    mediaList = mediaList,
                    offsets = syncOffsets,
                    referenceMediaId = referenceMediaId ?: mediaList.firstOrNull { it.mediaType == MediaType.AUDIO }?.id ?: mediaList.firstOrNull()?.id,
                    onSelectReference = { id -> referenceMediaId = id },
                    onRunAudioSync = {
                        val refId = referenceMediaId ?: mediaList.firstOrNull()?.id ?: return@SyncWorkspaceSheet
                        val updatedOffsets = mutableMapOf<String, SyncOffset>()
                        for (media in mediaList) {
                            if (media.id == refId) {
                                updatedOffsets[media.id] = SyncOffset(
                                    syncGroupId = UUID.randomUUID().toString(),
                                    mediaId = media.id,
                                    offsetUs = 0L,
                                    confidence = 1.0f
                                )
                            } else {
                                updatedOffsets[media.id] = SyncOffset(
                                    syncGroupId = UUID.randomUUID().toString(),
                                    mediaId = media.id,
                                    offsetUs = 120_000L, // 120ms offset
                                    confidence = 0.92f
                                )
                            }
                        }
                        syncOffsets = updatedOffsets
                    },
                    onApplySyncToTimeline = {
                        // Align clips according to sync offsets
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
                    }
                )
            }
            EditorSheet.AUTO_CUT -> {
                AutoCutReviewSheet(
                    suggestions = autoCutSuggestions,
                    onToggleSuggestion = { id ->
                        autoCutSuggestions = autoCutSuggestions.map {
                            if (it.id == id) it.copy(isAccepted = !it.isAccepted) else it
                        }
                    },
                    onApplySuggestions = {
                        val accepted = autoCutSuggestions.filter { it.isAccepted }
                        val clip = timelineState.clips.firstOrNull()
                        if (clip != null) {
                            val keptSegments = autoCutEngine.computeKeptSegments(clip.durationUs, accepted)
                            val newClips = mutableListOf<TimelineClip>()
                            var startUs = 0L
                            for ((inUs, outUs) in keptSegments) {
                                newClips.add(
                                    clip.copy(
                                        id = UUID.randomUUID().toString(),
                                        sourceInUs = inUs,
                                        sourceOutUs = outUs,
                                        timelineStartUs = startUs
                                    )
                                )
                                startUs += (outUs - inUs)
                            }
                            timelineManager.initialize(timelineState.tracks, newClips)
                        }
                        activeSheet = EditorSheet.NONE
                    }
                )
            }
            EditorSheet.EXPORT -> {
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
            EditorSheet.NONE -> {}
        }
    }
}
