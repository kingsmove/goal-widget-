package com.jongjun.hangeoreum.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import androidx.core.content.pm.PackageInfoCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

data class UpdateInfo(val build: Long, val name: String, val apkUrl: String, val notes: String)

/**
 * Checks GitHub Releases of the app repo and installs a newer APK.
 * Builds are numbered by the CI run number, which is also the versionCode.
 */
object Updater {
    private const val API = "https://api.github.com/repos/kingsmove/goal-widget-/releases/latest"

    fun currentBuild(ctx: Context): Long = runCatching {
        PackageInfoCompat.getLongVersionCode(ctx.packageManager.getPackageInfo(ctx.packageName, 0))
    }.getOrDefault(0L)

    fun currentName(ctx: Context): String = runCatching {
        ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: ""
    }.getOrDefault("")

    /** Returns info about the newest release if it is newer than the installed one. */
    suspend fun check(ctx: Context): Result<UpdateInfo?> = withContext(Dispatchers.IO) {
        runCatching {
            val conn = (URL(API).openConnection() as HttpURLConnection).apply {
                connectTimeout = 10_000
                readTimeout = 10_000
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("User-Agent", "hangeoreum-app")
            }
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            conn.disconnect()
            val o = JSONObject(body)
            val tag = o.optString("tag_name")
            val build = tag.substringAfterLast('-').toLongOrNull() ?: 0L
            val assets = o.optJSONArray("assets")
            var url = ""
            if (assets != null) {
                for (i in 0 until assets.length()) {
                    val a = assets.getJSONObject(i)
                    if (a.optString("name").endsWith(".apk")) {
                        url = a.optString("browser_download_url"); break
                    }
                }
            }
            if (build > currentBuild(ctx) && url.isNotEmpty()) {
                UpdateInfo(build, o.optString("name", tag), url, o.optString("body", ""))
            } else null
        }
    }

    suspend fun download(ctx: Context, info: UpdateInfo, onProgress: (Float) -> Unit): Result<File> =
        withContext(Dispatchers.IO) {
            runCatching {
                val dir = File(ctx.cacheDir, "updates").apply { mkdirs() }
                dir.listFiles()?.forEach { it.delete() }
                val out = File(dir, "update-${info.build}.apk")
                fun open(u: URL): HttpURLConnection = (u.openConnection() as HttpURLConnection).apply {
                    connectTimeout = 15_000
                    readTimeout = 30_000
                    instanceFollowRedirects = false
                    setRequestProperty("User-Agent", "hangeoreum-app")
                }
                var url = URL(info.apkUrl)
                var conn = open(url)
                var hops = 0
                while (conn.responseCode in 300..399 && hops < 5) {
                    val loc = conn.getHeaderField("Location") ?: break
                    url = URL(url, loc)
                    conn.disconnect()
                    conn = open(url)
                    hops++
                }
                val total = conn.contentLengthLong
                conn.inputStream.use { input ->
                    out.outputStream().use { output ->
                        val buf = ByteArray(64 * 1024)
                        var read = 0L
                        while (true) {
                            val n = input.read(buf)
                            if (n < 0) break
                            output.write(buf, 0, n)
                            read += n
                            if (total > 0) withContext(Dispatchers.Main) { onProgress(read.toFloat() / total) }
                        }
                    }
                }
                conn.disconnect()
                out
            }
        }

    fun canInstall(ctx: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O || ctx.packageManager.canRequestPackageInstalls()

    fun installPermissionIntent(ctx: Context): Intent =
        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${ctx.packageName}"))

    fun installIntent(ctx: Context, apk: File): Intent {
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", apk)
        return Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}
