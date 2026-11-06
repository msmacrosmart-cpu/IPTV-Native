package com.xtream.player.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.iptv.R
import com.xtream.player.ui.theme.DmSans
import com.xtream.player.ui.theme.XColors

/** The only six primary destinations requested for this app. */
enum class Tab(val label: String, val outlined: ImageVector, val filled: ImageVector) {
    Home("Home", Icons.Outlined.Home, Icons.Filled.Home),
    Live("TV", Icons.Outlined.LiveTv, Icons.Filled.LiveTv),
    Movies("Filmes", Icons.Outlined.LocalMovies, Icons.Filled.LocalMovies),
    Series("Séries", Icons.Outlined.Tv, Icons.Filled.Tv),
    Multi("Multitela", Icons.Outlined.GridView, Icons.Filled.GridView),
    Settings("Configurações", Icons.Outlined.Settings, Icons.Filled.Settings),
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SideButton(tab: Tab, selected: Boolean, size: Dp = 48.dp, onClick: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    val neon = Color(0xFF86B8FF)
    var focused by remember { mutableStateOf(false) }
    // The same restrained halo is drawn on phone and TV: no elevation or dark shadow.
    Box(Modifier.size(size).onFocusChanged { focused = it.hasFocus }
        .border(3.dp, if (focused) neon.copy(alpha = .12f) else Color.Transparent, shape)
        .padding(2.dp).clip(shape)
        .background(if (selected || focused) Color(0xFF202331) else Color.Transparent)
        .border(1.dp, when { focused -> neon; selected -> neon.copy(alpha = .5f); else -> Color.Transparent }, shape)
        .semantics { this.selected = selected }
        .combinedClickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(if (selected) tab.filled else tab.outlined, tab.label,
            tint = if (selected || focused) neon else XColors.TextDim, modifier = Modifier.size(22.dp))
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
        BoxWithConstraints(modifier.fillMaxHeight().width(64.dp).background(XColors.Sidebar)) {
            val dense = maxHeight < 420.dp
            val itemSize = when { maxHeight < 300.dp -> 34.dp; maxHeight < 350.dp -> 38.dp; else -> 48.dp }
            Column(Modifier.fillMaxSize().padding(horizontal = 8.dp, vertical = if (dense) 4.dp else 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally) {
                Image(painterResource(R.drawable.brand_square), "IPTV Native",
                    modifier = Modifier.size(if (dense) 32.dp else 42.dp).clip(RoundedCornerShape(8.dp)))
                Spacer(Modifier.height(if (dense) 4.dp else 18.dp))
                Tab.entries.filter { it != Tab.Settings }.forEach {
                    SideButton(it, selected == it, itemSize) { onSelect(it) }
                    Spacer(Modifier.height(2.dp))
                }
                Spacer(Modifier.weight(1f))
                SideButton(Tab.Settings, selected == Tab.Settings, itemSize) { onSelect(Tab.Settings) }
            }
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
    tab: Tab, query: String, onQuery: (String) -> Unit,
    searchFocus: FocusRequester, compact: Boolean, onRefresh: () -> Unit,
) {
    val hideKeyboard = rememberHideKeyboard()
    Row(Modifier.fillMaxWidth().height(56.dp).background(XColors.Sidebar).padding(horizontal = if (compact) 10.dp else 20.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        if (tab != Tab.Settings && tab != Tab.Multi) {
            val shape = RoundedCornerShape(10.dp)
            var focused by remember { mutableStateOf(false) }
            val placeholder = when (tab) {
                Tab.Live -> "Buscar canais..."; Tab.Movies -> "Buscar filmes..."
                Tab.Series -> "Buscar séries..."; else -> "Buscar na biblioteca..."
            }
            Box(Modifier.weight(1f)) {
                Row(Modifier.widthIn(max = 560.dp).fillMaxWidth().height(40.dp).clip(shape)
                    .background(XColors.Field).border(1.dp, if (focused) XColors.Focus else Color(0x17FFFFFF), shape)
                    .padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Search, null, tint = XColors.TextDim, modifier = Modifier.size(18.dp))
                    Box(Modifier.weight(1f).padding(start = 8.dp)) {
                        if (query.isEmpty()) Text(placeholder, color = XColors.Body, fontSize = 14.sp, maxLines = 1)
                        BasicTextField(query, onQuery, singleLine = true,
                            textStyle = TextStyle(color = XColors.Text, fontSize = 14.sp, fontFamily = DmSans),
                            cursorBrush = SolidColor(XColors.Accent),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = { hideKeyboard() }),
                            modifier = Modifier.fillMaxWidth().testTag("search-field").focusRequester(searchFocus)
                                .onFocusChanged {
                                    if (focused && !it.isFocused) hideKeyboard()
                                    focused = it.isFocused
                                })
                    }
                }
            }
        } else Spacer(Modifier.weight(1f))
        if (!compact) IconAction(Icons.Outlined.Refresh, "Atualizar catálogo", onClick = onRefresh)
    }
}
