package com.jongjun.hangeoreum.data

fun AppData.goal(id: String?): Goal? = if (id == null) null else goals.firstOrNull { it.id == id }

fun AppData.children(id: String): List<Goal> =
    goals.filter { it.parentId == id }.sortedWith(compareBy<Goal> { it.endDay }.thenBy { it.createdAt })

fun AppData.descendantIds(id: String): Set<String> {
    val out = mutableSetOf<String>()
    val queue = ArrayDeque(listOf(id))
    while (queue.isNotEmpty()) {
        val cur = queue.removeFirst()
        goals.filter { it.parentId == cur }.forEach { if (out.add(it.id)) queue.add(it.id) }
    }
    return out
}

/** 0..1. Done = 1. Otherwise the average of children (leaf = 0). */
fun AppData.progress(g: Goal, depth: Int = 0): Float {
    if (g.done) return 1f
    if (g.numeric) return (g.current.toFloat() / g.target).coerceIn(0f, 1f)
    if (depth > 8) return 0f
    val kids = goals.filter { it.parentId == g.id }
    if (kids.isEmpty()) return 0f
    return kids.map { progress(it, depth + 1) }.average().toFloat()
}

fun pct(p: Float): Int = (p * 100).toInt().coerceIn(0, 100)

fun AppData.todayGoals(today: Long = Dates.today()): List<Goal> =
    goals.filter { it.level == Level.DAY && it.endDay == today }
        .sortedWith(compareBy<Goal> { it.done }.thenBy { it.createdAt })

fun AppData.todayProgress(today: Long = Dates.today()): Float {
    val list = todayGoals(today)
    if (list.isEmpty()) return 0f
    return list.map { progress(it) }.average().toFloat()
}

/** Goals of upper levels that are still running. */
fun AppData.activeGoals(today: Long = Dates.today()): List<Goal> =
    goals.filter { it.level != Level.DAY && !it.done && it.endDay >= today }
        .sortedWith(compareBy<Goal> { it.level.rank }.thenBy { it.endDay })

fun AppData.studyMsForGoal(id: String, now: Long): Long {
    val ids = descendantIds(id) + id
    var sum = sessions.filter { it.goalId in ids }.sumOf { it.durationMs }
    if (timer.active && timer.goalId in ids) sum += timer.elapsed(now)
    return sum
}

fun AppData.studyMsOnDay(day: Long, now: Long): Long {
    var sum = sessions.filter { Dates.dayOfMs(it.startMs) == day }.sumOf { it.durationMs }
    if (timer.active && day == Dates.today()) sum += timer.elapsed(now)
    return sum
}

fun AppData.upcomingEvents(today: Long = Dates.today()): List<Event> =
    events.filter { it.endDay >= today }.sortedWith(compareBy<Event> { it.day }.thenBy { it.minute })

fun AppData.eventsOn(day: Long): List<Event> =
    events.filter { it.covers(day) }.sortedWith(compareBy<Event> { !it.isRange }.thenBy { it.minute }.thenBy { it.day })

fun AppData.dayGoalsOn(day: Long): List<Goal> =
    goals.filter { it.level == Level.DAY && it.endDay == day }.sortedWith(compareBy<Goal> { it.done }.thenBy { it.createdAt })
