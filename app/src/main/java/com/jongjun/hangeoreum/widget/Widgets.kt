package com.jongjun.hangeoreum.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.os.SystemClock
import android.view.View
import android.widget.RemoteViews
import androidx.compose.ui.graphics.toArgb
import com.jongjun.hangeoreum.R
import com.jongjun.hangeoreum.data.Dates
import com.jongjun.hangeoreum.data.Kind
import com.jongjun.hangeoreum.data.Level
import com.jongjun.hangeoreum.data.Store
import com.jongjun.hangeoreum.data.activeGoals
import com.jongjun.hangeoreum.data.goal
import com.jongjun.hangeoreum.data.pct
import com.jongjun.hangeoreum.data.progress
import com.jongjun.hangeoreum.data.todayGoals
import com.jongjun.hangeoreum.data.upcomingEvents

/** Common onUpdate for every widget. */
abstract class SimpleWidget : AppWidgetProvider() {
    abstract fun draw(context: Context): RemoteViews
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val v = draw(context)
        ids.forEach { manager.updateAppWidget(it, v) }
    }
}

// ---------------------------------------------------------------- 오늘 할 일 (체크 가능)
class TodoWidget : SimpleWidget() {
    override fun draw(context: Context) = build(context)

    companion object {
        private val ROWS = intArrayOf(R.id.todo_row1, R.id.todo_row2, R.id.todo_row3, R.id.todo_row4, R.id.todo_row5, R.id.todo_row6)
        private val CHK = intArrayOf(R.id.todo_chk1, R.id.todo_chk2, R.id.todo_chk3, R.id.todo_chk4, R.id.todo_chk5, R.id.todo_chk6)
        private val TXT = intArrayOf(R.id.todo_txt1, R.id.todo_txt2, R.id.todo_txt3, R.id.todo_txt4, R.id.todo_txt5, R.id.todo_txt6)

        fun build(context: Context): RemoteViews {
            val d = Store.current
            val today = Dates.today()
            val list = d.todayGoals(today)
            val v = RemoteViews(context.packageName, R.layout.widget_todo)
            val p = WidgetKit.colorize(context, v, R.id.todo_root, text = intArrayOf(R.id.todo_title), sub = intArrayOf(R.id.todo_empty), accent = intArrayOf(R.id.todo_count))
            v.setTextViewText(R.id.todo_title, "오늘 할 일 · ${Dates.full(today)}")
            v.setTextViewText(R.id.todo_count, if (list.isEmpty()) "" else "${list.count { it.done }}/${list.size}")
            v.setViewVisibility(R.id.todo_empty, if (list.isEmpty()) View.VISIBLE else View.GONE)
            ROWS.indices.forEach { i ->
                val g = list.getOrNull(i)
                if (g == null) {
                    v.setViewVisibility(ROWS[i], View.GONE)
                } else {
                    v.setViewVisibility(ROWS[i], View.VISIBLE)
                    v.setTextViewText(CHK[i], if (g.done) "✔" else "○")
                    v.setTextColor(CHK[i], if (g.done) p.blue.toArgb() else p.tertiary.toArgb())
                    v.setTextViewText(TXT[i], g.title)
                    v.setTextColor(TXT[i], if (g.done) p.tertiary.toArgb() else p.text.toArgb())
                    v.setOnClickPendingIntent(CHK[i], WidgetKit.action(context, WidgetActions.TOGGLE_GOAL, g.id))
                    v.setOnClickPendingIntent(TXT[i], WidgetKit.action(context, WidgetActions.TOGGLE_GOAL, g.id))
                }
            }
            v.setOnClickPendingIntent(R.id.todo_root, WidgetKit.openApp(context, 0))
            return v
        }
    }
}

// ---------------------------------------------------------------- 공부 타이머
class TimerWidget : SimpleWidget() {
    override fun draw(context: Context) = build(context)

    companion object {
        fun build(context: Context): RemoteViews {
            val d = Store.current
            val t = d.timer
            val now = System.currentTimeMillis()
            val v = RemoteViews(context.packageName, R.layout.widget_timer)
            WidgetKit.colorize(
                context, v, R.id.tm_root,
                text = intArrayOf(R.id.tm_clock), sub = intArrayOf(R.id.tm_goal), accent = intArrayOf(R.id.tm_status),
                accentBg = intArrayOf(R.id.tm_toggle),
            )
            v.setTextViewText(R.id.tm_goal, d.goal(t.goalId)?.title ?: "자유 공부")
            v.setChronometer(R.id.tm_clock, SystemClock.elapsedRealtime() - t.elapsed(now), null, t.running)
            v.setTextViewText(
                R.id.tm_status,
                when {
                    t.running -> "집중하는 중"
                    t.active -> "일시정지됨"
                    else -> "오늘 ${Dates.duration(d.sessions.filter { Dates.dayOfMs(it.startMs) == Dates.today() }.sumOf { it.durationMs })}"
                },
            )
            v.setTextViewText(R.id.tm_toggle, when { t.running -> "일시정지"; t.active -> "계속"; else -> "시작" })
            v.setViewVisibility(R.id.tm_stop, if (t.active) View.VISIBLE else View.GONE)
            v.setOnClickPendingIntent(R.id.tm_toggle, WidgetKit.action(context, WidgetActions.TIMER_TOGGLE))
            v.setOnClickPendingIntent(R.id.tm_stop, WidgetKit.action(context, WidgetActions.TIMER_STOP))
            v.setOnClickPendingIntent(R.id.tm_root, WidgetKit.openApp(context, 2))
            return v
        }
    }
}

// ---------------------------------------------------------------- D-day
class DdayWidget : SimpleWidget() {
    override fun draw(context: Context) = build(context)

    companion object {
        private data class Target(val title: String, val day: Long)

        fun build(context: Context): RemoteViews {
            val d = Store.current
            val today = Dates.today()
            val targets = buildList {
                d.events.filter { (it.kind != Kind.GENERAL || it.onPlan) && it.day >= today }
                    .forEach { add(Target(it.title, it.day)) }
                d.goals.filter { it.level != Level.DAY && !it.done && it.endDay >= today }
                    .forEach { add(Target(it.title, it.endDay)) }
            }.sortedBy { it.day }
            val v = RemoteViews(context.packageName, R.layout.widget_dday)
            WidgetKit.colorize(
                context, v, R.id.dd_root,
                text = intArrayOf(R.id.dd_title), sub = intArrayOf(R.id.dd_label, R.id.dd_date, R.id.dd_next), accent = intArrayOf(R.id.dd_big),
            )
            val first = targets.firstOrNull()
            if (first == null) {
                v.setTextViewText(R.id.dd_big, "D-?")
                v.setTextViewText(R.id.dd_title, "시험·마감을 일정에 추가해보세요")
                v.setTextViewText(R.id.dd_date, "")
                v.setTextViewText(R.id.dd_next, "")
            } else {
                v.setTextViewText(R.id.dd_big, Dates.dday(first.day))
                v.setTextViewText(R.id.dd_title, first.title)
                v.setTextViewText(R.id.dd_date, Dates.full(first.day))
                val second = targets.getOrNull(1)
                v.setTextViewText(R.id.dd_next, second?.let { "다음 · ${it.title} ${Dates.dday(it.day)}" } ?: "")
            }
            v.setOnClickPendingIntent(R.id.dd_root, WidgetKit.openApp(context, 4))
            return v
        }
    }
}

// ---------------------------------------------------------------- 목표 진행률
class GoalsWidget : SimpleWidget() {
    override fun draw(context: Context) = build(context)

    companion object {
        private val ROWS = intArrayOf(R.id.gl_row1, R.id.gl_row2, R.id.gl_row3)
        private val TXT = intArrayOf(R.id.gl_txt1, R.id.gl_txt2, R.id.gl_txt3)
        private val PCT = intArrayOf(R.id.gl_pct1, R.id.gl_pct2, R.id.gl_pct3)
        private val BAR = intArrayOf(R.id.gl_bar1, R.id.gl_bar2, R.id.gl_bar3)

        fun build(context: Context): RemoteViews {
            val d = Store.current
            val list = d.activeGoals()
            val v = RemoteViews(context.packageName, R.layout.widget_goals)
            WidgetKit.colorize(
                context, v, R.id.gl_root,
                text = intArrayOf(R.id.gl_title) + TXT, sub = intArrayOf(R.id.gl_empty), accent = intArrayOf(R.id.gl_count) + PCT,
                progress = BAR,
            )
            v.setTextViewText(R.id.gl_count, if (list.isEmpty()) "" else "${list.size}개")
            v.setViewVisibility(R.id.gl_empty, if (list.isEmpty()) View.VISIBLE else View.GONE)
            ROWS.indices.forEach { i ->
                val g = list.getOrNull(i)
                if (g == null) v.setViewVisibility(ROWS[i], View.GONE) else {
                    v.setViewVisibility(ROWS[i], View.VISIBLE)
                    val p = pct(d.progress(g))
                    v.setTextViewText(TXT[i], "${g.title}  ·  ${Dates.dday(g.endDay)}")
                    v.setTextViewText(PCT[i], "$p%")
                    v.setProgressBar(BAR[i], 100, p, false)
                }
            }
            v.setOnClickPendingIntent(R.id.gl_root, WidgetKit.openApp(context, 1))
            return v
        }
    }
}

// ---------------------------------------------------------------- 다가오는 일정
class EventsWidget : SimpleWidget() {
    override fun draw(context: Context) = build(context)

    companion object {
        private val ROWS = intArrayOf(R.id.ev_row1, R.id.ev_row2, R.id.ev_row3, R.id.ev_row4)
        private val DATE = intArrayOf(R.id.ev_date1, R.id.ev_date2, R.id.ev_date3, R.id.ev_date4)
        private val TXT = intArrayOf(R.id.ev_txt1, R.id.ev_txt2, R.id.ev_txt3, R.id.ev_txt4)
        private val TIME = intArrayOf(R.id.ev_time1, R.id.ev_time2, R.id.ev_time3, R.id.ev_time4)

        fun build(context: Context): RemoteViews {
            val d = Store.current
            val list = d.upcomingEvents()
            val v = RemoteViews(context.packageName, R.layout.widget_events)
            WidgetKit.colorize(
                context, v, R.id.ev_root,
                text = intArrayOf(R.id.ev_title) + TXT, sub = intArrayOf(R.id.ev_empty) + TIME, accent = intArrayOf(R.id.ev_count) + DATE,
            )
            v.setTextViewText(R.id.ev_count, if (list.isEmpty()) "" else "${list.size}개")
            v.setViewVisibility(R.id.ev_empty, if (list.isEmpty()) View.VISIBLE else View.GONE)
            ROWS.indices.forEach { i ->
                val e = list.getOrNull(i)
                if (e == null) v.setViewVisibility(ROWS[i], View.GONE) else {
                    v.setViewVisibility(ROWS[i], View.VISIBLE)
                    val dt = Dates.date(e.day)
                    v.setTextViewText(DATE[i], when (Dates.relative(e.day)) {
                        "오늘" -> "오늘"
                        "내일" -> "내일"
                        else -> "${dt.monthValue}/${dt.dayOfMonth} (${Dates.dow(dt)})"
                    })
                    v.setTextViewText(TXT[i], e.title)
                    v.setTextViewText(TIME[i], if (e.isRange) "~${Dates.md(Dates.date(e.endDay))}" else if (e.minute >= 0) Dates.minuteLabel(e.minute) else "")
                }
            }
            v.setOnClickPendingIntent(R.id.ev_root, WidgetKit.openApp(context, 3))
            return v
        }
    }
}
