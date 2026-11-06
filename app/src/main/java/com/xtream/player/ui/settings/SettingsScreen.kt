package com.xtream.player.ui.settings

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.iptv.R
import com.example.iptv.data.model.UserSettings
import com.example.iptv.data.preferences.SavedProfile
import com.xtream.player.data.ContentState
import com.xtream.player.data.Programme
import com.xtream.player.data.Session
import com.xtream.player.ui.components.GlassPanel
import com.xtream.player.ui.components.GlowButton
import com.xtream.player.ui.components.PillField
import com.xtream.player.ui.theme.XColors
import com.xtream.player.util.fmtDateTime

@Composable
fun SettingsScreen(
    session: Session?, content: ContentState, epg: Map<String, List<Programme>>,
    epgUpdatedAt: Long?, epgLoading: Boolean, epgMessage: String?, loading: Boolean, error: String?,
    profiles: List<SavedProfile>, settings: UserSettings, parentalUnlocked: Boolean, parentalBusy: Boolean,
    testingConnection: Boolean, connectionTest: String?, actions: SettingsActions,
) {
    var server by remember(session?.server) { mutableStateOf(session?.server.orEmpty()) }
    var user by remember(session?.username) { mutableStateOf(session?.username.orEmpty()) }
    // Passwords are never in saved-instance state or displayed in account details.
    var password by remember(session?.password) { mutableStateOf(session?.password.orEmpty()) }
    val save = { if (!loading) actions.saveCredentials(server, user, password) }
    var showLicenses by remember { mutableStateOf(false) }
    var removeProfile by remember { mutableStateOf<SavedProfile?>(null) }
    val context = LocalContext.current
    val licenses = remember {
        listOf("IPTVnator-MIT.txt", "DM-Sans-OFL.txt").joinToString("\n\n") { name ->
            runCatching { context.assets.open("licenses/$name").bufferedReader().use { it.readText() } }.getOrDefault(name)
        }
    }
    if (showLicenses) Dialog(onDismissRequest = { showLicenses = false }) {
        GlassPanel(Modifier.widthIn(max = 620.dp).fillMaxWidth().heightIn(max = 600.dp), RoundedCornerShape(16.dp)) {
            Column(Modifier.padding(20.dp)) {
                Text("Licenças de terceiros", color = XColors.Text, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                Image(painterResource(R.drawable.tmdb_logo), "The Movie Database", Modifier.padding(top = 10.dp).width(90.dp))
                Text("This product uses the TMDB API but is not endorsed or certified by TMDB.",
                    color = XColors.Body, fontSize = 10.sp, modifier = Modifier.padding(top = 5.dp))
                Text(licenses, color = XColors.TextDim, fontSize = 12.sp, lineHeight = 18.sp,
                    modifier = Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(vertical = 14.dp))
                GlowButton("Fechar", ringAlways = false) { showLicenses = false }
            }
        }
    }
    removeProfile?.let { profile ->
        ConfirmSettingsDialog("Remover perfil?", "As credenciais, favoritos e histórico locais de ${profile.username} serão removidos deste dispositivo.",
            onDismiss = { removeProfile = null }, onConfirm = { actions.removeProfile(profile.id); removeProfile = null })
    }
    val anchors = rememberSettingsAnchors()
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val gutter = if (maxWidth < 600.dp) 16.dp else 32.dp
        CompositionLocalProvider(LocalSettingsAnchors provides anchors) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).testTag("settings-list")
                .padding(horizontal = gutter, vertical = 26.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                Column {
                    Text("Configurações", color = XColors.Text, fontSize = 28.sp, fontWeight = FontWeight.SemiBold)
                    Text("Contas, reprodução e guia de programação", color = XColors.TextDim, fontSize = 14.sp, modifier = Modifier.padding(top = 4.dp))
                }
                SettingsShortcuts()
                SettingsPanel("Perfis", "Alterne entre contas Xtream sem misturar favoritos, histórico ou cache.") {
                    if (profiles.isEmpty()) Text("Nenhum perfil salvo neste dispositivo.", color = XColors.TextDim, fontSize = 13.sp)
                    profiles.forEach { profile ->
                        Column(Modifier.fillMaxWidth().padding(vertical = 6.dp).testTag("profile-${profile.id}")) {
                            Text(profile.username + if (profile.active && session?.isDemo != true) "  ·  Em uso" else "", color = XColors.Text,
                                fontWeight = FontWeight.SemiBold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(profile.server, color = XColors.Body, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (!profile.active || session?.isDemo == true) Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                GlowButton("Alternar", ringAlways = false, modifier = Modifier.testTag("switch-profile-${profile.id}")) { actions.switchProfile(profile.id) }
                                GlowButton("Remover", ringAlways = false, modifier = Modifier.testTag("remove-profile-${profile.id}")) { removeProfile = profile }
                            }
                        }
                    }
                    GlowButton("Adicionar perfil", ringAlways = false) { server = ""; user = ""; password = "" }
                    Text("Para adicionar, preencha a conexão abaixo e selecione Salvar e conectar.", color = XColors.Body,
                        fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
                }
                SettingsPanel("Dados da conta", "Informações fornecidas pelo painel Xtream.") {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.AccountCircle, null, tint = XColors.Accent, modifier = Modifier.size(24.dp))
                        Text(session?.username ?: "—", color = XColors.Text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(start = 10.dp))
                    }
                    Spacer(Modifier.height(8.dp))
                    AccountLine("Servidor", session?.server.orEmpty().ifBlank { "—" })
                    AccountLine("Status", session?.status ?: "—")
                    AccountLine("Criada em", session?.createdAt?.let { fmtDateTime(it, settings.time24Hour) } ?: "Não informado")
                    AccountLine("Expira em", session?.expDate?.let { fmtDateTime(it, settings.time24Hour) } ?: "Não informado")
                    AccountLine("Conta de teste", if (session?.isTrial == true) "Sim" else "Não")
                    AccountLine("Conexões ativas / limite", session?.let { "${it.activeConns} / ${it.maxConns}" } ?: "—")
                    AccountLine("Formatos permitidos", session?.allowedFormats?.joinToString(", ")?.ifBlank { "Não informado" } ?: "—")
                    AccountLine("Fuso horário do servidor", session?.timezone?.ifBlank { "Não informado" } ?: "—")
                    AccountLine("Última validação", session?.syncedAt?.let { fmtDateTime(it, settings.time24Hour) } ?: "Não informada")
                    Text("${content.channels.size} canais  ·  ${content.movies.size} filmes  ·  ${content.series.size} séries",
                        color = XColors.Body, fontSize = 12.sp, modifier = Modifier.padding(vertical = 8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        GlowButton("Atualizar conta", Icons.Filled.Refresh, ringAlways = false, onClick = actions.refreshAccount)
                        GlowButton(if (testingConnection) "Testando…" else "Testar conexão", ringAlways = false, onClick = actions.testConnection)
                    }
                    if (connectionTest != null) Text(connectionTest, color = XColors.TextDim, fontSize = 13.sp,
                        modifier = Modifier.padding(top = 10.dp).testTag("connection-test-result"))
                }
                SettingsPanel("Conta Xtream Codes", "URL, usuário e senha do seu provedor") {
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
                }
                AdvancedSettings(settings, epg.size, epgUpdatedAt, epgLoading, epgMessage, actions)
                ParentalSettings(content, settings, parentalUnlocked, parentalBusy, actions)
                DeviceTools()
                GlowButton("Atualizar catálogo", Icons.Filled.Refresh, ringAlways = false, onClick = actions.refreshContent)
                GlowButton("Sair da conta", Icons.Filled.Logout, ringAlways = false, onClick = actions.logout)
                GlowButton("Licenças de terceiros", Icons.Filled.Info, ringAlways = false) { showLicenses = true }
                Text("IPTV Native ${com.example.iptv.BuildConfig.VERSION_NAME}", color = XColors.Body, fontSize = 12.sp, modifier = Modifier.padding(bottom = 20.dp))
            }
        }
    }
}

@Composable
internal fun SettingsPanel(title: String, description: String = "", content: @Composable ColumnScope.() -> Unit) {
    GlassPanel(Modifier.widthIn(max = 1000.dp).fillMaxWidth(), RoundedCornerShape(16.dp)) {
        Column(Modifier.fillMaxWidth().padding(22.dp)) {
            Text(title, color = XColors.Text, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, modifier = settingsAnchor(title))
            if (description.isNotBlank()) Text(description, color = XColors.TextDim, fontSize = 13.sp, modifier = Modifier.padding(top = 5.dp))
            Spacer(Modifier.height(16.dp))
            content()
        }
    }
}

@Composable
private fun AccountLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(label, color = XColors.TextDim, fontSize = 13.sp, modifier = Modifier.weight(1f))
        Text(value, color = XColors.Text, fontSize = 13.sp, modifier = Modifier.weight(1f))
    }
}

@Composable
internal fun FieldLabel(label: String) {
    Text(label, color = XColors.Text, fontSize = 13.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(bottom = 6.dp))
}

@Composable
internal fun ConfirmSettingsDialog(title: String, message: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        GlassPanel(Modifier.widthIn(max = 540.dp).fillMaxWidth(), RoundedCornerShape(16.dp)) {
            Column(Modifier.padding(22.dp)) {
                Text(title, color = XColors.Text, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                Text(message, color = XColors.TextDim, fontSize = 14.sp, modifier = Modifier.padding(vertical = 16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    GlowButton("Cancelar", ringAlways = false, onClick = onDismiss)
                    GlowButton("Confirmar", onClick = onConfirm)
                }
            }
        }
    }
}
