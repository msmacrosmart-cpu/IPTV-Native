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
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xtream.player.data.Category
import com.xtream.player.data.CatalogSort
import com.xtream.player.data.sortCatalog
import com.xtream.player.data.VodItem
import com.xtream.player.ui.components.CategoryChip
import com.xtream.player.ui.components.CatalogGridFraction
import com.xtream.player.ui.components.CatalogGridGap
import com.xtream.player.ui.components.CatalogGridPadding
import com.xtream.player.ui.components.PosterCard
import com.xtream.player.ui.components.catalogColumns
import com.xtream.player.ui.components.focusGlow
import com.xtream.player.ui.components.rememberHideKeyboard
import com.xtream.player.ui.theme.DmSans
import com.xtream.player.ui.theme.XColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Landscape catalog: a continuously scrollable grid, with folder and sort state saved per tab. */
@Composable
fun BrowseScreen(title: String, categories: List<Category>, items: List<VodItem>, query: String,
                 favorites: List<String>, onToggleFav: (String) -> Unit, onSelect: (VodItem) -> Unit,
                 restoreItemKey: String? = null, onReturnFocusRestored: () -> Unit = {}) {
    var category by rememberSaveable(title) { mutableStateOf<String?>(null) }
    var sort by rememberSaveable(title) { mutableStateOf(CatalogSort.RECENT) }
    var ascending by rememberSaveable(title) { mutableStateOf(false) }
    val counts = remember(items) { items.groupingBy { it.categoryId }.eachCount() }
    // Filtering and sorting big provider libraries should not block the TV's UI thread.
    val request = remember(items, category, query, sort, ascending) { Any() }
    val result by produceState<Pair<Any, List<VodItem>>?>(null, request) {
        val ordered = withContext(Dispatchers.Default) { sortCatalog(items, category, query, sort, ascending) }
        value = request to ordered
    }
    // Never briefly show the previous folder/order while a new result is being calculated.
    val filtered = result?.takeIf { it.first === request }?.second
    val categoryTitle = if (category == null) "Todos" else categories.firstOrNull { it.id == category }?.name ?: "Categoria indisponível"
    Row(Modifier.fillMaxSize()) {
        CategoryPane(title, categories, counts, items.size, category, { category = it },
            Modifier.weight(1f - CatalogGridFraction).fillMaxHeight().testTag("browse-categories-pane"))
        CatalogGrid(categoryTitle, category, query, filtered, favorites, sort, ascending,
            onSort = { criterion -> sort = criterion; ascending = criterion.defaultAscending },
            onDirection = { ascending = !ascending }, onToggleFav = onToggleFav, onSelect = onSelect,
            restoreItemKey = restoreItemKey, onReturnFocusRestored = onReturnFocusRestored,
            modifier = Modifier.weight(CatalogGridFraction).fillMaxHeight())
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
            item { CatalogCategoryRow("Todos", total, selected == null, Modifier.testTag("browse-category-all")) { onSelect(null) } }
            items(visibleCategories, key = { it.id }) { cat ->
                CatalogCategoryRow(cat.name, counts[cat.id] ?: 0, selected == cat.id,
                    Modifier.testTag("browse-category-${cat.id}")) { onSelect(cat.id) }
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
    val hideKeyboard = rememberHideKeyboard()
    val shape = RoundedCornerShape(12.dp)
    var focused by remember { mutableStateOf(false) }
    BasicTextField(value, onValueChange, singleLine = true,
        textStyle = TextStyle(color = XColors.Text, fontSize = 13.sp, fontFamily = DmSans),
        cursorBrush = SolidColor(XColors.Accent),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { hideKeyboard() }),
        modifier = modifier.fillMaxWidth().heightIn(min = 46.dp).testTag("category-search")
            .onFocusChanged {
                if (focused && !it.isFocused) hideKeyboard()
                focused = it.isFocused
            },
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
fun CatalogCategoryRow(name: String, count: Int, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(10.dp)
    Row(modifier.fillMaxWidth().heightIn(min = 44.dp).focusGlow(shape, zoom = 1f).clip(shape)
        .semantics { this.selected = selected }
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
private fun CatalogGrid(title: String, category: String?, query: String, items: List<VodItem>?, favorites: List<String>,
                        sort: CatalogSort, ascending: Boolean, onSort: (CatalogSort) -> Unit, onDirection: () -> Unit,
                        onToggleFav: (String) -> Unit, onSelect: (VodItem) -> Unit,
                        restoreItemKey: String?, onReturnFocusRestored: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier) {
        Column(Modifier.fillMaxWidth().background(Color(0x990A0C10)).padding(bottom = 8.dp)) {
            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(start = 16.dp, end = 12.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Text(title, color = XColors.Text, fontSize = 18.sp, fontWeight = FontWeight.Bold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).testTag("catalog-category-title"))
                Text(if (items == null) "Ordenando…" else "${items.size} títulos", color = XColors.TextDim, fontSize = 12.sp, maxLines = 1)
            }
            Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                LazyRow(Modifier.weight(1f).testTag("catalog-sort-list"),
                    horizontalArrangement = Arrangement.spacedBy(6.dp), contentPadding = PaddingValues(end = 6.dp)) {
                    items(CatalogSort.entries, key = { it.name }) { criterion ->
                        CategoryChip(criterion.label, sort == criterion,
                            modifier = Modifier.testTag("catalog-sort-${criterion.name.lowercase()}")
                                .semantics { selected = sort == criterion }) { onSort(criterion) }
                    }
                }
                CategoryChip(if (ascending) "↑ Crescente" else "↓ Decrescente", selected = true,
                    modifier = Modifier.testTag("catalog-sort-direction")) { onDirection() }
            }
        }
        // Changing folder, query, sort or direction starts that new result at its first row.
        // Returning from details keeps the active key and restores its saved grid position.
        key(category, query, sort, ascending) {
            // Keep the state mounted while results are recomputed asynchronously.
            val gridState = rememberLazyGridState()
            when {
                items == null -> Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(Modifier.size(24.dp), color = XColors.Accent, strokeWidth = 2.dp)
                }
                items.isEmpty() -> {
                    LaunchedEffect(restoreItemKey) { if (restoreItemKey != null) onReturnFocusRestored() }
                    Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Text("Nenhum item encontrado.", color = XColors.Text, fontSize = 14.sp)
                    }
                }
                else -> BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                    val restoreFocus = remember(restoreItemKey) { FocusRequester() }
                    val inputMode = LocalInputModeManager.current
                    LaunchedEffect(restoreItemKey, items) {
                        val key = restoreItemKey ?: return@LaunchedEffect
                        val index = items.indexOfFirst { it.favoriteKey == key }
                        if (index >= 0 && inputMode.inputMode != InputMode.Touch) {
                            // The saved scroll position normally already contains the selected card.
                            // If data changed while details were open, bring that item into view.
                            withFrameNanos { }
                            if (gridState.layoutInfo.visibleItemsInfo.none { it.key == key }) gridState.scrollToItem(index)
                            withFrameNanos { }
                            runCatching { restoreFocus.requestFocus() }
                        }
                        onReturnFocusRestored()
                    }
                    LazyVerticalGrid(columns = GridCells.Fixed(catalogColumns(maxWidth)), state = gridState,
                        modifier = Modifier.fillMaxSize().testTag("catalog-grid"),
                        contentPadding = PaddingValues(CatalogGridPadding), horizontalArrangement = Arrangement.spacedBy(CatalogGridGap),
                        verticalArrangement = Arrangement.spacedBy(CatalogGridGap)) {
                        items(items, key = { it.favoriteKey }) { item ->
                            BoxWithConstraints(Modifier.fillMaxWidth()) {
                                PosterCard(item, width = maxWidth, isFavorite = item.favoriteKey in favorites,
                                    modifier = if (item.favoriteKey == restoreItemKey) Modifier.focusRequester(restoreFocus) else Modifier,
                                    onLongClick = { onToggleFav(item.favoriteKey) }, onClick = { onSelect(item) })
                            }
                        }
                    }
                }
            }
        }
    }
}
