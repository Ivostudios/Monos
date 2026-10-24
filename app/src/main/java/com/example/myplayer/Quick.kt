package com.example.myplayer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

sealed class Quick {
    class Track(val song: Song, val playlistId: Long?) : Quick()
    class AddTo(val song: Song) : Quick()
    class Details(val song: Song) : Quick()
    class AlbumQ(val album: Album) : Quick()
    class AlbumDetails(val album: Album) : Quick()
    class PlaylistQ(val playlist: Playlist) : Quick()
    class ConfirmDelete(val playlist: Playlist) : Quick()
    class ArtistQ(val name: String) : Quick()
}

val LocalQuick = staticCompositionLocalOf<(Quick) -> Unit> { { } }
val LocalPlaylistCtx = compositionLocalOf<Long?> { null }

private val Red = Color(0xFFC00000)

@Composable
private fun QHeader(title: String, onClose: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 14.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Txt(title, Modifier.weight(1f), TS.h2, maxLines = 1)
        IconBtn(Icons.Rounded.Close, onClose)
    }
}

@Composable
private fun QBody(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.heightIn(max = 560.dp).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp), content = content
    )
}

@Composable
private fun QLabel(text: String) { Txt(text, Modifier.padding(top = 10.dp, bottom = 2.dp), TS.label, pal.sub) }

@Composable
private fun QOption(icon: ImageVector, title: String, sub: String? = null, danger: Boolean = false, onClick: () -> Unit) {
    val p = pal
    Row(
        Modifier.fillMaxWidth().pressable(0.98f, onClick).clip(RoundedCornerShape(14.dp))
            .background(if (danger) Red.copy(alpha = 0.08f) else p.card).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Ico(icon, tint = if (danger) Red else p.fg, size = 22.dp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Txt(title, style = TS.title, color = if (danger) Red else p.fg, maxLines = 2)
            if (sub != null) Txt(sub, style = TS.small, color = Red.copy(alpha = 0.8f))
        }
        Ico(Icons.Rounded.ChevronRight, tint = if (danger) Red.copy(alpha = 0.6f) else p.sub)
    }
}

@Composable
private fun TrackCard(s: Song) {
    val p = pal
    val ctx = LocalContext.current
    val info by produceState<List<Pair<String, String>>>(emptyList(), s.id) { value = audioInfo(ctx, s) }
    val br = info.firstOrNull { it.first == "Bitrate" }?.second
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(p.card).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Cover(s.art, Modifier.size(64.dp))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Txt(s.title, style = TS.title, maxLines = 2)
            Txt("Di: ${s.artists.joinToString(" / ")}", style = TS.small, color = p.sub, maxLines = 1)
            Txt("Durata: ${fmt(s.duration)}", style = TS.small, color = p.sub)
            if (br != null) Txt(br, style = TS.small, color = p.sub)
        }
    }
}

@Composable
private fun InfoRows(rows: List<Pair<String, String>>) {
    val p = pal
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(p.card).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        rows.forEach { (k, v) ->
            Row(Modifier.fillMaxWidth()) {
                Txt(k, Modifier.width(110.dp), TS.small, p.sub)
                Txt(v, Modifier.weight(1f), TS.title, maxLines = 3)
            }
        }
    }
}

@Composable
fun QuickMenuHost(
    ctl: Ctl, quick: Quick?, setQuick: (Quick?) -> Unit, onDelete: (List<Song>) -> Unit,
    onEditPlaylist: (Playlist, Boolean) -> Unit, onNewPlaylist: () -> Unit, onEditArtist: (String) -> Unit
) {
    val p = pal
    val ctx = LocalContext.current
    val holder = remember { arrayOfNulls<Quick>(1) }
    val cur = quick ?: holder[0]
    SideEffect { if (quick != null) holder[0] = quick }
    val close = { setQuick(null) }
    Sheet(quick != null, close) {
        when (cur) {
            is Quick.Track -> {
                val s = cur.song
                val liked = s.id in ctl.likes
                QHeader("Menu rapido", close)
                QBody {
                    TrackCard(s)
                    QLabel("Opzioni")
                    QOption(if (liked) Icons.Rounded.FavoriteBorder else Icons.Rounded.Favorite, if (liked) "Rimuovi dai preferiti" else "Aggiungi ai preferiti") { ctl.toggleLike(s.id); close() }
                    QOption(Icons.Rounded.AddBox, "Aggiungi a una playlist") { setQuick(Quick.AddTo(s)) }
                    val pid = cur.playlistId
                    if (pid != null) QOption(Icons.Rounded.RemoveCircleOutline, "Rimuovi da questa playlist") { ctl.removeFromPlaylist(pid, s.id); close() }
                    QOption(Icons.Rounded.Info, "Vedi i dettagli") { setQuick(Quick.Details(s)) }
                    Spacer(Modifier.height(6.dp))
                    QOption(Icons.Rounded.Warning, "Elimina dal dispositivo", "Eliminando questo brano, verr\u00E0 rimosso definitivamente.", danger = true) { onDelete(listOf(s)); close() }
                }
            }
            is Quick.AddTo -> {
                val s = cur.song
                QHeader("Aggiungi a una playlist", close)
                QBody {
                    if (ctl.playlists.isEmpty()) Txt("Non hai ancora nessuna playlist.", Modifier.padding(vertical = 8.dp), TS.body, p.sub)
                    ctl.playlists.forEach { pl ->
                        Row(
                            Modifier.fillMaxWidth().pressable(0.98f) { ctl.addToPlaylist(pl.id, s.id); close() }.clip(RoundedCornerShape(14.dp)).background(p.card).padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(Modifier.size(48.dp)) {
                                val c = playlistCover(pl)
                                if (c != null) Cover(c, Modifier.fillMaxSize()) else Mosaic(ctl.playlistSongs(pl).map { it.art }, Modifier.fillMaxSize())
                            }
                            Spacer(Modifier.width(14.dp))
                            Column(Modifier.weight(1f)) {
                                Txt(pl.name, style = TS.title, maxLines = 1)
                                Txt("${pl.songIds.size} brani" + if (s.id in pl.songIds) "  \u2022  gi\u00E0 presente" else "", style = TS.small, color = p.sub)
                            }
                        }
                    }
                    QOption(Icons.Rounded.AddBox, "Nuova playlist") { close(); onNewPlaylist() }
                }
            }
            is Quick.Details -> {
                val s = cur.song
                val info by produceState<List<Pair<String, String>>>(emptyList(), s.id) { value = audioInfo(ctx, s) }
                val added = java.time.Instant.ofEpochSecond(s.added).atZone(java.time.ZoneId.systemDefault()).toLocalDate().toString()
                QHeader("Dettagli", close)
                QBody {
                    InfoRows(
                        listOfNotNull(
                            "Titolo" to s.title, "Artista" to s.artist, "Album" to s.album,
                            if (s.track > 0) "Traccia" to "${s.track}" else null
                        ) + info + listOf("Aggiunto il" to added)
                    )
                }
            }
            is Quick.AlbumQ -> {
                val a = cur.album
                QHeader("Menu rapido", close)
                QBody {
                    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Cover(a.songs[0].art, Modifier.size(150.dp))
                        Spacer(Modifier.height(10.dp))
                        Txt(a.title, style = TS.h3, maxLines = 2, align = TextAlign.Center)
                        Txt("Album  \u2022  ${a.artist}", style = TS.small, color = p.sub, maxLines = 1, align = TextAlign.Center)
                    }
                    QLabel("Opzioni")
                    QOption(Icons.Rounded.Info, "Vedi i dettagli") { setQuick(Quick.AlbumDetails(a)) }
                    QOption(Icons.Rounded.VisibilityOff, "Nascondi questo album") { ctl.hideAlbum(a.id); close() }
                    Spacer(Modifier.height(6.dp))
                    QOption(Icons.Rounded.Warning, "Elimina questo album", "Eliminando questo album, verranno rimossi tutti i suoi brani.", danger = true) { onDelete(a.songs); close() }
                }
            }
            is Quick.AlbumDetails -> {
                val a = cur.album
                QHeader("Dettagli dell'album", close)
                QBody {
                    InfoRows(
                        listOf(
                            "Titolo" to a.title, "Artista" to a.artist, "Brani" to "${a.songs.size}",
                            "Durata" to fmt(a.songs.sumOf { it.duration }), "Dimensione" to bytesFmt(a.songs.sumOf { it.size })
                        )
                    )
                }
            }
            is Quick.PlaylistQ -> {
                val pl = ctl.playlists.firstOrNull { it.id == cur.playlist.id } ?: cur.playlist
                QHeader("Menu rapido", close)
                QBody {
                    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(Modifier.size(150.dp)) {
                            val c = playlistCover(pl)
                            if (c != null) Cover(c, Modifier.fillMaxSize()) else Mosaic(ctl.playlistSongs(pl).map { it.art }, Modifier.fillMaxSize())
                        }
                        Spacer(Modifier.height(10.dp))
                        Txt(pl.name, style = TS.h3, maxLines = 2, align = TextAlign.Center)
                        Txt("Playlist  \u2022  Creata da te", style = TS.small, color = p.sub, align = TextAlign.Center)
                    }
                    QLabel("Opzioni")
                    QOption(Icons.Rounded.Edit, "Modifica le informazioni") { onEditPlaylist(pl, false); close() }
                    QOption(Icons.Rounded.AddBox, "Aggiungi a questa playlist") { onEditPlaylist(pl, true); close() }
                    Spacer(Modifier.height(6.dp))
                    QOption(Icons.Rounded.Warning, "Elimina questa playlist", "Eliminando la playlist, verr\u00E0 rimossa per sempre.", danger = true) { setQuick(Quick.ConfirmDelete(pl)) }
                }
            }
            is Quick.ConfirmDelete -> {
                val pl = cur.playlist
                QHeader("Eliminare la playlist?", close)
                QBody {
                    Txt("\"${pl.name}\" verr\u00E0 rimossa per sempre. I brani non vengono toccati.", Modifier.padding(vertical = 6.dp), TS.body, p.sub)
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Box(Modifier.weight(1f).height(50.dp).pressable(0.97f) { close() }.clip(RoundedCornerShape(25.dp)).background(p.card), contentAlignment = Alignment.Center) { Txt("Annulla", style = TS.title) }
                        Box(Modifier.weight(1f).height(50.dp).pressable(0.97f) { ctl.deletePlaylist(pl.id); close() }.clip(RoundedCornerShape(25.dp)).background(Red), contentAlignment = Alignment.Center) { Txt("Elimina", style = TS.title, color = Color.White) }
                    }
                }
            }
            is Quick.ArtistQ -> {
                val n = cur.name
                val fav = n in ctl.favArtists
                QHeader("Menu rapido", close)
                QBody {
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Cover(ctl.artistImage(n), Modifier.size(130.dp), CircleShape)
                        Spacer(Modifier.width(18.dp))
                        Column(Modifier.weight(1f)) {
                            Txt(ctl.artistDisplay(n), style = TS.h3, maxLines = 2)
                            Txt("${ctl.artistAlbumsOf(n).size} album  \u2022  ${ctl.artistPlays[n] ?: 0} ascolti", style = TS.small, color = p.sub)
                        }
                    }
                    QLabel("Opzioni")
                    QOption(if (fav) Icons.Rounded.StarBorder else Icons.Rounded.Star, if (fav) "Rimuovi dai preferiti" else "Aggiungi ai preferiti") { ctl.toggleFavArtist(n); close() }
                    QOption(Icons.Rounded.Edit, "Modifica le informazioni") { onEditArtist(n); close() }
                }
            }
            null -> {}
        }
    }
}
