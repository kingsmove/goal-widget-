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

/** One bar: a goal or a plan event. */
private data class GRow(
    val key: String,
    val title: String,
    val label: String,
    val start: Long,
    val end: Long,
    val depth: Int,
    val color: Color,
    val progress: Float?,
    val done: Boolean,
    val onTap: () -> Unit,
    val onOpen: () -> Unit,
    val commit: (Long, Long) -> Unit,
)

private fun monthsBetween(a: LocalDate, b: LocalDate): Int = (b.year - a.year) * 12 + (b.monthValue - a.monthValue)

/** Goals as a schedule chart (예정공정표): one row per goal, a bar from start to deadline. */
@Composable
fun GanttScreen(data: AppData, nav: Nav, onBack: (() -> Unit)?) {
    val embedded = onBack == null
    val ctx = LocalContext.current
    val density = LocalDensity.current
    var showWeek by rememberSaveable { mutableStateOf(false) }
    var showDone by rememberSaveable { mutableStateOf(true) }
    var monthW by rememberSaveable { mutableFloatStateOf(64f) }
    var landscape by rememberSaveable { mutableStateOf(false) }
    var showEvents by rememberSaveable { mutableStateOf(true) }
    val today = Dates.today()
    val nowDate = LocalDate.now()

    // rotate only while this screen is open
    DisposableEffect(landscape, embedded) {
        if (embedded) return@DisposableEffect onDispose { }
        val act = ctx as? Activity
        act?.requestedOrientation = if (landscape) ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        else ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        onDispose { act?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED }
    }

    // ---- rows: goals in tree order (year → its months → its weeks), then plan events
    val levels = buildSet { add(Level.YEAR); add(Level.MONTH); if (showWeek) add(Level.WEEK) }
    val shown = data.goals.filter { it.level in levels && (showDone || !it.done) }
    val shownIds = shown.map { it.id }.toSet()
    val rows = mutableListOf<GRow>()
    val colors = mutableMapOf<String, Color>()
    shown.forEach { colors[it.id] = goalColor(it) }
    val evColors = data.events.associate { it.id to eventColorOf(it) }
    fun addTree(g: Goal, depth: Int) {
        rows += GRow(
            key = "g" + g.id, title = g.title, label = g.level.label, start = g.startDay, end = g.endDay, depth = depth,
            color = colors[g.id] ?: Color.Gray, progress = data.progress(g), done = g.done,
            onTap = { nav.editGoal(GoalEditReq(g, g.level, g.parentId)) },
            onOpen = { nav.openGoal(g.id) },
            commit = { s0, e0 -> Store.upsertGoal(g.copy(startDay = s0, endDay = e0)) },
        )
        shown.filter { it.parentId == g.id }.sortedWith(compareBy<Goal>({ it.startDay }, { it.endDay }))
            .forEach { if (depth < 4) addTree(it, depth + 1) }
    }
    shown.filter { it.parentId == null || it.parentId !in shownIds }
        .sortedWith(compareBy<Goal>({ it.level.ordinal }, { it.startDay }, { it.endDay }))
        .forEach { addTree(it, 0) }
    if (showEvents) {
        data.events.filter { it.onPlan && (showDone || it.endDay >= today) }.sortedWith(compareBy<com.jongjun.hangeoreum.data.Event>({ it.day }, { it.endDay })).forEach { e ->
            rows += GRow(
                key = "e" + e.id, title = e.title,
                label = if (e.kind == com.jongjun.hangeoreum.data.Kind.GENERAL) "일정" else e.kind.label,
                start = e.day, end = e.endDay, depth = 0, color = evColors[e.id] ?: Color.Gray,
                progress = null, done = e.endDay < today,
                onTap = { nav.editEvent(EventEditReq(e)) },
                onOpen = { nav.editEvent(EventEditReq(e)) },
                commit = { s0, e0 -> Store.upsertEvent(e.copy(day = s0, endDay = e0, minute = if (e0 > s0) -1 else e.minute)) },
            )
        }
    }

    // ---- time range
    val minDay = minOf(rows.minOfOrNull { it.start } ?: today, today)
    val maxDay = maxOf(rows.maxOfOrNull { it.end } ?: today, today)
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
        // top bar (full-screen only)
        if (onBack != null) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconCircle(Icons.AutoMirrored.Filled.ArrowBack, onBack, bg = pal.bg)
                T("공정표", 18, Modifier.padding(start = 6.dp).weight(1f), weight = FontWeight.Bold)
                IconCircle(Icons.Filled.Add, { nav.editGoal(GoalEditReq(null, Level.MONTH, null)) }, tint = pal.blue, bg = pal.card)
            }
        }
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (embedded) ChoiceChip("크게 보기", false) { nav.openGantt() }
            else ChoiceChip(if (landscape) "세로로" else "가로로 보기", landscape) { landscape = !landscape }
            ChoiceChip("플랜 일정", showEvents) { showEvents = !showEvents }
            ChoiceChip("주간 목표", showWeek) { showWeek = !showWeek }
            ChoiceChip("지난·완료", showDone) { showDone = !showDone }
            ChoiceChip("−", false) { monthW = (monthW - 14f).coerceAtLeast(30f) }
            ChoiceChip("+", false) { monthW = (monthW + 14f).coerceAtMost(180f) }
        }
        T(
            "막대 누르기: 수정 · 길게 눌러 끌기: 기간 이동 · 오른쪽 끝 끌기: 끝나는 날 변경", 12,
            Modifier.padding(horizontal = 20.dp, vertical = 4.dp), color = pal.sub,
        )

        Column(
            Modifier.padding(horizontal = if (embedded) 20.dp else 12.dp, vertical = 8.dp).fillMaxSize()
                .clip(RoundedCornerShape(20.dp)).background(pal.card)
        ) {
            // ---- header: years + months
            Row(Modifier.fillMaxWidth().height(HEAD_H.dp)) {
                Box(
                    Modifier.width(NAME_W.dp).fillMaxHeight().background(pal.card).padding(start = 14.dp),
                    contentAlignment = Alignment.CenterStart,
                ) { T("항목", 13, weight = FontWeight.Bold, color = pal.sub) }
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
                            Modifier.width(NAME_W.dp).height(ROW_H.dp).bounceClick(onClick = r.onOpen)
                                .padding(start = (14 + r.depth * 10).dp, end = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(Modifier.width(3.dp).height(26.dp).clip(RoundedCornerShape(2.dp)).background(r.color))
                            Column(Modifier.padding(start = 8.dp)) {
                                T(
                                    r.title, if (r.depth == 0) 14 else 13, maxLines = 2,
                                    weight = if (r.depth == 0) FontWeight.SemiBold else FontWeight.Medium,
                                    color = if (r.done) pal.tertiary else pal.text,
                                )
                                T(r.label, 10, color = pal.tertiary)
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
                                "연간·월간 목표나 플랜 일정을 추가하면 여기에 막대로 나와요", 13,
                                Modifier.offset(x = mw * pos(today) + 12.dp, y = 18.dp), color = pal.sub,
                            )
                        }

                        rows.forEachIndexed { i, r -> GanttBar(r, i, mw, dayW, ::pos) }
                    }
                }
            }
        }
    }
}

@Composable
private fun GanttBar(
    r: GRow,
    index: Int,
    mw: androidx.compose.ui.unit.Dp,
    dayW: Float,
    pos: (Long) -> Float,
) {
    val density = LocalDensity.current
    val color = r.color
    val p = r.progress ?: if (r.done) 1f else 0f
    var moveDays by remember(r.key, r.start, r.end) { mutableIntStateOf(0) }
    var endDays by remember(r.key, r.start, r.end) { mutableIntStateOf(0) }
    var accum by remember { mutableFloatStateOf(0f) }
    val dayPx = with(density) { dayW.dp.toPx() }

    val start = r.start + moveDays
    val end = maxOf(start, r.end + moveDays + endDays)
    val x0 = mw * pos(start)
    val w = (mw * (pos(end + 1) - pos(start))).coerceAtLeast(12.dp)
    val barH = 24.dp
    val y = (ROW_H * index).dp + ((ROW_H.dp - barH) / 2)
    val dragging = moveDays != 0 || endDays != 0
    val isEvent = r.progress == null

    Box(
        Modifier.offset(x = x0, y = y).width(w).height(barH).zIndex(if (dragging) 3f else 1f)
            .clip(RoundedCornerShape(7.dp))
            .background(if (isEvent) color.copy(alpha = if (r.done) 0.45f else 0.85f) else color.copy(alpha = if (r.done) 0.35f else 0.22f))
            .border(if (dragging) 2.dp else 0.dp, color, RoundedCornerShape(7.dp))
            .pointerInput(r.key, r.start, r.end) { detectTapGestures(onTap = { r.onTap() }) }
            .pointerInput(r.key, r.start, r.end, dayPx) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { accum = 0f },
                    onDrag = { change, amount ->
                        change.consume()
                        accum += amount.x
                        moveDays = (accum / dayPx).roundToInt()
                    },
                    onDragEnd = {
                        if (moveDays != 0) r.commit(r.start + moveDays, r.end + moveDays)
                        moveDays = 0
                    },
                    onDragCancel = { moveDays = 0 },
                )
            },
    ) {
        if (!isEvent) Box(Modifier.fillMaxHeight().fillMaxWidth(p.coerceIn(0f, 1f)).background(color.copy(alpha = if (r.done) 0.6f else 0.85f)))
        Row(Modifier.fillMaxSize().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            T(
                when {
                    dragging -> Dates.span(start, end)
                    isEvent -> r.title
                    else -> "${pct(p)}%"
                },
                11, Modifier.weight(1f), weight = FontWeight.Bold,
                color = if (isEvent || p > 0.3f) Color.White else color, maxLines = 1,
            )
        }
        Box(
            Modifier.align(Alignment.CenterEnd).width(18.dp).fillMaxHeight()
                .pointerInput(r.key, r.start, r.end, dayPx) {
                    detectHorizontalDragGestures(
                        onDragStart = { accum = 0f },
                        onHorizontalDrag = { change, dx ->
                            change.consume()
                            accum += dx
                            endDays = maxOf((accum / dayPx).roundToInt(), -(r.end - r.start).toInt())
                        },
                        onDragEnd = {
                            if (endDays != 0) r.commit(r.start, r.end + endDays)
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
