package com.jongjun.hangeoreum.focus

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import com.jongjun.hangeoreum.MainActivity
import com.jongjun.hangeoreum.R
import com.jongjun.hangeoreum.data.Dates
import com.jongjun.hangeoreum.data.Store

/**
 * 열품타 style guard: when the user leaves the app while the timer runs,
 * a full-screen card is drawn over the other app.
 *  - 공부로 돌아가기  -> brings the app back
 *  - 무시하고 계속하기 -> pauses the timer and lets the user stay
 */
object FocusGuard {
    private var view: View? = null
    private val handler = Handler(Looper.getMainLooper())
    private var ticker: Runnable? = null

    fun canDraw(ctx: Context): Boolean = Settings.canDrawOverlays(ctx)

    @SuppressLint("InflateParams")
    fun show(ctx: Context) {
        if (view != null || !canDraw(ctx)) return
        val app = ctx.applicationContext
        val wm = app.getSystemService(WindowManager::class.java) ?: return
        val v = LayoutInflater.from(app).inflate(R.layout.focus_overlay, null)
        val timeView = v.findViewById<TextView>(R.id.ov_time)

        val tick = object : Runnable {
            override fun run() {
                val t = Store.current.timer
                timeView.text = "⏱ " + Dates.clock(t.elapsed(System.currentTimeMillis()))
                handler.postDelayed(this, 1000)
            }
        }
        ticker = tick
        tick.run()

        v.findViewById<View>(R.id.ov_back).setOnClickListener {
            hide(app)
            val i = Intent(app, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
            app.startActivity(i)
        }
        v.findViewById<View>(R.id.ov_ignore).setOnClickListener {
            Store.pauseTimer()
            hide(app)
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        )
        runCatching {
            wm.addView(v, params)
            view = v
        }.onFailure { stopTicker() }
    }

    fun hide(ctx: Context) {
        stopTicker()
        val v = view ?: return
        view = null
        val wm = ctx.applicationContext.getSystemService(WindowManager::class.java)
        runCatching { wm?.removeView(v) }
    }

    private fun stopTicker() {
        ticker?.let { handler.removeCallbacks(it) }
        ticker = null
    }
}
