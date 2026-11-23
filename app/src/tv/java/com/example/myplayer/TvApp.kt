package com.example.myplayer

import android.Manifest
import android.os.Build
import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.random.Random

// ---------------------------------------------------------------- scala e componenti di base

/** Unit\u00E0 di progetto: 1 px del Figma (1920x1080) in dp, per qualunque densit\u00E0 dello schermo. */
val LocalTvU = compositionLocalOf { 0.5f }

@Composable
fun Int.tv(): Dp = (this * LocalTvU.current).dp

@Composable
fun Int.tsp(): TextUnit = (this * LocalTvU.current * 1.12f).sp

@Composable
fun TvTxt(
    text: String, size: Int, modifier: Modifier = Modifier, color: Color = Color.White, bold: Boolean = false,
    serif: Boolean = false, maxLines: Int = 1, align: TextAlign = TextAlign.Unspecified
) {
    BasicText(
        text, modifier,
        TextStyle(
            fontFamily = if (serif) UnnaFont else UrbFont,
            fontWeight = if (bold || serif) FontWeight.Bold else FontWeight.Medium,
            fontSize = size.tsp(), color = color, textAlign = align
        ),
        overflow = TextOverflow.Ellipsis, maxLines = maxLines
    )
}

/** Elemento selezionabile col telecomando: bordo bianco e leggero ingrandimento quando ha il focus. */
fun Modifier.tvFocus(shape: Shape, ring: Dp = 3.dp, onClick: () -> Unit): Modifier = composed {
    var f by remember { mutableStateOf(false) }
    val sc by animateFloatAsState(if (f) 1.04f else 1f, spring(stiffness = Spring.StiffnessMedium), label = "tvf")
    this.onFocusChanged { f = it.isFocused }
        .graphicsLayer { scaleX = sc; scaleY = sc }
        .border(if (f) ring else 0.dp, Color.White, shape)
        .clip(shape)
        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
}

@Composable
fun TvButton(label: String, icon: ImageVector?, filled: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Row(
        modifier.tvFocus(CircleShape, 3.dp, onClick).background(if (filled) Color.White else Color.White.copy(alpha = 0.14f))
            .padding(horizontal = 30.tv(), vertical = 15.tv()),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center
    ) {
        if (icon != null) { Ico(icon, tint = if (filled) Color.Black else Color.White, size = 26.tv()); Spacer(Modifier.width(10.tv())) }
        TvTxt(label, 20, color = if (filled) Color.Black else Color.White, bold = true)
    }
}

@Composable
fun TvRound(icon: ImageVector, size: Int, filled: Boolean, modifier: Modifier = Modifier, tint: Color? = null, onClick: () -> Unit) {
    Box(
        modifier.size(size.tv()).tvFocus(CircleShape, 3.dp, onClick).background(if (filled) Color.White else Color.White.copy(alpha = 0.14f)),
        contentAlignment = Alignment.Center
    ) { Ico(icon, tint = tint ?: (if (filled) Color.Black else Color.White), size = (size * 0.5f).toInt().tv()) }
}

@Composable
fun TvTextBtn(icon: ImageVector, label: String, active: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.tvFocus(CircleShape, 3.dp, onClick).background(if (active) Color.White.copy(alpha = 0.16f) else Color.Transparent)
            .padding(horizontal = 18.tv(), vertical = 10.tv()),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Ico(icon, tint = if (active) Color.White else Color.White.copy(alpha = 0.7f), size = 24.tv())
        Spacer(Modifier.width(8.tv()))
        TvTxt(label, 17, color = if (active) Color.White else Color.White.copy(alpha = 0.7f), bold = active)
    }
}

@Composable
fun TvTile(title: String, sub: String, circle: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit, art: @Composable (Shape) -> Unit) {
    val shape: Shape = if (circle) CircleShape else RoundedCornerShape(10.tv())
    Column(modifier) {
        Box(Modifier.fillMaxWidth().aspectRatio(1f).tvFocus(shape, 3.dp, onClick)) { art(shape) }
        Spacer(Modifier.height(10.tv()))
        TvTxt(title, 18, serif = true)
        TvTxt(sub, 14, color = Color.White.copy(alpha = 0.55f))
    }
}

sealed class TvPage {
    class AlbumP(val album: Album) : TvPage()
    class ArtistP(val name: String) : TvPage()
    object Liked : TvPage()
    class PlaylistP(val id: Long) : TvPage()
}

// ---------------------------------------------------------------- barra in alto

@Composable
private fun TvPill(icon: ImageVector, label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.tvFocus(CircleShape, 3.dp, onClick).background(if (selected) Color(0xFF1E1E1E) else Color.Transparent)
            .padding(horizontal = 24.tv(), vertical = 13.tv()),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Ico(icon, tint = if (selected) Color.White else Color.White.copy(alpha = 0.5f), size = 26.tv())
        Spacer(Modifier.width(10.tv()))
        TvTxt(label, 18, color = if (selected) Color.White else Color.White.copy(alpha = 0.5f), bold = selected)
    }
}

@Composable
private fun TvTopBar(ctl: Ctl, screen: Int, onScreen: (Int) -> Unit, onPlayer: () -> Unit, onLogin: () -> Unit) {
    val s = ctl.currentSong
    Row(Modifier.fillMaxWidth().padding(horizontal = 48.tv(), vertical = 28.tv()), verticalAlignment = Alignment.CenterVertically) {
        Row(
            Modifier.width(340.tv()).tvFocus(RoundedCornerShape(14.tv()), 3.dp) { if (s != null) onPlayer() }.background(Color(0xFF1A1A1A)).padding(10.tv()),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.size(46.tv()).clip(RoundedCornerShape(8.tv())).background(Color(0xFF333333))) { if (s != null) Cover(s.art, Modifier.fillMaxSize()) }
            Spacer(Modifier.width(12.tv()))
            Column {
                TvTxt(s?.title ?: "Nessun brano in riproduzione", 15, bold = true)
                TvTxt(s?.artists?.joinToString(" / ") ?: "Scegli un brano per iniziare", 12, color = Color.White.copy(alpha = 0.5f))
            }
        }
        Spacer(Modifier.weight(1f))
        Row(horizontalArrangement = Arrangement.spacedBy(16.tv())) {
            TvPill(Icons.Rounded.Home, "Home", screen == 0) { onScreen(0) }
            TvPill(Icons.Rounded.Search, "Cerca", screen == 1) { onScreen(1) }
            TvPill(Icons.Rounded.FolderOpen, "Libreria", screen == 2) { onScreen(2) }
        }
        Spacer(Modifier.weight(1f))
        Row(Modifier.tvFocus(CircleShape, 3.dp, onLogin).padding(horizontal = 16.tv(), vertical = 9.tv()), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(34.tv()).clip(CircleShape).background(Color(0xFF0026FF)), contentAlignment = Alignment.Center) { Ico(Icons.Rounded.Person, tint = Color.White, size = 20.tv()) }
            Spacer(Modifier.width(10.tv()))
            TvTxt(ctl.jelly?.userName?.ifEmpty { "Profilo" } ?: "Accedi", 17)
        }
    }
}

// ---------------------------------------------------------------- home

@Composable
private fun TvHome(ctl: Ctl, openAlbum: (Album) -> Unit, openArtist: (String) -> Unit, openLogin: () -> Unit) {
    val day = remember { java.time.LocalDate.now().toEpochDay() }
    val albumOfDay = remember(ctl.albums, day) { ctl.albums.takeIf { it.isNotEmpty() }?.let { it[Random(day).nextInt(it.size)] } }
    val mix: List<Any> = remember(ctl.albums, ctl.artistMap, day) {
        val al = ctl.albums.shuffled(Random(day + 7)).take(7)
        val ar = ctl.artistMap.keys.filter { it != "Artista sconosciuto" }.shuffled(Random(day + 3)).take(3)
        buildList<Any> { addAll(al); addAll(ar) }.shuffled(Random(day))
    }
    val recent = remember(ctl.recent, ctl.byId, ctl.albums) {
        ctl.recent.mapNotNull { ctl.byId[it] }.distinctBy { it.albumId }.take(12)
    }
    val first = remember { FocusRequester() }
    LaunchedEffect(albumOfDay) { if (albumOfDay != null) { delay(200); runCatching { first.requestFocus() } } }
    if (ctl.songs.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                TvTxt(if (Jelly.busy) "Sincronizzazione con Jellyfin..." else "Nessuna musica da mostrare", 34, serif = true)
                Spacer(Modifier.height(10.tv()))
                TvTxt(Jelly.error ?: "Accedi al tuo server Jellyfin per ascoltare la tua musica.", 18, color = Color.White.copy(alpha = 0.55f))
                Spacer(Modifier.height(28.tv()))
                TvButton("Accedi a Jellyfin", Icons.Rounded.Person, true, Modifier.focusRequester(first), openLogin)
            }
        }
        return
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 60.tv())) {
        item { TvTxt("La tua musica del giorno", 24, Modifier.padding(horizontal = 48.tv(), vertical = 12.tv()), bold = true) }
        item {
            Row(Modifier.fillMaxWidth().padding(horizontal = 48.tv()), horizontalArrangement = Arrangement.spacedBy(24.tv())) {
                if (albumOfDay != null) {
                    Box(
                        Modifier.width(560.tv()).height(620.tv()).focusRequester(first).tvFocus(RoundedCornerShape(16.tv()), 3.dp) { openAlbum(albumOfDay) }
                    ) {
                        Cover(albumOfDay.songs[0].art, Modifier.fillMaxSize(), RoundedCornerShape(16.tv()))
                        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.4f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.85f))))
                        Row(Modifier.align(Alignment.TopStart).padding(22.tv()), verticalAlignment = Alignment.CenterVertically) {
                            Ico(Icons.Rounded.Star, tint = Color(0xFFFFC83D), size = 30.tv())
                            Spacer(Modifier.width(10.tv()))
                            TvTxt("Album del giorno", 20, serif = true)
                        }
                        Column(Modifier.align(Alignment.BottomStart).padding(28.tv()).fillMaxWidth(0.8f)) {
                            TvTxt(albumOfDay.title, 40, serif = true, maxLines = 2)
                            TvTxt("Album  \u2022  ${albumOfDay.artist}", 17, color = Color.White.copy(alpha = 0.7f))
                        }
                        Box(
                            Modifier.align(Alignment.BottomEnd).padding(24.tv()).size(76.tv()).clip(CircleShape).background(Color.White).clickable(
                                interactionSource = remember { MutableInteractionSource() }, indication = null
                            ) { ctl.play(albumOfDay.songs, 0) },
                            contentAlignment = Alignment.Center
                        ) { Ico(Icons.Rounded.PlayArrow, tint = Color.Black, size = 44.tv()) }
                    }
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(22.tv())) {
                    mix.chunked(5).take(2).forEach { row ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(20.tv())) {
                            row.forEach { item ->
                                if (item is Album) TvTile(item.title, "Album  \u2022  ${item.artist}", false, Modifier.weight(1f), { openAlbum(item) }) { sh ->
                                    Cover(item.songs[0].art, Modifier.fillMaxSize(), sh)
                                }
                                else if (item is String) TvTile(ctl.artistDisplay(item), "Artista", true, Modifier.weight(1f), { openArtist(item) }) { sh ->
                                    Cover(ctl.artistImage(item), Modifier.fillMaxSize(), sh)
                                }
                            }
                            repeat(5 - row.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
            }
        }
        if (recent.isNotEmpty()) {
            item { TvTxt("Ascoltati di recente", 24, Modifier.padding(horizontal = 48.tv(), vertical = 24.tv()), bold = true) }
            item {
                LazyRow(contentPadding = PaddingValues(horizontal = 48.tv()), horizontalArrangement = Arrangement.spacedBy(20.tv())) {
                    items(recent) { s ->
                        val al = ctl.albums.firstOrNull { it.id == s.albumId }
                        TvTile(s.title, s.artists.joinToString(" / "), false, Modifier.width(230.tv()), { if (al != null) openAlbum(al) else ctl.play(listOf(s), 0) }) { sh ->
                            Cover(s.art, Modifier.fillMaxSize(), sh)
                        }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- libreria

@Composable
private fun TvLibrary(ctl: Ctl, openAlbum: (Album) -> Unit, openArtist: (String) -> Unit, openLiked: () -> Unit, openPlaylist: (Long) -> Unit) {
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { delay(200); runCatching { first.requestFocus() } }
    val liked = ctl.likes.count { it in ctl.byId }
    LazyVerticalGrid(
        GridCells.Fixed(5), Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 48.tv(), end = 48.tv(), top = 12.tv(), bottom = 60.tv()),
        horizontalArrangement = Arrangement.spacedBy(24.tv()), verticalArrangement = Arrangement.spacedBy(28.tv())
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) { TvTxt("La tua libreria", 64, Modifier.padding(vertical = 24.tv()), serif = true) }
        item {
            TvTile("Brani preferiti", "$liked brani", false, Modifier.focusRequester(first), openLiked) { sh ->
                Box(Modifier.fillMaxSize().clip(sh).background(Brush.linearGradient(listOf(Color(0xFF1E3CFF), Color(0xFFB04BFF)))), contentAlignment = Alignment.Center) {
                    Ico(Icons.Rounded.Favorite, tint = Color.White, size = 120.tv())
                }
            }
        }
        gridItems(ctl.playlists) { pl ->
            TvTile(pl.name, "Playlist", false, Modifier, { openPlaylist(pl.id) }) { sh ->
                val c = playlistCover(pl)
                if (c != null) Cover(c, Modifier.fillMaxSize(), sh) else Mosaic(ctl.playlistSongs(pl).map { it.art }, Modifier.fillMaxSize())
            }
        }
        gridItems(ctl.albums) { a ->
            TvTile(a.title, "Album  \u2022  ${a.artist}", false, Modifier, { openAlbum(a) }) { sh -> Cover(a.songs[0].art, Modifier.fillMaxSize(), sh) }
        }
        gridItems(ctl.artistMap.keys.toList()) { n ->
            TvTile(ctl.artistDisplay(n), "Artista", true, Modifier, { openArtist(n) }) { sh -> Cover(ctl.artistImage(n), Modifier.fillMaxSize(), sh) }
        }
    }
}

// ---------------------------------------------------------------- cerca

@Composable
private fun TvSearch(ctl: Ctl, openAlbum: (Album) -> Unit) {
    var q by remember { mutableStateOf("") }
    val fr = remember { FocusRequester() }
    LaunchedEffect(Unit) { delay(250); runCatching { fr.requestFocus() } }
    val key = q.trim().lowercase()
    val songs = remember(key, ctl.songs) { if (key.isEmpty()) emptyList() else ctl.songs.filter { it.title.lowercase().contains(key) || it.artist.lowercase().contains(key) || it.album.lowercase().contains(key) }.take(60) }
    val albums = remember(key, ctl.albums) { if (key.isEmpty()) emptyList() else ctl.albums.filter { it.title.lowercase().contains(key) || it.artist.lowercase().contains(key) } }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 48.tv(), vertical = 12.tv())) {
        item { TvField(q, "Cerca brani, album, artisti", { q = it }, false, Modifier.focusRequester(fr)) }
        if (albums.isNotEmpty()) {
            item { TvTxt("Album", 22, Modifier.padding(vertical = 16.tv()), bold = true) }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(20.tv())) {
                    items(albums) { a -> TvTile(a.title, a.artist, false, Modifier.width(220.tv()), { openAlbum(a) }) { sh -> Cover(a.songs[0].art, Modifier.fillMaxSize(), sh) } }
                }
            }
        }
        if (songs.isNotEmpty()) {
            item { TvTxt("Brani", 22, Modifier.padding(vertical = 16.tv()), bold = true) }
            itemsIndexed(songs) { i, _ -> TvTrackRow(ctl, songs, i, null) }
        }
        if (key.isNotEmpty() && songs.isEmpty() && albums.isEmpty()) item { TvTxt("Nessun risultato", 22, Modifier.padding(top = 32.tv()), color = Color.White.copy(alpha = 0.55f)) }
    }
}

@Composable
fun TvField(value: String, hint: String, onChange: (String) -> Unit, password: Boolean, modifier: Modifier = Modifier) {
    var f by remember { mutableStateOf(false) }
    val kb = LocalSoftwareKeyboardController.current
    val shape = RoundedCornerShape(12.tv())
    Box(
        Modifier.fillMaxWidth().clip(shape).background(Color(0xFF1C1C1C)).border(if (f) 3.dp else 0.dp, Color.White, shape)
            .padding(horizontal = 22.tv(), vertical = 16.tv())
    ) {
        BasicTextField(
            value, onChange, singleLine = true,
            textStyle = TextStyle(fontFamily = UrbFont, fontWeight = FontWeight.Medium, fontSize = 22.tsp(), color = Color.White),
            cursorBrush = SolidColor(Color.White),
            visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
            modifier = modifier.fillMaxWidth().onFocusChanged { f = it.isFocused }
                .onPreviewKeyEvent { e -> if (e.type == KeyEventType.KeyUp && (e.key == Key.DirectionCenter || e.key == Key.Enter)) { kb?.show(); true } else false },
            decorationBox = { inner -> Box { if (value.isEmpty()) TvTxt(hint, 20, color = Color.White.copy(alpha = 0.4f)); inner() } }
        )
    }
}

// ---------------------------------------------------------------- dettaglio (album, artista, playlist, preferiti)

@Composable
private fun TvTrackRow(ctl: Ctl, list: List<Song>, i: Int, number: Int?) {
    val s = list[i]
    val cur = ctl.currentSong?.id == s.id
    Row(
        Modifier.fillMaxWidth().tvFocus(RoundedCornerShape(12.tv()), 3.dp) { ctl.play(list, i) }.padding(horizontal = 18.tv(), vertical = 12.tv()),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (number != null) TvTxt("$number", 26, Modifier.width(56.tv()), color = if (cur) Color(0xFF7388FF) else Color.White, bold = true)
        else { Box(Modifier.size(56.tv()).clip(RoundedCornerShape(8.tv()))) { Cover(s.art, Modifier.fillMaxSize()) }; Spacer(Modifier.width(18.tv())) }
        Column(Modifier.weight(1f)) {
            TvTxt(s.title, 22, color = if (cur) Color(0xFF7388FF) else Color.White, bold = true)
            TvTxt(s.artists.joinToString(" / "), 16, color = Color.White.copy(alpha = 0.55f))
        }
        TvTxt(fmt(s.duration), 17, color = Color.White.copy(alpha = 0.55f))
    }
}

@Composable
private fun TvDetail(
    title: String, sub: String, art: android.net.Uri?, heart: Boolean, songs: List<Song>, numbered: Boolean, ctl: Ctl,
    favState: Boolean?, onFav: () -> Unit
) {
    val tone by animateColorAsState(rememberCoverColor(art ?: songs.firstOrNull()?.art, Color(0xFF222222)), tween(500), label = "tone")
    val playFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { delay(250); runCatching { playFocus.requestFocus() } }
    Box(Modifier.fillMaxSize().background(Color(0xFF0A0A0A))) {
        Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(0f to tone.copy(alpha = 0.75f), 0.55f to Color(0xFF0A0A0A), 1f to Color(0xFF0A0A0A))))
        Row(Modifier.fillMaxSize().padding(horizontal = 80.tv(), vertical = 70.tv()), horizontalArrangement = Arrangement.spacedBy(70.tv())) {
            Column(Modifier.width(560.tv())) {
                Box(Modifier.size(520.tv())) {
                    when {
                        art != null -> Cover(art, Modifier.fillMaxSize(), RoundedCornerShape(16.tv()))
                        heart -> Box(Modifier.fillMaxSize().clip(RoundedCornerShape(16.tv())).background(Brush.linearGradient(listOf(Color(0xFF1E3CFF), Color(0xFFB04BFF)))), contentAlignment = Alignment.Center) {
                            Ico(Icons.Rounded.Favorite, tint = Color.White, size = 150.tv())
                        }
                        else -> Mosaic(songs.map { it.art }, Modifier.fillMaxSize())
                    }
                }
                Spacer(Modifier.height(26.tv()))
                TvTxt(title, 44, serif = true, maxLines = 2)
                Spacer(Modifier.height(6.tv()))
                TvTxt(sub, 18, color = Color.White.copy(alpha = 0.6f), maxLines = 2)
                Spacer(Modifier.height(26.tv()))
                Row(horizontalArrangement = Arrangement.spacedBy(16.tv())) {
                    TvButton("Riproduci", Icons.Rounded.PlayArrow, true, Modifier.focusRequester(playFocus)) { if (songs.isNotEmpty()) ctl.play(songs, 0) }
                    if (favState != null) TvButton(if (favState) "Preferito" else "Preferiti", if (favState) Icons.Rounded.Star else Icons.Rounded.StarBorder, false, onClick = onFav)
                    else TvButton("Casuale", Icons.Rounded.Shuffle, false) { if (songs.isNotEmpty()) ctl.playShuffled(songs) }
                }
            }
            LazyColumn(Modifier.weight(1f).fillMaxHeight(), contentPadding = PaddingValues(vertical = 8.tv())) {
                if (songs.isEmpty()) item { TvTxt("Ancora nessun brano.", 22, color = Color.White.copy(alpha = 0.55f)) }
                itemsIndexed(songs) { i, _ -> TvTrackRow(ctl, songs, i, if (numbered) i + 1 else null) }
            }
        }
    }
}

// ---------------------------------------------------------------- player

@Composable
private fun TvQueue(ctl: Ctl) {
    val upcoming = ctl.queue.withIndex().filter { it.index > ctl.current }
    Column(Modifier.fillMaxSize().padding(start = 40.tv())) {
        TvTxt("Prossimi brani", 28, Modifier.padding(bottom = 14.tv()), bold = true)
        LazyColumn(Modifier.fillMaxSize()) {
            if (upcoming.isEmpty()) item { TvTxt("Nessun altro brano in coda.", 20, color = Color.White.copy(alpha = 0.6f)) }
            items(upcoming) { iv ->
                Row(
                    Modifier.fillMaxWidth().tvFocus(RoundedCornerShape(12.tv()), 3.dp) { ctl.play(ctl.queue, iv.index) }.padding(12.tv()),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(Modifier.size(60.tv()).clip(RoundedCornerShape(8.tv()))) { Cover(iv.value.art, Modifier.fillMaxSize()) }
                    Spacer(Modifier.width(16.tv()))
                    Column {
                        TvTxt(iv.value.title, 21, bold = true)
                        TvTxt(iv.value.artists.joinToString(" / "), 15, color = Color.White.copy(alpha = 0.6f))
                    }
                }
            }
        }
    }
}

@Composable
private fun TvStepper(label: String, value: Float, onStep: (Float) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        TvTxt(label, 22, Modifier.width(220.tv()), bold = true)
        TvRound(Icons.Rounded.Remove, 58, false) { onStep(-0.05f) }
        TvTxt("%.2fx".format(value), 24, Modifier.width(130.tv()), align = TextAlign.Center)
        TvRound(Icons.Rounded.Add, 58, false) { onStep(0.05f) }
    }
}

@Composable
private fun TvMore(ctl: Ctl) {
    Column(Modifier.fillMaxSize().padding(start = 40.tv()), verticalArrangement = Arrangement.spacedBy(22.tv())) {
        TvTxt("Velocit\u00E0 e pitch", 28, bold = true)
        TvStepper(if (ctl.linked) "Velocit\u00E0 e pitch" else "Velocit\u00E0", ctl.speed) { d ->
            val v = (ctl.speed + d).coerceIn(0.5f, 2f)
            if (ctl.linked) ctl.setParams(v, v) else ctl.setParams(v, ctl.pitch)
        }
        if (!ctl.linked) TvStepper("Pitch", ctl.pitch) { d -> ctl.setParams(ctl.speed, (ctl.pitch + d).coerceIn(0.5f, 2f)) }
        Row(horizontalArrangement = Arrangement.spacedBy(16.tv())) {
            TvButton(if (ctl.linked) "Separa" else "Unisci", if (ctl.linked) Icons.Rounded.LinkOff else Icons.Rounded.Link, true) {
                val l = !ctl.linked
                ctl.linked = l
                if (l) ctl.setParams(ctl.speed, ctl.speed)
            }
            TvButton("Reset", null, false) { ctl.setParams(1f, 1f) }
        }
    }
}

@Composable
private fun TvProgress(ctl: Ctl, s: Song) {
    var f by remember { mutableStateOf(false) }
    val frac = if (s.duration > 0) (ctl.pos.toFloat() / s.duration).coerceIn(0f, 1f) else 0f
    val h by animateFloatAsState(if (f) 12f else 6f, label = "ph")
    Box(
        Modifier.fillMaxWidth().height(28.tv()).onFocusChanged { f = it.isFocused }
            .onPreviewKeyEvent { e ->
                if (e.type == KeyEventType.KeyDown && (e.key == Key.DirectionLeft || e.key == Key.DirectionRight)) {
                    val d = if (e.key == Key.DirectionLeft) -10_000L else 10_000L
                    ctl.seek((ctl.pos + d).coerceIn(0L, s.duration)); true
                } else false
            }.focusable(),
        contentAlignment = Alignment.CenterStart
    ) {
        Box(Modifier.fillMaxWidth().height((h * LocalTvU.current * 2f).dp).clip(CircleShape).background(Color.White.copy(alpha = 0.25f))) {
            Box(Modifier.fillMaxWidth(frac).fillMaxHeight().background(Color.White))
        }
    }
}

@Composable
private fun TvPlayer(ctl: Ctl, close: () -> Unit) {
    val s = ctl.currentSong
    if (s == null) { LaunchedEffect(Unit) { close() }; return }
    var showLyrics by remember { mutableStateOf(true) }
    var panel by remember { mutableIntStateOf(0) }
    var controls by remember { mutableStateOf(true) }
    var lastInput by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    val playFocus = remember { FocusRequester() }
    LaunchedEffect(lastInput, panel) { delay(7000); if (panel == 0) controls = false }
    LaunchedEffect(controls) { if (controls) { delay(200); runCatching { playFocus.requestFocus() } } }
    BackHandler(true) { if (panel != 0) panel = 0 else close() }
    val split = showLyrics || panel != 0
    Box(Modifier.fillMaxSize().background(Color.Black).onPreviewKeyEvent { lastInput = SystemClock.elapsedRealtime(); controls = true; false }) {
        BlurBg(s.art)
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.5f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.7f))))
        Row(
            Modifier.fillMaxSize().padding(start = 64.tv(), end = 64.tv(), top = 56.tv(), bottom = if (controls) 300.tv() else 56.tv()),
            horizontalArrangement = Arrangement.spacedBy(40.tv())
        ) {
            Column(Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = if (split) Alignment.Start else Alignment.CenterHorizontally) {
                TvTxt(s.title, 36, bold = true, maxLines = 2, align = if (split) TextAlign.Start else TextAlign.Center, modifier = Modifier.fillMaxWidth())
                TvTxt(s.artists.joinToString(" / "), 20, Modifier.fillMaxWidth(), Color.White.copy(alpha = 0.65f), align = if (split) TextAlign.Start else TextAlign.Center)
                Spacer(Modifier.height(18.tv()))
                Box(Modifier.weight(1f).fillMaxWidth()) { CoverPage(ctl, s) }
            }
            if (split) Box(Modifier.weight(1.1f).fillMaxHeight()) {
                when (panel) {
                    1 -> TvQueue(ctl)
                    2 -> TvMore(ctl)
                    else -> LyricsPage(ctl, s, true, false)
                }
            }
        }
        FadeIn(controls) {
            Box(Modifier.fillMaxSize()) {
                Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = 64.tv(), vertical = 34.tv())) {
                    TvProgress(ctl, s)
                    Row(Modifier.fillMaxWidth()) {
                        TvTxt(fmt(ctl.pos), 15, color = Color.White.copy(alpha = 0.7f))
                        Spacer(Modifier.weight(1f))
                        TvTxt(fmt(s.duration), 15, color = Color.White.copy(alpha = 0.7f))
                    }
                    Spacer(Modifier.height(10.tv()))
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(8.tv())) {
                            TvTextBtn(if (ctl.repeat == androidx.media3.common.Player.REPEAT_MODE_ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat, "Ripeti", ctl.repeat != androidx.media3.common.Player.REPEAT_MODE_OFF) { ctl.cycleRepeat() }
                            TvTextBtn(Icons.Rounded.Shuffle, "Casuale", ctl.shuffle) { ctl.toggleShuffle() }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(22.tv()), verticalAlignment = Alignment.CenterVertically) {
                            TvRound(Icons.Rounded.SkipPrevious, 76, false) { ctl.prev() }
                            TvRound(if (ctl.playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, 96, true, Modifier.focusRequester(playFocus)) { ctl.toggle() }
                            TvRound(Icons.Rounded.SkipNext, 76, false) { ctl.next() }
                        }
                        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(14.tv(), Alignment.End), verticalAlignment = Alignment.CenterVertically) {
                            val liked = s.id in ctl.likes
                            TvRound(if (liked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, 66, liked, tint = if (liked) Color(0xFFE0245E) else Color.White) { ctl.toggleLike(s.id) }
                            TvTextBtn(Icons.Rounded.Notes, "Testi", showLyrics && panel == 0) { showLyrics = !showLyrics; panel = 0 }
                        }
                    }
                    Row(Modifier.fillMaxWidth().padding(top = 8.tv()), horizontalArrangement = Arrangement.spacedBy(8.tv(), Alignment.End)) {
                        TvTextBtn(Icons.Rounded.FormatListBulleted, "Prossimi brani", panel == 1) { panel = if (panel == 1) 0 else 1 }
                        TvTextBtn(Icons.Rounded.MoreVert, "Altro", panel == 2) { panel = if (panel == 2) 0 else 2 }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- accesso a Jellyfin

@Composable
private fun TvLogin(ctl: Ctl, onClose: () -> Unit) {
    val scope = rememberCoroutineScope()
    var url by remember { mutableStateOf("") }
    var user by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    var err by remember { mutableStateOf<String?>(null) }
    var working by remember { mutableStateOf(false) }
    var servers by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    var scanning by remember { mutableStateOf(false) }
    var qc by remember { mutableStateOf<Jelly.QuickCode?>(null) }
    val sess = ctl.jelly
    fun scan() {
        scope.launch {
            scanning = true
            servers = Jelly.discover()
            scanning = false
            if (servers.isNotEmpty() && url.isEmpty()) url = servers[0].second
        }
    }
    fun quick() {
        scope.launch {
            err = null
            val did = ctl.jellyDeviceId()
            val r = Jelly.quickInitiate(url, did)
            val q = r.getOrNull()
            if (q == null) { err = r.exceptionOrNull()?.message; return@launch }
            qc = q
            var tries = 0
            while (tries < 150 && qc != null) {
                delay(2000); tries++
                if (Jelly.quickAuthenticated(url, did, q.secret)) {
                    val lr = Jelly.quickLogin(url, did, q.secret)
                    val sn = lr.getOrNull()
                    qc = null
                    if (sn != null) { ctl.jellyAccept(sn); onClose() } else err = lr.exceptionOrNull()?.message
                    return@launch
                }
            }
            qc = null
            err = "Codice scaduto. Riprova."
        }
    }
    LaunchedEffect(Unit) { if (sess == null) scan() }
    val first = remember { FocusRequester() }
    LaunchedEffect(sess) { delay(250); runCatching { first.requestFocus() } }
    Box(Modifier.fillMaxSize().background(Color(0xF00A0A0A)).focusable(), contentAlignment = Alignment.Center) {
        Column(
            Modifier.width(1000.tv()).clip(RoundedCornerShape(24.tv())).background(Color(0xFF141414)).padding(44.tv()),
            verticalArrangement = Arrangement.spacedBy(16.tv())
        ) {
            TvTxt(if (sess != null) "Profilo Jellyfin" else "Accedi a Jellyfin", 40, serif = true)
            if (sess != null) {
                TvTxt("${sess.userName.ifEmpty { "Utente" }}  \u2022  ${sess.url}", 20, color = Color.White.copy(alpha = 0.7f))
                TvTxt(
                    if (Jelly.busy) "Sincronizzazione in corso..." else (Jelly.error ?: "${ctl.songs.size} brani  \u2022  ${ctl.playlists.size} playlist"),
                    18, color = Color.White.copy(alpha = 0.55f)
                )
                Row(horizontalArrangement = Arrangement.spacedBy(16.tv())) {
                    TvButton("Sincronizza ora", Icons.Rounded.Sync, true, Modifier.focusRequester(first)) { if (!Jelly.busy) scope.launch { ctl.syncJelly() } }
                    TvButton("Esci dal profilo", Icons.Rounded.Warning, false) { ctl.jellyLogout(); onClose() }
                    TvButton("Chiudi", null, false, onClick = onClose)
                }
            } else {
                if (servers.isNotEmpty()) {
                    TvTxt("Server trovati sulla rete", 20, bold = true)
                    servers.forEach { (name, addr) ->
                        Row(
                            Modifier.fillMaxWidth().tvFocus(RoundedCornerShape(12.tv()), 3.dp) { url = addr }.background(Color(0xFF1E1E1E)).padding(18.tv()),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Ico(Icons.Rounded.Storage, tint = Color.White, size = 28.tv())
                            Spacer(Modifier.width(16.tv()))
                            Column { TvTxt(name, 20, bold = true); TvTxt(addr, 16, color = Color.White.copy(alpha = 0.6f)) }
                        }
                    }
                } else TvTxt(if (scanning) "Cerco il server sulla rete..." else "Nessun server trovato: scrivi l'indirizzo qui sotto.", 18, color = Color.White.copy(alpha = 0.6f))
                TvField(url, "Indirizzo del server (es. 192.168.1.20:8096)", { url = it }, false, Modifier.focusRequester(first))
                val code = qc
                if (code != null) {
                    TvTxt("Su un altro dispositivo apri Jellyfin, vai in Impostazioni \u2192 Quick Connect e inserisci:", 18, color = Color.White.copy(alpha = 0.7f), maxLines = 2)
                    TvTxt(code.code, 64, bold = true)
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(16.tv())) {
                        TvButton("Quick Connect", Icons.Rounded.Sync, true) { quick() }
                        TvButton("Cerca server", Icons.Rounded.Search, false) { if (!scanning) scan() }
                    }
                    TvTxt("Oppure accedi con utente e password:", 18, Modifier.padding(top = 8.tv()), color = Color.White.copy(alpha = 0.6f))
                    TvField(user, "Nome utente", { user = it }, false)
                    TvField(pass, "Password", { pass = it }, true)
                    TvButton(if (working) "Accesso in corso..." else "Accedi", null, false) {
                        if (!working) {
                            working = true; err = null
                            scope.launch {
                                val r = ctl.jellyLogin(url, user.trim(), pass)
                                working = false
                                if (r == null) onClose() else err = r
                            }
                        }
                    }
                }
                if (err != null) TvTxt(err!!, 18, color = Color(0xFFFF6B6B), maxLines = 3)
                TvButton("Annulla", null, false, onClick = onClose)
            }
        }
    }
}

// ---------------------------------------------------------------- radice dell'app per TV

@Composable
fun TvApp(ctl: Ctl) {
    val scope = rememberCoroutineScope()
    var screen by remember { mutableIntStateOf(0) }
    var pages by remember { mutableStateOf(listOf<TvPage>()) }
    var playerOpen by remember { mutableStateOf(false) }
    var loginOpen by remember { mutableStateOf(false) }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { scope.launch { ctl.scan() } }
    LaunchedEffect(Unit) {
        val p = if (Build.VERSION.SDK_INT >= 33) arrayOf(Manifest.permission.READ_MEDIA_AUDIO, Manifest.permission.POST_NOTIFICATIONS)
        else arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        launcher.launch(p)
    }
    LaunchedEffect(Unit) { while (true) { ctl.refreshPos(); delay(500) } }

    BackHandler(pages.isNotEmpty()) { pages = pages.dropLast(1) }
    BackHandler(loginOpen) { loginOpen = false }
    BackHandler(playerOpen) { playerOpen = false }

    AppTheme(true, Color.Unspecified, false, true) {
        BoxWithConstraints(Modifier.fillMaxSize().background(Color(0xFF0A0A0A))) {
            CompositionLocalProvider(LocalTvU provides (maxWidth.value / 1920f)) {
                Column(Modifier.fillMaxSize()) {
                    TvTopBar(ctl, screen, { screen = it; pages = emptyList() }, { playerOpen = true }, { loginOpen = true })
                    Box(Modifier.weight(1f).fillMaxWidth()) {
                        AnimatedContent(screen, transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(120)) }, label = "tvscreen") { sc ->
                            when (sc) {
                                0 -> TvHome(ctl, { pages = pages + TvPage.AlbumP(it) }, { pages = pages + TvPage.ArtistP(it) }, { loginOpen = true })
                                1 -> TvSearch(ctl) { pages = pages + TvPage.AlbumP(it) }
                                else -> TvLibrary(
                                    ctl, { pages = pages + TvPage.AlbumP(it) }, { pages = pages + TvPage.ArtistP(it) },
                                    { pages = pages + TvPage.Liked }, { pages = pages + TvPage.PlaylistP(it) }
                                )
                            }
                        }
                    }
                }
                AnimatedContent(pages.lastOrNull(), transitionSpec = { fadeIn(tween(250)) togetherWith fadeOut(tween(180)) }, label = "tvpages") { pg ->
                    when (pg) {
                        is TvPage.AlbumP -> TvDetail(pg.album.title, "Album  \u2022  ${pg.album.artist}  \u2022  ${pg.album.songs.size} brani", pg.album.songs[0].art, false, pg.album.songs, true, ctl, null) { }
                        is TvPage.ArtistP -> {
                            val l = ctl.artistMap[pg.name] ?: emptyList()
                            TvDetail(ctl.artistDisplay(pg.name), "${l.size} brani", ctl.artistImage(pg.name), false, l, false, ctl, pg.name in ctl.favArtists) { ctl.toggleFavArtist(pg.name) }
                        }
                        is TvPage.Liked -> TvDetail("Brani preferiti", "Ci\u00F2 che ti piace davvero", null, true, ctl.songs.filter { it.id in ctl.likes }, false, ctl, null) { }
                        is TvPage.PlaylistP -> {
                            val pl = ctl.playlists.firstOrNull { it.id == pg.id }
                            if (pl != null) TvDetail(pl.name, "Playlist", playlistCover(pl), false, ctl.playlistSongs(pl), false, ctl, null) { }
                            else Box(Modifier.fillMaxSize())
                        }
                        null -> Box(Modifier.fillMaxSize())
                    }
                }
                FadeIn(playerOpen) { TvPlayer(ctl) { playerOpen = false } }
                FadeIn(loginOpen) { TvLogin(ctl) { loginOpen = false } }
            }
        }
    }
}
