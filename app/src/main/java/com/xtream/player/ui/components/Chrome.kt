package com.xtream.player.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.iptv.R
import com.xtream.player.ui.theme.DmSans
import java.util.Locale
import com.xtream.player.ui.theme.XColors

/** Four primary destinations; settings is a separate action in the header. */
enum class Tab(val label: String, val icon: Int?) {
    Home("Home", R.drawable.ic_nav_home),
    Live("TV", R.drawable.ic_nav_tv),
    Movies("Filmes", R.drawable.ic_nav_movies),
    Series("Séries", R.drawable.ic_nav_series),
    Settings("Configurações", null),
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SideButton(tab: Tab, selected: Boolean, size: Dp = 48.dp, onClick: () -> Unit) {
    val neon = Color(0xFF8BAFFF)
    val shape = RoundedCornerShape(12.dp)
    var focused by remember { mutableStateOf(false) }
    // Thin translucent rings give the supplied icon style a blue halo, without a heavy shadow.
    Column(Modifier.width(size + 14.dp).onFocusChanged { focused = it.hasFocus }
        .semantics(mergeDescendants = true) { contentDescription = tab.label; this.selected = selected }
        .combinedClickable(onClick = onClick).padding(vertical = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(size + 6.dp)
            .background(neon.copy(alpha = if (focused) .24f else .10f), RoundedCornerShape(15.dp))
            .padding(3.dp).clip(shape)
            .background(Brush.verticalGradient(listOf(Color(0xD8414655), Color(0xE8191C25))))
            .border(if (focused) 2.dp else 1.5.dp,
                neon.copy(alpha = if (focused || selected) 1f else .67f), shape),
            contentAlignment = Alignment.Center) {
            tab.icon?.let { icon ->
                Icon(painterResource(icon), null, tint = Color.Unspecified, modifier = Modifier.size(size * .55f))
            }
        }
        Text(tab.label.uppercase(Locale.ROOT), color = if (focused || selected) Color.White else XColors.TextDim,
            fontSize = if (size < 44.dp) 8.sp else 10.sp, fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Clip)
    }
}

@Composable
fun Sidebar(selected: Tab, onSelect: (Tab) -> Unit, compact: Boolean, modifier: Modifier = Modifier) {
    val primary = listOf(Tab.Home, Tab.Live, Tab.Movies, Tab.Series)
    if (compact) {
        Row(modifier.fillMaxWidth().height(78.dp).background(XColors.Sidebar).padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            primary.forEach { SideButton(it, selected == it, 40.dp) { onSelect(it) } }
        }
    } else {
        BoxWithConstraints(modifier.fillMaxHeight().width(76.dp).background(XColors.Sidebar)) {
            val itemSize = when { maxHeight < 300.dp -> 38.dp; maxHeight < 350.dp -> 42.dp; maxHeight < 450.dp -> 48.dp; else -> 56.dp }
            Column(Modifier.fillMaxSize().padding(vertical = 4.dp), horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceEvenly) {
                primary.forEach { SideButton(it, selected == it, itemSize) { onSelect(it) } }
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
    searchFocus: FocusRequester, compact: Boolean, onRefresh: () -> Unit, onOpenSettings: () -> Unit,
) {
    val hideKeyboard = rememberHideKeyboard()
    Row(Modifier.fillMaxWidth().height(56.dp).background(XColors.Sidebar).padding(horizontal = if (compact) 10.dp else 20.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        if (tab != Tab.Settings) {
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
        IconAction(Icons.Outlined.Settings, "Configurações", onClick = onOpenSettings)
    }
}
