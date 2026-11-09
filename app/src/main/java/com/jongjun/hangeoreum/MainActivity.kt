package com.jongjun.hangeoreum

import android.app.KeyguardManager
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import android.graphics.Color as AndroidColor
import androidx.activity.SystemBarStyle
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import com.jongjun.hangeoreum.ui.isDarkFor
import com.jongjun.hangeoreum.data.Store
import com.jongjun.hangeoreum.focus.FocusGuard
import com.jongjun.hangeoreum.ui.AppRoot
import com.jongjun.hangeoreum.ui.AppTheme
import com.jongjun.hangeoreum.widget.GoalWidget

class MainActivity : ComponentActivity() {

    private val resumeTick = mutableIntStateOf(0)
    private val handler = Handler(Looper.getMainLooper())
    private var started = false
    private var suppressGuard = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val data by Store.data.collectAsState()
            val dark = isDarkFor(data.theme, isSystemInDarkTheme())
            DisposableEffect(dark) {
                enableEdgeToEdge(
                    statusBarStyle = if (dark) SystemBarStyle.dark(AndroidColor.TRANSPARENT)
                    else SystemBarStyle.light(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT),
                    navigationBarStyle = if (dark) SystemBarStyle.dark(AndroidColor.TRANSPARENT)
                    else SystemBarStyle.light(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT),
                )
                onDispose { }
            }
            AppTheme(data.theme) {
                AppRoot(resumeTick.intValue, ::openOverlaySettings)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        started = true
        suppressGuard = false
        handler.removeCallbacksAndMessages(null)
        FocusGuard.hide(this)
    }

    override fun onResume() {
        super.onResume()
        resumeTick.intValue++
        GoalWidget.refresh(this)
    }

    override fun onStop() {
        super.onStop()
        started = false
        if (isChangingConfigurations || suppressGuard) return
        // Wait a moment so that screen-off / lock can be told apart from switching apps.
        handler.postDelayed({ maybeWarn() }, 350)
    }

    private fun maybeWarn() {
        if (started) return
        val d = Store.current
        if (!d.timer.running || !d.focusGuard) return
        val pm = getSystemService(PowerManager::class.java)
        if (pm != null && !pm.isInteractive) return
        val km = getSystemService(KeyguardManager::class.java)
        if (km != null && km.isKeyguardLocked) return
        FocusGuard.show(applicationContext)
    }

    private fun openOverlaySettings() {
        launchExternal(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
    }

    /** Call right before launching a system picker so leaving the app doesn't count as a focus break. */
    fun expectExternal() {
        suppressGuard = true
    }

    /** Opens another screen (settings, installer) without triggering the focus warning. */
    fun launchExternal(intent: Intent) {
        suppressGuard = true
        runCatching { startActivity(intent) }.onFailure { suppressGuard = false }
    }
}
