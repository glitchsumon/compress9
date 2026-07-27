package com.compress9.app

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ImageCompressor(private val context: Context) {

    data class CompressionResult(
        val outputPath: String,
        val success: Boolean,
        val message: String,
        val originalSize: Long = 0,
        val compressedSize: Long = 0
    )

    suspend fun compress(
        inputUri: Uri,
        quality: Int,
        onProgress: (Int) -> Unit
    ): CompressionResult = withContext(Dispatchers.IO) {
        try {
            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())

            val inputStream = context.contentResolver.openInputStream(inputUri)
                ?: return@withContext CompressionResult("", false, "Cannot read file")

            val originalBytes = inputStream.readBytes()
            inputStream.close()
            val originalSize = originalBytes.size.toLong()

            val bitmap = BitmapFactory.decodeByteArray(originalBytes, 0, originalBytes.size)
                ?: return@withContext CompressionResult("", false, "Cannot decode image")

            onProgress(30)

            val scaleFactor = when {
                quality >= 80 -> 1.0f
                quality >= 60 -> 0.8f
                quality >= 40 -> 0.6f
                else -> 0.4f
            }
            val outWidth = (bitmap.width * scaleFactor).toInt().coerceAtLeast(100)
            val outHeight = (bitmap.height * scaleFactor).toInt().coerceAtLeast(100)
            val scaled = if (scaleFactor < 1.0f) {
                Bitmap.createScaledBitmap(bitmap, outWidth, outHeight, true)
            } else bitmap

            onProgress(50)

            val cacheFile = File(context.cacheDir, "img_$timeStamp.jpg")
            FileOutputStream(cacheFile).use { out ->
                scaled.compress(Bitmap.CompressFormat.JPEG, quality, out)
            }

            val compressedSize = cacheFile.length()

            if (scaled !== bitmap) scaled.recycle()
            bitmap.recycle()

            // Copy to public Pictures folder
            val publicUri = saveToPublic(cacheFile, timeStamp)
            cacheFile.delete()

            onProgress(100)
            if (publicUri != null) {
                return@withContext CompressionResult(
                    publicUri.toString(), true, "Compression successful",
                    originalSize, compressedSize
                )
            } else {
                return@withContext CompressionResult("", false, "Failed to save to public folder")
            }
        } catch (e: Throwable) {
            return@withContext CompressionResult("", false, "Error: ${e.message}")
        }
    }

    private fun saveToPublic(file: File, timeStamp: String): Uri? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, "compressed_$timeStamp.jpg")
                    put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                    put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Compress9")
                }
                val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                uri?.let {
                    context.contentResolver.openOutputStream(it)?.use { os ->
                        file.inputStream().use { input -> input.copyTo(os) }
                    }
                }
                uri
            } else {
                val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
                val outDir = File(dir, "Compress9")
                outDir.mkdirs()
                val outFile = File(outDir, "compressed_$timeStamp.jpg")
                file.inputStream().use { input ->
                    outFile.outputStream().use { input.copyTo(it) }
                }
                Uri.fromFile(outFile)
            }
        } catch (e: Exception) {
            null
        }
    }
}
