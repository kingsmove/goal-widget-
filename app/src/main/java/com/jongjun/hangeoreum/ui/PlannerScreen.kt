package com.jongjun.hangeoreum.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.jongjun.hangeoreum.data.AppData
import com.jongjun.hangeoreum.data.Dates
import com.jongjun.hangeoreum.data.Session
import com.jongjun.hangeoreum.data.Store
import com.jongjun.hangeoreum.data.Subject
import com.jongjun.hangeoreum.data.dayGoalsOn
import com.jongjun.hangeoreum.data.goal
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId

private const val FIRST_HOUR = 6
private const val HOURS = 20 // 06:00 ~ 02:00

private fun dayStartMs(day: Long): Long =
    LocalDate.ofEpochDay(day).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

/** Sessions of a day, including the running timer as a pseudo-session. */
private fun sessionsOn(d: AppData, day: Long, now: Long): List<Session> {
    val list = d.sessions.filter { Dates.dayOfMs(it.startMs) == day }.toMutableList()
    val t = d.timer
    if (t.active && day == Dates.today()) {
        val start = t.sessionStartMs.takeIf { it > 0 } ?: (now - t.elapsed(now))
        list += Session("running", t.goalId, start, t.elapsed(now), t.subjectId)
    }
    return list.sortedBy { it.startMs }
}

@Composable
fun sessionColor(d: AppData, s: Session): Color {
    d.subjects.firstOrNull { it.id == s.subjectId }?.let { return Color(it.color) }
    d.goal(s.goalId)?.let { return goalColor(it) }
    return pal.blue
}

fun sessionLabel(d: AppData, s: Session): String =
    d.subjects.firstOrNull { it.id == s.subjectId }?.name ?: d.goal(s.goalId)?.title ?: "기타"

@Composable
fun PlannerScreen(data: AppData, now: Long, nav: Nav, onBack: () -> Unit) {
    val today = Dates.today()
    var day by rememberSaveable { mutableLongStateOf(today) }
    var subjectsSheet by remember { mutableStateOf(false) }
    var manualSheet by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<Session?>(null) }
    val sessions = sessionsOn(data, day, now)
    val total = sessions.sumOf { it.durationMs }
    val todos = data.dayGoalsOn(day)
    val colors = sessions.associate { it.id to sessionColor(data, it) }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconCircle(Icons.AutoMirrored.Filled.ArrowBack, onBack, bg = pal.bg)
            T("데일리 플래너", 18, Modifier.padding(start = 6.dp).weight(1f), weight = FontWeight.Bold)
        }
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // date + total
            item {
                CardBox {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconCircle(Icons.Filled.KeyboardArrowLeft, { day-- }, bg = pal.fill, size = 34.dp)
                        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                            T(Dates.full(day), 16, weight = FontWeight.Bold)
                            if (day != today) T("오늘로", 12, Modifier.bounceClick { day = today }, color = pal.blue, weight = FontWeight.SemiBold)
                        }
                        IconCircle(Icons.Filled.KeyboardArrowRight, { if (day < today) day++ }, bg = pal.fill, size = 34.dp)
                    }
                    Row(Modifier.padding(top = 16.dp), verticalAlignment = Alignment.Bottom) {
                        T("총 공부", 14, Modifier.weight(1f).padding(bottom = 4.dp), color = pal.sub)
                        T(Dates.clock(total).let { if (it.startsWith("00:")) it else it }, 32, weight = FontWeight.Bold, tabular = true)
                    }
                }
            }

            // per subject
            item {
                CardBox {
                    SectionTitle("과목별", "과목 관리") { subjectsSheet = true }
                    val groups = sessions.groupBy { sessionLabel(data, it) }
                        .map { (k, v) -> Triple(k, v.sumOf { it.durationMs }, v.first()) }
                        .sortedByDescending { it.second }
                    if (groups.isEmpty()) EmptyNote("공부 기록이 없어요")
                    val top = groups.firstOrNull()?.second ?: 1L
                    groups.forEach { (name, ms, s) ->
                        val c = colors[s.id] ?: pal.blue
                        Column(Modifier.fillMaxWidth().padding(vertical = 7.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(10.dp).clip(CircleShape).background(c))
                                Gap(8.dp)
                                T(name, 15, Modifier.weight(1f), weight = FontWeight.Medium, maxLines = 1)
                                T(Dates.duration(ms), 14, weight = FontWeight.SemiBold)
                            }
                            ProgressLine(ms.toFloat() / top, Modifier.padding(top = 6.dp), color = c, height = 6.dp)
                        }
                    }
                }
            }

            // timetable
            item {
                CardBox(padding = 16.dp) {
                    SectionTitle("타임테이블", "+ 직접 기록") { manualSheet = true }
                    val base = dayStartMs(day)
                    for (h in 0 until HOURS) {
                        val hour = FIRST_HOUR + h
                        Row(Modifier.fillMaxWidth().height(22.dp), verticalAlignment = Alignment.CenterVertically) {
                            T("${hour % 24}", 11, Modifier.width(26.dp), color = pal.sub, align = TextAlign.Center, tabular = true)
                            for (m in 0 until 6) {
                                val cs = base + (hour * 60 + m * 10) * 60_000L
                                val ce = cs + 10 * 60_000L
                                val hit = sessions.maxByOrNull { s ->
                                    (minOf(ce, s.startMs + s.durationMs) - maxOf(cs, s.startMs)).coerceAtLeast(0)
                                }?.takeIf { s -> minOf(ce, s.startMs + s.durationMs) - maxOf(cs, s.startMs) >= 3 * 60_000L }
                                Box(
                                    Modifier.weight(1f).height(20.dp).padding(horizontal = 1.dp)
                                        .clip(RoundedCornerShape(3.dp))
                                        .background(hit?.let { colors[it.id] } ?: pal.fill)
                                )
                            }
                        }
                    }
                    T("10분 칸마다 3분 넘게 공부하면 그 과목 색으로 칠해져요", 11, Modifier.padding(top = 8.dp), color = pal.tertiary)
                }
            }

            // to-dos
            item {
                CardBox {
                    SectionTitle("할 일", "+ 추가") { nav.editGoal(GoalEditReq(null, com.jongjun.hangeoreum.data.Level.DAY, null)) }
                    if (todos.isEmpty()) EmptyNote("이 날 할 일이 없어요")
                    todos.forEach { g -> GoalCheckRow(g, data, nav) }
                }
            }

            // records
            item {
                CardBox {
                    SectionTitle("기록")
                    if (sessions.isEmpty()) EmptyNote("타이머를 쓰거나 ‘직접 기록’으로 추가해요")
                    sessions.forEach { s ->
                        Row(
                            Modifier.fillMaxWidth().bounceClick { if (s.id != "running") deleting = s }.padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(Modifier.width(4.dp).height(30.dp).clip(RoundedCornerShape(2.dp)).background(colors[s.id] ?: pal.blue))
                            Gap(10.dp)
                            Column(Modifier.weight(1f)) {
                                T(sessionLabel(data, s) + if (s.id == "running") " · 측정 중" else "", 15, weight = FontWeight.Medium, maxLines = 1)
                                T("${Dates.hm(s.startMs)} – ${Dates.hm(s.startMs + s.durationMs)}", 12, color = pal.sub)
                            }
                            T(Dates.duration(s.durationMs), 14, weight = FontWeight.SemiBold)
                        }
                    }
                }
            }

            // one-line memo
            item { DayNoteCard(data, day) }
        }
    }

    if (subjectsSheet) SubjectsSheet(data) { subjectsSheet = false }
    if (manualSheet) ManualSessionSheet(data, day) { manualSheet = false }
    deleting?.let { s ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            containerColor = pal.card,
            title = { T("이 기록을 지울까요?", 19, weight = FontWeight.Bold) },
            text = { T("${sessionLabel(data, s)} · ${Dates.hm(s.startMs)} – ${Dates.hm(s.startMs + s.durationMs)}", 15, color = pal.sub) },
            confirmButton = {
                TextButton(onClick = { Store.deleteSession(s.id); deleting = null }) { T("지우기", 16, weight = FontWeight.SemiBold, color = pal.red) }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { T("취소", 16, color = pal.sub) } },
        )
    }
}

@Composable
private fun DayNoteCard(data: AppData, day: Long) {
    val saved = data.dayNotes[day] ?: ""
    var text by remember(day, saved) { mutableStateOf(saved) }
    CardBox {
        Row(verticalAlignment = Alignment.CenterVertically) {
            T("오늘의 한 줄", 17, Modifier.weight(1f), weight = FontWeight.Bold)
            if (text != saved) T("저장", 14, Modifier.bounceClick { Store.setDayNote(day, text.trim()) }, weight = FontWeight.Bold, color = pal.blue)
        }
        InputField(text, { text = it }, "오늘 공부 어땠어요?", size = 16, singleLine = false)
    }
}

/** Chips to pick a subject (used by the timer and manual entry). */
@Composable
fun SubjectChips(data: AppData, selected: String?, onManage: () -> Unit, onPick: (String?) -> Unit) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ChoiceChip("없음", selected == null) { onPick(null) }
        data.subjects.forEach { s ->
            val sel = s.id == selected
            Row(
                Modifier.clip(RoundedCornerShape(12.dp)).background(if (sel) Color(s.color) else pal.fill)
                    .bounceClick { onPick(s.id) }.padding(horizontal = 12.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (!sel) { Box(Modifier.size(8.dp).clip(CircleShape).background(Color(s.color))); Gap(6.dp) }
                T(s.name, 14, weight = FontWeight.SemiBold, color = if (sel) Color.White else pal.text)
            }
        }
        ChoiceChip("+ 과목", false, onManage)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubjectsSheet(data: AppData, onDismiss: () -> Unit) {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var name by remember { mutableStateOf("") }
    var colorIdx by remember { mutableIntStateOf(data.subjects.size % 12) }
    var custom by remember { mutableStateOf<Int?>(null) }
    val palette = pal.itemColors

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = state, containerColor = pal.card) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp)
                .navigationBarsPadding().imePadding().padding(bottom = 16.dp)
        ) {
            T("과목 관리", 22, weight = FontWeight.Bold)
            if (data.subjects.isEmpty()) T("타이머와 플래너에서 고를 과목을 만들어요", 14, Modifier.padding(top = 6.dp), color = pal.sub)
            data.subjects.forEach { s ->
                Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(14.dp).clip(CircleShape).background(Color(s.color)))
                    Gap(10.dp)
                    T(s.name, 16, Modifier.weight(1f), weight = FontWeight.Medium)
                    T("삭제", 14, Modifier.bounceClick { Store.deleteSubject(s.id) }, color = pal.red, weight = FontWeight.SemiBold)
                }
            }
            FieldLabel("새 과목")
            InputField(name, { name = it }, "예: 건축시공, 법규, 토익 LC", size = 18)
            VGap(12.dp)
            ColorChooser(colorIdx, custom, allowDefault = false, onIndex = { colorIdx = it; custom = null }, onCustom = { custom = it })
            VGap(20.dp)
            PrimaryButton("과목 추가", Modifier.fillMaxWidth(), enabled = name.isNotBlank()) {
                Store.upsertSubject(Subject(Store.newId(), name.trim(), custom ?: palette[colorIdx.coerceIn(0, palette.lastIndex)].toArgb()))
                name = ""
                colorIdx = (colorIdx + 1) % palette.size
                custom = null
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ManualSessionSheet(data: AppData, day: Long, onDismiss: () -> Unit) {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    var subject by remember { mutableStateOf(data.timer.subjectId) }
    var startMin by remember { mutableIntStateOf(9 * 60) }
    var endMin by remember { mutableIntStateOf(10 * 60) }
    var picking by remember { mutableIntStateOf(0) } // 1 start, 2 end
    var manage by remember { mutableStateOf(false) }
    val dur = ((endMin - startMin + 1440) % 1440).let { if (it == 0) 1440 else it }
    fun close() { scope.launch { state.hide() }.invokeOnCompletion { onDismiss() } }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = state, containerColor = pal.card) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp)
                .navigationBarsPadding().padding(bottom = 16.dp)
        ) {
            T("직접 기록", 22, weight = FontWeight.Bold)
            T(Dates.full(day), 14, Modifier.padding(top = 4.dp), color = pal.sub)
            FieldLabel("과목")
            SubjectChips(data, subject, onManage = { manage = true }) { subject = it }
            FieldLabel("시간")
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ChoiceChip(Dates.minuteLabel(startMin), true) { picking = 1 }
                T("~", 16, color = pal.sub)
                ChoiceChip(Dates.minuteLabel(endMin), true) { picking = 2 }
            }
            T(Dates.duration(dur * 60_000L), 15, Modifier.padding(top = 10.dp), weight = FontWeight.Bold, color = pal.blue)
            VGap(24.dp)
            PrimaryButton("기록하기", Modifier.fillMaxWidth()) {
                Store.addSession(Session(Store.newId(), null, dayStartMs(day) + startMin * 60_000L, dur * 60_000L, subject))
                close()
            }
        }
    }
    if (manage) SubjectsSheet(data) { manage = false }
    if (picking != 0) {
        val cur = if (picking == 1) startMin else endMin
        val st = rememberTimePickerState(initialHour = cur / 60, initialMinute = cur % 60, is24Hour = false)
        AlertDialog(
            onDismissRequest = { picking = 0 },
            containerColor = pal.card,
            text = { TimePicker(state = st) },
            confirmButton = {
                TextButton(onClick = {
                    val m = st.hour * 60 + st.minute
                    if (picking == 1) { startMin = m; if (endMin == startMin) endMin = (m + 60) % 1440 } else endMin = m
                    picking = 0
                }) { T("확인", 16, weight = FontWeight.SemiBold, color = pal.blue) }
            },
            dismissButton = { TextButton(onClick = { picking = 0 }) { T("취소", 16, color = pal.sub) } },
        )
    }
}
