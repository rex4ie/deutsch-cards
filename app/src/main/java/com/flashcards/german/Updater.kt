package com.flashcards.german

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** Checks the repo's "latest" GitHub release and installs its APK over the current app. */
object Updater {
    const val REPO = "rex4ie/deutsch-cards"
    const val REPO_URL = "https://github.com/$REPO"

    data class Release(val versionCode: Int, val apkUrl: String, val notes: String)

    suspend fun fetchLatest(): Release = withContext(Dispatchers.IO) {
        val conn = URL("https://api.github.com/repos/$REPO/releases/tags/latest").openConnection() as HttpURLConnection
        conn.setRequestProperty("Accept", "application/vnd.github+json")
        conn.connectTimeout = 15_000; conn.readTimeout = 15_000
        try {
            if (conn.responseCode != 200) error("GitHub answered ${conn.responseCode}")
            val obj = Json.parseToJsonElement(conn.inputStream.bufferedReader().readText()).jsonObject
            val body = obj["body"]?.jsonPrimitive?.contentOrNull.orEmpty()
            val code = Regex("versionCode:\\s*(\\d+)").find(body)?.groupValues?.get(1)?.toInt() ?: 0
            val apk = obj["assets"]!!.jsonArray.map { it.jsonObject }
                .first { it["name"]!!.jsonPrimitive.content.endsWith(".apk") }["browser_download_url"]!!.jsonPrimitive.content
            Release(code, apk, body)
        } finally { conn.disconnect() }
    }

    suspend fun download(ctx: Context, url: String, onProgress: (Float) -> Unit): File = withContext(Dispatchers.IO) {
        val dir = File(ctx.cacheDir, "updates").apply { mkdirs(); listFiles()?.forEach { it.delete() } }
        val out = File(dir, "update.apk")
        var current = URL(url)
        var conn: HttpURLConnection
        // follow GitHub's redirect to its storage host manually (cross-host redirects aren't automatic)
        while (true) {
            conn = current.openConnection() as HttpURLConnection
            conn.instanceFollowRedirects = false
            conn.connectTimeout = 15_000; conn.readTimeout = 30_000
            if (conn.responseCode in 300..399) { current = URL(conn.getHeaderField("Location")); conn.disconnect() } else break
        }
        if (conn.responseCode != 200) error("Download failed (${conn.responseCode})")
        val total = conn.contentLengthLong.toFloat()
        conn.inputStream.use { input ->
            out.outputStream().use { output ->
                val buf = ByteArray(64 * 1024); var read = 0L
                while (true) {
                    val n = input.read(buf); if (n < 0) break
                    output.write(buf, 0, n); read += n
                    if (total > 0) onProgress(read / total)
                }
            }
        }
        conn.disconnect()
        out
    }

    fun canInstall(ctx: Context) = Build.VERSION.SDK_INT < 26 || ctx.packageManager.canRequestPackageInstalls()

    fun openInstallPermission(ctx: Context) {
        ctx.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${ctx.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    fun install(ctx: Context, apk: File) {
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", apk)
        ctx.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
