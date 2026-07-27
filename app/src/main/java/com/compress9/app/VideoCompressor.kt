package com.compress9.app

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.FFprobeKit
import com.arthenica.ffmpegkit.LogCallback
import com.arthenica.ffmpegkit.ReturnCode
import kotlinx.coroutines.*
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.math.min

class VideoCompressor(private val context: Context) {

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
        val startTime = System.currentTimeMillis()
        var lastLogTime = startTime

        fun elapsed() = (System.currentTimeMillis() - startTime) / 1000
        fun sinceLast() = (System.currentTimeMillis() - lastLogTime) / 1000
        fun mark() { lastLogTime = System.currentTimeMillis() }

        try {
            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val cacheInput = File(context.cacheDir, "input_${timeStamp}.mp4")
            val cacheOutput = File(context.cacheDir, "output_${timeStamp}.mp4")

            onStatus("Copying input file... (${elapsed()}s)")
            context.contentResolver.openInputStream(inputUri)?.use { input ->
                cacheInput.outputStream().use { input.copyTo(it) }
            } ?: return@withContext CompressionResult("", false, "Cannot read file")
            onStatus("Copy done in ${elapsed()}s")
            mark()

            onProgress(3)
            onStatus("Analyzing video... (${elapsed()}s)")

            var durationUs = 0L
            try {
                val probeSession = FFprobeKit.getMediaInformation(cacheInput.absolutePath)
                val dur = probeSession.mediaInformation?.duration
                if (dur != null) {
                    durationUs = ((dur.toDoubleOrNull() ?: 0.0) * 1_000_000).toLong()
                }
            } catch (_: Exception) {}
            onStatus("FFprobe done in ${elapsed()}s, duration=${durationUs}us")
            mark()

            onProgress(5)
            onStatus("Compressing video...")

            val bitrate = bitrateForQuality(quality)
            val scale = scaleForQuality(quality)

            // Try encoders in order
            val attempts = listOf(
                // 1: HW encoder with downscale
                "" to "-c:v h264_mediacodec -b:v ${bitrate}k -vf scale=${scale}:-2",
                // 2: HW encoder original resolution  
                "" to "-c:v h264_mediacodec -b:v ${bitrate}k",
                // 3: Built-in mpeg4
                "" to "-c:v mpeg4 -q:v ${qualityToQscale(quality)}"
            )

            var result: CompressionResult? = null
            var attemptNum = 0

            for ((_, encOpts) in attempts) {
                attemptNum++
                if (result != null) break
                onStatus("Attempt $attemptNum: ${encOpts.take(50)}... (${elapsed()}s)")

                val cmd = "-y -i \"${cacheInput.absolutePath}\" " +
                        "$encOpts " +
                        "-c:a aac -b:a 96k " +
                        "-movflags +faststart " +
                        "\"${cacheOutput.absolutePath}\""

                val encodeResult = coroutineScope {
                    val deferred = CompletableDeferred<CompressionResult>()
                    var lastPct = 5

                    val timerJob = launch {
                        while (isActive) {
                            delay(3000)
                            val elapsedSec = ((System.currentTimeMillis() - startTime) / 1000).toInt()
                            val fakePct = minOf(90, 5 + (elapsedSec * 85 / 300))
                            if (fakePct > lastPct) {
                                lastPct = fakePct
                                onProgress(fakePct)
                            }
                            val rem = 300 - elapsedSec
                            if (rem > 0) onStatus("~${rem / 60}m ${rem % 60}s (${elapsedSec}s elapsed)")
                        }
                    }

                    FFmpegKit.executeAsync(
                        cmd,
                        { session ->
                            timerJob.cancel()
                            if (ReturnCode.isSuccess(session.returnCode)) {
                                val publicUri = saveToPublic(cacheOutput, timeStamp)
                                cacheOutput.delete()
                                if (publicUri != null) {
                                    deferred.complete(CompressionResult(publicUri.toString(), true,
                                        "Done in ${elapsed()}s (attempt $attemptNum)"))
                                } else {
                                    deferred.complete(CompressionResult(cacheOutput.absolutePath, true,
                                        "Done in ${elapsed()}s (attempt $attemptNum)"))
                                }
                            } else {
                                deferred.complete(CompressionResult("", false,
                                    "Attempt $attemptNum failed: ${session.failStackTrace?.take(100) ?: "Unknown"}"))
                            }
                        },
                        LogCallback { log ->
                            val msg = log.message ?: return@LogCallback
                            if (msg.startsWith("out_time_us=")) {
                                val timeUs = msg.substringAfter("=").trim().toLongOrNull()
                                if (timeUs != null && timeUs > 0 && durationUs > 0) {
                                    val pct = ((timeUs * 90) / durationUs).toInt().coerceIn(5, 95)
                                    if (pct > lastPct) {
                                        lastPct = pct
                                        onProgress(pct)
                                        val elapsedSec = ((System.currentTimeMillis() - startTime) / 1000).toInt()
                                        if (pct > 5) {
                                            val total = (elapsedSec * 100 / pct).toInt()
                                            val rem = total - elapsedSec
                                            if (rem > 0) onStatus("~${rem / 60}m ${rem % 60}s remaining")
                                        }
                                    }
                                }
                            }
                        },
                        null
                    )
                    deferred.await()
                }

                if (encodeResult.success) {
                    result = encodeResult
                } else {
                    cacheOutput.delete()
                }
            }

            cacheInput.delete()

            if (result == null) {
                result = CompressionResult("", false, "All encoders failed (${elapsed()}s)")
            }

            return@withContext result!!

        } catch (e: Throwable) {
            return@withContext CompressionResult("", false,
                "${e.javaClass.simpleName}: ${e.message ?: "Error"} (${elapsed()}s)")
        }
    }

    private fun saveToPublic(file: File, timeStamp: String): Uri? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Video.Media.DISPLAY_NAME, "compressed_$timeStamp.mp4")
                    put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                    put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/Compress9")
                    put(MediaStore.Video.Media.IS_PENDING, 1)
                }
                val uri = context.contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
                uri?.let {
                    context.contentResolver.openOutputStream(it)?.use { os ->
                        file.inputStream().use { input -> input.copyTo(os) }
                    }
                    values.clear()
                    values.put(MediaStore.Video.Media.IS_PENDING, 0)
                    context.contentResolver.update(it, values, null, null)
                }
                uri
            } else {
                val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES)
                val outDir = File(dir, "Compress9")
                outDir.mkdirs()
                val outFile = File(outDir, "compressed_$timeStamp.mp4")
                file.inputStream().use { input ->
                    outFile.outputStream().use { input.copyTo(it) }
                }
                Uri.fromFile(outFile)
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun bitrateForQuality(quality: Int): Int = when {
        quality >= 90 -> 1500
        quality >= 75 -> 800
        quality >= 50 -> 400
        quality >= 25 -> 200
        else -> 100
    }

    private fun qualityToQscale(quality: Int): Int = when {
        quality >= 90 -> 5
        quality >= 75 -> 8
        quality >= 50 -> 12
        quality >= 25 -> 16
        else -> 20
    }

    private fun scaleForQuality(quality: Int): Int = when {
        quality >= 90 -> 1920
        quality >= 75 -> 1280
        quality >= 50 -> 854
        else -> 640
    }
}
