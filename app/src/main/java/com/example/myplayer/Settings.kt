package com.example.myplayer

import android.os.Environment
import android.os.StatFs
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.delay
import java.io.File

fun bytesFmt(b: Long): String {
    val gb = b / (1024.0 * 1024.0 * 1024.0)
    return if (gb >= 1.0) "%.2f GB".format(gb) else "%.1f MB".format(b / (1024.0 * 1024.0))
}

@Composable
fun SettingsBar(title: String, back: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        IconBtn(Icons.Rounded.ArrowBack, back)
        Txt(title, Modifier.padding(start = 4.dp), TS.h2)
    }
}

@Composable
fun SetLabel(icon: ImageVector?, text: String) {
    val p = pal
    Row(Modifier.padding(top = 20.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        if (icon != null) { Ico(icon, tint = p.sub, size = 18.dp); Spacer(Modifier.width(8.dp)) }
        Txt(text, style = TS.label, color = p.sub)
    }
}

@Composable
fun SetRow(icon: ImageVector, title: String, sub: String, enabled: Boolean = true, danger: Boolean = false, onClick: () -> Unit = {}) {
    val p = pal
    val tint = if (danger) Color(0xFFC00000) else p.fg
    Row(
        Modifier.fillMaxWidth().then(if (enabled) Modifier.pressable(0.98f, onClick) else Modifier).clip(RoundedCornerShape(16.dp))
            .background(if (danger) Color(0xFFC00000).copy(alpha = 0.08f) else p.card).padding(14.dp)
            .graphicsAlpha(if (enabled) 1f else 0.45f),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Ico(icon, tint = tint, size = 22.dp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Txt(title, style = TS.title, color = tint, maxLines = 1)
            Txt(if (enabled) sub else "Prossimamente", style = TS.small, color = if (danger) tint.copy(alpha = 0.8f) else p.sub, maxLines = 2)
        }
        if (enabled && !danger) Ico(Icons.Rounded.ChevronRight, tint = p.sub)
    }
}

fun Modifier.graphicsAlpha(a: Float): Modifier = this.alpha(a)

@Composable
fun ToggleRow(icon: ImageVector?, title: String, sub: String, on: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    val p = pal
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(p.card).padding(14.dp).graphicsAlpha(if (enabled) 1f else 0.45f),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) { Ico(icon, size = 22.dp); Spacer(Modifier.width(14.dp)) }
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Txt(title, style = TS.title)
            Txt(sub, style = TS.small, color = p.sub)
        }
        if (enabled) Toggle(on, onChange) else Toggle(false) { }
    }
}

@Composable
fun SettingsMain(back: () -> Unit, open: (Int) -> Unit, onReset: () -> Unit) {
    val p = pal
    Box(Modifier.fillMaxSize().background(p.bg).blockTouches()) {
        Column(Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            SettingsBar("Impostazioni", back)
            SetLabel(null, "Personalizzazione")
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SetRow(Icons.Rounded.Brush, "Stile dell'app", "Tutta la personalizzazione dell'interfaccia") { open(1) }
                SetRow(Icons.Rounded.Notes, "Testi", "Sincronizzazione e stile dei testi") { open(2) }
                SetRow(Icons.Rounded.Navigation, "Navigazione", "Stile del mini player e pagine visibili") { open(3) }
                SetRow(Icons.Rounded.Tune, "Avanzate", "Altre opzioni per la navigazione", enabled = false)
                SetRow(Icons.Rounded.Science, "Strumenti sperimentali", "Roba non ancora ottimizzata", enabled = false)
            }
            SetLabel(null, "Dati")
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SetRow(Icons.Rounded.Storage, "Dati", "Controlla l'uso dei dati dell'app") { open(4) }
                SetRow(Icons.Rounded.Folder, "Cartelle", "Opzioni avanzate per musica e cartelle", enabled = false)
            }
            SetLabel(null, "Informazioni")
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SetRow(Icons.Rounded.Info, "Crediti", "Chi c'\u00E8 dietro il progetto", enabled = false)
                SetRow(Icons.Rounded.Code, "Pagina GitHub", "Scopri le novit\u00E0 dell'app", enabled = false)
                SetRow(Icons.Rounded.ChatBubble, "Suggerimenti e idee", "Hai un'idea o un bug? Scrivimi", enabled = false)
                SetRow(Icons.Rounded.PhoneAndroid, "Versione dell'app", "Controlla versione e build", enabled = false)
            }
            Txt("Zona pericolosa", Modifier.padding(top = 24.dp, bottom = 10.dp), TS.label, Color(0xFFC00000))
            SetRow(Icons.Rounded.Warning, "Resetta tutto", "Attenzione: cancella tutti i dati dell'app.", danger = true, onClick = onReset)
            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
fun ModeCard(dark: Boolean, selected: Boolean, label: String, modifier: Modifier, onClick: () -> Unit) {
    val p = pal
    Column(modifier.pressable(0.97f, onClick), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.fillMaxWidth().height(110.dp).clip(RoundedCornerShape(14.dp))
                .background(if (dark) Color(0xFF111111) else Color(0xFFF0F0F0))
                .border(if (selected) 2.dp else 0.dp, p.fg, RoundedCornerShape(14.dp)).padding(10.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(Modifier.width(50.dp).height(8.dp).clip(CircleShape).background(if (dark) Color(0xFFF8F8F8) else Color(0xFF040404)))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    repeat(2) { Box(Modifier.size(42.dp).clip(RoundedCornerShape(6.dp)).background(if (dark) Color(0xFF2A2A2A) else Color(0xFFD8D8D8))) }
                }
                Box(Modifier.fillMaxWidth().height(10.dp).clip(CircleShape).background(if (dark) Color(0xFF2A2A2A) else Color(0xFFD8D8D8)))
            }
        }
        Spacer(Modifier.height(6.dp))
        Txt(label, style = TS.label)
    }
}

@Composable
fun StylePage(theme: Int, accent: Int, coloured: Boolean, amoled: Boolean, crop: Boolean, dark: Boolean, back: () -> Unit, save: (Preferences.Key<Int>, Int) -> Unit) {
    val p = pal
    Box(Modifier.fillMaxSize().background(p.bg).blockTouches()) {
        Column(Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            SettingsBar("Stile dell'app", back)
            SetLabel(Icons.Rounded.Visibility, "Modalit\u00E0")
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ModeCard(false, !dark, "Chiara", Modifier.weight(1f)) { save(K.theme, 1) }
                ModeCard(true, dark, "Scura", Modifier.weight(1f)) { save(K.theme, 2) }
            }
            SetLabel(Icons.Rounded.Palette, "Colore")
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(p.card).padding(14.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Accents.forEachIndexed { i, c ->
                    Box(
                        Modifier.size(42.dp).pressable(0.9f) { save(K.accent, i) }.clip(CircleShape)
                            .background(if (c == Color.Unspecified) p.fg else c).border(if (i == accent) 3.dp else 0.dp, p.sub, CircleShape),
                        contentAlignment = Alignment.CenterEnd
                    ) { if (c == Color.Unspecified) Box(Modifier.fillMaxHeight().width(21.dp).background(p.bg)) }
                }
            }
            SetLabel(Icons.Rounded.Tune, "Impostazioni avanzate")
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ToggleRow(Icons.Rounded.PhoneAndroid, "Segui le preferenze del dispositivo", "L'app segue chiaro o scuro del telefono", theme == 0) { on ->
                    save(K.theme, if (on) 0 else if (dark) 2 else 1)
                }
                ToggleRow(Icons.Rounded.Palette, "Sfondo colorato", "Il fondo di barra e mini player prende il colore della copertina in riproduzione", coloured) { save(K.coloured, if (it) 1 else 0) }
            }
            SetLabel(Icons.Rounded.MoreHoriz, "Altre preferenze")
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ToggleRow(Icons.Rounded.DarkMode, "Modalit\u00E0 scura AMOLED", "Disponibile solo con la modalit\u00E0 scura attiva: nero puro", amoled, enabled = dark) { save(K.amoled, if (it) 1 else 0) }
                ToggleRow(Icons.Rounded.Crop, "Ritaglio delle copertine", "Le copertine non quadrate vengono ritagliate in 1:1", crop) { save(K.crop, if (it) 1 else 0) }
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
fun LyricsSettingsPage(auto: Boolean, center: Boolean, ctl: Ctl, back: () -> Unit, save: (Preferences.Key<Int>, Int) -> Unit) {
    val p = pal
    val ctx = LocalContext.current
    Box(Modifier.fillMaxSize().background(p.bg).blockTouches()) {
        Column(Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            SettingsBar("Testi", back)
            SetLabel(Icons.Rounded.Visibility, "Vista")
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                for ((idx, label, icon) in listOf(Triple(0, "A sinistra", Icons.Rounded.FormatAlignLeft), Triple(1, "Al centro", Icons.Rounded.FormatAlignCenter))) {
                    val sel = (idx == 1) == center
                    Box(
                        Modifier.weight(1f).height(80.dp).pressable(0.97f) { save(K.lyricAlign, idx) }.clip(RoundedCornerShape(14.dp)).background(p.card)
                            .border(if (sel) 2.dp else 0.dp, p.fg, RoundedCornerShape(14.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) { Ico(icon, size = 28.dp); Spacer(Modifier.height(4.dp)); Txt(label, style = TS.label) }
                    }
                }
            }
            SetLabel(Icons.Rounded.Sync, "Sincronizzazione")
            ToggleRow(Icons.Rounded.Download, "Sincronizzazione automatica", "All'avvio l'app cerca i testi dei brani che ascolti", auto) { save(K.autoLyrics, if (it) 1 else 0) }
            Txt("I testi arrivano da LRCLIB, un database gratuito e collaborativo, e restano salvati nell'app. Usali solo per ascolto personale.", Modifier.padding(vertical = 10.dp), TS.small, p.sub)
            SetLabel(Icons.Rounded.Sync, "Sincronizzazione manuale")
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(p.card).padding(14.dp)) {
                Txt("Manca qualcosa?", style = TS.title)
                Txt("Nessun problema: premi il pulsante qui sotto e l'app sincronizzer\u00E0 i testi per te.", Modifier.padding(vertical = 6.dp), TS.small, p.sub)
                PillBtn("Sincronizza i testi", null, { if (!Lyrics.running) Lyrics.downloadAll(ctx.applicationContext, ctl.songs) }, Modifier.fillMaxWidth())
                if (!Lyrics.running && Lyrics.status.isNotEmpty()) Txt(Lyrics.status, Modifier.padding(top = 10.dp), TS.small, p.sub)
            }
            Spacer(Modifier.height(32.dp))
        }
        Sheet(Lyrics.running, { }, floating = true) {
            Column(Modifier.padding(20.dp)) {
                Txt("Sincronizzazione...", style = TS.h3)
                Txt("L'app sta cercando i testi nel database, abbi pazienza finch\u00E9 non ha finito.", Modifier.padding(vertical = 8.dp), TS.small, p.sub)
                val f = if (Lyrics.total > 0) Lyrics.done.toFloat() / Lyrics.total else 0f
                Box(Modifier.fillMaxWidth().height(6.dp).clip(CircleShape).background(p.line)) { Box(Modifier.fillMaxWidth(f.coerceIn(0f, 1f)).fillMaxHeight().background(p.accent)) }
                Txt("Trovati: ${Lyrics.found} su ${Lyrics.total}", Modifier.padding(vertical = 10.dp), TS.small, p.fg)
                Box(
                    Modifier.fillMaxWidth().height(50.dp).pressable(0.97f) { Lyrics.cancel() }.clip(RoundedCornerShape(25.dp)).background(Color(0xFFC00000)),
                    contentAlignment = Alignment.Center
                ) { Txt("Interrompi la sincronizzazione", style = TS.title, color = Color.White) }
            }
        }
    }
}

@Composable
fun NavSettingsPage(navStyle: Int, hiddenMask: Int, back: () -> Unit, save: (Preferences.Key<Int>, Int) -> Unit) {
    val p = pal
    @Composable
    fun StyleCard(idx: Int, label: String, sub: String, modifier: Modifier) {
        val sel = navStyle == idx
        Column(modifier.pressable(0.97f) { save(K.navStyle, idx) }) {
            Box(
                Modifier.fillMaxWidth().height(120.dp).clip(RoundedCornerShape(14.dp)).background(p.card)
                    .border(if (sel) 2.dp else 0.dp, p.fg, RoundedCornerShape(14.dp))
            ) {
                if (idx == 0) {
                    Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(46.dp).background(Color(0xFF1B2412)))
                } else {
                    Box(Modifier.align(Alignment.BottomCenter).padding(bottom = 28.dp, start = 10.dp, end = 10.dp).fillMaxWidth().height(24.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFF1B2412)))
                }
            }
            Txt(label, Modifier.padding(top = 6.dp), TS.label)
            Txt(sub, style = TS.small, color = p.sub)
        }
    }
    Box(Modifier.fillMaxSize().background(p.bg).blockTouches()) {
        Column(Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            SettingsBar("Navigazione", back)
            SetLabel(Icons.Rounded.PlayArrow, "Stile del mini player")
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StyleCard(0, "Tutto in uno", "Il player prende il colore della barra", Modifier.weight(1f))
                StyleCard(1, "Pillola", "Il player \u00E8 una pillola sopra la barra", Modifier.weight(1f))
            }
            SetLabel(Icons.Rounded.ContentCopy, "Pagine")
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(p.card).padding(vertical = 4.dp)) {
                val pages = listOf(Triple(0, "Home", Icons.Rounded.Home), Triple(1, "Library", Icons.Rounded.FolderOpen), Triple(2, "Brani", Icons.Rounded.MusicNote), Triple(3, "Dashboard", Icons.Rounded.GridView))
                pages.forEach { (id, name, icon) ->
                    val locked = id == 0
                    val on = locked || (hiddenMask and (1 shl id)) == 0
                    Row(
                        Modifier.fillMaxWidth().then(if (!locked) Modifier.pressable(0.99f) { save(K.hiddenMask, hiddenMask xor (1 shl id)) } else Modifier).padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Ico(icon, tint = if (locked) p.sub else p.fg)
                        Spacer(Modifier.width(14.dp))
                        Txt(name, Modifier.weight(1f), TS.title, if (locked) p.sub else p.fg)
                        if (locked) Txt("non removibile", style = TS.small, color = p.sub)
                        else Box(Modifier.size(24.dp).clip(RoundedCornerShape(7.dp)).background(if (on) p.accent else Color.Transparent).border(2.dp, if (on) p.accent else p.line, RoundedCornerShape(7.dp)), contentAlignment = Alignment.Center) {
                            if (on) Ico(Icons.Rounded.Check, tint = p.onAccent, size = 16.dp)
                        }
                    }
                }
                Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp).graphicsAlpha(0.45f), verticalAlignment = Alignment.CenterVertically) {
                    Ico(Icons.Rounded.Person)
                    Spacer(Modifier.width(14.dp))
                    Txt("Artisti", Modifier.weight(1f), TS.title)
                    Txt("prossimamente", style = TS.small, color = p.sub)
                }
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
fun DataPage(ctl: Ctl, back: () -> Unit) {
    val p = pal
    val ctx = LocalContext.current
    val st = remember { StatFs(Environment.getDataDirectory().path) }
    val total = st.totalBytes
    val free = st.availableBytes
    val tracks = ctl.songs.sumOf { it.size }
    val device = (total - free - tracks).coerceAtLeast(0L)
    var cacheSize by remember { mutableStateOf(0L) }
    fun dirSize(f: File): Long = if (f.isFile) f.length() else (f.listFiles()?.sumOf { dirSize(it) } ?: 0L)
    LaunchedEffect(Unit) { cacheSize = dirSize(ctx.cacheDir) + CoverCache.cache.size().toLong() }
    Box(Modifier.fillMaxSize().background(p.bg).blockTouches()) {
        Column(Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            SettingsBar("Dati", back)
            SetLabel(Icons.Rounded.Storage, "Spazio")
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(p.card).padding(16.dp)) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Txt(bytesFmt(tracks), Modifier.weight(1f), TS.big)
                    Txt("${bytesFmt(total)} in totale", style = TS.small, color = p.sub)
                }
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth().height(8.dp).clip(CircleShape).background(p.line)) {
                    val t = total.coerceAtLeast(1L).toFloat()
                    if (tracks > 0) Box(Modifier.weight((tracks / t).coerceAtLeast(0.02f)).fillMaxHeight().background(p.accent))
                    if (device > 0) Box(Modifier.weight((device / t).coerceAtLeast(0.02f)).fillMaxHeight().background(p.fg.copy(alpha = 0.35f)))
                    Box(Modifier.weight((free / t).coerceAtLeast(0.02f)).fillMaxHeight())
                }
                Spacer(Modifier.height(12.dp))
                listOf("Brani" to tracks, "Il tuo dispositivo" to device, "Spazio libero" to free).forEach { (k, v) ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) { Txt(k, Modifier.weight(1f), TS.title); Txt(bytesFmt(v), style = TS.body, color = p.sub) }
                }
            }
            if (ctl.hiddenAlbums.isNotEmpty()) {
                SetLabel(Icons.Rounded.VisibilityOff, "Album nascosti")
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(p.card).padding(16.dp)) {
                    Txt("${ctl.hiddenAlbums.size} album nascosti", style = TS.title)
                    Txt("Non compaiono in Library, Home e ricerca. I loro brani restano nell'elenco Brani.", Modifier.padding(vertical = 6.dp), TS.small, p.sub)
                    PillBtn("Mostra di nuovo tutti", null, { ctl.unhideAll() }, Modifier.fillMaxWidth())
                }
            }
            SetLabel(Icons.Rounded.Layers, "Cache")
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(p.card).padding(16.dp)) {
                Txt("Pulisci la cache", style = TS.title)
                Txt("Svuota la cache temporanea per mantenere l'app veloce. Copertine e testi salvati non vengono toccati.", Modifier.padding(vertical = 6.dp), TS.small, p.sub)
                PillBtn("Pulisci la cache  ${bytesFmt(cacheSize)}", null, {
                    ctx.cacheDir.listFiles()?.forEach { it.deleteRecursively() }
                    CoverCache.cache.evictAll()
                    cacheSize = 0L
                }, Modifier.fillMaxWidth())
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
fun ResetSheet(visible: Boolean, onClose: () -> Unit, onConfirm: () -> Unit) {
    val p = pal
    Sheet(visible, onClose, floating = true) {
        Column(Modifier.padding(20.dp)) {
            Txt("Resettare tutto?", style = TS.h3, color = Color(0xFFC00000))
            Txt("Cancella preferiti, playlist, cartelle, statistiche, testi scaricati e impostazioni. Non si pu\u00F2 annullare.", Modifier.padding(vertical = 10.dp), TS.small, p.sub)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.weight(1f).height(50.dp).pressable(0.97f) { onClose() }.clip(RoundedCornerShape(25.dp)).background(p.card), contentAlignment = Alignment.Center) { Txt("Annulla", style = TS.title) }
                Box(Modifier.weight(1f).height(50.dp).pressable(0.97f) { onConfirm() }.clip(RoundedCornerShape(25.dp)).background(Color(0xFFC00000)), contentAlignment = Alignment.Center) { Txt("Resetta", style = TS.title, color = Color.White) }
            }
        }
    }
}

@Composable
fun CreatePlaylistSheet(ctl: Ctl, visible: Boolean, editing: Playlist?, startPicking: Boolean, onClose: () -> Unit) {
    val p = pal
    var name by remember { mutableStateOf("") }
    var desc by remember { mutableStateOf("") }
    var cover by remember { mutableStateOf<Uri?>(null) }
    var picked by remember { mutableStateOf(listOf<Long>()) }
    var picking by remember { mutableStateOf(false) }
    var coverChanged by remember { mutableStateOf(false) }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri -> if (uri != null) { cover = uri; coverChanged = true } }
    LaunchedEffect(visible, editing?.id) {
        if (visible) {
            if (editing != null) { name = editing.name; desc = editing.desc; cover = playlistCover(editing); picked = editing.songIds; picking = startPicking }
            else { name = ""; desc = ""; cover = null; picked = emptyList(); picking = false }
            coverChanged = false
        } else { delay(450); name = ""; desc = ""; cover = null; picked = emptyList(); picking = false; coverChanged = false }
    }
    val ready = name.isNotBlank() && picked.isNotEmpty()
    @Composable
    fun Field(value: String, hint: String, onChange: (String) -> Unit, single: Boolean, h: Dp?) {
        Box(
            Modifier.fillMaxWidth().then(if (h != null) Modifier.height(h) else Modifier).clip(RoundedCornerShape(14.dp)).background(p.card).padding(14.dp),
            contentAlignment = if (single) Alignment.CenterStart else Alignment.TopStart
        ) {
            BasicTextField(
                value, onChange, singleLine = single, textStyle = TS.body.copy(color = p.fg), cursorBrush = SolidColor(p.fg), modifier = Modifier.fillMaxWidth(),
                decorationBox = { inner -> Box { if (value.isEmpty()) Txt(hint, color = p.sub, style = TS.small); inner() } }
            )
        }
    }
    Sheet(visible, onClose, fullHeight = true) {
        if (picking) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconBtn(Icons.Rounded.ArrowBack, { picking = false })
                Txt("Scegli i brani (${picked.size})", Modifier.weight(1f), TS.h3)
                PillBtn("Fatto", null, { picking = false })
            }
            LazyColumn(Modifier.weight(1f)) {
                items(ctl.songs) { s ->
                    val on = s.id in picked
                    val bg by animateColorAsState(if (on) p.accent else Color.Transparent, tween(200), label = "ck")
                    Row(
                        Modifier.fillMaxWidth().pressable(0.98f) { picked = if (on) picked - s.id else picked + s.id }.padding(horizontal = 16.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Cover(s.art, Modifier.size(46.dp))
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Txt(s.title, style = TS.title, maxLines = 1)
                            Txt(s.artists.joinToString(" / "), style = TS.small, color = p.sub, maxLines = 1)
                        }
                        Box(Modifier.size(26.dp).clip(CircleShape).background(bg).border(2.dp, if (on) p.accent else p.line, CircleShape), contentAlignment = Alignment.Center) {
                            if (on) Ico(Icons.Rounded.Check, tint = p.onAccent, size = 16.dp)
                        }
                    }
                }
            }
        } else {
            Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Txt(if (editing != null) "Modifica la playlist" else "Crea una playlist", Modifier.weight(1f), TS.h2)
                IconBtn(Icons.Rounded.Close, onClose)
            }
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Spacer(Modifier.height(12.dp))
                Box(Modifier.size(172.dp).pressable(0.97f) { gallery.launch("image/*") }.clip(RoundedCornerShape(16.dp)).background(p.card), contentAlignment = Alignment.Center) {
                    val c = cover
                    if (c != null) Cover(c, Modifier.fillMaxSize())
                    else Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Ico(Icons.Rounded.AddPhotoAlternate, size = 30.dp)
                        Spacer(Modifier.height(8.dp))
                        Txt("Aggiungi un'immagine", style = TS.title)
                        Txt("Scegli una copertina dalla galleria", Modifier.padding(horizontal = 14.dp), TS.small, p.sub, align = androidx.compose.ui.text.style.TextAlign.Center)
                    }
                }
                Column(Modifier.fillMaxWidth()) {
                    SetLabel(Icons.Rounded.Edit, "Dettagli della playlist")
                    Txt("Nome", Modifier.padding(bottom = 6.dp), TS.title)
                    Field(name, "Nome della playlist", { name = it.take(40) }, true, null)
                    Txt("Descrizione", Modifier.padding(top = 14.dp, bottom = 6.dp), TS.title)
                    Field(desc, "Scrivi una descrizione di questa playlist", { desc = it.take(200) }, false, 110.dp)
                    SetLabel(Icons.Rounded.Edit, "Brani")
                    Row(
                        Modifier.fillMaxWidth().pressable(0.98f) { picking = true }, verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(Modifier.size(52.dp).clip(RoundedCornerShape(12.dp)).background(p.card), contentAlignment = Alignment.Center) { Ico(Icons.Rounded.Add) }
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Txt("Aggiungi i brani a questa playlist", style = TS.title)
                            Txt(if (picked.isEmpty()) "Scegli cosa vuoi aggiungere" else "${picked.size} brani scelti", style = TS.small, color = p.sub)
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                }
            }
            Box(Modifier.fillMaxWidth().padding(16.dp)) {
                PillBtn(if (editing != null) "Salva le modifiche" else "Crea la playlist", null, {
                    if (ready) {
                        if (editing != null) ctl.updatePlaylist(editing.id, name, desc, picked, if (coverChanged) cover else null)
                        else ctl.createPlaylist(name, desc, picked, cover)
                        onClose()
                    }
                }, Modifier.fillMaxWidth(), filled = ready)
            }
        }
    }
}
