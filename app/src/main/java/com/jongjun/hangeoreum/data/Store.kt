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
        runCatching { com.jongjun.hangeoreum.widget.WidgetKit.refreshAll(app) }
        runCatching { com.jongjun.hangeoreum.notify.Reminders.reschedule(app) }
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
            routineSkips = d.routineSkips + remove.filter { it.startsWith("rt-") },
            timer = if (d.timer.goalId in remove) d.timer.copy(goalId = null) else d.timer,
        )
    }

    fun setGoalCurrent(id: String, value: Int) = update { d ->
        d.copy(goals = d.goals.map {
            if (it.id == id) it.copy(current = value.coerceAtLeast(0), done = if (it.target > 0) value >= it.target else it.done) else it
        })
    }

    /** Moves DAY goals to another day (carry over / postpone). */
    fun moveGoals(ids: Collection<String>, day: Long) = update { d ->
        d.copy(goals = d.goals.map { if (it.id in ids) it.copy(startDay = day, endDay = day) else it })
    }

    fun addAll(goals: List<Goal> = emptyList(), events: List<Event> = emptyList()) = update { d ->
        d.copy(goals = d.goals + goals, events = d.events + events)
    }

    /** Replaces everything that came from Excel (and the older Obsidian import) with [p]. */
    fun applyExcel(p: ExcelPlan) = update { d ->
        d.copy(
            events = d.events.filterNot { it.source == ExcelPlanner.SOURCE || it.source == CanvasPlan.SOURCE } + p.events,
            goals = d.goals.filterNot { it.source == ExcelPlanner.SOURCE } + p.goals,
            routines = d.routines.filterNot { it.source == ExcelPlanner.SOURCE } + p.routines,
        )
    }

    // ---------- Routines ----------
    fun upsertRoutine(r: Routine) = update { d ->
        val exists = d.routines.any { it.id == r.id }
        d.copy(routines = if (exists) d.routines.map { if (it.id == r.id) r else it } else d.routines + r)
    }

    fun deleteRoutine(id: String) = update { d ->
        val today = Dates.today()
        // drop today's unfinished copy too; keep history
        d.copy(
            routines = d.routines.filterNot { it.id == id },
            goals = d.goals.filterNot { it.id == "rt-$id-$today" && !it.done },
        )
    }

    /** Creates today's to-dos for routines (idempotent). */
    fun ensureRoutines(today: Long = Dates.today()) {
        val d = _data.value
        val dow = Dates.date(today).dayOfWeek.value
        val missing = d.routines.filter { it.on(dow) }.map { r -> r to "rt-${r.id}-$today" }
            .filter { (_, gid) -> gid !in d.routineSkips && d.goals.none { it.id == gid } }
        if (missing.isEmpty()) return
        update { cur ->
            cur.copy(goals = cur.goals + missing.map { (r, gid) ->
                Goal(
                    id = gid, title = r.title, level = Level.DAY, parentId = r.parentId,
                    startDay = today, endDay = today, done = false, memo = r.memo,
                    createdAt = System.currentTimeMillis(),
                )
            })
        }
    }

    fun setNotif(block: (NotifPrefs) -> NotifPrefs) = update { d -> d.copy(notif = block(d.notif)) }

    // ---------- Events ----------
    fun upsertEvent(e: Event) = update { d ->
        val exists = d.events.any { it.id == e.id }
        d.copy(events = if (exists) d.events.map { if (it.id == e.id) e else it } else d.events + e)
    }

    /** Replaces every event that came from [source] with [list]. */
    fun replaceImported(source: String, list: List<Event>) = update { d ->
        d.copy(events = d.events.filterNot { it.source == source } + list)
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

    fun setTheme(block: (ThemePrefs) -> ThemePrefs) = update { d -> d.copy(theme = block(d.theme)) }

    // ---------- Persistence ----------
    private val file: File get() = File(app.filesDir, "data.json")

    private fun save(d: AppData) {
        val text = toJson(d)
        val tmp = File(app.filesDir, "data.json.tmp")
        tmp.writeText(text)
        if (!tmp.renameTo(file)) {
            file.writeText(text)
            tmp.delete()
        }
    }

    /** Full data as JSON (also the backup file format). */
    fun exportJson(): String = toJson(_data.value)

    /** Replaces everything with a backup. The current data is kept as data.before-restore.json. */
    fun importJson(text: String): Result<AppData> = runCatching {
        val d = fromJson(text)
        runCatching { file.copyTo(File(app.filesDir, "data.before-restore.json"), overwrite = true) }
        update { d }
        d
    }

    private fun toJson(d: AppData): String {
        val root = JSONObject()
        root.put("v", 1)
        root.put("focusGuard", d.focusGuard)
        root.put("goals", JSONArray().apply {
            d.goals.forEach { g ->
                put(JSONObject().apply {
                    put("id", g.id); put("title", g.title); put("level", g.level.name)
                    put("parentId", g.parentId ?: JSONObject.NULL)
                    put("start", g.startDay); put("end", g.endDay); put("done", g.done)
                    put("memo", g.memo); put("createdAt", g.createdAt); put("color", g.color)
                    put("cc", g.customColor ?: JSONObject.NULL)
                    put("target", g.target); put("current", g.current); put("unit", g.unit); put("src", g.source)
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
                    put("cc", e.customColor ?: JSONObject.NULL); put("src", e.source); put("type", e.type)
                })
            }
        })
        val t = d.timer
        root.put("timer", JSONObject().apply {
            put("goalId", t.goalId ?: JSONObject.NULL); put("running", t.running)
            put("acc", t.accumulatedMs); put("startedAt", t.startedAtMs); put("sessionStart", t.sessionStartMs)
        })
        val th = d.theme
        root.put("theme", JSONObject().apply {
            put("mode", th.mode)
            putC("accent", th.accent)
            putC("bgLight", th.bgLight); putC("cardLight", th.cardLight); putC("textLight", th.textLight)
            putC("bgDark", th.bgDark); putC("cardDark", th.cardDark); putC("textDark", th.textDark)
            put("items", JSONArray().apply { th.itemColors.forEach { put(it ?: JSONObject.NULL) } })
            put("years", JSONArray().apply { th.yearColors.forEach { put(it ?: JSONObject.NULL) } })
            put("levels", JSONArray().apply { th.levelColors.forEach { put(it ?: JSONObject.NULL) } })
        })
        root.put("routines", JSONArray().apply {
            d.routines.forEach { r ->
                put(JSONObject().apply {
                    put("id", r.id); put("title", r.title); put("days", r.days)
                    put("parentId", r.parentId ?: JSONObject.NULL); put("memo", r.memo)
                    put("createdAt", r.createdAt); put("src", r.source)
                })
            }
        })
        root.put("routineSkips", JSONArray().apply { d.routineSkips.forEach { put(it) } })
        val n = d.notif
        root.put("notif", JSONObject().apply {
            put("events", n.events); put("lead", n.eventLeadMin); put("ddays", n.ddays)
            put("morning", n.morning); put("morningMin", n.morningMin)
            put("evening", n.evening); put("eveningMin", n.eveningMin)
        })
        return root.toString()
    }

    private fun JSONObject.putC(key: String, v: Int?) {
        put(key, v ?: JSONObject.NULL)
    }

    private fun JSONObject.optC(key: String): Int? = if (!has(key) || isNull(key)) null else getInt(key)

    private fun JSONArray?.colors(n: Int): List<Int?> =
        List(n) { i -> if (this == null || i >= length() || isNull(i)) null else getInt(i) }

    private fun JSONObject.optStr(key: String): String? = if (isNull(key) || !has(key)) null else getString(key)

    private fun load(): AppData {
        if (!file.exists()) return AppData()
        val text = file.readText()
        // keep a copy of what was there when the app last started
        runCatching { File(app.filesDir, "data.prev.json").writeText(text) }
        return fromJson(text)
    }

    private fun fromJson(text: String): AppData {
        val root = JSONObject(text)
        if (!root.has("goals") && !root.has("events")) error("UFP 백업 파일이 아니에요")
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
                    color = o.optInt("color", -1),
                    customColor = o.optC("cc"),
                    target = o.optInt("target", 0),
                    current = o.optInt("current", 0),
                    unit = o.optString("unit", ""),
                    source = o.optString("src", ""),
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
                    customColor = o.optC("cc"),
                    source = o.optString("src", ""),
                    type = o.optInt("type", 0),
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
        val tj = root.optJSONObject("theme")
        val theme = if (tj == null) ThemePrefs() else ThemePrefs(
            mode = tj.optInt("mode", 0),
            accent = tj.optC("accent"),
            bgLight = tj.optC("bgLight"), cardLight = tj.optC("cardLight"), textLight = tj.optC("textLight"),
            bgDark = tj.optC("bgDark"), cardDark = tj.optC("cardDark"), textDark = tj.optC("textDark"),
            itemColors = tj.optJSONArray("items").colors(12),
            yearColors = tj.optJSONArray("years").colors(5),
            levelColors = tj.optJSONArray("levels").colors(4),
        )
        val routines = root.optJSONArray("routines")?.let { arr ->
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                Routine(
                    id = o.getString("id"), title = o.getString("title"), days = o.optInt("days", 0x7F),
                    parentId = o.optStr("parentId"), memo = o.optString("memo", ""),
                    createdAt = o.optLong("createdAt"), source = o.optString("src", ""),
                )
            }
        } ?: emptyList()
        val skips = root.optJSONArray("routineSkips")?.let { arr -> (0 until arr.length()).map { arr.getString(it) }.toSet() } ?: emptySet()
        val nj = root.optJSONObject("notif")
        val notif = if (nj == null) NotifPrefs() else NotifPrefs(
            events = nj.optBoolean("events", true), eventLeadMin = nj.optInt("lead", 30),
            ddays = nj.optBoolean("ddays", true),
            morning = nj.optBoolean("morning", true), morningMin = nj.optInt("morningMin", 480),
            evening = nj.optBoolean("evening", true), eveningMin = nj.optInt("eveningMin", 1260),
        )
        return AppData(
            goals, sessions, events, timer, root.optBoolean("focusGuard", true), theme,
            routines = routines, routineSkips = skips, notif = notif,
        )
    }
}
