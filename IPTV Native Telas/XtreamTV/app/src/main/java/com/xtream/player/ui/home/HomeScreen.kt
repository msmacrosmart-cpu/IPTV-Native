package com.xtream.player.ui.home

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.xtream.player.data.*
import com.xtream.player.ui.components.*
import com.xtream.player.ui.theme.XColors
import com.xtream.player.util.initials

@Composable
fun HomeScreen(
    content: ContentState,
    epg: Map<String, List<Programme>>,
    query: String,
    onPlayItem: (VodItem) -> Unit,
    onPlayChannel: (Channel) -> Unit,
    onToggleFav: (String) -> Unit,
) {
    val now = System.currentTimeMillis()
    val all = (content.movies + content.series)
    val cont = all.filter { it.progress > 0f && it.title.contains(query, true) }
    val featured = content.series.filter { it.title.contains(query, true) }.take(12)
    val live = content.channels.filter { it.name.contains(query, true) }.take(12)

    LazyColumn(contentPadding = PaddingValues(start = 26.dp, end = 26.dp, top = 12.dp, bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        content.hero?.let { h -> item { Hero(h, onPlayItem) } }
        if (cont.isNotEmpty()) item {
            SectionTitle("Continue Watching")
            LazyRow(contentPadding = PaddingValues(vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                items(cont, key = { it.id }) { LandscapeCard(it, 140.dp, onLongClick = { onToggleFav("vod:${it.id}") }) { onPlayItem(it) } }
            }
        }
        if (live.isNotEmpty()) item {
            SectionTitle("Live now on your favorites")
            LazyRow(contentPadding = PaddingValues(vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                items(live, key = { it.id }) { LiveCard(it, epg[it.epgId], now) { onPlayChannel(it) } }
            }
        }
        if (featured.isNotEmpty()) item {
            SectionTitle("Séries em Destaque")
            LazyRow(contentPadding = PaddingValues(vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                items(featured, key = { it.id }) { LandscapeCard(it, 150.dp) { onPlayItem(it) } }
            }
        }
    }
}

@Composable
private fun Hero(h: VodItem, onPlay: (VodItem) -> Unit) {
    Box(Modifier.fillMaxWidth().height(290.dp)) {
        Box(
            Modifier.align(Alignment.CenterEnd).fillMaxWidth(0.58f).fillMaxHeight()
                .background(Brush.horizontalGradient(listOf(Color.Transparent, Color(h.c1).copy(alpha = 0.45f)))),
        ) {
            val img = h.backdrop ?: h.poster
            if (img != null) AsyncImage(img, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(listOf(XColors.Bg.copy(alpha = 0.9f), Color.Transparent))))
        }
        Column(Modifier.fillMaxWidth(0.52f).align(Alignment.CenterStart)) {
            Text("MOVIE  •  FAVORITE", color = XColors.TextDim, fontSize = 15.sp, letterSpacing = 0.5.sp)
            Text(h.title, color = XColors.Text, fontSize = 56.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Star, null, tint = XColors.Gold, modifier = Modifier.size(18.dp))
                Text(" ${h.rating}", color = XColors.Text, fontSize = 17.sp)
                Text("   |   ${h.genre}   |   ${h.year}", color = XColors.TextDim, fontSize = 17.sp)
            }
            Spacer(Modifier.height(10.dp))
            Text(h.plot.ifBlank { " " }, color = Color(0xFFD8D8DC), fontSize = 17.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 24.sp)
            Spacer(Modifier.height(18.dp))
            GlowButton("Details", Icons.Filled.PlayArrow) { onPlay(h) }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LiveCard(ch: Channel, prog: List<Programme>?, now: Long, onClick: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    val (cur, _) = prog.nowNext(now)
    val pct = cur?.let { ((now - it.start).toFloat() / (it.stop - it.start)).coerceIn(0f, 1f) } ?: 0f
    Column(
        Modifier.width(236.dp).focusGlow(shape).clip(shape).background(Color(0x14FFFFFF))
            .combinedClickable(onClick = onClick).padding(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(44.dp).clip(RoundedCornerShape(10.dp)).background(Brush.linearGradient(listOf(Color(0xFF5B3FD0), Color(0xFF2A1A66)))),
                contentAlignment = Alignment.Center,
            ) { Text(initials(ch.name), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp) }
            Column(Modifier.padding(start = 12.dp)) {
                Text(ch.name, color = XColors.Text, fontSize = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(cur?.title ?: "Sem guia", color = XColors.TextDim, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(6.dp).clip(RoundedCornerShape(50)).background(XColors.Live))
            Text(" LIVE", color = XColors.Live, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(8.dp))
            Box(Modifier.weight(1f).height(3.dp).background(Color(0x33FFFFFF))) {
                Box(Modifier.fillMaxHeight().fillMaxWidth(pct).background(Brush.horizontalGradient(listOf(XColors.Accent, XColors.Gold))))
            }
        }
    }
}
