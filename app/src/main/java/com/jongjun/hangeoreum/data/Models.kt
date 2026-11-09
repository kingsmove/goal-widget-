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
)

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
) {
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

data class AppData(
    val goals: List<Goal> = emptyList(),
    val sessions: List<Session> = emptyList(),
    val events: List<Event> = emptyList(),
    val timer: TimerState = TimerState(),
    val focusGuard: Boolean = true,
)
