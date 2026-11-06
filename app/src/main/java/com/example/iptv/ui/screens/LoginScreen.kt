package com.example.iptv.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Language
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.iptv.ui.components.AppLogo
import com.example.iptv.ui.components.GlassCard
import com.example.iptv.ui.components.PrimaryButton
import com.example.iptv.ui.components.TopographicBackground
import com.example.iptv.ui.theme.AccentCoral
import com.example.iptv.ui.theme.TextMuted
import com.example.iptv.ui.theme.TextPrimary
import com.example.iptv.ui.theme.TextSecondary
import com.example.iptv.viewmodel.AuthViewModel

/**
 * Xtream Codes login. The fields are bound to [AuthViewModel] state, so rotating the device or
 * opening the keyboard never loses what was typed, and the session is persisted on success.
 */
@Composable
fun LoginScreen(viewModel: AuthViewModel) {
    val serverUrl by viewModel.serverInput.collectAsState()
    val username by viewModel.usernameInput.collectAsState()
    val password by viewModel.passwordInput.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()

    var passwordVisible by remember { mutableStateOf(false) }

    TopographicBackground {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(20.dp),
            contentAlignment = Alignment.Center
        ) {
            GlassCard(
                modifier = Modifier
                    .widthIn(max = 470.dp)
                    .fillMaxWidth(0.94f)
                    .heightIn(max = 620.dp),
                shape = RoundedCornerShape(28.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 34.dp, vertical = 28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    AppLogo(markSize = 54)

                    Spacer(modifier = Modifier.height(22.dp))

                    Text(
                        text = "Bem-vindo de volta",
                        color = TextPrimary,
                        fontSize = 25.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Entre com os dados do seu painel Xtream Codes",
                        color = TextSecondary,
                        fontSize = 12.5.sp,
                        textAlign = TextAlign.Center
                    )

                    AnimatedVisibility(visible = errorMessage != null) {
                        Column {
                            Spacer(modifier = Modifier.height(16.dp))
                            errorMessage?.let { message ->
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(Color(0x33FF3B30), RoundedCornerShape(12.dp))
                                        .border(1.dp, Color(0x80FF3B30), RoundedCornerShape(12.dp))
                                        .padding(horizontal = 14.dp, vertical = 10.dp)
                                ) {
                                    Text(text = message, color = Color(0xFFFF9B93), fontSize = 12.sp)
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    LoginField(
                        value = serverUrl,
                        onValueChange = viewModel::updateServer,
                        placeholder = "http://servidor.com:8080",
                        icon = Icons.Default.Language,
                        keyboardType = KeyboardType.Uri,
                        imeAction = ImeAction.Next
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    LoginField(
                        value = username,
                        onValueChange = viewModel::updateUsername,
                        placeholder = "Usuário",
                        icon = Icons.Default.Person,
                        keyboardType = KeyboardType.Text,
                        imeAction = ImeAction.Next
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    LoginField(
                        value = password,
                        onValueChange = viewModel::updatePassword,
                        placeholder = "Senha",
                        icon = Icons.Default.Lock,
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Done,
                        isPassword = true,
                        passwordVisible = passwordVisible,
                        onToggleVisibility = { passwordVisible = !passwordVisible },
                        onDone = viewModel::login
                    )

                    Spacer(modifier = Modifier.height(22.dp))

                    // Same width (fillMaxWidth), height (56dp) and corner radius (24dp) as the fields.
                    PrimaryButton(
                        label = "Entrar  →",
                        onClick = viewModel::login,
                        modifier = Modifier.fillMaxWidth(),
                        loading = isLoading
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Aceita",
                            color = TextMuted,
                            fontSize = 10.5.sp
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "servidor:porta  •  player_api.php  •  get.php?username=…",
                            color = AccentCoral.copy(alpha = 0.8f),
                            fontSize = 10.5.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = "Sua sessão fica salva no dispositivo com criptografia AES (Android Keystore).",
                        color = TextMuted,
                        fontSize = 10.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

@Composable
private fun LoginField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    keyboardType: KeyboardType,
    imeAction: ImeAction,
    isPassword: Boolean = false,
    passwordVisible: Boolean = false,
    onToggleVisibility: (() -> Unit)? = null,
    onDone: (() -> Unit)? = null
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
        placeholder = {
            Text(text = placeholder, color = TextMuted, fontSize = 13.5.sp)
        },
        leadingIcon = {
            Icon(imageVector = icon, contentDescription = null, tint = TextMuted, modifier = Modifier.size(18.dp))
        },
        trailingIcon = if (isPassword && onToggleVisibility != null) {
            {
                IconButton(onClick = onToggleVisibility) {
                    Icon(
                        imageVector = if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                        contentDescription = "Mostrar senha",
                        tint = TextMuted,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        } else {
            null
        },
        singleLine = true,
        shape = RoundedCornerShape(18.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = AccentCoral,
            unfocusedBorderColor = Color.White.copy(alpha = 0.12f),
            focusedTextColor = TextPrimary,
            unfocusedTextColor = TextPrimary,
            cursorColor = AccentCoral,
            focusedContainerColor = Color(0x33202430),
            unfocusedContainerColor = Color(0x1F171A23)
        ),
        visualTransformation = if (isPassword && !passwordVisible) {
            PasswordVisualTransformation()
        } else {
            VisualTransformation.None
        },
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = imeAction),
        keyboardActions = KeyboardActions(onDone = { onDone?.invoke() })
    )
}
