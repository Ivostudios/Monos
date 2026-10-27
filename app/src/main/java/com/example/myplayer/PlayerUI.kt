package com.example.myplayer

import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.min

@Composable
fun PlayIcon(ctl: Ctl, size: Dp, tint: Color) {
    AnimatedContent(
        ctl.playing,
        transitionSpec = { (fadeIn(tween(150)) + scaleIn(tween(150), 0.6f)) togetherWith (fadeOut(tween(100)) + scaleOut(tween(100), 0.6f)) },
        label = "pp"
    ) { p -> Ico(if (p) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, tint = tint, size = size) }
}

fun vivid(c: Color): Color {
    val hsv = FloatArray(3)
    android.graphics.Color.RGBToHSV((c.red * 255).toInt(), (c.green * 255).toInt(), (c.blue * 255).toInt(), hsv)
    if (hsv[1] < 0.15f) { hsv[2] = hsv[2].coerceIn(0.14f, 0.38f) }
    else { hsv[1] = (hsv[1] * 1.55f).coerceIn(0.35f, 1f); hsv[2] = hsv[2].coerceIn(0.5f, 0.82f) }
    return Color(android.graphics.Color.HSVToColor(hsv))
}

@Composable
fun BlurBg(art: Uri?) {
    val base = rememberCoverColor(art, Color(0xFF0026FF))
    val tone by animateColorAsState(vivid(base), tween(700), label = "tone")
    Box(Modifier.fillMaxSize().background(tone)) {
        AnimatedContent(art, transitionSpec = { fadeIn(tween(700)) togetherWith fadeOut(tween(700)) }, label = "bg") { a ->
            if (Build.VERSION.SDK_INT >= 31)
                Cover(a, Modifier.fillMaxSize().graphicsLayer { scaleX = 1.6f; scaleY = 1.6f; alpha = 0.55f }.blur(52.dp, BlurredEdgeTreatment.Unbounded), RectangleShape)
            else Box(Modifier.fillMaxSize())
        }
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to Color.White.copy(alpha = 0.08f), 0.5f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.5f))))
    }
}

@Composable
fun CoverPage(ctl: Ctl, s: Song) {
    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val side = minOf(maxWidth - 56.dp, maxHeight - 8.dp).coerceAtLeast(100.dp)
        AnimatedContent(
            s,
            transitionSpec = { fadeIn(tween(1, delayMillis = 240)) togetherWith fadeOut(tween(1, delayMillis = 250)) using SizeTransform(clip = false) },
            label = "cv"
        ) { song ->
            val rot by transition.animateFloat(
                transitionSpec = { if (targetState == EnterExitState.Visible) tween(260, delayMillis = 240, easing = FastOutSlowInEasing) else tween(260, easing = FastOutSlowInEasing) },
                label = "flip"
            ) { st -> if (st == EnterExitState.Visible) 0f else 90f }
            Box(Modifier.graphicsLayer { rotationY = rot; cameraDistance = 14f * density; alpha = if (rot > 88f) 0f else 1f }) {
                Cover(song.art, Modifier.size(side).shadow(24.dp, CoverShape))
            }
        }
    }
}

@Composable
fun GlassCard(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    val m = if (onClick != null) modifier.pressable(0.98f, onClick) else modifier
    Column(m.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Color.White.copy(alpha = 0.10f)).padding(18.dp), content = content)
}

@Composable
fun InfoPage(ctl: Ctl, s: Song, openAlbum: (Album) -> Unit) {
    val ctx = LocalContext.current
    val W = Color.White
    val album = ctl.albums.firstOrNull { it.id == s.albumId }
    val position = (album?.songs?.indexOfFirst { it.id == s.id } ?: -1) + 1
    val info by produceState<List<Pair<String, String>>>(emptyList(), s.id) {
        value = withContext(Dispatchers.IO) {
            try {
                val r = MediaMetadataRetriever()
                r.setDataSource(ctx, s.uri)
                val mime = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE)
                val br = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)?.toLongOrNull()
                r.release()
                listOfNotNull(
                    mime?.let { "Formato" to it.substringAfter('/').uppercase() },
                    br?.let { "Bitrate" to "${it / 1000} kbps" },
                    "Durata" to fmt(s.duration)
                )
            } catch (e: Exception) { listOf("Durata" to fmt(s.duration)) }
        }
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        if (album != null) {
            GlassCard(onClick = { openAlbum(album) }) {
                Txt("Dall'album", style = TS.label, color = W.copy(alpha = 0.6f))
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Cover(album.songs[0].art, Modifier.size(64.dp))
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Txt(album.title, style = TS.h3, color = W, maxLines = 2)
                        Txt(album.artist, style = TS.small, color = W.copy(alpha = 0.6f), maxLines = 1)
                    }
                }
                Spacer(Modifier.height(14.dp))
                Txt("Traccia $position di ${album.songs.size}", style = TS.title, color = W)
            }
        }
        GlassCard {
            Txt("Artisti", style = TS.label, color = W.copy(alpha = 0.6f))
            Spacer(Modifier.height(8.dp))
            s.artists.forEach { Txt(it, Modifier.padding(vertical = 3.dp), TS.title, W, maxLines = 1) }
        }
        GlassCard {
            Txt("Informazioni audio", style = TS.label, color = W.copy(alpha = 0.6f))
            Spacer(Modifier.height(8.dp))
            info.forEach { (k, v) ->
                Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                    Txt(k, Modifier.weight(1f), TS.body, W.copy(alpha = 0.7f))
                    Txt(v, style = TS.title, color = W)
                }
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun LightBtn(text: String, filled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.pressable(0.94f, onClick).clip(CircleShape).background(if (filled) Color.White else Color.White.copy(alpha = 0.14f))
            .padding(horizontal = 24.dp, vertical = 12.dp)
    ) { Txt(text, style = TS.title, color = if (filled) Color.Black else Color.White) }
}

@Composable
private fun LyricItem(ctl: Ctl, data: LyricsData, i: Int, idx: Int, l: LyricLine, center: Boolean) {
    val d = if (data.synced) abs(i - idx) else 0
    val a by animateFloatAsState(if (!data.synced) 1f else when (d) { 0 -> 1f; 1 -> 0.5f; 2 -> 0.32f; else -> 0.2f }, tween(450), label = "la")
    val sc by animateFloatAsState(if (!data.synced || d == 0) 1f else 0.86f, tween(450), label = "ls")
    val bl by animateFloatAsState(if (!data.synced) 0f else min(d, 4) * 1.5f, tween(450), label = "lb")
    Txt(
        l.text.ifEmpty { "\u266A" },
        Modifier.fillMaxWidth()
            .graphicsLayer { alpha = a; scaleX = sc; scaleY = sc; transformOrigin = TransformOrigin(if (center) 0.5f else 0f, 0.5f) }
            .then(if (bl > 0.1f) Modifier.blur(bl.dp, BlurredEdgeTreatment.Unbounded) else Modifier)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { if (data.synced) ctl.seek(l.time) }
            .padding(vertical = 10.dp),
        if (data.synced) TS.lyric else TS.lyricPlain, Color.White, align = if (center) TextAlign.Center else TextAlign.Start
    )
}

@Composable
private fun LyricsList(ctl: Ctl, data: LyricsData, center: Boolean) {
    val lines = data.lines
    val state = rememberLazyListState()
    val idx by remember(lines) { derivedStateOf { if (!data.synced) -1 else lines.indexOfLast { it.time <= ctl.pos + 250 } } }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val hPx = with(LocalDensity.current) { maxHeight.toPx() }
        LaunchedEffect(idx) {
            if (data.synced) { if (idx >= 0) state.animateScrollToItem(idx, -(hPx * 0.26f).toInt()) else state.animateScrollToItem(0) }
        }
        LazyColumn(
            state = state, modifier = Modifier.fillMaxSize().fadeEdges(),
            contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 24.dp, bottom = maxHeight * 0.5f)
        ) { itemsIndexed(lines) { i, l -> LyricItem(ctl, data, i, idx, l, center) } }
    }
}

@Composable
fun LyricsPage(ctl: Ctl, s: Song, auto: Boolean, center: Boolean) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var reload by remember { mutableIntStateOf(0) }
    var phase by remember(s.id) { mutableIntStateOf(0) }
    var data by remember(s.id) { mutableStateOf<LyricsData?>(null) }
    LaunchedEffect(s.id, reload) {
        phase = 0
        var d = withContext(Dispatchers.IO) { Lyrics.readLocal(ctx, s) }
        if (d == null && auto && !Lyrics.missed(ctx, s)) {
            if (Lyrics.fetch(ctx, s) == 1) d = withContext(Dispatchers.IO) { Lyrics.readLocal(ctx, s) }
        }
        data = d
        phase = if (d != null) 1 else 2
    }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch(Dispatchers.IO) {
            val t = try { ctx.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } } catch (e: Exception) { null }
            if (t != null) { Lyrics.saveRaw(ctx, s, t); reload++ }
        }
    }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        when (phase) {
            0 -> Txt("Cerco il testo...", color = Color.White.copy(alpha = 0.6f))
            1 -> data?.let { LyricsList(ctl, it, center) }
            else -> Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Txt("Nessun testo", style = TS.h2, color = Color.White)
                Txt("Non l'ho trovato online. Puoi riprovare o importare un file .lrc.", Modifier.padding(horizontal = 40.dp), TS.small, Color.White.copy(alpha = 0.6f), align = TextAlign.Center)
                LightBtn("Cerca online", true) { scope.launch { phase = 0; Lyrics.fetch(ctx, s); reload++ } }
                LightBtn("Importa .lrc", false) { importer.launch(arrayOf("*/*")) }
            }
        }
    }
}

@Composable
private fun ActiveIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, active: Boolean, onClick: () -> Unit) {
    val W = Color.White
    Box(
        Modifier.size(width = 58.dp, height = 48.dp).pressable(onClick = onClick).clip(RoundedCornerShape(16.dp))
            .background(if (active) W.copy(alpha = 0.14f) else Color.Transparent),
        contentAlignment = Alignment.Center
    ) { Ico(icon, tint = if (active) W else W.copy(alpha = 0.8f)) }
}

@Composable
fun PlayerIcons(ctl: Ctl, s: Song, listActive: Boolean, panelActive: Boolean, onList: () -> Unit, onPanel: () -> Unit, modifier: Modifier = Modifier) {
    val W = Color.White
    Row(modifier.fillMaxWidth().padding(horizontal = 8.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
        ActiveIcon(Icons.Rounded.FormatListBulleted, listActive, onList)
        IconBtn(if (ctl.repeat == Player.REPEAT_MODE_ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat, { ctl.cycleRepeat() }, tint = if (ctl.repeat != Player.REPEAT_MODE_OFF) W else W.copy(alpha = 0.55f))
        IconBtn(Icons.Rounded.Shuffle, { ctl.toggleShuffle() }, tint = if (ctl.shuffle) W else W.copy(alpha = 0.55f))
        ActiveIcon(Icons.Rounded.MoreVert, panelActive, onPanel)
        HeartBtn(s.id in ctl.likes, { ctl.toggleLike(s.id) }, W.copy(alpha = 0.8f), Color(0xFFFF5C8A))
    }
}

@Composable
fun SpeedPanel(ctl: Ctl) {
    val W = Color.White
    Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 6.dp)) {
        Txt("Velocit\u00E0 e pitch", style = TS.label, color = W)
        Txt("Regola la velocit\u00E0 e il pitch del brano come preferisci", Modifier.padding(top = 2.dp, bottom = 8.dp), TS.small, W.copy(alpha = 0.6f))
        if (ctl.linked) {
            Row { Txt("Velocit\u00E0 e pitch", Modifier.weight(1f), TS.label, W); Txt("%.2fx".format(ctl.speed), style = TS.label, color = W) }
            LineSlider(ctl.speed, { ctl.setParams(it, it) }, range = 0.5f..2f)
        } else {
            Row { Txt("Velocit\u00E0 del brano", Modifier.weight(1f), TS.label, W); Txt("%.2fx".format(ctl.speed), style = TS.label, color = W) }
            LineSlider(ctl.speed, { ctl.setParams(it, ctl.pitch) }, range = 0.5f..2f)
            Row { Txt("Pitch del brano", Modifier.weight(1f), TS.label, W); Txt("%.2fx".format(ctl.pitch), style = TS.label, color = W) }
            LineSlider(ctl.pitch, { ctl.setParams(ctl.speed, it) }, range = 0.5f..2f)
        }
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                Modifier.weight(1f).height(52.dp).pressable(0.97f) { ctl.setParams(1f, 1f) }.clip(RoundedCornerShape(26.dp)).background(Color.Black.copy(alpha = 0.4f)),
                contentAlignment = Alignment.Center
            ) { Txt("Reset", style = TS.title, color = W) }
            Box(
                Modifier.weight(1f).height(52.dp).pressable(0.97f) {
                    val l = !ctl.linked
                    ctl.linked = l
                    if (l) ctl.setParams(ctl.speed, ctl.speed)
                }.clip(RoundedCornerShape(26.dp)).background(W),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Txt(if (ctl.linked) "Separa" else "Unisci", style = TS.title, color = Color.Black)
                    Spacer(Modifier.width(8.dp))
                    Ico(if (ctl.linked) Icons.Rounded.LinkOff else Icons.Rounded.Link, tint = Color.Black, size = 20.dp)
                }
            }
        }
    }
}

@Composable
fun QueueList(ctl: Ctl, s: Song, modifier: Modifier = Modifier) {
    val W = Color.White
    val upcoming = ctl.queue.withIndex().filter { it.index > ctl.current }
    Column(modifier) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Cover(s.art, Modifier.size(56.dp))
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Txt(s.title, style = TS.title, color = W, maxLines = 1)
                Txt(s.artists.joinToString(" / "), style = TS.small, color = W.copy(alpha = 0.6f), maxLines = 1)
                Spacer(Modifier.height(6.dp))
                val f = if (s.duration > 0) (ctl.pos.toFloat() / s.duration).coerceIn(0f, 1f) else 0f
                Box(Modifier.fillMaxWidth().height(4.dp).clip(CircleShape).background(W.copy(alpha = 0.22f))) { Box(Modifier.fillMaxWidth(f).fillMaxHeight().background(W)) }
            }
            HeartBtn(s.id in ctl.likes, { ctl.toggleLike(s.id) }, W, W)
            Box(Modifier.size(44.dp).pressable { ctl.toggle() }, contentAlignment = Alignment.Center) { PlayIcon(ctl, 28.dp, W) }
        }
        Txt("Prossimi brani", Modifier.padding(horizontal = 20.dp, vertical = 8.dp), TS.label, W)
        LazyColumn(Modifier.fillMaxSize()) {
            items(upcoming) { iv ->
                Row(
                    Modifier.fillMaxWidth().pressable(0.98f) { ctl.play(ctl.queue, iv.index) }.padding(horizontal = 20.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Cover(iv.value.art, Modifier.size(52.dp))
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Txt(iv.value.title, style = TS.title, color = W, maxLines = 1)
                        Txt(iv.value.artists.joinToString(" / "), style = TS.small, color = W.copy(alpha = 0.6f), maxLines = 1)
                    }
                }
            }
            if (upcoming.isEmpty()) item { Txt("Nessun altro brano in coda.", Modifier.padding(20.dp), TS.body, W.copy(alpha = 0.6f)) }
        }
    }
}

@Composable
fun QueueView(ctl: Ctl, s: Song, onBack: () -> Unit, onPanel: () -> Unit) {
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        PlayerIcons(ctl, s, true, false, onBack, { onBack(); onPanel() }, Modifier.padding(top = 16.dp))
        QueueList(ctl, s, Modifier.weight(1f))
    }
}

@Composable
fun ProgressBlock(ctl: Ctl, s: Song) {
    val W = Color.White
    var drag by remember { mutableStateOf<Float?>(null) }
    Column(Modifier.padding(horizontal = 24.dp)) {
        LineSlider(
            value = drag ?: (if (s.duration > 0) (ctl.pos.toFloat() / s.duration).coerceIn(0f, 1f) else 0f),
            onChange = { drag = it },
            onFinish = { drag?.let { ctl.seek((it * s.duration).toLong()) }; drag = null }
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Txt(fmt(drag?.let { (it * s.duration).toLong() } ?: ctl.pos), style = TS.small, color = W.copy(alpha = 0.7f))
            Txt(fmt(s.duration), style = TS.small, color = W.copy(alpha = 0.7f))
        }
    }
}

@Composable
fun ControlsRow(ctl: Ctl) {
    val W = Color.White
    Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
        IconBtn(Icons.Rounded.SkipPrevious, { ctl.prev() }, tint = W, size = 40.dp, box = 68.dp)
        Box(Modifier.size(84.dp).pressable { ctl.toggle() }, contentAlignment = Alignment.Center) { PlayIcon(ctl, 64.dp, W) }
        IconBtn(Icons.Rounded.SkipNext, { ctl.next() }, tint = W, size = 40.dp, box = 68.dp)
    }
}

@Composable
private fun WidePlayer(
    ctl: Ctl, s: Song, auto: Boolean, center: Boolean, showQueue: Boolean, panel: Boolean,
    onQueue: () -> Unit, onPanel: () -> Unit, collapse: () -> Unit
) {
    val W = Color.White
    Row(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        Column(Modifier.weight(1f).fillMaxHeight().padding(vertical = 12.dp)) {
            Row(Modifier.fillMaxWidth().padding(start = 24.dp, end = 12.dp, top = 8.dp)) {
                AnimatedContent(s, Modifier.weight(1f), transitionSpec = { fadeIn(tween(300)) togetherWith fadeOut(tween(200)) }, label = "wh") { song ->
                    Column {
                        Txt(song.title, style = TS.h2, color = W, maxLines = 2)
                        Spacer(Modifier.height(2.dp))
                        Txt(song.artists.joinToString(" / "), style = TS.title, color = W.copy(alpha = 0.6f), maxLines = 1)
                    }
                }
                IconBtn(Icons.Rounded.KeyboardArrowDown, collapse, tint = W, size = 30.dp, box = 48.dp)
            }
            Box(Modifier.weight(1f).fillMaxWidth()) { CoverPage(ctl, s) }
            ProgressBlock(ctl, s)
            ControlsRow(ctl)
            PlayerIcons(ctl, s, showQueue, panel, onQueue, onPanel, Modifier.padding(vertical = 4.dp))
            AnimatedVisibility(panel, enter = expandVertically(tween(280)) + fadeIn(tween(250)), exit = shrinkVertically(tween(220)) + fadeOut(tween(150))) {
                SpeedPanel(ctl)
            }
        }
        Box(Modifier.weight(1f).fillMaxHeight()) {
            AnimatedContent(showQueue, transitionSpec = { fadeIn(tween(250)) togetherWith fadeOut(tween(150)) }, label = "wr") { q ->
                if (q) QueueList(ctl, s, Modifier.fillMaxSize().padding(top = 8.dp)) else LyricsPage(ctl, s, auto, center)
            }
        }
    }
}

@Composable
fun PlayerContent(ctl: Ctl, s: Song, auto: Boolean, center: Boolean, openness: () -> Float, collapse: () -> Unit, openAlbum: (Album) -> Unit) {
    val W = Color.White
    val scope = rememberCoroutineScope()
    val wide = LocalWide.current
    var showQueue by remember { mutableStateOf(false) }
    var panel by remember { mutableStateOf(false) }
    val hasAlbum = s.hasAlbum
    val coverIdx = if (hasAlbum) 1 else 0
    val pager = rememberPagerState(initialPage = coverIdx) { if (hasAlbum) 3 else 2 }
    LaunchedEffect(hasAlbum) { pager.scrollToPage(coverIdx) }
    Box(Modifier.fillMaxSize()) {
        BlurBg(s.art)
        if (wide) WidePlayer(ctl, s, auto, center, showQueue, panel, { showQueue = !showQueue }, { panel = !panel }, collapse)
        else AnimatedContent(
            showQueue,
            transitionSpec = { (fadeIn(tween(250)) + slideInVertically(tween(300)) { it / 12 }) togetherWith fadeOut(tween(150)) },
            label = "view"
        ) { queue ->
            if (queue) QueueView(ctl, s, { showQueue = false }, { panel = true })
            else Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
                Row(
                    Modifier.fillMaxWidth().padding(start = 24.dp, end = 12.dp, top = 20.dp)
                        .graphicsLayer { alpha = openness(); translationY = (1f - openness()) * 70f }
                ) {
                    AnimatedContent(s, Modifier.weight(1f), transitionSpec = { fadeIn(tween(300)) togetherWith fadeOut(tween(200)) }, label = "hd") { song ->
                        Column {
                            Txt(song.title, style = TS.h1, color = W, maxLines = 2)
                            Spacer(Modifier.height(4.dp))
                            Txt(song.artists.joinToString(" / "), style = TS.title, color = W.copy(alpha = 0.6f), maxLines = 1)
                        }
                    }
                    IconBtn(Icons.Rounded.KeyboardArrowDown, collapse, tint = W, size = 30.dp, box = 48.dp)
                }
                Box(
                    Modifier.weight(1f).fillMaxWidth().graphicsLayer {
                        val k = openness()
                        val sc = 0.4f + 0.6f * k
                        scaleX = sc; scaleY = sc
                        transformOrigin = TransformOrigin(0.1f, 1f)
                        alpha = (k * 1.8f).coerceIn(0f, 1f)
                    }
                ) {
                    HorizontalPager(pager, Modifier.fillMaxSize()) { page ->
                        val kind = if (hasAlbum) page else page + 1
                        Box(
                            Modifier.fillMaxSize().graphicsLayer {
                                val o = abs((pager.currentPage - page) + pager.currentPageOffsetFraction).coerceIn(0f, 1f)
                                alpha = 1f - o * 0.5f
                                val sc = 1f - o * 0.06f
                                scaleX = sc; scaleY = sc
                            }
                        ) {
                            when (kind) {
                                0 -> InfoPage(ctl, s, openAlbum)
                                1 -> CoverPage(ctl, s)
                                else -> LyricsPage(ctl, s, auto, center)
                            }
                        }
                    }
                }
                Column(Modifier.graphicsLayer { alpha = ((openness() - 0.3f) / 0.7f).coerceIn(0f, 1f) }) {
                    ProgressBlock(ctl, s)
                    ControlsRow(ctl)
                    PlayerIcons(ctl, s, false, panel, { showQueue = true }, { panel = !panel }, Modifier.padding(vertical = 8.dp))
                    AnimatedVisibility(panel, enter = expandVertically(tween(280)) + fadeIn(tween(250)), exit = shrinkVertically(tween(220)) + fadeOut(tween(150))) {
                        SpeedPanel(ctl)
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
    }
}

@Composable
fun PlayerSheet(ctl: Ctl, sheet: Animatable<Float, AnimationVector1D>, auto: Boolean, center: Boolean, openAlbum: (Album) -> Unit) {
    val s = ctl.currentSong ?: return
    val scope = rememberCoroutineScope()
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val hPx = with(LocalDensity.current) { maxHeight.toPx() }
        val dragState = rememberDraggableState { d -> scope.launch { sheet.snapTo((sheet.value + d / hPx).coerceIn(0f, 1f)) } }
        Box(
            Modifier.fillMaxSize()
                .graphicsLayer {
                    val pr = sheet.value.coerceIn(0f, 1f)
                    translationY = pr * size.height
                    shape = RoundedCornerShape((30 * pr).dp)
                    clip = true
                }
                .draggable(dragState, Orientation.Vertical, onDragStopped = { v ->
                    sheet.animateTo(if (sheet.value > 0.3f || v > 1800f) 1f else 0f, spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow))
                })
                .blockTouches()
        ) {
            PlayerContent(
                ctl, s, auto, center, { 1f - sheet.value.coerceIn(0f, 1f) },
                collapse = { scope.launch { sheet.animateTo(1f, spring(0.9f, Spring.StiffnessMediumLow)) } }, openAlbum = openAlbum
            )
        }
    }
}
