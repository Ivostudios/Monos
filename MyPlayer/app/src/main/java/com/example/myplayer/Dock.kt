package com.example.myplayer

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

@Composable
fun MiniBar(ctl: Ctl, sheet: Animatable<Float, AnimationVector1D>, hPx: Float) {
    val s = ctl.currentSong ?: return
    val scope = rememberCoroutineScope()
    val W = Color.White
    val liked = s.id in ctl.likes
    val drag = rememberDraggableState { d -> scope.launch { sheet.snapTo((sheet.value + d / hPx).coerceIn(0f, 1f)) } }
    Row(
        Modifier.fillMaxWidth()
            .draggable(drag, Orientation.Vertical, onDragStopped = { v ->
                sheet.animateTo(if (sheet.value < 0.75f || v < -1200f) 0f else 1f, spring(0.88f, Spring.StiffnessMediumLow))
            })
            .pointerInput(Unit) { detectTapGestures { scope.launch { sheet.animateTo(0f, spring(0.88f, Spring.StiffnessMediumLow)) } } }
            .padding(start = 16.dp, end = 8.dp, top = 14.dp, bottom = 10.dp),
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
        IconBtn(if (liked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, { ctl.toggleLike(s.id) }, tint = W, box = 44.dp)
        Box(Modifier.size(44.dp).pressable { ctl.toggle() }, contentAlignment = Alignment.Center) { PlayIcon(ctl, 28.dp, W) }
    }
}

@Composable
fun NavBar(tab: Int, onTab: (Int) -> Unit) {
    val items = listOf(
        Triple("Home", Icons.Rounded.Home, 0), Triple("Library", Icons.Rounded.FolderOpen, 1),
        Triple("Brani", Icons.Rounded.MusicNote, 2), Triple("Dashboard", Icons.Rounded.GridView, 3)
    )
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(74.dp)) {
        items.forEach { (label, icon, i) ->
            val sel = tab == i
            val c by animateColorAsState(if (sel) Color.White else Color.White.copy(alpha = 0.38f), label = "nc")
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
