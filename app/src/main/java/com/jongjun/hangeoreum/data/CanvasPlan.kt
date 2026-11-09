package com.jongjun.hangeoreum.data

import java.time.LocalDate
import java.time.YearMonth
import kotlin.math.floor

/** A node of an Obsidian .canvas file (only the fields we need). */
data class CNode(
    val id: String,
    val type: String,
    val text: String,
    val label: String,
    val x: Double,
    val y: Double,
    val w: Double,
    val h: Double,
    val color: String?,
)

data class CanvasResult(val events: List<Event>, val months: Int, val error: String? = null)

/**
 * Turns a timeline canvas into plan events.
 *
 * Expected layout (what the user draws in Obsidian):
 *  - a row of month boxes labelled "2026.06", "2026.07", … (the time axis)
 *  - optional groups labelled with a year ("2026") around them
 *  - blocks above the axis; their left/right edges decide the months they cover,
 *    and dates written inside ("09/01~12/20", "11/7", "2월 중순", "2027.03~2027.09")
 *    refine the exact days.
 */
object CanvasPlan {
    const val SOURCE = "canvas"

    private val monthRe = Regex("""^[\s*#]*(\d{4})\s*[.\-/]\s*(\d{1,2})[\s*]*$""")
    private val yearRe = Regex("""^\s*(\d{4})\s*$""")

    private val rYmRange = Regex("""(\d{4})\.(\d{1,2})\s*~\s*(\d{4})\.(\d{1,2})""")
    private val rMdRange = Regex("""(\d{1,2})/(\d{1,2})\s*~\s*(\d{1,2})/(\d{1,2})""")
    private val rMd = Regex("""(?<![\d.])(\d{1,2})/(\d{1,2})(?![\d/])""")
    private val rKorDay = Regex("""(\d{1,2})월\s*(\d{1,2})일""")
    private val rKorPart = Regex("""(\d{1,2})월\s*(초순|초|중순|중|하순|말)(\s*~)?""")

    fun convert(nodes: List<CNode>): CanvasResult {
        val months = nodes.mapNotNull { n ->
            if (n.type != "text") return@mapNotNull null
            val m = monthRe.find(n.text.trim()) ?: return@mapNotNull null
            val mo = m.groupValues[2].toInt()
            if (mo !in 1..12) return@mapNotNull null
            n to YearMonth.of(m.groupValues[1].toInt(), mo)
        }.sortedBy { it.first.x }
        if (months.size < 2) return CanvasResult(emptyList(), 0, "월 칸(예: 2026.06)을 찾지 못했어요")

        // axis: x of first month + average step between consecutive months
        val first = months.first()
        val steps = months.zipWithNext().mapNotNull { (a, b) ->
            val dm = monthsBetween(a.second, b.second)
            if (dm > 0) (b.first.x - a.first.x) / dm else null
        }
        val step = steps.sorted().let { it[it.size / 2] }
        val axisTop = months.minOf { it.first.y }
        val monthIds = months.map { it.first.id }.toSet()

        fun monthAt(x: Double): YearMonth {
            val idx = floor((x - first.first.x) / step).toLong()
            return first.second.plusMonths(idx)
        }

        val yearGroups = nodes.filter { it.type == "group" && yearRe.matches(it.label) }

        fun inScope(n: CNode): Boolean {
            if (n.y + n.h > axisTop + 2) return false
            if (yearGroups.isEmpty()) {
                return n.x + n.w > months.first().first.x - step && n.x < months.last().first.x + 2 * step &&
                    n.y > axisTop - 2000
            }
            val cx = n.x + n.w / 2
            val cy = n.y + n.h / 2
            return yearGroups.any { g -> cx >= g.x && cx <= g.x + g.w && cy >= g.y && cy <= g.y + g.h }
        }

        // titles of template blocks ("블록 샘플" group) -> use the filled-in value instead
        val sampleGroups = nodes.filter { it.type == "group" && it.label.contains("샘플") }
        val sampleTitles = nodes.filter { n ->
            n.type == "text" && sampleGroups.any { g -> n.x >= g.x && n.x <= g.x + g.w && n.y >= g.y && n.y <= g.y + g.h }
        }.map { splitText(it.text).first }.toSet()

        val out = nodes.filter { it.type == "text" && it.id !in monthIds && inScope(it) }.mapNotNull { n ->
            val (rawTitle, memo) = splitText(n.text)
            val title = if (rawTitle in sampleTitles) {
                memo.split(" · ").mapNotNull { part ->
                    part.substringAfter(':', "").trim().takeIf { part.contains(':') && it.isNotEmpty() }
                }.firstOrNull() ?: rawTitle
            } else rawTitle
            if (title.isBlank()) return@mapNotNull null
            val startYm = monthAt(n.x + step * 0.15)
            val endYm = maxOf(startYm, monthAt(n.x + n.w - step * 0.15))
            val range = parseDates("$title\n$memo", startYm, endYm)
                ?.let { fitToBox(it, startYm, endYm) }
                ?: (startYm.atDay(1) to endYm.atEndOfMonth())
            Event(
                id = "cv-" + n.id,
                title = title,
                day = range.first.toEpochDay(),
                endDay = maxOf(range.first, range.second).toEpochDay(),
                minute = -1,
                memo = memo,
                color = 0,
                onPlan = true,
                customColor = colorOf(n.color),
                source = SOURCE,
                type = kindOf(n.color).ordinal,
            )
        }
        return CanvasResult(out, months.size)
    }

    /**
     * If the written date is far from where the block sits (e.g. copied text left as "2월 중순"
     * on a block placed in April), trust the block's position and keep only the day.
     */
    private fun fitToBox(r: Pair<LocalDate, LocalDate>, startYm: YearMonth, endYm: YearMonth): Pair<LocalDate, LocalDate> {
        val s = YearMonth.from(r.first)
        if (!s.isBefore(startYm.minusMonths(1)) && !s.isAfter(endYm.plusMonths(1))) return r
        val shift = monthsBetween(s, startYm).toLong()
        return r.first.plusMonths(shift) to r.second.plusMonths(shift)
    }

    /** "**제목 | 줄바꿈**\n본문" -> (제목, 본문). */
    fun splitText(raw: String): Pair<String, String> {
        val t = raw.trim()
        val title: String
        val rest: String
        if (t.startsWith("**")) {
            val close = t.indexOf("**", 2)
            if (close > 0) {
                title = t.substring(2, close)
                rest = t.substring(close + 2)
            } else {
                title = t.lineSequence().first().removePrefix("**")
                rest = t.substringAfter('\n', "")
            }
        } else {
            title = t.lineSequence().first()
            rest = t.substringAfter('\n', "")
        }
        val cleanTitle = title.replace('\n', ' ').replace(Regex("^#+\\s*"), "").replace(Regex("\\s+"), " ").trim()
        val memo = rest.lines().map { it.trim().trim('*').trim() }.filter { it.isNotEmpty() && it != "-" }
            .joinToString(" · ")
        return cleanTitle to memo
    }

    fun parseDates(text: String, startYm: YearMonth, endYm: YearMonth): Pair<LocalDate, LocalDate>? {
        fun year(m: Int): Int {
            var y = startYm.year
            if (YearMonth.of(y, m).isBefore(startYm.minusMonths(2))) y++
            return y
        }
        fun safe(y: Int, m: Int, d: Int): LocalDate? = runCatching {
            val ym = YearMonth.of(y, m)
            ym.atDay(d.coerceIn(1, ym.lengthOfMonth()))
        }.getOrNull()

        rYmRange.find(text)?.let { r ->
            val (y1, m1, y2, m2) = r.destructured
            val a = runCatching { YearMonth.of(y1.toInt(), m1.toInt()) }.getOrNull()
            val b = runCatching { YearMonth.of(y2.toInt(), m2.toInt()) }.getOrNull()
            if (a != null && b != null) return a.atDay(1) to b.atEndOfMonth()
        }
        rMdRange.find(text)?.let { r ->
            val (m1, d1, m2, d2) = r.destructured
            val y1 = year(m1.toInt())
            val a = safe(y1, m1.toInt(), d1.toInt())
            var b = safe(y1, m2.toInt(), d2.toInt())
            if (a != null && b != null && b.isBefore(a)) b = b.plusYears(1)
            if (a != null && b != null) return a to b
        }
        rKorDay.find(text)?.let { r ->
            val (m, d) = r.destructured
            safe(year(m.toInt()), m.toInt(), d.toInt())?.let { return it to it }
        }
        rMd.find(text)?.let { r ->
            val (m, d) = r.destructured
            if (m.toInt() in 1..12) safe(year(m.toInt()), m.toInt(), d.toInt())?.let { return it to it }
        }
        rKorPart.find(text)?.let { r ->
            val m = r.groupValues[1].toInt()
            if (m in 1..12) {
                val day = when (r.groupValues[2]) {
                    "초", "초순" -> 5
                    "중", "중순" -> 15
                    else -> 25
                }
                val a = safe(year(m), m, day) ?: return null
                val open = r.groupValues[3].isNotBlank()
                return if (open) a to maxOf(a, endYm.atEndOfMonth()) else a to a
            }
        }
        return null
    }

    /** Matches the color legend of the user's canvas template. */
    fun kindOf(c: String?): Kind = when (c?.trim()?.lowercase()) {
        "4" -> Kind.CERT
        "5" -> Kind.LANG
        "6" -> Kind.WORK
        "2" -> Kind.ACTIVITY
        "3" -> Kind.EDU
        "#d4537e" -> Kind.PROJECT
        "#5f5e5a" -> Kind.MILESTONE
        else -> Kind.GENERAL
    }

    /** Obsidian canvas colors: "1".."6" presets or "#rrggbb". */
    fun colorOf(c: String?): Int? {
        val hex = when (c?.trim()) {
            null, "" -> "#8B95A1"
            "1" -> "#E93147"
            "2" -> "#EC7500"
            "3" -> "#E0AC00"
            "4" -> "#08B94E"
            "5" -> "#00BFBC"
            "6" -> "#A882FF"
            else -> c.trim()
        }
        val h = hex.removePrefix("#")
        if (h.length != 6) return null
        return h.toLongOrNull(16)?.let { (0xFF000000L or it).toInt() }
    }

    private fun monthsBetween(a: YearMonth, b: YearMonth): Int = (b.year - a.year) * 12 + (b.monthValue - a.monthValue)
}
