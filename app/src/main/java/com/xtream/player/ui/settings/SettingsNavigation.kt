@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.xtream.player.ui.settings

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xtream.player.ui.components.focusGlow
import com.xtream.player.ui.theme.XColors
import kotlinx.coroutines.launch

internal class SettingsAnchor(val bringIntoView: BringIntoViewRequester = BringIntoViewRequester(),
                              val focus: FocusRequester = FocusRequester())
internal val LocalSettingsAnchors = staticCompositionLocalOf<Map<String, SettingsAnchor>> { emptyMap() }

private data class Shortcut(val id: String, val title: String, val icon: ImageVector, val target: String)

/** The reference has blue outline icons arranged in cards. All shortcuts lead to real controls. */
private val shortcuts = listOf(
    Shortcut("general", "Configurações gerais", Icons.Outlined.Settings, "Configurações gerais"),
    Shortcut("epg", "EPG", Icons.Outlined.LiveTv, "Guia de programação (EPG)"),
    Shortcut("stream", "Formato de transmissão", Icons.Outlined.Schedule, "Formato da transmissão ao vivo"),
    Shortcut("time", "Formato de hora", Icons.Outlined.AccessTime, "Formato do relógio"),
    Shortcut("automation", "Automação", Icons.Outlined.Sync, "Automação"),
    Shortcut("parental", "Controle parental", Icons.Outlined.VerifiedUser, "Controle parental"),
    Shortcut("player", "Seleção de player", Icons.Outlined.PlayCircleOutline, "Seleção de player"),
    Shortcut("player-settings", "Configurações do player", Icons.Outlined.Tune, "Configurações gerais"),
    Shortcut("external", "Players externos", Icons.Outlined.OndemandVideo, "Seleção de player"),
    Shortcut("speed", "Teste de velocidade", Icons.Outlined.Speed, "Teste de velocidade"),
    Shortcut("vpn", "VPN", Icons.Outlined.Security, "VPN"),
    Shortcut("device", "Tipo de dispositivo", Icons.Outlined.Devices, "Tipo de dispositivo"),
    Shortcut("account", "Informações da conta", Icons.Outlined.AccountCircle, "Dados da conta"),
    Shortcut("profiles", "Perfis", Icons.Outlined.People, "Perfis"),
)

@Composable
internal fun rememberSettingsAnchors(): Map<String, SettingsAnchor> = remember {
    shortcuts.map { it.target }.distinct().associateWith { SettingsAnchor() }
}

/** A shortcut scrolls to and focuses the real setting rather than opening an inert card. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun settingsAnchor(name: String): Modifier {
    val anchor = LocalSettingsAnchors.current[name] ?: return Modifier
    return Modifier.bringIntoViewRequester(anchor.bringIntoView).focusRequester(anchor.focus)
        .focusGlow(RoundedCornerShape(6.dp), zoom = 1f).focusable()
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
internal fun SettingsShortcuts() {
    val anchors = LocalSettingsAnchors.current
    val scope = rememberCoroutineScope()
    BoxWithConstraints(Modifier.widthIn(max = 1000.dp).fillMaxWidth().testTag("settings-shortcuts")) {
        val columns = if (maxWidth < 440.dp) 2 else 3
        val width = (maxWidth - 10.dp * (columns - 1)) / columns
        FlowRow(maxItemsInEachRow = columns, horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            shortcuts.forEach { shortcut ->
                val shape = RoundedCornerShape(12.dp)
                Column(Modifier.width(width).height(104.dp).testTag("setting-shortcut-${shortcut.id}")
                    .focusGlow(shape, zoom = 1f).clip(shape).background(XColors.Panel)
                    .border(1.dp, XColors.Line, shape).combinedClickable {
                        anchors[shortcut.target]?.let { anchor ->
                            scope.launch {
                                anchor.bringIntoView.bringIntoView()
                                runCatching { anchor.focus.requestFocus() }
                            }
                        }
                    }.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center) {
                    Icon(shortcut.icon, null, tint = Color(0xFF79ACF6), modifier = Modifier.size(35.dp))
                    Text(shortcut.title, color = XColors.Text, fontSize = 12.sp, fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 6.dp))
                }
            }
        }
    }
}
