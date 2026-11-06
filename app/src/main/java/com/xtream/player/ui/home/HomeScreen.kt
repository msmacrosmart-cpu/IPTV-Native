package com.xtream.player.ui.home

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.iptv.data.model.VodDetails
import com.xtream.player.data.*
import com.xtream.player.ui.components.*
import com.xtream.player.ui.theme.XColors
import com.xtream.player.util.fmtClock
import com.xtream.player.util.initials
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

@Composable
fun HomeScreen(content: ContentState, epg: Map<String, List<Programme>>, query: String,
               favorites: List<String>, heroDetails: Map<String, VodDetails>, loadingHeroKey: String?, time24Hour: Boolean,
               onSelectItem: (VodItem) -> Unit, onWatchItem: (VodItem) -> Unit,
               onPrepareHeroes: (List<VodItem>) -> Unit,
               onPlayChannel: (Channel) -> Unit, onToggleFav: (String) -> Unit) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { delay(30_000); now = System.currentTimeMillis() } }
    val all = remember(content.movies, content.series) { content.movies + content.series }
    val favoriteSet = favorites.toSet()
    val matching = remember(all, query) { all.filter { it.title.contains(query, true) } }
    val continuing = remember(matching) { matching.filter { it.progress > 0f } }
    val favoriteVod = remember(matching, favoriteSet) { matching.filter { it.favoriteKey in favoriteSet } }
    val favoriteChannels = remember(content.channels, favoriteSet, query) {
        content.channels.filter { "ch:${it.id}" in favoriteSet && it.name.contains(query, true) }
    }
    // The hero is always the newest movies from the panel's Lançamentos category,
    // never favorites, series or unrelated highly-rated titles.
    val candidates = remember(content.movieCategories, content.movies) {
        latestReleaseMovies(content.movieCategories, content.movies)
    }
    val topMovies = remember(content.movies, query) { topRated(content.movies).filter { it.title.contains(query, true) } }
    val topSeries = remember(content.series, query) { topRated(content.series).filter { it.title.contains(query, true) } }
    LaunchedEffect(candidates.map { it.favoriteKey }) { onPrepareHeroes(candidates) }
    val slides = remember(candidates, heroDetails) { candidates.map { it.withHeroDetails(heroDetails[it.favoriteKey]) } }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val compact = maxWidth < 800.dp || maxHeight < 400.dp
        val gutter = if (compact) 16.dp else 28.dp
        // Match the catalog posters on Home too, leaving more titles visible at once.
        val cardWidth = catalogCardWidth(maxWidth * CatalogGridFraction)
        val heroHeight = (maxHeight * .80f).coerceIn(if (compact) 270.dp else 360.dp, 560.dp)
        LazyColumn(Modifier.fillMaxSize().testTag("home-list"), contentPadding = PaddingValues(bottom = 36.dp),
            verticalArrangement = Arrangement.spacedBy(if (compact) 24.dp else 30.dp)) {
            if (slides.isNotEmpty()) item(key = "hero") {
                HeroCarousel(slides, heroHeight, compact, loadingHeroKey, onSelectItem, onWatchItem)
            }
            else item(key = "hero-empty") { Text("Nenhum filme na categoria Lançamentos do provedor.",
                color = XColors.TextDim, modifier = Modifier.padding(gutter).testTag("hero-empty")) }
            item(key = "top-movies") {
                PosterRail("Top 10 filmes · por nota do provedor", topMovies, gutter, cardWidth, favorites, onSelectItem, onToggleFav,
                    "Nenhum filme disponível.", ranked = true, tag = "top-movies")
            }
            item(key = "top-series") {
                PosterRail("Top 10 séries · por nota do provedor", topSeries, gutter, cardWidth, favorites, onSelectItem, onToggleFav,
                    "Nenhuma série disponível.", ranked = true, tag = "top-series")
            }
            if (continuing.isNotEmpty()) item(key = "continue") {
                PosterRail("Continuar assistindo", continuing, gutter, cardWidth, favorites, onSelectItem, onToggleFav,
                    "Os filmes e episódios que você começar aparecerão aqui.")
            }
            if (favoriteChannels.isNotEmpty()) item(key = "live-favs") {
                Column {
                    RailHeader("Canais favoritos ao vivo", favoriteChannels.size, gutter)
                    LazyRow(contentPadding = PaddingValues(start = gutter, end = gutter, top = 10.dp, bottom = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(favoriteChannels, key = { it.id }) { ch ->
                            LiveCard(ch, epg[ch.epgId], now, time24Hour, onLongClick = { onToggleFav("ch:${ch.id}") }) { onPlayChannel(ch) }
                        }
                    }
                }
            }
            if (favoriteVod.isNotEmpty()) item(key = "vod-favs") {
                PosterRail("Filmes e séries favoritos", favoriteVod, gutter, cardWidth, favorites, onSelectItem, onToggleFav,
                    "Marque filmes ou séries como favoritos para encontrá-los aqui.")
            }
            if (matching.isNotEmpty()) item(key = "suggested") {
                PosterRail("Explore sua biblioteca", matching, gutter, cardWidth, favorites, onSelectItem, onToggleFav, "", tag = "explore")
            }
        }
    }
}

@Composable
private fun PosterRail(title: String, items: List<VodItem>, gutter: androidx.compose.ui.unit.Dp,
                       cardWidth: androidx.compose.ui.unit.Dp, favorites: List<String>, onSelect: (VodItem) -> Unit,
                       onToggleFav: (String) -> Unit, empty: String, ranked: Boolean = false, tag: String = "") {
    Column(if (tag.isNotEmpty()) Modifier.testTag(tag) else Modifier) {
        RailHeader(title, items.size, gutter)
        if (items.isEmpty()) EmptyRail(empty, gutter)
        else LazyRow(modifier = if (tag.isNotEmpty()) Modifier.testTag("rail-$tag") else Modifier,
            contentPadding = PaddingValues(start = gutter, end = gutter, top = 10.dp, bottom = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(CatalogGridGap)) {
            itemsIndexed(items, key = { _, item -> item.favoriteKey }) { index, item ->
                PosterCard(item, width = cardWidth, onLongClick = { onToggleFav(item.favoriteKey) },
                    onClick = { onSelect(item) }, isFavorite = item.favoriteKey in favorites,
                    rank = if (ranked) index + 1 else null)
            }
        }
    }
}

@Composable
private fun RailHeader(title: String, count: Int, gutter: androidx.compose.ui.unit.Dp) {
    Row(Modifier.fillMaxWidth().padding(horizontal = gutter), verticalAlignment = Alignment.CenterVertically) {
        SectionTitle(title)
        if (count > 0) Text("  $count", color = XColors.Body, fontSize = 12.sp)
    }
}

@Composable
private fun EmptyRail(message: String, gutter: androidx.compose.ui.unit.Dp) {
    Text(message, color = XColors.Body, fontSize = 13.sp, modifier = Modifier.padding(start = gutter, end = gutter, top = 10.dp))
}

private enum class HeroFocus { Watch, Details, Indicator, None }

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HeroCarousel(slides: List<VodItem>, height: androidx.compose.ui.unit.Dp, compact: Boolean,
                         loadingHeroKey: String?, onSelect: (VodItem) -> Unit, onWatch: (VodItem) -> Unit) {
    val pager = rememberPagerState(pageCount = { slides.size })
    val scope = rememberCoroutineScope()
    val inputMode = LocalInputModeManager.current
    val ids = slides.map { it.favoriteKey }
    val watchFocus = remember(ids) { ids.associateWith { FocusRequester() } }
    val detailsFocus = remember(ids) { ids.associateWith { FocusRequester() } }
    val indicatorFocus = remember(ids) { ids.associateWith { FocusRequester() } }
    var hasFocus by remember { mutableStateOf(false) }
    var focusedControl by remember { mutableStateOf(HeroFocus.None) }
    var changingPage by remember { mutableStateOf(false) }
    // Auto-advance only for touch. A physical keyboard or TV remote never fights a timer.
    LaunchedEffect(pager, slides.size, hasFocus, loadingHeroKey, inputMode.inputMode) {
        while (slides.size > 1) {
            delay(8_000)
            if (inputMode.inputMode == InputMode.Touch && !hasFocus && !changingPage &&
                loadingHeroKey == null && !pager.isScrollInProgress) {
                pager.animateScrollToPage((pager.currentPage + 1) % slides.size)
            }
        }
    }
    Box(Modifier.fillMaxWidth().height(height).testTag("hero-carousel")
        .onFocusChanged { hasFocus = it.hasFocus; if (!it.hasFocus && !changingPage) focusedControl = HeroFocus.None }
        .onPreviewKeyEvent { event ->
            val delta = when (event.key) { Key.DirectionRight -> 1; Key.DirectionLeft -> -1; else -> 0 }
            val control = focusedControl
            if (delta == 0 || control == HeroFocus.None) false
            else if (event.type == KeyEventType.KeyUp) true
            else if (event.type != KeyEventType.KeyDown) false
            else {
                // Consume repeats and both edges: HorizontalPager must not also move the page.
                if (!changingPage && event.nativeKeyEvent.repeatCount == 0) {
                    val current = pager.currentPage.coerceIn(0, slides.lastIndex)
                    val currentKey = slides[current].favoriteKey
                    when {
                        control == HeroFocus.Watch && delta > 0 ->
                            runCatching { detailsFocus[currentKey]?.requestFocus() }
                        control == HeroFocus.Details && delta < 0 ->
                            runCatching { watchFocus[currentKey]?.requestFocus() }
                        slides.size > 1 -> {
                            val target = (current + delta + slides.size) % slides.size
                            changingPage = true
                            scope.launch {
                                try {
                                    // A direct jump avoids the focused old page pulling an animation back.
                                    pager.scrollToPage(target)
                                    withFrameNanos { }
                                    val nextKey = slides[target].favoriteKey
                                    runCatching {
                                        when (control) {
                                            HeroFocus.Watch -> watchFocus[nextKey]?.requestFocus()
                                            HeroFocus.Details -> detailsFocus[nextKey]?.requestFocus()
                                            HeroFocus.Indicator -> indicatorFocus[nextKey]?.requestFocus()
                                            HeroFocus.None -> Unit
                                        }
                                    }
                                } finally { changingPage = false }
                            }
                        }
                    }
                }
                true
            }
        }.focusGroup()) {
        HorizontalPager(state = pager, key = { slides[it].favoriteKey }, modifier = Modifier.fillMaxSize()) { index ->
            val item = slides[index]
            HeroSlide(item, compact, loadingHeroKey == item.favoriteKey,
                watchModifier = Modifier.focusRequester(watchFocus.getValue(item.favoriteKey))
                    .onFocusChanged { if (it.isFocused) focusedControl = HeroFocus.Watch },
                detailsModifier = Modifier.focusRequester(detailsFocus.getValue(item.favoriteKey))
                    .onFocusChanged { if (it.isFocused) focusedControl = HeroFocus.Details },
                onSelect = { onSelect(item) }, onWatch = { onWatch(item) })
        }
        if (slides.size > 1) Row(
            if (compact) Modifier.align(Alignment.TopEnd).padding(end = 8.dp, top = 2.dp)
            else Modifier.align(Alignment.BottomEnd).padding(end = 24.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically) {
            slides.forEachIndexed { index, slide ->
                val shape = RoundedCornerShape(50)
                Box(Modifier.size(if (compact) 26.dp else 34.dp)
                    .focusRequester(indicatorFocus.getValue(slide.favoriteKey))
                    .onFocusChanged { if (it.isFocused) focusedControl = HeroFocus.Indicator }
                    .focusGlow(shape, zoom = 1f).clip(shape)
                    .semantics { contentDescription = "Destaque ${index + 1}: ${slide.title}" }
                    .combinedClickable { scope.launch { pager.scrollToPage(index) } }, contentAlignment = Alignment.Center) {
                    Box(Modifier.width(if (pager.currentPage == index) 18.dp else 6.dp).height(6.dp).clip(shape)
                        .background(if (pager.currentPage == index) XColors.Text else XColors.TextDim.copy(alpha = .5f)))
                }
            }
        }
    }
}

@Composable
private fun HeroSlide(item: VodItem, compact: Boolean, loading: Boolean,
                      watchModifier: Modifier, detailsModifier: Modifier, onSelect: () -> Unit, onWatch: () -> Unit) {
    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(XColors.Bg, XColors.PanelHeader, XColors.Shell))))
        WideBackdrop(item.backdrop, "hero-backdrop-${item.favoriteKey}", "hero-backdrop-loaded-${item.favoriteKey}",
            "hero-backdrop-rejected-${item.favoriteKey}", Modifier.fillMaxSize())
        Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(
            0f to XColors.Bg.copy(alpha = .97f), .45f to XColors.Bg.copy(alpha = .82f), 1f to Color.Transparent)))
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(
            0f to XColors.Bg.copy(alpha = .10f), .65f to Color.Transparent, 1f to XColors.Bg.copy(alpha = .8f))))
        // Text and buttons occupy disjoint regions: long titles/plots may ellipsize, but
        // never measure over, cover or clip the two actions on short landscape screens.
        Column(Modifier.align(Alignment.BottomStart).fillMaxWidth(if (compact) .90f else .60f)
            .fillMaxHeight().padding(start = if (compact) 16.dp else 28.dp, end = 12.dp, top = 10.dp, bottom = 70.dp),
            verticalArrangement = Arrangement.Bottom) {
            Text("FILME  ·  LANÇAMENTOS", color = XColors.TextDim,
                fontSize = 11.sp, letterSpacing = 1.1.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
            Spacer(Modifier.height(5.dp))
            Text(item.title, color = XColors.Text, fontSize = if (compact) 27.sp else 40.sp,
                lineHeight = if (compact) 31.sp else 44.sp, fontWeight = FontWeight.Bold,
                maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (item.rating > 0) HeroPill("★ ${String.format(Locale.US, "%.1f", item.rating)}", XColors.Gold)
                if (item.genre.isNotBlank()) HeroPill(item.genre, XColors.Text)
                if (item.year.isNotBlank()) HeroPill(item.year, XColors.Text)
            }
            if (item.plot.isNotBlank()) Text(item.plot, color = XColors.TextDim, fontSize = 13.sp,
                lineHeight = 19.sp, maxLines = if (compact) 1 else 2, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 8.dp))
            if (item.progress > 0) Box(Modifier.padding(top = 8.dp).width(170.dp).height(3.dp).background(Color(0x4DFFFFFF))) {
                Box(Modifier.fillMaxHeight().fillMaxWidth(item.progress.coerceIn(0f, 1f)).background(XColors.Accent))
            }
        }
        Row(Modifier.align(Alignment.BottomStart).fillMaxWidth(if (compact) .9f else .6f)
            .padding(start = if (compact) 16.dp else 28.dp, end = 12.dp, bottom = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            GlowButton(if (loading) "Carregando…" else "Assistir", Icons.Filled.PlayArrow,
                watchModifier.weight(1f, fill = false).testTag("hero-watch-${item.favoriteKey}"), onClick = { if (!loading) onWatch() })
            GlowButton("Detalhes", Icons.Filled.Info, detailsModifier.weight(1f, fill = false).testTag("hero-details-${item.favoriteKey}"),
                ringAlways = false, onClick = onSelect)
        }
    }
}

@Composable
private fun HeroPill(text: String, color: Color) {
    Text(text, color = color, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
        modifier = Modifier.widthIn(max = 150.dp).clip(RoundedCornerShape(50)).background(Color(0x30FFFFFF))
            .padding(horizontal = 9.dp, vertical = 4.dp))
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LiveCard(ch: Channel, prog: List<Programme>?, now: Long, time24Hour: Boolean, onLongClick: () -> Unit, onClick: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    val (cur, _) = prog.nowNext(now)
    val pct = cur?.let { ((now - it.start).toFloat() / (it.stop - it.start).coerceAtLeast(1)).coerceIn(0f, 1f) } ?: 0f
    Column(Modifier.width(280.dp).focusGlow(shape).clip(shape).background(XColors.Panel)
        .combinedClickable(onClick = onClick, onLongClick = onLongClick).padding(horizontal = 12.dp, vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).clip(RoundedCornerShape(8.dp)).background(XColors.PanelHeader), contentAlignment = Alignment.Center) {
                if (!ch.logo.isNullOrBlank()) AsyncImage(ch.logo, null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize().padding(4.dp))
                else Text(initials(ch.name), color = XColors.BlueSoft, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
            Column(Modifier.padding(start = 10.dp).weight(1f)) {
                Text(ch.name, color = XColors.Text, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(cur?.title ?: "Sem programação", color = XColors.Body, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("● AO VIVO", color = XColors.Live, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(8.dp))
            Box(Modifier.weight(1f).height(3.dp).background(Color(0x33888888))) {
                Box(Modifier.fillMaxHeight().fillMaxWidth(pct).background(XColors.Live))
            }
            if (cur != null) Text("  ${fmtClock(cur.start, time24Hour)} – ${fmtClock(cur.stop, time24Hour)}", color = XColors.Body, fontSize = 10.sp)
        }
    }
}
