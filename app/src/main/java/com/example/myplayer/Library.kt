package com.example.myplayer

import android.net.Uri
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
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
fun LibraryScreen(
    ctl: Ctl, openLiked: () -> Unit, openPlaylist: (Long) -> Unit, openFolder: (Long) -> Unit, openAlbum: (Album) -> Unit,
    onCreate: () -> Unit, onSearch: () -> Unit, onSettings: () -> Unit
) {
    val p = pal
    var grid by rememberSaveable { mutableStateOf(true) }
    val likedCount = ctl.likes.count { it in ctl.byId }
    val inFolder = ctl.folders.flatMap { it.albumIds }.toSet()
    val albumsById = ctl.albums.associateBy { it.id }
    // 0 = preferiti, 1 = playlist, 2 = album, 3 = cartella
    val entries = remember(ctl.playlists, ctl.albums, ctl.folders) {
        buildList<Pair<Int, Any>> {
            add(0 to "liked")
            ctl.playlists.forEach { add(1 to it) }
            ctl.folders.forEach { add(3 to it) }
            ctl.albums.filter { it.id !in inFolder }.forEach { add(2 to it) }
        }
    }
    val bounds = remember { mutableStateMapOf<String, Rect>() }
    var dragAlbum by remember { mutableStateOf<Album?>(null) }
    var finger by remember { mutableStateOf(Offset.Zero) }
    var origin by remember { mutableStateOf(Offset.Zero) }
    val hover = dragAlbum?.let { da -> bounds.entries.firstOrNull { (k, r) -> k != "a:${da.id}" && k != "liked" && !k.startsWith("p:") && r.contains(finger) }?.key }
    val hoverNow = rememberUpdatedState(hover)
    val dim by animateFloatAsState(if (dragAlbum != null) 0.3f else 1f, label = "dim")
    val density = LocalDensity.current

    Box(Modifier.fillMaxSize().onGloballyPositioned { origin = it.positionInRoot() }) {
        Column(Modifier.fillMaxSize()) {
            PageHeader("Library", onSearch, onSettings) { IconBtn(Icons.Rounded.AddBox, onCreate) }
            Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Txt(if (dragAlbum != null) "Rilascia su un album per unirli" else "Ultimi creati", Modifier.weight(1f), TS.label, p.sub)
                IconBtn(Icons.Rounded.ViewList, { grid = false }, tint = if (!grid) p.fg else p.sub, box = 40.dp)
                IconBtn(Icons.Rounded.GridView, { grid = true }, tint = if (grid) p.fg else p.sub, box = 40.dp)
            }
            if (ctl.songs.isEmpty()) Empty()
            else if (grid) {
                LazyVerticalGrid(
                    GridCells.Fixed(2), Modifier.fillMaxSize().graphicsLayer { alpha = dim }, contentPadding = PaddingValues(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    gridItems(entries) { (kind, obj) ->
                        when (kind) {
                            0 -> GridTile("liked", "Brani preferiti", "$likedCount brani  \u2022  Ci\u00F2 che ti piace", false, false, bounds, Modifier, openLiked) {
                                HeartCover(Modifier.fillMaxSize(), 64.dp)
                            }
                            1 -> {
                                val pl = obj as Playlist
                                val covers = ctl.playlistSongs(pl).map { it.art }
                                GridTile("p:${pl.id}", pl.name, "Playlist  \u2022  ${pl.songIds.size} brani", false, false, bounds, Modifier, { openPlaylist(pl.id) }) {
                                    val c = playlistCover(pl)
                                    if (c != null) Cover(c, Modifier.fillMaxSize()) else Mosaic(covers, Modifier.fillMaxSize())
                                }
                            }
                            3 -> {
                                val f = obj as Folder
                                val al = f.albumIds.mapNotNull { albumsById[it] }
                                GridTile("f:${f.id}", f.name, "${al.size} album inclusi", hover == "f:${f.id}", false, bounds, Modifier, { openFolder(f.id) }) {
                                    FolderCover(al, Modifier.fillMaxSize())
                                }
                            }
                            else -> {
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
                }
            } else {
                LazyColumn(Modifier.fillMaxSize()) {
                    items(entries) { (kind, obj) ->
                        val title: String; val sub: String; val click: () -> Unit
                        when (kind) {
                            0 -> { title = "Brani preferiti"; sub = "$likedCount brani"; click = openLiked }
                            1 -> { val pl = obj as Playlist; title = pl.name; sub = "Playlist  \u2022  ${pl.songIds.size} brani"; click = { openPlaylist(pl.id) } }
                            3 -> { val f = obj as Folder; title = f.name; sub = "${f.albumIds.size} album inclusi"; click = { openFolder(f.id) } }
                            else -> { val a = obj as Album; title = a.title; sub = "Album  \u2022  ${a.artist}"; click = { openAlbum(a) } }
                        }
                        Row(Modifier.fillMaxWidth().pressable(0.98f, click).padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(56.dp)) {
                                when (kind) {
                                    0 -> HeartCover(Modifier.fillMaxSize(), 26.dp)
                                    1 -> {
                                        val pl = obj as Playlist
                                        val c = playlistCover(pl)
                                        if (c != null) Cover(c, Modifier.fillMaxSize()) else Mosaic(ctl.playlistSongs(pl).map { it.art }, Modifier.fillMaxSize())
                                    }
                                    3 -> FolderCover((obj as Folder).albumIds.mapNotNull { albumsById[it] }, Modifier.fillMaxSize())
                                    else -> Cover((obj as Album).songs[0].art, Modifier.fillMaxSize())
                                }
                            }
                            Spacer(Modifier.width(16.dp))
                            Column(Modifier.weight(1f)) { Txt(title, style = TS.tile, maxLines = 1); Txt(sub, style = TS.small, color = p.sub, maxLines = 1) }
                        }
                    }
                }
            }
        }
        dragAlbum?.let { da ->
            val sz = bounds["a:${da.id}"]
            val w = sz?.width ?: 300f
            val h = sz?.width ?: 300f
            Box(
                Modifier.offset { IntOffset((finger.x - origin.x - w / 2).toInt(), (finger.y - origin.y - h / 2).toInt()) }
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
                GridCells.Fixed(2), Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(16.dp)
            ) { gridItems(albums) { a -> AlbumTile(a, Modifier.fillMaxWidth()) { openAlbum(a) } } }
        }
    }
}
