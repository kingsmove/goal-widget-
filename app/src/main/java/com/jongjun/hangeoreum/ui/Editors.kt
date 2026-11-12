package com.jongjun.hangeoreum.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.ui.draw.clip
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
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jongjun.hangeoreum.data.AppData
import com.jongjun.hangeoreum.data.Dates
import com.jongjun.hangeoreum.data.Event
import com.jongjun.hangeoreum.data.Goal
import com.jongjun.hangeoreum.data.Kind
import androidx.compose.foundation.horizontalScroll
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
    val levels = if (parent != null) Level.ordered.filter { it.rank > parent.level.rank } else Level.ordered

    var title by remember { mutableStateOf(existing?.title ?: "") }
    var memo by remember { mutableStateOf(existing?.memo ?: "") }
    var level by remember { mutableStateOf(if (req.level in levels) req.level else levels.first()) }
    var gColor by remember { mutableIntStateOf(existing?.color ?: -1) }
    var gCustom by remember { mutableStateOf(existing?.customColor) }
    var numeric by remember { mutableStateOf((existing?.target ?: 0) > 0) }
    var targetText by remember { mutableStateOf(existing?.target?.takeIf { it > 0 }?.toString() ?: "") }
    var unit by remember { mutableStateOf(existing?.unit ?: "회") }
    var anchor by remember {
        mutableStateOf(existing?.let { Dates.date(it.endDay) } ?: LocalDate.now())
    }
    // free dates for PLAN goals
    var planStart by remember { mutableLongStateOf(existing?.startDay ?: Dates.today()) }
    var planEnd by remember { mutableLongStateOf(existing?.endDay ?: (Dates.today() + 30)) }
    var pickPlan by remember { mutableIntStateOf(0) } // 1 start, 2 end
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
                    Level.PLAN -> "기간"
                }
            )
            if (level == Level.PLAN) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Column(Modifier.weight(1f)) {
                        T("시작", 12, color = pal.sub)
                        ChoiceChip(Dates.full(planStart), true) { pickPlan = 1 }
                    }
                    Column(Modifier.weight(1f)) {
                        T("마감", 12, color = pal.sub)
                        ChoiceChip(Dates.full(planEnd), true) { pickPlan = 2 }
                    }
                }
                T(
                    "${planEnd - planStart + 1}일 · 마감 ${Dates.dday(planEnd)}", 13,
                    Modifier.padding(top = 8.dp), weight = FontWeight.SemiBold, color = pal.blue,
                )
                Row(Modifier.padding(top = 10.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(7L to "1주", 14L to "2주", 30L to "한 달", 90L to "3개월").forEach { (n, label) ->
                        ChoiceChip("+$label", false) { planEnd = planStart + n - 1 }
                    }
                }
            } else Stepper(
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

            FieldLabel("색상")
            ColorChooser(gColor, gCustom, allowDefault = true,
                onIndex = { gColor = it; gCustom = null },
                onCustom = { gCustom = it })

            Row(Modifier.padding(top = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    T("숫자로 진행률 채우기", 15, weight = FontWeight.Medium)
                    T("예: 기출 10회 중 4회 → 40%", 13, color = pal.sub)
                }
                Switch(
                    checked = numeric, onCheckedChange = { numeric = it },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White, checkedTrackColor = pal.blue,
                        uncheckedThumbColor = Color.White, uncheckedTrackColor = pal.line, uncheckedBorderColor = Color.Transparent,
                    ),
                )
            }
            if (numeric) {
                Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    InputField(targetText, { v -> targetText = v.filter { it.isDigit() }.take(5) }, "목표 수치", Modifier.weight(1f), size = 18)
                    InputField(unit, { unit = it.take(6) }, "단위", Modifier.weight(1f), size = 18)
                }
            }

            FieldLabel("메모 (선택)")
            InputField(memo, { memo = it }, "예: 기출 3회차까지", size = 16, singleLine = false)

            VGap(28.dp)
            PrimaryButton(if (existing == null) "추가하기" else "저장하기", Modifier.fillMaxWidth(), enabled = title.isNotBlank() && (!numeric || (targetText.toIntOrNull() ?: 0) > 0)) {
                val (s, e) = if (level == Level.PLAN) planStart to maxOf(planStart, planEnd) else Dates.range(level, anchor)
                val goal = if (existing != null) {
                    existing.copy(
                        title = title.trim(), memo = memo.trim(), level = level, endDay = e, color = gColor, customColor = gCustom,
                        target = if (numeric) targetText.toIntOrNull() ?: 0 else 0, unit = if (numeric) unit.trim() else "",
                        startDay = if (level != Level.PLAN && existing.startDay <= e && existing.level == level &&
                            (level == Level.YEAR || level == Level.MONTH)) existing.startDay else s,
                    )
                } else Goal(
                    id = Store.newId(), title = title.trim(), level = level, parentId = req.parentId,
                    startDay = s, endDay = e, done = false, memo = memo.trim(), createdAt = System.currentTimeMillis(),
                    color = gColor,
                    customColor = gCustom,
                    target = if (numeric) targetText.toIntOrNull() ?: 0 else 0,
                    unit = if (numeric) unit.trim() else "",
                )
                Store.upsertGoal(goal)
                close()
            }
        }
    }

    if (pickPlan != 0) {
        val initial = if (pickPlan == 1) planStart else planEnd
        val dp = rememberDatePickerState(
            initialSelectedDateMillis = LocalDate.ofEpochDay(initial).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { pickPlan = 0 },
            confirmButton = {
                TextButton(onClick = {
                    dp.selectedDateMillis?.let {
                        val d = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toEpochDay()
                        if (pickPlan == 1) { planStart = d; if (planEnd < d) planEnd = d } else planEnd = maxOf(d, planStart)
                    }
                    pickPlan = 0
                }) { T("확인", 16, weight = FontWeight.SemiBold, color = pal.blue) }
            },
            dismissButton = { TextButton(onClick = { pickPlan = 0 }) { T("취소", 16, color = pal.sub) } },
        ) { DatePicker(state = dp, showModeToggle = false) }
    }
}

private fun placeholder(level: Level) = when (level) {
    Level.YEAR -> "예: 자격증 2개 취득"
    Level.MONTH -> "예: 자격증 실기 합격"
    Level.WEEK -> "예: 시공 파트 1회독"
    Level.DAY -> "예: 기출 2023년 1회 풀기"
    Level.PLAN -> "예: 포트폴리오 완성"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventEditorSheet(req: EventEditReq, onDismiss: () -> Unit) {
    val event = req.existing
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    var title by remember { mutableStateOf(event?.title ?: "") }
    var memo by remember { mutableStateOf(event?.memo ?: "") }
    var day by remember { mutableLongStateOf(event?.day ?: req.day) }
    var endDay by remember { mutableLongStateOf(event?.endDay ?: req.day) }
    var ranged by remember { mutableStateOf(event?.isRange ?: req.onPlan) }
    var minute by remember { mutableIntStateOf(event?.minute ?: -1) }
    var color by remember { mutableIntStateOf(event?.color ?: 0) }
    var custom by remember {
        mutableStateOf(event?.customColor ?: Kind.entries.getOrNull(req.kind)?.takeIf { it != Kind.GENERAL }?.color?.toInt())
    }
    var kind by remember { mutableIntStateOf(event?.type ?: req.kind) }
    var onPlan by remember { mutableStateOf(event?.onPlan ?: req.onPlan) }
    // which date is being picked: 0 = none, 1 = start, 2 = end
    var pickDate by remember { mutableIntStateOf(0) }
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

    val switchColors = SwitchDefaults.colors(
        checkedThumbColor = Color.White, checkedTrackColor = pal.blue,
        uncheckedThumbColor = Color.White, uncheckedTrackColor = pal.line, uncheckedBorderColor = Color.Transparent,
    )

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = state, containerColor = pal.card) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp)
                .navigationBarsPadding().imePadding().padding(bottom = 16.dp)
        ) {
            T(if (event == null) "새 일정" else "일정 수정", 22, weight = FontWeight.Bold)

            FieldLabel("무슨 일정인가요?")
            InputField(title, { title = it }, if (req.onPlan) "예: 졸업설계, 인턴, 여행" else "예: 친구랑 저녁 약속", focusRequester = focus)

            FieldLabel("종류 · 일반이 아니면 이력서에 들어가요")
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Kind.entries.forEach { k ->
                    ChoiceChip(k.label, kind == k.ordinal) {
                        kind = k.ordinal
                        custom = if (k == Kind.GENERAL) null else k.color.toInt()
                    }
                }
            }

            Row(Modifier.padding(top = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                T("여러 날 이어지는 일정", 15, Modifier.weight(1f), weight = FontWeight.Medium)
                Switch(checked = ranged, onCheckedChange = { on ->
                    ranged = on
                    if (on) { minute = -1; if (endDay <= day) endDay = day + 2 } else endDay = day
                }, colors = switchColors)
            }

            if (ranged) {
                FieldLabel("기간")
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ChoiceChip(fullDate(day), true) { pickDate = 1 }
                    T("~", 16, color = pal.sub)
                    ChoiceChip(fullDate(endDay), true) { pickDate = 2 }
                }
                T("${endDay - day + 1}일", 13, Modifier.padding(top = 8.dp), color = pal.sub)
            } else {
                FieldLabel("날짜")
                val today = Dates.today()
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ChoiceChip("오늘", day == today) { day = today; endDay = today }
                    ChoiceChip("내일", day == today + 1) { day = today + 1; endDay = today + 1 }
                    val other = day != today && day != today + 1
                    ChoiceChip(if (other) fullDate(day) else "날짜 선택", other) { pickDate = 1 }
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
                        onCheckedChange = { on -> if (on) { minute = 9 * 60; showTime = true } else minute = -1 },
                        colors = switchColors,
                    )
                }
            }

            FieldLabel("색상")
            ColorChooser(color, custom, allowDefault = false,
                onIndex = { color = it; custom = null },
                onCustom = { custom = it })

            Row(Modifier.padding(top = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    T("장기 플랜에 표시", 15, weight = FontWeight.Medium)
                    T("플랜 탭의 다이어그램에 나와요", 13, color = pal.sub)
                }
                Switch(checked = onPlan, onCheckedChange = { onPlan = it }, colors = switchColors)
            }

            FieldLabel("메모 (선택)")
            InputField(memo, { memo = it }, "장소, 준비물 등", size = 16, singleLine = false)

            VGap(28.dp)
            PrimaryButton(if (event == null) "추가하기" else "저장하기", Modifier.fillMaxWidth(), enabled = title.isNotBlank()) {
                val end = if (ranged) maxOf(day, endDay) else day
                Store.upsertEvent(
                    Event(
                        id = event?.id ?: Store.newId(),
                        title = title.trim(),
                        day = day,
                        endDay = end,
                        minute = if (end > day) -1 else minute,
                        memo = memo.trim(),
                        color = color,
                        onPlan = onPlan,
                        customColor = custom,
                        source = event?.source ?: "",
                        type = kind,
                    )
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

    if (pickDate != 0) {
        val initial = if (pickDate == 1) day else endDay
        val dpState = rememberDatePickerState(
            initialSelectedDateMillis = LocalDate.ofEpochDay(initial).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { pickDate = 0 },
            confirmButton = {
                TextButton(onClick = {
                    dpState.selectedDateMillis?.let {
                        val picked = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toEpochDay()
                        if (pickDate == 1) {
                            val len = endDay - day
                            day = picked
                            endDay = if (ranged) picked + maxOf(len, 0L) else picked
                        } else {
                            endDay = maxOf(picked, day)
                        }
                    }
                    pickDate = 0
                }) { T("확인", 16, weight = FontWeight.SemiBold, color = pal.blue) }
            },
            dismissButton = {
                TextButton(onClick = { pickDate = 0 }) { T("취소", 16, weight = FontWeight.SemiBold, color = pal.sub) }
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

private fun fullDate(day: Long): String {
    val d = Dates.date(day)
    return if (d.year == LocalDate.now().year) Dates.full(day) else "${d.year}. ${Dates.full(day)}"
}

/** 12 palette swatches + any custom color, in a scrollable row. */
@Composable
fun ColorChooser(
    index: Int,
    custom: Int?,
    allowDefault: Boolean,
    onIndex: (Int) -> Unit,
    onCustom: (Int) -> Unit,
) {
    var picking by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (allowDefault) ChoiceChip("기본", index < 0 && custom == null) { onIndex(-1) }
        if (custom != null) {
            androidx.compose.foundation.layout.Box(
                Modifier.size(34.dp).clip(CircleShape).background(Color(custom)).bounceClick { picking = true },
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Filled.Check, null, tint = Color.White, modifier = Modifier.size(20.dp)) }
        }
        pal.itemColors.forEachIndexed { i, c ->
            androidx.compose.foundation.layout.Box(
                Modifier.size(34.dp).clip(CircleShape).background(c).bounceClick { onIndex(i) },
                contentAlignment = Alignment.Center,
            ) {
                if (i == index && custom == null) Icon(Icons.Filled.Check, null, tint = Color.White, modifier = Modifier.size(20.dp))
            }
        }
        androidx.compose.foundation.layout.Box(
            Modifier.size(34.dp).clip(CircleShape).background(pal.fill).bounceClick { picking = true },
            contentAlignment = Alignment.Center,
        ) { T("+", 18, weight = FontWeight.Bold, color = pal.sub) }
    }
    if (picking) {
        val start = custom?.let { Color(it) } ?: pal.itemColors.getOrElse(index) { pal.blue }
        ColorPickerSheet("직접 고르기", start, isDefault = true, onDismiss = { picking = false }) { picked ->
            if (picked != null) onCustom(picked.toArgb())
            picking = false
        }
    }
}
