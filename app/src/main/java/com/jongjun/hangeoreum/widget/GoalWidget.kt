package com.jongjun.hangeoreum.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.view.View
import android.widget.RemoteViews
import com.jongjun.hangeoreum.MainActivity
import com.jongjun.hangeoreum.R
import com.jongjun.hangeoreum.data.Dates
import com.jongjun.hangeoreum.data.Store
import com.jongjun.hangeoreum.data.goal
import com.jongjun.hangeoreum.data.pct
import com.jongjun.hangeoreum.data.studyMsOnDay
import com.jongjun.hangeoreum.data.todayGoals
import com.jongjun.hangeoreum.data.todayProgress
import com.jongjun.hangeoreum.data.upcomingEvents

class GoalWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { manager.updateAppWidget(it, build(context)) }
    }

    companion object {
        fun refresh(context: Context) {
            val mgr = AppWidgetManager.getInstance(context) ?: return
            val ids = mgr.getAppWidgetIds(ComponentName(context, GoalWidget::class.java))
            if (ids.isEmpty()) return
            val views = build(context)
            ids.forEach { mgr.updateAppWidget(it, views) }
        }

        private fun build(context: Context): RemoteViews {
            val d = Store.current
            val today = Dates.today()
            val now = System.currentTimeMillis()
            val v = RemoteViews(context.packageName, R.layout.widget_goal)

            val goals = d.todayGoals(today)
            val p = pct(d.todayProgress(today))
            v.setTextViewText(R.id.w_date, "${Dates.full(today)} · 오늘 할 일 ${goals.count { it.done }}/${goals.size}")
            v.setTextViewText(R.id.w_percent, "$p%")
            v.setProgressBar(R.id.w_progress, 100, p, false)

            val rows = intArrayOf(R.id.w_goal1, R.id.w_goal2, R.id.w_goal3)
            val shown = goals.take(3)
            rows.forEachIndexed { i, id ->
                val g = shown.getOrNull(i)
                if (g == null) {
                    if (i == 0 && goals.isEmpty()) {
                        v.setViewVisibility(id, View.VISIBLE)
                        v.setTextViewText(id, "오늘 할 일을 추가해보세요")
                    } else v.setViewVisibility(id, View.GONE)
                } else {
                    v.setViewVisibility(id, View.VISIBLE)
                    v.setTextViewText(id, (if (g.done) "✓  " else "○  ") + g.title)
                }
            }

            val next = d.upcomingEvents(today).firstOrNull()
            val study = Dates.duration(d.studyMsOnDay(today, now))
            val footer = buildString {
                append("공부 $study")
                if (next != null) {
                    append("  ·  ")
                    append(Dates.relative(next.day))
                    if (next.minute >= 0) append(" ").append(Dates.minuteLabel(next.minute))
                    append(" ").append(next.title)
                }
            }
            v.setTextViewText(R.id.w_footer, footer)

            val t = d.timer
            if (t.active) {
                v.setViewVisibility(R.id.w_timer, View.VISIBLE)
                val base = SystemClock.elapsedRealtime() - t.elapsed(now)
                val label = d.goal(t.goalId)?.title?.let { if (it.length > 8) it.take(8) + "…" else it }
                val fmt = (if (t.running) "● " else "Ⅱ ") + (label?.let { "$it " } ?: "") + "%s"
                v.setChronometer(R.id.w_timer, base, fmt, t.running)
            } else {
                v.setViewVisibility(R.id.w_timer, View.GONE)
                v.setChronometer(R.id.w_timer, SystemClock.elapsedRealtime(), null, false)
            }

            val intent = Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            val pi = PendingIntent.getActivity(
                context, 0, intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            v.setOnClickPendingIntent(R.id.w_root, pi)
            return v
        }
    }
}
