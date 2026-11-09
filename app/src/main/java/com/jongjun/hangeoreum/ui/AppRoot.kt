package com.jongjun.hangeoreum.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jongjun.hangeoreum.data.Dates
import com.jongjun.hangeoreum.data.Event
import com.jongjun.hangeoreum.data.Goal
import com.jongjun.hangeoreum.data.Level
import com.jongjun.hangeoreum.data.Store
import androidx.compose.ui.platform.LocalContext
import com.jongjun.hangeoreum.update.UpdateInfo
import com.jongjun.hangeoreum.update.Updater
import kotlinx.coroutines.delay

/** Request to open the goal editor. */
data class GoalEditReq(val existing: Goal?, val level: Level, val parentId: String?)

/** Request to open the event editor. */
data class EventEditReq(
    val existing: Event?,
    val day: Long = Dates.today(),
    val onPlan: Boolean = false,
    val kind: Int = 0,
)

const val SETTINGS = "__settings"
const val STATS = "__stats"

class Nav(
    val openGoal: (String) -> Unit,
    val editGoal: (GoalEditReq) -> Unit,
    val editEvent: (EventEditReq) -> Unit,
    val startTimerFor: (String?) -> Unit,
    val goTab: (Int) -> Unit,
    val openStats: () -> Unit = {},
)

@Composable
fun AppRoot(resumeTick: Int, openOverlaySettings: () -> Unit, tabRequest: Pair<Int, Long>? = null) {
    val data by Store.data.collectAsState()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var stack by remember { mutableStateOf(listOf<String>()) }
    var goalReq by remember { mutableStateOf<GoalEditReq?>(null) }
    var eventReq by remember { mutableStateOf<EventEditReq?>(null) }

    // Clock that ticks only while a timer is active (plus on every resume).
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(data.timer.running, resumeTick) {
        now = System.currentTimeMillis()
        while (data.timer.running) {
            delay(250)
            now = System.currentTimeMillis()
        }
    }

    val nav = remember {
        Nav(
            openGoal = { id -> stack = stack + id },
            editGoal = { goalReq = it },
            editEvent = { eventReq = it },
            startTimerFor = { id ->
                if (id != null) Store.setTimerGoal(id)
                stack = emptyList()
                tab = 2
            },
            goTab = { tab = it },
            openStats = { stack = stack + STATS },
        )
    }

    BackHandler(enabled = stack.isNotEmpty()) { stack = stack.dropLast(1) }

    LaunchedEffect(tabRequest) {
        tabRequest?.let { (t, _) -> tab = t; stack = emptyList() }
    }

    // check GitHub for a newer build once per launch
    val ctx = LocalContext.current
    var update by remember { mutableStateOf<UpdateInfo?>(null) }
    LaunchedEffect(Unit) { Updater.check(ctx).onSuccess { update = it } }

    Box(Modifier.fillMaxSize().background(pal.bg)) {
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f).statusBarsPadding()) {
                AnimatedContent(
                    targetState = tab,
                    transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(120)) },
                    label = "tab",
                ) { t ->
                    when (t) {
                        0 -> HomeScreen(data, now, nav, update) { stack = stack + SETTINGS }
                        1 -> GoalsScreen(data, now, nav)
                        2 -> TimerScreen(data, now, nav, resumeTick, openOverlaySettings)
                        3 -> CalendarScreen(data, now, nav)
                        else -> PlanScreen(data, nav)
                    }
                }
            }
            BottomBar(tab) { tab = it }
        }

        AnimatedVisibility(
            visible = stack.isNotEmpty(),
            enter = slideInHorizontally(tween(260)) { it } + fadeIn(),
            exit = slideOutHorizontally(tween(220)) { it } + fadeOut(),
        ) {
            val last = stack.lastOrNull()
            if (last != null) {
                Box(Modifier.fillMaxSize().background(pal.bg).statusBarsPadding().navigationBarsPadding()) {
                    if (last == STATS) {
                        StatsScreen(data, now) { stack = stack.dropLast(1) }
                    } else if (last == SETTINGS) {
                        SettingsScreen(data, onBack = { stack = stack.dropLast(1) }, update = update) { update = it }
                    } else {
                        GoalDetailScreen(
                            goalId = last,
                            data = data,
                            now = now,
                            nav = nav,
                            onBack = { stack = stack.dropLast(1) },
                            onDeleted = { stack = stack.dropLast(1) },
                        )
                    }
                }
            }
        }
    }

    goalReq?.let { req -> GoalEditorSheet(req, data) { goalReq = null } }
    eventReq?.let { req -> EventEditorSheet(req) { eventReq = null } }
}

@Composable
private fun BottomBar(tab: Int, onSelect: (Int) -> Unit) {
    val items = listOf<Pair<ImageVector, String>>(
        AppIcons.Home to "홈",
        AppIcons.Flag to "목표",
        AppIcons.Timer to "타이머",
        AppIcons.Calendar to "캘린더",
        AppIcons.Plan to "플랜",
    )
    Column(Modifier.fillMaxWidth().background(pal.card).navigationBarsPadding()) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(pal.line))
        Row(Modifier.fillMaxWidth().height(62.dp)) {
            items.forEachIndexed { i, (icon, label) ->
                val sel = i == tab
                val c = if (sel) pal.text else pal.tertiary
                Column(
                    Modifier.weight(1f).height(62.dp).plainClick { onSelect(i) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
                ) {
                    Icon(icon, null, tint = c, modifier = Modifier.size(24.dp))
                    T(label, 11, Modifier.padding(top = 3.dp), weight = if (sel) FontWeight.SemiBold else FontWeight.Medium, color = c)
                }
            }
        }
    }
}
