package com.jongjun.hangeoreum.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.os.Build
import android.widget.RemoteViews
import androidx.compose.ui.graphics.toArgb
import com.jongjun.hangeoreum.MainActivity
import com.jongjun.hangeoreum.data.Store
import com.jongjun.hangeoreum.ui.Palette
import com.jongjun.hangeoreum.ui.buildPalette
import com.jongjun.hangeoreum.ui.isDarkFor

/** Shared helpers for all home-screen widgets. */
object WidgetKit {
    /** Every widget kind and how to draw it. */
    private val all: List<Pair<Class<*>, (Context) -> RemoteViews>> = listOf(
        GoalWidget::class.java to { c -> GoalWidget.build(c) },
        TodoWidget::class.java to { c -> TodoWidget.build(c) },
        TimerWidget::class.java to { c -> TimerWidget.build(c) },
        DdayWidget::class.java to { c -> DdayWidget.build(c) },
        GoalsWidget::class.java to { c -> GoalsWidget.build(c) },
        EventsWidget::class.java to { c -> EventsWidget.build(c) },
    )

    fun refreshAll(context: Context) {
        val mgr = AppWidgetManager.getInstance(context) ?: return
        all.forEach { (cls, build) ->
            val ids = runCatching { mgr.getAppWidgetIds(ComponentName(context, cls)) }.getOrNull() ?: return@forEach
            if (ids.isEmpty()) return@forEach
            val views = runCatching { build(context) }.getOrNull() ?: return@forEach
            ids.forEach { runCatching { mgr.updateAppWidget(it, views) } }
        }
    }

    fun palette(context: Context): Palette {
        val th = Store.current.theme
        val sysDark = (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
        return buildPalette(isDarkFor(th, sysDark), th)
    }

    /** Applies theme colors. [accent] views get the accent color, etc. */
    fun colorize(
        context: Context,
        v: RemoteViews,
        root: Int,
        text: IntArray = intArrayOf(),
        sub: IntArray = intArrayOf(),
        accent: IntArray = intArrayOf(),
        progress: IntArray = intArrayOf(),
        accentBg: IntArray = intArrayOf(),
    ): Palette {
        val p = palette(context)
        text.forEach { v.setTextColor(it, p.text.toArgb()) }
        sub.forEach { v.setTextColor(it, p.sub.toArgb()) }
        accent.forEach { v.setTextColor(it, p.blue.toArgb()) }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            v.setColorStateList(root, "setBackgroundTintList", ColorStateList.valueOf(p.card.toArgb()))
            progress.forEach {
                v.setColorStateList(it, "setProgressTintList", ColorStateList.valueOf(p.blue.toArgb()))
                v.setColorStateList(it, "setProgressBackgroundTintList", ColorStateList.valueOf(p.line.toArgb()))
            }
            accentBg.forEach { v.setColorStateList(it, "setBackgroundTintList", ColorStateList.valueOf(p.blue.toArgb())) }
        }
        return p
    }

    fun openApp(context: Context, tab: Int = 0): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .putExtra(MainActivity.EXTRA_TAB, tab)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        return PendingIntent.getActivity(
            context, 100 + tab, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    fun action(context: Context, action: String, id: String? = null): PendingIntent {
        val intent = Intent(context, WidgetActions::class.java).setAction(action)
        if (id != null) intent.putExtra(WidgetActions.EXTRA_ID, id)
        val code = (action + (id ?: "")).hashCode()
        return PendingIntent.getBroadcast(
            context, code, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }
}

/** Handles taps on widget buttons (check a to-do, start/stop the timer). */
class WidgetActions : android.content.BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            TOGGLE_GOAL -> intent.getStringExtra(EXTRA_ID)?.let { Store.toggleGoal(it) }
            TIMER_TOGGLE -> if (Store.current.timer.running) Store.pauseTimer() else Store.startTimer()
            TIMER_STOP -> Store.stopTimer()
        }
        WidgetKit.refreshAll(context)
    }

    companion object {
        const val TOGGLE_GOAL = "com.jongjun.hangeoreum.TOGGLE_GOAL"
        const val TIMER_TOGGLE = "com.jongjun.hangeoreum.TIMER_TOGGLE"
        const val TIMER_STOP = "com.jongjun.hangeoreum.TIMER_STOP"
        const val EXTRA_ID = "id"
    }
}
