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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jongjun.hangeoreum.data.AppData
import com.jongjun.hangeoreum.data.Dates
import com.jongjun.hangeoreum.data.Store
import com.jongjun.hangeoreum.data.children
import com.jongjun.hangeoreum.data.descendantIds
import com.jongjun.hangeoreum.data.goal
import com.jongjun.hangeoreum.data.pct
import com.jongjun.hangeoreum.data.progress
import com.jongjun.hangeoreum.data.studyMsForGoal

@Composable
fun GoalDetailScreen(
    goalId: String,
    data: AppData,
    now: Long,
    nav: Nav,
    onBack: () -> Unit,
    onDeleted: () -> Unit,
) {
    val g = data.goal(goalId)
    if (g == null) {
        LaunchedEffect(goalId) { onBack() }
        return
    }
    var confirmDelete by remember { mutableStateOf(false) }
    val p = data.progress(g)
    val kids = data.children(g.id)
    val childLevel = g.level.child()
    val parent = data.goal(g.parentId)
    val study = data.studyMsForGoal(g.id, now)
    val ids = data.descendantIds(g.id) + g.id
    val gc = goalColor(g)
    val sessions = data.sessions.filter { it.goalId in ids }.sortedByDescending { it.startMs }.take(5)

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconCircle(Icons.AutoMirrored.Filled.ArrowBack, onBack, bg = pal.bg)
            T("", 1, Modifier.weight(1f))
            IconCircle(Icons.Filled.Edit, { nav.editGoal(GoalEditReq(g, g.level, g.parentId)) }, tint = pal.sub, bg = pal.bg)
            IconCircle(Icons.Filled.Delete, { confirmDelete = true }, tint = pal.sub, bg = pal.bg)
        }

        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Column(Modifier.padding(start = 4.dp, bottom = 8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Pill("${g.level.label} 목표", color = gc, bg = gc.copy(alpha = 0.12f))
                        Gap(8.dp)
                        T("${Dates.period(g.level, g.startDay, g.endDay)} · ${Dates.dday(g.endDay)}", 14, color = pal.sub)
                    }
                    T(g.title, 26, Modifier.padding(top = 10.dp), weight = FontWeight.Bold)
                    if (parent != null) {
                        T(
                            "↑ ${parent.title}", 14,
                            Modifier.padding(top = 8.dp).bounceClick { nav.openGoal(parent.id) },
                            weight = FontWeight.Medium, color = pal.blue,
                        )
                    }
                    if (g.memo.isNotBlank()) T(g.memo, 15, Modifier.padding(top = 10.dp), color = pal.sub)
                }
            }

            item {
                CardBox {
                    Row(verticalAlignment = Alignment.Bottom) {
                        T("${pct(p)}", 44, weight = FontWeight.Bold, color = gc, tabular = true)
                        T("%", 22, Modifier.padding(bottom = 6.dp, start = 2.dp), weight = FontWeight.Bold, color = gc)
                        T("", 1, Modifier.weight(1f))
                        if (study > 0) T("공부 ${Dates.duration(study)}", 14, Modifier.padding(bottom = 8.dp), weight = FontWeight.SemiBold, color = pal.sub)
                    }
                    ProgressLine(p, Modifier.padding(top = 10.dp), color = gc, height = 10.dp)
                    if (g.numeric) {
                        Row(Modifier.fillMaxWidth().padding(top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                            T("${g.current} / ${g.target}${g.unit}", 20, Modifier.weight(1f), weight = FontWeight.Bold, tabular = true)
                            SecondaryButton("−", Modifier.width(56.dp)) { Store.setGoalCurrent(g.id, g.current - 1) }
                            Gap(8.dp)
                            PrimaryButton("+1", Modifier.width(72.dp), color = gc) { Store.setGoalCurrent(g.id, g.current + 1) }
                        }
                    }
                    if (kids.isNotEmpty()) {
                        T(
                            "세부 목표 ${kids.size}개 중 ${kids.count { it.done || data.progress(it) >= 1f }}개 완료", 13,
                            Modifier.padding(top = 10.dp), color = pal.sub,
                        )
                    }
                    Row(Modifier.padding(top = 18.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SecondaryButton(
                            if (g.done) "완료 취소" else "완료로 표시",
                            Modifier.weight(1f),
                            textColor = if (g.done) pal.sub else pal.text,
                        ) { Store.toggleGoal(g.id) }
                        PrimaryButton("타이머 시작", Modifier.weight(1f), color = gc) { nav.startTimerFor(g.id) }
                    }
                    if (g.level == com.jongjun.hangeoreum.data.Level.DAY && !g.done) {
                        T(
                            "내일로 미루기 →", 14,
                            Modifier.padding(top = 14.dp).bounceClick { Store.moveGoals(listOf(g.id), g.endDay + 1) },
                            weight = FontWeight.SemiBold, color = pal.sub,
                        )
                    }
                }
            }

            if (childLevel != null) {
                item {
                    CardBox {
                        SectionTitle("세부 목표", "+ 추가") {
                            nav.editGoal(GoalEditReq(null, childLevel, g.id))
                        }
                        if (kids.isEmpty()) EmptyNote(childHint(g.level))
                        kids.forEach { k -> GoalCheckRow(k, data, nav, showParent = false) }
                    }
                }
            }

            if (sessions.isNotEmpty()) {
                item {
                    CardBox {
                        SectionTitle("최근 공부 기록")
                        sessions.forEach { s ->
                            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    T(Dates.relative(Dates.dayOfMs(s.startMs)), 15, weight = FontWeight.Medium)
                                    val sub = data.goal(s.goalId)?.takeIf { it.id != g.id }?.title
                                    T(Dates.hm(s.startMs) + (sub?.let { " · $it" } ?: ""), 13, color = pal.sub, maxLines = 1)
                                }
                                T(Dates.duration(s.durationMs), 15, weight = FontWeight.SemiBold, color = pal.blue)
                            }
                        }
                    }
                }
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            containerColor = pal.card,
            title = { T("목표를 삭제할까요?", 19, weight = FontWeight.Bold) },
            text = { T(if (kids.isNotEmpty()) "세부 목표도 함께 삭제돼요." else "삭제하면 되돌릴 수 없어요.", 15, color = pal.sub) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    Store.deleteGoal(g.id)
                    onDeleted()
                }) { T("삭제", 16, weight = FontWeight.SemiBold, color = pal.red) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { T("취소", 16, weight = FontWeight.SemiBold, color = pal.sub) }
            },
        )
    }
}

private fun childHint(level: com.jongjun.hangeoreum.data.Level) = when (level) {
    com.jongjun.hangeoreum.data.Level.YEAR -> "월별 목표로 나눠보세요"
    com.jongjun.hangeoreum.data.Level.MONTH -> "주간·일간 목표로 나눠보세요 · 오늘은 어디까지"
    com.jongjun.hangeoreum.data.Level.WEEK -> "하루 단위로 나눠보세요 · 오늘은 어디까지"
    com.jongjun.hangeoreum.data.Level.DAY -> ""
    com.jongjun.hangeoreum.data.Level.PLAN -> "하루 단위 할 일로 나눠보세요"
}
