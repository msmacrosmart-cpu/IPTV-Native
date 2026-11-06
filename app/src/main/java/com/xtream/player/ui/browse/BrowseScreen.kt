package com.xtream.player.ui.browse

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xtream.player.data.Category
import com.xtream.player.data.VodItem
import com.xtream.player.ui.components.CategoryChip
import com.xtream.player.ui.components.PosterCard
import com.xtream.player.ui.components.focusGlow
import com.xtream.player.ui.theme.XColors

private const val PAGE_SIZE = 24

/** IPTVnator's category rail, 2:3 poster grid and explicit, bounded pagination. */
@Composable
fun BrowseScreen(title: String, categories: List<Category>, items: List<VodItem>, query: String,
                 favorites: List<String>, onToggleFav: (String) -> Unit, onSelect: (VodItem) -> Unit) {
    var category by rememberSaveable(title) { mutableStateOf<String?>(null) }
    var page by rememberSaveable(title) { mutableIntStateOf(0) }
    val counts = remember(items) { items.groupingBy { it.categoryId }.eachCount() }
    val filtered = remember(items, category, query) {
        items.filter { (category == null || it.categoryId == category) && it.title.contains(query, true) }
    }
    val pages = ((filtered.size + PAGE_SIZE - 1) / PAGE_SIZE).coerceAtLeast(1)
    LaunchedEffect(category, query) { page = 0 }
    LaunchedEffect(pages) { if (page >= pages) page = pages - 1 }
    val safePage = page.coerceIn(0, pages - 1)
    val from = (safePage * PAGE_SIZE).coerceAtMost(filtered.size)
    val visible = filtered.subList(from, minOf(from + PAGE_SIZE, filtered.size))
    val categoryTitle = categories.firstOrNull { it.id == category }?.name ?: "Todos os itens"
    BoxWithConstraints(Modifier.fillMaxSize().background(XColors.Bg)) {
        val wide = maxWidth >= 800.dp
        if (wide) Row(Modifier.fillMaxSize()) {
            CategoryPane(title, categories, counts, items.size, category, { category = it }, Modifier.width(300.dp).fillMaxHeight())
            CatalogGrid(categoryTitle, visible, filtered.size, safePage, pages, favorites, onToggleFav, onSelect,
                onPage = { page = it }, modifier = Modifier.weight(1f).fillMaxHeight())
        } else Column(Modifier.fillMaxSize()) {
            LazyRow(Modifier.fillMaxWidth().background(XColors.Sidebar).testTag("category-list"),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item { CategoryChip("Todos", category == null) { category = null } }
                items(categories, key = { it.id }) { cat -> CategoryChip(cat.name, category == cat.id) { category = cat.id } }
            }
            CatalogGrid(categoryTitle, visible, filtered.size, safePage, pages, favorites, onToggleFav, onSelect,
                onPage = { page = it }, modifier = Modifier.weight(1f).fillMaxWidth())
        }
    }
}

@Composable
private fun CategoryPane(title: String, categories: List<Category>, counts: Map<String, Int>, total: Int,
                         selected: String?, onSelect: (String?) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.background(XColors.Sidebar).padding(start = 12.dp, end = 12.dp, top = 18.dp)) {
        Text("Categorias de $title", color = XColors.Text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp))
        LazyColumn(Modifier.testTag("category-list"), contentPadding = PaddingValues(vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)) {
            item { CatalogCategoryRow("Todos", total, selected == null) { onSelect(null) } }
            items(categories, key = { it.id }) { cat ->
                CatalogCategoryRow(cat.name, counts[cat.id] ?: 0, selected == cat.id) { onSelect(cat.id) }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CatalogCategoryRow(name: String, count: Int, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(8.dp)
    Row(Modifier.fillMaxWidth().heightIn(min = 42.dp).focusGlow(shape, zoom = 1f).clip(shape)
        .background(if (selected) XColors.Selection else androidx.compose.ui.graphics.Color.Transparent)
        .border(1.dp, if (selected) XColors.Accent.copy(alpha = .55f) else androidx.compose.ui.graphics.Color.Transparent, shape)
        .combinedClickable(onClick = onClick).padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Text(name, color = if (selected) XColors.BlueSoft else XColors.Text, fontSize = 14.sp,
            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        Text("$count", color = XColors.Body, fontSize = 12.sp)
    }
}

@Composable
private fun CatalogGrid(title: String, visible: List<VodItem>, count: Int, page: Int, pages: Int,
                        favorites: List<String>, onToggleFav: (String) -> Unit, onSelect: (VodItem) -> Unit,
                        onPage: (Int) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier) {
        Row(Modifier.fillMaxWidth().heightIn(min = 54.dp).background(XColors.PanelHeader).padding(start = 16.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Text(title, color = XColors.Text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Text("$count itens", color = XColors.TextDim, fontSize = 12.sp)
            Spacer(Modifier.width(12.dp))
            Text("${if (count == 0) 0 else page * PAGE_SIZE + 1}–${minOf((page + 1) * PAGE_SIZE, count)}", color = XColors.TextDim, fontSize = 12.sp)
            PageButton(Icons.Filled.ChevronLeft, "Página anterior", page > 0) { onPage(page - 1) }
            PageButton(Icons.Filled.ChevronRight, "Próxima página", page + 1 < pages) { onPage(page + 1) }
        }
        if (count == 0) Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            Text("Nenhum item encontrado.", color = XColors.TextDim, fontSize = 14.sp)
        } else LazyVerticalGrid(columns = GridCells.Adaptive(minSize = 148.dp), modifier = Modifier.weight(1f).testTag("catalog-grid"),
            contentPadding = PaddingValues(20.dp), horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)) {
            items(visible, key = { it.favoriteKey }) { item ->
                BoxWithConstraints(Modifier.fillMaxWidth()) {
                    PosterCard(item, width = maxWidth, onLongClick = { onToggleFav(item.favoriteKey) },
                        onClick = { onSelect(item) }, isFavorite = item.favoriteKey in favorites)
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PageButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, enabled: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(8.dp)
    Box(Modifier.size(42.dp).focusGlow(shape, zoom = 1f).clip(shape).combinedClickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center) {
        Icon(icon, label, tint = if (enabled) XColors.Text else XColors.Body.copy(alpha = .35f), modifier = Modifier.size(22.dp))
    }
}
