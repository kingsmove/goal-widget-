package com.jongjun.hangeoreum.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.first
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.jongjun.hangeoreum.data.AppData
import com.jongjun.hangeoreum.data.Dates
import com.jongjun.hangeoreum.data.Level
import com.jongjun.hangeoreum.data.pct
import com.jongjun.hangeoreum.data.progress
import java.time.LocalDate


private data class PlanItem(
    val key: String,
    val title: String,
    val sub: String,
    val start: Long,
    val end: Long,
    val color: Color,
    val progress: Float?,
    val onClick: () -> Unit,
)

private data class Placed(val item: PlanItem, val x0: Float, val x1: Float, val lane: Int)

private const val ITEM_H = 48
private const val LANE_GAP = 8
private const val TOP = 38
private const val AXIS = 34
private const val MIN_ITEM_W = 96

@Composable
fun PlanScreen(data: AppData, nav: Nav) {
    var showGoals by rememberSaveable { mutableStateOf(true) }
    var view by rememberSaveable { mutableStateOf(0) }
    var monthW by rememberSaveable { mutableFloatStateOf(68f) }
    val today = Dates.today()
    val nowDate = LocalDate.now()

    // ---------- items ----------
    val items = buildList {
        data.events.filter { it.onPlan }.forEach { e ->
            add(
                PlanItem(
                    key = "e" + e.id, title = e.title,
                    sub = if (e.isRange) shortSpan(e.day, e.endDay) else shortDate(e.day),
                    start = e.day, end = e.endDay, color = eventColorOf(e), progress = null,
                    onClick = { nav.editEvent(EventEditReq(e)) },
                )
            )
        }
        if (showGoals) {
            data.goals.filter { it.level == Level.YEAR || it.level == Level.MONTH }.forEach { g ->
                val p = data.progress(g)
                add(
                    PlanItem(
                        key = "g" + g.id, title = g.title,
                        sub = "${g.level.label} 목표 · ${if (g.done) "완료" else Dates.dday(g.endDay)} · ${pct(p)}%",
                        start = g.startDay, end = g.endDay, color = goalColor(g), progress = p,
                        onClick = { nav.openGoal(g.id) },
                    )
                )
            }
        }
    }.sortedWith(compareBy<PlanItem> { it.start }.thenByDescending { it.end - it.start })

    // ---------- time range (in months) ----------
    val earliest = items.minOfOrNull { it.start }?.let { minOf(it, today) } ?: today
    val latest = items.maxOfOrNull { it.end }?.let { maxOf(it, today) } ?: today
    val firstMonth = Dates.date(earliest).withDayOfMonth(1).minusMonths(1)
    var lastMonth = Dates.date(latest).withDayOfMonth(1).plusMonths(2)
    if (lastMonth.isBefore(nowDate.withDayOfMonth(1).plusMonths(12))) lastMonth = nowDate.withDayOfMonth(1).plusMonths(12)
    val monthCount = (monthsBetween(firstMonth, lastMonth) + 1).coerceAtMost(120)

    fun pos(day: Long): Float {
        val d = Dates.date(day)
        return monthsBetween(firstMonth, d.withDayOfMonth(1)) + (d.dayOfMonth - 1f) / d.lengthOfMonth()
    }

    // ---------- lane packing (in month units) ----------
    val minSpan = MIN_ITEM_W / monthW
    val gap = 4f / monthW
    val laneEnds = mutableListOf<Float>()
    val placed = items.map { item ->
        val x0 = pos(item.start)
        val x1 = maxOf(pos(item.end + 1), x0 + minSpan)
        var lane = laneEnds.indexOfFirst { end -> end + gap <= x0 }
        if (lane == -1) { laneEnds.add(x1); lane = laneEnds.lastIndex } else laneEnds[lane] = x1
        Placed(item, x0, x1, lane)
    }
    val lanes = maxOf(laneEnds.size, 1)
    val mw: Dp = monthW.dp
    val totalW = mw * monthCount
    val bodyH = (ITEM_H + LANE_GAP) * lanes
    val totalH = (TOP + bodyH + 10 + AXIS).dp

    val hScroll = rememberScrollState()
    val density = LocalDensity.current
    LaunchedEffect(monthW, firstMonth) {
        snapshotFlow { hScroll.maxValue }.first { it > 0 }
        val target = with(density) { (mw * (pos(today) - 1f)).toPx() }
        hScroll.scrollTo(target.toInt().coerceAtLeast(0))
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
        Row(Modifier.fillMaxWidth().padding(start = 24.dp, end = 20.dp, top = 20.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            T(when (view) { 2 -> "이력서"; 1 -> "공정표"; else -> "장기 플랜" }, 26, Modifier.weight(1f), weight = FontWeight.Bold)
            IconCircle(Icons.Filled.Add, {
                if (view == 1) nav.editGoal(GoalEditReq(null, Level.PLAN, null))
                else nav.editEvent(EventEditReq(null, today, onPlan = true))
            }, tint = pal.blue, bg = pal.card)
        }
        Segmented(listOf("타임라인", "공정표", "이력서"), view, Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) { view = it }
        if (view == 2) {
            ResumeView(data, nav)
            return@Column
        }
        if (view == 1) {
            Box(Modifier.fillMaxWidth().height(560.dp)) { GanttScreen(data, nav, onBack = null) }
            return@Column
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ChoiceChip("연간·월간 목표", showGoals) { showGoals = !showGoals }
            T("", 1, Modifier.weight(1f))
            ChoiceChip("−", false) { monthW = (monthW - 16f).coerceAtLeast(36f) }
            ChoiceChip("+", false) { monthW = (monthW + 16f).coerceAtMost(160f) }
        }

        Box(
            Modifier.padding(horizontal = 20.dp).fillMaxWidth().clip(RoundedCornerShape(24.dp))
                .background(pal.card).padding(vertical = 12.dp)
        ) {
            Box(Modifier.horizontalScroll(hScroll).padding(horizontal = 12.dp)) {
                Box(Modifier.width(totalW).height(totalH)) {
                    // year bands
                    var m = 0
                    while (m < monthCount) {
                        val ym = firstMonth.plusMonths(m.toLong())
                        val span = minOf(12 - ym.monthValue + 1, monthCount - m)
                        val c = pal.yearColors[Math.floorMod(ym.year, pal.yearColors.size)]
                        Box(
                            Modifier.offset(x = mw * m + 2.dp).width(mw * span - 4.dp).height(totalH)
                                .clip(RoundedCornerShape(12.dp))
                                .background(c.copy(alpha = if (pal.dark) 0.10f else 0.05f))
                                .border(1.dp, c.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
                        ) {
                            Box(
                                Modifier.padding(6.dp).clip(RoundedCornerShape(6.dp)).background(c)
                                    .padding(horizontal = 7.dp, vertical = 2.dp)
                            ) { T("${ym.year}", 13, weight = FontWeight.Bold, color = Color.White) }
                        }
                        m += span
                    }

                    // today line
                    val tx = mw * pos(today)
                    Box(
                        Modifier.offset(x = tx, y = (TOP - 6).dp).width(2.dp).height((bodyH + 12).dp)
                            .background(pal.red.copy(alpha = 0.7f))
                    )
                    T("오늘", 10, Modifier.offset(x = tx + 4.dp, y = (TOP - 12).dp), weight = FontWeight.Bold, color = pal.red)

                    if (items.isEmpty()) {
                        T(
                            "오른쪽 위 + 로 졸업, 인턴, 시험, 여행 같은\n긴 일정을 추가해보세요", 13,
                            Modifier.offset(x = tx + 12.dp, y = (TOP + 8).dp), color = pal.sub,
                        )
                    }

                    // items
                    placed.forEach { p ->
                        val item = p.item
                        val x = mw * p.x0
                        val w = mw * (p.x1 - p.x0)
                        val y = (TOP + p.lane * (ITEM_H + LANE_GAP)).dp
                        Column(
                            Modifier.offset(x = x, y = y).width(w).height(ITEM_H.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (pal.dark) item.color.copy(alpha = 0.22f) else lerpWhite(item.color))
                                .border(1.dp, item.color.copy(alpha = 0.45f), RoundedCornerShape(10.dp))
                                .bounceClick(onClick = item.onClick)
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                        ) {
                            T(item.title, 12, weight = FontWeight.Bold, color = if (pal.dark) pal.text else item.color.darker(), maxLines = 1)
                            T(item.sub, 10, color = pal.sub, maxLines = 1)
                            if (item.progress != null) {
                                Box(Modifier.padding(top = 2.dp)) {
                                    ProgressLine(item.progress, color = item.color, height = 3.dp)
                                }
                            }
                        }
                    }

                    // month axis
                    val axisY = (TOP + bodyH + 10).dp
                    for (i in 0 until monthCount) {
                        val ym = firstMonth.plusMonths(i.toLong())
                        val isNow = ym.year == nowDate.year && ym.monthValue == nowDate.monthValue
                        Box(
                            Modifier.offset(x = mw * i + 3.dp, y = axisY).width(mw - 6.dp).height((AXIS - 6).dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isNow) pal.blue else pal.fill),
                            contentAlignment = Alignment.Center,
                        ) {
                            T(
                                if (monthW >= 60f) "${ym.year % 100}.${"%02d".format(ym.monthValue)}" else "${ym.monthValue}월",
                                11, weight = FontWeight.SemiBold, color = if (isNow) Color.White else pal.sub, maxLines = 1,
                            )
                        }
                    }
                }
            }
        }

        T(
            "막대를 누르면 수정할 수 있어요 · 일정 추가 시 ‘장기 플랜에 표시’를 켜면 여기에 나와요", 12,
            Modifier.padding(horizontal = 28.dp, vertical = 12.dp), color = pal.sub,
        )
        Box(Modifier.padding(horizontal = 20.dp)) { ExcelCard(data) }
        VGap(12.dp)
        Box(Modifier.padding(horizontal = 20.dp)) { CanvasImportCard(data) }
    }
}

private fun monthsBetween(a: LocalDate, b: LocalDate): Int = (b.year - a.year) * 12 + (b.monthValue - a.monthValue)

private fun shortDate(day: Long): String {
    val d = Dates.date(day)
    return "${d.year % 100}.${d.monthValue}.${d.dayOfMonth}"
}

private fun shortSpan(a: Long, b: Long): String = "${shortDate(a)} ~ ${shortDate(b)}"

private fun lerpWhite(c: Color): Color = Color(
    red = 1f - (1f - c.red) * 0.12f,
    green = 1f - (1f - c.green) * 0.12f,
    blue = 1f - (1f - c.blue) * 0.12f,
)

private fun Color.darker(): Color = Color(red * 0.75f, green * 0.75f, blue * 0.75f)
