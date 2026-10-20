package com.example.myplayer

import android.net.Uri
import android.os.Build
import androidx.compose.animation.*
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.delay
import kotlin.random.Random

sealed class Page(val depth: Int) {
    class AlbumP(val album: Album, d: Int) : Page(d)
    class Liked(d: Int) : Page(d)
    class PlaylistP(val id: Long, d: Int) : Page(d)
    class FolderP(val id: Long, d: Int) : Page(d)
    class Artist(val name: String, d: Int) : Page(d)
    class Settings(val kind: Int, d: Int) : Page(d)
}

// ---------- menu e piccoli componenti ----------

@Composable
fun PopMenu(open: Boolean, onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    if (!open) return
    val p = pal
    Popup(alignment = Alignment.TopEnd, offset = IntOffset(0, 100), onDismissRequest = onDismiss, properties = PopupProperties(focusable = true)) {
        var shown by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) { shown = true }
        AnimatedVisibility(shown, enter = fadeIn(tween(150)) + scaleIn(tween(200), 0.85f, TransformOrigin(1f, 0f))) {
            Column(
                Modifier.padding(end = 8.dp).shadow(18.dp, RoundedCornerShape(18.dp)).clip(RoundedCornerShape(18.dp))
                    .background(p.surface).padding(vertical = 8.dp).widthIn(min = 210.dp),
                content = content
            )
        }
    }
}

@Composable
fun MenuRow(text: String, icon: ImageVector, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().pressable(0.98f, onClick).padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) { Ico(icon, size = 20.dp); Txt(text, style = TS.title) }
}

@Composable
fun MoreMenu(items: List<Triple<String, ImageVector, () -> Unit>>) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconBtn(Icons.Rounded.MoreVert, { open = true })
        PopMenu(open, { open = false }) {
            items.forEach { (t, i, f) -> MenuRow(t, i) { open = false; f() } }
        }
    }
}

@Composable
fun TrackMenu(ctl: Ctl, s: Song) {
    val p = pal
    var open by remember { mutableStateOf(false) }
    val liked = s.id in ctl.likes
    Box {
        IconBtn(Icons.Rounded.MoreVert, { open = true }, tint = p.sub, box = 40.dp)
        PopMenu(open, { open = false }) {
            MenuRow(if (liked) "Rimuovi dai preferiti" else "Aggiungi ai preferiti", if (liked) Icons.Rounded.FavoriteBorder else Icons.Rounded.Favorite) {
                open = false; ctl.toggleLike(s.id)
            }
            if (ctl.playlists.isNotEmpty()) {
                Txt("Aggiungi a playlist", Modifier.padding(horizontal = 18.dp, vertical = 6.dp), TS.small, p.sub)
                ctl.playlists.forEach { pl -> MenuRow(pl.name, Icons.Rounded.QueueMusic) { open = false; ctl.addToPlaylist(pl.id, s.id) } }
            }
        }
    }
}

@Composable
fun PageHeader(title: String, onSearch: () -> Unit, onSettings: () -> Unit, extra: @Composable RowScope.() -> Unit = {}) {
    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 12.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Txt(title, Modifier.weight(1f), TS.h1, maxLines = 1)
        extra()
        IconBtn(Icons.Rounded.Search, onSearch)
        MoreMenu(listOf(Triple("Impostazioni", Icons.Rounded.Settings, onSettings)))
    }
}

@Composable
fun Label(text: String, modifier: Modifier = Modifier) {
    Txt(text, modifier.padding(horizontal = 16.dp, vertical = 12.dp), TS.label, pal.sub)
}

@Composable
fun Empty(msg: String = "Nessun brano trovato.\nControlla il permesso audio.") {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) { Txt(msg, color = pal.sub, align = TextAlign.Center) }
}

@Composable
fun HeartCover(modifier: Modifier, iconSize: androidx.compose.ui.unit.Dp = 56.dp) {
    Box(
        modifier.clip(CoverShape).background(Brush.linearGradient(listOf(Color(0xFF151515), Color(0xFF454545)))),
        contentAlignment = Alignment.Center
    ) { Ico(Icons.Rounded.Favorite, tint = Color.White, size = iconSize) }
}

@Composable
fun Mosaic(covers: List<Uri>, modifier: Modifier) {
    val prog = remember { Animatable(0f) }
    LaunchedEffect(Unit) { prog.animateTo(1f, spring(0.7f, Spring.StiffnessLow)) }
    if (covers.size < 4) { Cover(covers.firstOrNull(), modifier); return }
    BoxWithConstraints(modifier.clip(CoverShape)) {
        val half = maxWidth / 2
        covers.take(4).forEachIndexed { i, u ->
            val col = i % 2
            val row = i / 2
            Cover(
                u,
                Modifier.size(half).offset(half * col, half * row).graphicsLayer {
                    val k = 1f - prog.value
                    translationX = (if (col == 0) 1f else -1f) * half.toPx() * 0.5f * k
                    translationY = (if (row == 0) 1f else -1f) * half.toPx() * 0.5f * k
                },
                RectangleShape
            )
        }
    }
}

@Composable
fun TrackRow(ctl: Ctl, list: List<Song>, i: Int, number: Int? = null) {
    val s = list[i]
    val p = pal
    val cur = ctl.currentSong?.id == s.id
    val col by animateColorAsState(if (cur) p.accentText else p.fg, tween(300), label = "row")
    Row(
        Modifier.fillMaxWidth().pressable(0.98f) { ctl.play(list, i) }.padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (number != null) Txt("$number", Modifier.width(38.dp), TS.num, col)
        else { Cover(s.art, Modifier.size(50.dp)); Spacer(Modifier.width(16.dp)) }
        Column(Modifier.weight(1f)) {
            Txt(s.title, style = TS.title, color = col, maxLines = 1)
            Txt(if (number != null) "${s.artists.joinToString(" / ")}  \u2022  ${fmt(s.duration)}" else s.artists.joinToString(" / "), style = TS.small, color = p.sub, maxLines = 1)
        }
        TrackMenu(ctl, s)
    }
}

@Composable
fun AlbumTile(a: Album, modifier: Modifier, onClick: () -> Unit) {
    val p = pal
    Column(modifier.pressable(0.96f, onClick)) {
        Cover(a.songs[0].art, Modifier.fillMaxWidth().aspectRatio(1f))
        Spacer(Modifier.height(8.dp))
        Txt(a.title, style = TS.tile, maxLines = 2)
        Txt("Album  \u2022  ${a.artist}", style = TS.small, color = p.sub, maxLines = 1)
    }
}

// ---------- schermate ----------

@Composable
fun HomeScreen(ctl: Ctl, openAlbum: (Album) -> Unit, openArtist: (String) -> Unit, onSearch: () -> Unit, onSettings: () -> Unit) {
    val p = pal
    val day = remember { java.time.LocalDate.now().toEpochDay() }
    val sugAlbums = remember(ctl.albums) { ctl.albums.shuffled().take(8) }
    val albumOfDay = remember(ctl.albums, day) { ctl.albums.takeIf { it.isNotEmpty() }?.let { it[Random(day).nextInt(it.size)] } }
    val artistOfDay = remember(ctl.artistMap, day) {
        val c = ctl.artistMap.filter { it.key != "Artista sconosciuto" && it.value.size >= 2 }.keys.toList()
        c.takeIf { it.isNotEmpty() }?.let { c[Random(day + 1).nextInt(c.size)] }
    }
    val artistTracks = remember(artistOfDay, ctl.artistMap) { artistOfDay?.let { ctl.artistMap[it]?.shuffled(Random(day))?.take(8) } ?: emptyList() }
    val sugTracks = remember(ctl.songs) { ctl.songs.shuffled().take(6) }
    Column(Modifier.fillMaxSize()) {
        PageHeader("Home", onSearch, onSettings)
        if (ctl.songs.isEmpty()) Empty()
        else LazyColumn(Modifier.fillMaxSize()) {
            if (sugAlbums.isNotEmpty()) {
                item { Label("Album suggeriti") }
                item {
                    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(sugAlbums) { a -> AlbumTile(a, Modifier.width(160.dp)) { openAlbum(a) } }
                    }
                }
            }
            albumOfDay?.let { a ->
                item {
                    Spacer(Modifier.height(16.dp))
                    Box(
                        Modifier.padding(horizontal = 16.dp).fillMaxWidth().height(203.dp).pressable(0.98f) { openAlbum(a) }.clip(RoundedCornerShape(24.dp))
                    ) {
                        Cover(a.songs[0].art, Modifier.fillMaxSize(), RectangleShape)
                        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to Color.Black.copy(alpha = 0.2f), 1f to Color.Black.copy(alpha = 0.78f))))
                        Row(Modifier.align(Alignment.TopStart).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Ico(Icons.Rounded.Star, tint = Color(0xFFFFC83D), size = 22.dp)
                            Spacer(Modifier.width(8.dp))
                            Txt("Album del giorno", style = TS.h3.copy(fontSize = 15.sp), color = Color.White)
                        }
                        Column(Modifier.align(Alignment.BottomStart).padding(16.dp).fillMaxWidth(0.8f)) {
                            Txt(a.title, style = TS.h2, color = Color.White, maxLines = 2)
                            Txt("Album  \u2022  ${a.artist}", style = TS.small, color = Color.White.copy(alpha = 0.75f), maxLines = 1)
                        }
                        Box(Modifier.align(Alignment.BottomEnd).padding(12.dp).size(44.dp).pressable { ctl.play(a.songs, 0) }, contentAlignment = Alignment.Center) {
                            Ico(Icons.Rounded.PlayArrow, tint = Color.White, size = 34.dp)
                        }
                    }
                }
            }
            if (artistOfDay != null && artistTracks.isNotEmpty()) {
                item {
                    Label("Artista del giorno")
                    Row(
                        Modifier.fillMaxWidth().pressable(0.98f) { openArtist(artistOfDay) }.padding(horizontal = 16.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Cover(artistTracks[0].art, Modifier.size(56.dp), CircleShape)
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Txt(artistOfDay, style = TS.h3, maxLines = 1)
                            Txt("${ctl.artistMap[artistOfDay]?.size ?: 0} brani", style = TS.small, color = p.sub)
                        }
                        Ico(Icons.Rounded.ChevronRight, tint = p.sub)
                    }
                }
                itemsIndexed(artistTracks) { i, _ -> TrackRow(ctl, artistTracks, i) }
            }
            item { Label("Brani suggeriti") }
            itemsIndexed(sugTracks) { i, _ -> TrackRow(ctl, sugTracks, i) }
            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}

@Composable
fun TracksScreen(ctl: Ctl, onSearch: () -> Unit, onSettings: () -> Unit) {
    val p = pal
    var recent by rememberSaveable { mutableStateOf(true) }
    val list = remember(ctl.songs, recent) { if (recent) ctl.songs.sortedByDescending { it.added } else ctl.songs }
    Column(Modifier.fillMaxSize()) {
        PageHeader("Brani", onSearch, onSettings)
        Row(Modifier.fillMaxWidth().pressable(0.99f) { recent = !recent }.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Txt(if (recent) "Aggiunti di recente" else "In ordine alfabetico", Modifier.weight(1f), TS.label, p.sub)
            Txt("${ctl.songs.size} brani", style = TS.small, color = p.sub)
        }
        if (ctl.songs.isEmpty()) Empty()
        else LazyColumn(Modifier.fillMaxSize()) { itemsIndexed(list) { i, _ -> TrackRow(ctl, list, i) } }
    }
}

@Composable
fun DashboardScreen(ctl: Ctl, openAlbum: (Album) -> Unit, onSearch: () -> Unit, onSettings: () -> Unit) {
    val p = pal
    val streak = ctl.streak
    val hours = ctl.totalMs / 3_600_000.0
    val favAlbum = remember(ctl.albumMs, ctl.albums) {
        ctl.albumMs.entries.sortedByDescending { it.value }.firstNotNullOfOrNull { e -> ctl.albums.firstOrNull { it.id == e.key } }
    }
    val history = ctl.recent.mapNotNull { ctl.byId[it] }
    val letters = listOf("L", "M", "M", "G", "V", "S", "D")
    val done = ctl.weekDone
    @Composable
    fun Card(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
        Column(modifier.clip(RoundedCornerShape(24.dp)).background(p.card).padding(16.dp), content = content)
    }
    @Composable
    fun Chip(text: String) {
        Box(Modifier.clip(CircleShape).background(p.accent).padding(horizontal = 14.dp, vertical = 9.dp)) { Txt(text, style = TS.title.copy(fontSize = 18.sp), color = p.onAccent) }
    }
    Column(Modifier.fillMaxSize()) {
        PageHeader("Dashboard", onSearch, onSettings)
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
            item {
                Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Txt("I tuoi record", Modifier.weight(1f), TS.label, p.sub)
                    Ico(Icons.Rounded.AccessTime, tint = p.sub)
                }
            }
            item {
                Card(Modifier.padding(horizontal = 16.dp).fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Ico(Icons.Rounded.LocalFireDepartment, size = 34.dp)
                        Spacer(Modifier.width(10.dp))
                        Txt(if (streak == 1) "1 giorno" else "$streak giorni", style = TS.big)
                    }
                    Spacer(Modifier.height(10.dp))
                    Txt(if (streak > 0) "Hai ascoltato tanto, continua cos\u00EC!" else "Ascolta qualcosa oggi per iniziare la serie.", style = TS.body)
                    Spacer(Modifier.height(12.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        letters.forEachIndexed { i, l ->
                            val on = done.getOrElse(i) { false }
                            val bg by animateColorAsState(if (on) p.accent else p.fg.copy(alpha = 0.08f), tween(400), label = "wd")
                            Box(Modifier.size(40.dp).clip(CircleShape).background(bg), contentAlignment = Alignment.Center) {
                                Txt(l, style = TS.title, color = if (on) p.onAccent else p.sub)
                            }
                        }
                    }
                }
            }
            item {
                Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Card(Modifier.weight(1f)) {
                        Chip(if (hours >= 1) "${hours.toInt()} Ore" else "${ctl.totalMs / 60000} Min")
                        Spacer(Modifier.height(10.dp))
                        Txt("Ascolto senza fine, eh?", style = TS.body)
                    }
                    Card(Modifier.weight(1f)) {
                        Chip("${ctl.plays} Brani")
                        Spacer(Modifier.height(10.dp))
                        Txt("Ne hai ascoltati tanti", style = TS.body)
                    }
                }
            }
            item {
                Card(Modifier.padding(horizontal = 16.dp).fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Ico(Icons.Rounded.Star, tint = Color(0xFFFFC83D), size = 24.dp)
                        Spacer(Modifier.width(8.dp))
                        Txt("Album di sempre", style = TS.h3.copy(fontSize = 15.sp))
                    }
                    Spacer(Modifier.height(12.dp))
                    if (favAlbum == null) Txt("Ascolta un album per un po' e comparir\u00E0 qui.", style = TS.small, color = p.sub)
                    else Row(Modifier.fillMaxWidth().pressable(0.98f) { openAlbum(favAlbum) }, verticalAlignment = Alignment.CenterVertically) {
                        Cover(favAlbum.songs[0].art, Modifier.size(50.dp))
                        Spacer(Modifier.width(16.dp))
                        Column(Modifier.weight(1f)) {
                            Txt(favAlbum.title, style = TS.title, maxLines = 1)
                            Txt("Di ${favAlbum.artist}", style = TS.small, color = p.sub, maxLines = 1)
                        }
                        Ico(Icons.Rounded.ChevronRight, tint = p.sub)
                    }
                }
            }
            item {
                Row(Modifier.padding(horizontal = 16.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Txt("Cronologia ascolti", Modifier.weight(1f), TS.label, p.sub)
                    Ico(Icons.Rounded.AccessTime, tint = p.sub)
                }
            }
            if (history.isEmpty()) item { Txt("Ancora niente: i brani che ascolti compariranno qui.", Modifier.padding(horizontal = 16.dp), TS.small, p.sub) }
            itemsIndexed(history) { i, _ -> TrackRow(ctl, history, i) }
        }
    }
}

// ---------- pagine in sovrapposizione ----------

@Composable
fun DetailPage(
    title: String, sub: String, art: Uri?, mosaic: List<Uri>, heart: Boolean, songs: List<Song>, numbered: Boolean,
    ctl: Ctl, back: () -> Unit, menu: List<Triple<String, ImageVector, () -> Unit>>
) {
    val p = pal
    val ambient = art ?: mosaic.firstOrNull()
    val tone by animateColorAsState(rememberCoverColor(ambient, p.fg), tween(500), label = "tone")
    Box(Modifier.fillMaxSize().background(p.bg).blockTouches()) {
        Box(Modifier.fillMaxWidth().height(340.dp)) {
            if (Build.VERSION.SDK_INT >= 31 && ambient != null)
                Cover(ambient, Modifier.fillMaxSize().blur(26.dp, BlurredEdgeTreatment.Unbounded).graphicsLayer { alpha = 0.7f }, RectangleShape)
            else Box(Modifier.fillMaxSize().background(tone.copy(alpha = 0.3f)))
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to Color.Transparent, 1f to p.bg)))
        }
        LazyColumn(Modifier.fillMaxSize().statusBarsPadding()) {
            item {
                Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconBtn(Icons.Rounded.ArrowBack, back)
                    Spacer(Modifier.weight(1f))
                    MoreMenu(menu)
                }
            }
            item {
                Box(Modifier.padding(16.dp).size(173.dp)) {
                    when {
                        art != null -> Cover(art, Modifier.fillMaxSize())
                        heart -> HeartCover(Modifier.fillMaxSize(), 64.dp)
                        else -> Mosaic(mosaic, Modifier.fillMaxSize())
                    }
                }
            }
            item {
                Row(Modifier.padding(start = 16.dp, end = 8.dp, top = 4.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Txt(title, style = TS.h2, maxLines = 3)
                        Spacer(Modifier.height(2.dp))
                        Txt(sub, style = TS.small, color = p.sub, maxLines = 2)
                    }
                    Box(Modifier.size(56.dp).pressable { if (songs.isNotEmpty()) ctl.play(songs, 0) }, contentAlignment = Alignment.Center) {
                        Ico(Icons.Rounded.PlayArrow, size = 42.dp)
                    }
                }
            }
            if (songs.isEmpty()) item { Txt("Ancora nessun brano.", Modifier.padding(16.dp), TS.body, p.sub) }
            itemsIndexed(songs) { i, _ -> TrackRow(ctl, songs, i, if (numbered) i + 1 else null) }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
fun SearchPage(ctl: Ctl, back: () -> Unit, openAlbum: (Album) -> Unit) {
    val p = pal
    var q by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    val kb = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) { delay(180); try { focus.requestFocus() } catch (e: Exception) { }; kb?.show() }
    val key = q.trim().lowercase()
    val songs = remember(key, ctl.songs) { if (key.isEmpty()) emptyList() else ctl.songs.filter { it.title.lowercase().contains(key) || it.artist.lowercase().contains(key) || it.album.lowercase().contains(key) } }
    val albums = remember(key, ctl.albums) { if (key.isEmpty()) emptyList() else ctl.albums.filter { it.title.lowercase().contains(key) || it.artist.lowercase().contains(key) } }
    Box(Modifier.fillMaxSize().background(p.bg).blockTouches()) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 16.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconBtn(Icons.Rounded.ArrowBack, { kb?.hide(); back() })
                Box(Modifier.weight(1f).height(48.dp).clip(CircleShape).background(p.card).padding(horizontal = 18.dp), contentAlignment = Alignment.CenterStart) {
                    BasicTextField(
                        value = q, onValueChange = { q = it }, singleLine = true,
                        textStyle = TS.body.copy(color = p.fg), cursorBrush = SolidColor(p.fg),
                        modifier = Modifier.fillMaxWidth().focusRequester(focus),
                        decorationBox = { inner ->
                            Box(contentAlignment = Alignment.CenterStart) {
                                if (q.isEmpty()) Txt("Cerca brani, album, artisti", color = p.sub, maxLines = 1)
                                inner()
                            }
                        }
                    )
                }
                if (q.isNotEmpty()) IconBtn(Icons.Rounded.Close, { q = "" })
            }
            if (key.isNotEmpty() && songs.isEmpty() && albums.isEmpty()) Empty("Nessun risultato per \"$q\"")
            else LazyColumn(Modifier.fillMaxSize()) {
                if (albums.isNotEmpty()) {
                    item { Label("Album") }
                    item {
                        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            items(albums) { a -> AlbumTile(a, Modifier.width(140.dp)) { openAlbum(a) } }
                        }
                    }
                }
                if (songs.isNotEmpty()) {
                    item { Label("Brani") }
                    itemsIndexed(songs) { i, _ -> TrackRow(ctl, songs, i) }
                }
            }
        }
    }
}

@Composable
fun CrashScreen(text: String, onClose: () -> Unit) {
    val p = pal
    val clip = LocalClipboardManager.current
    Box(Modifier.fillMaxSize().background(p.bg).blockTouches()) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(20.dp)) {
            Txt("L'app si \u00E8 chiusa", style = TS.h2)
            Spacer(Modifier.height(6.dp))
            Txt("Copia il testo qui sotto e incollalo nella chat: mi dice cosa \u00E8 andato storto.", style = TS.small, color = p.sub)
            Spacer(Modifier.height(14.dp))
            Box(Modifier.weight(1f).fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(p.card).verticalScroll(rememberScrollState()).padding(12.dp)) {
                Txt(text, style = TS.small.copy(fontSize = 11.sp, lineHeight = 15.sp))
            }
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PillBtn("Copia", Icons.Rounded.ContentCopy, { clip.setText(AnnotatedString(text)) })
                PillBtn("Chiudi", null, onClose, filled = false)
            }
        }
    }
}
