package com.jongjun.hangeoreum.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jongjun.hangeoreum.data.AppData
import com.jongjun.hangeoreum.data.Dates
import com.jongjun.hangeoreum.data.Event
import com.jongjun.hangeoreum.data.Kind

private val ResumeKinds = listOf(Kind.WORK, Kind.EDU, Kind.CERT, Kind.LANG, Kind.ACTIVITY, Kind.PROJECT)

private fun ym(day: Long): String {
    val d = Dates.date(day)
    return "${d.year}.${"%02d".format(d.monthValue)}"
}

/** "2026.11.07" / "2026.09" / "2026.09 – 2026.12" */
fun resumePeriod(e: Event): String {
    val s = Dates.date(e.day)
    val en = Dates.date(e.endDay)
    return when {
        !e.isRange -> "${s.year}.${"%02d".format(s.monthValue)}.${"%02d".format(s.dayOfMonth)}"
        s.year == en.year && s.monthValue == en.monthValue -> ym(e.day)
        else -> "${ym(e.day)} – ${ym(e.endDay)}"
    }
}

@Composable
fun ResumeView(data: AppData, nav: Nav) {
    val today = Dates.today()
    var withPlanned by rememberSaveable { mutableStateOf(true) }
    var copied by remember { mutableStateOf(false) }
    val clipboard = LocalClipboardManager.current

    val sections = ResumeKinds.map { k ->
        k to data.events.filter { it.kind == k && (withPlanned || it.day <= today) }
            .sortedWith(compareByDescending<Event> { it.day }.thenByDescending { it.endDay })
    }
    val total = sections.sumOf { it.second.size }

    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ChoiceChip("예정 포함", withPlanned) { withPlanned = !withPlanned }
            T("", 1, Modifier.weight(1f))
            ChoiceChip(if (copied) "복사됨" else "텍스트 복사", false) {
                clipboard.setText(AnnotatedString(resumeText(sections, today)))
                copied = true
            }
        }

        if (total == 0) {
            CardBox {
                EmptyNote("일정을 추가할 때 종류를 고르면\n여기에 이력서처럼 모여요")
            }
        }

        sections.forEach { (k, list) ->
            CardBox {
                Row(Modifier.padding(bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(10.dp).clip(CircleShape).background(Color(k.color)))
                    Gap(8.dp)
                    T(k.section, 18, Modifier.weight(1f), weight = FontWeight.Bold)
                    T(
                        "+ 추가", 13,
                        Modifier.bounceClick { nav.editEvent(EventEditReq(null, today, onPlan = true, kind = k.ordinal)) },
                        weight = FontWeight.SemiBold, color = pal.blue,
                    )
                }
                if (list.isEmpty()) {
                    T("아직 없어요", 14, Modifier.padding(vertical = 8.dp), color = pal.tertiary)
                }
                list.forEachIndexed { i, e ->
                    if (i > 0) Box(Modifier.fillMaxWidth().padding(vertical = 2.dp).height(1.dp).background(pal.line))
                    ResumeRow(e, today) { nav.editEvent(EventEditReq(e)) }
                }
            }
        }
    }
}

@Composable
private fun ResumeRow(e: Event, today: Long, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().bounceClick(onClick = onClick).padding(vertical = 12.dp), verticalAlignment = Alignment.Top) {
        T(resumePeriod(e), 13, Modifier.width(112.dp).padding(top = 2.dp), weight = FontWeight.Medium, color = pal.sub, tabular = true)
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                T(e.title, 16, Modifier.weight(1f, fill = false), weight = FontWeight.SemiBold)
                when {
                    e.day > today -> { Gap(6.dp); Pill("예정") }
                    e.endDay >= today -> { Gap(6.dp); Pill("진행 중", color = pal.green, bg = pal.green.copy(alpha = 0.12f)) }
                }
            }
            if (e.memo.isNotBlank()) T(e.memo, 13, Modifier.padding(top = 3.dp), color = pal.sub)
        }
    }
}

private fun resumeText(sections: List<Pair<Kind, List<Event>>>, today: Long): String = buildString {
    sections.filter { it.second.isNotEmpty() }.forEach { (k, list) ->
        if (isNotEmpty()) append("\n")
        append("[").append(k.section).append("]\n")
        list.forEach { e ->
            append(resumePeriod(e)).append("  ").append(e.title)
            if (e.day > today) append(" (예정)")
            if (e.memo.isNotBlank()) append(" — ").append(e.memo)
            append("\n")
        }
    }
}
