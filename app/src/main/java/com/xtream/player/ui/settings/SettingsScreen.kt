package com.xtream.player.ui.settings

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xtream.player.data.*
import com.xtream.player.ui.components.*
import com.xtream.player.ui.theme.XColors
import com.xtream.player.util.*

@Composable
fun SettingsScreen(
    session: Session?,
    content: ContentState,
    epg: Map<String, List<Programme>>,
    epgUpdatedAt: Long?,
    epgLoading: Boolean,
    epgMessage: String?,
    onRefreshEpg: () -> Unit,
    onLogout: () -> Unit,
    onChannelFocused: (Channel) -> Unit = {},
) {
    val now = System.currentTimeMillis()
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 26.dp, vertical = 16.dp)) {
        Text("CONFIGURAÇÕES", color = XColors.Text, fontSize = 40.sp, fontWeight = FontWeight.Bold)
        Text("Conta, plano e guia de programação (EPG)", color = XColors.TextDim, fontSize = 17.sp)
        Spacer(Modifier.height(18.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            // ---------- Conta
            Panel("Conta", Icons.Filled.AccountCircle, Modifier.weight(1f)) {
                Info("Usuário", session?.username ?: "—")
                Info("Servidor", session?.server ?: "—")
                Info("Status", session?.status ?: "—", valueColor = if (session?.status.equals("Ativa", true) || session?.status.equals("Active", true)) XColors.Ok else XColors.Accent)
                val exp = session?.expDate
                val days = exp?.let { ((it - now) / 86_400_000L).toInt() }
                Info("Expira em", exp?.let { fmtDate(it) } ?: "Ilimitado")
                if (days != null) Info("Dias restantes", if (exp != null && exp <= now) "Expirado" else "$days dias", valueColor = if (days < 7) XColors.Accent else XColors.Text)
                Info("Conexões", "${session?.activeConns ?: 0} / ${session?.maxConns ?: 0}")
                Info("Criada em", session?.createdAt?.let { fmtDate(it) } ?: "—")
                Info("Canais · Filmes · Séries", "${content.channels.size} · ${content.movies.size} · ${content.series.size}")
            }
            // ---------- Atualizador de EPG
            Panel("Guia de programação (EPG)", Icons.Filled.Schedule, Modifier.weight(1f)) {
                Info("Fonte", session?.let { it.epgSource.ifBlank { "${it.server.trimEnd('/')}/xmltv.php" } } ?: "—")
                Info("Última atualização", epgUpdatedAt?.let { fmtDateTime(it) } ?: "Nunca")
                Info("Canais com guia", "${epg.size}")
                Info("Programas carregados", "${epg.values.sumOf { it.size }}")
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    GlowButton(if (epgLoading) "Atualizando…" else "Atualizar EPG", Icons.Filled.Refresh) { onRefreshEpg() }
                    if (epgLoading) CircularProgressIndicator(Modifier.padding(start = 14.dp).size(24.dp), color = XColors.Accent, strokeWidth = 2.5.dp)
                }
                if (epgMessage != null) Text(epgMessage, color = XColors.TextDim, fontSize = 14.sp, modifier = Modifier.padding(top = 10.dp))
            }
        }
        Spacer(Modifier.height(18.dp))
        EpgReader(content.channels, epg, now, onChannelFocused)
        Spacer(Modifier.height(22.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            GlowButton("Sair da conta", Icons.Filled.Logout, ringAlways = false) { onLogout() }
            Spacer(Modifier.weight(1f))
            Text("IPTV Native ${com.example.iptv.BuildConfig.VERSION_NAME}", color = XColors.TextDim, fontSize = 14.sp)
        }
    }
}

/** Leitor de EPG: navega pelos canais e lista a programação a seguir. */
@Composable
private fun EpgReader(channels: List<Channel>, epg: Map<String, List<Programme>>, now: Long, onChannelFocused: (Channel) -> Unit) {
    var idx by remember { mutableIntStateOf(0) }
    val ch = channels.getOrNull(idx.coerceIn(0, (channels.size - 1).coerceAtLeast(0)))
    LaunchedEffect(ch?.id) { ch?.let(onChannelFocused) }
    val list = ch?.let { epg[it.epgId] }.orEmpty().filter { it.stop > now }.sortedBy { it.start }.take(6)
    Panel("Leitor de EPG", Icons.Filled.Event, Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SmallBtn(Icons.Filled.ChevronLeft) { if (channels.isNotEmpty()) idx = (idx - 1 + channels.size) % channels.size }
            Text(ch?.let { "${it.number} · ${it.name}" } ?: "Nenhum canal", color = XColors.Text, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 18.dp).widthIn(min = 200.dp))
            SmallBtn(Icons.Filled.ChevronRight) { if (channels.isNotEmpty()) idx = (idx + 1) % channels.size }
        }
        Spacer(Modifier.height(12.dp))
        if (list.isEmpty()) Text("Sem programação carregada. Toque em “Atualizar EPG”.", color = XColors.TextDim, fontSize = 15.sp)
        list.forEach { p ->
            val live = now in p.start until p.stop
            Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("${fmtClock(p.start)} – ${fmtClock(p.stop)}", color = if (live) XColors.Accent else XColors.TextDim, fontSize = 15.sp, modifier = Modifier.width(130.dp))
                Text(p.title, color = XColors.Text, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                if (live) Text("AO VIVO", color = XColors.Live, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SmallBtn(icon: ImageVector, onClick: () -> Unit) {
    val shape = RoundedCornerShape(50)
    Box(Modifier.size(44.dp).focusGlow(shape).clip(shape).background(Color(0x1FFFFFFF)).combinedClickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = XColors.Text, modifier = Modifier.size(26.dp))
    }
}

@Composable
private fun Panel(title: String, icon: ImageVector, modifier: Modifier, content: @Composable ColumnScope.() -> Unit) {
    GlassPanel(modifier, RoundedCornerShape(22.dp)) {
        Column(Modifier.padding(22.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, tint = XColors.Gold, modifier = Modifier.size(24.dp))
                Text(title, color = XColors.Text, fontSize = 21.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 10.dp))
            }
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
private fun Info(label: String, value: String, valueColor: Color = XColors.Text) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = XColors.TextDim, fontSize = 16.sp, modifier = Modifier.weight(0.9f))
        Text(value, color = valueColor, fontSize = 16.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1.3f))
    }
}
