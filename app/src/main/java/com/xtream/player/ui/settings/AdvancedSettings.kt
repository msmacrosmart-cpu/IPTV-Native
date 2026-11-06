package com.xtream.player.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.iptv.data.model.DEFAULT_USER_AGENT
import com.example.iptv.data.model.PlaybackApp
import com.example.iptv.data.model.StreamFormat
import com.example.iptv.data.model.UserSettings
import com.example.iptv.data.model.VideoDecoder
import com.xtream.player.ui.components.CategoryChip
import com.xtream.player.ui.components.GlowButton
import com.xtream.player.ui.components.IconAction
import com.xtream.player.ui.components.PillField
import com.xtream.player.ui.theme.XColors
import com.xtream.player.util.fmtDateTime

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AdvancedSettings(settings: UserSettings, epgChannels: Int, epgUpdatedAt: Long?, epgLoading: Boolean,
                     epgMessage: String?, actions: SettingsActions) {
    var xmltv by remember(settings.customXmltvUrl) { mutableStateOf(settings.customXmltvUrl) }
    var agent by remember(settings.userAgent) { mutableStateOf(settings.userAgent) }
    var confirmClear by remember { mutableStateOf(false) }
    if (confirmClear) ConfirmSettingsDialog("Limpar cache desta conta?",
        "O catálogo e o guia salvos serão removidos. Credenciais, perfis, favoritos e histórico serão mantidos.",
        onDismiss = { confirmClear = false }, onConfirm = { confirmClear = false; actions.clearCache() })

    SettingsPanel("Reprodução", "As alterações são aplicadas aos próximos vídeos e à Multitela.") {
        SettingLabel("Formato da transmissão ao vivo")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            StreamFormat.entries.forEach { format ->
                CategoryChip(format.label, settings.streamFormat == format) { actions.updateSettings(settings.copy(streamFormat = format)) }
            }
        }
        SettingLabel("Decodificador")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            VideoDecoder.entries.forEach { decoder ->
                CategoryChip(decoder.label, settings.decoder == decoder) { actions.updateSettings(settings.copy(decoder = decoder)) }
            }
        }
        Text("A preferência utiliza os decodificadores disponíveis no aparelho; o fallback continua ativo.",
            color = XColors.Body, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
        SettingLabel("Buffer mínimo")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(5, 10, 15, 30, 60).forEach { seconds ->
                CategoryChip("${seconds}s", settings.bufferSeconds == seconds) { actions.updateSettings(settings.copy(bufferSeconds = seconds)) }
            }
        }
        SettingToggle("Legendas ativadas por padrão", settings.subtitlesEnabled, "setting-subtitles") {
            actions.updateSettings(settings.copy(subtitlesEnabled = it))
        }
        SettingLabel("User agent")
        PillField(agent, { agent = it }, "User agent", Modifier.fillMaxWidth().testTag("setting-user-agent"), imeAction = ImeAction.Done,
            onDone = { actions.updateSettings(settings.copy(userAgent = agent)) })
        Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GlowButton("Salvar user agent", ringAlways = false) { actions.updateSettings(settings.copy(userAgent = agent)) }
            GlowButton("Padrão", ringAlways = false) { agent = DEFAULT_USER_AGENT; actions.updateSettings(settings.copy(userAgent = DEFAULT_USER_AGENT)) }
        }
    }
    Spacer(Modifier.height(18.dp))
    SettingsPanel("Seleção de player", "Escolha o player interno ou outro aplicativo para cada tipo de conteúdo.") {
        PlayerChoice("TV ao vivo", settings.livePlayer) { actions.updateSettings(settings.copy(livePlayer = it)) }
        PlayerChoice("Filmes", settings.moviePlayer) { actions.updateSettings(settings.copy(moviePlayer = it)) }
        PlayerChoice("Séries", settings.seriesPlayer) { actions.updateSettings(settings.copy(seriesPlayer = it)) }
        Text("Ao usar outro aplicativo, o endereço do vídeo e as credenciais nele contidas serão compartilhados com o aplicativo escolhido.",
            color = XColors.TextDim, fontSize = 12.sp, modifier = Modifier.padding(top = 12.dp))
    }
    Spacer(Modifier.height(18.dp))
    SettingsPanel("Guia de programação (EPG)", "Última atualização: ${epgUpdatedAt?.let(::fmtDateTime) ?: "Nunca"}  ·  $epgChannels canais") {
        SettingToggle("Atualizar EPG automaticamente", settings.autoUpdateEpg, "setting-epg-auto") {
            actions.updateSettings(settings.copy(autoUpdateEpg = it))
        }
        SettingLabel("Intervalo de atualização")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(6, 12, 24, 48).forEach { hours ->
                CategoryChip("${hours}h", settings.epgIntervalHours == hours) { actions.updateSettings(settings.copy(epgIntervalHours = hours)) }
            }
        }
        SettingLabel("Deslocamento do EPG")
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconAction(Icons.Filled.Remove, "Diminuir deslocamento do EPG") {
                actions.updateSettings(settings.copy(epgOffsetHours = (settings.epgOffsetHours - 1).coerceAtLeast(-12)))
            }
            Text("${if (settings.epgOffsetHours > 0) "+" else ""}${settings.epgOffsetHours} h", color = XColors.Text,
                fontSize = 15.sp, modifier = Modifier.widthIn(min = 54.dp).testTag("setting-epg-offset"))
            IconAction(Icons.Filled.Add, "Aumentar deslocamento do EPG") {
                actions.updateSettings(settings.copy(epgOffsetHours = (settings.epgOffsetHours + 1).coerceAtMost(12)))
            }
        }
        SettingToggle("Mostrar EPG na lista de canais", settings.showChannelEpg, "setting-show-epg") {
            actions.updateSettings(settings.copy(showChannelEpg = it))
        }
        SettingLabel("Formato do relógio")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CategoryChip("24 horas", settings.time24Hour) { actions.updateSettings(settings.copy(time24Hour = true)) }
            CategoryChip("12 horas", !settings.time24Hour) { actions.updateSettings(settings.copy(time24Hour = false)) }
        }
        SettingLabel("Fonte XMLTV personalizada (opcional)")
        PillField(xmltv, { xmltv = it }, "https://servidor/guia.xml", Modifier.fillMaxWidth().testTag("setting-xmltv-url"),
            keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done,
            onDone = { actions.updateSettings(settings.copy(customXmltvUrl = xmltv)) })
        Text("Deixe em branco para usar o guia do provedor. A URL pode conter credenciais e é armazenada como segredo.",
            color = XColors.Body, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
        Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GlowButton("Salvar fonte", ringAlways = false) { actions.updateSettings(settings.copy(customXmltvUrl = xmltv)) }
            GlowButton(if (epgLoading) "Atualizando…" else "Atualizar EPG", Icons.Filled.Refresh, ringAlways = false, onClick = actions.refreshEpg)
        }
        if (epgMessage != null) Text(epgMessage, color = XColors.TextDim, fontSize = 13.sp, modifier = Modifier.padding(top = 10.dp))
    }
    Spacer(Modifier.height(18.dp))
    SettingsPanel("Multitela e armazenamento", "Até quatro canais independentes; o limite real depende do provedor e do dispositivo.") {
        SettingLabel("Quantidade de telas")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            (1..4).forEach { count ->
                CategoryChip("$count", settings.multiScreens == count) { actions.updateSettings(settings.copy(multiScreens = count)) }
            }
        }
        Spacer(Modifier.height(14.dp))
        GlowButton("Limpar cache da conta", ringAlways = false) { confirmClear = true }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PlayerChoice(label: String, selected: PlaybackApp, onSelect: (PlaybackApp) -> Unit) {
    SettingLabel(label)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        PlaybackApp.entries.forEach { app -> CategoryChip(app.label, selected == app) { onSelect(app) } }
    }
}

@Composable
internal fun SettingLabel(label: String) {
    Text(label, color = XColors.Text, fontSize = 13.sp, fontWeight = FontWeight.Medium,
        modifier = Modifier.padding(top = 16.dp, bottom = 8.dp))
}

@Composable
internal fun SettingToggle(label: String, checked: Boolean, tag: String, onCheckedChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = XColors.Text, fontSize = 14.sp, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange, modifier = Modifier.testTag(tag))
    }
}
