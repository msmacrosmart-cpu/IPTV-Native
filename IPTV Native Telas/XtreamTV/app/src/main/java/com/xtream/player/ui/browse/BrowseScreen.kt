package com.xtream.player.ui.browse

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xtream.player.data.Category
import com.xtream.player.data.VodItem
import com.xtream.player.ui.components.*
import com.xtream.player.ui.theme.XColors

/** Tela de catálogo usada por FILMES e SÉRIES (mesmo layout das referências). */
@Composable
fun BrowseScreen(
    title: String,
    allLabel: String,
    categories: List<Category>,
    items: List<VodItem>,
    query: String,
    scopeAll: Boolean,
    onToggleFav: (String) -> Unit,
    onPlay: (VodItem) -> Unit,
) {
    var cat by remember { mutableStateOf<String?>(null) }
    var showAll by remember { mutableStateOf(false) }
    val searching = query.isNotBlank()
    val filtered = items.filter {
        (cat == null || (searching && scopeAll) || it.categoryId == cat) && it.title.contains(query, true)
    }
    val cont = filtered.filter { it.progress > 0f }
    val recent = filtered.reversed().take(12)
    val catLabel = categories.firstOrNull { it.id == cat }?.name

    LazyColumn(contentPadding = PaddingValues(start = 26.dp, end = 26.dp, top = 16.dp, bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Text(title.uppercase(), color = XColors.Text, fontSize = 40.sp, fontWeight = FontWeight.Bold)
            Text("Browse and search within your collection", color = XColors.TextDim, fontSize = 17.sp)
            Spacer(Modifier.height(10.dp))
        }
        item {
            LazyRow(contentPadding = PaddingValues(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                item { CategoryChip(catLabel ?: "All Categories", selected = cat == null, dropdown = true) { cat = null } }
                items(categories, key = { it.id }) { c -> CategoryChip(c.name, selected = cat == c.id) { cat = if (cat == c.id) null else c.id } }
            }
        }
        item {
            SectionTitle(if (catLabel != null) catLabel else allLabel)
            LazyRow(contentPadding = PaddingValues(vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                items(filtered, key = { it.id }) { PosterCard(it, onLongClick = { onToggleFav("vod:${it.id}") }) { onPlay(it) } }
            }
        }
        if (cont.isNotEmpty()) item {
            SectionTitle("Continue Watching")
            LazyRow(contentPadding = PaddingValues(vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                items(cont, key = { it.id }) { LandscapeCard(it, 190.dp) { onPlay(it) } }
            }
        }
        if (recent.isNotEmpty()) item {
            SectionTitle("Recently Added")
            LazyRow(contentPadding = PaddingValues(vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                items(recent, key = { "r" + it.id }) { LandscapeCard(it, 150.dp) { onPlay(it) } }
            }
        }
    }
}
