package com.jongjun.hangeoreum.data

import android.content.Context
import com.jongjun.hangeoreum.widget.GoalWidget
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

object Store {
    private lateinit var app: Context
    private val _data = MutableStateFlow(AppData())
    val data: StateFlow<AppData> = _data
    val current: AppData get() = _data.value

    fun init(context: Context) {
        app = context.applicationContext
        _data.value = runCatching { load() }.getOrDefault(AppData())
    }

    fun newId(): String = UUID.randomUUID().toString()

    @Synchronized
    fun update(block: (AppData) -> AppData) {
        val next = block(_data.value)
        _data.value = next
        runCatching { save(next) }
        runCatching { GoalWidget.refresh(app) }
    }

    // ---------- Goals ----------
    fun upsertGoal(goal: Goal) = update { d ->
        val exists = d.goals.any { it.id == goal.id }
        d.copy(goals = if (exists) d.goals.map { if (it.id == goal.id) goal else it } else d.goals + goal)
    }

    fun toggleGoal(id: String) = update { d ->
        d.copy(goals = d.goals.map { if (it.id == id) it.copy(done = !it.done) else it })
    }

    fun deleteGoal(id: String) = update { d ->
        val remove = d.descendantIds(id) + id
        d.copy(
            goals = d.goals.filterNot { it.id in remove },
            timer = if (d.timer.goalId in remove) d.timer.copy(goalId = null) else d.timer,
        )
    }

    // ---------- Events ----------
    fun upsertEvent(e: Event) = update { d ->
        val exists = d.events.any { it.id == e.id }
        d.copy(events = if (exists) d.events.map { if (it.id == e.id) e else it } else d.events + e)
    }

    fun deleteEvent(id: String) = update { d -> d.copy(events = d.events.filterNot { it.id == id }) }

    // ---------- Timer ----------
    fun setTimerGoal(goalId: String?) = update { d -> d.copy(timer = d.timer.copy(goalId = goalId)) }

    fun startTimer() = update { d ->
        val t = d.timer
        if (t.running) d else {
            val now = System.currentTimeMillis()
            d.copy(
                timer = t.copy(
                    running = true,
                    startedAtMs = now,
                    sessionStartMs = if (t.accumulatedMs == 0L) now else t.sessionStartMs,
                )
            )
        }
    }

    fun pauseTimer() = update { d ->
        val t = d.timer
        if (!t.running) d else {
            val now = System.currentTimeMillis()
            d.copy(timer = t.copy(running = false, accumulatedMs = t.elapsed(now), startedAtMs = 0L))
        }
    }

    /** Stops and saves a session (if longer than 10 seconds). */
    fun stopTimer() = update { d ->
        val t = d.timer
        val now = System.currentTimeMillis()
        val elapsed = t.elapsed(now)
        val sessions = if (elapsed >= 10_000L) {
            d.sessions + Session(newId(), t.goalId, t.sessionStartMs.takeIf { it > 0 } ?: (now - elapsed), elapsed)
        } else d.sessions
        d.copy(sessions = sessions, timer = TimerState(goalId = t.goalId))
    }

    fun deleteSession(id: String) = update { d -> d.copy(sessions = d.sessions.filterNot { it.id == id }) }

    fun setFocusGuard(on: Boolean) = update { d -> d.copy(focusGuard = on) }

    // ---------- Persistence ----------
    private val file: File get() = File(app.filesDir, "data.json")

    private fun save(d: AppData) {
        val root = JSONObject()
        root.put("v", 1)
        root.put("focusGuard", d.focusGuard)
        root.put("goals", JSONArray().apply {
            d.goals.forEach { g ->
                put(JSONObject().apply {
                    put("id", g.id); put("title", g.title); put("level", g.level.name)
                    put("parentId", g.parentId ?: JSONObject.NULL)
                    put("start", g.startDay); put("end", g.endDay); put("done", g.done)
                    put("memo", g.memo); put("createdAt", g.createdAt)
                })
            }
        })
        root.put("sessions", JSONArray().apply {
            d.sessions.forEach { s ->
                put(JSONObject().apply {
                    put("id", s.id); put("goalId", s.goalId ?: JSONObject.NULL)
                    put("start", s.startMs); put("dur", s.durationMs)
                })
            }
        })
        root.put("events", JSONArray().apply {
            d.events.forEach { e ->
                put(JSONObject().apply {
                    put("id", e.id); put("title", e.title); put("day", e.day); put("endDay", e.endDay)
                    put("minute", e.minute); put("memo", e.memo); put("color", e.color); put("plan", e.onPlan)
                })
            }
        })
        val t = d.timer
        root.put("timer", JSONObject().apply {
            put("goalId", t.goalId ?: JSONObject.NULL); put("running", t.running)
            put("acc", t.accumulatedMs); put("startedAt", t.startedAtMs); put("sessionStart", t.sessionStartMs)
        })
        val tmp = File(app.filesDir, "data.json.tmp")
        tmp.writeText(root.toString())
        if (!tmp.renameTo(file)) {
            file.writeText(root.toString())
            tmp.delete()
        }
    }

    private fun JSONObject.optStr(key: String): String? = if (isNull(key) || !has(key)) null else getString(key)

    private fun load(): AppData {
        if (!file.exists()) return AppData()
        val root = JSONObject(file.readText())
        val goals = root.optJSONArray("goals")?.let { arr ->
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                Goal(
                    id = o.getString("id"),
                    title = o.getString("title"),
                    level = runCatching { Level.valueOf(o.getString("level")) }.getOrDefault(Level.DAY),
                    parentId = o.optStr("parentId"),
                    startDay = o.getLong("start"),
                    endDay = o.getLong("end"),
                    done = o.optBoolean("done"),
                    memo = o.optString("memo", ""),
                    createdAt = o.optLong("createdAt"),
                )
            }
        } ?: emptyList()
        val sessions = root.optJSONArray("sessions")?.let { arr ->
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                Session(o.getString("id"), o.optStr("goalId"), o.getLong("start"), o.getLong("dur"))
            }
        } ?: emptyList()
        val events = root.optJSONArray("events")?.let { arr ->
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                val day = o.getLong("day")
                Event(
                    id = o.getString("id"),
                    title = o.getString("title"),
                    day = day,
                    endDay = maxOf(day, o.optLong("endDay", day)),
                    minute = o.optInt("minute", -1),
                    memo = o.optString("memo", ""),
                    color = o.optInt("color", 0),
                    onPlan = o.optBoolean("plan", false),
                )
            }
        } ?: emptyList()
        val t = root.optJSONObject("timer")
        val timer = if (t == null) TimerState() else TimerState(
            goalId = t.optStr("goalId"),
            running = t.optBoolean("running"),
            accumulatedMs = t.optLong("acc"),
            startedAtMs = t.optLong("startedAt"),
            sessionStartMs = t.optLong("sessionStart"),
        )
        return AppData(goals, sessions, events, timer, root.optBoolean("focusGuard", true))
    }
}
