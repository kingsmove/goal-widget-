package com.jongjun.hangeoreum.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jongjun.hangeoreum.data.AppData
import com.jongjun.hangeoreum.data.Dates
import com.jongjun.hangeoreum.data.Level
import com.jongjun.hangeoreum.data.Routine
import com.jongjun.hangeoreum.data.Store
import com.jongjun.hangeoreum.data.goal
import kotlinx.coroutines.launch

private val DOW = listOf("월", "화", "수", "목", "금", "토", "일")

fun daysLabel(days: Int): String = when (days and 0x7F) {
    0x7F -> "매일"
    0x1F -> "평일"
    0x60 -> "주말"
    0 -> "요일 없음"
    else -> DOW.indices.filter { days and (1 shl it) != 0 }.joinToString("·") { DOW[it] }
}

/** List of repeating to-dos, shown on the 일간 goals tab. */
@Composable
fun RoutineCard(data: AppData, onEdit: (Routine?) -> Unit) {
    CardBox {
        SectionTitle("반복 할 일", "+ 추가") { onEdit(null) }
        if (data.routines.isEmpty()) {
            T("매일·특정 요일마다 자동으로 오늘 할 일에 들어가요", 14, Modifier.padding(vertical = 6.dp), color = pal.sub)
        }
        data.routines.sortedBy { it.createdAt }.forEach { r ->
            Row(
                Modifier.fillMaxWidth().bounceClick { onEdit(r) }.padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    T(r.title, 16, weight = FontWeight.Medium, maxLines = 1)
                    val parent = data.goal(r.parentId)?.title
                    T(daysLabel(r.days) + (parent?.let { " · $it" } ?: ""), 13, color = pal.sub, maxLines = 1)
                }
                Pill("↻ ${daysLabel(r.days)}")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoutineEditorSheet(existing: Routine?, data: AppData, onDismiss: () -> Unit) {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    var title by remember { mutableStateOf(existing?.title ?: "") }
    var memo by remember { mutableStateOf(existing?.memo ?: "") }
    var days by remember { mutableIntStateOf(existing?.days ?: 0x7F) }
    var parentId by remember { mutableStateOf(existing?.parentId) }
    val today = Dates.today()
    val parents = data.goals.filter { it.level != Level.DAY && !it.done && it.endDay >= today }
        .sortedWith(compareBy<com.jongjun.hangeoreum.data.Goal>({ it.level.ordinal }, { it.endDay }))

    fun close() {
        scope.launch { state.hide() }.invokeOnCompletion { onDismiss() }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = state, containerColor = pal.card) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp)
                .navigationBarsPadding().imePadding().padding(bottom = 16.dp)
        ) {
            T(if (existing == null) "반복 할 일" else "반복 할 일 수정", 22, weight = FontWeight.Bold)
            FieldLabel("할 일")
            InputField(title, { title = it }, "예: 영단어 30개")

            FieldLabel("반복 요일")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ChoiceChip("매일", days == 0x7F) { days = 0x7F }
                ChoiceChip("평일", days == 0x1F) { days = 0x1F }
                ChoiceChip("주말", days == 0x60) { days = 0x60 }
            }
            Row(
                Modifier.fillMaxWidth().padding(top = 10.dp).horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                DOW.forEachIndexed { i, label ->
                    val bit = 1 shl i
                    ChoiceChip(label, days and bit != 0) { days = days xor bit }
                }
            }

            FieldLabel("상위 목표 (선택)")
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ChoiceChip("없음", parentId == null) { parentId = null }
                parents.forEach { g -> ChoiceChip(g.title, parentId == g.id) { parentId = g.id } }
            }

            FieldLabel("메모 (선택)")
            InputField(memo, { memo = it }, "예: 단어장 3장씩", size = 16, singleLine = false)

            VGap(28.dp)
            PrimaryButton(
                if (existing == null) "추가하기" else "저장하기", Modifier.fillMaxWidth(),
                enabled = title.isNotBlank() && days != 0,
            ) {
                Store.upsertRoutine(
                    Routine(
                        id = existing?.id ?: Store.newId(), title = title.trim(), days = days,
                        parentId = parentId, memo = memo.trim(),
                        createdAt = existing?.createdAt ?: System.currentTimeMillis(),
                        source = existing?.source ?: "",
                    )
                )
                Store.ensureRoutines()
                close()
            }
            if (existing != null) {
                VGap(8.dp)
                SecondaryButton("반복 그만하기", Modifier.fillMaxWidth(), textColor = pal.red) {
                    Store.deleteRoutine(existing.id)
                    close()
                }
            }
        }
    }
}
