package com.xtream.player.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xtream.player.ui.theme.DmSans
import com.xtream.player.ui.theme.XColors

/** The only six primary destinations requested for this app. */
enum class Tab(val label: String, val outlined: ImageVector, val filled: ImageVector) {
    Home("Início", Icons.Outlined.Dashboard, Icons.Filled.Dashboard),
    Live("TV ao vivo", Icons.Outlined.LiveTv, Icons.Filled.LiveTv),
    Movies("Filmes", Icons.Outlined.Movie, Icons.Filled.Movie),
    Series("Séries", Icons.Outlined.Tv, Icons.Filled.Tv),
    Multi("Multitela", Icons.Outlined.GridView, Icons.Filled.GridView),
    Settings("Configurações", Icons.Outlined.Settings, Icons.Filled.Settings),
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SideButton(tab: Tab, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Box(Modifier.size(48.dp).focusGlow(shape, width = 2.dp, zoom = 1f).clip(shape)
        .background(if (selected) XColors.Selection else Color.Transparent)
        .border(1.dp, if (selected) XColors.Accent.copy(alpha = .62f) else Color.Transparent, shape)
        .semantics { this.selected = selected }
        .combinedClickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(if (selected) tab.filled else tab.outlined, tab.label,
            tint = if (selected) XColors.BlueSoft else XColors.TextDim, modifier = Modifier.size(22.dp))
        if (selected) Box(Modifier.align(Alignment.CenterEnd).width(3.dp).height(20.dp)
            .clip(RoundedCornerShape(2.dp)).background(XColors.Accent))
    }
}

@Composable
fun Sidebar(selected: Tab, onSelect: (Tab) -> Unit, compact: Boolean, modifier: Modifier = Modifier) {
    if (compact) {
        Row(modifier.fillMaxWidth().height(56.dp).background(XColors.Sidebar).padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            Tab.entries.forEach { SideButton(it, selected == it) { onSelect(it) } }
        }
    } else {
        Column(modifier.fillMaxHeight().width(64.dp).background(XColors.Sidebar).padding(horizontal = 8.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.height(48.dp))
            Tab.entries.filter { it != Tab.Settings }.forEach {
                SideButton(it, selected == it) { onSelect(it) }
                Spacer(Modifier.height(2.dp))
            }
            Spacer(Modifier.weight(1f))
            SideButton(Tab.Settings, selected == Tab.Settings) { onSelect(Tab.Settings) }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun IconAction(icon: ImageVector, label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(10.dp)
    Box(modifier.size(44.dp).focusGlow(shape, zoom = 1f).clip(shape).combinedClickable(onClick = onClick),
        contentAlignment = Alignment.Center) {
        Icon(icon, label, tint = XColors.TextDim, modifier = Modifier.size(21.dp))
    }
}

@Composable
fun TopBar(
    tab: Tab, query: String, onQuery: (String) -> Unit, playlistName: String,
    playlistInfo: String, searchFocus: FocusRequester, compact: Boolean, onRefresh: () -> Unit,
) {
    Row(Modifier.fillMaxWidth().height(56.dp).background(XColors.Sidebar).padding(horizontal = if (compact) 10.dp else 20.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.width(if (compact) 100.dp else 224.dp).clip(RoundedCornerShape(9.dp))
            .background(Color(0x0DFFFFFF)).padding(horizontal = 10.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.PlaylistPlay, null, tint = XColors.TextDim, modifier = Modifier.size(19.dp))
            Column(Modifier.padding(start = 8.dp).weight(1f)) {
                Text(playlistName, color = XColors.Text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (!compact) Text(playlistInfo, color = XColors.Body, fontSize = 10.sp, maxLines = 1)
            }
        }
        if (tab != Tab.Settings && tab != Tab.Multi) {
            val shape = RoundedCornerShape(10.dp)
            var focused by remember { mutableStateOf(false) }
            val placeholder = when (tab) {
                Tab.Live -> "Buscar canais..."; Tab.Movies -> "Buscar filmes..."
                Tab.Series -> "Buscar séries..."; else -> "Buscar na biblioteca..."
            }
            Row(Modifier.weight(1f).widthIn(max = 560.dp).height(40.dp).clip(shape)
                .background(XColors.Field).border(1.dp, if (focused) XColors.Focus else Color(0x17FFFFFF), shape)
                .padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Search, null, tint = XColors.TextDim, modifier = Modifier.size(18.dp))
                Box(Modifier.weight(1f).padding(start = 8.dp)) {
                    if (query.isEmpty()) Text(placeholder, color = XColors.Body, fontSize = 14.sp, maxLines = 1)
                    BasicTextField(query, onQuery, singleLine = true,
                        textStyle = TextStyle(color = XColors.Text, fontSize = 14.sp, fontFamily = DmSans),
                        cursorBrush = SolidColor(XColors.Accent),
                        modifier = Modifier.fillMaxWidth().testTag("search-field").focusRequester(searchFocus)
                            .onFocusChanged { focused = it.isFocused })
                }
            }
        } else Spacer(Modifier.weight(1f))
        if (!compact) IconAction(Icons.Outlined.Refresh, "Atualizar catálogo", onClick = onRefresh)
    }
}
