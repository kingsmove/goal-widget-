package com.jongjun.hangeoreum.data

enum class Level(val label: String) {
    YEAR("연간"),
    MONTH("월간"),
    WEEK("주간"),
    DAY("일간");

    fun child(): Level? = entries.getOrNull(ordinal + 1)
}

data class Goal(
    val id: String,
    val title: String,
    val level: Level,
    val parentId: String?,
    val startDay: Long,
    val endDay: Long,
    val done: Boolean,
    val memo: String,
    val createdAt: Long,
    /** -1 = use the level color, otherwise index into the item palette. */
    val color: Int = -1,
    /** Exact ARGB color picked freely; overrides [color]. */
    val customColor: Int? = null,
)

/** What an event is, for the resume view. color = canvas-style default color. */
enum class Kind(val label: String, val section: String, val color: Long) {
    GENERAL("일반", "", 0xFF8B95A1),
    WORK("근무·인턴", "경력", 0xFFA882FF),
    EDU("학업", "학력·학업", 0xFFE0AC00),
    CERT("자격증", "자격증", 0xFF08B94E),
    LANG("어학", "어학", 0xFF00BFBC),
    ACTIVITY("대외활동", "대외활동", 0xFFEC7500),
    PROJECT("공모전·프로젝트", "공모전·프로젝트", 0xFFD4537E),
    MILESTONE("마일스톤", "", 0xFF5F5E5A),
}

data class Session(
    val id: String,
    val goalId: String?,
    val startMs: Long,
    val durationMs: Long,
)

/**
 * day..endDay (inclusive). minute = -1 means all-day.
 * color = palette index, onPlan = shown on the long-term plan diagram.
 */
data class Event(
    val id: String,
    val title: String,
    val day: Long,
    val endDay: Long = day,
    val minute: Int = -1,
    val memo: String = "",
    val color: Int = 0,
    val onPlan: Boolean = false,
    /** Exact ARGB color (e.g. from an Obsidian canvas); overrides [color]. */
    val customColor: Int? = null,
    /** "" = made in the app, "canvas" = imported from Obsidian. */
    val source: String = "",
    /** [Kind] ordinal; anything but GENERAL shows up on the resume view. */
    val type: Int = 0,
) {
    val kind: Kind get() = Kind.entries.getOrElse(type) { Kind.GENERAL }
    val isRange: Boolean get() = endDay > day
    fun covers(d: Long): Boolean = d in day..endDay
}

data class TimerState(
    val goalId: String? = null,
    val running: Boolean = false,
    val accumulatedMs: Long = 0L,
    val startedAtMs: Long = 0L,
    val sessionStartMs: Long = 0L,
) {
    val active: Boolean get() = running || accumulatedMs > 0L
    fun elapsed(now: Long): Long = accumulatedMs + if (running) (now - startedAtMs).coerceAtLeast(0L) else 0L
}

/** Colors are ARGB ints; null = app default. */
data class ThemePrefs(
    val mode: Int = 0, // 0 system, 1 light, 2 dark
    val accent: Int? = null,
    val bgLight: Int? = null,
    val cardLight: Int? = null,
    val textLight: Int? = null,
    val bgDark: Int? = null,
    val cardDark: Int? = null,
    val textDark: Int? = null,
    val itemColors: List<Int?> = List(12) { null },
    val yearColors: List<Int?> = List(5) { null },
    val levelColors: List<Int?> = List(4) { null },
)

data class AppData(
    val goals: List<Goal> = emptyList(),
    val sessions: List<Session> = emptyList(),
    val events: List<Event> = emptyList(),
    val timer: TimerState = TimerState(),
    val focusGuard: Boolean = true,
    val theme: ThemePrefs = ThemePrefs(),
)
