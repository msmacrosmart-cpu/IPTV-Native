package com.example.iptv.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.iptv.BuildConfig
import com.example.iptv.data.model.StreamFormat
import com.example.iptv.data.model.VideoDecoder
import com.example.iptv.ui.components.AppLogo
import com.example.iptv.ui.components.GlassCard
import com.example.iptv.ui.components.PrimaryButton
import com.example.iptv.ui.components.TopographicBackground
import com.example.iptv.ui.theme.AccentCoral
import com.example.iptv.ui.theme.LiveRed
import com.example.iptv.ui.theme.SuccessGreen
import com.example.iptv.ui.theme.TextMuted
import com.example.iptv.ui.theme.TextPrimary
import com.example.iptv.ui.theme.TextSecondary
import com.example.iptv.util.CacheUtils
import com.example.iptv.util.FormatUtils
import com.example.iptv.viewmodel.MainTvViewModel
import kotlinx.coroutines.delay

/**
 * Settings: real account data, stream format, buffer, decoder preference, EPG refresh,
 * latency test, cache cleanup and logout.
 */
@Composable
fun SettingsScreen(
    viewModel: MainTvViewModel,
    onLogout: () -> Unit
) {
    val account by viewModel.account.collectAsState()
    val settings by viewModel.settings.collectAsState()
    val latency by viewModel.pingLatencyMillis.collectAsState()
    val pingLoading by viewModel.pingLoading.collectAsState()
    val epgSyncLabel by viewModel.epgSyncLabel.collectAsState()

    val context = LocalContext.current
    var cacheMessage by remember { mutableStateOf<String?>(null) }
    var confirmLogout by remember { mutableStateOf(false) }
    val cacheSizeKb = remember(cacheMessage) { CacheUtils.approximateSizeKb(context) }

    LaunchedEffect(cacheMessage) {
        if (cacheMessage != null) {
            delay(3_000)
            cacheMessage = null
        }
    }

    TopographicBackground {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AppLogo(markSize = 38)
                    }
                    Text(
                        text = "Versão ${BuildConfig.VERSION_NAME}",
                        color = TextMuted,
                        fontSize = 11.sp
                    )
                }

                Spacer(modifier = Modifier.height(18.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // ------------------------------------------------- left column
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        SettingsCard(icon = Icons.Default.LiveTv, title = "Conta Xtream") {
                            InfoRow("Usuário", account?.username?.ifBlank { "—" } ?: "—")
                            InfoRow(
                                "Status",
                                account?.statusLabel ?: "—",
                                valueColor = when {
                                    account?.isActive != true -> LiveRed
                                    account?.statusLabel == "Ativa" -> SuccessGreen
                                    else -> AccentCoral
                                }
                            )
                            InfoRow(
                                "Expira em",
                                account?.expiresAtMillis?.let { millis ->
                                    val days = FormatUtils.remainingDays(millis)
                                    val suffix = if (days != null && days >= 0) " ($days dias)" else ""
                                    FormatUtils.formatDate(millis) + suffix
                                } ?: "Ilimitado"
                            )
                            InfoRow(
                                "Conexões",
                                "${account?.activeConnections ?: 0} de ${account?.maxConnections ?: 0} em uso"
                            )
                            InfoRow("Servidor", account?.panelUrl?.ifBlank { "—" } ?: "—", maxLines = 1)
                            InfoRow("Protocolo", account?.serverProtocol?.uppercase() ?: "—")
                            InfoRow("Fuso do painel", account?.timezone ?: "—")
                            InfoRow("Formatos", account?.allowedFormats?.joinToString(", ")?.ifBlank { "—" } ?: "—")
                        }

                        SettingsCard(icon = Icons.Default.Speed, title = "Rede e EPG") {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Text("Latência do painel", color = TextSecondary, fontSize = 12.sp)
                                    Text(
                                        text = latency?.let { "$it ms" } ?: "Não testado",
                                        color = if (latency != null) SuccessGreen else TextMuted,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                SmallButton(
                                    label = if (pingLoading) "Testando…" else "Testar",
                                    onClick = { viewModel.testLatency() },
                                    enabled = !pingLoading
                                )
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Guia de programação (EPG)", color = TextSecondary, fontSize = 12.sp)
                                    Text(
                                        text = "Última atualização: $epgSyncLabel",
                                        color = TextMuted,
                                        fontSize = 11.sp
                                    )
                                }
                                SmallButton(
                                    label = "Atualizar",
                                    onClick = { viewModel.refreshEpg() }
                                )
                            }
                        }
                    }

                    // ------------------------------------------------ right column
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        SettingsCard(icon = Icons.Default.Cloud, title = "Reprodução") {
                            Text(
                                text = "Formato do stream",
                                color = TextSecondary,
                                fontSize = 12.sp
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                StreamFormat.entries.forEach { format ->
                                    OptionChip(
                                        label = format.label,
                                        isSelected = settings.streamFormat == format,
                                        onClick = { viewModel.updateStreamFormat(format) }
                                    )
                                }
                            }
                            Text(
                                text = "Use HLS (m3u8) caso o canal fique em tela preta no formato padrão.",
                                color = TextMuted,
                                fontSize = 10.5.sp,
                                modifier = Modifier.padding(top = 6.dp)
                            )

                            Spacer(modifier = Modifier.height(14.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Buffer de reprodução", color = TextSecondary, fontSize = 12.sp)
                                Text("${settings.bufferSeconds}s", color = AccentCoral, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                            Slider(
                                value = settings.bufferSeconds.toFloat(),
                                onValueChange = { viewModel.updateBufferSeconds(it.toInt()) },
                                valueRange = 5f..60f,
                                steps = 10,
                                colors = SliderDefaults.colors(
                                    thumbColor = AccentCoral,
                                    activeTrackColor = AccentCoral,
                                    inactiveTrackColor = Color.White.copy(alpha = 0.18f)
                                )
                            )

                            Spacer(modifier = Modifier.height(6.dp))

                            Text("Decodificador de vídeo", color = TextSecondary, fontSize = 12.sp)
                            Spacer(modifier = Modifier.height(6.dp))
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                VideoDecoder.entries.forEach { decoder ->
                                    DecoderRow(
                                        decoder = decoder,
                                        isSelected = settings.decoder == decoder,
                                        onClick = { viewModel.updateDecoder(decoder) }
                                    )
                                }
                            }
                        }

                        SettingsCard(icon = Icons.Default.Storage, title = "Armazenamento") {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Cache de imagens e streams", color = TextSecondary, fontSize = 12.sp)
                                    Text(
                                        text = "$cacheSizeKb KB em uso",
                                        color = TextMuted,
                                        fontSize = 11.sp
                                    )
                                    cacheMessage?.let {
                                        Text(text = it, color = AccentCoral, fontSize = 11.sp)
                                    }
                                }
                                SmallButton(
                                    label = "Limpar",
                                    onClick = {
                                        val ok = CacheUtils.clear(context)
                                        cacheMessage = if (ok) "Cache limpo com sucesso" else "Não foi possível limpar"
                                    },
                                    icon = Icons.Default.Delete
                                )
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            Text(
                                text = "IPTV Native ${BuildConfig.VERSION_NAME} · player_api.php · " +
                                    "Media3 ExoPlayer · Kotlin + Jetpack Compose",
                                color = TextMuted,
                                fontSize = 10.5.sp
                            )
                        }

                        SettingsCard(icon = Icons.AutoMirrored.Filled.ExitToApp, title = "Sessão") {
                            Text(
                                text = if (confirmLogout) {
                                    "Ao sair, login, senha e catálogo serão removidos deste dispositivo."
                                } else {
                                    "Sua sessão está salva com criptografia AES no Android Keystore."
                                },
                                color = TextMuted,
                                fontSize = 11.sp
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                PrimaryButton(
                                    label = if (confirmLogout) "Confirmar saída" else "Sair da conta",
                                    onClick = {
                                        if (confirmLogout) onLogout() else confirmLogout = true
                                    },
                                    modifier = Modifier.width(200.dp)
                                )
                                if (confirmLogout) {
                                    SmallButton(
                                        label = "Cancelar",
                                        onClick = { confirmLogout = false }
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                if (confirmLogout) {
                    Text(
                        text = "Toque em \"Confirmar saída\" para desconectar desta conta.",
                        color = AccentCoral,
                        fontSize = 11.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    content: @Composable () -> Unit
) {
    GlassCard(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp)) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0x26FF6B5B)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(imageVector = icon, contentDescription = null, tint = AccentCoral, modifier = Modifier.size(15.dp))
                }
                Spacer(modifier = Modifier.width(9.dp))
                Text(text = title, color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
private fun InfoRow(
    label: String,
    value: String,
    valueColor: Color = TextPrimary,
    maxLines: Int = 2
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, color = TextMuted, fontSize = 11.5.sp)
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = value,
            color = valueColor,
            fontSize = 11.5.sp,
            fontWeight = FontWeight.Medium,
            maxLines = maxLines,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 260.dp)
        )
    }
}

@Composable
private fun OptionChip(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    Box(
        modifier = Modifier
            .height(36.dp)
            .clip(RoundedCornerShape(11.dp))
            .background(
                when {
                    isSelected -> AccentCoral
                    isFocused -> Color(0x33FFFFFF)
                    else -> Color(0x1AFFFFFF)
                }
            )
            .border(
                1.dp,
                if (isFocused) Color.White else Color.White.copy(alpha = 0.12f),
                RoundedCornerShape(11.dp)
            )
            .focusable(interactionSource = interactionSource)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = if (isSelected) Color.White else TextSecondary,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun DecoderRow(
    decoder: VideoDecoder,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(38.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (isSelected) Color(0x26FF6B5B) else Color(0x14FFFFFF))
            .border(
                1.dp,
                if (isSelected) AccentCoral else Color.White.copy(alpha = 0.08f),
                RoundedCornerShape(10.dp)
            )
            .focusable(interactionSource = interactionSource)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(12.dp)
                .clip(CircleShape)
                .background(if (isSelected) AccentCoral else Color.Transparent)
                .border(1.5.dp, if (isSelected) AccentCoral else Color.White.copy(alpha = 0.3f), CircleShape)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = decoder.label,
            color = if (isSelected || isFocused) TextPrimary else TextSecondary,
            fontSize = 11.5.sp,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}

@Composable
private fun SmallButton(
    label: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    Row(
        modifier = modifier
            .height(34.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(
                when {
                    !enabled -> Color(0x14FFFFFF)
                    isFocused -> AccentCoral
                    else -> Color(0x26FFFFFF)
                }
            )
            .border(
                1.dp,
                if (isFocused) Color.White else Color.White.copy(alpha = 0.14f),
                RoundedCornerShape(10.dp)
            )
            .focusable(interactionSource = interactionSource, enabled = enabled)
            .clickable(interactionSource = interactionSource, indication = null, enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (enabled) Color.White else TextMuted,
                modifier = Modifier.size(13.dp)
            )
            Spacer(modifier = Modifier.width(5.dp))
        }
        Text(
            text = label,
            color = if (enabled) Color.White else TextMuted,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}
