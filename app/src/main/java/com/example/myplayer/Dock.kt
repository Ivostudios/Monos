package com.example.myplayer

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

@Composable
fun MiniBar(ctl: Ctl, sheet: Animatable<Float, AnimationVector1D>, hPx: Float, pill: Boolean, tint: Color) {
    val s = ctl.currentSong ?: return
    val scope = rememberCoroutineScope()
    val W = Color.White
    val liked = s.id in ctl.likes
    val drag = rememberDraggableState { d -> scope.launch { sheet.snapTo((sheet.value + d / hPx).coerceIn(0f, 1f)) } }
    Row(
        Modifier.fillMaxWidth()
            .then(if (pill) Modifier.padding(horizontal = 12.dp, vertical = 6.dp).clip(RoundedCornerShape(28.dp)).background(tint) else Modifier)
            .draggable(drag, Orientation.Vertical, onDragStopped = { v ->
                sheet.animateTo(if (sheet.value < 0.75f || v < -1200f) 0f else 1f, spring(0.88f, Spring.StiffnessMediumLow))
            })
            .pointerInput(Unit) { detectTapGestures { scope.launch { sheet.animateTo(0f, spring(0.88f, Spring.StiffnessMediumLow)) } } }
            .padding(start = 16.dp, end = 8.dp, top = if (pill) 10.dp else 14.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AnimatedContent(s.art, transitionSpec = { fadeIn(tween(300)) togetherWith fadeOut(tween(300)) }, label = "mc") { a ->
            Cover(a, Modifier.size(40.dp))
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            AnimatedContent(
                s,
                transitionSpec = { (slideInVertically(tween(250)) { it / 2 } + fadeIn(tween(250))) togetherWith (slideOutVertically(tween(200)) { -it / 2 } + fadeOut(tween(150))) },
                label = "mt"
            ) { song ->
                Column {
                    Txt(song.title, style = TS.title.copy(fontSize = 14.sp), color = W, maxLines = 1)
                    Txt(song.artists.joinToString(" / "), style = TS.small, color = W.copy(alpha = 0.6f), maxLines = 1)
                }
            }
            Spacer(Modifier.height(6.dp))
            val f = if (s.duration > 0) (ctl.pos.toFloat() / s.duration).coerceIn(0f, 1f) else 0f
            Box(Modifier.fillMaxWidth().height(4.dp).clip(CircleShape).background(W.copy(alpha = 0.22f))) {
                Box(Modifier.fillMaxWidth(f).fillMaxHeight().background(W))
            }
        }
        Spacer(Modifier.width(4.dp))
        HeartBtn(liked, { ctl.toggleLike(s.id) }, W, W)
        Box(Modifier.size(44.dp).pressable { ctl.toggle() }, contentAlignment = Alignment.Center) { PlayIcon(ctl, 28.dp, W) }
    }
}

@Composable
fun NavBar(tabs: List<Int>, tab: Int, onDark: Boolean, onTab: (Int) -> Unit) {
    val p = pal
    val all = mapOf(
        0 to Pair("Home", Icons.Rounded.Home), 1 to Pair("Playlist", Icons.Rounded.FolderOpen),
        2 to Pair("Libreria", Icons.Rounded.MusicNote), 3 to Pair("Dashboard", Icons.Rounded.GridView)
    )
    val on = if (onDark) Color.White else p.fg
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(74.dp)) {
        tabs.forEach { i ->
            val (label, icon) = all.getValue(i)
            val sel = tab == i
            val c by animateColorAsState(if (sel) on else on.copy(alpha = 0.38f), label = "nc")
            val sc by animateFloatAsState(if (sel) 1.1f else 1f, spring(0.6f), label = "ns")
            Column(
                Modifier.weight(1f).fillMaxHeight().pressable(0.94f) { onTab(i) },
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center
            ) {
                Ico(icon, Modifier.graphicsLayer { scaleX = sc; scaleY = sc }, c, 26.dp)
                Spacer(Modifier.height(4.dp))
                Txt(label, style = TS.small.copy(fontWeight = if (sel) FontWeight.Bold else FontWeight.Medium), color = c, maxLines = 1)
            }
        }
    }
}

@Composable
fun HeartBtn(liked: Boolean, onClick: () -> Unit, tint: Color, likedTint: Color, box: Dp = 44.dp, size: Dp = 24.dp) {
    val sc = remember { Animatable(1f) }
    var first by remember { mutableStateOf(true) }
    LaunchedEffect(liked) {
        if (first) first = false else { sc.snapTo(0.65f); sc.animateTo(1f, spring(0.35f, Spring.StiffnessMedium)) }
    }
    Box(Modifier.size(box).pressable(onClick = onClick), contentAlignment = Alignment.Center) {
        Ico(
            if (liked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
            Modifier.graphicsLayer { scaleX = sc.value; scaleY = sc.value }, if (liked) likedTint else tint, size
        )
    }
}

@Composable
private fun SideItem(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, sel: Boolean, onClick: () -> Unit) {
    val c by animateColorAsState(if (sel) Color.White else Color.White.copy(alpha = 0.45f), label = "sc")
    val bg by animateColorAsState(if (sel) Color.White.copy(alpha = 0.10f) else Color.Transparent, label = "sb")
    Row(
        Modifier.fillMaxWidth().padding(vertical = 2.dp).pressable(0.97f, onClick).clip(RoundedCornerShape(14.dp)).background(bg)
            .padding(horizontal = 14.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Ico(icon, tint = c, size = 22.dp)
        Spacer(Modifier.width(14.dp))
        Txt(label, style = TS.label, color = c, maxLines = 1)
    }
}

@Composable
fun Sidebar(sel: Int, hiddenMask: Int, onItem: (Int) -> Unit, onHistory: () -> Unit, onSettings: () -> Unit) {
    val items = listOf(
        Triple(0, "Home", Icons.Rounded.Home), Triple(1, "Playlist", Icons.Rounded.FolderOpen),
        Triple(2, "Brani", Icons.Rounded.MusicNote), Triple(3, "Album", Icons.Rounded.LibraryMusic),
        Triple(4, "Artisti", Icons.Rounded.Person), Triple(5, "Dashboard", Icons.Rounded.GridView)
    ).filter { (id, _, _) ->
        when (id) { 1 -> (hiddenMask and 2) == 0; 2, 3, 4 -> (hiddenMask and 4) == 0; 5 -> (hiddenMask and 8) == 0; else -> true }
    }
    Column(Modifier.width(240.dp).fillMaxHeight().statusBarsPadding().navigationBarsPadding().padding(horizontal = 14.dp, vertical = 12.dp)) {
        Image(painterResource(R.mipmap.ic_launcher_foreground), null, Modifier.padding(top = 8.dp).size(150.dp, 90.dp), contentScale = ContentScale.Crop)
        Spacer(Modifier.height(24.dp))
        items.forEach { (id, label, icon) -> SideItem(label, icon, sel == id) { onItem(id) } }
        Spacer(Modifier.weight(1f))
        SideItem("Cronologia ascolti", Icons.Rounded.AccessTime, false, onHistory)
        SideItem("Impostazioni", Icons.Rounded.Settings, false, onSettings)
    }
}

@Composable
fun MiniBarWide(ctl: Ctl, sheet: Animatable<Float, AnimationVector1D>, tint: Color) {
    val s = ctl.currentSong ?: return
    val scope = rememberCoroutineScope()
    val W = Color.White
    var drag by remember { mutableStateOf<Float?>(null) }
    val open: () -> Unit = { scope.launch { sheet.animateTo(0f, spring(0.88f, Spring.StiffnessMediumLow)) } }
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp).clip(RoundedCornerShape(26.dp)).background(tint)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconBtn(Icons.Rounded.KeyboardArrowUp, open, tint = W.copy(alpha = 0.6f))
        Row(Modifier.pressable(0.98f, open), verticalAlignment = Alignment.CenterVertically) {
            AnimatedContent(s.art, transitionSpec = { fadeIn(tween(300)) togetherWith fadeOut(tween(300)) }, label = "wc") { a -> Cover(a, Modifier.size(48.dp)) }
            Spacer(Modifier.width(14.dp))
            AnimatedContent(
                s, Modifier.width(220.dp),
                transitionSpec = { (slideInVertically(tween(250)) { it / 2 } + fadeIn(tween(250))) togetherWith (slideOutVertically(tween(200)) { -it / 2 } + fadeOut(tween(150))) },
                label = "wt"
            ) { song ->
                Column {
                    Txt(song.title, style = TS.title, color = W, maxLines = 1)
                    Txt(song.artists.joinToString(" / "), style = TS.small, color = W.copy(alpha = 0.6f), maxLines = 1)
                }
            }
        }
        Spacer(Modifier.width(16.dp))
        LineSlider(
            value = drag ?: (if (s.duration > 0) (ctl.pos.toFloat() / s.duration).coerceIn(0f, 1f) else 0f),
            onChange = { drag = it }, modifier = Modifier.weight(1f),
            onFinish = { drag?.let { ctl.seek((it * s.duration).toLong()) }; drag = null }
        )
        Spacer(Modifier.width(12.dp))
        HeartBtn(s.id in ctl.likes, { ctl.toggleLike(s.id) }, W, W)
        IconBtn(Icons.Rounded.SkipPrevious, { ctl.prev() }, tint = W)
        Box(Modifier.size(44.dp).pressable { ctl.toggle() }, contentAlignment = Alignment.Center) { PlayIcon(ctl, 28.dp, W) }
        IconBtn(Icons.Rounded.SkipNext, { ctl.next() }, tint = W)
        IconBtn(if (ctl.repeat == androidx.media3.common.Player.REPEAT_MODE_ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat, { ctl.cycleRepeat() },
            tint = if (ctl.repeat != androidx.media3.common.Player.REPEAT_MODE_OFF) W else W.copy(alpha = 0.4f))
        IconBtn(Icons.Rounded.Shuffle, { ctl.toggleShuffle() }, tint = if (ctl.shuffle) W else W.copy(alpha = 0.4f))
    }
}
