package com.example.myplayer

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.URL
import java.time.Instant
import java.util.UUID

class JellySession(val url: String, val token: String, val userId: String, val userName: String, val deviceId: String)

/** Cliente minimo per l'API REST di Jellyfin (accesso, libreria musicale, preferiti, playlist, testi). */
object Jelly {
    var current: JellySession? = null
    var busy by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)

    private fun auth(deviceId: String, token: String?): String {
        val b = "MediaBrowser Client=\"Monos\", Device=\"Android\", DeviceId=\"$deviceId\", Version=\"1.26\""
        return if (token == null) b else "$b, Token=\"$token\""
    }

    private fun http(method: String, url: String, deviceId: String, token: String?, body: String? = null): Pair<Int, String> {
        val c = URL(url).openConnection() as HttpURLConnection
        try {
            c.requestMethod = method
            c.connectTimeout = 8000
            c.readTimeout = 30000
            c.setRequestProperty("Authorization", auth(deviceId, token))
            c.setRequestProperty("Accept", "application/json")
            if (body != null) {
                c.doOutput = true
                c.setRequestProperty("Content-Type", "application/json")
                c.outputStream.use { it.write(body.toByteArray()) }
            }
            val code = c.responseCode
            val txt = (if (code in 200..299) c.inputStream else c.errorStream)?.bufferedReader()?.use { it.readText() } ?: ""
            return code to txt
        } finally { c.disconnect() }
    }

    private fun req(s: JellySession, method: String, path: String, body: String? = null) = http(method, s.url + path, s.deviceId, s.token, body)

    /** Id numerico stabile ricavato dall'identificativo di Jellyfin. */
    fun guidLong(g: String): Long = try {
        val d = if (g.length == 32) "${g.substring(0, 8)}-${g.substring(8, 12)}-${g.substring(12, 16)}-${g.substring(16, 20)}-${g.substring(20)}" else g
        UUID.fromString(d).mostSignificantBits
    } catch (e: Exception) { g.hashCode().toLong() }

    suspend fun login(urlIn: String, user: String, pass: String, deviceId: String): Result<JellySession> = withContext(Dispatchers.IO) {
        try {
            var url = urlIn.trim().trimEnd('/')
            if (url.isEmpty()) return@withContext Result.failure<JellySession>(Exception("Inserisci l'indirizzo del server."))
            if (!url.startsWith("http://") && !url.startsWith("https://")) url = "http://$url"
            val (ic, _) = http("GET", "$url/System/Info/Public", deviceId, null)
            if (ic != 200) return@withContext Result.failure<JellySession>(Exception("Il server non risponde come Jellyfin (codice $ic)."))
            val (code, txt) = http(
                "POST", "$url/Users/AuthenticateByName", deviceId, null,
                JSONObject().put("Username", user).put("Pw", pass).toString()
            )
            when {
                code == 200 -> {
                    val o = JSONObject(txt)
                    val u = o.getJSONObject("User")
                    Result.success(JellySession(url, o.getString("AccessToken"), u.getString("Id"), u.optString("Name", user), deviceId))
                }
                code == 401 || code == 403 -> Result.failure<JellySession>(Exception("Nome utente o password errati."))
                else -> Result.failure<JellySession>(Exception("Errore del server ($code)."))
            }
        } catch (e: java.net.UnknownHostException) {
            Result.failure<JellySession>(Exception("Indirizzo non trovato. Controlla di essere sulla stessa rete del server."))
        } catch (e: java.net.SocketTimeoutException) {
            Result.failure<JellySession>(Exception("Il server non risponde."))
        } catch (e: java.net.ConnectException) {
            Result.failure<JellySession>(Exception("Impossibile connettersi: controlla indirizzo e porta."))
        } catch (e: Exception) {
            Result.failure<JellySession>(Exception("Connessione non riuscita: ${e.message}"))
        }
    }

    private fun toSong(s: JellySession, o: JSONObject): Pair<Song, Boolean>? {
        val id = o.optString("Id", "")
        if (id.isEmpty()) return null
        val artists = o.optJSONArray("Artists")?.let { a -> (0 until a.length()).map { a.getString(it) } } ?: emptyList()
        val artist = artists.joinToString(", ").ifEmpty { o.optString("AlbumArtist", "") }.ifEmpty { "Artista sconosciuto" }
        val albumGuid = o.optString("AlbumId", "")
        val album = o.optString("Album", "").ifEmpty { NO_ALBUM }
        val added = try { Instant.parse(o.optString("DateCreated", "")).epochSecond } catch (e: Exception) { 0L }
        val size = o.optJSONArray("MediaSources")?.optJSONObject(0)?.optLong("Size", 0L) ?: 0L
        val imgId = albumGuid.ifEmpty { id }
        val art = "${s.url}/Items/$imgId/Images/Primary?maxWidth=700&quality=90&api_key=${s.token}"
        val stream = "${s.url}/Audio/$id/stream?static=true&api_key=${s.token}"
        val fav = o.optJSONObject("UserData")?.optBoolean("IsFavorite", false) == true
        val song = Song(
            guidLong(id), fix(o.optString("Name", "Senza titolo")), fix(artist),
            if (albumGuid.isEmpty()) 0L else guidLong(albumGuid), fix(album),
            o.optLong("RunTimeTicks", 0L) / 10_000L, added, o.optInt("IndexNumber", 0), size, "",
            remoteId = id, remoteUri = stream, remoteArt = art
        )
        return song to fav
    }

    /** Tutti i brani della libreria musicale e l'elenco di quelli preferiti. */
    suspend fun loadSongs(s: JellySession): Pair<List<Song>, Set<Long>> = withContext(Dispatchers.IO) {
        val out = mutableListOf<Song>()
        val favs = mutableSetOf<Long>()
        var start = 0
        while (true) {
            val (code, txt) = req(
                s, "GET",
                "/Items?userId=${s.userId}&includeItemTypes=Audio&recursive=true&sortBy=SortName&sortOrder=Ascending" +
                    "&fields=DateCreated,MediaSources&enableUserData=true&startIndex=$start&limit=500"
            )
            if (code != 200) throw Exception("Codice $code")
            val o = JSONObject(txt)
            val arr = o.getJSONArray("Items")
            if (arr.length() == 0) break
            for (i in 0 until arr.length()) {
                toSong(s, arr.getJSONObject(i))?.let { (song, fav) -> out += song; if (fav) favs += song.id }
            }
            start += arr.length()
            if (start >= o.optInt("TotalRecordCount", start)) break
        }
        out to favs
    }

    suspend fun setFavorite(s: JellySession, itemId: String, fav: Boolean): Boolean = withContext(Dispatchers.IO) {
        try {
            val m = if (fav) "POST" else "DELETE"
            var c = req(s, m, "/UserFavoriteItems/$itemId?userId=${s.userId}").first
            if (c == 404 || c == 405) c = req(s, m, "/Users/${s.userId}/FavoriteItems/$itemId").first
            c in 200..299
        } catch (e: Exception) { false }
    }

    suspend fun loadPlaylists(s: JellySession): List<Playlist> = withContext(Dispatchers.IO) {
        try {
            val (c, t) = req(s, "GET", "/Items?userId=${s.userId}&includeItemTypes=Playlist&recursive=true")
            if (c != 200) return@withContext emptyList<Playlist>()
            val arr = JSONObject(t).getJSONArray("Items")
            (0 until arr.length()).map { arr.getJSONObject(it) }.map { p ->
                val pid = p.getString("Id")
                val ids = mutableListOf<Long>()
                val entries = mutableMapOf<Long, String>()
                val (ic, body) = req(s, "GET", "/Playlists/$pid/Items?userId=${s.userId}&limit=5000")
                if (ic == 200) {
                    val items = JSONObject(body).getJSONArray("Items")
                    for (k in 0 until items.length()) {
                        val o = items.getJSONObject(k)
                        val sid = guidLong(o.getString("Id"))
                        ids += sid
                        o.optString("PlaylistItemId", "").takeIf { it.isNotEmpty() }?.let { entries[sid] = it }
                    }
                }
                Playlist(guidLong(pid), fix(p.optString("Name", "Playlist")), ids, "", null, pid, entries)
            }
        } catch (e: Exception) { emptyList() }
    }

    suspend fun createPlaylist(s: JellySession, name: String, itemIds: List<String>): Boolean = withContext(Dispatchers.IO) {
        try {
            val body = JSONObject().put("Name", name).put("Ids", JSONArray(itemIds)).put("UserId", s.userId).put("MediaType", "Audio").toString()
            req(s, "POST", "/Playlists", body).first in 200..299
        } catch (e: Exception) { false }
    }

    suspend fun addToPlaylist(s: JellySession, playlistId: String, itemIds: List<String>): Boolean = withContext(Dispatchers.IO) {
        try { req(s, "POST", "/Playlists/$playlistId/Items?ids=${itemIds.joinToString(",")}&userId=${s.userId}").first in 200..299 } catch (e: Exception) { false }
    }

    suspend fun removeFromPlaylist(s: JellySession, playlistId: String, entryIds: List<String>): Boolean = withContext(Dispatchers.IO) {
        try { req(s, "DELETE", "/Playlists/$playlistId/Items?entryIds=${entryIds.joinToString(",")}").first in 200..299 } catch (e: Exception) { false }
    }

    suspend fun deleteItem(s: JellySession, itemId: String): Boolean = withContext(Dispatchers.IO) {
        try { req(s, "DELETE", "/Items/$itemId").first in 200..299 } catch (e: Exception) { false }
    }

    /** Testo del brano dal server, in formato LRC se sincronizzato (null se assente). */
    suspend fun lyricsLrc(s: JellySession, itemId: String): String? = withContext(Dispatchers.IO) {
        try {
            val (c, t) = req(s, "GET", "/Audio/$itemId/Lyrics")
            if (c != 200) return@withContext null
            val arr = JSONObject(t).optJSONArray("Lyrics") ?: return@withContext null
            val lines = (0 until arr.length()).map { arr.getJSONObject(it) }
            if (lines.isEmpty()) return@withContext null
            if (lines.any { it.has("Start") && !it.isNull("Start") }) {
                lines.filter { it.has("Start") && !it.isNull("Start") }.joinToString("\n") { o ->
                    val ms = o.getLong("Start") / 10_000L
                    "[%02d:%02d.%02d]%s".format(ms / 60000, (ms / 1000) % 60, (ms % 1000) / 10, o.optString("Text", ""))
                }
            } else lines.joinToString("\n") { it.optString("Text", "") }
        } catch (e: Exception) { null }
    }

    // ---- ricerca automatica del server sulla rete locale (UDP 7359) ----
    /** Restituisce coppie (nome, indirizzo) dei server Jellyfin che rispondono entro pochi secondi. */
    suspend fun discover(): List<Pair<String, String>> = withContext(Dispatchers.IO) {
        val found = LinkedHashMap<String, String>()
        var sock: DatagramSocket? = null
        try {
            sock = DatagramSocket().apply { broadcast = true; soTimeout = 1000 }
            val msg = "who is JellyfinServer?".toByteArray()
            sock.send(DatagramPacket(msg, msg.size, InetAddress.getByName("255.255.255.255"), 7359))
            val end = System.currentTimeMillis() + 3000
            val buf = ByteArray(2048)
            while (System.currentTimeMillis() < end) {
                try {
                    val pk = DatagramPacket(buf, buf.size)
                    sock.receive(pk)
                    val o = JSONObject(String(pk.data, 0, pk.length))
                    val addr = o.optString("Address", "")
                    if (addr.isNotEmpty()) found[addr] = o.optString("Name", addr)
                } catch (e: java.net.SocketTimeoutException) { }
            }
        } catch (e: Exception) { } finally { sock?.close() }
        found.map { it.value to it.key }
    }

    // ---- Quick Connect: codice mostrato sulla TV, approvato da un altro dispositivo ----
    class QuickCode(val code: String, val secret: String)

    suspend fun quickInitiate(urlIn: String, deviceId: String): Result<QuickCode> = withContext(Dispatchers.IO) {
        try {
            val url = normalizeUrl(urlIn)
            val (c, t) = http("POST", "$url/QuickConnect/Initiate", deviceId, null)
            if (c == 401 || c == 403) return@withContext Result.failure<QuickCode>(Exception("Quick Connect non \u00E8 attivo sul server."))
            if (c != 200) return@withContext Result.failure<QuickCode>(Exception("Quick Connect non disponibile (codice $c)."))
            val o = JSONObject(t)
            Result.success(QuickCode(o.getString("Code"), o.getString("Secret")))
        } catch (e: Exception) { Result.failure<QuickCode>(Exception("Server non raggiungibile.")) }
    }

    suspend fun quickAuthenticated(urlIn: String, deviceId: String, secret: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val (c, t) = http("GET", "${normalizeUrl(urlIn)}/QuickConnect/Connect?secret=$secret", deviceId, null)
            c == 200 && JSONObject(t).optBoolean("Authenticated", false)
        } catch (e: Exception) { false }
    }

    suspend fun quickLogin(urlIn: String, deviceId: String, secret: String): Result<JellySession> = withContext(Dispatchers.IO) {
        try {
            val url = normalizeUrl(urlIn)
            val (c, t) = http("POST", "$url/Users/AuthenticateWithQuickConnect", deviceId, null, JSONObject().put("Secret", secret).toString())
            if (c != 200) return@withContext Result.failure<JellySession>(Exception("Accesso non riuscito (codice $c)."))
            val o = JSONObject(t)
            val u = o.getJSONObject("User")
            Result.success(JellySession(url, o.getString("AccessToken"), u.getString("Id"), u.optString("Name", ""), deviceId))
        } catch (e: Exception) { Result.failure<JellySession>(Exception("Accesso non riuscito.")) }
    }

    fun normalizeUrl(urlIn: String): String {
        var url = urlIn.trim().trimEnd('/')
        if (url.isNotEmpty() && !url.startsWith("http://") && !url.startsWith("https://")) url = "http://$url"
        return url
    }
}
