package com.example.myplayer

import android.Manifest
import android.app.Activity
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private lateinit var ctl: Ctl
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        CrashLog.install(this)
        enableEdgeToEdge()
        ctl = Ctl(applicationContext)
        ctl.connect()
        setContent { App(ctl) }
    }
    override fun onDestroy() { ctl.release(); super.onDestroy() }
}

/** Il primo colore (Unspecified) significa monocromatico: nero in chiaro, bianco in scuro. */
val Accents = listOf(
    Color.Unspecified, Color(0xFF0026FF), Color(0xFF0F8A6F),
    Color(0xFFD85A30), Color(0xFF7A3FD1), Color(0xFFD4537E)
)

fun fmt(ms: Long): String { val t = ms / 1000; return "%d:%02d".format(t / 60, t % 60) }

fun Modifier.blockTouches(): Modifier = pointerInput(Unit) { detectTapGestures { } }

@Composable
fun FadeIn(visible: Boolean, content: @Composable () -> Unit) {
    AnimatedVisibility(visible, enter = fadeIn(tween(200)), exit = fadeOut(tween(150))) { content() }
}

@Composable
fun App(ctl: Ctl) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs by ctx.store.data.collectAsState(initial = null)
    val theme = prefs?.get(K.theme) ?: 0
    val accent = (prefs?.get(K.accent) ?: 0).coerceIn(0, Accents.lastIndex)
    val auto = (prefs?.get(K.autoLyrics) ?: 1) == 1
    val dark = when (theme) { 1 -> false; 2 -> true; else -> isSystemInDarkTheme() }
    val save: (Preferences.Key<Int>, Int) -> Unit = { k, v -> scope.launch { ctx.store.edit { it[k] = v } } }

    var tab by rememberSaveable { mutableIntStateOf(0) }
    var pages by remember { mutableStateOf(listOf<Page>()) }
    var searchOpen by remember { mutableStateOf(false) }
    var crash by remember { mutableStateOf(CrashLog.read(ctx)) }
    val sheet = remember { Animatable(1f) }
    val playerVisible by remember { derivedStateOf { sheet.value < 0.999f } }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        scope.launch { ctl.scan() }
    }
    LaunchedEffect(Unit) {
        val p = if (Build.VERSION.SDK_INT >= 33)
            arrayOf(Manifest.permission.READ_MEDIA_AUDIO, Manifest.permission.POST_NOTIFICATIONS)
        else arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        launcher.launch(p)
    }
    LaunchedEffect(Unit) { while (true) { ctl.refreshPos(); delay(500) } }

    fun push(f: (Int) -> Page) { pages = pages + f(pages.size + 1) }
    val openSettings: () -> Unit = { push { Page.Settings(it) } }
    val openAlbum: (Album) -> Unit = { a -> push { Page.AlbumP(a, it) } }
    val openArtist: (String) -> Unit = { n -> push { Page.Artist(n, it) } }
    val openSearch: () -> Unit = { searchOpen = true }

    BackHandler(pages.isNotEmpty()) { pages = pages.dropLast(1) }
    BackHandler(searchOpen) { searchOpen = false }
    BackHandler(playerVisible) { scope.launch { sheet.animateTo(1f, spring(0.9f, Spring.StiffnessMediumLow)) } }

    AppTheme(dark, Accents[accent]) {
        val p = pal
        val view = LocalView.current
        SideEffect {
            val w = (view.context as Activity).window
            val c = WindowCompat.getInsetsController(w, view)
            c.isAppearanceLightStatusBars = !dark && !playerVisible
            c.isAppearanceLightNavigationBars = false
        }
        val cover = rememberCoverColor(ctl.currentSong?.art, Color(0xFF1A1A1A))
        val dock by animateColorAsState(lerp(cover, Color.Black, 0.8f), tween(600), label = "dock")
        BoxWithConstraints(Modifier.fillMaxSize().background(dock)) {
            val hPx = with(LocalDensity.current) { maxHeight.toPx() }
            val menu = listOf(Triple("Impostazioni", Icons.Rounded.Settings, openSettings))
            Column(Modifier.fillMaxSize().imePadding()) {
                Box(Modifier.weight(1f).fillMaxWidth().clip(RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp)).background(p.bg)) {
                    Box(Modifier.fillMaxSize().statusBarsPadding()) {
                        AnimatedContent(tab, transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(120)) }, label = "tab") { t ->
                            when (t) {
                                0 -> HomeScreen(ctl, openAlbum, openArtist, openSearch, openSettings)
                                1 -> LibraryScreen(
                                    ctl, { push { Page.Liked(it) } }, { id -> push { Page.PlaylistP(id, it) } },
                                    openAlbum, { push { Page.Create(it) } }, openSearch, openSettings
                                )
                                2 -> TracksScreen(ctl, openSearch, openSettings)
                                else -> DashboardScreen(ctl, openAlbum, openSearch, openSettings)
                            }
                        }
                    }
                    AnimatedContent(
                        pages.lastOrNull(),
                        transitionSpec = {
                            val deeper = (targetState?.depth ?: 0) > (initialState?.depth ?: 0)
                            if (deeper) (slideInHorizontally(tween(320)) { it } + fadeIn(tween(250))) togetherWith (slideOutHorizontally(tween(320)) { -it / 4 } + fadeOut(tween(250)))
                            else (slideInHorizontally(tween(320)) { -it / 4 } + fadeIn(tween(250))) togetherWith (slideOutHorizontally(tween(320)) { it } + fadeOut(tween(250)))
                        },
                        label = "pages"
                    ) { pg ->
                        val back = { pages = pages.dropLast(1) }
                        when (pg) {
                            is Page.AlbumP -> DetailPage(
                                pg.album.title, "Album  \u2022  ${pg.album.artist}  \u2022  ${pg.album.songs.size} brani",
                                pg.album.songs[0].art, emptyList(), false, pg.album.songs, true, ctl, back, menu
                            )
                            is Page.Liked -> {
                                val liked = ctl.songs.filter { it.id in ctl.likes }
                                DetailPage("Brani preferiti", "${liked.size} brani", null, emptyList(), true, liked, false, ctl, back, menu)
                            }
                            is Page.PlaylistP -> {
                                val pl = ctl.playlists.firstOrNull { it.id == pg.id }
                                if (pl == null) Box(Modifier.fillMaxSize())
                                else {
                                    val l = ctl.playlistSongs(pl)
                                    DetailPage(
                                        pl.name, "Playlist  \u2022  ${l.size} brani", null, l.map { it.art }, false, l, false, ctl, back,
                                        menu + Triple("Elimina playlist", Icons.Rounded.Delete, { ctl.deletePlaylist(pl.id); back() })
                                    )
                                }
                            }
                            is Page.Artist -> {
                                val l = ctl.artistMap[pg.name] ?: emptyList()
                                DetailPage(pg.name, "${l.size} brani", l.firstOrNull()?.art, emptyList(), false, l, false, ctl, back, menu)
                            }
                            is Page.Settings -> SettingsPage(theme, accent, auto, ctl, back, save)
                            is Page.Create -> CreatePlaylistPage(ctl, back)
                            null -> Box(Modifier.fillMaxSize())
                        }
                    }
                    FadeIn(searchOpen) { SearchPage(ctl, { searchOpen = false }) { a -> searchOpen = false; openAlbum(a) } }
                }
                Column(Modifier.navigationBarsPadding()) {
                    AnimatedVisibility(ctl.currentSong != null, enter = slideInVertically(tween(300)) { it } + fadeIn(), exit = slideOutVertically(tween(250)) { it } + fadeOut()) {
                        MiniBar(ctl, sheet, hPx)
                    }
                    NavBar(tab) { tab = it; pages = emptyList() }
                }
            }
            if (playerVisible) PlayerSheet(ctl, sheet, auto) { a ->
                scope.launch { sheet.animateTo(1f, spring(0.9f, Spring.StiffnessMediumLow)) }
                pages = listOf(Page.AlbumP(a, 1))
            }
            crash?.let { CrashScreen(it) { crash = null; CrashLog.clear(ctx) } }
        }
    }
}
