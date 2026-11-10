package com.jongjun.hangeoreum.ui

import android.app.Activity
import android.content.pm.ActivityInfo
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.jongjun.hangeoreum.data.AppData
import com.jongjun.hangeoreum.data.Dates
import com.jongjun.hangeoreum.data.Goal
import com.jongjun.hangeoreum.data.Level
import com.jongjun.hangeoreum.data.Store
import com.jongjun.hangeoreum.data.pct
import com.jongjun.hangeoreum.data.progress
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import kotlin.math.roundToInt

private const val ROW_H = 54
private const val NAME_W = 140
private const val HEAD_H = 56

private data class GRow(val goal: Goal, val depth: Int)

private fun monthsBetween(a: LocalDate, b: LocalDate): Int = (b.year - a.year) * 12 + (b.monthValue - a.monthValue)

/** Goals as a schedule chart (예정공정표): one row per goal, a bar from start to deadline. */
@Composable
fun GanttScreen(data: AppData, nav: Nav, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val density = LocalDensity.current
    var showWeek by rememberSaveable { mutableStateOf(false) }
    var showDone by rememberSaveable { mutableStateOf(true) }
    var monthW by rememberSaveable { mutableFloatStateOf(64f) }
    var landscape by rememberSaveable { mutableStateOf(false) }
    val today = Dates.today()
    val nowDate = LocalDate.now()

    // rotate only while this screen is open
    DisposableEffect(landscape) {
        val act = ctx as? Activity
        act?.requestedOrientation = if (landscape) ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        else ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        onDispose { act?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED }
    }

    // ---- rows: tree order (year → its months → its weeks)
    val levels = buildSet { add(Level.YEAR); add(Level.MONTH); if (showWeek) add(Level.WEEK) }
    val shown = data.goals.filter { it.level in levels && (showDone || !it.done) }
    val shownIds = shown.map { it.id }.toSet()
    val rows = mutableListOf<GRow>()
    fun addTree(g: Goal, depth: Int) {
        rows += GRow(g, depth)
        shown.filter { it.parentId == g.id }.sortedWith(compareBy<Goal>({ it.startDay }, { it.endDay }))
            .forEach { if (depth < 4) addTree(it, depth + 1) }
    }
    shown.filter { it.parentId == null || it.parentId !in shownIds }
        .sortedWith(compareBy<Goal>({ it.level.ordinal }, { it.startDay }, { it.endDay }))
        .forEach { addTree(it, 0) }

    // ---- time range
    val minDay = minOf(rows.minOfOrNull { it.goal.startDay } ?: today, today)
    val maxDay = maxOf(rows.maxOfOrNull { it.goal.endDay } ?: today, today)
    val first = Dates.date(minDay).withDayOfMonth(1).minusMonths(1)
    var last = Dates.date(maxDay).withDayOfMonth(1).plusMonths(1)
    if (monthsBetween(first, last) < 11) last = first.plusMonths(11)
    val months = (monthsBetween(first, last) + 1).coerceAtMost(120)
    val mw = monthW.dp
    val totalW = mw * months
    fun pos(day: Long): Float {
        val d = Dates.date(day)
        return monthsBetween(first, d.withDayOfMonth(1)) + (d.dayOfMonth - 1f) / d.lengthOfMonth()
    }
    val dayW = monthW / 30.44f // dp per day, for dragging

    val hScroll = rememberScrollState()
    LaunchedEffect(monthW, first) {
        snapshotFlow { hScroll.maxValue }.first { it > 0 }
        hScroll.scrollTo(with(density) { (mw * (pos(today) - 1.5f)).toPx() }.toInt().coerceAtLeast(0))
    }

    Column(Modifier.fillMaxSize()) {
        // top bar
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconCircle(Icons.AutoMirrored.Filled.ArrowBack, onBack, bg = pal.bg)
            T("목표 공정표", 18, Modifier.padding(start = 6.dp).weight(1f), weight = FontWeight.Bold)
            IconCircle(Icons.Filled.Add, { nav.editGoal(GoalEditReq(null, Level.MONTH, null)) }, tint = pal.blue, bg = pal.card)
        }
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ChoiceChip("주간 포함", showWeek) { showWeek = !showWeek }
            ChoiceChip("완료 포함", showDone) { showDone = !showDone }
            ChoiceChip(if (landscape) "세로로" else "가로로 보기", landscape) { landscape = !landscape }
            ChoiceChip("−", false) { monthW = (monthW - 14f).coerceAtLeast(30f) }
            ChoiceChip("+", false) { monthW = (monthW + 14f).coerceAtMost(180f) }
        }
        T(
            "막대 누르기: 수정 · 길게 눌러 끌기: 기간 이동 · 오른쪽 끝 끌기: 기한 변경", 12,
            Modifier.padding(horizontal = 20.dp, vertical = 4.dp), color = pal.sub,
        )

        Column(
            Modifier.padding(horizontal = 12.dp, vertical = 8.dp).fillMaxSize()
                .clip(RoundedCornerShape(20.dp)).background(pal.card)
        ) {
            // ---- header: years + months
            Row(Modifier.fillMaxWidth().height(HEAD_H.dp)) {
                Box(
                    Modifier.width(NAME_W.dp).fillMaxHeight().background(pal.card).padding(start = 14.dp),
                    contentAlignment = Alignment.CenterStart,
                ) { T("목표", 13, weight = FontWeight.Bold, color = pal.sub) }
                Box(Modifier.weight(1f).fillMaxHeight().horizontalScroll(hScroll)) {
                    Box(Modifier.width(totalW).fillMaxHeight()) {
                        var m = 0
                        while (m < months) {
                            val ym = first.plusMonths(m.toLong())
                            val span = minOf(12 - ym.monthValue + 1, months - m)
                            val c = pal.yearColors[Math.floorMod(ym.year, pal.yearColors.size)]
                            Box(
                                Modifier.offset(x = mw * m).width(mw * span).height(26.dp).padding(horizontal = 1.dp)
                                    .background(c.copy(alpha = 0.10f)),
                                contentAlignment = Alignment.Center,
                            ) { T("${ym.year}년", 12, weight = FontWeight.Bold, color = c) }
                            m += span
                        }
                        for (i in 0 until months) {
                            val ym = first.plusMonths(i.toLong())
                            val now = ym.year == nowDate.year && ym.monthValue == nowDate.monthValue
                            Box(
                                Modifier.offset(x = mw * i, y = 26.dp).width(mw).height(30.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                T(
                                    "${ym.monthValue}월", 12, weight = if (now) FontWeight.Bold else FontWeight.Medium,
                                    color = if (now) pal.blue else pal.sub, align = TextAlign.Center,
                                )
                            }
                        }
                    }
                }
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(pal.line))

            // ---- body
            Row(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState())) {
                // names
                Column(Modifier.width(NAME_W.dp)) {
                    if (rows.isEmpty()) Box(Modifier.height(ROW_H.dp))
                    rows.forEach { r ->
                        Row(
                            Modifier.width(NAME_W.dp).height(ROW_H.dp).bounceClick { nav.openGoal(r.goal.id) }
                                .padding(start = (14 + r.depth * 10).dp, end = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column {
                                T(
                                    r.goal.title, if (r.depth == 0) 14 else 13, maxLines = 2,
                                    weight = if (r.depth == 0) FontWeight.SemiBold else FontWeight.Medium,
                                    color = if (r.goal.done) pal.tertiary else pal.text,
                                )
                                T(r.goal.level.label, 10, color = pal.tertiary)
                            }
                        }
                    }
                }
                Box(Modifier.width(1.dp).height((ROW_H * maxOf(rows.size, 1)).dp).background(pal.line))
                // timeline
                Box(Modifier.weight(1f).horizontalScroll(hScroll)) {
                    Box(Modifier.width(totalW).height((ROW_H * maxOf(rows.size, 1)).dp)) {
                        // month grid
                        for (i in 0..months) {
                            val ym = first.plusMonths(i.toLong())
                            Box(
                                Modifier.offset(x = mw * i).width(1.dp).fillMaxHeight()
                                    .background(if (ym.monthValue == 1) pal.line else pal.line.copy(alpha = 0.5f))
                            )
                        }
                        // row lines
                        rows.indices.forEach { i ->
                            Box(Modifier.offset(y = (ROW_H * (i + 1)).dp - 1.dp).fillMaxWidth().height(1.dp).background(pal.fill))
                        }
                        // today
                        Box(Modifier.offset(x = mw * pos(today)).width(2.dp).fillMaxHeight().background(pal.red.copy(alpha = 0.6f)).zIndex(2f))

                        if (rows.isEmpty()) {
                            T(
                                "연간·월간 목표를 추가하면 여기에 막대로 나와요", 13,
                                Modifier.offset(x = mw * pos(today) + 12.dp, y = 18.dp), color = pal.sub,
                            )
                        }

                        rows.forEachIndexed { i, r -> GanttBar(r.goal, i, data, mw, dayW, ::pos, nav) }
                    }
                }
            }
        }
    }
}

@Composable
private fun GanttBar(
    g: Goal,
    index: Int,
    data: AppData,
    mw: androidx.compose.ui.unit.Dp,
    dayW: Float,
    pos: (Long) -> Float,
    nav: Nav,
) {
    val density = LocalDensity.current
    val color = goalColor(g)
    val p = data.progress(g)
    // live drag offsets in days
    var moveDays by remember(g.id, g.startDay, g.endDay) { mutableIntStateOf(0) }
    var endDays by remember(g.id, g.startDay, g.endDay) { mutableIntStateOf(0) }
    var accum by remember { mutableFloatStateOf(0f) }
    val dayPx = with(density) { dayW.dp.toPx() }

    val start = g.startDay + moveDays
    val end = maxOf(start, g.endDay + moveDays + endDays)
    val x0 = mw * pos(start)
    val w = (mw * (pos(end + 1) - pos(start))).coerceAtLeast(10.dp)
    val barH = 24.dp
    val y = (ROW_H * index).dp + ((ROW_H.dp - barH) / 2)
    val dragging = moveDays != 0 || endDays != 0

    Box(
        Modifier.offset(x = x0, y = y).width(w).height(barH).zIndex(if (dragging) 3f else 1f)
            .clip(RoundedCornerShape(7.dp))
            .background(if (g.done) color.copy(alpha = 0.35f) else color.copy(alpha = 0.22f))
            .border(if (dragging) 2.dp else 0.dp, color, RoundedCornerShape(7.dp))
            .pointerInput(g.id, g.startDay, g.endDay) {
                detectTapGestures(onTap = { nav.editGoal(GoalEditReq(g, g.level, g.parentId)) })
            }
            .pointerInput(g.id, g.startDay, g.endDay, dayPx) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { accum = 0f },
                    onDrag = { change, amount ->
                        change.consume()
                        accum += amount.x
                        moveDays = (accum / dayPx).roundToInt()
                    },
                    onDragEnd = {
                        if (moveDays != 0) Store.upsertGoal(g.copy(startDay = g.startDay + moveDays, endDay = g.endDay + moveDays))
                        moveDays = 0
                    },
                    onDragCancel = { moveDays = 0 },
                )
            },
    ) {
        // progress fill
        Box(Modifier.fillMaxHeight().fillMaxWidth(p.coerceIn(0f, 1f)).background(color.copy(alpha = if (g.done) 0.6f else 0.85f)))
        Row(Modifier.fillMaxSize().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            T(
                if (dragging) Dates.span(start, end) else "${pct(p)}%",
                11, Modifier.weight(1f), weight = FontWeight.Bold,
                color = if (p > 0.3f) Color.White else color, maxLines = 1,
            )
        }
        // right-edge handle: change deadline
        Box(
            Modifier.align(Alignment.CenterEnd).width(18.dp).fillMaxHeight()
                .pointerInput(g.id, g.startDay, g.endDay, dayPx) {
                    detectHorizontalDragGestures(
                        onDragStart = { accum = 0f },
                        onHorizontalDrag = { change, dx ->
                            change.consume()
                            accum += dx
                            endDays = maxOf((accum / dayPx).roundToInt(), -(g.endDay - g.startDay).toInt())
                        },
                        onDragEnd = {
                            if (endDays != 0) Store.upsertGoal(g.copy(endDay = g.endDay + endDays))
                            endDays = 0
                        },
                        onDragCancel = { endDays = 0 },
                    )
                },
            contentAlignment = Alignment.Center,
        ) {
            Box(Modifier.width(3.dp).height(12.dp).clip(RoundedCornerShape(2.dp)).background(Color.White.copy(alpha = 0.8f)))
        }
    }
}
