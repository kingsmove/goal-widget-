package com.jongjun.hangeoreum.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jongjun.hangeoreum.data.Dates
import com.jongjun.hangeoreum.data.Event
import com.jongjun.hangeoreum.data.Goal
import com.jongjun.hangeoreum.data.Kind
import com.jongjun.hangeoreum.data.Level
import com.jongjun.hangeoreum.data.Store
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.temporal.TemporalAdjusters

/** Weeks from this week up to the day before the exam, with chapters spread across them. */
data class ExamWeek(val start: Long, val end: Long, val title: String)

fun planExamWeeks(today: Long, exam: Long, chapters: List<String>, review: Boolean): List<ExamWeek> {
    if (exam <= today) return emptyList()
    val weeks = mutableListOf<Pair<Long, Long>>()
    var mon = Dates.date(today).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).toEpochDay()
    while (mon < exam) {
        val s = maxOf(mon, today)
        val e = minOf(mon + 6, exam - 1)
        if (e >= s) weeks += s to e
        mon += 7
    }
    if (weeks.isEmpty()) return emptyList()
    val studyWeeks = if (review && weeks.size >= 2) weeks.size - 1 else weeks.size
    val out = mutableListOf<ExamWeek>()
    for (i in 0 until studyWeeks) {
        val from = chapters.size * i / studyWeeks
        val to = chapters.size * (i + 1) / studyWeeks
        val part = chapters.subList(from, to)
        val title = if (part.isEmpty()) "${i + 1}주차 학습" else "${i + 1}주차 · ${part.joinToString(", ")}"
        out += ExamWeek(weeks[i].first, weeks[i].second, title)
    }
    if (studyWeeks < weeks.size) out += ExamWeek(weeks.last().first, weeks.last().second, "${weeks.size}주차 · 총정리·기출")
    return out
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExamPlanSheet(nav: Nav, onDismiss: () -> Unit) {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val today = Dates.today()
    var name by remember { mutableStateOf("") }
    var exam by remember { mutableLongStateOf(today + 56) }
    var scopeText by remember { mutableStateOf("") }
    var review by remember { mutableStateOf(true) }
    var picking by remember { mutableStateOf(false) }
    val chapters = scopeText.lines().map { it.trim() }.filter { it.isNotEmpty() }
    val weeks = planExamWeeks(today, exam, chapters, review)

    fun close() {
        scope.launch { state.hide() }.invokeOnCompletion { onDismiss() }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = state, containerColor = pal.card) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp)
                .navigationBarsPadding().imePadding().padding(bottom = 16.dp)
        ) {
            T("시험 플랜 만들기", 22, weight = FontWeight.Bold)
            T("시험일까지 남은 기간을 주 단위로 나눠서 목표를 만들어줘요", 14, Modifier.padding(top = 4.dp), color = pal.sub)

            FieldLabel("시험 이름")
            InputField(name, { name = it }, "예: 도시계획기사 필기")

            FieldLabel("시험일")
            Row(verticalAlignment = Alignment.CenterVertically) {
                ChoiceChip(Dates.full(exam), true) { picking = true }
                Gap(10.dp)
                T(Dates.dday(exam), 15, weight = FontWeight.Bold, color = pal.blue)
            }

            FieldLabel("공부 범위 (한 줄에 하나씩, 선택)")
            InputField(scopeText, { scopeText = it }, "예:\n1과목 도시계획론\n2과목 도시설계\n3과목 도시개발론", size = 16, singleLine = false)

            Row(Modifier.padding(top = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                T("마지막 주는 총정리·기출", 15, Modifier.weight(1f), weight = FontWeight.Medium)
                Switch(
                    checked = review, onCheckedChange = { review = it },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White, checkedTrackColor = pal.blue,
                        uncheckedThumbColor = Color.White, uncheckedTrackColor = pal.line, uncheckedBorderColor = Color.Transparent,
                    ),
                )
            }

            FieldLabel("미리보기 · 주간 목표 ${weeks.size}개")
            if (weeks.isEmpty()) T("시험일을 오늘 이후로 정해주세요", 14, color = pal.red)
            weeks.forEach { w ->
                Row(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
                    T(Dates.span(w.start, w.end), 13, Modifier.padding(end = 10.dp), color = pal.sub, tabular = true)
                    T(w.title, 14, Modifier.weight(1f), weight = FontWeight.Medium)
                }
            }

            VGap(24.dp)
            PrimaryButton("플랜 만들기", Modifier.fillMaxWidth(), enabled = name.isNotBlank() && weeks.isNotEmpty()) {
                val now = System.currentTimeMillis()
                val main = Goal(
                    id = Store.newId(), title = "${name.trim()} 준비", level = Level.MONTH, parentId = null,
                    startDay = today, endDay = exam, done = false, memo = "", createdAt = now,
                    customColor = Kind.CERT.color.toInt(),
                )
                val weekGoals = weeks.mapIndexed { i, w ->
                    Goal(
                        id = Store.newId(), title = w.title, level = Level.WEEK, parentId = main.id,
                        startDay = w.start, endDay = w.end, done = false, memo = "", createdAt = now + i,
                    )
                }
                val ev = Event(
                    id = Store.newId(), title = name.trim(), day = exam, onPlan = true,
                    customColor = Kind.CERT.color.toInt(), type = Kind.CERT.ordinal,
                )
                Store.addAll(listOf(main) + weekGoals, listOf(ev))
                close()
                nav.openGoal(main.id)
            }
        }
    }

    if (picking) {
        val dp = rememberDatePickerState(
            initialSelectedDateMillis = LocalDate.ofEpochDay(exam).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { picking = false },
            confirmButton = {
                TextButton(onClick = {
                    dp.selectedDateMillis?.let { exam = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toEpochDay() }
                    picking = false
                }) { T("확인", 16, weight = FontWeight.SemiBold, color = pal.blue) }
            },
            dismissButton = { TextButton(onClick = { picking = false }) { T("취소", 16, color = pal.sub) } },
        ) { DatePicker(state = dp, showModeToggle = false) }
    }
}
