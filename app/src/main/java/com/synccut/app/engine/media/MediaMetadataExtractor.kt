package com.synccut.app.engine.media

import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import com.synccut.app.core.model.MediaType
import com.synccut.app.core.model.SourceMedia
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class MediaMetadataExtractor(private val context: Context) {

    suspend fun extractMetadata(
        projectId: String,
        uri: Uri,
        displayName: String,
        fileSize: Long,
        forcedMediaType: MediaType? = null
    ): Result<SourceMedia> = withContext(Dispatchers.IO) {
        val retriever = MediaMetadataRetriever()
        val extractor = MediaExtractor()

        try {
            if (uri.scheme == "file") {
                val file = File(uri.path ?: "")
                if (!file.exists()) {
                    return@withContext Result.failure(Exception("File does not exist: ${uri.path}"))
                }
                retriever.setDataSource(file.absolutePath)
                extractor.setDataSource(file.absolutePath)
            } else {
                retriever.setDataSource(context, uri)
                extractor.setDataSource(context, uri, null)
            }

            // Duration in microseconds
            val durationMsStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            val durationUs = (durationMsStr?.toLongOrNull() ?: 0L) * 1_000L

            // Video dimensions
            val width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
            val height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
            val rotation = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0

            // Codecs and Frame Rate
            var videoCodec: String? = null
            var audioCodec: String? = null
            var frameRate = 30.0
            val isVfr = false

            val trackCount = extractor.trackCount
            var hasVideoTrack = false
            for (i in 0 until trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: continue

                if (mime.startsWith("video/")) {
                    hasVideoTrack = true
                    videoCodec = mime
                    if (format.containsKey(MediaFormat.KEY_FRAME_RATE)) {
                        frameRate = format.getInteger(MediaFormat.KEY_FRAME_RATE).toDouble()
                    }
                } else if (mime.startsWith("audio/")) {
                    audioCodec = mime
                }
            }

            val determinedType = forcedMediaType ?: if (hasVideoTrack) MediaType.VIDEO else MediaType.AUDIO

            val media = SourceMedia(
                projectId = projectId,
                uri = uri.toString(),
                displayName = displayName,
                durationUs = durationUs,
                width = width,
                height = height,
                rotationDegrees = rotation,
                videoCodec = videoCodec,
                audioCodec = audioCodec,
                frameRate = frameRate,
                isVariableFrameRate = isVfr,
                fileSizeBytes = fileSize,
                permissionValid = true,
                captureTimeMs = null,
                mediaType = determinedType
            )

            Result.success(media)
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            try {
                retriever.release()
            } catch (_: Exception) {}
            try {
                extractor.release()
            } catch (_: Exception) {}
        }
    }
}
