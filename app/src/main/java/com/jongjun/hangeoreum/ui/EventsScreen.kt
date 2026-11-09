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
import com.jongjun.hangeoreum.data.Event

@Composable
fun EventsScreen(data: AppData, nav: Nav) {
    val today = Dates.today()
    var showPast by rememberSaveable { mutableStateOf(false) }
    val upcoming = data.events.filter { it.day >= today }
        .sortedWith(compareBy<Event> { it.day }.thenBy { it.minute })
        .groupBy { it.day }
    val past = data.events.filter { it.day < today }
        .sortedWith(compareByDescending<Event> { it.day }.thenByDescending { it.minute })
        .take(30)

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(Modifier.fillMaxWidth().padding(start = 4.dp, top = 8.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                T("일정", 26, Modifier.weight(1f), weight = FontWeight.Bold)
                IconCircle(Icons.Filled.Add, { nav.editEvent(null) }, tint = pal.blue, bg = pal.card)
            }
        }
        if (upcoming.isEmpty()) {
            item { CardBox { EmptyNote("약속이나 일정을 추가해보세요") } }
        }
        upcoming.forEach { (day, list) ->
            item(key = "d$day") {
                CardBox {
                    val rel = Dates.relative(day)
                    T(
                        if (rel == Dates.full(day)) rel else "$rel · ${Dates.full(day)}", 15,
                        Modifier.padding(bottom = 6.dp), weight = FontWeight.Bold,
                        color = if (day == today) pal.blue else pal.text,
                    )
                    list.forEach { e -> EventLine(e) { nav.editEvent(e) } }
                }
            }
        }
        if (past.isNotEmpty()) {
            item {
                T(
                    if (showPast) "지난 일정 접기" else "지난 일정 보기", 14,
                    Modifier.fillMaxWidth().plainClick { showPast = !showPast }.padding(vertical = 10.dp),
                    weight = FontWeight.SemiBold, color = pal.sub, align = TextAlign.Center,
                )
            }
            if (showPast) {
                item {
                    CardBox {
                        past.forEach { e -> EventRow(e) { nav.editEvent(e) } }
                    }
                }
            }
        }
    }
}

@Composable
private fun EventLine(e: Event, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().bounceClick(onClick = onClick).padding(vertical = 10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        T(
            if (e.minute < 0) "종일" else Dates.minuteLabel(e.minute), 14,
            Modifier.width(78.dp).padding(top = 1.dp), weight = FontWeight.SemiBold, color = pal.sub,
        )
        Column(Modifier.weight(1f)) {
            T(e.title, 16, weight = FontWeight.SemiBold)
            if (e.memo.isNotBlank()) T(e.memo, 13, Modifier.padding(top = 2.dp), color = pal.sub, maxLines = 2)
        }
    }
}
