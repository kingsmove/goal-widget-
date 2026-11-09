package com.jongjun.hangeoreum.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jongjun.hangeoreum.data.AppData
import com.jongjun.hangeoreum.data.Dates
import com.jongjun.hangeoreum.data.Event
import com.jongjun.hangeoreum.data.Goal
import com.jongjun.hangeoreum.data.Level
import com.jongjun.hangeoreum.data.Store
import com.jongjun.hangeoreum.data.goal
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GoalEditorSheet(req: GoalEditReq, data: AppData, onDismiss: () -> Unit) {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val existing = req.existing
    val parent = data.goal(req.parentId)
    val levels = if (parent != null) Level.entries.filter { it.ordinal > parent.level.ordinal } else Level.entries.toList()

    var title by remember { mutableStateOf(existing?.title ?: "") }
    var memo by remember { mutableStateOf(existing?.memo ?: "") }
    var level by remember { mutableStateOf(if (req.level in levels) req.level else levels.first()) }
    var anchor by remember {
        mutableStateOf(existing?.let { Dates.date(it.endDay) } ?: LocalDate.now())
    }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        if (existing == null) {
            delay(250)
            runCatching { focus.requestFocus() }
        }
    }

    fun close() {
        scope.launch { state.hide() }.invokeOnCompletion { onDismiss() }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = state, containerColor = pal.card) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp)
                .navigationBarsPadding().imePadding().padding(bottom = 16.dp)
        ) {
            T(if (existing == null) "새 목표" else "목표 수정", 22, weight = FontWeight.Bold)
            if (parent != null) T("↑ ${parent.title}", 14, Modifier.padding(top = 4.dp), color = pal.blue, weight = FontWeight.Medium)

            if (existing == null && levels.size > 1) {
                FieldLabel("종류")
                Segmented(levels.map { it.label }, levels.indexOf(level)) { level = levels[it] }
            }

            FieldLabel("목표")
            InputField(title, { title = it }, placeholder(level), focusRequester = focus)

            FieldLabel(
                when (level) {
                    Level.YEAR -> "연도"
                    Level.MONTH -> "몇 월까지"
                    Level.WEEK -> "주"
                    Level.DAY -> "날짜"
                }
            )
            Stepper(
                Dates.anchorLabel(level, anchor),
                onPrev = { anchor = Dates.stepAnchor(level, anchor, -1) },
                onNext = { anchor = Dates.stepAnchor(level, anchor, 1) },
            )
            if (level == Level.DAY) {
                Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val t = LocalDate.now()
                    ChoiceChip("오늘", anchor == t) { anchor = t }
                    ChoiceChip("내일", anchor == t.plusDays(1)) { anchor = t.plusDays(1) }
                }
            }

            FieldLabel("메모 (선택)")
            InputField(memo, { memo = it }, "예: 기출 3회차까지", size = 16, singleLine = false)

            VGap(28.dp)
            PrimaryButton(if (existing == null) "추가하기" else "저장하기", Modifier.fillMaxWidth(), enabled = title.isNotBlank()) {
                val (s, e) = Dates.range(level, anchor)
                val goal = if (existing != null) {
                    existing.copy(
                        title = title.trim(), memo = memo.trim(), level = level, endDay = e,
                        startDay = if (existing.startDay <= e && existing.level == level &&
                            (level == Level.YEAR || level == Level.MONTH)) existing.startDay else s,
                    )
                } else Goal(
                    id = Store.newId(), title = title.trim(), level = level, parentId = req.parentId,
                    startDay = s, endDay = e, done = false, memo = memo.trim(), createdAt = System.currentTimeMillis(),
                )
                Store.upsertGoal(goal)
                close()
            }
        }
    }
}

private fun placeholder(level: Level) = when (level) {
    Level.YEAR -> "예: 자격증 2개 취득"
    Level.MONTH -> "예: 자격증 실기 합격"
    Level.WEEK -> "예: 시공 파트 1회독"
    Level.DAY -> "예: 기출 2023년 1회 풀기"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventEditorSheet(event: Event?, onDismiss: () -> Unit) {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    var title by remember { mutableStateOf(event?.title ?: "") }
    var memo by remember { mutableStateOf(event?.memo ?: "") }
    var day by remember { mutableLongStateOf(event?.day ?: Dates.today()) }
    var minute by remember { mutableIntStateOf(event?.minute ?: -1) }
    var showDate by remember { mutableStateOf(false) }
    var showTime by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        if (event == null) {
            delay(250)
            runCatching { focus.requestFocus() }
        }
    }

    fun close() {
        scope.launch { state.hide() }.invokeOnCompletion { onDismiss() }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = state, containerColor = pal.card) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp)
                .navigationBarsPadding().imePadding().padding(bottom = 16.dp)
        ) {
            T(if (event == null) "새 일정" else "일정 수정", 22, weight = FontWeight.Bold)

            FieldLabel("무슨 일정인가요?")
            InputField(title, { title = it }, "예: 친구랑 저녁 약속", focusRequester = focus)

            FieldLabel("날짜")
            val today = Dates.today()
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ChoiceChip("오늘", day == today) { day = today }
                ChoiceChip("내일", day == today + 1) { day = today + 1 }
                ChoiceChip(if (day != today && day != today + 1) Dates.full(day) else "날짜 선택", day != today && day != today + 1) {
                    showDate = true
                }
            }

            FieldLabel("시간")
            Row(verticalAlignment = Alignment.CenterVertically) {
                Row(Modifier.weight(1f)) {
                    if (minute >= 0) ChoiceChip(Dates.minuteLabel(minute), true) { showTime = true }
                    else T("하루 종일", 16, weight = FontWeight.Medium, color = pal.sub)
                }
                T("시간 지정", 14, Modifier.padding(end = 10.dp), color = pal.sub)
                Switch(
                    checked = minute >= 0,
                    onCheckedChange = { on ->
                        if (on) { minute = 9 * 60; showTime = true } else minute = -1
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White, checkedTrackColor = pal.blue,
                        uncheckedThumbColor = Color.White, uncheckedTrackColor = pal.line, uncheckedBorderColor = Color.Transparent,
                    ),
                )
            }

            FieldLabel("메모 (선택)")
            InputField(memo, { memo = it }, "장소, 준비물 등", size = 16, singleLine = false)

            VGap(28.dp)
            PrimaryButton(if (event == null) "추가하기" else "저장하기", Modifier.fillMaxWidth(), enabled = title.isNotBlank()) {
                Store.upsertEvent(
                    Event(event?.id ?: Store.newId(), title.trim(), day, minute, memo.trim())
                )
                close()
            }
            if (event != null) {
                VGap(8.dp)
                SecondaryButton("일정 삭제", Modifier.fillMaxWidth(), textColor = pal.red) {
                    Store.deleteEvent(event.id)
                    close()
                }
            }
        }
    }

    if (showDate) {
        val dpState = rememberDatePickerState(
            initialSelectedDateMillis = LocalDate.ofEpochDay(day).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { showDate = false },
            confirmButton = {
                TextButton(onClick = {
                    dpState.selectedDateMillis?.let {
                        day = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toEpochDay()
                    }
                    showDate = false
                }) { T("확인", 16, weight = FontWeight.SemiBold, color = pal.blue) }
            },
            dismissButton = {
                TextButton(onClick = { showDate = false }) { T("취소", 16, weight = FontWeight.SemiBold, color = pal.sub) }
            },
        ) {
            DatePicker(state = dpState, showModeToggle = false)
        }
    }

    if (showTime) {
        val tpState = rememberTimePickerState(
            initialHour = if (minute >= 0) minute / 60 else 9,
            initialMinute = if (minute >= 0) minute % 60 else 0,
            is24Hour = false,
        )
        AlertDialog(
            onDismissRequest = { showTime = false },
            containerColor = pal.card,
            text = { TimePicker(state = tpState) },
            confirmButton = {
                TextButton(onClick = {
                    minute = tpState.hour * 60 + tpState.minute
                    showTime = false
                }) { T("확인", 16, weight = FontWeight.SemiBold, color = pal.blue) }
            },
            dismissButton = {
                TextButton(onClick = { showTime = false }) { T("취소", 16, weight = FontWeight.SemiBold, color = pal.sub) }
            },
        )
    }
}
