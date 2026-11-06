package com.example.iptv.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.iptv.BuildConfig
import com.example.iptv.data.epg.EpgGuide
import com.example.iptv.data.epg.EpgUpdateState
import com.example.iptv.data.model.AccountInfo
import com.example.iptv.data.model.StreamFormat
import com.example.iptv.data.model.UserSettings
import com.example.iptv.data.model.VideoDecoder
import com.example.iptv.ui.components.GlassPanel
import com.example.iptv.ui.components.ScreenHeader
import com.example.iptv.ui.components.SunsetButton
import com.example.iptv.ui.components.SunsetFocusable
import com.example.iptv.ui.components.SunsetProgressBar
import com.example.iptv.ui.components.TopographicBackground
import com.example.iptv.ui.components.formatDate
import com.example.iptv.ui.components.formatDateTime
import com.example.iptv.ui.components.rememberNowMs
import com.example.iptv.ui.theme.SunsetColors
import com.example.iptv.util.CacheUtils
import com.example.iptv.viewmodel.MainTvViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/** The new account/plan/EPG layout, retaining the existing playback and storage controls. */
@Composable
fun SettingsScreen(viewModel: MainTvViewModel, onLogout: () -> Unit) {
    val account by viewModel.account.collectAsState()
    val settings by viewModel.settings.collectAsState()
    val guide by viewModel.epgGuide.collectAsState()
    val epgState by viewModel.epgUpdateState.collectAsState()
    val accountRefreshing by viewModel.accountRefreshing.collectAsState()
    val latency by viewModel.pingLatencyMillis.collectAsState()
    val pingLoading by viewModel.pingLoading.collectAsState()
    val now = rememberNowMs(60_000L)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var confirmLogout by remember { mutableStateOf(false) }
    var clearing by remember { mutableStateOf(false) }
    var cacheMessage by remember { mutableStateOf<String?>(null) }

    TopographicBackground {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val compact = maxWidth < 900.dp || maxHeight < 500.dp
            val padding = if (compact) 14.dp else 28.dp
            val gap = if (compact) 8.dp else 16.dp
            Column(Modifier.fillMaxSize().padding(horizontal = padding, vertical = if (compact) 12.dp else 24.dp)) {
                ScreenHeader("CONFIGURAÇÕES", "Gerencie sua conta e a guia de programação", compact)
                Spacer(Modifier.height(if (compact) 8.dp else 16.dp))
                Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(gap)) {
                    Column(Modifier.weight(1f).fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(gap)) {
                        AccountCard(account, now)
                        PlanCard(account, now)
                        PlaybackCard(settings, viewModel)
                    }
                    Column(Modifier.weight(1f).fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(gap)) {
                        EpgCard(settings, guide, epgState, viewModel)
                        SessionCard(
                            refreshing = accountRefreshing,
                            confirmLogout = confirmLogout,
                            onRefresh = viewModel::refreshAccount,
                            onLogout = { if (confirmLogout) onLogout() else confirmLogout = true },
                            onCancel = { confirmLogout = false }
                        )
                        NetworkCard(latency, pingLoading, viewModel::testLatency)
                        StorageCard(cacheMessage, clearing, onClear = {
                            if (!clearing) {
                                clearing = true
                                viewModel.clearEpgCache()
                                scope.launch {
                                    val success = withContext(Dispatchers.IO) { CacheUtils.clear(context) }
                                    cacheMessage = if (success) "Cache limpo com sucesso." else "Não foi possível limpar o cache."
                                    clearing = false
                                }
                            }
                        })
                    }
                }
            }
        }
    }
}

@Composable
private fun CardColumn(title: String, content: @Composable () -> Unit) {
    GlassPanel(Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, color = SunsetColors.TextPrimary, fontSize = 19.sp, fontWeight = FontWeight.Bold)
            content()
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String, valueColor: Color = SunsetColors.TextPrimary) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = SunsetColors.TextSecondary, fontSize = 13.sp, modifier = Modifier.weight(0.43f))
        Text(value, color = valueColor, fontSize = 13.sp, fontWeight = FontWeight.Medium,
            maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(0.57f))
    }
}

@Composable
private fun AccountCard(info: AccountInfo?, now: Long) {
    CardColumn("Conta") {
        if (info == null) {
            Text("Dados da conta indisponíveis.", color = SunsetColors.TextSecondary, fontSize = 13.sp)
        } else {
            val expired = info.expiresAtMillis?.let { it <= now } == true
            val active = info.isActive && !expired
            InfoRow("Usuário", info.username.ifBlank { "—" })
            InfoRow("Servidor", info.panelUrl.ifBlank { "—" })
            InfoRow("Status", if (expired) "EXPIRADA" else info.statusLabel.uppercase(), if (active) SunsetColors.Success else SunsetColors.Danger)
            InfoRow("Conexões", "${info.activeConnections} / ${info.maxConnections}")
            info.createdAtMillis?.let { InfoRow("Criada em", formatDate(it)) }
            InfoRow("Período de teste", if (info.isTrial) "Sim" else "Não")
            InfoRow("Fuso do servidor", info.timezone.ifBlank { "—" })
            if (info.allowedFormats.isNotEmpty()) InfoRow("Formatos", info.allowedFormats.joinToString(", "))
        }
    }
}

@Composable
private fun PlanCard(info: AccountInfo?, now: Long) {
    CardColumn("Plano") {
        if (info == null) {
            Text("Dados do plano indisponíveis.", color = SunsetColors.TextSecondary, fontSize = 13.sp)
        } else {
            val expiry = info.expiresAtMillis
            if (expiry == null) {
                Text("Plano ilimitado", color = SunsetColors.Success, fontSize = 23.sp, fontWeight = FontWeight.Bold)
                Text("Sem data de expiração", color = SunsetColors.TextSecondary, fontSize = 13.sp)
            } else {
                val days = TimeUnit.MILLISECONDS.toDays((expiry - now).coerceAtLeast(0))
                val expired = expiry <= now
                val headline = when {
                    expired -> "Plano expirado"
                    days == 0L -> "Expira hoje"
                    days == 1L -> "Expira em 1 dia"
                    else -> "Expira em $days dias"
                }
                Text(headline, color = if (expired || days <= 7) SunsetColors.Danger else SunsetColors.TextPrimary,
                    fontSize = 23.sp, fontWeight = FontWeight.Bold)
                Text("Vencimento: ${formatDateTime(expiry)}", color = SunsetColors.TextSecondary, fontSize = 13.sp)
                info.createdAtMillis?.takeIf { expiry > it }?.let { created ->
                    val remaining = ((expiry - now).toFloat() / (expiry - created)).coerceIn(0f, 1f)
                    SunsetProgressBar(remaining, Modifier.fillMaxWidth())
                    Text("${(remaining * 100).toInt()}% do período restante", color = SunsetColors.TextSecondary, fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun EpgCard(settings: UserSettings, guide: EpgGuide, state: EpgUpdateState, viewModel: MainTvViewModel) {
    val busy = state is EpgUpdateState.Downloading || state is EpgUpdateState.Parsing
    CardColumn("Guia de programação (EPG)") {
        when (state) {
            is EpgUpdateState.Downloading -> {
                Text(state.progress?.let { "Baixando guia… ${(it * 100).toInt()}%" } ?: "Baixando guia…", color = SunsetColors.TextPrimary, fontSize = 13.sp)
                if (state.progress == null) LinearProgressIndicator(Modifier.fillMaxWidth(), color = SunsetColors.Coral, trackColor = Color(0x33FFFFFF))
                else SunsetProgressBar(state.progress, Modifier.fillMaxWidth())
            }
            EpgUpdateState.Parsing -> {
                Text("Processando a programação…", color = SunsetColors.TextPrimary, fontSize = 13.sp)
                LinearProgressIndicator(Modifier.fillMaxWidth(), color = SunsetColors.Coral, trackColor = Color(0x33FFFFFF))
            }
            is EpgUpdateState.Error -> Text(state.message, color = SunsetColors.Danger, fontSize = 13.sp)
            EpgUpdateState.Idle -> Unit
        }
        InfoRow("Última atualização", settings.lastEpgSyncMillis.takeIf { it > 0 }?.let(::formatDateTime) ?: "Nunca")
        InfoRow("Canais com EPG", guide.channelCount.toString())
        InfoRow("Programas carregados", guide.programCount.toString())
        SunsetButton(if (busy) "Atualizando…" else "Atualizar EPG agora", onClick = { viewModel.refreshEpg() },
            icon = Icons.Filled.Refresh, enabled = !busy, modifier = Modifier.fillMaxWidth())
        SunsetFocusable(onClick = { viewModel.updateAutoEpg(!settings.autoUpdateEpg) }, modifier = Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Atualizar automaticamente ao abrir (a cada 12 h)", color = SunsetColors.TextPrimary, fontSize = 13.sp, modifier = Modifier.weight(1f))
                Switch(checked = settings.autoUpdateEpg, onCheckedChange = null,
                    colors = SwitchDefaults.colors(checkedTrackColor = SunsetColors.Coral, checkedThumbColor = Color.White))
            }
        }
        Text("Fonte XMLTV personalizada (opcional)", color = SunsetColors.TextSecondary, fontSize = 12.sp)
        SunsetUrlField(settings.customXmltvUrl, viewModel::updateCustomXmltvUrl)
        Text("Deixe vazio para usar o guia do servidor. Depois de alterar a fonte, atualize o EPG.", color = SunsetColors.TextSecondary, fontSize = 11.sp)
    }
}

@Composable
private fun SunsetUrlField(value: String, onValueChange: (String) -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(16.dp)
    BasicTextField(value = value, onValueChange = onValueChange, singleLine = true,
        textStyle = TextStyle(color = SunsetColors.TextPrimary, fontSize = 13.sp), cursorBrush = SolidColor(SunsetColors.Coral),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
        modifier = Modifier.fillMaxWidth().onFocusChanged { focused = it.isFocused },
        decorationBox = { inner ->
            Box(Modifier.fillMaxWidth().clip(shape).background(if (focused) Color(0x33FFFFFF) else Color(0x14FFFFFF))
                .border(if (focused) 2.dp else 1.dp, if (focused) SunsetColors.Orange else Color(0x1AFFFFFF), shape)
                .padding(horizontal = 14.dp, vertical = 12.dp)) {
                if (value.isEmpty()) Text("Usar xmltv.php do servidor", color = SunsetColors.TextSecondary, fontSize = 13.sp, maxLines = 1)
                inner()
            }
        })
}

@Composable
private fun SessionCard(refreshing: Boolean, confirmLogout: Boolean, onRefresh: () -> Unit, onLogout: () -> Unit, onCancel: () -> Unit) {
    CardColumn("Sessão") {
        Text(if (confirmLogout) "Ao sair, as credenciais, o catálogo e o guia serão removidos deste dispositivo." else "Gerencie os dados e a sessão desta conta.",
            color = SunsetColors.TextSecondary, fontSize = 12.sp)
        SunsetButton(if (refreshing) "Atualizando conta…" else "Atualizar dados da conta", onRefresh,
            icon = Icons.Filled.Refresh, enabled = !refreshing, modifier = Modifier.fillMaxWidth())
        SunsetButton(if (confirmLogout) "Confirmar saída" else "Sair da conta", onLogout,
            icon = Icons.AutoMirrored.Filled.ExitToApp, danger = true, modifier = Modifier.fillMaxWidth())
        if (confirmLogout) SunsetButton("Cancelar", onCancel, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun PlaybackCard(settings: UserSettings, viewModel: MainTvViewModel) {
    CardColumn("Reprodução") {
        Text("Formato do stream", color = SunsetColors.TextSecondary, fontSize = 13.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StreamFormat.entries.forEach { format ->
                SunsetFocusable(onClick = { viewModel.updateStreamFormat(format) }, selected = settings.streamFormat == format,
                    shape = RoundedCornerShape(50), modifier = Modifier.weight(1f)) {
                    Text(format.label, color = SunsetColors.TextPrimary, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp))
                }
            }
        }
        Text("Use HLS se um canal não reproduzir em MPEG-TS.", color = SunsetColors.TextSecondary, fontSize = 11.sp)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Buffer de reprodução", color = SunsetColors.TextSecondary, fontSize = 13.sp)
            Text("${settings.bufferSeconds}s", color = SunsetColors.Coral, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
        Slider(value = settings.bufferSeconds.toFloat(), onValueChange = { viewModel.updateBufferSeconds(it.toInt()) }, valueRange = 5f..60f, steps = 10,
            colors = SliderDefaults.colors(thumbColor = SunsetColors.Coral, activeTrackColor = SunsetColors.Coral, inactiveTrackColor = Color.White.copy(alpha = 0.18f)))
        Text("Decodificador de vídeo", color = SunsetColors.TextSecondary, fontSize = 13.sp)
        VideoDecoder.entries.forEach { decoder ->
            SunsetFocusable(onClick = { viewModel.updateDecoder(decoder) }, selected = settings.decoder == decoder,
                modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                Text(decoder.label, color = SunsetColors.TextPrimary, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp))
            }
        }
    }
}

@Composable
private fun NetworkCard(latency: Long?, loading: Boolean, onTest: () -> Unit) {
    CardColumn("Rede") {
        InfoRow("Latência do painel", latency?.let { "$it ms" } ?: "Não testada")
        SunsetButton(if (loading) "Testando…" else "Testar conexão", onTest, enabled = !loading, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun StorageCard(message: String?, clearing: Boolean, onClear: () -> Unit) {
    CardColumn("Armazenamento") {
        Text("Limpa imagens, streams temporários e o guia XMLTV salvo.", color = SunsetColors.TextSecondary, fontSize = 12.sp)
        message?.let { Text(it, color = SunsetColors.TextPrimary, fontSize = 12.sp) }
        SunsetButton(if (clearing) "Limpando…" else "Limpar cache", onClear, icon = Icons.Filled.Delete,
            enabled = !clearing, modifier = Modifier.fillMaxWidth())
        Text("IPTV Native ${BuildConfig.VERSION_NAME} · Media3 ExoPlayer", color = SunsetColors.TextSecondary, fontSize = 11.sp)
    }
}
