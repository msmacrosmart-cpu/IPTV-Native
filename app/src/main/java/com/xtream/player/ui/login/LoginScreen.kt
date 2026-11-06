package com.xtream.player.ui.login

import androidx.compose.foundation.background
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xtream.player.ui.components.GlassPanel
import com.xtream.player.ui.components.GlowButton
import com.xtream.player.ui.components.PillField
import com.xtream.player.ui.components.rememberHideKeyboard
import com.xtream.player.ui.theme.XColors
import kotlinx.coroutines.delay

@Composable
fun LoginScreen(loading: Boolean, error: String?, onLogin: (server: String, user: String, pass: String) -> Unit) {
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
    val firstField = remember { FocusRequester() }
    val inputMode = LocalInputModeManager.current
    LaunchedEffect(Unit) {
        if (inputMode.inputMode != InputMode.Touch) { delay(100); runCatching { firstField.requestFocus() } }
    }
    Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp),
        contentAlignment = Alignment.Center) {
        GlassPanel(Modifier.widthIn(max = 500.dp).fillMaxWidth(), RoundedCornerShape(18.dp)) {
            Column(Modifier.fillMaxWidth().padding(26.dp)) {
                Text("Adicionar conexão", color = XColors.Text, fontSize = 27.sp, fontWeight = FontWeight.SemiBold)
                Text("Entre com sua conta Xtream Codes", color = XColors.TextDim, fontSize = 14.sp, modifier = Modifier.padding(top = 6.dp))
                Spacer(Modifier.height(22.dp))
                PillField(server, { server = it }, "Servidor Xtream", Modifier.fillMaxWidth().focusRequester(firstField), keyboardType = KeyboardType.Uri)
                Spacer(Modifier.height(12.dp))
                PillField(user, { user = it }, "Usuário", Modifier.fillMaxWidth())
                Spacer(Modifier.height(12.dp))
                PillField(pass, { pass = it }, "Senha", Modifier.fillMaxWidth(), password = true, imeAction = ImeAction.Done, onDone = submit)
                if (error != null) Text(error, color = XColors.Live, fontSize = 13.sp, modifier = Modifier.padding(top = 12.dp))
                Spacer(Modifier.height(22.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                    if (loading) CircularProgressIndicator(Modifier.padding(end = 12.dp).size(22.dp), color = XColors.Accent, strokeWidth = 2.dp)
                    GlowButton("Entrar", Icons.Filled.Link, Modifier.semantics { contentDescription = "Entrar" }, onClick = submit)
                }
                Text("Use demo no servidor para explorar a interface.", color = XColors.Body, fontSize = 12.sp, modifier = Modifier.padding(top = 18.dp))
            }
        }
    }
}
