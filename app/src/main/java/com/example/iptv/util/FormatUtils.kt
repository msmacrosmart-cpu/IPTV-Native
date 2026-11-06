package com.example.iptv.util

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit

/**
 * Small formatting helpers shared by every screen.
 */
object FormatUtils {

    private val BRAZIL: Locale = Locale("pt", "BR")

    fun formatDate(millis: Long?): String {
        if (millis == null || millis <= 0L) return "—"
        return SimpleDateFormat("dd/MM/yyyy", BRAZIL).format(Date(millis))
    }

    fun formatTime(epochMillis: Long): String =
        SimpleDateFormat("HH:mm", BRAZIL).format(Date(epochMillis))

    fun formatDayAndTime(epochMillis: Long): String =
        SimpleDateFormat("dd/MM HH:mm", BRAZIL).format(Date(epochMillis))

    /** 95 -> "1:35", 3600 -> "1:00:00". */
    fun formatClock(millis: Long): String {
        if (millis <= 0L) return "00:00"
        val totalSeconds = TimeUnit.MILLISECONDS.toSeconds(millis)
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return if (hours > 0) {
            String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.US, "%02d:%02d", minutes, seconds)
        }
    }

    fun formatCount(value: Int): String = String.format(BRAZIL, "%,d", value)

    fun remainingDays(expiresAtMillis: Long?, nowMillis: Long = System.currentTimeMillis()): Long? {
        if (expiresAtMillis == null || expiresAtMillis <= 0L) return null
        return TimeUnit.MILLISECONDS.toDays(expiresAtMillis - nowMillis)
    }

    fun parseEpgDate(raw: String?): Long? {
        val value = raw?.trim().orEmpty()
        if (value.isEmpty()) return null
        val patterns = listOf("yyyy-MM-dd HH:mm:ss", "yyyy-MM-dd'T'HH:mm:ss", "dd/MM/yyyy HH:mm:ss")
        for (pattern in patterns) {
            try {
                val parser = SimpleDateFormat(pattern, Locale.US)
                parser.timeZone = TimeZone.getTimeZone("UTC")
                return parser.parse(value)?.time
            } catch (_: Exception) {
                // try next pattern
            }
        }
        return null
    }
}

/** Walks up the ContextWrapper chain to find the hosting Activity. */
fun Context.findActivity(): Activity? {
    var context: Context = this
    while (context is ContextWrapper) {
        if (context is Activity) return context
        context = context.baseContext
    }
    return null
}
