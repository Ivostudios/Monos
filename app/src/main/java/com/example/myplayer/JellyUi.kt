package com.example.myplayer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
private fun JField(label: String, value: String, hint: String, password: Boolean, keyboard: KeyboardType, onChange: (String) -> Unit) {
    val p = pal
    Txt(label, Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 6.dp), TS.title)
    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(p.card).padding(14.dp)) {
        BasicTextField(
            value, onChange, singleLine = true, textStyle = TS.body.copy(color = p.fg), cursorBrush = SolidColor(p.fg),
            visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
            keyboardOptions = KeyboardOptions(keyboardType = keyboard),
            modifier = Modifier.fillMaxWidth(),
            decorationBox = { inner -> Box { if (value.isEmpty()) Txt(hint, color = p.sub, style = TS.small); inner() } }
        )
    }
}

@Composable
fun ProfileSheet(ctl: Ctl, visible: Boolean, onClose: () -> Unit) {
    val p = pal
    val scope = rememberCoroutineScope()
    var url by remember { mutableStateOf("") }
    var user by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    var err by remember { mutableStateOf<String?>(null) }
    var working by remember { mutableStateOf(false) }
    val sess = ctl.jelly
    LaunchedEffect(visible) { if (!visible) { delay(450); pass = ""; err = null; working = false } }
    Sheet(visible, onClose) {
        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 14.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Txt(if (sess != null) "Profilo Jellyfin" else "Accedi a Jellyfin", Modifier.weight(1f), TS.h2, maxLines = 1)
            IconBtn(Icons.Rounded.Close, onClose)
        }
        Column(Modifier.heightIn(max = 560.dp).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 20.dp)) {
            if (sess != null) {
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(p.card).padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(40.dp).clip(CircleShape).background(Color(0xFF0026FF)), contentAlignment = Alignment.Center) { Ico(Icons.Rounded.Person, tint = Color.White) }
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Txt(sess.userName.ifEmpty { "Utente" }, style = TS.title, maxLines = 1)
                            Txt(sess.url, style = TS.small, color = p.sub, maxLines = 1)
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Txt(
                        when {
                            Jelly.busy -> "Sincronizzazione in corso..."
                            Jelly.error != null -> Jelly.error!!
                            else -> "${ctl.songs.size} brani  \u2022  ${ctl.playlists.size} playlist"
                        },
                        style = TS.small, color = if (Jelly.error != null && !Jelly.busy) Color(0xFFC00000) else p.sub
                    )
                }
                Spacer(Modifier.height(14.dp))
                PillBtn("Sincronizza ora", Icons.Rounded.Sync, { if (!Jelly.busy) scope.launch { ctl.syncJelly() } }, Modifier.fillMaxWidth())
                Spacer(Modifier.height(10.dp))
                Txt("Preferiti e playlist sono salvati sul server e condivisi con gli altri dispositivi.", style = TS.small, color = p.sub)
                Spacer(Modifier.height(14.dp))
                Box(
                    Modifier.fillMaxWidth().height(50.dp).pressable(0.97f) { ctl.jellyLogout(); onClose() }.clip(RoundedCornerShape(25.dp)).background(Color(0xFFC00000)),
                    contentAlignment = Alignment.Center
                ) { Txt("Esci dal profilo", style = TS.title, color = Color.White) }
            } else {
                Txt("Usa la musica del tuo server Jellyfin. L'app deve essere sulla stessa rete del server.", Modifier.padding(top = 4.dp), TS.small, p.sub)
                JField("Indirizzo del server", url, "192.168.1.20:8096", false, KeyboardType.Uri) { url = it }
                JField("Nome utente", user, "Il tuo utente Jellyfin", false, KeyboardType.Text) { user = it }
                JField("Password", pass, "La tua password", true, KeyboardType.Password) { pass = it }
                if (err != null) Txt(err!!, Modifier.padding(top = 12.dp), TS.small, Color(0xFFC00000))
                Spacer(Modifier.height(18.dp))
                PillBtn(if (working) "Accesso in corso..." else "Accedi", null, {
                    if (!working) {
                        working = true; err = null
                        scope.launch {
                            val r = ctl.jellyLogin(url, user.trim(), pass)
                            working = false
                            if (r == null) onClose() else err = r
                        }
                    }
                }, Modifier.fillMaxWidth())
                Txt("La password non viene salvata: l'app conserva solo un codice di accesso.", Modifier.padding(top = 10.dp), TS.small, p.sub)
            }
        }
    }
}
