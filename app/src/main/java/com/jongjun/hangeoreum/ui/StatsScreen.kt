package com.jongjun.hangeoreum.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.jongjun.hangeoreum.data.AppData
import com.jongjun.hangeoreum.data.Dates
import com.jongjun.hangeoreum.data.Goal
import com.jongjun.hangeoreum.data.goal
import java.time.DayOfWeek
import java.time.temporal.TemporalAdjusters

/** Study minutes per day, including a running timer for today. */
private fun dailyMs(d: AppData, now: Long): Map<Long, Long> {
    val m = HashMap<Long, Long>()
    d.sessions.forEach { s -> val k = Dates.dayOfMs(s.startMs); m[k] = (m[k] ?: 0L) + s.durationMs }
    if (d.timer.active) { val k = Dates.today(); m[k] = (m[k] ?: 0L) + d.timer.elapsed(now) }
    return m
}

private fun rootOf(d: AppData, id: String?): Goal? {
    var g = d.goal(id) ?: return null
    var guard = 0
    while (g.parentId != null && guard++ < 10) g = d.goal(g.parentId) ?: break
    return g
}

@Composable
fun StatsScreen(data: AppData, now: Long, onBack: () -> Unit) {
    var mode by rememberSaveable { mutableIntStateOf(0) } // 0 week, 1 month
    var offset by rememberSaveable { mutableIntStateOf(0) }
    val today = Dates.today()
    val todayDate = Dates.date(today)
    val (start, end) = if (mode == 0) {
        val mon = todayDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).plusWeeks(offset.toLong())
        mon.toEpochDay() to mon.plusDays(6).toEpochDay()
    } else {
        val first = todayDate.withDayOfMonth(1).plusMonths(offset.toLong())
        first.toEpochDay() to first.with(TemporalAdjusters.lastDayOfMonth()).toEpochDay()
    }
    val len = end - start + 1
    val daily = dailyMs(data, now)
    val values = (start..end).map { daily[it] ?: 0L }
    val total = values.sum()
    val prevTotal = (start - len until start).sumOf { daily[it] ?: 0L }
    val elapsedDays = (minOf(end, today) - start + 1).coerceAtLeast(1)
    val maxV = (values.maxOrNull() ?: 0L).coerceAtLeast(60_000L)

    // streaks (days with at least 1 minute)
    val studied = daily.filterValues { it >= 60_000L }.keys
    var cur = 0
    var day = if (today in studied) today else today - 1
    while (day in studied) { cur++; day-- }
    var best = 0
    var run = 0
    var prev = Long.MIN_VALUE
    studied.sorted().forEach { k -> run = if (k == prev + 1) run + 1 else 1; best = maxOf(best, run); prev = k }

    // per root goal within the period
    val byGoal = HashMap<String, Long>()
    data.sessions.filter { Dates.dayOfMs(it.startMs) in start..end }.forEach { s ->
        val key = rootOf(data, s.goalId)?.title ?: "자유 공부"
        byGoal[key] = (byGoal[key] ?: 0L) + s.durationMs
    }
    if (data.timer.active && today in start..end) {
        val key = rootOf(data, data.timer.goalId)?.title ?: "자유 공부"
        byGoal[key] = (byGoal[key] ?: 0L) + data.timer.elapsed(now)
    }
    val goalRows = byGoal.entries.sortedByDescending { it.value }.take(8)

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconCircle(Icons.AutoMirrored.Filled.ArrowBack, onBack, bg = pal.bg)
            T("공부 통계", 18, Modifier.padding(start = 6.dp), weight = FontWeight.Bold)
        }
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { Segmented(listOf("주간", "월간"), mode) { mode = it; offset = 0 } }

            item {
                CardBox {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconCircle(Icons.Filled.KeyboardArrowLeft, { offset-- }, bg = pal.fill, size = 34.dp)
                        T(
                            if (mode == 0) Dates.span(start, end) else "${Dates.date(start).year}년 ${Dates.date(start).monthValue}월",
                            15, Modifier.weight(1f), weight = FontWeight.SemiBold, align = TextAlign.Center,
                        )
                        IconCircle(Icons.Filled.KeyboardArrowRight, { if (offset < 0) offset++ }, bg = pal.fill, size = 34.dp)
                    }
                    T(Dates.duration(total), 32, Modifier.padding(top = 16.dp), weight = FontWeight.Bold, tabular = true)
                    val diff = total - prevTotal
                    T(
                        "하루 평균 ${Dates.duration(total / elapsedDays)}" +
                            if (prevTotal > 0) " · 지난${if (mode == 0) "주" else "달"}보다 ${if (diff >= 0) "+" else "−"}${Dates.duration(kotlin.math.abs(diff))}" else "",
                        14, Modifier.padding(top = 2.dp), color = pal.sub,
                    )

                    // bars
                    Row(
                        Modifier.fillMaxWidth().height(150.dp).padding(top = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(if (mode == 0) 10.dp else 2.dp),
                        verticalAlignment = Alignment.Bottom,
                    ) {
                        values.forEachIndexed { i, v ->
                            val d = start + i
                            Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.Bottom, horizontalAlignment = Alignment.CenterHorizontally) {
                                if (mode == 0 && v > 0) T(shortDur(v), 10, color = pal.sub, maxLines = 1)
                                val frac = (v.toFloat() / maxV).coerceIn(0f, 1f)
                                Box(
                                    Modifier.fillMaxWidth().fillMaxHeight(if (v > 0) maxOf(frac * 0.85f, 0.03f) else 0.015f)
                                        .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                                        .background(if (d == today) pal.blue else if (v > 0) pal.blue.copy(alpha = 0.45f) else pal.fill)
                                )
                            }
                        }
                    }
                    Row(Modifier.fillMaxWidth().padding(top = 6.dp)) {
                        (start..end).forEachIndexed { i, d ->
                            val label = if (mode == 0) Dates.dow(Dates.date(d))
                            else if (i == 0 || (i + 1) % 5 == 0) "${Dates.date(d).dayOfMonth}" else ""
                            T(label, 10, Modifier.weight(1f), color = if (d == today) pal.blue else pal.sub, align = TextAlign.Center, maxLines = 1)
                        }
                    }
                }
            }

            item {
                CardBox {
                    Row {
                        Column(Modifier.weight(1f)) {
                            T("연속 공부", 13, color = pal.sub)
                            T("${cur}일", 26, weight = FontWeight.Bold, color = if (cur > 0) pal.blue else pal.text)
                        }
                        Column(Modifier.weight(1f)) {
                            T("최고 기록", 13, color = pal.sub)
                            T("${best}일", 26, weight = FontWeight.Bold)
                        }
                        Column(Modifier.weight(1f)) {
                            T("공부한 날", 13, color = pal.sub)
                            T("${values.count { it >= 60_000L }}/${len}", 26, weight = FontWeight.Bold)
                        }
                    }
                }
            }

            item {
                CardBox {
                    SectionTitle("목표별 시간")
                    if (goalRows.isEmpty()) EmptyNote("이 기간에 기록이 없어요")
                    val top = goalRows.firstOrNull()?.value ?: 1L
                    goalRows.forEach { (title, ms) ->
                        Column(Modifier.fillMaxWidth().padding(vertical = 7.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                T(title, 15, Modifier.weight(1f), weight = FontWeight.Medium, maxLines = 1)
                                T(Dates.duration(ms), 14, weight = FontWeight.SemiBold, color = pal.blue)
                                T(" · ${if (total > 0) ms * 100 / total else 0}%", 13, color = pal.sub)
                            }
                            ProgressLine(ms.toFloat() / top, Modifier.padding(top = 6.dp), height = 6.dp)
                        }
                    }
                }
            }
        }
    }
}

private fun shortDur(ms: Long): String {
    val m = ms / 60000
    return if (m >= 60) "${m / 60}h${if (m % 60 > 0) "${m % 60}" else ""}" else "${m}m"
}
