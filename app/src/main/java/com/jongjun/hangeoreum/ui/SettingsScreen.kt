package com.jongjun.hangeoreum.ui

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jongjun.hangeoreum.MainActivity
import com.jongjun.hangeoreum.data.AppData
import com.jongjun.hangeoreum.data.Level
import com.jongjun.hangeoreum.data.Store
import com.jongjun.hangeoreum.data.ThemePrefs
import com.jongjun.hangeoreum.update.UpdateInfo
import com.jongjun.hangeoreum.update.Updater
import kotlinx.coroutines.launch
import java.time.LocalDate

/** What the color picker is editing. */
private data class PickTarget(
    val title: String,
    val current: Color,
    val isDefault: Boolean,
    val setter: (ThemePrefs, Int?) -> ThemePrefs,
)

private val Presets = listOf(
    0xFF3182F6, 0xFF1B64DA, 0xFF00A6A6, 0xFF03B26C, 0xFF7AC74F, 0xFFFFC342,
    0xFFFF8A3D, 0xFFF04452, 0xFFF25C8A, 0xFF8B5CF6, 0xFF5B5BD6, 0xFF6B7684,
    0xFF191F28, 0xFF333D4B, 0xFFF2F4F6, 0xFFFFFFFF, 0xFFFFF8EC, 0xFFEEF6F0,
    0xFF17171C, 0xFF202027, 0xFF101F33, 0xFF1F1A2E, 0xFFF2F2F4, 0xFFE8F3FF,
).map { Color(it) }

@Composable
fun SettingsScreen(data: AppData, onBack: () -> Unit, update: UpdateInfo?, onUpdateChecked: (UpdateInfo?) -> Unit) {
    val t = data.theme
    val p = pal
    var target by remember { mutableStateOf<PickTarget?>(null) }
    val modeName = if (p.dark) "다크" else "라이트"
    val yearNow = LocalDate.now().year

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconCircle(Icons.AutoMirrored.Filled.ArrowBack, onBack, bg = p.bg)
            T("설정", 18, Modifier.padding(start = 6.dp), weight = FontWeight.Bold)
        }
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                CardBox {
                    SectionTitle("화면 모드")
                    Segmented(listOf("시스템", "라이트", "다크"), t.mode) { m -> Store.setTheme { it.copy(mode = m) } }
                }
            }

            item {
                CardBox {
                    SectionTitle("기본 색 · 지금 $modeName 모드")
                    ColorRow("포인트 색", p.blue, t.accent == null) {
                        target = PickTarget("포인트 색", p.blue, t.accent == null) { th, c -> th.copy(accent = c) }
                    }
                    val bgCur = if (p.dark) t.bgDark else t.bgLight
                    ColorRow("배경", p.bg, bgCur == null) {
                        target = PickTarget("배경 ($modeName)", p.bg, bgCur == null) { th, c ->
                            if (p.dark) th.copy(bgDark = c) else th.copy(bgLight = c)
                        }
                    }
                    val cardCur = if (p.dark) t.cardDark else t.cardLight
                    ColorRow("카드", p.card, cardCur == null) {
                        target = PickTarget("카드 ($modeName)", p.card, cardCur == null) { th, c ->
                            if (p.dark) th.copy(cardDark = c) else th.copy(cardLight = c)
                        }
                    }
                    val textCur = if (p.dark) t.textDark else t.textLight
                    ColorRow("글자", p.text, textCur == null) {
                        target = PickTarget("글자 ($modeName)", p.text, textCur == null) { th, c ->
                            if (p.dark) th.copy(textDark = c) else th.copy(textLight = c)
                        }
                    }
                    T("배경·카드·글자는 라이트/다크 모드별로 따로 저장돼요", 12, Modifier.padding(top = 8.dp), color = p.sub)
                }
            }

            item {
                CardBox {
                    SectionTitle("목표 레벨 색")
                    Level.entries.forEach { lv ->
                        val i = lv.ordinal
                        val isDef = t.levelColors.getOrNull(i) == null
                        ColorRow("${lv.label} 목표", p.levelColors[i], isDef) {
                            target = PickTarget("${lv.label} 목표 색", p.levelColors[i], isDef) { th, c ->
                                th.copy(levelColors = th.levelColors.toMutableList().also { l -> while (l.size < 4) l.add(null); l[i] = c })
                            }
                        }
                    }
                    T("목표마다 따로 색을 고르면 그 색이 우선이에요", 12, Modifier.padding(top = 8.dp), color = p.sub)
                }
            }

            item {
                CardBox {
                    SectionTitle("일정·목표 팔레트")
                    p.itemColors.chunked(6).forEachIndexed { row, colors ->
                        Row(Modifier.padding(bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            colors.forEachIndexed { j, c ->
                                val i = row * 6 + j
                                Swatch(c, 40) {
                                    val isDef = t.itemColors.getOrNull(i) == null
                                    target = PickTarget("팔레트 ${i + 1}번", c, isDef) { th, nc ->
                                        th.copy(itemColors = th.itemColors.toMutableList().also { l -> while (l.size < 12) l.add(null); l[i] = nc })
                                    }
                                }
                            }
                        }
                    }
                    T("일정·목표 만들 때 바로 고르는 12가지 색이에요. 바꾸면 그 색을 쓴 항목이 전부 바뀌어요. 만들 때 ‘+’로 아무 색이나 직접 고를 수도 있어요", 12, Modifier.padding(top = 2.dp), color = p.sub)
                }
            }

            item {
                CardBox {
                    SectionTitle("플랜 연도 색")
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        (0 until 5).forEach { k ->
                            val year = yearNow + k
                            val i = Math.floorMod(year, 5)
                            val c = p.yearColors[i]
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Swatch(c, 40) {
                                    target = PickTarget("${year}년 색", c, t.yearColors.getOrNull(i) == null) { th, nc ->
                                        th.copy(yearColors = th.yearColors.toMutableList().also { l -> while (l.size < 5) l.add(null); l[i] = nc })
                                    }
                                }
                                T("$year", 12, Modifier.padding(top = 4.dp), color = p.sub)
                            }
                        }
                    }
                }
            }

            item {
                SecondaryButton("모든 색 기본값으로", Modifier.fillMaxWidth(), textColor = p.red) {
                    Store.setTheme { ThemePrefs(mode = it.mode) }
                }
            }

            item { BackupCard() }

            item { CanvasImportCard(data) }

            item { UpdateCard(update, onUpdateChecked) }
        }
    }

    target?.let { tg ->
        ColorPickerSheet(tg.title, tg.current, tg.isDefault, onDismiss = { target = null }) { picked ->
            Store.setTheme { th -> tg.setter(th, picked?.toArgb()) }
            target = null
        }
    }
}

@Composable
private fun ColorRow(label: String, color: Color, isDefault: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().bounceClick(onClick = onClick).padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        T(label, 16, Modifier.weight(1f), weight = FontWeight.Medium)
        if (!isDefault) {
            T("변경됨", 12, Modifier.padding(end = 10.dp), weight = FontWeight.SemiBold, color = pal.blue)
        }
        Swatch(color, 30, onClick)
    }
}

@Composable
private fun Swatch(c: Color, size: Int, onClick: () -> Unit) {
    Box(
        Modifier.size(size.dp).clip(CircleShape).background(c)
            .border(1.dp, pal.line, CircleShape).bounceClick(onClick = onClick)
    )
}

@Composable
private fun SwatchLine(colors: List<Color>, onPick: (Int) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        colors.forEachIndexed { i, c -> Swatch(c, 40) { onPick(i) } }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ColorPickerSheet(
    title: String,
    initial: Color,
    isDefault: Boolean,
    onDismiss: () -> Unit,
    onPick: (Color?) -> Unit,
) {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val hsv0 = remember {
        FloatArray(3).also { android.graphics.Color.colorToHSV(initial.toArgb(), it) }
    }
    var h by remember { mutableFloatStateOf(hsv0[0]) }
    var s by remember { mutableFloatStateOf(hsv0[1]) }
    var v by remember { mutableFloatStateOf(hsv0[2]) }
    val current = Color(android.graphics.Color.HSVToColor(floatArrayOf(h, s, v)))

    fun setFrom(c: Color) {
        val a = FloatArray(3)
        android.graphics.Color.colorToHSV(c.toArgb(), a)
        h = a[0]; s = a[1]; v = a[2]
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = state, containerColor = pal.card) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp)
                .padding(bottom = 16.dp).navigationBarsPadding()
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    T(title, 22, weight = FontWeight.Bold)
                    T("#%06X".format(current.toArgb() and 0xFFFFFF), 14, Modifier.padding(top = 2.dp), color = pal.sub, tabular = true)
                }
                Box(Modifier.size(56.dp).clip(RoundedCornerShape(16.dp)).background(current).border(1.dp, pal.line, RoundedCornerShape(16.dp)))
            }

            FieldLabel("추천 색")
            Presets.chunked(6).forEach { row ->
                Row(Modifier.fillMaxWidth().padding(bottom = 10.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    row.forEach { c ->
                        val sel = c.toArgb() == current.toArgb()
                        Box(
                            Modifier.size(40.dp).clip(CircleShape).background(c).border(1.dp, pal.line, CircleShape)
                                .bounceClick { setFrom(c) },
                            contentAlignment = Alignment.Center,
                        ) {
                            if (sel) Icon(Icons.Filled.Check, null, tint = if (c.luminance() > 0.6f) Color.Black else Color.White, modifier = Modifier.size(20.dp))
                        }
                    }
                }
            }

            FieldLabel("직접 고르기")
            GradientSlider("색조", h, 0f..360f, Brush.horizontalGradient((0..6).map { Color.hsv(it * 60f % 360f, 1f, 1f) })) { h = it }
            GradientSlider(
                "선명도", s, 0f..1f,
                Brush.horizontalGradient(listOf(Color.hsv(h, 0f, v), Color.hsv(h, 1f, v))),
            ) { s = it }
            GradientSlider(
                "밝기", v, 0f..1f,
                Brush.horizontalGradient(listOf(Color.Black, Color.hsv(h, s, 1f))),
            ) { v = it }

            androidx.compose.foundation.layout.Spacer(Modifier.height(24.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SecondaryButton("기본값", Modifier.weight(1f), textColor = if (isDefault) pal.tertiary else pal.text) { onPick(null) }
                PrimaryButton("적용", Modifier.weight(2f)) { onPick(current) }
            }
        }
    }
}

@Composable
private fun GradientSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float>, brush: Brush, onChange: (Float) -> Unit) {
    T(label, 13, Modifier.padding(top = 8.dp), weight = FontWeight.Medium, color = pal.sub)
    Box(Modifier.fillMaxWidth().height(40.dp), contentAlignment = Alignment.Center) {
        Box(Modifier.fillMaxWidth().padding(horizontal = 10.dp).height(12.dp).clip(RoundedCornerShape(6.dp)).background(brush))
        Slider(
            value = value,
            onValueChange = onChange,
            valueRange = range,
            colors = SliderDefaults.colors(
                thumbColor = Color.White,
                activeTrackColor = Color.Transparent,
                inactiveTrackColor = Color.Transparent,
            ),
        )
    }
}

private fun Color.luminance(): Float = 0.299f * red + 0.587f * green + 0.114f * blue

@Composable
private fun UpdateCard(update: UpdateInfo?, onChecked: (UpdateInfo?) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }
    val activity = ctx as? MainActivity

    fun launch(intent: Intent) {
        if (activity != null) activity.launchExternal(intent) else ctx.startActivity(intent)
    }

    CardBox {
        SectionTitle("앱 업데이트")
        T("지금 버전  ${Updater.currentName(ctx)} (빌드 ${Updater.currentBuild(ctx)})", 14, color = pal.sub)
        if (update != null) {
            T("새 버전: ${update.name}", 15, Modifier.padding(top = 6.dp), weight = FontWeight.SemiBold, color = pal.blue)
        }
        status?.let { msg -> T(msg, 13, Modifier.padding(top = 6.dp), color = pal.sub) }
        if (busy && progress > 0f) ProgressLine(progress, Modifier.padding(top = 10.dp))
        VGap(14.dp)
        if (update == null) {
            SecondaryButton(if (busy) "확인 중…" else "업데이트 확인", Modifier.fillMaxWidth()) {
                if (busy) return@SecondaryButton
                busy = true; status = null
                scope.launch {
                    Updater.check(ctx)
                        .onSuccess { info ->
                            onChecked(info)
                            status = if (info == null) "최신 버전이에요" else null
                        }
                        .onFailure { status = "확인하지 못했어요. 인터넷 연결을 확인해주세요" }
                    busy = false
                }
            }
        } else {
            PrimaryButton(if (busy) "받는 중… ${(progress * 100).toInt()}%" else "다운로드하고 설치", Modifier.fillMaxWidth()) {
                if (busy) return@PrimaryButton
                if (!Updater.canInstall(ctx)) {
                    status = "‘이 출처의 앱 설치 허용’을 켜고 돌아와서 다시 눌러주세요"
                    launch(Updater.installPermissionIntent(ctx))
                    return@PrimaryButton
                }
                busy = true; progress = 0f; status = null
                scope.launch {
                    Updater.download(ctx, update) { progress = it }
                        .onSuccess { file -> launch(Updater.installIntent(ctx, file)) }
                        .onFailure { status = "다운로드에 실패했어요. 다시 시도해주세요" }
                    busy = false
                }
            }
        }
    }
}
