package com.compress9.app

import android.content.ContentValues
import android.content.Context
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class NativeVideoCompressor(private val context: Context) {

    data class CompressionResult(
        val outputPath: String,
        val success: Boolean,
        val message: String
    )

    suspend fun compress(
        inputUri: Uri,
        quality: Int,
        onProgress: (Int) -> Unit,
        onStatus: (String) -> Unit = {}
    ): CompressionResult = withContext(Dispatchers.IO) {
        val t0 = System.currentTimeMillis()
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val cacheInput = File(context.cacheDir, "ni_${timeStamp}.mp4")
        val cacheOutput = File(context.cacheDir, "no_${timeStamp}.mp4")

        fun elapsed() = (System.currentTimeMillis() - t0) / 1000

        try {
            onStatus("Copying (${elapsed()}s)...")
            context.contentResolver.openInputStream(inputUri)?.use { input ->
                cacheInput.outputStream().use { input.copyTo(it) }
            } ?: return@withContext CompressionResult("", false, "Cannot read input")
            onStatus("Copy done (${elapsed()}s)")

            onProgress(3)
            onStatus("Analyzing (${elapsed()}s)...")

            val extractor = MediaExtractor()
            extractor.setDataSource(cacheInput.absolutePath)

            var videoTrackIdx = -1
            var videoFmt: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val f = extractor.getTrackFormat(i)
                if (f.getString(MediaFormat.KEY_MIME)?.startsWith("video/") == true) {
                    videoTrackIdx = i; videoFmt = f; break
                }
            }
            if (videoTrackIdx < 0) return@withContext CompressionResult("", false, "No video track")

            val srcMime = videoFmt!!.getString(MediaFormat.KEY_MIME)!!
            val srcW = videoFmt!!.getInteger(MediaFormat.KEY_WIDTH)
            val srcH = videoFmt!!.getInteger(MediaFormat.KEY_HEIGHT)
            val durationUs = try { videoFmt!!.getLong(MediaFormat.KEY_DURATION) } catch (_: Exception) { 0L }

            // Aggressive downscale for speed
            val maxDim = 640
            val s = if (srcW > srcH) maxDim.toFloat() / srcW else maxDim.toFloat() / srcH
            val outW = (srcW * s).toInt().coerceAtLeast(160) / 2 * 2
            val outH = (srcH * s).toInt().coerceAtLeast(144) / 2 * 2
            val bitrate = bitrateForQuality(quality) * 1000
            val outFps = 24

            onStatus("Encoding ${outW}x${outH} @ ${bitrate/1000}k (${elapsed()}s)...")

            // Encoder
            val encFmt = MediaFormat.createVideoFormat("video/avc", outW, outH).apply {
                setInteger(MediaFormat.KEY_BIT_RATE, bitrate)
                setInteger(MediaFormat.KEY_FRAME_RATE, outFps)
                setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 3)
                setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
            }
            val encoder = MediaCodec.createEncoderByType("video/avc")
            encoder.configure(encFmt, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            val encSurface = encoder.createInputSurface()
            encoder.start()

            // Decoder
            val decoder = MediaCodec.createDecoderByType(srcMime)
            decoder.configure(videoFmt, encSurface, null, 0)
            decoder.start()

            // Muxer
            val muxer = MediaMuxer(cacheOutput.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            var muxerTrack = -1
            var muxerStarted = false

            extractor.selectTrack(videoTrackIdx)

            var inputEOS = false
            var outputEOS = false
            val decInfo = MediaCodec.BufferInfo()
            val encInfo = MediaCodec.BufferInfo()
            var frames = 0
            var lastPct = 5

            while (!outputEOS) {
                if (!inputEOS) {
                    val inIdx = decoder.dequeueInputBuffer(5000L)
                    if (inIdx >= 0) {
                        val buf = decoder.getInputBuffer(inIdx)!!
                        val sz = extractor.readSampleData(buf, 0)
                        if (sz < 0) {
                            decoder.queueInputBuffer(inIdx, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputEOS = true
                        } else {
                            decoder.queueInputBuffer(inIdx, 0, sz, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }

                // Decode and render to surface
                val dIdx = decoder.dequeueOutputBuffer(decInfo, 5000L)
                if (dIdx >= 0) {
                    decoder.releaseOutputBuffer(dIdx, true)
                    frames++
                }

                // Collect encoder output
                while (true) {
                    val eIdx = encoder.dequeueOutputBuffer(encInfo, 5000L)
                    when {
                        eIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                            if (muxerTrack < 0) muxerTrack = muxer.addTrack(encoder.outputFormat)
                        }
                        eIdx >= 0 -> {
                            val eBuf = encoder.getOutputBuffer(eIdx)!!
                            if ((encInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                                encoder.releaseOutputBuffer(eIdx, false)
                                continue
                            }
                            if (!muxerStarted && muxerTrack >= 0) {
                                muxer.start(); muxerStarted = true
                            }
                            if (muxerStarted && encInfo.size > 0) {
                                muxer.writeSampleData(muxerTrack, eBuf, encInfo)
                            }
                            encoder.releaseOutputBuffer(eIdx, false)
                            if ((encInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                                outputEOS = true
                            }
                        }
                        eIdx == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                            break
                        }
                    }
                    if (outputEOS) break
                }

                // Progress
                if (durationUs > 0 && extractor.sampleTime > 0) {
                    val pct = ((extractor.sampleTime * 90) / durationUs).toInt().coerceIn(5, 95)
                    if (pct > lastPct) {
                        lastPct = pct; onProgress(pct)
                        val es = elapsed().toInt()
                        val tot = if (pct > 5) (es * 100 / pct) else 0
                        val rem = tot - es
                        onStatus("${frames}f encoded (${es}s) ~${rem/60}m ${rem%60}s left")
                    }
                }
            }

            onStatus("Finalizing (${elapsed()}s)...")
            encoder.stop(); encoder.release()
            decoder.stop(); decoder.release()
            extractor.release()
            if (muxerStarted) { muxer.stop(); muxer.release() } else { muxer.release(); return@withContext CompressionResult("", false, "Muxer error") }

            cacheInput.delete()

            val uri = saveToPublic(cacheOutput, timeStamp)
            cacheOutput.delete()
            val totalTime = elapsed().toInt()

            onProgress(100)
            onStatus("Done! ${frames}f in ${totalTime/60}m ${totalTime%60}s")

            if (uri != null) return@withContext CompressionResult(uri.toString(), true, "${frames}f - ${totalTime}s")
            return@withContext CompressionResult("", false, "Failed to save output")
        } catch (e: Throwable) {
            cacheInput.delete(); cacheOutput.delete()
            return@withContext CompressionResult("", false, "${e.javaClass.simpleName}: ${e.message} (${elapsed()}s)")
        }
    }

    private fun saveToPublic(file: File, ts: String): Uri? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val v = ContentValues().apply {
                    put(MediaStore.Video.Media.DISPLAY_NAME, "compressed_$ts.mp4")
                    put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                    put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/Compress9")
                }
                val uri = context.contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, v)
                uri?.let {
                    context.contentResolver.openOutputStream(it)?.use { os ->
                        file.inputStream().use { `in` -> `in`.copyTo(os) }
                    }
                }
                uri
            } else {
                val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES), "Compress9")
                dir.mkdirs()
                val out = File(dir, "compressed_$ts.mp4")
                file.inputStream().use { `in` -> out.outputStream().use { `in`.copyTo(it) } }
                Uri.fromFile(out)
            }
        } catch (_: Exception) { null }
    }

    private fun bitrateForQuality(q: Int): Int = when {
        q >= 90 -> 300
        q >= 75 -> 200
        q >= 50 -> 150
        q >= 25 -> 100
        else -> 80
    }
}
