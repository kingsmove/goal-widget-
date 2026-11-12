package com.jongjun.hangeoreum.data

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

object Dates {
    private val DOW = arrayOf("월", "화", "수", "목", "금", "토", "일")

    fun today(): Long = LocalDate.now().toEpochDay()
    fun date(day: Long): LocalDate = LocalDate.ofEpochDay(day)
    fun dayOfMs(ms: Long): Long =
        Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).toLocalDate().toEpochDay()

    fun dow(d: LocalDate): String = DOW[d.dayOfWeek.value - 1]

    /** 11월 9일 (월) */
    fun full(day: Long): String {
        val d = date(day)
        return "${d.monthValue}월 ${d.dayOfMonth}일 (${dow(d)})"
    }

    fun md(d: LocalDate): String = "${d.monthValue}/${d.dayOfMonth}"

    fun relative(day: Long): String {
        val diff = day - today()
        return when (diff) {
            0L -> "오늘"
            1L -> "내일"
            2L -> "모레"
            -1L -> "어제"
            else -> full(day)
        }
    }

    fun dday(end: Long): String {
        val diff = end - today()
        return when {
            diff == 0L -> "D-Day"
            diff > 0 -> "D-$diff"
            else -> "D+${-diff}"
        }
    }

    /** Human label of a goal's period. */
    fun period(level: Level, start: Long, end: Long): String {
        val s = date(start)
        val e = date(end)
        return when (level) {
            Level.YEAR -> if (e.monthValue == 12 && e.dayOfMonth == 31) "${e.year}년" else "${e.year}년 ${e.monthValue}월까지"
            Level.MONTH -> if (e.year == LocalDate.now().year) "${e.monthValue}월까지" else "${e.year}년 ${e.monthValue}월까지"
            Level.WEEK -> "${md(s)} ~ ${md(e)}"
            Level.DAY -> full(end)
            Level.PLAN -> if (start == end) "${full(end)}까지" else "${span(start, end)}"
        }
    }

    /** Range for a level anchored at [anchor]. */
    fun range(level: Level, anchor: LocalDate): Pair<Long, Long> {
        val today = LocalDate.now()
        return when (level) {
            Level.YEAR -> {
                val end = anchor.with(TemporalAdjusters.lastDayOfYear())
                val start = if (anchor.year == today.year) today else anchor.with(TemporalAdjusters.firstDayOfYear())
                start.toEpochDay() to end.toEpochDay()
            }
            Level.MONTH -> {
                val end = anchor.with(TemporalAdjusters.lastDayOfMonth())
                val start = if (!today.isAfter(end)) minOf(today, end) else anchor.withDayOfMonth(1)
                start.toEpochDay() to end.toEpochDay()
            }
            Level.WEEK -> {
                val mon = anchor.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                mon.toEpochDay() to mon.plusDays(6).toEpochDay()
            }
            Level.DAY -> anchor.toEpochDay() to anchor.toEpochDay()
            Level.PLAN -> minOf(today, anchor).toEpochDay() to anchor.toEpochDay()
        }
    }

    fun stepAnchor(level: Level, anchor: LocalDate, dir: Int): LocalDate = when (level) {
        Level.YEAR -> anchor.plusYears(dir.toLong())
        Level.MONTH -> anchor.plusMonths(dir.toLong())
        Level.WEEK -> anchor.plusWeeks(dir.toLong())
        Level.DAY, Level.PLAN -> anchor.plusDays(dir.toLong())
    }

    fun anchorLabel(level: Level, anchor: LocalDate): String {
        val (s, e) = range(level, anchor)
        return when (level) {
            Level.YEAR -> "${anchor.year}년"
            Level.MONTH -> "${anchor.year}년 ${anchor.monthValue}월까지"
            Level.WEEK -> "${md(date(s))} ~ ${md(date(e))}"
            Level.DAY -> full(e)
            Level.PLAN -> "${full(e)}까지"
        }
    }

    /** "11/20 ~ 11/23" or "2026.12 ~ 2027.02" when spanning years. */
    fun span(start: Long, end: Long): String {
        val s = date(start)
        val e = date(end)
        return if (s.year != e.year) "${s.year}.${s.monthValue}.${s.dayOfMonth} ~ ${e.year}.${e.monthValue}.${e.dayOfMonth}"
        else "${md(s)} ~ ${md(e)}"
    }

    fun eventWhen(day: Long, endDay: Long, minute: Int): String =
        if (endDay > day) span(day, endDay) else "${relative(day)} · ${minuteLabel(minute)}"

    fun duration(ms: Long): String {
        val totalMin = ms / 60000
        val h = totalMin / 60
        val m = totalMin % 60
        return when {
            h > 0 && m > 0 -> "${h}시간 ${m}분"
            h > 0 -> "${h}시간"
            else -> "${m}분"
        }
    }

    fun clock(ms: Long): String {
        val s = ms / 1000
        return "%02d:%02d:%02d".format(s / 3600, (s % 3600) / 60, s % 60)
    }

    fun hm(ms: Long): String {
        val t = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault())
        return "%02d:%02d".format(t.hour, t.minute)
    }

    fun minuteLabel(minute: Int): String {
        if (minute < 0) return "하루 종일"
        val h = minute / 60
        val m = minute % 60
        val ampm = if (h < 12) "오전" else "오후"
        val h12 = if (h % 12 == 0) 12 else h % 12
        return if (m == 0) "$ampm ${h12}시" else "$ampm ${h12}:%02d".format(m)
    }
}
