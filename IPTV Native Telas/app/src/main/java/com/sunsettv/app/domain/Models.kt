package com.sunsettv.app.domain

/** Credenciais Xtream Codes. Adapte/mapeie para a classe que você já usa na tela de Login. */
data class XtreamCredentials(
    val server: String,
    val username: String,
    val password: String,
) {
    val baseUrl: String
        get() {
            val s = server.trim().trimEnd('/')
            return if (s.startsWith("http://", true) || s.startsWith("https://", true)) s else "http://$s"
        }
}

data class LiveCategory(val id: String, val name: String)

data class LiveChannel(
    val streamId: Int,
    val number: Int,
    val name: String,
    val logoUrl: String?,
    val categoryId: String,
    val epgChannelId: String?,
    val streamUrl: String,
)

data class EpgProgram(
    val title: String,
    val description: String,
    val startMs: Long,
    val endMs: Long,
) {
    fun isLiveAt(now: Long) = now in startMs until endMs

    fun progressAt(now: Long): Float {
        val total = (endMs - startMs).coerceAtLeast(1L)
        return ((now - startMs).toFloat() / total).coerceIn(0f, 1f)
    }
}

data class AccountInfo(
    val username: String,
    val status: String,
    val expiresAtMs: Long?,      // null = ilimitado
    val createdAtMs: Long?,
    val isTrial: Boolean,
    val activeConnections: Int,
    val maxConnections: Int,
    val serverUrl: String,
    val timezone: String?,
) {
    fun isExpiredAt(now: Long) = expiresAtMs != null && expiresAtMs < now
    fun isActiveAt(now: Long) = status.equals("Active", true) && !isExpiredAt(now)
}
