package com.example.iptv.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.iptv.ui.components.GlassCard
import com.example.iptv.ui.components.TopographicBackground
import com.example.iptv.ui.theme.*
import com.example.iptv.viewmodel.MainTvViewModel

/**
 * Dashboard Oficial Atualizado (Black Harbor) conforme nova imagem anexada:
 * - Seção Hero: 'Black Harbor' (MOVIE • FAVORITE | ★ 8.2 | Maritime Crime Thriller | 2026)
 * - Botão opaco estilizado '▶ Details'
 * - Transição 100% limpa e sem linhas neon divisórias horizontais
 * - Prateleiras: 'Continue Watching', 'Live now on your favorites' e 'Séries em Destaque'
 */
@Composable
fun DashboardScreen(
    viewModel: MainTvViewModel,
    onOpenHeroDetails: () -> Unit,
    onPlayContent: (url: String, title: String) -> Unit
) {
    TopographicBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 40.dp)
        ) {
            // Barra de Busca Superior do Dashboard
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "release-demo",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White.copy(alpha = 0.9f)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "M3U • file • 6 channels",
                        fontSize = 11.sp,
                        color = Color.White.copy(alpha = 0.45f)
                    )
                }

                // Campo de Busca Rápida no Topo
                Box(
                    modifier = Modifier
                        .width(380.dp)
                        .height(38.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0x331E202B))
                        .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 12.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Search,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.4f),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Search in this playlist...",
                            color = Color.White.copy(alpha = 0.4f),
                            fontSize = 12.sp
                        )
                    }
                }
            }

            // ==========================================
            // SEÇÃO HERO (Black Harbor)
            // ==========================================
            GlassCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(340.dp),
                shape = RoundedCornerShape(26.dp),
                isFocusable = false
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    // Imagem de Fundo do Hero (Black Harbor)
                    AsyncImage(
                        model = "https://images.unsplash.com/photo-1507525428034-b723cf961d3e?w=1600",
                        contentDescription = "Black Harbor",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )

                    // Gradiente de escurecimento suave para contraste
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.horizontalGradient(
                                    colors = listOf(
                                        Color(0xFA0B0C11),
                                        Color(0xE60B0C11),
                                        Color(0x730B0C11),
                                        Color(0x1A0B0C11)
                                    )
                                )
                            )
                    )

                    // Informações do Hero
                    Column(
                        modifier = Modifier
                            .fillMaxHeight()
                            .width(520.dp)
                            .padding(32.dp),
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "MOVIE • FAVORITE",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White.copy(alpha = 0.55f),
                            letterSpacing = 1.2.sp
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        Text(
                            text = "Black Harbor",
                            fontSize = 38.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Star, contentDescription = null, tint = RatingGold, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "8.2",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "|  Maritime Crime Thriller  |  2026",
                                fontSize = 13.sp,
                                color = Color.White.copy(alpha = 0.7f)
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = "Toda maré traz um segredo. Quando um cargueiro não registrado chega a um porto sob forte neblina...",
                            fontSize = 13.sp,
                            color = Color.White.copy(alpha = 0.78f),
                            lineHeight = 18.sp,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis
                        )

                        Spacer(modifier = Modifier.height(20.dp))

                        HeroActionButton(
                            title = "Details",
                            onClick = onOpenHeroDetails
                        )
                    }
                }
            }

            // Transição totalmente limpa sem riscos ou linha divisória neon
            Spacer(modifier = Modifier.height(26.dp))

            // ==========================================
            // PRATELEIRA 1: CONTINUE WATCHING
            // ==========================================
            ShelfSection(
                title = "Continue Watching",
                items = listOf(
                    ShelfCardItem("Summer Static", "", "https://images.unsplash.com/photo-1534447677768-be436bb09401?w=500", progress = 0.65f),
                    ShelfCardItem("House of Tides", "", "https://images.unsplash.com/photo-1518709268805-4e9042af9f23?w=500", progress = 0.40f),
                    ShelfCardItem("Field Notes", "", "https://images.unsplash.com/photo-1440404653325-ab127d49abc1?w=500", progress = 0.85f),
                    ShelfCardItem("White Room Six", "", "https://images.unsplash.com/photo-1497366216548-37526070297c?w=500", progress = 0.20f),
                    ShelfCardItem("Open Late", "", "https://images.unsplash.com/photo-1486406146926-c627a92ad1ab?w=500", progress = 0.50f),
                    ShelfCardItem("Skyline Sentinels", "", "https://images.unsplash.com/photo-1486406146926-c627a92ad1ab?w=500", progress = 0.70f),
                    ShelfCardItem("The Paper Astronaut", "", "https://images.unsplash.com/photo-1509198397868-475647b2a1e5?w=500", progress = 0.90f, isHighlighted = true),
                    ShelfCardItem("Black Harbor", "", "https://images.unsplash.com/photo-1507525428034-b723cf961d3e?w=500", progress = 0.15f)
                ),
                onItemClick = { item -> onPlayContent("http://demo.stream", item.title) }
            )

            Spacer(modifier = Modifier.height(26.dp))

            // ==========================================
            // PRATELEIRA 2: LIVE NOW ON YOUR FAVORITES
            // ==========================================
            ShelfSection(
                title = "Live now on your favorites",
                items = listOf(
                    ShelfCardItem("Aurora News", "Global Window", "https://images.unsplash.com/photo-1585829365295-ab7cd400c167?w=500", isLive = true, badgePrefix = "AN"),
                    ShelfCardItem("Fieldside Sports", "Final Whistle", "https://images.unsplash.com/photo-1508098682722-e99c43a406b2?w=500", isLive = true, badgePrefix = "FS", isHighlighted = true),
                    ShelfCardItem("Horizon Kids", "Story Lantern", "https://images.unsplash.com/photo-1534447677768-be436bb09401?w=500", isLive = true, badgePrefix = "HK"),
                    ShelfCardItem("Atlas Docs", "Wide Angle", "https://images.unsplash.com/photo-1518709268805-4e9042af9f23?w=500", isLive = true, badgePrefix = "AD"),
                    ShelfCardItem("Night Music", "Ambient Office", "https://images.unsplash.com/photo-1511671782779-c97d3d27a1d4?w=500", isLive = true, badgePrefix = "AM")
                ),
                onItemClick = { item -> onPlayContent("http://demo.stream", item.title) }
            )

            Spacer(modifier = Modifier.height(26.dp))

            // ==========================================
            // PRATELEIRA 3: SÉRIES EM DESTAQUE
            // ==========================================
            ShelfSection(
                title = "Séries em Destaque",
                items = listOf(
                    ShelfCardItem("Operação", "★ 8.2", "https://images.unsplash.com/photo-1509198397868-475647b2a1e5?w=500"),
                    ShelfCardItem("Guerra Fria", "★ 8.5", "https://images.unsplash.com/photo-1518709268805-4e9042af9f23?w=500"),
                    ShelfCardItem("Último Trem", "★ 8.0", "https://images.unsplash.com/photo-1514565131-fce0801e5785?w=500"),
                    ShelfCardItem("The Bear", "★ 8.6", "https://images.unsplash.com/photo-1555396273-367ea4eb4db5?w=500"),
                    ShelfCardItem("Shōgun", "★ 8.8", "https://images.unsplash.com/photo-1534447677768-be436bb09401?w=500")
                ),
                onItemClick = { item -> onPlayContent("http://demo.stream", item.title) }
            )
        }
    }
}

data class ShelfCardItem(
    val title: String,
    val subtitle: String = "",
    val imageUrl: String,
    val progress: Float? = null,
    val isLive: Boolean = false,
    val badgePrefix: String? = null,
    val isHighlighted: Boolean = false
)

@Composable
private fun ShelfSection(
    title: String,
    items: List<ShelfCardItem>,
    onItemClick: (ShelfCardItem) -> Unit
) {
    Column {
        Text(
            text = title,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items.forEach { item ->
                ShelfCard(item = item, onClick = { onItemClick(item) })
            }
        }
    }
}

@Composable
private fun ShelfCard(
    item: ShelfCardItem,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    val scale = if (isFocused || item.isHighlighted) 1.05f else 1.0f
    val borderColor = when {
        isFocused -> AccentCoral
        item.isHighlighted -> AccentCoral.copy(alpha = 0.9f)
        else -> Color.White.copy(alpha = 0.08f)
    }

    Column(
        modifier = Modifier
            .scale(scale)
            .width(220.dp)
            .focusable(interactionSource = interactionSource)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(115.dp)
                .shadow(
                    elevation = if (isFocused || item.isHighlighted) 14.dp else 4.dp,
                    shape = RoundedCornerShape(14.dp),
                    spotColor = AccentCoral
                )
                .clip(RoundedCornerShape(14.dp))
                .background(Color(0xFF141720))
                .border(
                    width = if (isFocused || item.isHighlighted) 2.dp else 1.dp,
                    color = borderColor,
                    shape = RoundedCornerShape(14.dp)
                )
        ) {
            AsyncImage(
                model = item.imageUrl,
                contentDescription = item.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )

            // Gradiente sombreado
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color(0xCC000000)),
                            startY = 50f
                        )
                    )
            )

            // Badge Prefixo (ex: AN, FS, HK) para canais ao vivo
            if (item.badgePrefix != null) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp)
                        .size(26.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0x80000000))
                        .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(6.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = item.badgePrefix,
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Título & Subtítulo na base
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(horizontal = 10.dp, vertical = 8.dp)
            ) {
                Text(
                    text = item.title,
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (item.subtitle.isNotBlank()) {
                    Text(
                        text = item.subtitle,
                        color = Color.White.copy(alpha = 0.55f),
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Barra de Progresso
            item.progress?.let { p ->
                LinearProgressIndicator(
                    progress = { p },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .align(Alignment.BottomCenter),
                    color = AccentCoral,
                    trackColor = Color(0x33FFFFFF)
                )
            }
        }
    }
}

@Composable
private fun HeroActionButton(
    title: String,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    val scale = if (isFocused) 1.04f else 1.0f

    Box(
        modifier = Modifier
            .scale(scale)
            .width(140.dp)
            .height(44.dp)
            .shadow(12.dp, RoundedCornerShape(22.dp), spotColor = AccentCoral)
            .clip(RoundedCornerShape(22.dp))
            .background(if (isFocused) Color(0xFFE85045) else Color(0x331F222E))
            .border(
                1.5.dp,
                if (isFocused) Color.White else AccentCoral,
                RoundedCornerShape(22.dp)
            )
            .focusable(interactionSource = interactionSource)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Default.PlayArrow,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = title,
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
