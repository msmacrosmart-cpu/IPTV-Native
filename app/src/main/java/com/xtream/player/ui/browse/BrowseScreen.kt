package com.xtream.player.ui.browse

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xtream.player.data.Category
import com.xtream.player.data.VodItem
import com.xtream.player.ui.components.CatalogGridFraction
import com.xtream.player.ui.components.CatalogGridGap
import com.xtream.player.ui.components.CatalogGridPadding
import com.xtream.player.ui.components.PosterCard
import com.xtream.player.ui.components.catalogColumns
import com.xtream.player.ui.components.focusGlow
import com.xtream.player.ui.theme.DmSans
import com.xtream.player.ui.theme.XColors

private const val PAGE_SIZE = 24

/** Landscape catalog: a dedicated vertical category column and a paged poster grid. */
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
    val categoryTitle = categories.firstOrNull { it.id == category }?.name ?: "Todos"
    Row(Modifier.fillMaxSize()) {
        CategoryPane(title, categories, counts, items.size, category, { category = it },
            Modifier.weight(1f - CatalogGridFraction).fillMaxHeight().testTag("browse-categories-pane"))
        CatalogGrid(categoryTitle, visible, filtered.size, safePage, pages, favorites, onToggleFav, onSelect,
            onPage = { page = it }, modifier = Modifier.weight(CatalogGridFraction).fillMaxHeight())
    }
}

@Composable
private fun CategoryPane(title: String, categories: List<Category>, counts: Map<String, Int>, total: Int,
                         selected: String?, onSelect: (String?) -> Unit, modifier: Modifier = Modifier) {
    var categoryQuery by rememberSaveable(title) { mutableStateOf("") }
    val visibleCategories = remember(categories, categoryQuery) {
        categories.filter { it.name.contains(categoryQuery, ignoreCase = true) }
    }
    Column(modifier.background(XColors.Sidebar.copy(alpha = .88f)).padding(horizontal = 10.dp, vertical = 12.dp)) {
        Text("Categorias de $title", color = XColors.Text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
        CategorySearch(categoryQuery, { categoryQuery = it }, Modifier.padding(top = 4.dp, bottom = 8.dp))
        LazyColumn(Modifier.weight(1f).testTag("category-list"), contentPadding = PaddingValues(vertical = 2.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)) {
            item { CatalogCategoryRow("Todos", total, selected == null) { onSelect(null) } }
            items(visibleCategories, key = { it.id }) { cat ->
                CatalogCategoryRow(cat.name, counts[cat.id] ?: 0, selected == cat.id) { onSelect(cat.id) }
            }
            if (visibleCategories.isEmpty() && categoryQuery.isNotBlank()) item {
                Text("Nenhuma categoria encontrada.", color = XColors.TextDim, fontSize = 12.sp,
                    modifier = Modifier.padding(8.dp))
            }
        }
    }
}

@Composable
private fun CategorySearch(value: String, onValueChange: (String) -> Unit, modifier: Modifier = Modifier) {
    val keyboard = LocalSoftwareKeyboardController.current
    val shape = RoundedCornerShape(12.dp)
    var focused by remember { mutableStateOf(false) }
    BasicTextField(value, onValueChange, singleLine = true,
        textStyle = TextStyle(color = XColors.Text, fontSize = 13.sp, fontFamily = DmSans),
        cursorBrush = SolidColor(XColors.Accent),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
        modifier = modifier.fillMaxWidth().heightIn(min = 46.dp).testTag("category-search")
            .onFocusChanged { focused = it.isFocused },
        decorationBox = { inner ->
            Row(Modifier.fillMaxWidth().heightIn(min = 46.dp).clip(shape).background(Color(0xB00A0C10))
                .border(if (focused) 2.dp else 1.dp, if (focused) XColors.Focus else XColors.Line, shape)
                .padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Search, null, tint = XColors.TextDim, modifier = Modifier.size(18.dp))
                Box(Modifier.weight(1f).padding(start = 7.dp)) {
                    if (value.isEmpty()) Text("Pesquisar categorias", color = XColors.TextDim, fontSize = 12.sp,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    inner()
                }
            }
        })
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CatalogCategoryRow(name: String, count: Int, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(10.dp)
    Row(Modifier.fillMaxWidth().heightIn(min = 44.dp).focusGlow(shape, zoom = 1f).clip(shape)
        .background(if (selected) XColors.Selection else Color(0xB00C0E12))
        .border(1.dp, if (selected) XColors.Accent.copy(alpha = .7f) else XColors.Line, shape)
        .combinedClickable(onClick = onClick).padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Text(name, color = if (selected) XColors.BlueSoft else XColors.Text, fontSize = 14.sp,
            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(4.dp))
        Text("$count", color = XColors.Body, fontSize = 12.sp, maxLines = 1)
    }
}

@Composable
private fun CatalogGrid(title: String, visible: List<VodItem>, count: Int, page: Int, pages: Int,
                        favorites: List<String>, onToggleFav: (String) -> Unit, onSelect: (VodItem) -> Unit,
                        onPage: (Int) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier) {
        Row(Modifier.fillMaxWidth().heightIn(min = 54.dp).background(Color(0x990A0C10)).padding(start = 16.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Text(title, color = XColors.Text, fontSize = 18.sp, fontWeight = FontWeight.Bold,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Text("$count títulos", color = XColors.TextDim, fontSize = 12.sp, maxLines = 1)
            Spacer(Modifier.width(12.dp))
            Text("${if (count == 0) 0 else page * PAGE_SIZE + 1}–${minOf((page + 1) * PAGE_SIZE, count)}", color = XColors.TextDim, fontSize = 12.sp)
            PageButton(Icons.Filled.ChevronLeft, "Página anterior", page > 0) { onPage(page - 1) }
            PageButton(Icons.Filled.ChevronRight, "Próxima página", page + 1 < pages) { onPage(page + 1) }
        }
        if (count == 0) Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            Text("Nenhum item encontrado.", color = XColors.Text, fontSize = 14.sp)
        } else BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            LazyVerticalGrid(columns = GridCells.Fixed(catalogColumns(maxWidth)), modifier = Modifier.fillMaxSize().testTag("catalog-grid"),
                contentPadding = PaddingValues(CatalogGridPadding), horizontalArrangement = Arrangement.spacedBy(CatalogGridGap),
                verticalArrangement = Arrangement.spacedBy(CatalogGridGap)) {
                items(visible, key = { it.favoriteKey }) { item ->
                    BoxWithConstraints(Modifier.fillMaxWidth()) {
                        PosterCard(item, width = maxWidth, isFavorite = item.favoriteKey in favorites,
                            onLongClick = { onToggleFav(item.favoriteKey) }, onClick = { onSelect(item) })
                    }
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
