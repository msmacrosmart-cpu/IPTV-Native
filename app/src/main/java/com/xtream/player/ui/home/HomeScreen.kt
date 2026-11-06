package com.xtream.player.ui.home

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.focus.onFocusChanged
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
               favorites: List<String>, heroDetails: Map<String, VodDetails>, loadingHeroKey: String?,
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
    val candidates = remember(content.hero, favoriteVod, continuing, matching, query) {
        (sequenceOf(content.hero).filterNotNull() + favoriteVod.asSequence() + continuing.asSequence() + matching.asSequence())
            .filter { it.title.contains(query, true) }.distinctBy { it.favoriteKey }.take(6).toList()
    }
    LaunchedEffect(candidates.map { it.favoriteKey }) { onPrepareHeroes(candidates) }
    val slides = remember(candidates, heroDetails) { candidates.map { it.withHeroDetails(heroDetails[it.favoriteKey]) } }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val compact = maxWidth < 800.dp || maxHeight < 400.dp
        val gutter = if (compact) 16.dp else 28.dp
        // Match the catalog posters on Home too, leaving more titles visible at once.
        val cardWidth = catalogCardWidth(maxWidth * CatalogGridFraction)
        val heroHeight = (maxHeight * .62f).coerceIn(if (compact) 240.dp else 320.dp, 520.dp)
        LazyColumn(Modifier.fillMaxSize().testTag("home-list"), contentPadding = PaddingValues(bottom = 36.dp),
            verticalArrangement = Arrangement.spacedBy(if (compact) 24.dp else 30.dp)) {
            if (slides.isNotEmpty()) item(key = "hero") {
                HeroCarousel(slides, heroHeight, compact, loadingHeroKey, onSelectItem, onWatchItem)
            }
            else item { Text(if (query.isBlank()) "Sua biblioteca está vazia." else "Nenhum resultado encontrado.",
                color = XColors.TextDim, modifier = Modifier.padding(gutter)) }
            item(key = "continue") {
                PosterRail("Continuar assistindo", continuing, gutter, cardWidth, favorites, onSelectItem, onToggleFav,
                    "Os filmes e episódios que você começar aparecerão aqui.")
            }
            item(key = "live-favs") {
                Column {
                    RailHeader("Canais favoritos ao vivo", favoriteChannels.size, gutter)
                    if (favoriteChannels.isEmpty()) EmptyRail("Adicione canais aos favoritos na TV ao vivo.", gutter)
                    else LazyRow(contentPadding = PaddingValues(start = gutter, end = gutter, top = 10.dp, bottom = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(favoriteChannels, key = { it.id }) { ch ->
                            LiveCard(ch, epg[ch.epgId], now, onLongClick = { onToggleFav("ch:${ch.id}") }) { onPlayChannel(ch) }
                        }
                    }
                }
            }
            item(key = "vod-favs") {
                PosterRail("Filmes e séries favoritos", favoriteVod, gutter, cardWidth, favorites, onSelectItem, onToggleFav,
                    "Marque filmes ou séries como favoritos para encontrá-los aqui.")
            }
            if (matching.isNotEmpty()) item(key = "suggested") {
                PosterRail("Explore sua biblioteca", matching.take(20), gutter, cardWidth, favorites, onSelectItem, onToggleFav, "")
            }
        }
    }
}

@Composable
private fun PosterRail(title: String, items: List<VodItem>, gutter: androidx.compose.ui.unit.Dp,
                       cardWidth: androidx.compose.ui.unit.Dp, favorites: List<String>, onSelect: (VodItem) -> Unit, onToggleFav: (String) -> Unit, empty: String) {
    Column {
        RailHeader(title, items.size, gutter)
        if (items.isEmpty()) EmptyRail(empty, gutter)
        else LazyRow(contentPadding = PaddingValues(start = gutter, end = gutter, top = 10.dp, bottom = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(CatalogGridGap)) {
            items(items, key = { it.favoriteKey }) { item ->
                PosterCard(item, width = cardWidth, onLongClick = { onToggleFav(item.favoriteKey) },
                    onClick = { onSelect(item) }, isFavorite = item.favoriteKey in favorites)
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HeroCarousel(slides: List<VodItem>, height: androidx.compose.ui.unit.Dp, compact: Boolean,
                         loadingHeroKey: String?, onSelect: (VodItem) -> Unit, onWatch: (VodItem) -> Unit) {
    val pager = rememberPagerState(pageCount = { slides.size })
    val scope = rememberCoroutineScope()
    var hasFocus by remember { mutableStateOf(false) }
    LaunchedEffect(pager, slides.size, hasFocus, loadingHeroKey) {
        while (slides.size > 1) {
            delay(8_000)
            if (!hasFocus && loadingHeroKey == null && !pager.isScrollInProgress) pager.animateScrollToPage((pager.currentPage + 1) % slides.size)
        }
    }
    Box(Modifier.fillMaxWidth().height(height).onFocusChanged { hasFocus = it.hasFocus }.focusGroup()) {
        HorizontalPager(state = pager, key = { slides[it].favoriteKey }, modifier = Modifier.fillMaxSize()) { index ->
            val item = slides[index]
            HeroSlide(item, compact, loadingHeroKey == item.favoriteKey,
                onSelect = { onSelect(item) }, onWatch = { onWatch(item) })
        }
        if (slides.size > 1) Row(Modifier.align(Alignment.BottomEnd).padding(end = if (compact) 8.dp else 24.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically) {
            slides.forEachIndexed { index, slide ->
                val shape = RoundedCornerShape(50)
                Box(Modifier.size(34.dp).focusGlow(shape, zoom = 1f).clip(shape)
                    .semantics { contentDescription = "Destaque ${index + 1}: ${slide.title}" }
                    .combinedClickable { scope.launch { pager.animateScrollToPage(index) } }, contentAlignment = Alignment.Center) {
                    Box(Modifier.width(if (pager.currentPage == index) 18.dp else 6.dp).height(6.dp).clip(shape)
                        .background(if (pager.currentPage == index) XColors.Text else XColors.TextDim.copy(alpha = .5f)))
                }
            }
        }
    }
}

@Composable
private fun HeroSlide(item: VodItem, compact: Boolean, loading: Boolean, onSelect: () -> Unit, onWatch: () -> Unit) {
    var backdropFailed by remember(item.backdrop) { mutableStateOf(false) }
    val backdrop = item.backdrop?.takeIf { it.isNotBlank() && !backdropFailed }
    val image = backdrop ?: item.poster?.takeIf { it.isNotBlank() }
    var imageLoaded by remember(image) { mutableStateOf(false) }
    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(XColors.Bg, XColors.PanelHeader, XColors.Shell))))
        if (image != null) {
            // A wide backdrop fills the hero. If the provider only has a poster, keep it sharp
            // and uncropped on the right instead of stretching and blurring it across the screen.
            val imageModifier = if (backdrop != null) Modifier.fillMaxSize() else Modifier.align(Alignment.CenterEnd)
                .fillMaxWidth(if (compact) .48f else .52f).fillMaxHeight().padding(8.dp)
            val imageTag = "hero-${if (backdrop != null) "backdrop" else "poster"}-${if (imageLoaded) "loaded-" else ""}${item.favoriteKey}"
            AsyncImage(image, null, contentScale = if (backdrop != null) ContentScale.Crop else ContentScale.Fit,
                modifier = imageModifier.testTag(imageTag), onSuccess = { imageLoaded = true },
                onError = { if (backdrop != null) backdropFailed = true })
        }
        Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(
            0f to XColors.Bg.copy(alpha = .97f), .45f to XColors.Bg.copy(alpha = .82f), 1f to Color.Transparent)))
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(
            0f to XColors.Bg.copy(alpha = .10f), .65f to Color.Transparent, 1f to XColors.Bg.copy(alpha = .8f))))
        Column(Modifier.align(Alignment.BottomStart).fillMaxWidth(if (compact) .88f else .52f)
            .padding(start = if (compact) 16.dp else 28.dp, bottom = if (compact) 42.dp else 38.dp)) {
            Text(if (item.isSeries) "SÉRIE  ·  DESTAQUE" else "FILME  ·  DESTAQUE", color = XColors.TextDim,
                fontSize = 11.sp, letterSpacing = 1.1.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(5.dp))
            Text(item.title, color = XColors.Text, fontSize = if (compact) 30.sp else 40.sp,
                lineHeight = if (compact) 34.sp else 44.sp, fontWeight = FontWeight.Bold,
                maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (item.rating > 0) HeroPill("★ ${String.format(Locale.US, "%.1f", item.rating)}", XColors.Gold)
                if (item.genre.isNotBlank()) HeroPill(item.genre, XColors.Text)
                if (item.year.isNotBlank()) HeroPill(item.year, XColors.Text)
            }
            if (item.plot.isNotBlank()) Text(item.plot, color = XColors.TextDim, fontSize = 13.sp,
                lineHeight = 19.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 10.dp))
            if (item.progress > 0) Box(Modifier.padding(top = 10.dp).width(170.dp).height(3.dp).background(Color(0x4DFFFFFF))) {
                Box(Modifier.fillMaxHeight().fillMaxWidth(item.progress.coerceIn(0f, 1f)).background(XColors.Accent))
            }
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                GlowButton(if (loading) "Carregando…" else "Assistir", Icons.Filled.PlayArrow,
                    Modifier.testTag("hero-watch-${item.favoriteKey}"), onClick = { if (!loading) onWatch() })
                GlowButton("Detalhes", Icons.Filled.Info, Modifier.testTag("hero-details-${item.favoriteKey}"),
                    ringAlways = false, onClick = onSelect)
            }
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
private fun LiveCard(ch: Channel, prog: List<Programme>?, now: Long, onLongClick: () -> Unit, onClick: () -> Unit) {
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
            Box(Modifier.weight(1f).height(3.dp).background(Color(0x334F5666))) {
                Box(Modifier.fillMaxHeight().fillMaxWidth(pct).background(XColors.Live))
            }
            if (cur != null) Text("  ${fmtClock(cur.start)} – ${fmtClock(cur.stop)}", color = XColors.Body, fontSize = 10.sp)
        }
    }
}
