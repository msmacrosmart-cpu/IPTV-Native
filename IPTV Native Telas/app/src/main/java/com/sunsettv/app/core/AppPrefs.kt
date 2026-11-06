package com.sunsettv.app.core

import android.content.Context
import com.sunsettv.app.domain.XtreamCredentials

/** Preferências simples do app (EPG e opções). */
class AppPrefs(context: Context) {
    private val sp = context.getSharedPreferences("sunset_prefs", Context.MODE_PRIVATE)

    var lastEpgUpdateMs: Long
        get() = sp.getLong("epg_last", 0L)
        set(v) { sp.edit().putLong("epg_last", v).apply() }

    var epgChannels: Int
        get() = sp.getInt("epg_channels", 0)
        set(v) { sp.edit().putInt("epg_channels", v).apply() }

    var epgPrograms: Int
        get() = sp.getInt("epg_programs", 0)
        set(v) { sp.edit().putInt("epg_programs", v).apply() }

    var customXmltvUrl: String
        get() = sp.getString("epg_custom_url", "").orEmpty()
        set(v) { sp.edit().putString("epg_custom_url", v.trim()).apply() }

    var autoUpdateEpg: Boolean
        get() = sp.getBoolean("epg_auto", true)
        set(v) { sp.edit().putBoolean("epg_auto", v).apply() }
}

/**
 * Sessão Xtream. Se o seu projeto já guarda as credenciais (ex.: EncryptedSharedPreferences/DataStore),
 * troque APENAS a implementação de get()/clear() pela sua.
 */
class SessionStore(context: Context) {
    private val sp = context.getSharedPreferences("sunset_session", Context.MODE_PRIVATE)

    fun get(): XtreamCredentials? {
        val s = sp.getString("server", null)
        val u = sp.getString("user", null)
        val p = sp.getString("pass", null)
        return if (s.isNullOrBlank() || u.isNullOrBlank() || p == null) null else XtreamCredentials(s, u, p)
    }

    fun save(c: XtreamCredentials) {
        sp.edit().putString("server", c.server).putString("user", c.username).putString("pass", c.password).apply()
    }

    fun clear() { sp.edit().clear().apply() }
}
