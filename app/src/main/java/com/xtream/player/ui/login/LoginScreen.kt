package com.xtream.player.ui.login

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xtream.player.ui.components.GlassPanel
import com.xtream.player.ui.components.PillField
import com.xtream.player.ui.components.TopoBackground
import com.xtream.player.ui.components.focusGlow
import com.xtream.player.ui.theme.XColors

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LoginScreen(loading: Boolean, error: String?, onLogin: (server: String, user: String, pass: String) -> Unit) {
    var server by rememberSaveable { mutableStateOf("") }
    var user by rememberSaveable { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    val submit = { if (!loading) onLogin(server, user, pass) }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        TopoBackground()
        GlassPanel(Modifier.padding(12.dp).width(440.dp).heightIn(max = 560.dp)) {
            Column(
                Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 40.dp, vertical = 36.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("Bem-vindo de volta", color = XColors.Text, fontSize = 34.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center)
                Spacer(Modifier.height(6.dp))
                Text("Conecte-se à sua conta Xtream", color = XColors.TextDim, fontSize = 20.sp, textAlign = TextAlign.Center)
                Spacer(Modifier.height(30.dp))
                PillField(server, { server = it }, "Servidor Xtream", Modifier.fillMaxWidth(), keyboardType = KeyboardType.Uri)
                Spacer(Modifier.height(18.dp))
                PillField(user, { user = it }, "Usuário", Modifier.fillMaxWidth())
                Spacer(Modifier.height(18.dp))
                PillField(pass, { pass = it }, "Senha", Modifier.fillMaxWidth(), password = true, imeAction = ImeAction.Done, onDone = { submit() })
                Spacer(Modifier.height(30.dp))
                val pill = RoundedCornerShape(50)
                // Botão com a MESMA largura e altura dos campos de texto
                Box(
                    Modifier.fillMaxWidth().height(58.dp)
                        .focusGlow(pill, zoom = 1.02f)
                        .shadow(18.dp, pill, ambientColor = XColors.Accent, spotColor = XColors.Accent)
                        .clip(pill).background(XColors.Accent)
                        .combinedClickable(enabled = !loading, onClick = { submit() }),
                    contentAlignment = Alignment.Center,
                ) {
                    if (loading) CircularProgressIndicator(color = Color.White, strokeWidth = 2.5.dp, modifier = Modifier.size(26.dp))
                    else Icon(Icons.Filled.ArrowForward, "Entrar", tint = Color.White, modifier = Modifier.size(30.dp))
                }
                if (error != null) {
                    Spacer(Modifier.height(14.dp))
                    Text(error, color = XColors.Accent, fontSize = 15.sp, textAlign = TextAlign.Center)
                }
            }
        }
    }
}
