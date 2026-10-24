package com.example.myplayer

import android.Manifest
import android.app.Activity
import android.app.RecoverableSecurityException
import android.content.IntentSender
import android.os.Build
import android.provider.MediaStore
import androidx.activity.result.IntentSenderRequest
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
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
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
/** Versione scura ma chiaramente colorata della copertina, per la fascia del mini player. */
fun dockTone(c: Color): Color {
    val hsv = FloatArray(3)
    android.graphics.Color.RGBToHSV((c.red * 255).toInt(), (c.green * 255).toInt(), (c.blue * 255).toInt(), hsv)
    if (hsv[1] < 0.12f) { hsv[1] = 0f; hsv[2] = 0.07f }
    else { hsv[1] = hsv[1].coerceIn(0.45f, 0.9f); hsv[2] = 0.40f }
    return Color(android.graphics.Color.HSVToColor(hsv))
}
/** Il primo colore (Unspecified) significa monocromatico: nero in chiaro, bianco in scuro. */
val Accents = listOf(
    Color.Unspecified, Color(0xFF0026FF), Color(0xFFD63AF0), Color(0xFF2EBF4F), Color(0xFFC00000)
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
    val navStyle = prefs?.get(K.navStyle) ?: 0
    val hiddenMask = prefs?.get(K.hiddenMask) ?: 0
    val coloured = (prefs?.get(K.coloured) ?: 1) == 1
    val amoledPref = (prefs?.get(K.amoled) ?: 0) == 1
    val crop = (prefs?.get(K.crop) ?: 1) == 1
    val lyricCenter = (prefs?.get(K.lyricAlign) ?: 0) == 1
    val dark = when (theme) { 1 -> false; 2 -> true; else -> isSystemInDarkTheme() }
    val save: (Preferences.Key<Int>, Int) -> Unit = { k, v -> scope.launch { ctx.store.edit { it[k] = v } } }

    var tab by rememberSaveable { mutableIntStateOf(0) }
    var pages by remember { mutableStateOf(listOf<Page>()) }
    var searchOpen by remember { mutableStateOf(false) }
    var createOpen by remember { mutableStateOf(false) }
    var resetOpen by remember { mutableStateOf(false) }
    var crash by remember { mutableStateOf(CrashLog.read(ctx)) }
    var quick by remember { mutableStateOf<Quick?>(null) }
    var editPlaylist by remember { mutableStateOf<Playlist?>(null) }
    var editPick by remember { mutableStateOf(false) }
    var editArtist by remember { mutableStateOf<String?>(null) }
    val delLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { scope.launch { ctl.scan() } }
    fun deleteFromDevice(list: List<Song>) {
        val uris = list.map { it.uri }
        try {
            if (Build.VERSION.SDK_INT >= 30) {
                val pi = MediaStore.createDeleteRequest(ctx.contentResolver, uris)
                delLauncher.launch(IntentSenderRequest.Builder(pi.intentSender).build())
            } else {
                var need: IntentSender? = null
                uris.forEach { u ->
                    try { ctx.contentResolver.delete(u, null, null) }
                    catch (e: SecurityException) {
                        if (Build.VERSION.SDK_INT >= 29 && e is RecoverableSecurityException) need = e.userAction.actionIntent.intentSender
                    }
                }
                val n = need
                if (n != null) delLauncher.launch(IntentSenderRequest.Builder(n).build()) else scope.launch { ctl.scan() }
            }
        } catch (e: Exception) { }
    }
    val sheet = remember { Animatable(1f).apply { updateBounds(0f, 1f) } }
    val playerVisible by remember { derivedStateOf { sheet.value < 0.999f } }

    val tabs = listOf(0, 1, 2, 3).filter { it == 0 || (hiddenMask and (1 shl it)) == 0 }
    val curTab = if (tab in tabs) tab else 0

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
    val openSettings: () -> Unit = { push { Page.Settings(0, it) } }
    val openAlbum: (Album) -> Unit = { a -> push { Page.AlbumP(a, it) } }
    val openArtist: (String) -> Unit = { n -> push { Page.Artist(n, it) } }
    val openSearch: () -> Unit = { searchOpen = true }

    BackHandler(pages.isNotEmpty()) { pages = pages.dropLast(1) }
    BackHandler(searchOpen) { searchOpen = false }
    BackHandler(createOpen || editPlaylist != null) { createOpen = false; editPlaylist = null }
    BackHandler(resetOpen) { resetOpen = false }
    BackHandler(quick != null) { quick = null }
    BackHandler(editArtist != null) { editArtist = null }
    BackHandler(playerVisible) { scope.launch { sheet.animateTo(1f, spring(0.9f, Spring.StiffnessMediumLow)) } }

    AppTheme(dark, Accents[accent], amoledPref && dark, crop) {
        val p = pal
        val pill = navStyle == 1
        val view = LocalView.current
        SideEffect {
            val w = (view.context as Activity).window
            val c = WindowCompat.getInsetsController(w, view)
            c.isAppearanceLightStatusBars = !dark && !playerVisible
            c.isAppearanceLightNavigationBars = pill && !dark && !playerVisible
        }
        val cover = rememberCoverColor(ctl.currentSong?.art, Color(0xFF070707))
        val dockTarget = if (coloured && ctl.currentSong != null) dockTone(cover) else Color(0xFF070707)
        val dock by animateColorAsState(dockTarget, tween(600), label = "dock")
        CompositionLocalProvider(LocalQuick provides { q: Quick -> quick = q }) {
        BoxWithConstraints(Modifier.fillMaxSize().background(if (pill) p.bg else dock)) {
            val hPx = with(LocalDensity.current) { maxHeight.toPx() }
            Column(Modifier.fillMaxSize().imePadding()) {
                Box(
                    Modifier.weight(1f).fillMaxWidth()
                        .graphicsLayer {
                            // il bordo inferiore della pagina segue il bordo superiore del player
                            val top = sheet.value.coerceIn(0f, 1f) * hPx
                            translationY = minOf(0f, top - size.height)
                        }
                        .then(if (pill) Modifier else Modifier.clip(RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp)))
                        .background(p.bg)
                ) {
                    Box(Modifier.fillMaxSize().statusBarsPadding()) {
                        AnimatedContent(curTab, transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(120)) }, label = "tab") { t ->
                            when (t) {
                                0 -> HomeScreen(ctl, openAlbum, openArtist, openSearch, openSettings)
                                1 -> PlaylistScreen(
                                    ctl, { push { Page.Liked(it) } }, { id -> push { Page.PlaylistP(id, it) } },
                                    { createOpen = true }, openSearch, openSettings
                                )
                                2 -> LibraryHub(ctl, openAlbum, { id -> push { Page.FolderP(id, it) } }, openArtist, openSearch, openSettings)
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
                        val menu = listOf(Triple("Impostazioni", Icons.Rounded.Settings, openSettings))
                        when (pg) {
                            is Page.AlbumP -> DetailPage(
                                pg.album.title, "Album  \u2022  ${pg.album.artist}  \u2022  ${pg.album.songs.size} brani",
                                pg.album.songs[0].art, emptyList(), false, pg.album.songs, true, ctl, back, menu
                            ) { quick = Quick.AlbumQ(pg.album) }
                            is Page.Liked -> {
                                val liked = ctl.songs.filter { it.id in ctl.likes }
                                DetailPage("Brani preferiti", "${liked.size} brani", null, emptyList(), true, liked, false, ctl, back, menu)
                            }
                            is Page.PlaylistP -> {
                                val pl = ctl.playlists.firstOrNull { it.id == pg.id }
                                if (pl == null) { LaunchedEffect(Unit) { pages = pages.dropLast(1) }; Box(Modifier.fillMaxSize()) }
                                else {
                                    val l = ctl.playlistSongs(pl)
                                    val sub = listOf(pl.desc, "Playlist  \u2022  ${l.size} brani").filter { it.isNotBlank() }.joinToString("\n")
                                    CompositionLocalProvider(LocalPlaylistCtx provides pl.id) {
                                        DetailPage(pl.name, sub, playlistCover(pl), l.map { it.art }, false, l, false, ctl, back, menu) { quick = Quick.PlaylistQ(pl) }
                                    }
                                }
                            }
                            is Page.FolderP -> {
                                val f = ctl.folders.firstOrNull { it.id == pg.id }
                                if (f == null) Box(Modifier.fillMaxSize())
                                else FolderPage(f, ctl, back, openAlbum) { ctl.deleteFolder(f.id); back() }
                            }
                            is Page.Artist -> ArtistPage(pg.name, ctl, back, openAlbum) { quick = Quick.ArtistQ(pg.name) }
                            is Page.Settings -> when (pg.kind) {
                                0 -> SettingsMain(back, { k -> push { Page.Settings(k, it) } }, { resetOpen = true })
                                1 -> StylePage(theme, accent, coloured, amoledPref, crop, dark, back, save)
                                2 -> LyricsSettingsPage(auto, lyricCenter, ctl, back, save)
                                3 -> NavSettingsPage(navStyle, hiddenMask, back, save)
                                4 -> DataPage(ctl, back)
                                5 -> FoldersPage(ctl, back)
                                else -> AppVersionPage(back)
                            }
                            null -> Box(Modifier.fillMaxSize())
                        }
                    }
                    FadeIn(searchOpen) { SearchPage(ctl, { searchOpen = false }) { a -> searchOpen = false; openAlbum(a) } }
                }
                Column(Modifier.navigationBarsPadding().then(if (pill) Modifier.background(p.bg) else Modifier)) {
                    AnimatedVisibility(ctl.currentSong != null, enter = slideInVertically(tween(300)) { it } + fadeIn(), exit = slideOutVertically(tween(250)) { it } + fadeOut()) {
                        MiniBar(ctl, sheet, hPx, pill, dock)
                    }
                    NavBar(tabs, curTab, !pill) { tab = it; pages = emptyList() }
                }
            }
            CreatePlaylistSheet(ctl, createOpen || editPlaylist != null, editPlaylist, editPick) { createOpen = false; editPlaylist = null }
            QuickMenuHost(ctl, quick, { quick = it }, { deleteFromDevice(it) }, { pl, pick -> editPlaylist = pl; editPick = pick }, { createOpen = true }, { n -> editArtist = n })
            EditArtistSheet(ctl, editArtist) { editArtist = null }
            ResetSheet(resetOpen, { resetOpen = false }) {
                ctl.resetAll(); CoverCache.cache.evictAll(); resetOpen = false; pages = emptyList()
            }
            if (playerVisible) PlayerSheet(ctl, sheet, auto, lyricCenter) { a ->
                scope.launch { sheet.animateTo(1f, spring(0.9f, Spring.StiffnessMediumLow)) }
                pages = listOf(Page.AlbumP(a, 1))
            }
            crash?.let { CrashScreen(it) { crash = null; CrashLog.clear(ctx) } }
        }
        }
    }
}
