package com.xtream.player.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xtream.player.data.ContentState
import com.xtream.player.data.Programme
import com.xtream.player.data.Session
import com.xtream.player.ui.components.GlassPanel
import com.xtream.player.ui.components.GlowButton
import com.xtream.player.ui.components.PillField
import com.xtream.player.ui.theme.XColors
import com.xtream.player.util.fmtDateTime

@Composable
fun SettingsScreen(session: Session?, content: ContentState, epg: Map<String, List<Programme>>,
                   epgUpdatedAt: Long?, epgLoading: Boolean, epgMessage: String?, loading: Boolean,
                   error: String?, onSave: (String, String, String) -> Unit, onRefreshEpg: () -> Unit,
                   onRefreshContent: () -> Unit, onLogout: () -> Unit) {
    var server by remember(session?.server) { mutableStateOf(session?.server.orEmpty()) }
    var user by remember(session?.username) { mutableStateOf(session?.username.orEmpty()) }
    // Never save the password in saved instance state or a log.
    var password by remember(session?.password) { mutableStateOf(session?.password.orEmpty()) }
    val save = { if (!loading) onSave(server, user, password) }
    var showLicenses by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val licenses = remember {
        listOf("IPTVnator-MIT.txt", "DM-Sans-OFL.txt").joinToString("\n\n") { name ->
            runCatching { context.assets.open("licenses/$name").bufferedReader().use { it.readText() } }.getOrDefault(name)
        }
    }
    if (showLicenses) Dialog(onDismissRequest = { showLicenses = false }) {
        GlassPanel(Modifier.fillMaxWidth().widthIn(max = 620.dp).heightIn(max = 600.dp), RoundedCornerShape(16.dp)) {
            Column(Modifier.padding(20.dp)) {
                Text("Licenças de terceiros", color = XColors.Text, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                Text(licenses, color = XColors.TextDim, fontSize = 12.sp, lineHeight = 18.sp,
                    modifier = Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(vertical = 14.dp))
                GlowButton("Fechar", ringAlways = false) { showLicenses = false }
            }
        }
    }
    BoxWithConstraints(Modifier.fillMaxSize().background(XColors.Bg)) {
        val gutter = if (maxWidth < 600.dp) 16.dp else 32.dp
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = gutter, vertical = 26.dp)) {
            Text("Configurações", color = XColors.Text, fontSize = 28.sp, fontWeight = FontWeight.SemiBold)
            Text("Gerencie sua conexão e o guia de programação", color = XColors.TextDim, fontSize = 14.sp, modifier = Modifier.padding(top = 4.dp, bottom = 24.dp))
            GlassPanel(Modifier.fillMaxWidth().widthIn(max = 1000.dp), RoundedCornerShape(16.dp)) {
                Column(Modifier.padding(22.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.AccountCircle, null, tint = XColors.Accent, modifier = Modifier.size(24.dp))
                        Column(Modifier.padding(start = 12.dp)) {
                            Text("Conta Xtream Codes", color = XColors.BlueSoft, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                            Text("URL, usuário e senha do seu provedor", color = XColors.TextDim, fontSize = 13.sp)
                        }
                    }
                    Spacer(Modifier.height(20.dp))
                    FieldLabel("URL do servidor")
                    PillField(server, { server = it }, "Servidor Xtream", Modifier.fillMaxWidth().testTag("credential-server"), keyboardType = KeyboardType.Uri)
                    Spacer(Modifier.height(14.dp))
                    FieldLabel("Usuário")
                    PillField(user, { user = it }, "Usuário", Modifier.fillMaxWidth().testTag("credential-user"))
                    Spacer(Modifier.height(14.dp))
                    FieldLabel("Senha")
                    PillField(password, { password = it }, "Senha", Modifier.fillMaxWidth().testTag("credential-password"), password = true,
                        imeAction = ImeAction.Done, onDone = save)
                    if (error != null) Text(error, color = XColors.Live, fontSize = 13.sp, modifier = Modifier.padding(top = 12.dp))
                    Spacer(Modifier.height(18.dp))
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        GlowButton("Salvar e conectar", Icons.Filled.Save, onClick = save)
                        if (loading) CircularProgressIndicator(Modifier.size(22.dp), color = XColors.Accent, strokeWidth = 2.dp)
                    }
                    Text("Status: ${session?.status ?: "—"}  ·  ${content.channels.size} canais  ·  ${content.movies.size} filmes  ·  ${content.series.size} séries",
                        color = XColors.Body, fontSize = 12.sp, modifier = Modifier.padding(top = 15.dp))
                }
            }
            Spacer(Modifier.height(18.dp))
            GlassPanel(Modifier.fillMaxWidth().widthIn(max = 1000.dp), RoundedCornerShape(16.dp)) {
                Column(Modifier.padding(22.dp)) {
                    Text("Guia de programação (EPG)", color = XColors.Text, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                    Text("Última atualização: ${epgUpdatedAt?.let(::fmtDateTime) ?: "Nunca"}  ·  ${epg.size} canais",
                        color = XColors.TextDim, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp, bottom = 16.dp))
                    GlowButton(if (epgLoading) "Atualizando…" else "Atualizar EPG", Icons.Filled.Refresh, ringAlways = false, onClick = onRefreshEpg)
                    if (epgMessage != null) Text(epgMessage, color = XColors.TextDim, fontSize = 13.sp, modifier = Modifier.padding(top = 10.dp))
                }
            }
            Spacer(Modifier.height(18.dp))
            GlowButton("Atualizar catálogo", Icons.Filled.Refresh, ringAlways = false, onClick = onRefreshContent)
            Spacer(Modifier.height(10.dp))
            GlowButton("Sair da conta", Icons.Filled.Logout, ringAlways = false, onClick = onLogout)
            Spacer(Modifier.height(10.dp))
            GlowButton("Licenças de terceiros", Icons.Filled.Info, ringAlways = false) { showLicenses = true }
            Text("IPTV Native ${com.example.iptv.BuildConfig.VERSION_NAME}", color = XColors.Body, fontSize = 12.sp, modifier = Modifier.padding(top = 20.dp, bottom = 20.dp))
        }
    }
}

@Composable
private fun FieldLabel(label: String) {
    Text(label, color = XColors.Text, fontSize = 13.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(bottom = 6.dp))
}
