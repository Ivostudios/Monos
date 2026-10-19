package com.example.myplayer

import android.content.ComponentName
import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore.Audio.Media as M
import androidx.compose.runtime.*
import androidx.core.content.ContextCompat
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject
import java.time.DayOfWeek
import java.time.LocalDate
import android.os.SystemClock

val Context.store by preferencesDataStore("settings")

object K {
    val theme = intPreferencesKey("theme")
    val accent = intPreferencesKey("accent")
    val autoLyrics = intPreferencesKey("autoLyrics")
    val likes = stringSetPreferencesKey("likes")
    val playlists = stringPreferencesKey("playlists")
    val stats = stringPreferencesKey("stats")
}

data class Playlist(val id: Long, val name: String, val songIds: List<Long>)

fun fix(s: String) = s.replace('\u2019', '\'').replace('\u2018', '\'')
    .replace('\u201C', '"').replace('\u201D', '"').replace("\u2026", "...").replace('\u2013', '-')

const val NO_ALBUM = "Album sconosciuto"

data class Song(
    val id: Long, val title: String, val artist: String, val albumId: Long,
    val album: String, val duration: Long, val added: Long, val track: Int
) {
    val uri: Uri get() = ContentUris.withAppendedId(M.EXTERNAL_CONTENT_URI, id)
    val art: Uri get() = ContentUris.withAppendedId(Uri.parse("content://media/external/audio/albumart"), albumId)
    val hasAlbum: Boolean get() = album != NO_ALBUM
    val artists: List<String> get() = artist.split(", ", " / ", ";", " & ").map { it.trim() }.filter { it.isNotEmpty() }
    fun toItem(): MediaItem = MediaItem.Builder()
        .setMediaId(id.toString()).setUri(uri)
        .setMediaMetadata(MediaMetadata.Builder().setTitle(title).setArtist(artist).setAlbumTitle(album).setArtworkUri(art).build())
        .build()
}

data class Album(val id: Long, val title: String, val artist: String, val songs: List<Song>)

fun loadSongs(ctx: Context): List<Song> {
    val out = mutableListOf<Song>()
    try {
        ctx.contentResolver.query(
            M.EXTERNAL_CONTENT_URI,
            arrayOf(M._ID, M.TITLE, M.ARTIST, M.ALBUM_ID, M.ALBUM, M.DURATION, M.DATE_ADDED, M.TRACK),
            "${M.IS_MUSIC} != 0 AND ${M.DURATION} >= 30000", null, "${M.TITLE} COLLATE NOCASE"
        )?.use { c ->
            while (c.moveToNext()) {
                out += Song(
                    c.getLong(0), fix(c.getString(1) ?: "Senza titolo"),
                    fix(c.getString(2)?.takeIf { it != "<unknown>" } ?: "Artista sconosciuto"),
                    c.getLong(3), fix(c.getString(4)?.takeIf { it != "<unknown>" } ?: NO_ALBUM),
                    c.getLong(5), c.getLong(6), c.getInt(7) % 1000
                )
            }
        }
    } catch (e: Exception) { }
    return out
}

class Ctl(private val ctx: Context) {
    var songs by mutableStateOf<List<Song>>(emptyList())
    var queue by mutableStateOf<List<Song>>(emptyList())
    var current by mutableIntStateOf(-1)
    var playing by mutableStateOf(false)
    var pos by mutableLongStateOf(0L)
    var speed by mutableFloatStateOf(1f)
    var pitch by mutableFloatStateOf(1f)
    var shuffle by mutableStateOf(false)
    var repeat by mutableIntStateOf(Player.REPEAT_MODE_OFF)
    var likes by mutableStateOf<Set<Long>>(emptySet())
    private var mc: MediaController? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    var playlists by mutableStateOf<List<Playlist>>(emptyList())
    var totalMs by mutableLongStateOf(0L)
    var plays by mutableIntStateOf(0)
    var days by mutableStateOf<Set<String>>(emptySet())
    var albumMs by mutableStateOf<Map<Long, Long>>(emptyMap())
    var recent by mutableStateOf<List<Long>>(emptyList())
    private var curMs = 0L
    private var curCounted = false
    private var lastTick = 0L
    private var sinceSave = 0

    init {
        scope.launch {
            val p = ctx.store.data.first()
            val ids = (p[K.likes] ?: emptySet()).mapNotNull { it.toLongOrNull() }.toSet()
            val pl = parsePlaylists(p[K.playlists])
            val st = p[K.stats]
            withContext(Dispatchers.Main) { likes = ids; playlists = pl; st?.let { loadStats(it) } }
        }
    }

    private fun parsePlaylists(raw: String?): List<Playlist> = try {
        if (raw == null) emptyList() else {
            val a = JSONArray(raw)
            (0 until a.length()).map {
                val o = a.getJSONObject(it)
                val ids = o.getJSONArray("ids")
                Playlist(o.getLong("id"), o.getString("name"), (0 until ids.length()).map { i -> ids.getLong(i) })
            }
        }
    } catch (e: Exception) { emptyList() }

    private fun savePlaylists() {
        val a = JSONArray()
        playlists.forEach { p ->
            a.put(JSONObject().put("id", p.id).put("name", p.name).put("ids", JSONArray(p.songIds)))
        }
        val raw = a.toString()
        scope.launch { ctx.store.edit { it[K.playlists] = raw } }
    }

    private fun loadStats(raw: String) {
        try {
            val o = JSONObject(raw)
            totalMs = o.optLong("ms"); plays = o.optInt("plays")
            val d = o.optJSONArray("days"); days = if (d == null) emptySet() else (0 until d.length()).map { d.getString(it) }.toSet()
            val al = o.optJSONObject("albums")
            albumMs = if (al == null) emptyMap() else al.keys().asSequence().associate { it.toLong() to al.getLong(it) }
            val r = o.optJSONArray("recent"); recent = if (r == null) emptyList() else (0 until r.length()).map { r.getLong(it) }
        } catch (e: Exception) { }
    }

    fun saveStats() {
        val o = JSONObject().put("ms", totalMs).put("plays", plays).put("days", JSONArray(days.toList()))
            .put("albums", JSONObject().also { j -> albumMs.forEach { (k, v) -> j.put(k.toString(), v) } })
            .put("recent", JSONArray(recent))
        val raw = o.toString()
        scope.launch { ctx.store.edit { it[K.stats] = raw } }
    }

    val streak: Int get() {
        var d = LocalDate.now()
        if (d.toString() !in days) d = d.minusDays(1)
        var n = 0
        while (d.toString() in days) { n++; d = d.minusDays(1) }
        return n
    }
    val weekDone: List<Boolean> get() {
        val mon = LocalDate.now().with(DayOfWeek.MONDAY)
        return (0..6).map { mon.plusDays(it.toLong()).toString() in days }
    }
    val byId by derivedStateOf { songs.associateBy { it.id } }

    fun createPlaylist(name: String, ids: List<Long>) {
        playlists = listOf(Playlist(System.currentTimeMillis(), name.trim(), ids)) + playlists
        savePlaylists()
    }
    fun deletePlaylist(id: Long) { playlists = playlists.filter { it.id != id }; savePlaylists() }
    fun addToPlaylist(pid: Long, songId: Long) {
        playlists = playlists.map { if (it.id == pid && songId !in it.songIds) it.copy(songIds = it.songIds + songId) else it }
        savePlaylists()
    }
    fun playlistSongs(p: Playlist): List<Song> = p.songIds.mapNotNull { byId[it] }

    val currentSong: Song? get() = queue.getOrNull(current)
    val albums by derivedStateOf {
        songs.filter { it.hasAlbum }.groupBy { it.albumId }.map { (id, l) ->
            val artists = l.map { it.artist }.distinct()
            Album(id, l[0].album, if (artists.size == 1) artists[0] else "Artisti vari", l.sortedBy { it.track })
        }.sortedBy { it.title.lowercase() }
    }
    val artistMap by derivedStateOf {
        val m = linkedMapOf<String, MutableList<Song>>()
        songs.forEach { s -> s.artists.forEach { a -> m.getOrPut(a) { mutableListOf() }.add(s) } }
        m.entries.sortedBy { it.key.lowercase() }.associate { it.key to it.value.toList() }
    }

    private val listener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) { playing = isPlaying; if (!isPlaying) saveStats() }
        override fun onMediaItemTransition(item: MediaItem?, reason: Int) {
            current = if (item == null) -1 else mc?.currentMediaItemIndex ?: -1
            noteStart()
        }
        override fun onPlaybackParametersChanged(p: PlaybackParameters) { speed = p.speed; pitch = p.pitch }
        override fun onShuffleModeEnabledChanged(on: Boolean) { shuffle = on }
        override fun onRepeatModeChanged(mode: Int) { repeat = mode }
    }

    fun connect() {
        val token = SessionToken(ctx, ComponentName(ctx, PlaybackService::class.java))
        val f = MediaController.Builder(ctx, token).buildAsync()
        f.addListener({
            val c = f.get()
            mc = c
            c.addListener(listener)
            playing = c.isPlaying
            speed = c.playbackParameters.speed
            pitch = c.playbackParameters.pitch
            shuffle = c.shuffleModeEnabled
            repeat = c.repeatMode
            syncQueue()
        }, ContextCompat.getMainExecutor(ctx))
    }

    private fun syncQueue() {
        val c = mc ?: return
        if (queue.isNotEmpty() || c.mediaItemCount == 0 || songs.isEmpty()) return
        val byId = songs.associateBy { it.id.toString() }
        val q = (0 until c.mediaItemCount).mapNotNull { byId[c.getMediaItemAt(it).mediaId] }
        if (q.size == c.mediaItemCount) { queue = q; current = c.currentMediaItemIndex }
    }

    suspend fun scan() { songs = withContext(Dispatchers.IO) { loadSongs(ctx) }; syncQueue() }
    private fun noteStart() {
        curMs = 0L; curCounted = false
        currentSong?.let { s -> recent = (listOf(s.id) + recent.filter { it != s.id }).take(30) }
    }

    fun refreshPos() {
        pos = mc?.currentPosition ?: 0L
        val now = SystemClock.elapsedRealtime()
        val s = currentSong
        if (playing && s != null && lastTick > 0L) {
            val d = (now - lastTick).coerceIn(0L, 1500L)
            totalMs += d; curMs += d
            val today = LocalDate.now().toString()
            if (today !in days) days = days + today
            if (s.hasAlbum) albumMs = albumMs + (s.albumId to ((albumMs[s.albumId] ?: 0L) + d))
            if (!curCounted && curMs >= 20_000L) { curCounted = true; plays++ }
            sinceSave++
            if (sinceSave >= 20) { sinceSave = 0; saveStats() }
        }
        lastTick = now
    }
    fun release() { mc?.release(); mc = null }

    fun toggleLike(id: Long) {
        likes = if (id in likes) likes - id else likes + id
        val snap = likes.map { it.toString() }.toSet()
        scope.launch { ctx.store.edit { it[K.likes] = snap } }
    }

    fun play(list: List<Song>, i: Int) {
        val c = mc ?: return
        if (list.map { it.id } != queue.map { it.id }) { c.setMediaItems(list.map { it.toItem() }); queue = list }
        c.seekToDefaultPosition(i)
        c.prepare(); c.play(); current = i
        noteStart()
    }
    fun playShuffled(list: List<Song>) {
        mc?.shuffleModeEnabled = true
        play(list, list.indices.random())
    }
    fun toggle() {
        val c = mc ?: return
        if (c.isPlaying) c.pause()
        else if (c.mediaItemCount == 0 && songs.isNotEmpty()) play(songs, 0)
        else c.play()
    }
    fun next() { mc?.seekToNextMediaItem() }
    fun prev() { mc?.seekToPreviousMediaItem() }
    fun seek(ms: Long) { mc?.seekTo(ms); pos = ms }
    fun toggleShuffle() { mc?.let { it.shuffleModeEnabled = !it.shuffleModeEnabled } }
    fun cycleRepeat() {
        mc?.let {
            it.repeatMode = when (it.repeatMode) {
                Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                else -> Player.REPEAT_MODE_OFF
            }
        }
    }
    fun setParams(s: Float, p: Float) {
        mc?.playbackParameters = PlaybackParameters(s, p)
        speed = s; pitch = p
    }
}
