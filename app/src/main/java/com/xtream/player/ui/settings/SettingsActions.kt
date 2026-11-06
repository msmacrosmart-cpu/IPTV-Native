package com.xtream.player.ui.settings

import com.example.iptv.data.model.UserSettings

/** Actions are kept separate from the visual sections so every option changes real state. */
class SettingsActions(
    val saveCredentials: (String, String, String) -> Unit,
    val switchProfile: (String) -> Unit,
    val removeProfile: (String) -> Unit,
    val refreshAccount: () -> Unit,
    val refreshEpg: () -> Unit,
    val refreshContent: () -> Unit,
    val updateSettings: (UserSettings) -> Unit,
    val clearCache: () -> Unit,
    val setPin: (String, String, String, (String?) -> Unit) -> Unit,
    val unlock: (String, (String?) -> Unit) -> Unit,
    val lock: () -> Unit,
    val clearPin: () -> Unit,
    val toggleCategory: (String) -> Unit,
    val logout: () -> Unit,
)
