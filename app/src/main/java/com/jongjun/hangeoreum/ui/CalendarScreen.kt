package com.jongjun.hangeoreum.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.jongjun.hangeoreum.data.AppData
import com.jongjun.hangeoreum.data.Dates
import com.jongjun.hangeoreum.data.Event
import com.jongjun.hangeoreum.data.Store
import com.jongjun.hangeoreum.data.dayGoalsOn
import com.jongjun.hangeoreum.data.eventsOn
import com.jongjun.hangeoreum.data.goal
import com.jongjun.hangeoreum.data.studyMsOnDay
import java.time.LocalDate

private const val MAX_LANES = 3

private data class Seg(val event: Event, val start: Long, val end: Long, val lane: Int)

@Composable
fun CalendarScreen(data: AppData, now: Long, nav: Nav) {
    val today = Dates.today()
    var monthStart by rememberSaveable { mutableLongStateOf(LocalDate.now().withDayOfMonth(1).toEpochDay()) }
    var selected by rememberSaveable { mutableLongStateOf(today) }
    val first = Dates.date(monthStart)

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(Modifier.fillMaxWidth().padding(start = 4.dp, top = 8.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                T("캘린더", 26, Modifier.weight(1f), weight = FontWeight.Bold)
                IconCircle(Icons.Filled.Add, { nav.editEvent(EventEditReq(null, selected)) }, tint = pal.blue, bg = pal.card)
            }
        }

        item {
            CardBox(padding = 14.dp) {
                Row(Modifier.fillMaxWidth().padding(start = 6.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    T("${first.year}년 ${first.monthValue}월", 19, Modifier.weight(1f), weight = FontWeight.Bold)
                    if (monthStart != LocalDate.now().withDayOfMonth(1).toEpochDay()) {
                        Box(
                            Modifier.clip(RoundedCornerShape(10.dp)).background(pal.fill)
                                .bounceClick {
                                    monthStart = LocalDate.now().withDayOfMonth(1).toEpochDay()
                                    selected = today
                                }.padding(horizontal = 10.dp, vertical = 6.dp)
                        ) { T("오늘", 13, weight = FontWeight.SemiBold) }
                        Gap(6.dp)
                    }
                    IconCircle(Icons.Filled.KeyboardArrowLeft, { monthStart = first.minusMonths(1).toEpochDay() }, bg = pal.fill, size = 34.dp)
                    Gap(6.dp)
                    IconCircle(Icons.Filled.KeyboardArrowRight, { monthStart = first.plusMonths(1).toEpochDay() }, bg = pal.fill, size = 34.dp)
                }
                MonthGrid(data, first, today, selected) { selected = it }
            }
        }

        item { DayDetail(data, selected, now, nav) }
    }
}

@Composable
private fun MonthGrid(data: AppData, first: LocalDate, today: Long, selected: Long, onSelect: (Long) -> Unit) {
    val offset = first.dayOfWeek.value % 7 // Sunday = 0
    val gridStart = first.toEpochDay() - offset
    val weeks = (offset + first.lengthOfMonth() + 6) / 7

    Row(Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
        listOf("일", "월", "화", "수", "목", "금", "토").forEachIndexed { i, d ->
            T(
                d, 12, Modifier.weight(1f), weight = FontWeight.Medium, align = TextAlign.Center,
                color = when (i) { 0 -> pal.red; 6 -> pal.blue; else -> pal.sub },
            )
        }
    }

    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val cellW: Dp = maxWidth / 7
        Column(Modifier.fillMaxWidth()) {
            for (w in 0 until weeks) {
                val ws = gridStart + w * 7
                val we = ws + 6
                // multi-day events -> bars with lanes
                val ranged = data.events.filter { it.isRange && it.day <= we && it.endDay >= ws }
                    .sortedWith(compareBy<Event> { it.day }.thenByDescending { it.endDay - it.day })
                val laneEnds = mutableListOf<Long>()
                val segs = mutableListOf<Seg>()
                var hidden = 0
                ranged.forEach { e ->
                    val s = maxOf(e.day, ws)
                    val en = minOf(e.endDay, we)
                    var lane = laneEnds.indexOfFirst { it < s }
                    if (lane == -1) { laneEnds.add(en); lane = laneEnds.lastIndex } else laneEnds[lane] = en
                    if (lane < MAX_LANES) segs.add(Seg(e, s, en, lane)) else hidden++
                }
                val lanes = minOf(laneEnds.size, MAX_LANES)
                val weekH = 44.dp + 17.dp * lanes + 4.dp

                Box(Modifier.fillMaxWidth().height(weekH)) {
                    // day cells (tap targets + number + dots)
                    Row(Modifier.fillMaxWidth().fillMaxHeight()) {
                        for (i in 0..6) {
                            val day = ws + i
                            val date = Dates.date(day)
                            val inMonth = date.monthValue == first.monthValue
                            val isSel = day == selected
                            val isToday = day == today
                            Column(
                                Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(10.dp))
                                    .background(if (isSel) pal.blueSoft else Color.Transparent)
                                    .plainClick { onSelect(day) },
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Box(
                                    Modifier.padding(top = 4.dp).size(28.dp).clip(CircleShape)
                                        .background(if (isToday) pal.blue else Color.Transparent),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    T(
                                        "${date.dayOfMonth}", 14,
                                        weight = if (isToday || isSel) FontWeight.Bold else FontWeight.Medium,
                                        color = when {
                                            isToday -> Color.White
                                            !inMonth -> pal.tertiary
                                            i == 0 -> pal.red
                                            else -> pal.text
                                        },
                                    )
                                }
                                // dots for single-day events + day goals
                                val singles = data.events.filter { !it.isRange && it.day == day }
                                val goalCount = data.dayGoalsOn(day).size
                                Row(Modifier.padding(top = 3.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                                    singles.take(3).forEach { e ->
                                        Box(Modifier.size(5.dp).clip(CircleShape).background(eventColor(e.color)))
                                    }
                                    if (goalCount > 0 && singles.size < 3) {
                                        Box(Modifier.size(5.dp).clip(CircleShape).background(pal.tertiary))
                                    }
                                }
                            }
                        }
                    }
                    // bars
                    segs.forEach { seg ->
                        val c = eventColor(seg.event.color)
                        val x = cellW * (seg.start - ws).toInt() + 2.dp
                        val wdt = cellW * (seg.end - seg.start + 1).toInt() - 4.dp
                        Box(
                            Modifier.offset(x = x, y = 44.dp + 17.dp * seg.lane)
                                .width(wdt).height(15.dp)
                                .clip(RoundedCornerShape(4.dp)).background(c.copy(alpha = if (pal.dark) 0.35f else 0.18f))
                                .plainClick { onSelect(seg.start) }
                                .padding(horizontal = 4.dp),
                            contentAlignment = Alignment.CenterStart,
                        ) {
                            if (seg.start == seg.event.day || seg.start == ws) {
                                T(seg.event.title, 10, weight = FontWeight.SemiBold, color = c, maxLines = 1)
                            }
                        }
                    }
                    if (hidden > 0) {
                        T("+$hidden", 9, Modifier.align(Alignment.BottomEnd).padding(end = 4.dp), color = pal.sub)
                    }
                }
            }
        }
    }
}

@Composable
private fun DayDetail(data: AppData, day: Long, now: Long, nav: Nav) {
    val events = data.eventsOn(day)
    val goals = data.dayGoalsOn(day)
    val study = data.studyMsOnDay(day, now)
    CardBox {
        SectionTitle(
            Dates.relative(day).let { if (it == Dates.full(day)) it else "$it · ${Dates.full(day)}" },
            "+ 일정",
        ) { nav.editEvent(EventEditReq(null, day)) }

        if (events.isEmpty() && goals.isEmpty()) EmptyNote("일정이 없어요")

        events.forEach { e ->
            val c = eventColor(e.color)
            Row(
                Modifier.fillMaxWidth().bounceClick { nav.editEvent(EventEditReq(e)) }.padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.width(4.dp).height(38.dp).clip(RoundedCornerShape(2.dp)).background(c))
                Gap(12.dp)
                Column(Modifier.weight(1f)) {
                    T(e.title, 16, weight = FontWeight.SemiBold, maxLines = 1)
                    val sub = if (e.isRange) "${Dates.span(e.day, e.endDay)} · ${day - e.day + 1}/${e.endDay - e.day + 1}일차"
                    else Dates.minuteLabel(e.minute)
                    T(sub + if (e.memo.isNotBlank()) " · ${e.memo}" else "", 13, color = pal.sub, maxLines = 1)
                }
            }
        }

        if (goals.isNotEmpty()) {
            T("할 일", 13, Modifier.padding(top = 12.dp, bottom = 2.dp), weight = FontWeight.SemiBold, color = pal.sub)
            goals.forEach { g ->
                Row(
                    Modifier.fillMaxWidth().bounceClick { nav.openGoal(g.id) }.padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CheckDot(g.done, { Store.toggleGoal(g.id) }, size = 22.dp)
                    Gap(12.dp)
                    Column(Modifier.weight(1f)) {
                        T(g.title, 15, weight = FontWeight.Medium, color = if (g.done) pal.tertiary else pal.text, maxLines = 1)
                        data.goal(g.parentId)?.let { T(it.title, 12, color = pal.sub, maxLines = 1) }
                    }
                }
            }
        }

        if (study > 0) {
            Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                T("공부 시간", 14, Modifier.weight(1f), color = pal.sub)
                T(Dates.duration(study), 14, weight = FontWeight.Bold, color = pal.blue)
            }
        }
    }
}
