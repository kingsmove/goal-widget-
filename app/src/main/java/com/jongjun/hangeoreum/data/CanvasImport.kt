package com.jongjun.hangeoreum.data

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Reads an Obsidian .canvas (from a file or the app's GitHub repo) and stores it as plan events. */
object CanvasImport {
    const val REPO_URL = "https://raw.githubusercontent.com/kingsmove/goal-widget-/main/plan/plan.canvas"

    fun parseJson(text: String): List<CNode> {
        val arr = JSONObject(text).optJSONArray("nodes") ?: return emptyList()
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            CNode(
                id = o.optString("id", "n$i"),
                type = o.optString("type", "text"),
                text = o.optString("text", ""),
                label = o.optString("label", ""),
                x = o.optDouble("x", 0.0),
                y = o.optDouble("y", 0.0),
                w = o.optDouble("width", 0.0),
                h = o.optDouble("height", 0.0),
                color = if (o.has("color") && !o.isNull("color")) o.optString("color") else null,
            )
        }
    }

    /** Parses and replaces previously imported canvas events. Returns a message for the user. */
    fun apply(text: String): Result<Int> = runCatching {
        val r = CanvasPlan.convert(parseJson(text))
        if (r.error != null) error(r.error)
        Store.replaceImported(CanvasPlan.SOURCE, r.events)
        r.events.size
    }

    suspend fun fromUri(ctx: Context, uri: Uri): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            ctx.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                ?: error("파일을 열 수 없어요")
        }
    }.mapCatching { text -> withContext(Dispatchers.Main) { apply(text).getOrThrow() } }

    suspend fun fromRepo(): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val conn = (URL(REPO_URL + "?t=" + System.currentTimeMillis()).openConnection() as HttpURLConnection).apply {
                connectTimeout = 10_000
                readTimeout = 15_000
                setRequestProperty("User-Agent", "hangeoreum-app")
                setRequestProperty("Cache-Control", "no-cache")
            }
            if (conn.responseCode == 404) error("아직 올라온 플랜 파일이 없어요")
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            conn.disconnect()
            body
        }
    }.mapCatching { text -> withContext(Dispatchers.Main) { apply(text).getOrThrow() } }
}
