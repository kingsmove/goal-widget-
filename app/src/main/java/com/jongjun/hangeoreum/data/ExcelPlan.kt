package com.jongjun.hangeoreum.data

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/** What an uploaded Excel file will add. */
data class ExcelPlan(
    val events: List<Event>,
    val goals: List<Goal>,
    val routines: List<Routine>,
    val warnings: List<String>,
)

object ExcelPlanner {
    const val SOURCE = "excel"

    private val dateRe = Regex("""(\d{4})\D+(\d{1,2})\D+(\d{1,2})""")
    private val timeRe = Regex("""(\d{1,2})\s*[:시]\s*(\d{1,2})?""")

    fun date(c: XCell?): LocalDate? {
        if (c == null) return null
        c.num?.let { n -> if (n > 1000) return LocalDate.of(1899, 12, 30).plusDays(n.toLong()) }
        val m = dateRe.find(c.text) ?: return null
        return runCatching {
            LocalDate.of(m.groupValues[1].toInt(), m.groupValues[2].toInt(), m.groupValues[3].toInt())
        }.getOrNull()
    }

    /** minutes after midnight, or -1 */
    fun time(c: XCell?): Int {
        if (c == null || c.text.isBlank()) return -1
        c.num?.let { n ->
            val frac = n - kotlin.math.floor(n)
            if (frac > 0 || n == 0.0) return (frac * 1440).let { Math.round(it).toInt() }.coerceIn(0, 1439)
        }
        val t = c.text.trim()
        val m = timeRe.find(t) ?: return -1
        var h = m.groupValues[1].toInt()
        val min = m.groupValues[2].toIntOrNull() ?: 0
        if (t.contains("오후") && h < 12) h += 12
        if (t.contains("오전") && h == 12) h = 0
        return if (h in 0..23 && min in 0..59) h * 60 + min else -1
    }

    private fun yes(c: XCell?): Boolean? = when (c?.text?.trim()?.uppercase()) {
        "O", "Y", "YES", "TRUE", "1", "예", "ㅇ", "V", "✓", "✔" -> true
        "X", "N", "NO", "FALSE", "0", "아니오" -> false
        else -> null
    }

    private fun hex(c: XCell?): Int? {
        val h = c?.text?.trim()?.removePrefix("#") ?: return null
        if (h.length != 6) return null
        return h.toLongOrNull(16)?.let { (0xFF000000L or it).toInt() }
    }

    fun kind(text: String?): Kind {
        val t = text?.replace(" ", "") ?: return Kind.GENERAL
        if (t.isEmpty()) return Kind.GENERAL
        return Kind.entries.firstOrNull { k -> k.label.replace("·", "").let { t.replace("·", "").contains(it) || it.contains(t.replace("·", "")) } }
            ?: when {
                t.contains("인턴") || t.contains("근무") || t.contains("경력") -> Kind.WORK
                t.contains("자격") || t.contains("시험") -> Kind.CERT
                t.contains("어학") || t.contains("토익") -> Kind.LANG
                t.contains("공모") || t.contains("프로젝트") -> Kind.PROJECT
                else -> Kind.GENERAL
            }
    }

    fun level(text: String?): Level? = when (text?.trim()?.take(1)) {
        "계", "기", "마" -> Level.PLAN
        "연" -> Level.YEAR
        "월" -> Level.MONTH
        "주" -> Level.WEEK
        "일" -> Level.DAY
        else -> null
    }

    fun days(text: String?): Int {
        val t = text?.replace(" ", "") ?: return 0
        return when {
            t.contains("매일") -> 0x7F
            t.contains("평일") -> 0x1F
            t.contains("주말") -> 0x60
            else -> "월화수목금토일".mapIndexedNotNull { i, ch -> if (t.contains(ch)) 1 shl i else null }.fold(0) { a, b -> a or b }
        }
    }

    private fun stableId(prefix: String, key: String, used: MutableSet<String>): String {
        var id = prefix + Integer.toHexString(key.hashCode())
        var n = 2
        while (!used.add(id)) id = prefix + Integer.toHexString(key.hashCode()) + "-" + n++
        return id
    }

    /** Column lookup by header text ("제목*" matches "제목"). */
    private class Table(rows: List<List<XCell?>>) {
        val header = rows.firstOrNull()?.map { it?.text?.replace("*", "")?.trim() ?: "" } ?: emptyList()
        val body = rows.drop(1)
        fun col(name: String) = header.indexOfFirst { it.startsWith(name) }
        fun cell(row: List<XCell?>, name: String): XCell? = col(name).let { if (it < 0) null else row.getOrNull(it) }
        fun text(row: List<XCell?>, name: String): String = cell(row, name)?.text?.trim() ?: ""
    }

    private fun isExample(title: String) = title.startsWith("예)") || title.startsWith("예 )")

    fun plan(sheets: Map<String, List<List<XCell?>>>, existing: AppData, today: Long = Dates.today()): ExcelPlan {
        val warnings = mutableListOf<String>()
        val used = mutableSetOf<String>()
        val now = System.currentTimeMillis()

        // ---- 일정
        val events = mutableListOf<Event>()
        sheets.entries.firstOrNull { it.key.contains("일정") }?.value?.let { rows ->
            val t = Table(rows)
            t.body.forEachIndexed { i, row ->
                val title = t.text(row, "제목")
                if (title.isEmpty() || isExample(title)) return@forEachIndexed
                val start = date(t.cell(row, "시작"))
                if (start == null) { warnings += "일정 ${i + 2}행 '$title': 시작일을 못 읽었어요"; return@forEachIndexed }
                val end = date(t.cell(row, "종료"))?.takeIf { !it.isBefore(start) } ?: start
                val k = kind(t.text(row, "종류"))
                val minute = if (end == start) time(t.cell(row, "시간")) else -1
                events += Event(
                    id = stableId("xl-e-", "$title|$start", used),
                    title = title,
                    day = start.toEpochDay(),
                    endDay = end.toEpochDay(),
                    minute = minute,
                    memo = t.text(row, "메모"),
                    onPlan = yes(t.cell(row, "장기")) ?: (k != Kind.GENERAL),
                    customColor = hex(t.cell(row, "색")) ?: if (k != Kind.GENERAL) k.color.toInt() else null,
                    source = SOURCE,
                    type = k.ordinal,
                )
            }
        }

        // ---- 목표 (two passes so parents can be listed after children)
        data class GRow(val title: String, val level: Level, val end: LocalDate, val parent: String, val row: List<XCell?>, val line: Int)
        val gRows = mutableListOf<GRow>()
        var gTable: Table? = null
        sheets.entries.firstOrNull { it.key.contains("목표") }?.value?.let { rows ->
            val t = Table(rows)
            gTable = t
            t.body.forEachIndexed { i, row ->
                val title = t.text(row, "제목")
                if (title.isEmpty() || isExample(title)) return@forEachIndexed
                val lv = level(t.text(row, "레벨"))
                val end = date(t.cell(row, "기한"))
                if (lv == null) { warnings += "목표 ${i + 2}행 '$title': 레벨(계획/연간/월간/주간/일간)이 필요해요"; return@forEachIndexed }
                if (end == null) { warnings += "목표 ${i + 2}행 '$title': 기한을 못 읽었어요"; return@forEachIndexed }
                gRows += GRow(title, lv, end, t.text(row, "상위"), row, i + 2)
            }
        }
        val goalIds = gRows.associate { it.title to stableId("xl-g-", "${it.level}|${it.title}", used) }
        val existingById = existing.goals.associateBy { it.id }
        val goals = gRows.map { r ->
            val t = gTable!!
            val id = goalIds.getValue(r.title)
            val old = existingById[id]
            val endDay = r.end.toEpochDay()
            val startDay = when (r.level) {
                Level.DAY -> endDay
                Level.WEEK -> r.end.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).toEpochDay()
                else -> date(t.cell(r.row, "시작"))?.toEpochDay()?.takeIf { it <= endDay } ?: minOf(today, endDay)
            }
            val parentId = r.parent.takeIf { it.isNotEmpty() }?.let { p ->
                goalIds[p] ?: existing.goals.firstOrNull { it.title == p && it.source != SOURCE }?.id
                    ?: run { warnings += "목표 ${r.line}행 '${r.title}': 상위 목표 '$p'를 못 찾았어요"; null }
            }
            val target = t.cell(r.row, "목표 수치")?.text?.toDoubleOrNull()?.toInt() ?: 0
            val current = t.cell(r.row, "현재")?.text?.toDoubleOrNull()?.toInt() ?: (old?.current ?: 0)
            Goal(
                id = id, title = r.title, level = r.level, parentId = parentId,
                startDay = startDay, endDay = endDay,
                done = yes(t.cell(r.row, "완료")) ?: (old?.done ?: false),
                memo = t.text(r.row, "메모"), createdAt = old?.createdAt ?: (now + r.line),
                color = old?.color ?: -1, customColor = old?.customColor,
                target = target, current = current, unit = t.text(r.row, "단위"), source = SOURCE,
            )
        }

        // ---- 반복
        val routines = mutableListOf<Routine>()
        sheets.entries.firstOrNull { it.key.contains("반복") }?.value?.let { rows ->
            val t = Table(rows)
            t.body.forEachIndexed { i, row ->
                val title = t.text(row, "할 일").ifEmpty { t.text(row, "제목") }
                if (title.isEmpty() || isExample(title)) return@forEachIndexed
                val d = days(t.text(row, "요일"))
                if (d == 0) { warnings += "반복 ${i + 2}행 '$title': 요일을 못 읽었어요"; return@forEachIndexed }
                val p = t.text(row, "상위")
                val parentId = p.takeIf { it.isNotEmpty() }?.let { goalIds[it] ?: existing.goals.firstOrNull { g -> g.title == it }?.id }
                routines += Routine(
                    id = stableId("xl-r-", title, used), title = title, days = d, parentId = parentId,
                    memo = t.text(row, "메모"), createdAt = now + i, source = SOURCE,
                )
            }
        }
        return ExcelPlan(events, goals, routines, warnings)
    }
}
