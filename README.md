# SyncCut — Mobile Auto-Sync Video Editor

SyncCut is an Android-first video assembly tool focused on multi-clip synchronization, automatic best-moment selection, a responsive non-destructive timeline, and dependable MP4 export.

---

## 1. Architecture Overview

SyncCut is organized with Clean Architecture and unidirectional data flow (UDF):

```
                        ┌────────────────────────┐
                        │      UI Layer          │
                        │  (Jetpack Compose M3)  │
                        └───────────┬────────────┘
                                    │ Events & State
                        ┌───────────▼────────────┐
                        │   ViewModel / State    │
                        └───────────┬────────────┘
                                    │
       ┌────────────────────────────┼────────────────────────────┐
       ▼                            ▼                            ▼
┌──────────────┐             ┌──────────────┐             ┌──────────────┐
│ Sync Engine  │             │Timeline Engine│             │Auto-Cut Engine│
│(Waveform/PTS)│             │(EDL/Undo/Snap)│             │(Energy/Black)│
└──────┬───────┘             └──────┬───────┘             └──────┬───────┘
       │                            │                            │
       └────────────────────────────┼────────────────────────────┘
                                    │
                        ┌───────────▼────────────┐
                        │     Domain & Data      │
                        │  (Room DB, Repository) │
                        └───────────┬────────────┘
                                    │
       ┌────────────────────────────┴────────────────────────────┐
       ▼                                                         ▼
┌────────────────────────┐                             ┌────────────────────────┐
│    Playback Engine     │                             │     Export Engine      │
│  (Media3 / ExoPlayer)  │                             │ (Media3 / MediaCodec)  │
└────────────────────────┘                             └────────────────────────┘
```

### Module / Package Structure
- `com.synccut.app.core.model`: Pure domain models (`Project`, `SourceMedia`, `TimelineTrack`, `TimelineClip`, `SyncGroup`, `SyncOffset`, `EditSuggestion`, `EditHistory`, `TimeCode`, `Rational`). High-resolution time base is **microseconds (`us`)** throughout the engine.
- `com.synccut.app.data.local`: Room database, entities, DAOs, type converters, and repository implementations with transactional autosave and crash recovery.
- `com.synccut.app.engine.sync`: Audio waveform cross-correlation engine, normalized cross-correlation (NCC), peak finding, timestamp alignment, and confidence scoring.
- `com.synccut.app.engine.autocut`: Rule-based deterministic detector (silence detection, RMS energy envelopes, black frame detection, motion difference scoring, speech boundary protection).
- `com.synccut.app.engine.timeline`: Non-destructive Edit Decision List (EDL) manipulation, clip snapping, boundary trimming, playhead splitting, and undo/redo history stack.
- `com.synccut.app.engine.playback`: Media3/ExoPlayer timeline player, proxy controller, frame stepping, and surface renderer.
- `com.synccut.app.engine.export`: Media3 Transformer and hardware MediaCodec/MediaMuxer export pipeline, foreground service with notification, cancellation, and output file verification.
- `com.synccut.app.ui`: Jetpack Compose UI:
  - `Home`: Project management, recent projects, crash recovery.
  - `Setup`: Project aspect ratio, resolution, framerate, audio mix rate.
  - `MediaImport`: Media picker, metadata extraction, waveform generation.
  - `Sync`: Waveform matching, master clip selection, offset adjustment.
  - `AutoCut`: Cut suggestions review screen with in/out preview and reason display.
  - `Timeline`: Multi-track timeline, pinch-to-zoom, snap-to-edge, trim handles, playhead.
  - `Export`: Resolution, codec (H.264 default), progress, file verification, and share sheet.

---

## 2. Technical Stack & Dependencies

- **Language**: Kotlin 1.9.24 / JVM 17
- **UI Framework**: Android Jetpack Compose (BOM 2024.04.01), Material 3
- **Media Engine**: AndroidX Media3 1.3.1 (`media3-exoplayer`, `media3-ui`, `media3-transformer`, `media3-common`)
- **Database**: Room 2.6.1 with KSP
- **Concurrency**: Kotlin Coroutines 1.8.0, StateFlow, Flow
- **Background Tasks**: AndroidX WorkManager 2.9.0
- **Build System**: Gradle 8.6 with Android Gradle Plugin 8.3.2

---

## 3. Media Pipeline Architecture & Limitations

### 3.1 Time Base & Accuracy
All timeline offsets, clip boundaries, and synchronization offsets are stored in **64-bit integer microseconds (`Long` in `us`)**. Frame numbers are derived dynamically using the project rational frame rate (`fpsNumerator / fpsDenominator`), avoiding rounding drift over long timelines.

### 3.2 Hardware Codec & Playback Constraints
- Mobile hardware decoders have strict limits on simultaneous active instances (typically 4–8 hardware decoders). 
- To avoid decoder exhaustion when multiple clips exist, SyncCut uses **sequential playback** in ExoPlayer (concatenated media sources) and preloads only adjacent timeline segments rather than initializing decoders for every clip at once.
- Adaptive preview proxies are generated for 4K / high-bitrate media to ensure smooth scrubbing and playback.

### 3.3 Synchronization Limitations
- **Audio Waveform Sync**: Uses downsampled mono PCM audio (e.g., 8kHz or 16kHz) for normalized cross-correlation. Works best when cameras recorded within audible distance of the same sound source. If audio levels are too low, clipped, or contaminated with uncorrelated noise, the sync engine flags a low confidence score (< 0.6) and asks the user for manual or timestamp sync.
- **Timestamp Sync**: Extracts EXIF/MP4 `creation_time`. Flags uncertainty if files have undergone transcoding, cloud messaging compression, or system clock divergence.

### 3.4 Export Pipeline
- Default target: H.264 / AAC in an MP4 container.
- Verification step: Following export, the file is inspected via `MediaExtractor` / `MediaMetadataRetriever` to verify non-zero byte size, expected duration, playable tracks, and presentation timestamps before notifying completion.

---

## 4. Development Milestones & Checklist

- [x] **Milestone 1 — Foundation**
  - [x] Project architecture & Gradle build configuration
  - [x] Domain models & typed time units (`TimeCode`, microseconds `us`)
  - [x] Room database schema & DAOs (`SyncCutDatabase`)
  - [x] Media inspection & metadata extraction
  - [x] Unit tests for project models & database repository
- [ ] **Milestone 2 — Playback Engine**
  - [ ] ExoPlayer / Media3 player integration
  - [ ] Timeline preview for sequential clips
  - [ ] Frame scrubbing and waveform preview
- [ ] **Milestone 3 — Timeline Engine**
  - [ ] Multi-track timeline model (Video & Audio tracks)
  - [ ] Trim, split, reorder, delete, and snap-to-edge
  - [ ] Undo/redo history stack
- [ ] **Milestone 4 — Synchronization Engine**
  - [ ] Audio feature extraction & normalized cross-correlation (NCC)
  - [ ] Timestamp alignment with trustworthiness check
  - [ ] Manual offset adjustment & confidence indicator
- [ ] **Milestone 5 — Auto-Cut Engine**
  - [ ] Silence & audio energy detection
  - [ ] Black frame & motion activity detection
  - [ ] Reviewable cut suggestions & user approval workflow
- [ ] **Milestone 6 — Export Engine**
  - [ ] Media3 Transformer / MediaCodec render pipeline
  - [ ] Background export service with progress & cancellation
  - [ ] Post-export file verification & Android Share Sheet
- [ ] **Milestone 7 — Polish & Diagnostics**
  - [ ] Touch target optimization (48dp hit targets)
  - [ ] Memory caching, crash recovery, diagnostics report
