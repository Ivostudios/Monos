package com.example.myplayer

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun LineSlider(
    value: Float, onChange: (Float) -> Unit, modifier: Modifier = Modifier, onFinish: () -> Unit = {},
    color: Color = Color.White, track: Color = Color.White.copy(alpha = 0.25f),
    range: ClosedFloatingPointRange<Float> = 0f..1f, height: Dp = 4.dp
) {
    var w by remember { mutableFloatStateOf(1f) }
    var active by remember { mutableStateOf(false) }
    val h by animateDpAsState(if (active) height * 2 else height, label = "h")
    val span = range.endInclusive - range.start
    val frac = ((value - range.start) / span).coerceIn(0f, 1f)
    fun at(x: Float) = range.start + (x / w).coerceIn(0f, 1f) * span
    Box(
        modifier.fillMaxWidth().height(32.dp).onSizeChanged { w = it.width.toFloat().coerceAtLeast(1f) }
            .pointerInput(range) { detectTapGestures(onTap = { onChange(at(it.x)); onFinish() }) }
            .pointerInput(range) {
                detectHorizontalDragGestures(
                    onDragStart = { active = true; onChange(at(it.x)) },
                    onDragEnd = { active = false; onFinish() },
                    onDragCancel = { active = false; onFinish() }
                ) { change, _ -> change.consume(); onChange(at(change.position.x)) }
            },
        contentAlignment = Alignment.CenterStart
    ) {
        Box(Modifier.fillMaxWidth().height(h).clip(CircleShape).background(track))
        Box(Modifier.fillMaxWidth(frac).height(h).clip(CircleShape).background(color))
    }
}

@Composable
fun Toggle(on: Boolean, onChange: (Boolean) -> Unit) {
    val p = pal
    val x by animateDpAsState(if (on) 24.dp else 2.dp, label = "x")
    val c by animateColorAsState(if (on) p.accent else p.fg.copy(alpha = 0.2f), label = "c")
    Box(Modifier.size(54.dp, 32.dp).pressable(0.95f) { onChange(!on) }.clip(CircleShape).background(c)) {
        Box(Modifier.offset(x, 2.dp).size(28.dp).clip(CircleShape).background(if (on) p.onAccent else Color.White))
    }
}

@Composable
fun Segmented(items: List<String>, sel: Int, onSel: (Int) -> Unit) {
    val p = pal
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(p.card).padding(4.dp)) {
        items.forEachIndexed { i, t ->
            val on = i == sel
            val bg by animateColorAsState(if (on) p.accent else Color.Transparent, label = "seg")
            Box(
                Modifier.weight(1f).pressable(0.96f) { onSel(i) }.clip(RoundedCornerShape(14.dp)).background(bg).padding(vertical = 11.dp),
                contentAlignment = Alignment.Center
            ) { Txt(t, style = TS.label, color = if (on) p.onAccent else p.sub) }
        }
    }
}

fun Modifier.fadeEdges(): Modifier = this
    .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
    .drawWithContent {
        drawContent()
        drawRect(
            Brush.verticalGradient(0f to Color.Transparent, 0.12f to Color.Black, 0.88f to Color.Black, 1f to Color.Transparent),
            blendMode = BlendMode.DstIn
        )
    }
