package com.example.myplayer

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val UnnaFont = FontFamily(
    Font(R.font.unna_regular, FontWeight.Normal),
    Font(R.font.unna_bold, FontWeight.Bold)
)
val UrbFont = FontFamily(
    Font(R.font.urbanist_regular, FontWeight.Normal),
    Font(R.font.urbanist_medium, FontWeight.Medium),
    Font(R.font.urbanist_bold, FontWeight.Bold),
    Font(R.font.urbanist_black, FontWeight.Black)
)

object TS {
    val h1 = TextStyle(fontFamily = UnnaFont, fontWeight = FontWeight.Bold, fontSize = 34.sp, lineHeight = 38.sp)
    val h2 = TextStyle(fontFamily = UnnaFont, fontWeight = FontWeight.Bold, fontSize = 26.sp, lineHeight = 30.sp)
    val h3 = TextStyle(fontFamily = UnnaFont, fontWeight = FontWeight.Bold, fontSize = 20.sp, lineHeight = 24.sp)
    val tile = TextStyle(fontFamily = UnnaFont, fontWeight = FontWeight.Bold, fontSize = 16.sp, lineHeight = 19.sp)
    val title = TextStyle(fontFamily = UrbFont, fontWeight = FontWeight.Bold, fontSize = 15.sp, lineHeight = 19.sp)
    val body = TextStyle(fontFamily = UrbFont, fontWeight = FontWeight.Medium, fontSize = 15.sp, lineHeight = 20.sp)
    val small = TextStyle(fontFamily = UrbFont, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp)
    val label = TextStyle(fontFamily = UrbFont, fontWeight = FontWeight.Bold, fontSize = 14.sp, lineHeight = 18.sp)
    val num = TextStyle(fontFamily = UrbFont, fontWeight = FontWeight.Bold, fontSize = 22.sp, lineHeight = 26.sp)
    val big = TextStyle(fontFamily = UrbFont, fontWeight = FontWeight.Black, fontSize = 26.sp, lineHeight = 30.sp)
    val lyric = TextStyle(fontFamily = UrbFont, fontWeight = FontWeight.Black, fontSize = 30.sp, lineHeight = 36.sp)
    val lyricPlain = TextStyle(fontFamily = UrbFont, fontWeight = FontWeight.Bold, fontSize = 20.sp, lineHeight = 28.sp)
}

class Pal(
    val dark: Boolean, val bg: Color, val fg: Color, val surface: Color,
    val accent: Color, val accentText: Color, val onAccent: Color
) {
    val sub: Color get() = fg.copy(alpha = 0.55f)
    val card: Color get() = fg.copy(alpha = 0.065f)
    val line: Color get() = fg.copy(alpha = 0.12f)
}

val LocalPal = staticCompositionLocalOf {
    Pal(false, Color(0xFFF8F8F8), Color(0xFF040404), Color.White, Color(0xFF040404), Color(0xFF040404), Color(0xFFF8F8F8))
}
val pal: Pal @Composable get() = LocalPal.current
val LocalCrop = compositionLocalOf { true }

/** accent == Color.Unspecified significa "monocromatico" (nero in chiaro, bianco in scuro). */
@Composable
fun AppTheme(dark: Boolean, accent: Color, amoled: Boolean, crop: Boolean, content: @Composable () -> Unit) {
    val p = remember(dark, accent, amoled) {
        val bg = if (dark) (if (amoled) Color.Black else Color(0xFF040404)) else Color(0xFFF8F8F8)
        val fg = if (dark) Color(0xFFF8F8F8) else Color(0xFF040404)
        val mono = accent == Color.Unspecified
        Pal(
            dark, bg, fg,
            if (dark) Color(0xFF161616) else Color.White,
            if (mono) fg else accent,
            if (mono) fg else if (dark) lerp(accent, Color.White, 0.45f) else accent,
            if (mono) bg else Color.White
        )
    }
    CompositionLocalProvider(LocalPal provides p, LocalCrop provides crop, content = content)
}

@Composable
fun Txt(
    text: String, modifier: Modifier = Modifier, style: TextStyle = TS.body, color: Color = pal.fg,
    maxLines: Int = Int.MAX_VALUE, align: TextAlign = TextAlign.Unspecified
) {
    BasicText(text, modifier, style.copy(color = color, textAlign = align), overflow = TextOverflow.Ellipsis, maxLines = maxLines)
}

@Composable
fun Ico(v: ImageVector, modifier: Modifier = Modifier, tint: Color = pal.fg, size: Dp = 24.dp) {
    Image(rememberVectorPainter(v), null, modifier.size(size), colorFilter = ColorFilter.tint(tint))
}

fun Modifier.pressable(scale: Float = 0.92f, onClick: () -> Unit): Modifier = composed {
    var down by remember { mutableStateOf(false) }
    val s by animateFloatAsState(if (down) scale else 1f, spring(stiffness = Spring.StiffnessMedium), label = "press")
    this.graphicsLayer { scaleX = s; scaleY = s }.pointerInput(Unit) {
        detectTapGestures(onPress = { down = true; tryAwaitRelease(); down = false }, onTap = { onClick() })
    }
}

@Composable
fun IconBtn(v: ImageVector, onClick: () -> Unit, modifier: Modifier = Modifier, tint: Color = pal.fg, size: Dp = 24.dp, box: Dp = 44.dp) {
    Box(modifier.size(box).pressable(onClick = onClick), contentAlignment = Alignment.Center) { Ico(v, tint = tint, size = size) }
}

@Composable
fun PillBtn(text: String, icon: ImageVector?, onClick: () -> Unit, modifier: Modifier = Modifier, filled: Boolean = true) {
    val p = pal
    val fg = if (filled) p.onAccent else p.fg
    Row(
        modifier.pressable(onClick = onClick).clip(CircleShape).background(if (filled) p.accent else p.card)
            .padding(horizontal = 22.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Txt(text, style = TS.title, color = fg)
        if (icon != null) Ico(icon, tint = fg, size = 20.dp)
    }
}

fun Modifier.pressableLong(scale: Float = 0.94f, onLong: () -> Unit, onClick: () -> Unit): Modifier = composed {
    var down by remember { mutableStateOf(false) }
    val s by animateFloatAsState(if (down) scale else 1f, spring(stiffness = Spring.StiffnessMedium), label = "pressL")
    this.graphicsLayer { scaleX = s; scaleY = s }.pointerInput(Unit) {
        detectTapGestures(onPress = { down = true; tryAwaitRelease(); down = false }, onLongPress = { onLong() }, onTap = { onClick() })
    }
}
