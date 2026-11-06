package com.xtream.player.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.iptv.data.model.UserSettings
import com.xtream.player.data.ContentState
import com.xtream.player.ui.components.GlassPanel
import com.xtream.player.ui.components.GlowButton
import com.xtream.player.ui.components.PillField
import com.xtream.player.ui.components.focusGlow
import com.xtream.player.ui.theme.XColors

private enum class PinDialog { Create, Unlock, Change }
private data class LockedCategory(val key: String, val label: String)

@Composable
fun ParentalSettings(content: ContentState, settings: UserSettings, unlocked: Boolean, busy: Boolean, actions: SettingsActions) {
    var dialog by remember { mutableStateOf<PinDialog?>(null) }
    var confirmClear by remember { mutableStateOf(false) }
    val hasPin = settings.parentalHash.isNotBlank()
    if (confirmClear) ConfirmSettingsDialog("Desativar controle parental?", "O PIN e todos os bloqueios de categoria serão removidos.",
        onDismiss = { confirmClear = false }, onConfirm = { actions.clearPin(); confirmClear = false })
    dialog?.let { mode ->
        PinEntryDialog(mode, busy, onDismiss = { dialog = null }, onUnlock = actions.unlock, onSetPin = actions.setPin)
    }
    SettingsPanel("Controle parental", "Bloqueie categorias para este perfil. O bloqueio é aplicado à Home, buscas e TV.") {
        when {
            !hasPin -> GlowButton("Criar PIN", ringAlways = false) { dialog = PinDialog.Create }
            !unlocked -> GlowButton("Desbloquear controle parental", ringAlways = false) { dialog = PinDialog.Unlock }
            else -> {
                Text("Desbloqueado neste acesso. Escolha as categorias e depois bloqueie novamente.",
                    color = XColors.TextDim, fontSize = 13.sp)
                Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GlowButton("Bloquear agora", ringAlways = false, onClick = actions.lock)
                    GlowButton("Alterar PIN", ringAlways = false) { dialog = PinDialog.Change }
                }
                GlowButton("Desativar controle parental", ringAlways = false, modifier = Modifier.padding(top = 10.dp)) { confirmClear = true }
                val categories = remember(content.liveCategories, content.movieCategories, content.seriesCategories) {
                    content.liveCategories.map { LockedCategory("live:${it.id}", "TV · ${it.name}") } +
                        content.movieCategories.map { LockedCategory("vod:${it.id}", "Filmes · ${it.name}") } +
                        content.seriesCategories.map { LockedCategory("series:${it.id}", "Séries · ${it.name}") }
                }
                SettingLabel("Categorias bloqueadas")
                if (categories.isEmpty()) Text("Nenhuma categoria disponível.", color = XColors.Body, fontSize = 13.sp)
                else LazyColumn(Modifier.fillMaxWidth().heightIn(max = 260.dp).testTag("parental-categories")) {
                    items(categories, key = { it.key }) { category ->
                        val checked = category.key in settings.lockedCategories
                        Row(Modifier.fillMaxWidth().focusGlow(RoundedCornerShape(8.dp), zoom = 1f)
                            .toggleable(checked, role = Role.Checkbox, onValueChange = { actions.toggleCategory(category.key) })
                            .testTag("parental-${category.key}").padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = checked, onCheckedChange = null)
                            Text(category.label, color = XColors.Text, fontSize = 14.sp, modifier = Modifier.padding(start = 8.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PinEntryDialog(mode: PinDialog, busy: Boolean, onDismiss: () -> Unit,
                           onUnlock: (String, (String?) -> Unit) -> Unit,
                           onSetPin: (String, String, String, (String?) -> Unit) -> Unit) {
    var current by remember(mode) { mutableStateOf("") }
    var pin by remember(mode) { mutableStateOf("") }
    var confirmation by remember(mode) { mutableStateOf("") }
    var error by remember(mode) { mutableStateOf<String?>(null) }
    val submit = {
        if (!busy) {
            val done: (String?) -> Unit = { result -> if (result == null) onDismiss() else error = result }
            if (mode == PinDialog.Unlock) onUnlock(pin, done) else onSetPin(current, pin, confirmation, done)
        }
    }
    Dialog(onDismissRequest = { if (!busy) onDismiss() }) {
        BoxWithConstraints(Modifier.fillMaxWidth().widthIn(max = 500.dp)) {
            GlassPanel(Modifier.fillMaxWidth().heightIn(max = maxHeight * .92f), RoundedCornerShape(16.dp)) {
                Column(Modifier.verticalScroll(rememberScrollState()).padding(20.dp)) {
                    Text(when (mode) { PinDialog.Create -> "Criar PIN"; PinDialog.Unlock -> "Desbloquear"; PinDialog.Change -> "Alterar PIN" },
                        color = XColors.Text, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                    if (mode == PinDialog.Change) {
                        SettingLabel("PIN atual")
                        PillField(current, { current = it.filter(Char::isDigit).take(8) }, "PIN atual", Modifier.fillMaxWidth().testTag("parental-current-pin"),
                            password = true, keyboardType = KeyboardType.NumberPassword)
                    }
                    SettingLabel(if (mode == PinDialog.Unlock) "PIN" else "Novo PIN (4 a 8 números)")
                    PillField(pin, { pin = it.filter(Char::isDigit).take(8) }, "PIN", Modifier.fillMaxWidth().testTag("parental-pin"),
                        password = true, keyboardType = KeyboardType.NumberPassword,
                        imeAction = if (mode == PinDialog.Unlock) ImeAction.Done else ImeAction.Next, onDone = submit)
                    if (mode != PinDialog.Unlock) {
                        SettingLabel("Confirmar PIN")
                        PillField(confirmation, { confirmation = it.filter(Char::isDigit).take(8) }, "Confirmar PIN",
                            Modifier.fillMaxWidth().testTag("parental-confirm-pin"), password = true,
                            keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Done, onDone = submit)
                    }
                    if (error != null) Text(error!!, color = XColors.Live, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
                    Row(Modifier.padding(top = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        GlowButton("Cancelar", ringAlways = false) { if (!busy) onDismiss() }
                        GlowButton(if (mode == PinDialog.Unlock) "Desbloquear" else "Salvar PIN", modifier = Modifier.testTag("parental-submit"), onClick = submit)
                        if (busy) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp, color = XColors.Accent)
                    }
                }
            }
        }
    }
}
