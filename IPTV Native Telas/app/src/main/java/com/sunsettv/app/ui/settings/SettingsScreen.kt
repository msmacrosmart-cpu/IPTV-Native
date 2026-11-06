package com.sunsettv.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sunsettv.app.data.EpgUpdateState
import com.sunsettv.app.domain.AccountInfo
import com.sunsettv.app.ui.components.CenteredLoading
import com.sunsettv.app.ui.components.ErrorState
import com.sunsettv.app.ui.components.GlassPanel
import com.sunsettv.app.ui.components.ScreenHeader
import com.sunsettv.app.ui.components.SunsetButton
import com.sunsettv.app.ui.components.SunsetFocusable
import com.sunsettv.app.ui.components.SunsetProgressBar
import com.sunsettv.app.ui.components.formatDate
import com.sunsettv.app.ui.components.formatDateTime
import com.sunsettv.app.ui.components.rememberNowMs
import com.sunsettv.app.ui.theme.SunsetColors
import java.util.concurrent.TimeUnit

/**
 * Aba "Configurações": conta, plano/expiração, EPG (leitor XMLTV + atualizador) e sessão.
 * Duas colunas com rolagem; cada item é focável, então o D-pad rola a tela automaticamente.
 */
@Composable
fun SettingsScreen(
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
    vm: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory),
) {
    val state by vm.state.collectAsState()
    val epgState by vm.epgState.collectAsState()
    val now = rememberNowMs(60_000L)

    Column(
        modifier.fillMaxSize().padding(start = 28.dp, end = 28.dp, top = 24.dp, bottom = 20.dp),
    ) {
        ScreenHeader("CONFIGURAÇÕES", "Gerencie sua conta e a guia de programação")
        Spacer(Modifier.height(16.dp))

        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            // ───── Coluna esquerda: Conta + Plano ─────
            Column(
                Modifier.weight(1f).fillMaxSize().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                when (val acc = state.account) {
                    AccountState.Loading -> GlassPanel(Modifier.fillMaxWidth()) { CenteredLoading() }
                    is AccountState.Error -> GlassPanel(Modifier.fillMaxWidth()) {
                        ErrorState(acc.message, onRetry = vm::loadAccount, modifier = Modifier.padding(bottom = 24.dp))
                    }
                    is AccountState.Ready -> {
                        AccountCard(acc.info, now)
                        PlanCard(acc.info, now)
                    }
                }
            }

            // ───── Coluna direita: EPG + Sessão ─────
            Column(
                Modifier.weight(1f).fillMaxSize().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                EpgCard(
                    state = state,
                    epgState = epgState,
                    onUpdate = vm::updateEpg,
                    onAutoChange = vm::setAutoEpg,
                    onCustomUrlChange = vm::setCustomXmltv,
                )
                SessionCard(onRefresh = vm::loadAccount, onLogout = { vm.logout(onLogout) })
            }
        }
    }
}

// ───────────────────────── Cartões ─────────────────────────

@Composable
private fun CardColumn(title: String, content: @Composable () -> Unit) {
    GlassPanel(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, color = SunsetColors.TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            content()
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String, valueColor: Color = SunsetColors.TextPrimary) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = SunsetColors.TextSecondary, fontSize = 14.sp, modifier = Modifier.weight(0.4f))
        Text(
            value,
            color = valueColor,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(0.6f),
        )
    }
}

@Composable
private fun StatusChip(text: String, color: Color) {
    Box(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.18f))
            .border(1.dp, color.copy(alpha = 0.6f), RoundedCornerShape(50))
            .padding(horizontal = 12.dp, vertical = 3.dp),
    ) {
        Text(text, color = color, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun AccountCard(info: AccountInfo, now: Long) {
    val active = info.isActiveAt(now)
    CardColumn("Conta") {
        InfoRow("Usuário", info.username)
        InfoRow("Servidor", info.serverUrl)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Status", color = SunsetColors.TextSecondary, fontSize = 14.sp, modifier = Modifier.weight(0.4f))
            Box(Modifier.weight(0.6f)) {
                if (active) StatusChip("ATIVA", SunsetColors.Success)
                else StatusChip(if (info.isExpiredAt(now)) "EXPIRADA" else info.status.uppercase(), SunsetColors.Danger)
            }
        }
        InfoRow("Conexões", "${info.activeConnections} / ${info.maxConnections}")
        info.createdAtMs?.let { InfoRow("Criada em", formatDate(it)) }
        InfoRow("Período de teste", if (info.isTrial) "Sim" else "Não")
        info.timezone?.let { InfoRow("Fuso do servidor", it) }
    }
}

@Composable
private fun PlanCard(info: AccountInfo, now: Long) {
    CardColumn("Plano") {
        val exp = info.expiresAtMs
        if (exp == null) {
            Text("Plano ilimitado", color = SunsetColors.Success, fontSize = 26.sp, fontWeight = FontWeight.Bold)
            Text("Sem data de expiração", color = SunsetColors.TextSecondary, fontSize = 14.sp)
        } else {
            val days = TimeUnit.MILLISECONDS.toDays(exp - now)
            val expired = exp < now
            val low = !expired && days <= 7
            val headline = when {
                expired -> "Plano expirado"
                days == 0L -> "Expira hoje"
                days == 1L -> "Expira em 1 dia"
                else -> "Expira em $days dias"
            }
            Text(
                headline,
                color = if (expired || low) SunsetColors.Danger else SunsetColors.TextPrimary,
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
            )
            Text("Vencimento: ${formatDateTime(exp)}", color = SunsetColors.TextSecondary, fontSize = 14.sp)
            val created = info.createdAtMs
            if (created != null && exp > created) {
                val remaining = ((exp - now).toFloat() / (exp - created)).coerceIn(0f, 1f)
                SunsetProgressBar(remaining, Modifier.fillMaxWidth())
                Text(
                    "${(remaining * 100).toInt()}% do período restante",
                    color = SunsetColors.TextSecondary,
                    fontSize = 12.sp,
                )
            }
        }
    }
}

@Composable
private fun EpgCard(
    state: SettingsUiState,
    epgState: EpgUpdateState,
    onUpdate: () -> Unit,
    onAutoChange: (Boolean) -> Unit,
    onCustomUrlChange: (String) -> Unit,
) {
    val busy = epgState is EpgUpdateState.Downloading || epgState is EpgUpdateState.Parsing
    CardColumn("Guia de programação (EPG)") {
        when (epgState) {
            is EpgUpdateState.Downloading -> {
                val p = epgState.progress
                Text(
                    if (p != null) "Baixando guia… ${(p * 100).toInt()}%" else "Baixando guia…",
                    color = SunsetColors.TextPrimary,
                    fontSize = 14.sp,
                )
                if (p != null) SunsetProgressBar(p, Modifier.fillMaxWidth())
                else LinearProgressIndicator(Modifier.fillMaxWidth(), color = SunsetColors.Coral, trackColor = Color(0x33FFFFFF))
            }
            EpgUpdateState.Parsing -> {
                Text("Processando a programação…", color = SunsetColors.TextPrimary, fontSize = 14.sp)
                LinearProgressIndicator(Modifier.fillMaxWidth(), color = SunsetColors.Coral, trackColor = Color(0x33FFFFFF))
            }
            is EpgUpdateState.Error -> Text(epgState.message, color = SunsetColors.Danger, fontSize = 14.sp)
            EpgUpdateState.Idle -> Unit
        }

        InfoRow(
            "Última atualização",
            if (state.lastEpgUpdateMs > 0) formatDateTime(state.lastEpgUpdateMs) else "Nunca",
        )
        InfoRow("Canais com EPG", state.epgChannels.toString())
        InfoRow("Programas carregados", state.epgPrograms.toString())

        SunsetButton(
            text = if (busy) "Atualizando…" else "Atualizar EPG agora",
            icon = Icons.Filled.Refresh,
            enabled = !busy,
            onClick = onUpdate,
        )

        SunsetFocusable(onClick = { onAutoChange(!state.autoEpg) }, modifier = Modifier.fillMaxWidth()) {
            Row(
                Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Atualizar automaticamente ao abrir (a cada 12 h)",
                    color = SunsetColors.TextPrimary,
                    fontSize = 14.sp,
                    modifier = Modifier.weight(1f),
                )
                Switch(
                    checked = state.autoEpg,
                    onCheckedChange = null,
                    colors = SwitchDefaults.colors(
                        checkedTrackColor = SunsetColors.Coral,
                        checkedThumbColor = Color.White,
                    ),
                )
            }
        }

        Text("Fonte XMLTV personalizada (opcional)", color = SunsetColors.TextSecondary, fontSize = 13.sp)
        SunsetTextField(
            value = state.customXmltv,
            onValueChange = onCustomUrlChange,
            placeholder = "Vazio = usar xmltv.php do servidor",
        )
    }
}

@Composable
private fun SunsetTextField(value: String, onValueChange: (String) -> Unit, placeholder: String) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(50)
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = TextStyle(color = SunsetColors.TextPrimary, fontSize = 14.sp),
        cursorBrush = SolidColor(SunsetColors.Coral),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
        modifier = Modifier.fillMaxWidth().onFocusChanged { focused = it.isFocused },
        decorationBox = { inner ->
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(shape)
                    .background(if (focused) Color(0x33FFFFFF) else Color(0x14FFFFFF))
                    .border(if (focused) 2.dp else 1.dp, if (focused) SunsetColors.Orange else Color(0x1AFFFFFF), shape)
                    .padding(horizontal = 18.dp, vertical = 12.dp),
            ) {
                if (value.isEmpty()) Text(placeholder, color = SunsetColors.TextSecondary, fontSize = 14.sp)
                inner()
            }
        },
    )
}

@Composable
private fun SessionCard(onRefresh: () -> Unit, onLogout: () -> Unit) {
    CardColumn("Sessão") {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SunsetButton("Atualizar dados da conta", onClick = onRefresh, icon = Icons.Filled.Refresh)
            SunsetButton("Sair da conta", onClick = onLogout, icon = Icons.AutoMirrored.Filled.ExitToApp, danger = true)
        }
    }
}
