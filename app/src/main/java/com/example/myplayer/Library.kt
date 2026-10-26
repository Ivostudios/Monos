package com.example.myplayer

import android.net.Uri
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
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.File

@Composable
fun FolderCover(albums: List<Album>, modifier: Modifier) {
    val p = pal
    BoxWithConstraints(modifier.clip(CoverShape).background(p.card)) {
        val pad = maxWidth * 0.08f
        val gap = maxWidth * 0.06f
        val cell = (maxWidth - pad * 2 - gap) / 2
        albums.take(4).forEachIndexed { i, a ->
            Cover(a.songs[0].art, Modifier.size(cell).offset(pad + (cell + gap) * (i % 2), pad + (cell + gap) * (i / 2)))
        }
    }
}

fun playlistCover(p: Playlist): Uri? = p.cover?.let { Uri.fromFile(File(it)) }

@Composable
fun GridTile(
    key: String, title: String, sub: String, hot: Boolean, hidden: Boolean, bounds: MutableMap<String, Rect>,
    dragModifier: Modifier, onClick: () -> Unit, art: @Composable () -> Unit
) {
    val p = pal
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    val sc by animateFloatAsState(if (shown) 1f else 0.7f, spring(0.6f, Spring.StiffnessMediumLow), label = "tile")
    val hs by animateFloatAsState(if (hot) 1.07f else 1f, spring(0.6f), label = "hot")
    Column(
        Modifier.onGloballyPositioned { bounds[key] = it.boundsInRoot() }
            .graphicsLayer { scaleX = sc * hs; scaleY = sc * hs; alpha = if (hidden) 0f else sc.coerceIn(0f, 1f) }
            .pressable(0.96f, onClick).then(dragModifier)
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(1f)) { art() }
        Spacer(Modifier.height(8.dp))
        Txt(title, style = TS.tile, maxLines = 2)
        Txt(sub, style = TS.small, color = p.sub, maxLines = 1)
    }
}

@Composable
fun SimpleTile(title: String, sub: String, onClick: () -> Unit, art: @Composable () -> Unit) {
    val p = pal
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    val sc by animateFloatAsState(if (shown) 1f else 0.7f, spring(0.6f, Spring.StiffnessMediumLow), label = "tile")
    Column(Modifier.graphicsLayer { scaleX = sc; scaleY = sc; alpha = sc.coerceIn(0f, 1f) }.pressable(0.96f, onClick)) {
        Box(Modifier.fillMaxWidth().aspectRatio(1f)) { art() }
        Spacer(Modifier.height(8.dp))
        Txt(title, style = TS.tile, maxLines = 2)
        Txt(sub, style = TS.small, color = p.sub, maxLines = 1)
    }
}

// ---------------------------------------------------------------- Playlist

@Composable
fun PlaylistScreen(
    ctl: Ctl, openLiked: () -> Unit, openPlaylist: (Long) -> Unit, onCreate: () -> Unit, onSearch: () -> Unit, onSettings: () -> Unit
) {
    val p = pal
    var grid by rememberSaveable { mutableStateOf(true) }
    val likedCount = ctl.likes.count { it in ctl.byId }
    val entries = remember(ctl.playlists) { listOf<Any>("liked") + ctl.playlists }
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            PageHeader("Playlist", onSearch, onSettings)
            Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Txt("Ultime create", Modifier.weight(1f), TS.label, p.sub)
                IconBtn(Icons.Rounded.ViewList, { grid = false }, tint = if (!grid) p.fg else p.sub, box = 40.dp)
                IconBtn(Icons.Rounded.GridView, { grid = true }, tint = if (grid) p.fg else p.sub, box = 40.dp)
            }
            if (grid) {
                LazyVerticalGrid(
                    GridCells.Adaptive(150.dp), Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 100.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    gridItems(entries) { e ->
                        if (e is Playlist) {
                            val covers = ctl.playlistSongs(e).map { it.art }
                            SimpleTile(e.name, "Playlist", { openPlaylist(e.id) }) {
                                val c = playlistCover(e)
                                if (c != null) Cover(c, Modifier.fillMaxSize()) else Mosaic(covers, Modifier.fillMaxSize())
                            }
                        } else SimpleTile("Brani preferiti", "$likedCount brani  \u2022  Ci\u00F2 che ti piace davvero", openLiked) { HeartCover(Modifier.fillMaxSize(), 64.dp) }
                    }
                }
            } else {
                LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 100.dp)) {
                    items(entries) { e ->
                        val pl = e as? Playlist
                        Row(
                            Modifier.fillMaxWidth().pressable(0.98f) { if (pl != null) openPlaylist(pl.id) else openLiked() }.padding(horizontal = 16.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(Modifier.size(56.dp)) {
                                if (pl == null) HeartCover(Modifier.fillMaxSize(), 26.dp)
                                else {
                                    val c = playlistCover(pl)
                                    if (c != null) Cover(c, Modifier.fillMaxSize()) else Mosaic(ctl.playlistSongs(pl).map { it.art }, Modifier.fillMaxSize())
                                }
                            }
                            Spacer(Modifier.width(16.dp))
                            Column(Modifier.weight(1f)) {
                                Txt(pl?.name ?: "Brani preferiti", style = TS.tile, maxLines = 1)
                                Txt(if (pl == null) "$likedCount brani" else "Playlist  \u2022  ${pl.songIds.size} brani", style = TS.small, color = p.sub, maxLines = 1)
                            }
                        }
                    }
                }
            }
        }
        Row(
            Modifier.align(Alignment.BottomCenter).padding(16.dp).fillMaxWidth().height(54.dp).pressable(0.97f, onCreate)
                .shadow(14.dp, CircleShape).clip(CircleShape).background(p.accent),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center
        ) {
            Txt("Nuova playlist", style = TS.title, color = p.onAccent)
            Spacer(Modifier.width(10.dp))
            Ico(Icons.Rounded.AddBox, tint = p.onAccent, size = 22.dp)
        }
    }
}

// ---------------------------------------------------------------- Libreria

@Composable
fun LibraryHub(
    ctl: Ctl, section: Int, onSection: (Int) -> Unit, showTabs: Boolean,
    openAlbum: (Album) -> Unit, openFolder: (Long) -> Unit, openArtist: (String) -> Unit,
    onSearch: () -> Unit, onSettings: () -> Unit
) {
    val p = pal
    var grid by rememberSaveable { mutableStateOf(true) }
    Column(Modifier.fillMaxSize()) {
        PageHeader(if (showTabs) "Libreria" else listOf("Brani", "Album", "Artisti")[section], onSearch, onSettings)
        if (showTabs) Box(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) { Segmented(listOf("Brani", "Album", "Artisti"), section) { onSection(it) } }
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Txt("Aggiunti di recente", Modifier.weight(1f), TS.label, p.sub)
            if (section != 0) {
                IconBtn(Icons.Rounded.ViewList, { grid = false }, tint = if (!grid) p.fg else p.sub, box = 40.dp)
                IconBtn(Icons.Rounded.GridView, { grid = true }, tint = if (grid) p.fg else p.sub, box = 40.dp)
            } else Spacer(Modifier.height(40.dp))
        }
        if (ctl.songs.isEmpty()) Empty()
        else AnimatedContent(section, transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(120)) }, label = "libsec") { s ->
            when (s) {
                0 -> {
                    val list = remember(ctl.songs) { ctl.songs.sortedByDescending { it.added } }
                    LazyColumn(Modifier.fillMaxSize()) { itemsIndexed(list) { i, _ -> TrackRow(ctl, list, i) } }
                }
                1 -> AlbumsSection(ctl, grid, openAlbum, openFolder)
                else -> ArtistsSection(ctl, grid, openArtist)
            }
        }
    }
}

@Composable
fun ArtistsSection(ctl: Ctl, grid: Boolean, openArtist: (String) -> Unit) {
    val p = pal
    val quick = LocalQuick.current
    val names = ctl.artistMap.keys.toList()
    @Composable
    fun Avatar(n: String, modifier: Modifier) {
        Box(modifier) {
            Cover(ctl.artistImage(n), Modifier.fillMaxSize(), CircleShape)
            if (n in ctl.favArtists) Box(Modifier.align(Alignment.BottomEnd).padding(2.dp).size(26.dp).clip(CircleShape).background(p.bg), contentAlignment = Alignment.Center) {
                Ico(Icons.Rounded.Star, tint = Color(0xFFFFB300), size = 20.dp)
            }
        }
    }
    if (grid) {
        LazyVerticalGrid(
            GridCells.Adaptive(100.dp), Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            gridItems(names) { n ->
                Column(
                    Modifier.pressableLong(0.94f, { quick(Quick.ArtistQ(n)) }, { openArtist(n) }),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Avatar(n, Modifier.fillMaxWidth().aspectRatio(1f))
                    Spacer(Modifier.height(6.dp))
                    Txt(ctl.artistDisplay(n), style = TS.tile.copy(fontSize = 14.sp), maxLines = 2, align = TextAlign.Center)
                }
            }
        }
    } else {
        LazyColumn(Modifier.fillMaxSize()) {
            items(names) { n ->
                Row(
                    Modifier.fillMaxWidth().pressableLong(0.98f, { quick(Quick.ArtistQ(n)) }, { openArtist(n) }).padding(horizontal = 16.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Avatar(n, Modifier.size(56.dp))
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Txt(ctl.artistDisplay(n), style = TS.tile, maxLines = 1)
                        Txt("${ctl.artistMap[n]?.size ?: 0} brani", style = TS.small, color = p.sub)
                    }
                }
            }
        }
    }
}

@Composable
fun AlbumsSection(ctl: Ctl, grid: Boolean, openAlbum: (Album) -> Unit, openFolder: (Long) -> Unit) {
    val p = pal
    val inFolder = ctl.folders.flatMap { it.albumIds }.toSet()
    val albumsById = ctl.albums.associateBy { it.id }
    // 3 = cartella, 2 = album
    val entries = remember(ctl.albums, ctl.folders) {
        buildList<Pair<Int, Any>> {
            ctl.folders.forEach { add(3 to it) }
            ctl.albums.filter { it.id !in inFolder }.forEach { add(2 to it) }
        }
    }
    val bounds = remember { mutableStateMapOf<String, Rect>() }
    var dragAlbum by remember { mutableStateOf<Album?>(null) }
    var finger by remember { mutableStateOf(Offset.Zero) }
    var origin by remember { mutableStateOf(Offset.Zero) }
    val hover = dragAlbum?.let { da -> bounds.entries.firstOrNull { (k, r) -> k != "a:${da.id}" && r.contains(finger) }?.key }
    val hoverNow = rememberUpdatedState(hover)
    val dim by animateFloatAsState(if (dragAlbum != null) 0.3f else 1f, label = "dim")
    val density = LocalDensity.current
    Box(Modifier.fillMaxSize().onGloballyPositioned { origin = it.positionInRoot() }) {
        if (grid) {
            LazyVerticalGrid(
                GridCells.Adaptive(150.dp), Modifier.fillMaxSize().graphicsLayer { alpha = dim }, contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                gridItems(entries) { (kind, obj) ->
                    if (kind == 3) {
                        val f = obj as Folder
                        val al = f.albumIds.mapNotNull { albumsById[it] }
                        GridTile("f:${f.id}", f.name, "${al.size} album inclusi", hover == "f:${f.id}", false, bounds, Modifier, { openFolder(f.id) }) {
                            FolderCover(al, Modifier.fillMaxSize())
                        }
                    } else {
                        val a = obj as Album
                        val key = "a:${a.id}"
                        val dm = Modifier.pointerInput(a.id) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = { off -> dragAlbum = a; finger = (bounds[key]?.topLeft ?: Offset.Zero) + off },
                                onDrag = { change, amount -> change.consume(); finger += amount },
                                onDragEnd = {
                                    val da = dragAlbum
                                    val t = hoverNow.value
                                    if (da != null && t != null) ctl.mergeAlbum(da.id, t)
                                    dragAlbum = null
                                },
                                onDragCancel = { dragAlbum = null }
                            )
                        }
                        GridTile(key, a.title, "Album  \u2022  ${a.artist}", hover == key, dragAlbum?.id == a.id, bounds, dm, { openAlbum(a) }) {
                            Cover(a.songs[0].art, Modifier.fillMaxSize())
                        }
                    }
                }
            }
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                items(entries) { (kind, obj) ->
                    val title: String; val sub: String; val click: () -> Unit
                    if (kind == 3) { val f = obj as Folder; title = f.name; sub = "${f.albumIds.size} album inclusi"; click = { openFolder(f.id) } }
                    else { val a = obj as Album; title = a.title; sub = "Album  \u2022  ${a.artist}"; click = { openAlbum(a) } }
                    Row(Modifier.fillMaxWidth().pressable(0.98f, click).padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(56.dp)) {
                            if (kind == 3) FolderCover((obj as Folder).albumIds.mapNotNull { albumsById[it] }, Modifier.fillMaxSize())
                            else Cover((obj as Album).songs[0].art, Modifier.fillMaxSize())
                        }
                        Spacer(Modifier.width(16.dp))
                        Column(Modifier.weight(1f)) { Txt(title, style = TS.tile, maxLines = 1); Txt(sub, style = TS.small, color = p.sub, maxLines = 1) }
                    }
                }
            }
        }
        dragAlbum?.let { da ->
            val w = bounds["a:${da.id}"]?.width ?: 300f
            Box(
                Modifier.offset { IntOffset((finger.x - origin.x - w / 2).toInt(), (finger.y - origin.y - w / 2).toInt()) }
                    .size(with(density) { (w * 0.92f).toDp() })
                    .graphicsLayer { scaleX = 1.05f; scaleY = 1.05f }
                    .shadow(20.dp, CoverShape)
            ) { Cover(da.songs[0].art, Modifier.fillMaxSize()) }
        }
    }
}

@Composable
fun FolderPage(folder: Folder, ctl: Ctl, back: () -> Unit, openAlbum: (Album) -> Unit, onUngroup: () -> Unit) {
    val p = pal
    val albums = folder.albumIds.mapNotNull { id -> ctl.albums.firstOrNull { it.id == id } }
    Box(Modifier.fillMaxSize().background(p.bg).blockTouches()) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconBtn(Icons.Rounded.ArrowBack, back)
                Spacer(Modifier.weight(1f))
                MoreMenu(listOf(Triple("Separa la cartella", Icons.Rounded.FolderOpen, { onUngroup() })))
            }
            Txt(folder.name, Modifier.padding(horizontal = 16.dp), TS.h1, maxLines = 2)
            Txt("${albums.size} album", Modifier.padding(horizontal = 16.dp, vertical = 4.dp), TS.small, p.sub)
            LazyVerticalGrid(
                GridCells.Adaptive(150.dp), Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(16.dp)
            ) { gridItems(albums) { a -> AlbumTile(a, Modifier.fillMaxWidth()) { openAlbum(a) } } }
        }
    }
}

// ---------------------------------------------------------------- Artista

@Composable
fun ArtistPage(name: String, ctl: Ctl, back: () -> Unit, openAlbum: (Album) -> Unit, onMore: () -> Unit) {
    val p = pal
    val songs = ctl.artistMap[name] ?: emptyList()
    val albums = ctl.artistAlbumsOf(name)
    val fav = name in ctl.favArtists
    val img = ctl.artistImage(name)
    val tone by animateColorAsState(rememberCoverColor(img, p.accent), tween(500), label = "tone")
    Box(Modifier.fillMaxSize().background(p.bg).blockTouches()) {
        Box(Modifier.fillMaxWidth().height(300.dp).background(Brush.verticalGradient(0f to tone.copy(alpha = 0.85f), 1f to p.bg)))
        LazyColumn(Modifier.fillMaxSize().statusBarsPadding()) {
            item {
                Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconBtn(Icons.Rounded.ArrowBack, back, tint = Color.White)
                    Spacer(Modifier.weight(1f))
                    IconBtn(Icons.Rounded.MoreVert, onMore, tint = Color.White)
                }
            }
            item {
                Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Cover(img, Modifier.size(112.dp).shadow(16.dp, CircleShape), CircleShape)
                    Spacer(Modifier.width(16.dp))
                    Column {
                        Txt(ctl.artistDisplay(name), style = TS.h2, color = Color.White, maxLines = 2)
                        Spacer(Modifier.height(4.dp))
                        Txt("${albums.size} album  \u2022  ${ctl.artistPlays[name] ?: 0} ascolti", style = TS.small, color = Color.White.copy(alpha = 0.85f))
                    }
                }
            }
            item {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        Modifier.weight(1f).height(50.dp).pressable(0.97f) { ctl.toggleFavArtist(name) }.clip(RoundedCornerShape(25.dp))
                            .background(if (fav) Brush.horizontalGradient(listOf(Color(0xFFFF8A00), Color(0xFFFFB300))) else Brush.horizontalGradient(listOf(p.card, p.card))),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center
                    ) {
                        Txt(if (fav) "\u00C8 tra i preferiti" else "Aggiungi ai preferiti", style = TS.title, color = if (fav) Color.White else p.fg, maxLines = 1)
                        Spacer(Modifier.width(8.dp))
                        Ico(if (fav) Icons.Rounded.Star else Icons.Rounded.StarBorder, tint = if (fav) Color.White else p.fg, size = 22.dp)
                    }
                    Row(
                        Modifier.weight(1f).height(50.dp).pressable(0.97f) { if (songs.isNotEmpty()) ctl.play(songs, 0) }.clip(RoundedCornerShape(25.dp)).background(p.accent),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center
                    ) { Txt("Riproduci", style = TS.title, color = p.onAccent); Spacer(Modifier.width(8.dp)); Ico(Icons.Rounded.PlayArrow, tint = p.onAccent, size = 22.dp) }
                }
            }
            if (albums.isNotEmpty()) {
                item { Label("Album") }
                item {
                    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(albums) { a -> AlbumTile(a, Modifier.width(150.dp)) { openAlbum(a) } }
                    }
                }
            }
            item { Label("Tutti i brani") }
            itemsIndexed(songs) { i, _ -> TrackRow(ctl, songs, i) }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}
