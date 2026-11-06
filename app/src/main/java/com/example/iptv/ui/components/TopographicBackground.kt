package com.example.iptv.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import com.example.iptv.R

/**
 * Plano de fundo cinematográfico padrão para carregamento, tela inicial e login:
 * Utiliza estritamente a arte anexada oficial (@drawable/bg_app_main) como base,
 * com overlay dark e acabamento Glassmorphism translúcido, SEM nenhuma linha neon divisória.
 */
@Composable
fun TopographicBackground(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF07080B))
    ) {
        // Imagem Oficial de Fundo de Carregamento e Login
        Image(
            painter = painterResource(id = R.drawable.bg_app_main),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )

        // Overlay em gradiente escuro de Glassmorphism (sem linhas neon horizontais ou riscos)
        // Mantém alto contraste e legibilidade impecável para os componentes
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0xCC07080B),
                            Color(0xD9090A0E),
                            Color(0xF207080B)
                        )
                    )
                )
        )

        // Conteúdo da interface nativa em Compose
        content()
    }
}
