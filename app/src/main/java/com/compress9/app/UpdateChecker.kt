package com.compress9.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

data class UpdateInfo(
    val hasUpdate: Boolean,
    val latestVersion: String = "",
    val downloadUrl: String = "",
    val releaseNotes: String = ""
)

class UpdateChecker(private val context: Context) {

    companion object {
        private const val API_URL = "https://api.github.com/repos/glitchsumon/compress9/releases/latest"
        private const val REPO_URL = "https://github.com/glitchsumon/compress9"
    }

    suspend fun check(): UpdateInfo = withContext(Dispatchers.IO) {
        try {
            val conn = URL(API_URL).openConnection() as HttpURLConnection
            conn.setRequestProperty("Accept", "application/vnd.github.v3+json")
            conn.connectTimeout = 8000
            conn.readTimeout = 8000

            val body = conn.inputStream.bufferedReader().readText()
            conn.disconnect()

            val tag = body.substringAfter("\"tag_name\":\"").substringBefore("\"")
            val notes = body.substringAfter("\"body\":\"").substringBefore("\"")
                .replace("\\r\\n", "\n").replace("\\n", "\n")
            val assetsUrl = body.substringAfter("\"browser_download_url\":\"")
                .substringBefore("\"")

            val current = context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "0.0.0"
            val hasUpdate = compareVersions(tag, current) > 0

            return@withContext UpdateInfo(
                hasUpdate = hasUpdate,
                latestVersion = tag,
                downloadUrl = assetsUrl.ifEmpty { "$REPO_URL/releases/tag/$tag" },
                releaseNotes = notes
            )
        } catch (_: Exception) {
            return@withContext UpdateInfo(false)
        }
    }

    fun openDownloadPage() {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("$REPO_URL/releases"))
        context.startActivity(intent)
    }

    private fun compareVersions(v1: String, v2: String): Int {
        val parts1 = v1.trimStart('v').split(".").map { it.toIntOrNull() ?: 0 }
        val parts2 = v2.trimStart('v').split(".").map { it.toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(parts1.size, parts2.size)) {
            val a = parts1.getOrElse(i) { 0 }
            val b = parts2.getOrElse(i) { 0 }
            if (a != b) return a - b
        }
        return 0
    }
}
