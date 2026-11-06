package com.xtream.player.ui.settings

import android.app.UiModeManager
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xtream.player.ui.components.GlowButton
import com.xtream.player.ui.theme.XColors

/** Real system/external actions are identified as such, rather than imitating a VPN or speed test. */
@Composable
internal fun DeviceTools() {
    val context = LocalContext.current
    val tv = (context.getSystemService(Context.UI_MODE_SERVICE) as? UiModeManager)
        ?.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION
    fun open(intent: Intent) {
        runCatching { context.startActivity(intent) }.onFailure {
            Toast.makeText(context, "Nenhum aplicativo compatível encontrado neste dispositivo.", Toast.LENGTH_LONG).show()
        }
    }
    SettingsPanel("Teste de velocidade", "Serviço externo disponível neste aparelho.") {
        SettingLabel("Teste de velocidade")
        Text("Abre um teste externo de velocidade no navegador. O resultado e a política de privacidade são do serviço escolhido.",
            color = XColors.Body, fontSize = 12.sp)
        Spacer(Modifier.height(8.dp))
        GlowButton("Abrir teste de velocidade", ringAlways = false, modifier = Modifier.testTag("setting-speed-test")) {
            open(Intent(Intent.ACTION_VIEW, Uri.parse("https://fast.com/")))
        }
    }
    SettingsPanel("VPN", "Configurações do sistema Android.") {
        SettingLabel("VPN")
        Text("Abra as configurações de VPN do Android. O aplicativo não fornece uma VPN própria.", color = XColors.Body, fontSize = 12.sp)
        Spacer(Modifier.height(8.dp))
        GlowButton("Configurar VPN no Android", ringAlways = false, modifier = Modifier.testTag("setting-vpn")) {
            open(Intent(Settings.ACTION_VPN_SETTINGS))
        }
    }
    SettingsPanel("Tipo de dispositivo") {
        SettingLabel("Tipo de dispositivo")
        Text("${if (tv) "Android TV" else "Celular ou tablet Android"}  ·  ${Build.MANUFACTURER} ${Build.MODEL}  ·  Android ${Build.VERSION.RELEASE}",
            color = XColors.TextDim, fontSize = 13.sp, modifier = Modifier.testTag("setting-device-type"))
        Text("O layout é ajustado automaticamente; a orientação do aplicativo permanece horizontal.", color = XColors.Body, fontSize = 12.sp)
    }
}
