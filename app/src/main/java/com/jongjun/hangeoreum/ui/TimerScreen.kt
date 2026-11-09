package com.jongjun.hangeoreum.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.background
import com.jongjun.hangeoreum.data.AppData
import com.jongjun.hangeoreum.data.Dates
import com.jongjun.hangeoreum.data.Level
import com.jongjun.hangeoreum.data.Store
import com.jongjun.hangeoreum.data.goal
import com.jongjun.hangeoreum.data.studyMsOnDay
import com.jongjun.hangeoreum.focus.FocusGuard

@Composable
fun TimerScreen(data: AppData, now: Long, nav: Nav, resumeTick: Int, openOverlaySettings: () -> Unit) {
    val ctx = LocalContext.current
    val t = data.timer
    val elapsed = t.elapsed(now)
    val today = Dates.today()
    val goal = data.goal(t.goalId)
    var picker by remember { mutableStateOf(false) }
    // re-evaluated on every resume (after returning from the permission screen)
    val canDraw = remember(resumeTick) { FocusGuard.canDraw(ctx) }
    val todaySessions = data.sessions.filter { Dates.dayOfMs(it.startMs) == today }.sortedByDescending { it.startMs }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(Modifier.fillMaxWidth().padding(start = 4.dp, top = 8.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                T("타이머", 26, Modifier.weight(1f), weight = FontWeight.Bold)
                Box(
                    Modifier.clip(RoundedCornerShape(12.dp)).background(pal.card)
                        .bounceClick { nav.openStats() }.padding(horizontal = 12.dp, vertical = 9.dp)
                ) { T("통계 보기", 14, weight = FontWeight.SemiBold, color = pal.blue) }
            }
        }
        item {
            CardBox(padding = 24.dp) {
                // goal selector
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(pal.fill)
                        .bounceClick { picker = true }.padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        T("무엇을 하고 있나요?", 12, weight = FontWeight.Medium, color = pal.sub)
                        T(goal?.title ?: "목표 선택 안 함", 16, Modifier.padding(top = 2.dp), weight = FontWeight.SemiBold,
                            color = if (goal == null) pal.tertiary else pal.text, maxLines = 1)
                    }
                    Icon(Icons.Filled.KeyboardArrowDown, null, tint = pal.sub)
                }

                Box(Modifier.fillMaxWidth().padding(top = 28.dp, bottom = 8.dp), contentAlignment = Alignment.Center) {
                    val ringP = ((elapsed / 1000) % 3600) / 3600f
                    ProgressRing(ringP, Modifier.size(240.dp), stroke = 8.dp, color = if (t.running) pal.blue else pal.tertiary)
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        T(Dates.clock(elapsed), 44, weight = FontWeight.Bold, tabular = true)
                        T(
                            when {
                                t.running -> "집중하는 중"
                                t.active -> "일시정지됨"
                                else -> "시작해볼까요?"
                            }, 15, Modifier.padding(top = 4.dp), weight = FontWeight.Medium,
                            color = if (t.running) pal.blue else pal.sub,
                        )
                    }
                }

                Row(Modifier.fillMaxWidth().padding(top = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    when {
                        !t.active -> PrimaryButton("시작", Modifier.weight(1f)) {
                            Store.startTimer()
                        }
                        t.running -> {
                            SecondaryButton("일시정지", Modifier.weight(1f)) { Store.pauseTimer() }
                            PrimaryButton("종료", Modifier.weight(1f)) { Store.stopTimer() }
                        }
                        else -> {
                            SecondaryButton("종료", Modifier.weight(1f)) { Store.stopTimer() }
                            PrimaryButton("계속하기", Modifier.weight(1f)) { Store.startTimer() }
                        }
                    }
                }
            }
        }

        item {
            CardBox {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        T("집중 모드", 17, weight = FontWeight.Bold)
                        T("타이머 중 다른 앱으로 나가면 경고하고, 무시하면 타이머를 멈춰요", 13, Modifier.padding(top = 4.dp), color = pal.sub)
                    }
                    Gap(12.dp)
                    Switch(
                        checked = data.focusGuard,
                        onCheckedChange = { Store.setFocusGuard(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White, checkedTrackColor = pal.blue,
                            uncheckedThumbColor = Color.White, uncheckedTrackColor = pal.line, uncheckedBorderColor = Color.Transparent,
                        ),
                    )
                }
                if (data.focusGuard && !canDraw) {
                    Column(
                        Modifier.fillMaxWidth().padding(top = 14.dp).clip(RoundedCornerShape(14.dp)).background(pal.blueSoft)
                            .padding(14.dp)
                    ) {
                        T("경고창을 띄우려면 ‘다른 앱 위에 표시’ 권한이 필요해요", 14, weight = FontWeight.Medium, color = pal.text)
                        T(
                            "권한 허용하러 가기 →", 14, Modifier.padding(top = 8.dp).bounceClick(onClick = openOverlaySettings),
                            weight = FontWeight.Bold, color = pal.blue,
                        )
                    }
                }
            }
        }

        item {
            CardBox {
                Row(Modifier.padding(bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    T("오늘 공부", 17, Modifier.weight(1f), weight = FontWeight.Bold)
                    T(Dates.duration(data.studyMsOnDay(today, now)), 17, weight = FontWeight.Bold, color = pal.blue)
                }
                if (todaySessions.isEmpty()) EmptyNote("아직 기록이 없어요")
                todaySessions.forEach { s ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            T(data.goal(s.goalId)?.title ?: "자유 공부", 15, weight = FontWeight.Medium, maxLines = 1)
                            T("${Dates.hm(s.startMs)} – ${Dates.hm(s.startMs + s.durationMs)}", 13, color = pal.sub)
                        }
                        T(Dates.duration(s.durationMs), 15, weight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }

    if (picker) GoalPickerSheet(data, t.goalId, onDismiss = { picker = false }) {
        Store.setTimerGoal(it)
        picker = false
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GoalPickerSheet(data: AppData, selected: String?, onDismiss: () -> Unit, onPick: (String?) -> Unit) {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val today = Dates.today()
    val candidates = data.goals.filter { !it.done && it.endDay >= today }
        .sortedWith(compareBy<com.jongjun.hangeoreum.data.Goal>({ it.level.ordinal }, { it.endDay }))
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = state, containerColor = pal.card) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp)
                .padding(bottom = 24.dp).navigationBarsPadding()
        ) {
            T("어떤 목표를 공부하나요?", 22, Modifier.padding(bottom = 12.dp), weight = FontWeight.Bold)
            PickRow("목표 선택 안 함", null, selected == null) { onPick(null) }
            Level.entries.forEach { lv ->
                val list = candidates.filter { it.level == lv }
                if (list.isNotEmpty()) {
                    T(lv.label, 13, Modifier.padding(top = 16.dp, bottom = 4.dp), weight = FontWeight.SemiBold, color = pal.sub)
                    list.forEach { g -> PickRow(g.title, Dates.period(g.level, g.startDay, g.endDay), g.id == selected) { onPick(g.id) } }
                }
            }
        }
    }
}

@Composable
private fun PickRow(title: String, sub: String?, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).bounceClick(onClick = onClick).padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            T(title, 16, weight = if (selected) FontWeight.Bold else FontWeight.Medium, color = if (selected) pal.blue else pal.text, maxLines = 1)
            if (sub != null) T(sub, 13, color = pal.sub, align = TextAlign.Start)
        }
        if (selected) T("✓", 18, weight = FontWeight.Bold, color = pal.blue)
    }
}
