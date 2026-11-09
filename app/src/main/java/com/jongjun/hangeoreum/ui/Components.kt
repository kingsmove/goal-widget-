package com.jongjun.hangeoreum.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val pal: Palette @Composable get() = LocalPalette.current

@Composable
fun T(
    text: String,
    size: Int,
    modifier: Modifier = Modifier,
    weight: FontWeight = FontWeight.Normal,
    color: Color = pal.text,
    maxLines: Int = Int.MAX_VALUE,
    align: TextAlign? = null,
    tabular: Boolean = false,
) {
    Text(
        text = text,
        modifier = modifier,
        color = color,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        textAlign = align,
        style = TextStyle(
            fontFamily = Pretendard,
            fontSize = size.sp,
            fontWeight = weight,
            letterSpacing = if (size >= 20) (-0.5).sp else (-0.2).sp,
            lineHeight = (size * 1.4f).sp,
            fontFeatureSettings = if (tabular) "tnum" else null,
        ),
    )
}

fun Modifier.bounceClick(enabled: Boolean = true, onClick: () -> Unit): Modifier = composed {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.96f else 1f, tween(120), label = "press")
    this
        .graphicsLayer { scaleX = scale; scaleY = scale }
        .clickable(interactionSource = interaction, indication = null, enabled = enabled, onClick = onClick)
}

fun Modifier.plainClick(onClick: () -> Unit): Modifier = composed {
    clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
}

@Composable
fun CardBox(modifier: Modifier = Modifier, padding: Dp = 20.dp, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(pal.card)
            .padding(padding),
        content = content,
    )
}

@Composable
fun SectionTitle(title: String, action: String? = null, onAction: (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().padding(bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        T(title, 17, Modifier.weight(1f), weight = FontWeight.Bold)
        if (action != null && onAction != null) {
            Box(
                Modifier.clip(RoundedCornerShape(10.dp)).background(pal.blueSoft)
                    .bounceClick(onClick = onAction).padding(horizontal = 10.dp, vertical = 6.dp)
            ) { T(action, 13, weight = FontWeight.SemiBold, color = pal.blue) }
        }
    }
}

@Composable
fun PrimaryButton(
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    color: Color = pal.blue,
    onClick: () -> Unit,
) {
    val bg by animateColorAsState(if (enabled) color else pal.fill, label = "btn")
    Box(
        modifier.height(56.dp).clip(RoundedCornerShape(16.dp)).background(bg)
            .bounceClick(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { T(text, 17, weight = FontWeight.SemiBold, color = if (enabled) Color.White else pal.tertiary) }
}

@Composable
fun SecondaryButton(text: String, modifier: Modifier = Modifier, textColor: Color = pal.text, onClick: () -> Unit) {
    Box(
        modifier.height(56.dp).clip(RoundedCornerShape(16.dp)).background(pal.fill).bounceClick(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { T(text, 17, weight = FontWeight.SemiBold, color = textColor) }
}

@Composable
fun IconCircle(icon: ImageVector, onClick: () -> Unit, tint: Color = pal.text, bg: Color = pal.card, size: Dp = 40.dp) {
    Box(
        Modifier.size(size).clip(CircleShape).background(bg).bounceClick(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, null, tint = tint, modifier = Modifier.size(size * 0.55f)) }
}

@Composable
fun ProgressRing(
    progress: Float,
    modifier: Modifier = Modifier,
    stroke: Dp = 10.dp,
    color: Color = pal.blue,
    track: Color = pal.fill,
) {
    val p by animateFloatAsState(progress.coerceIn(0f, 1f), tween(600), label = "ring")
    Canvas(modifier) {
        val sw = stroke.toPx()
        val d = size.minDimension - sw
        val tl = Offset((size.width - d) / 2, (size.height - d) / 2)
        drawArc(track, -90f, 360f, false, tl, Size(d, d), style = Stroke(sw, cap = StrokeCap.Round))
        if (p > 0f) drawArc(color, -90f, 360f * p, false, tl, Size(d, d), style = Stroke(sw, cap = StrokeCap.Round))
    }
}

@Composable
fun ProgressLine(progress: Float, modifier: Modifier = Modifier, color: Color = pal.blue, height: Dp = 8.dp) {
    val p by animateFloatAsState(progress.coerceIn(0f, 1f), tween(600), label = "line")
    Box(modifier.fillMaxWidth().height(height).clip(RoundedCornerShape(height)).background(pal.fill)) {
        if (p > 0f) Box(Modifier.fillMaxWidth(p).fillMaxHeight().clip(RoundedCornerShape(height)).background(color))
    }
}

@Composable
fun CheckDot(checked: Boolean, onClick: () -> Unit, size: Dp = 26.dp) {
    val bg by animateColorAsState(if (checked) pal.blue else Color.Transparent, label = "chk")
    Box(
        Modifier.size(size).clip(CircleShape).background(bg)
            .then(if (checked) Modifier else Modifier.border(2.dp, pal.line, CircleShape))
            .bounceClick(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (checked) Icon(Icons.Filled.Check, null, tint = Color.White, modifier = Modifier.size(size * 0.62f))
    }
}

@Composable
fun Pill(text: String, color: Color = pal.blue, bg: Color = pal.blueSoft) {
    Box(Modifier.clip(RoundedCornerShape(8.dp)).background(bg).padding(horizontal = 8.dp, vertical = 3.dp)) {
        T(text, 12, weight = FontWeight.SemiBold, color = color)
    }
}

@Composable
fun Segmented(options: List<String>, selected: Int, modifier: Modifier = Modifier, onSelect: (Int) -> Unit) {
    Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(if (pal.dark) pal.card else Color(0xFFE5E8EB))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        options.forEachIndexed { i, label ->
            val sel = i == selected
            val bg by animateColorAsState(if (sel) (if (pal.dark) pal.fill else Color.White) else Color.Transparent, label = "seg")
            Box(
                Modifier.weight(1f).height(36.dp).clip(RoundedCornerShape(10.dp)).background(bg).plainClick { onSelect(i) },
                contentAlignment = Alignment.Center,
            ) { T(label, 14, weight = if (sel) FontWeight.Bold else FontWeight.Medium, color = if (sel) pal.text else pal.sub) }
        }
    }
}

@Composable
fun Stepper(label: String, onPrev: () -> Unit, onNext: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(pal.fill).padding(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconCircle(Icons.Filled.KeyboardArrowLeft, onPrev, bg = pal.card, size = 40.dp)
        T(label, 16, Modifier.weight(1f), weight = FontWeight.SemiBold, align = TextAlign.Center)
        IconCircle(Icons.Filled.KeyboardArrowRight, onNext, bg = pal.card, size = 40.dp)
    }
}

@Composable
fun ChoiceChip(text: String, selected: Boolean, onClick: () -> Unit) {
    val bg by animateColorAsState(if (selected) pal.blue else pal.fill, label = "chip")
    Box(
        Modifier.clip(RoundedCornerShape(12.dp)).background(bg).bounceClick(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp)
    ) { T(text, 14, weight = FontWeight.SemiBold, color = if (selected) Color.White else pal.text) }
}

@Composable
fun InputField(
    value: String,
    onChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    size: Int = 22,
    singleLine: Boolean = true,
    focusRequester: FocusRequester? = null,
) {
    var focused by remember { mutableStateOf(false) }
    val lineColor by animateColorAsState(if (focused) pal.blue else pal.line, label = "line")
    Column(modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
            if (value.isEmpty()) T(placeholder, size, weight = FontWeight.SemiBold, color = pal.tertiary)
            BasicTextField(
                value = value,
                onValueChange = onChange,
                singleLine = singleLine,
                textStyle = TextStyle(
                    fontFamily = Pretendard, fontSize = size.sp, fontWeight = FontWeight.SemiBold,
                    color = pal.text, letterSpacing = (-0.3).sp,
                ),
                cursorBrush = SolidColor(pal.blue),
                keyboardOptions = KeyboardOptions(imeAction = if (singleLine) ImeAction.Done else ImeAction.Default),
                modifier = Modifier.fillMaxWidth()
                    .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
                    .onFocusChanged { focused = it.isFocused },
            )
        }
        Box(Modifier.fillMaxWidth().height(2.dp).background(lineColor))
    }
}

@Composable
fun FieldLabel(text: String) {
    T(text, 14, Modifier.padding(top = 20.dp, bottom = 8.dp), weight = FontWeight.Medium, color = pal.sub)
}

@Composable
fun EmptyNote(text: String) {
    T(text, 15, Modifier.fillMaxWidth().padding(vertical = 18.dp), color = pal.tertiary, align = TextAlign.Center)
}

@Composable
fun RowScope.Gap(w: Dp) = Spacer(Modifier.width(w))

@Composable
fun VGap(h: Dp) = Spacer(Modifier.height(h))
