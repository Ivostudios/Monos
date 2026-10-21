package com.example.myplayer

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlin.math.abs

data class LyricLine(val time: Long, val text: String)
class LyricsData(val lines: List<LyricLine>, val synced: Boolean)

object Lyrics {
    var status by mutableStateOf("")
    var running by mutableStateOf(false)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val ts = Regex("""\[(\d{1,3}):(\d{2})(?:[.:](\d{1,3}))?]""")

    private fun dir(ctx: Context) = File(ctx.filesDir, "lyrics").also { it.mkdirs() }
    private fun file(ctx: Context, s: Song, ext: String) = File(dir(ctx), "${s.id}.$ext")
    fun missed(ctx: Context, s: Song) = file(ctx, s, "none").exists()

    fun parse(raw: String): LyricsData? {
        val out = mutableListOf<LyricLine>()
        for (line in raw.lines()) {
            val ms = ts.findAll(line).toList()
            if (ms.isEmpty()) continue
            val text = fix(line.substring(ms.last().range.last + 1).trim())
            for (m in ms) {
                val mm = m.groupValues[1].toLong()
                val ss = m.groupValues[2].toLong()
                val fr = m.groupValues[3]
                val msec = if (fr.isEmpty()) 0L else fr.padEnd(3, '0').take(3).toLong()
                out += LyricLine(mm * 60000 + ss * 1000 + msec, text)
            }
        }
        return if (out.isEmpty()) null else LyricsData(out.sortedBy { it.time }, true)
    }

    fun readLocal(ctx: Context, s: Song): LyricsData? {
        val l = file(ctx, s, "lrc")
        if (l.exists()) parse(l.readText())?.let { return it }
        val t = file(ctx, s, "txt")
        if (t.exists()) return LyricsData(t.readLines().map { LyricLine(0, fix(it)) }, false)
        return null
    }

    fun saveRaw(ctx: Context, s: Song, raw: String) {
        file(ctx, s, "none").delete()
        if (ts.containsMatchIn(raw)) file(ctx, s, "lrc").writeText(raw) else file(ctx, s, "txt").writeText(raw)
    }

    private fun http(url: String): String? {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 8000; c.readTimeout = 8000
        c.setRequestProperty("User-Agent", "MyPlayer/1.1 (personal music player)")
        return try { if (c.responseCode == 200) c.inputStream.bufferedReader().readText() else null } finally { c.disconnect() }
    }
    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")
    private fun str(o: JSONObject, k: String): String? = if (o.isNull(k)) null else o.optString(k).takeIf { it.isNotBlank() }

    /** 1 = trovato, 0 = non esiste, -1 = errore di rete */
    suspend fun fetch(ctx: Context, s: Song): Int = withContext(Dispatchers.IO) {
        try {
            val dur = (s.duration / 1000).toInt()
            val first = s.artists.firstOrNull() ?: s.artist
            var js: JSONObject? = null
            for (a in listOf(s.artist, first).distinct()) {
                val alb = if (s.hasAlbum) "&album_name=${enc(s.album)}" else ""
                http("https://lrclib.net/api/get?track_name=${enc(s.title)}&artist_name=${enc(a)}$alb&duration=$dur")
                    ?.let { js = JSONObject(it) }
                if (js != null) break
            }
            if (js == null) {
                val raw = http("https://lrclib.net/api/search?track_name=${enc(s.title)}&artist_name=${enc(first)}")
                val arr = raw?.let { JSONArray(it) }
                if (arr != null) {
                    val all = (0 until arr.length()).map { arr.getJSONObject(it) }
                    js = all.firstOrNull { str(it, "syncedLyrics") != null && abs(it.optInt("duration") - dur) <= 3 }
                        ?: all.firstOrNull { str(it, "syncedLyrics") != null }
                        ?: all.firstOrNull { str(it, "plainLyrics") != null }
                }
            }
            val synced = js?.let { str(it, "syncedLyrics") }
            val plain = js?.let { str(it, "plainLyrics") }
            when {
                synced != null -> { saveRaw(ctx, s, synced); 1 }
                plain != null -> { saveRaw(ctx, s, plain); 1 }
                else -> { file(ctx, s, "none").writeText("x"); 0 }
            }
        } catch (e: Exception) { -1 }
    }

    var total by mutableIntStateOf(0)
    var done by mutableIntStateOf(0)
    var found by mutableIntStateOf(0)
    private var job: Job? = null
    fun cancel() { job?.cancel() }

    fun downloadAll(ctx: Context, songs: List<Song>) {
        if (running) return
        running = true; total = 0; done = 0; found = 0
        job = scope.launch {
            try {
                songs.forEach { file(ctx, it, "none").delete() }
                val todo = songs.filter { readLocal(ctx, it) == null }
                total = todo.size
                if (todo.isEmpty()) { status = "Tutti i brani hanno gi\u00E0 un testo."; return@launch }
                var err = 0
                for (s in todo) {
                    ensureActive()
                    when (fetch(ctx, s)) { 1 -> found++; -1 -> err++ }
                    done++
                    delay(250)
                }
                status = "Trovati $found testi su ${todo.size}." + if (err > 0) " Errori di rete: $err." else ""
            } catch (e: CancellationException) {
                status = "Sincronizzazione interrotta. Trovati $found testi."
            } finally { running = false }
        }
    }
}
