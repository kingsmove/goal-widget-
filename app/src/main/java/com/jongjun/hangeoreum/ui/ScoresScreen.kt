package com.jongjun.hangeoreum.ui

import android.annotation.SuppressLint
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.jongjun.hangeoreum.data.Store

/**
 * 성적: the same screen as the PC version (assets/ufk.html), running inside the app.
 * The page reads and writes the app's own data through [Bridge]; the app keeps doing the Firebase sync.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun ScoresScreen(onBack: () -> Unit) {
    val bridge = remember { Bridge() }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(start = 8.dp, top = 6.dp, bottom = 0.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, "뒤로", Modifier.size(44.dp).bounceClick(onClick = onBack).padding(10.dp), tint = pal.text)
        }
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                WebView(ctx).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.allowFileAccess = true
                    setBackgroundColor(android.graphics.Color.TRANSPARENT)
                    webViewClient = WebViewClient()
                    webChromeClient = WebChromeClient()   // confirm()/prompt() dialogs
                    addJavascriptInterface(bridge, "UFKAndroid")
                    loadUrl("file:///android_asset/ufk.html#scores")
                }
            },
            onRelease = { it.destroy() },
        )
    }
}

private class Bridge {
    private val main = Handler(Looper.getMainLooper())

    @JavascriptInterface
    fun load(): String = Store.exportJson()

    @JavascriptInterface
    fun save(json: String) {
        main.post { Store.applyJson(json) }
    }
}
