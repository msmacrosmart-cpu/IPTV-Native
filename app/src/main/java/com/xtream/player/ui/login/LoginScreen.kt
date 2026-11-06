package com.xtream.player.ui.login

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Link
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.iptv.data.preferences.SavedProfile
import com.xtream.player.ui.components.GlassPanel
import com.xtream.player.ui.components.GlowButton
import com.xtream.player.ui.components.PillField
import com.xtream.player.ui.components.focusGlow
import com.xtream.player.ui.components.rememberHideKeyboard
import com.xtream.player.ui.theme.XColors
import kotlinx.coroutines.delay

@Composable
fun LoginScreen(loading: Boolean, error: String?, profiles: List<SavedProfile> = emptyList(),
                onSwitchProfile: (String) -> Unit = {}, onLogin: (server: String, user: String, pass: String) -> Unit) {
    var showProfiles by remember { mutableStateOf(false) }
    if (showProfiles) Dialog(onDismissRequest = { showProfiles = false }) {
        GlassPanel(Modifier.widthIn(max = 500.dp).fillMaxWidth().heightIn(max = 440.dp), RoundedCornerShape(16.dp)) {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(20.dp)) {
                Text("Perfis salvos", color = XColors.Text, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                profiles.forEach { profile ->
                    Column(Modifier.fillMaxWidth().padding(top = 12.dp)) {
                        Text(profile.username, color = XColors.Text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        Text(profile.server, color = XColors.TextDim, fontSize = 12.sp, maxLines = 1)
                        GlowButton("Abrir perfil", ringAlways = false, modifier = Modifier.testTag("login-profile-${profile.id}")) {
                            showProfiles = false; onSwitchProfile(profile.id)
                        }
                    }
                }
                GlowButton("Fechar", ringAlways = false, modifier = Modifier.padding(top = 12.dp)) { showProfiles = false }
            }
        }
    }
    var server by rememberSaveable { mutableStateOf("") }
    var user by rememberSaveable { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    val hideKeyboard = rememberHideKeyboard()
    val focus = LocalFocusManager.current
    val submit = {
        if (!loading) {
            hideKeyboard()
            focus.clearFocus(force = true)
            onLogin(server, user, pass)
        }
    }
    // Start remote navigation on the heading: focusing a text field opens the TV keyboard
    // immediately and hides most of the form. Down moves to the server field; the device's
    // native IME is shown when the user chooses a field, not when the screen first opens.
    val initialHeading = remember { FocusRequester() }
    val inputMode = LocalInputModeManager.current
    LaunchedEffect(Unit) {
        if (inputMode.inputMode != InputMode.Touch) {
            delay(100)
            runCatching { initialHeading.requestFocus() }
            hideKeyboard()
        }
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        // Landscape phones and small TV viewports have much less vertical room. Keep
        // the complete form centered there; scrolling remains a fallback for large
        // accessibility fonts, error messages and the native keyboard.
        val compact = maxHeight < 430.dp
        val fieldHeight = if (compact) 44.dp else 52.dp
        val gap = if (compact) 6.dp else 12.dp
        Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(if (compact) 8.dp else 18.dp),
            contentAlignment = Alignment.Center) {
            GlassPanel(Modifier.widthIn(max = if (compact) 460.dp else 500.dp).fillMaxWidth().testTag("login-panel"), RoundedCornerShape(18.dp)) {
                Column(Modifier.fillMaxWidth().padding(if (compact) 14.dp else 26.dp)) {
                    Text("Adicionar conexão", color = XColors.Text, fontSize = if (compact) 22.sp else 27.sp,
                        fontWeight = FontWeight.SemiBold, modifier = Modifier.focusRequester(initialHeading)
                            .testTag("login-heading").focusGlow(RoundedCornerShape(6.dp), zoom = 1f).focusable()
                            .padding(horizontal = 6.dp, vertical = 3.dp))
                    Text("Entre com sua conta Xtream Codes", color = XColors.TextDim, fontSize = if (compact) 12.sp else 14.sp,
                        modifier = Modifier.padding(top = if (compact) 2.dp else 6.dp))
                    Spacer(Modifier.height(if (compact) 8.dp else 22.dp))
                    PillField(server, { server = it }, "Servidor Xtream", Modifier.fillMaxWidth(),
                        keyboardType = KeyboardType.Uri, minHeight = fieldHeight)
                    Spacer(Modifier.height(gap))
                    PillField(user, { user = it }, "Usuário", Modifier.fillMaxWidth(), minHeight = fieldHeight)
                    Spacer(Modifier.height(gap))
                    PillField(pass, { pass = it }, "Senha", Modifier.fillMaxWidth(), password = true,
                        imeAction = ImeAction.Done, onDone = submit, minHeight = fieldHeight)
                    if (error != null) Text(error, color = XColors.Live, fontSize = 13.sp, modifier = Modifier.padding(top = gap))
                    Spacer(Modifier.height(if (compact) 8.dp else 22.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                        if (profiles.isNotEmpty()) {
                            GlowButton("Perfis salvos", ringAlways = false) { showProfiles = true }
                            Spacer(Modifier.weight(1f))
                        }
                        if (loading) CircularProgressIndicator(Modifier.padding(end = 12.dp).size(22.dp), color = XColors.Accent, strokeWidth = 2.dp)
                        GlowButton("Entrar", Icons.Filled.Link, Modifier.testTag("login-submit")
                            .semantics { contentDescription = "Entrar" }, onClick = submit)
                    }
                    Text("Use demo no servidor para explorar a interface.", color = XColors.Body, fontSize = 12.sp,
                        modifier = Modifier.padding(top = if (compact) 6.dp else 18.dp))
                }
            }
        }
    }
}
