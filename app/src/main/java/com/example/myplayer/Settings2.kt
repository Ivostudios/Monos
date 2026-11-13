package com.example.myplayer

import android.net.Uri
import android.provider.DocumentsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
private fun FolderRow(name: String, size: String, onRemove: (() -> Unit)?) {
    val p = pal
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Ico(Icons.Rounded.FolderOpen)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Txt(name, style = TS.title, maxLines = 1)
            Txt(size, style = TS.small, color = p.sub)
        }
        if (onRemove != null) IconBtn(Icons.Rounded.Close, onRemove, tint = p.sub, box = 40.dp, size = 20.dp)
    }
}

@Composable
fun FoldersPage(ctl: Ctl, back: () -> Unit) {
    val p = pal
    fun toRel(uri: Uri): String? {
        val id = try { DocumentsContract.getTreeDocumentId(uri) } catch (e: Exception) { return null }
        val rel = id.substringAfter(':', "").trim('/')
        return if (rel.isEmpty()) null else "$rel/"
    }
    val addWhite = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let { toRel(it) }?.let { ctl.setFolderLists(ctl.white + it, ctl.black - it) }
    }
    val addBlack = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let { toRel(it) }?.let { ctl.setFolderLists(ctl.white - it, ctl.black + it) }
    }
    fun sizeOf(path: String) = ctl.rawSongs.filter { it.dir.startsWith(path) }.sumOf { it.size }
    Box(Modifier.fillMaxSize().background(p.bg).blockTouches()) {
        Column(Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            SettingsBar("Cartelle", back)
            SetLabel(Icons.Rounded.Check, "Cartelle attive")
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(p.card).padding(horizontal = 16.dp, vertical = 6.dp)) {
                if (ctl.white.isEmpty()) FolderRow("Tutte le cartelle", bytesFmt(ctl.rawSongs.sumOf { it.size }), null)
                else ctl.white.sorted().forEach { w -> FolderRow(w.trimEnd('/'), bytesFmt(sizeOf(w))) { ctl.setFolderLists(ctl.white - w, ctl.black) } }
            }
            Spacer(Modifier.height(12.dp))
            PillBtn("Aggiungi una cartella alla whitelist", null, { addWhite.launch(null) }, Modifier.fillMaxWidth())
            Txt("Se la whitelist \u00E8 vuota, l'app usa tutti i brani del telefono. Aggiungendo cartelle, restano solo quelle.", Modifier.padding(top = 8.dp), TS.small, p.sub)
            SetLabel(Icons.Rounded.Block, "Blacklist")
            if (ctl.black.isNotEmpty()) {
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(p.card).padding(horizontal = 16.dp, vertical = 6.dp)) {
                    ctl.black.sorted().forEach { b -> FolderRow(b.trimEnd('/'), bytesFmt(sizeOf(b))) { ctl.setFolderLists(ctl.white, ctl.black - b) } }
                }
                Spacer(Modifier.height(12.dp))
            }
            Box(
                Modifier.fillMaxWidth().pressable(0.97f) { addBlack.launch(null) }.clip(CircleShape).background(Color(0xFFB80000)).padding(vertical = 13.dp),
                contentAlignment = Alignment.Center
            ) { Txt("Aggiungi una cartella alla blacklist", style = TS.title, color = Color.White) }
            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
fun AppVersionPage(back: () -> Unit) {
    val p = pal
    val ctx = LocalContext.current
    val ver = remember { try { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: "?" } catch (e: Exception) { "?" } }
    val notes = listOf(
        "Nuova schermata delle impostazioni: stile dell'app, testi, navigazione, dati e cartelle.",
        "La Libreria raccoglie brani, album e artisti, con le cartelle unite trascinando un album su un altro.",
        "La pagina Playlist mostra i preferiti e le tue playlist, con la creazione dal basso.",
        "Menu rapidi per brani, album, playlist e artisti.",
        "Testi sincronizzati a sinistra o al centro, con download automatico o manuale.",
        "Velocit\u00E0 e pitch del brano regolabili insieme o separati.",
        "Coda di riproduzione e sfondo del player pi\u00F9 colorato.",
        "Dashboard con giorni di ascolto, ore, brani e album di sempre."
    )
    Box(Modifier.fillMaxSize().background(p.bg).blockTouches()) {
        Box(Modifier.fillMaxWidth().height(560.dp).background(Brush.verticalGradient(0f to Color(0xFF0026FF), 0.5f to Color(0xFF0A6BFF), 1f to p.bg)))
        Column(Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState())) {
            if (!LocalEmbedded.current) Row(Modifier.padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconBtn(Icons.Rounded.ArrowBack, back, tint = Color.White)
                Txt("Versione dell'app", Modifier.padding(start = 4.dp), TS.title.copy(fontSize = 20.sp), Color.White)
            }
            Image(painterResource(R.mipmap.ic_launcher_foreground), null, Modifier.fillMaxWidth().height(270.dp), contentScale = ContentScale.Crop)
            Txt("Monos $ver", Modifier.padding(horizontal = 20.dp, vertical = 10.dp), TS.big.copy(fontSize = 44.sp, lineHeight = 48.sp, fontWeight = FontWeight.Black), Color.White)
            Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(p.surface).padding(16.dp)) {
                Txt("Profilo", style = TS.title)
                Txt("La tua app \u00E8 una versione stabile.", style = TS.small, color = p.sub)
            }
            Column(Modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(p.surface).padding(16.dp)) {
                Txt("Monos v-$ver", style = TS.h3)
                Spacer(Modifier.height(10.dp))
                Txt("Quali sono le novit\u00E0?", style = TS.title)
                Spacer(Modifier.height(8.dp))
                notes.forEach { n -> Txt("\u2022  $n", Modifier.padding(vertical = 3.dp), TS.small) }
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
fun EditArtistSheet(ctl: Ctl, name: String?, onClose: () -> Unit) {
    val p = pal
    val holder = remember { arrayOfNulls<String>(1) }
    SideEffect { if (name != null) holder[0] = name }
    var display by remember { mutableStateOf("") }
    var img by remember { mutableStateOf<Uri?>(null) }
    var changed by remember { mutableStateOf(false) }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri -> if (uri != null) { img = uri; changed = true } }
    LaunchedEffect(name) {
        if (name != null) { display = ctl.artistDisplay(name); img = ctl.artistImage(name); changed = false }
    }
    Sheet(name != null, onClose) {
        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 14.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Txt("Modifica l'artista", Modifier.weight(1f), TS.h2, maxLines = 1)
            IconBtn(Icons.Rounded.Close, onClose)
        }
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.height(8.dp))
            Box(Modifier.size(140.dp).pressable(0.97f) { gallery.launch("image/*") }, contentAlignment = Alignment.BottomEnd) {
                Cover(img, Modifier.fillMaxSize(), CircleShape)
                Box(Modifier.size(38.dp).clip(CircleShape).background(p.accent), contentAlignment = Alignment.Center) { Ico(Icons.Rounded.Edit, tint = p.onAccent, size = 20.dp) }
            }
            Txt("Tocca per cambiare l'immagine", Modifier.padding(top = 8.dp), TS.small, p.sub)
            Spacer(Modifier.height(16.dp))
            Txt("Nome visualizzato", Modifier.fillMaxWidth().padding(bottom = 6.dp), TS.title)
            Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(p.card).padding(14.dp)) {
                BasicTextField(
                    display, { display = it.take(60) }, singleLine = true, textStyle = TS.body.copy(color = p.fg),
                    cursorBrush = SolidColor(p.fg), modifier = Modifier.fillMaxWidth()
                )
            }
            Spacer(Modifier.height(18.dp))
            PillBtn("Salva", null, {
                val n = name ?: holder[0]
                if (n != null) ctl.updateArtist(n, display, if (changed) img else null)
                onClose()
            }, Modifier.fillMaxWidth())
        }
    }
}
