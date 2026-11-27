package com.synccut.app.engine.sync

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sqrt

/**
 * High-performance hardware audio decoder that extracts downsampled RMS energy envelopes
 * directly from video and audio containers for waveform cross-correlation.
 */
class AudioPcmExtractor(private val context: Context) {

    companion object {
        private const val TAG = "AudioPcmExtractor"
        const val TARGET_ENVELOPE_SAMPLE_RATE_HZ = 200 // 1 sample every 5ms (200 Hz)
    }

    /**
     * Decodes audio stream from [uri] and returns an RMS energy envelope array at [TARGET_ENVELOPE_SAMPLE_RATE_HZ].
     */
    suspend fun extractRmsEnvelope(uriStr: Uri): FloatArray = withContext(Dispatchers.IO) {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null

        try {
            if (uriStr.scheme == "file") {
                val file = File(uriStr.path ?: "")
                if (!file.exists()) return@withContext FloatArray(0)
                extractor.setDataSource(file.absolutePath)
            } else {
                extractor.setDataSource(context, uriStr, null)
            }

            var audioTrackIndex = -1
            var audioFormat: MediaFormat? = null

            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
                if (mime.startsWith("audio/")) {
                    audioTrackIndex = i
                    audioFormat = format
                    break
                }
            }

            if (audioTrackIndex == -1 || audioFormat == null) {
                Log.w(TAG, "No audio track found in $uriStr")
                return@withContext FloatArray(0)
            }

            extractor.selectTrack(audioTrackIndex)
            val mime = audioFormat.getString(MediaFormat.KEY_MIME) ?: return@withContext FloatArray(0)
            val sampleRate = if (audioFormat.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                audioFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            } else 44100
            val channelCount = if (audioFormat.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                audioFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            } else 2

            codec = MediaCodec.createDecoderByType(mime)
            codec.configure(audioFormat, null, null, 0)
            codec.start()

            val envelopeList = mutableListOf<Float>()
            val samplesPerEnvelope = (sampleRate / TARGET_ENVELOPE_SAMPLE_RATE_HZ).coerceAtLeast(1)

            val bufferInfo = MediaCodec.BufferInfo()
            var isEos = false
            var currentBlockSumSq = 0.0
            var currentBlockSampleCount = 0

            val timeoutUs = 5000L

            while (!isEos) {
                val inIndex = codec.dequeueInputBuffer(timeoutUs)
                if (inIndex >= 0) {
                    val inBuffer = codec.getInputBuffer(inIndex)
                    if (inBuffer != null) {
                        val sampleSize = extractor.readSampleData(inBuffer, 0)
                        if (sampleSize < 0) {
                            codec.queueInputBuffer(inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            isEos = true
                        } else {
                            val presentationTimeUs = extractor.sampleTime
                            codec.queueInputBuffer(inIndex, 0, sampleSize, presentationTimeUs, 0)
                            extractor.advance()
                        }
                    }
                }

                var outIndex = codec.dequeueOutputBuffer(bufferInfo, timeoutUs)
                while (outIndex >= 0) {
                    val outBuffer = codec.getOutputBuffer(outIndex)
                    if (outBuffer != null && bufferInfo.size > 0) {
                        outBuffer.position(bufferInfo.offset)
                        outBuffer.limit(bufferInfo.offset + bufferInfo.size)
                        outBuffer.order(ByteOrder.LITTLE_ENDIAN)

                        val shortBuffer = outBuffer.asShortBuffer()
                        while (shortBuffer.hasRemaining()) {
                            // Average channels to mono
                            var sampleSum = 0.0
                            for (c in 0 until channelCount) {
                                if (shortBuffer.hasRemaining()) {
                                    val s = shortBuffer.get() / 32768.0
                                    sampleSum += s
                                }
                            }
                            val monoSample = sampleSum / channelCount
                            currentBlockSumSq += monoSample * monoSample
                            currentBlockSampleCount++

                            if (currentBlockSampleCount >= samplesPerEnvelope) {
                                val rms = sqrt(currentBlockSumSq / currentBlockSampleCount).toFloat()
                                envelopeList.add(rms)
                                currentBlockSumSq = 0.0
                                currentBlockSampleCount = 0
                            }
                        }
                    }

                    codec.releaseOutputBuffer(outIndex, false)
                    if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        isEos = true
                        break
                    }
                    outIndex = codec.dequeueOutputBuffer(bufferInfo, timeoutUs)
                }
            }

            if (currentBlockSampleCount > 0) {
                val rms = sqrt(currentBlockSumSq / currentBlockSampleCount).toFloat()
                envelopeList.add(rms)
            }

            return@withContext envelopeList.toFloatArray()

        } catch (e: Exception) {
            Log.e(TAG, "Audio extraction error for $uriStr", e)
            return@withContext FloatArray(0)
        } finally {
            try {
                codec?.stop()
                codec?.release()
            } catch (_: Exception) {}
            try {
                extractor.release()
            } catch (_: Exception) {}
        }
    }
}
