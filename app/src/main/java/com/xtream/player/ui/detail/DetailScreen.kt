package com.xtream.player.ui.detail

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
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
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.xtream.player.data.AppViewModel
import com.xtream.player.data.VodItem
import com.xtream.player.ui.components.CategoryChip
import com.xtream.player.ui.components.GlowButton
import com.xtream.player.ui.components.FavoriteAction
import com.xtream.player.ui.components.IconAction
import com.xtream.player.ui.components.PosterArt
import com.xtream.player.ui.components.focusGlow
import com.xtream.player.ui.theme.XColors
import java.util.Locale
import kotlinx.coroutines.delay

@Composable
fun DetailScreen(item: VodItem, favorite: Boolean, selection: AppViewModel.EpisodeSelection?, loading: Boolean,
                 error: String?, onBack: () -> Unit, onFavorite: () -> Unit, onPlay: () -> Unit,
                 onEpisode: (AppViewModel.EpisodeChoice) -> Unit) {
    BackHandler(onBack = onBack)
    val choices = selection?.takeIf { it.item.favoriteKey == item.favoriteKey }?.choices.orEmpty()
    val seasons = remember(choices) { choices.map { it.episode.season }.distinct().sorted() }
    var season by rememberSaveable(item.favoriteKey) { mutableIntStateOf(0) }
    LaunchedEffect(seasons) { if (seasons.isNotEmpty() && season !in seasons) season = seasons.first() }
    val visible = choices.filter { it.episode.season == season }
    BoxWithConstraints(Modifier.fillMaxSize()) {
      val compact = maxWidth < 700.dp || maxHeight < 400.dp
      val heroHeight = if (maxHeight < 400.dp) maxHeight.coerceAtLeast(230.dp) else if (compact) 390.dp else 420.dp
      LazyColumn(Modifier.fillMaxSize().testTag("detail-list"), contentPadding = PaddingValues(bottom = 32.dp)) {
        item { DetailHero(item, favorite, !item.isSeries || choices.isNotEmpty(), loading, compact, heroHeight, onBack, onFavorite, onPlay) }
        if (item.isSeries) {
            item {
                Column(Modifier.fillMaxWidth().padding(horizontal = 26.dp, vertical = 12.dp)) {
                    Text("Episódios", color = XColors.Text, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                    if (loading) CircularProgressIndicator(Modifier.padding(top = 14.dp).size(24.dp), color = XColors.Accent, strokeWidth = 2.dp)
                    if (error != null) Text(error, color = XColors.TextDim, fontSize = 14.sp, modifier = Modifier.padding(top = 10.dp))
                }
            }
            if (seasons.isNotEmpty()) item {
                LazyRow(contentPadding = PaddingValues(start = 26.dp, end = 26.dp, bottom = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(seasons, key = { it }) { number -> CategoryChip("Temporada $number", season == number) { season = number } }
                }
            }
            items(visible, key = { it.episode.id }) { episode -> EpisodeRow(episode) { onEpisode(episode) } }
        }
      }
    }
}

@Composable
private fun DetailHero(item: VodItem, favorite: Boolean, canPlay: Boolean, loading: Boolean, compact: Boolean,
                       heroHeight: androidx.compose.ui.unit.Dp, onBack: () -> Unit, onFavorite: () -> Unit, onPlay: () -> Unit) {
    val playFocus = remember(item.favoriteKey) { FocusRequester() }
    val inputMode = LocalInputModeManager.current
    LaunchedEffect(item.favoriteKey, canPlay) {
        if (canPlay && inputMode.inputMode != InputMode.Touch) { delay(120); runCatching { playFocus.requestFocus() } }
    }
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth().height(heroHeight)) {
            Box(Modifier.fillMaxSize().background(XColors.PanelHeader))
            var backdropFailed by remember(item.backdrop) { mutableStateOf(false) }
            val backdrop = item.backdrop?.takeIf { it.isNotBlank() && !backdropFailed }
            val image = backdrop ?: item.poster?.takeIf { it.isNotBlank() }
            if (image != null) AsyncImage(image, null, contentScale = if (backdrop != null) ContentScale.Crop else ContentScale.Fit,
                modifier = if (backdrop != null) Modifier.fillMaxSize() else Modifier.align(Alignment.CenterEnd)
                    .fillMaxWidth(.5f).fillMaxHeight().padding(8.dp),
                onError = { if (backdrop != null) backdropFailed = true })
            Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(
                0f to XColors.Bg.copy(alpha = .96f), .5f to XColors.Bg.copy(alpha = .82f), 1f to XColors.Bg.copy(alpha = .22f))))
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to XColors.Bg.copy(alpha = .15f), .56f to Color.Transparent, 1f to XColors.Bg)))
            IconAction(Icons.Filled.ArrowBack, "Voltar aos itens", Modifier.align(Alignment.TopStart).padding(start = 12.dp, top = 10.dp), onBack)
            Row(Modifier.align(Alignment.BottomStart).fillMaxWidth(if (compact) 1f else .78f)
                .padding(horizontal = if (compact) 16.dp else 30.dp, vertical = 24.dp),
                verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(if (compact) 14.dp else 24.dp)) {
                PosterArt(item, Modifier.width(if (compact) 98.dp else 150.dp).aspectRatio(2f / 3f).clip(RoundedCornerShape(10.dp)))
                Column(Modifier.weight(1f)) {
                    Text(if (item.isSeries) "SÉRIE  ·  XTREAM" else "FILME  ·  XTREAM", color = XColors.TextDim,
                        fontSize = 11.sp, letterSpacing = 1.sp, fontWeight = FontWeight.SemiBold)
                    Text(item.title, color = XColors.Text, fontSize = if (compact) 25.sp else 38.sp, lineHeight = if (compact) 29.sp else 42.sp,
                        fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
                    Text(listOfNotNull(item.year.takeIf { it.isNotBlank() }, item.genre.takeIf { it.isNotBlank() },
                        if (item.rating > 0) "★ ${String.format(Locale.US, "%.1f", item.rating)}" else null).joinToString("   ·   "),
                        color = XColors.TextDim, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 8.dp))
                    if (item.plot.isNotBlank()) Text(item.plot, color = XColors.TextDim, fontSize = 13.sp, lineHeight = 19.sp,
                        maxLines = if (compact) 3 else 4, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 10.dp))
                    Row(Modifier.padding(top = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (canPlay) GlowButton(if (item.progress > 0f) "Continuar" else "Reproduzir", Icons.Filled.PlayArrow, Modifier.focusRequester(playFocus), onClick = onPlay)
                        else Text(if (loading) "Carregando episódios…" else "Sem episódios", color = XColors.TextDim, fontSize = 13.sp)
                        FavoriteAction(favorite, if (favorite) "Remover dos favoritos" else "Adicionar aos favoritos", onClick = onFavorite)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun EpisodeRow(choice: AppViewModel.EpisodeChoice, onClick: () -> Unit) {
    val episode = choice.episode
    val shape = RoundedCornerShape(8.dp)
    Row(Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 2.dp).focusGlow(shape, zoom = 1f)
        .clip(shape).combinedClickable(onClick = onClick).padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("${episode.number}", color = XColors.Body, fontSize = 13.sp, modifier = Modifier.width(30.dp))
        Box(Modifier.width(120.dp).aspectRatio(16f / 9f).clip(RoundedCornerShape(6.dp)).background(XColors.Panel)) {
            if (!episode.image.isNullOrBlank()) AsyncImage(episode.image, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            else Icon(Icons.Filled.PlayArrow, null, tint = XColors.TextDim, modifier = Modifier.align(Alignment.Center).size(28.dp))
        }
        Column(Modifier.padding(start = 14.dp).weight(1f)) {
            Text(choice.label, color = XColors.Text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            episode.durationLabel?.let { Text(it, color = XColors.Body, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp)) }
            episode.plot?.takeIf { it.isNotBlank() }?.let { Text(it, color = XColors.TextDim, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp)) }
        }
        Icon(Icons.Filled.PlayArrow, null, tint = XColors.TextDim, modifier = Modifier.size(24.dp))
    }
}
