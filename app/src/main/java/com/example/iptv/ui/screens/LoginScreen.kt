package com.example.iptv.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.iptv.ui.components.TopographicBackground
import com.example.iptv.ui.theme.*
import com.example.iptv.viewmodel.AuthViewModel

/**
 * Tela de Login com Glassmorphism Avançado e Textura Topográfica Damasco.
 * O botão de login possui exatamente o mesmo tamanho (largura e altura) das caixas de texto.
 */
@Composable
fun LoginScreen(
    viewModel: AuthViewModel,
    onLoginSuccess: () -> Unit
) {
    val serverUrl by viewModel.serverUrl.collectAsState()
    val username by viewModel.username.collectAsState()
    val password by viewModel.password.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()

    var isPasswordVisible by remember { mutableStateOf(false) }

    TopographicBackground {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            // Cartão Central Glassmorphism (Largura 440dp para TV e Tablet/Mobile)
            Box(
                modifier = Modifier
                    .width(440.dp)
                    .shadow(32.dp, RoundedCornerShape(32.dp), spotColor = Color(0x66FF574D))
                    .clip(RoundedCornerShape(32.dp))
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color(0xCC13151C),
                                Color(0xE60E1015)
                            )
                        )
                    )
                    .border(
                        width = 1.2.dp,
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color(0x4DFFFFFF),
                                Color(0x14FFFFFF)
                            )
                        ),
                        shape = RoundedCornerShape(32.dp)
                    )
                    .padding(horizontal = 36.dp, vertical = 40.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Cabeçalho
                    Text(
                        text = "Bem-vindo de volta",
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Conecte-se à sua conta Xtream",
                        fontSize = 14.sp,
                        color = Color.White.copy(alpha = 0.55f)
                    )

                    Spacer(modifier = Modifier.height(28.dp))

                    // Mensagem de Erro / Validação
                    AnimatedVisibility(visible = errorMessage != null) {
                        errorMessage?.let { msg ->
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 16.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color(0x33FF3B30))
                                    .border(1.dp, Color(0x80FF3B30), RoundedCornerShape(12.dp))
                                    .padding(horizontal = 14.dp, vertical = 10.dp)
                            ) {
                                Text(
                                    text = msg,
                                    color = Color(0xFFFF8B8B),
                                    fontSize = 13.sp
                                )
                            }
                        }
                    }

                    // Campo 1: Servidor Xtream
                    XtreamInputField(
                        value = serverUrl,
                        onValueChange = viewModel::updateServer,
                        placeholder = "Servidor Xtream (ex: http://servidor.com:8080)",
                        imeAction = ImeAction.Next
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // Campo 2: Usuário
                    XtreamInputField(
                        value = username,
                        onValueChange = viewModel::updateUsername,
                        placeholder = "Usuário",
                        imeAction = ImeAction.Next
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // Campo 3: Senha
                    XtreamInputField(
                        value = password,
                        onValueChange = viewModel::updatePassword,
                        placeholder = "Senha",
                        isPassword = true,
                        isPasswordVisible = isPasswordVisible,
                        onTogglePasswordVisibility = { isPasswordVisible = !isPasswordVisible },
                        imeAction = ImeAction.Done,
                        onDone = { viewModel.login(onLoginSuccess) }
                    )

                    Spacer(modifier = Modifier.height(22.dp))

                    // Botão de Login: EXATAMENTE DO MESMO TAMANHO DAS CAIXAS DE TEXTO (fillMaxWidth, altura 56dp, formato pill 24dp)
                    LoginActionFullWidthButton(
                        isLoading = isLoading,
                        onClick = { viewModel.login(onLoginSuccess) }
                    )
                }
            }
        }
    }
}

/**
 * Caixa de texto padronizada em formato de pílula glassmorphism escura (altura 56dp).
 */
@Composable
private fun XtreamInputField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    isPassword: Boolean = false,
    isPasswordVisible: Boolean = false,
    onTogglePasswordVisibility: (() -> Unit)? = null,
    imeAction: ImeAction = ImeAction.Default,
    onDone: (() -> Unit)? = null
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    val borderColor = if (isFocused) AccentCoral else Color.White.copy(alpha = 0.14f)
    val containerBg = if (isFocused) Color(0x40252835) else Color(0x33181B24)

    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(containerBg),
        placeholder = {
            Text(
                text = placeholder,
                color = Color.White.copy(alpha = 0.38f),
                fontSize = 14.sp
            )
        },
        singleLine = true,
        shape = RoundedCornerShape(24.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = Color.White,
            unfocusedTextColor = Color.White,
            focusedBorderColor = borderColor,
            unfocusedBorderColor = Color.White.copy(alpha = 0.12f),
            cursorColor = AccentCoral
        ),
        visualTransformation = if (isPassword && !isPasswordVisible) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(
            keyboardType = if (isPassword) KeyboardType.Password else KeyboardType.Text,
            imeAction = imeAction
        ),
        keyboardActions = KeyboardActions(onDone = { onDone?.invoke() }),
        trailingIcon = if (isPassword && onTogglePasswordVisibility != null) {
            {
                IconButton(onClick = onTogglePasswordVisibility) {
                    Icon(
                        imageVector = if (isPasswordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                        contentDescription = "Alternar Visibilidade",
                        tint = Color.White.copy(alpha = 0.45f)
                    )
                }
            }
        } else null,
        interactionSource = interactionSource
    )
}

/**
 * Botão com exatamente o mesmo tamanho (largura total e altura de 56dp) e raio de canto (24dp) das caixas de texto.
 */
@Composable
private fun LoginActionFullWidthButton(
    isLoading: Boolean,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.025f else 1.0f,
        label = "loginButtonScale"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .scale(scale)
            .shadow(
                elevation = if (isFocused) 16.dp else 8.dp,
                shape = RoundedCornerShape(24.dp),
                spotColor = AccentCoral
            )
            .clip(RoundedCornerShape(24.dp))
            .background(
                brush = Brush.horizontalGradient(
                    colors = listOf(
                        Color(0xFFFF5247),
                        Color(0xFFFF6D55)
                    )
                )
            )
            .border(
                width = if (isFocused) 2.dp else 1.dp,
                color = if (isFocused) Color.White else Color.Transparent,
                shape = RoundedCornerShape(24.dp)
            )
            .focusable(interactionSource = interactionSource)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(24.dp),
                color = Color.White,
                strokeWidth = 2.5.dp
            )
        } else {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "Entrar",
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.width(10.dp))
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = "Entrar",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}
