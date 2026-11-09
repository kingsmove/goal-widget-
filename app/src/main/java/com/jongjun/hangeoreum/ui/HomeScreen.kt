package com.jongjun.hangeoreum.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import com.jongjun.hangeoreum.update.UpdateInfo
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.jongjun.hangeoreum.data.AppData
import com.jongjun.hangeoreum.data.Dates
import com.jongjun.hangeoreum.data.Event
import com.jongjun.hangeoreum.data.Goal
import com.jongjun.hangeoreum.data.Level
import com.jongjun.hangeoreum.data.Store
import com.jongjun.hangeoreum.data.activeGoals
import com.jongjun.hangeoreum.data.goal
import com.jongjun.hangeoreum.data.pct
import com.jongjun.hangeoreum.data.progress
import com.jongjun.hangeoreum.data.studyMsOnDay
import com.jongjun.hangeoreum.data.todayGoals
import com.jongjun.hangeoreum.data.todayProgress
import com.jongjun.hangeoreum.data.upcomingEvents

@Composable
fun HomeScreen(data: AppData, now: Long, nav: Nav, update: UpdateInfo?, openSettings: () -> Unit) {
    val today = Dates.today()
    val todays = data.todayGoals(today)
    val prog = data.todayProgress(today)
    val active = data.activeGoals(today)
    val events = data.upcomingEvents(today).take(3)

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(Modifier.padding(start = 4.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    T(Dates.full(today), 15, weight = FontWeight.Medium, color = pal.sub)
                    T(greeting(prog, todays.isEmpty()), 26, Modifier.padding(top = 4.dp), weight = FontWeight.Bold)
                }
                IconCircle(Icons.Filled.Settings, openSettings, tint = pal.sub, bg = pal.card)
            }
        }

        if (update != null) {
            item {
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(pal.blueSoft)
                        .bounceClick(onClick = openSettings).padding(horizontal = 18.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    T("새 버전이 나왔어요", 15, Modifier.weight(1f), weight = FontWeight.SemiBold, color = pal.blue)
                    T("설치하기 →", 14, weight = FontWeight.Bold, color = pal.blue)
                }
            }
        }

        item {
            CardBox {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(92.dp), contentAlignment = Alignment.Center) {
                        ProgressRing(prog, Modifier.size(92.dp), stroke = 10.dp)
                        T("${pct(prog)}%", 20, weight = FontWeight.Bold, tabular = true)
                    }
                    Gap(20.dp)
                    Column(Modifier.weight(1f)) {
                        T("오늘 달성률", 14, weight = FontWeight.Medium, color = pal.sub)
                        T("할 일 ${todays.count { it.done }} / ${todays.size}", 20, Modifier.padding(top = 2.dp), weight = FontWeight.Bold)
                        Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            T("공부 ", 14, color = pal.sub)
                            T(Dates.duration(data.studyMsOnDay(today, now)), 14, weight = FontWeight.SemiBold, color = pal.blue)
                            if (data.timer.running) {
                                Gap(6.dp)
                                Pill("측정 중")
                            }
                        }
                    }
                }
            }
        }

        item {
            CardBox {
                SectionTitle("오늘 할 일", "+ 추가") { nav.editGoal(GoalEditReq(null, Level.DAY, null)) }
                if (todays.isEmpty()) EmptyNote("오늘 할 일을 추가해보세요")
                todays.forEach { g -> GoalCheckRow(g, data, nav) }
            }
        }

        item {
            CardBox {
                SectionTitle("진행 중인 목표", "+ 추가") { nav.editGoal(GoalEditReq(null, Level.MONTH, null)) }
                if (active.isEmpty()) EmptyNote("연간·월간 목표를 세워보세요")
                active.take(6).forEachIndexed { i, g ->
                    if (i > 0) VGap(18.dp)
                    GoalProgressRow(g, data, nav)
                }
            }
        }

        item {
            CardBox {
                SectionTitle("다가오는 일정", "+ 추가") { nav.editEvent(EventEditReq(null)) }
                if (events.isEmpty()) EmptyNote("예정된 일정이 없어요")
                events.forEach { e -> EventRow(e) { nav.editEvent(EventEditReq(e)) } }
            }
        }
    }
}

private fun greeting(p: Float, empty: Boolean): String = when {
    empty -> "오늘 할 일을 정해볼까요?"
    p >= 1f -> "오늘 목표 완료!"
    p >= 0.5f -> "절반 넘게 왔어요"
    p > 0f -> "좋아요, 계속 가볼까요"
    else -> "오늘도 한 걸음씩"
}

@Composable
fun GoalCheckRow(g: Goal, data: AppData, nav: Nav, showParent: Boolean = true) {
    val parent = if (showParent) data.goal(g.parentId) else null
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).bounceClick { nav.openGoal(g.id) }.padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CheckDot(g.done, { Store.toggleGoal(g.id) })
        Gap(14.dp)
        Column(Modifier.weight(1f)) {
            T(g.title, 16, weight = FontWeight.Medium, color = if (g.done) pal.tertiary else pal.text, maxLines = 2)
            if (parent != null) T(parent.title, 13, Modifier.padding(top = 2.dp), color = pal.sub, maxLines = 1)
        }
        val p = data.progress(g)
        if (!g.done && p > 0f) T("${pct(p)}%", 14, weight = FontWeight.SemiBold, color = pal.blue)
    }
}

@Composable
fun GoalProgressRow(g: Goal, data: AppData, nav: Nav) {
    val p = data.progress(g)
    val gc = goalColor(g)
    Column(Modifier.fillMaxWidth().bounceClick { nav.openGoal(g.id) }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Pill(g.level.label, color = pal.sub, bg = pal.fill)
            Gap(8.dp)
            T(g.title, 16, Modifier.weight(1f), weight = FontWeight.SemiBold, maxLines = 1)
            Gap(8.dp)
            T(Dates.dday(g.endDay), 13, weight = FontWeight.SemiBold, color = pal.sub)
        }
        Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            ProgressLine(p, Modifier.weight(1f), color = gc)
            Gap(12.dp)
            T("${pct(p)}%", 14, Modifier.width(40.dp), weight = FontWeight.Bold, color = gc, align = TextAlign.End, tabular = true)
        }
    }
}

@Composable
fun EventRow(e: Event, onClick: () -> Unit) {
    val d = Dates.date(e.day)
    Row(
        Modifier.fillMaxWidth().bounceClick(onClick = onClick).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val ec = itemColor(e.color)
        Column(
            Modifier.size(48.dp).clip(RoundedCornerShape(14.dp)).background(ec.copy(alpha = 0.12f)),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            T("${d.monthValue}월", 11, weight = FontWeight.Medium, color = ec)
            T("${d.dayOfMonth}", 17, weight = FontWeight.Bold, color = ec)
        }
        Gap(14.dp)
        Column(Modifier.weight(1f)) {
            T(e.title, 16, weight = FontWeight.SemiBold, maxLines = 1)
            T(Dates.eventWhen(e.day, e.endDay, e.minute), 13, Modifier.padding(top = 2.dp), color = pal.sub, maxLines = 1)
        }
    }
}
