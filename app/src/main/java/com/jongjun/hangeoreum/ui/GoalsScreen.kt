package com.jongjun.hangeoreum.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.jongjun.hangeoreum.data.AppData
import com.jongjun.hangeoreum.data.Dates
import com.jongjun.hangeoreum.data.Goal
import com.jongjun.hangeoreum.data.Level
import com.jongjun.hangeoreum.data.Store
import com.jongjun.hangeoreum.data.children
import com.jongjun.hangeoreum.data.goal
import com.jongjun.hangeoreum.data.pct
import com.jongjun.hangeoreum.data.progress
import com.jongjun.hangeoreum.data.studyMsForGoal

@Composable
fun GoalsScreen(data: AppData, now: Long, nav: Nav) {
    var levelIdx by rememberSaveable { mutableStateOf(1) }
    var showPast by rememberSaveable { mutableStateOf(false) }
    val level = Level.entries[levelIdx]
    val today = Dates.today()
    val all = data.goals.filter { it.level == level }
    val current = all.filter { it.endDay >= today && !(it.done && it.endDay < today) }
        .sortedWith(compareBy<Goal> { it.done }.thenBy { it.endDay }.thenBy { it.createdAt })
    val past = all.filter { it.endDay < today }.sortedByDescending { it.endDay }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(Modifier.fillMaxWidth().padding(start = 4.dp, top = 8.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                T("목표", 26, Modifier.weight(1f), weight = FontWeight.Bold)
                IconCircle(Icons.Filled.Add, { nav.editGoal(GoalEditReq(null, level, null)) }, tint = pal.blue, bg = pal.card)
            }
        }
        item {
            Segmented(Level.entries.map { it.label }, levelIdx) { levelIdx = it }
        }
        item {
            T(hint(level), 13, Modifier.padding(start = 4.dp, top = 2.dp), color = pal.sub)
        }
        if (current.isEmpty()) {
            item { CardBox { EmptyNote("${level.label} 목표가 없어요\n오른쪽 위 + 로 추가해보세요") } }
        }
        items(current, key = { it.id }) { g -> GoalCard(g, data, now, nav) }

        if (past.isNotEmpty()) {
            item {
                T(
                    if (showPast) "지난 목표 접기" else "지난 목표 ${past.size}개 보기", 14,
                    Modifier.fillMaxWidth().plainClick { showPast = !showPast }.padding(vertical = 10.dp),
                    weight = FontWeight.SemiBold, color = pal.sub, align = TextAlign.Center,
                )
            }
            if (showPast) items(past, key = { "p" + it.id }) { g -> GoalCard(g, data, now, nav) }
        }
    }
}

private fun hint(level: Level) = when (level) {
    Level.YEAR -> "올해 이루고 싶은 큰 목표"
    Level.MONTH -> "몇 월까지 끝낼지 정하는 목표 · 자격증, 프로젝트"
    Level.WEEK -> "이번 주에 끝낼 단위"
    Level.DAY -> "오늘은 어디까지 할지"
}

@Composable
fun GoalCard(g: Goal, data: AppData, now: Long, nav: Nav) {
    val p = data.progress(g)
    val kids = data.children(g.id)
    val study = data.studyMsForGoal(g.id, now)
    val parent = data.goal(g.parentId)
    val gc = goalColor(g)
    CardBox(Modifier.bounceClick { nav.openGoal(g.id) }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (g.level == Level.DAY) {
                CheckDot(g.done, { Store.toggleGoal(g.id) })
                Gap(12.dp)
            }
            Column(Modifier.weight(1f)) {
                T(g.title, 17, weight = FontWeight.Bold, color = if (g.done) pal.tertiary else pal.text, maxLines = 2)
                val meta = buildList {
                    add(Dates.period(g.level, g.startDay, g.endDay))
                    if (parent != null) add(parent.title)
                }.joinToString(" · ")
                T(meta, 13, Modifier.padding(top = 3.dp), color = pal.sub, maxLines = 1)
            }
            Gap(8.dp)
            if (g.done) Pill("완료", color = pal.green, bg = pal.fill)
            else Pill(Dates.dday(g.endDay), color = gc, bg = gc.copy(alpha = 0.12f))
        }
        if (g.level != Level.DAY || kids.isNotEmpty()) {
            Row(Modifier.padding(top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                ProgressLine(p, Modifier.weight(1f), color = gc)
                Gap(12.dp)
                T("${pct(p)}%", 15, Modifier.width(44.dp), weight = FontWeight.Bold, color = gc, align = TextAlign.End, tabular = true)
            }
        }
        val foot = buildList {
            if (kids.isNotEmpty()) add("세부 목표 ${kids.count { it.done || data.progress(it) >= 1f }}/${kids.size}")
            if (study > 0) add("공부 ${Dates.duration(study)}")
        }
        if (foot.isNotEmpty()) T(foot.joinToString(" · "), 13, Modifier.padding(top = 10.dp), color = pal.sub)
    }
}
