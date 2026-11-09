package com.jongjun.hangeoreum.notify

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.jongjun.hangeoreum.MainActivity
import com.jongjun.hangeoreum.R
import com.jongjun.hangeoreum.data.AppData
import com.jongjun.hangeoreum.data.Dates
import com.jongjun.hangeoreum.data.Kind
import com.jongjun.hangeoreum.data.Level
import com.jongjun.hangeoreum.data.Store
import com.jongjun.hangeoreum.data.todayGoals
import java.time.LocalDate
import java.time.ZoneId

/**
 * One alarm at a time: it fires at the next due reminder, posts everything due,
 * then schedules the following one. Recomputed whenever data changes.
 */
object Reminders {
    const val CHANNEL = "ufp_reminders"
    private const val PREFS = "reminders"
    private const val KEY_LAST = "lastFired"

    enum class Type { EVENT, DDAY, MORNING, EVENING }

    data class Due(val at: Long, val key: String, val type: Type, val title: String, val text: String)

    private fun ms(day: Long, minute: Int): Long =
        LocalDate.ofEpochDay(day).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() + minute * 60_000L

    /** All reminders whose time falls in (from, until]. */
    fun due(d: AppData, from: Long, until: Long): List<Due> {
        val n = d.notif
        val out = mutableListOf<Due>()
        val firstDay = Dates.dayOfMs(from) - 1
        val lastDay = Dates.dayOfMs(until) + 8
        if (n.events) {
            d.events.filter { it.minute >= 0 && it.day in firstDay..lastDay }.forEach { e ->
                val at = ms(e.day, e.minute) - n.eventLeadMin * 60_000L
                val lead = if (n.eventLeadMin >= 60) "${n.eventLeadMin / 60}시간" else "${n.eventLeadMin}분"
                out += Due(at, "ev-${e.id}-${e.day}-${e.minute}", Type.EVENT, e.title, "$lead 뒤 · ${Dates.minuteLabel(e.minute)}" + if (e.memo.isNotBlank()) " · ${e.memo}" else "")
            }
        }
        if (n.ddays) {
            val targets = d.events.filter { it.kind != Kind.GENERAL || it.onPlan }.map { Triple(it.id, it.title, it.day) } +
                d.goals.filter { it.level != Level.DAY && !it.done }.map { Triple(it.id, it.title, it.endDay) }
            targets.forEach { (id, title, day) ->
                listOf(7L, 1L, 0L).forEach { before ->
                    val fireDay = day - before
                    if (fireDay in firstDay..lastDay) {
                        val label = if (before == 0L) "오늘이에요" else "D-$before"
                        out += Due(ms(fireDay, 9 * 60), "dd-$id-$day-$before", Type.DDAY, "$title · $label", Dates.full(day))
                    }
                }
            }
        }
        for (day in Dates.dayOfMs(from)..Dates.dayOfMs(until) + 1) {
            if (n.morning) out += Due(ms(day, n.morningMin), "am-$day", Type.MORNING, "", "")
            if (n.evening) out += Due(ms(day, n.eveningMin), "pm-$day", Type.EVENING, "", "")
        }
        return out.filter { it.at > from && it.at <= until }.sortedBy { it.at }
    }

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun ensureChannel(ctx: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = ctx.getSystemService(NotificationManager::class.java) ?: return
            if (nm.getNotificationChannel(CHANNEL) == null) {
                nm.createNotificationChannel(NotificationChannel(CHANNEL, "UFK 알림", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "일정, D-day, 오늘 할 일 알림"
                })
            }
        }
    }

    fun reschedule(ctx: Context) {
        val app = ctx.applicationContext
        val p = prefs(app)
        val now = System.currentTimeMillis()
        var last = p.getLong(KEY_LAST, 0L)
        if (last == 0L || last > now) { last = now; p.edit().putLong(KEY_LAST, now).apply() }
        val next = due(Store.current, maxOf(last, now), now + 3 * 86_400_000L).firstOrNull()?.at
            ?: (now + 86_400_000L) // nothing soon: look again tomorrow
        val am = app.getSystemService(AlarmManager::class.java) ?: return
        val pi = PendingIntent.getBroadcast(
            app, 0, Intent(app, ReminderReceiver::class.java).setAction(ReminderReceiver.FIRE),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        runCatching {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next, pi)
            } else {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next, pi)
            }
        }
    }

    /** Posts everything due since the last run. */
    fun fire(ctx: Context) {
        val app = ctx.applicationContext
        val p = prefs(app)
        val now = System.currentTimeMillis()
        val last = p.getLong(KEY_LAST, now - 60_000L)
        p.edit().putLong(KEY_LAST, now + 30_000L).apply()
        Store.ensureRoutines()
        val d = Store.current
        // skip anything older than 3 hours (phone was off etc.)
        due(d, maxOf(last, now - 3 * 3_600_000L), now + 30_000L).forEach { post(app, d, it) }
        reschedule(app)
    }

    private fun canPost(ctx: Context): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun post(ctx: Context, d: AppData, due: Due) {
        if (!canPost(ctx)) return
        ensureChannel(ctx)
        val today = Dates.today()
        var title = due.title
        var text = due.text
        var tab = 0
        var carry = false
        when (due.type) {
            Type.MORNING -> {
                val todos = d.todayGoals(today)
                val evs = d.events.filter { it.covers(today) }
                if (todos.isEmpty() && evs.isEmpty()) return
                title = "오늘 할 일 ${todos.size}개 · 일정 ${evs.size}개"
                text = (todos.take(3).map { it.title } + evs.take(2).map { "📅 " + it.title }).joinToString(", ")
            }
            Type.EVENING -> {
                val left = d.todayGoals(today).filter { !it.done }
                if (left.isEmpty()) return
                title = "아직 못 한 할 일 ${left.size}개"
                text = left.take(4).joinToString(", ") { it.title }
                carry = left.any { !it.id.startsWith("rt-") }
            }
            Type.EVENT -> tab = 3
            Type.DDAY -> tab = 4
        }
        val open = PendingIntent.getActivity(
            ctx, due.key.hashCode(),
            Intent(ctx, MainActivity::class.java).putExtra(MainActivity.EXTRA_TAB, tab)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val b = NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_ufp)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(open)
            .setAutoCancel(true)
            .setColor(0xFFF04452.toInt())
        if (carry) {
            val pi = PendingIntent.getBroadcast(
                ctx, 77, Intent(ctx, ReminderReceiver::class.java).setAction(ReminderReceiver.CARRY),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            b.addAction(0, "내일로 넘기기", pi)
        }
        runCatching { NotificationManagerCompat.from(ctx).notify(due.key.hashCode(), b.build()) }
    }

    /** Moves today's unfinished (non-repeating) to-dos to tomorrow. Returns how many moved. */
    fun carryToTomorrow(): Int {
        val today = Dates.today()
        val ids = Store.current.todayGoals(today).filter { !it.done && !it.id.startsWith("rt-") }.map { it.id }
        if (ids.isNotEmpty()) Store.moveGoals(ids, today + 1)
        return ids.size
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            FIRE -> Reminders.fire(context)
            CARRY -> {
                Reminders.carryToTomorrow()
                runCatching { NotificationManagerCompat.from(context).cancel("pm-${Dates.today()}".hashCode()) }
            }
            else -> { // boot, app update, time change
                Store.ensureRoutines()
                Reminders.reschedule(context)
            }
        }
    }

    companion object {
        const val FIRE = "com.jongjun.hangeoreum.REMINDER_FIRE"
        const val CARRY = "com.jongjun.hangeoreum.CARRY_TOMORROW"
    }
}
