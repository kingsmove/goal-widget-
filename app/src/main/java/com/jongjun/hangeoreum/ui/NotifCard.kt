package com.jongjun.hangeoreum.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jongjun.hangeoreum.data.Dates
import com.jongjun.hangeoreum.data.NotifPrefs
import com.jongjun.hangeoreum.data.Store

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotifCard(n: NotifPrefs) {
    // 1 = morning, 2 = evening
    var editing by remember { mutableStateOf(0) }

    CardBox {
        SectionTitle("알림")
        ToggleRow("일정 알림", "시간이 정해진 일정 전에 알려줘요", n.events) { on -> Store.setNotif { it.copy(events = on) } }
        if (n.events) {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(bottom = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf(10 to "10분 전", 30 to "30분 전", 60 to "1시간 전", 1440 to "하루 전").forEach { (m, label) ->
                    ChoiceChip(label, n.eventLeadMin == m) { Store.setNotif { it.copy(eventLeadMin = m) } }
                }
            }
        }
        ToggleRow("시험·마감 D-day", "D-7, D-1, 당일 오전 9시에 알려줘요", n.ddays) { on -> Store.setNotif { it.copy(ddays = on) } }
        ToggleRow("아침 요약", "오늘 할 일과 일정을 정리해서 보내줘요", n.morning, time = n.morningMin, onTime = { editing = 1 }) { on ->
            Store.setNotif { it.copy(morning = on) }
        }
        ToggleRow("저녁 미완료 알림", "못 한 할 일이 있으면 알려주고, 내일로 넘길 수 있어요", n.evening, time = n.eveningMin, onTime = { editing = 2 }) { on ->
            Store.setNotif { it.copy(evening = on) }
        }
    }

    if (editing != 0) {
        val cur = if (editing == 1) n.morningMin else n.eveningMin
        val st = rememberTimePickerState(initialHour = cur / 60, initialMinute = cur % 60, is24Hour = false)
        AlertDialog(
            onDismissRequest = { editing = 0 },
            containerColor = pal.card,
            text = { TimePicker(state = st) },
            confirmButton = {
                TextButton(onClick = {
                    val m = st.hour * 60 + st.minute
                    val which = editing
                    Store.setNotif { if (which == 1) it.copy(morningMin = m) else it.copy(eveningMin = m) }
                    editing = 0
                }) { T("확인", 16, weight = FontWeight.SemiBold, color = pal.blue) }
            },
            dismissButton = {
                TextButton(onClick = { editing = 0 }) { T("취소", 16, weight = FontWeight.SemiBold, color = pal.sub) }
            },
        )
    }
}

@Composable
private fun ToggleRow(
    title: String,
    desc: String,
    on: Boolean,
    time: Int? = null,
    onTime: (() -> Unit)? = null,
    onChange: (Boolean) -> Unit,
) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            T(title, 16, weight = FontWeight.Medium)
            T(desc, 12, color = pal.sub)
        }
        if (on && time != null && onTime != null) {
            ChoiceChip(Dates.minuteLabel(time), false, onTime)
            Gap(8.dp)
        }
        Switch(
            checked = on, onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White, checkedTrackColor = pal.blue,
                uncheckedThumbColor = Color.White, uncheckedTrackColor = pal.line, uncheckedBorderColor = Color.Transparent,
            ),
        )
    }
}
