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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xtream.player.ui.theme.XColors

enum class Tab(val outlined: ImageVector, val filled: ImageVector) {
    Home(Icons.Outlined.Home, Icons.Filled.Home),
    Movies(Icons.Outlined.Movie, Icons.Filled.Movie),
    Live(Icons.Outlined.Theaters, Icons.Filled.Theaters),
    Series(Icons.Outlined.Tv, Icons.Filled.Tv),
    Favorites(Icons.Outlined.StarBorder, Icons.Filled.Star),
    Settings(Icons.Outlined.Settings, Icons.Filled.Settings),
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SideButton(icon: ImageVector, selected: Boolean, size: androidx.compose.ui.unit.Dp, onClick: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Box(
        Modifier.size(size)
            .focusGlow(shape, zoom = 1.06f)
            .then(if (selected) Modifier.shadow(12.dp, shape, ambientColor = XColors.Blue, spotColor = XColors.Blue) else Modifier)
            .clip(shape)
            .background(if (selected) Color(0x264F6BFF) else Color.Transparent)
            .then(if (selected) Modifier.border(2.dp, XColors.Blue, shape) else Modifier)
            .combinedClickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, tint = if (selected) XColors.BlueSoft else Color(0xFFB9B9BF), modifier = Modifier.size(size * 0.5f))
    }
}

@Composable
fun Sidebar(selected: Tab, onSelect: (Tab) -> Unit, onSearch: () -> Unit, modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier.fillMaxHeight().width(104.dp).background(XColors.Sidebar)) {
        val size = if (maxHeight < 480.dp) 44.dp else 58.dp
        val gap = if (maxHeight < 480.dp) 6.dp else 16.dp
        Column(Modifier.fillMaxSize().padding(vertical = 14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            SideButton(Icons.Outlined.Search, false, size, onSearch)
            Spacer(Modifier.height(gap))
            listOf(Tab.Home, Tab.Movies, Tab.Live, Tab.Series, Tab.Favorites).forEach {
                SideButton(if (selected == it) it.filled else it.outlined, selected == it, size) { onSelect(it) }
                Spacer(Modifier.height(gap))
            }
            Spacer(Modifier.weight(1f))
            SideButton(if (selected == Tab.Settings) Tab.Settings.filled else Tab.Settings.outlined, selected == Tab.Settings, size) { onSelect(Tab.Settings) }
        }
        Box(Modifier.align(Alignment.CenterEnd).fillMaxHeight().width(1.dp).background(XColors.Line))
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BarIcon(icon: ImageVector, onClick: () -> Unit = {}) {
    val shape = RoundedCornerShape(12.dp)
    Box(Modifier.size(44.dp).focusGlow(shape).clip(shape).combinedClickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = Color(0xFFD0D0D5), modifier = Modifier.size(24.dp))
    }
}

@Composable
fun TopBar(
    placeholder: String,
    query: String,
    onQuery: (String) -> Unit,
    showScope: Boolean,
    scopeAll: Boolean,
    onScope: (Boolean) -> Unit,
    playlistName: String,
    playlistInfo: String,
    searchFocus: FocusRequester,
    onDownload: () -> Unit = {},
) {
    Row(Modifier.fillMaxWidth().height(60.dp).background(Color(0x66000000)), verticalAlignment = Alignment.CenterVertically) {
        Row(Modifier.padding(start = 28.dp).width(250.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Description, null, tint = XColors.TextDim, modifier = Modifier.size(18.dp))
            Column(Modifier.padding(start = 12.dp).weight(1f)) {
                Text(playlistName, color = XColors.Text, fontSize = 15.sp, maxLines = 1)
                Text(playlistInfo, color = XColors.TextDim, fontSize = 11.sp, maxLines = 1)
            }
            Icon(Icons.Filled.KeyboardArrowDown, null, tint = XColors.TextDim, modifier = Modifier.size(18.dp))
        }
        val shape = RoundedCornerShape(12.dp)
        var focused by remember { mutableStateOf(false) }
        Row(
            Modifier.widthIn(max = 520.dp).weight(1f).height(44.dp).clip(shape)
                .background(Color(0xFF241210))
                .border(if (focused) 2.dp else 1.dp, if (focused) FocusBrush else SolidColor(Color(0xFF7A2E24)), shape)
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Outlined.Search, null, tint = XColors.TextDim, modifier = Modifier.size(20.dp))
            Box(Modifier.weight(1f).padding(start = 10.dp)) {
                if (query.isEmpty()) Text(placeholder, color = XColors.TextDim, fontSize = 16.sp, maxLines = 1)
                BasicTextField(
                    query, onQuery, singleLine = true,
                    textStyle = TextStyle(color = XColors.Text, fontSize = 16.sp),
                    cursorBrush = SolidColor(XColors.Accent),
                    modifier = Modifier.fillMaxWidth().focusRequester(searchFocus)
                        .androidxOnFocus { focused = it },
                )
            }
            if (showScope) {
                Row(Modifier.clip(RoundedCornerShape(50)).background(Color(0x33000000))) {
                    ScopePill("Folder", !scopeAll) { onScope(false) }
                    ScopePill("All", scopeAll) { onScope(true) }
                }
            } else {
                Box(Modifier.clip(RoundedCornerShape(6.dp)).background(Color(0xFF7A2E24)).padding(horizontal = 8.dp, vertical = 3.dp)) {
                    Text("KK", color = Color(0xFFFFB38A), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        Spacer(Modifier.width(10.dp))
        BarIcon(Icons.Outlined.Keyboard)
        BarIcon(Icons.Outlined.Add)
        Spacer(Modifier.weight(0.001f))
        Box(Modifier.padding(start = 12.dp).fillMaxHeight().width(1.dp).background(XColors.Line))
        Box(Modifier.width(72.dp), contentAlignment = Alignment.Center) { BarIcon(Icons.Outlined.Download, onDownload) }
    }
}

@Composable
private fun ScopePill(text: String, active: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.clip(RoundedCornerShape(50))
            .background(if (active) Color(0xFF8A3A22) else Color.Transparent)
            .combinedClickableCompat(onClick)
            .padding(horizontal = 12.dp, vertical = 5.dp),
    ) { Text(text, color = if (active) Color(0xFFFFD2B0) else XColors.TextDim, fontSize = 14.sp) }
}

@OptIn(ExperimentalFoundationApi::class)
private fun Modifier.combinedClickableCompat(onClick: () -> Unit): Modifier = this.combinedClickable(onClick = onClick)

private fun Modifier.androidxOnFocus(cb: (Boolean) -> Unit): Modifier =
    this.then(Modifier.onFocusChangedCompat(cb))

private fun Modifier.onFocusChangedCompat(cb: (Boolean) -> Unit): Modifier =
    this.onFocusChanged { cb(it.isFocused) }
