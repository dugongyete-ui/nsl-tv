package com.nsl.tv.data

import android.content.Context

/**
 * Repositori saluran: API utama, cache offline, fallback M3U.
 */
class ChannelRepository(private val ctx: Context) {

    private val api = ApiClient.get(ctx)

    /** Ambil saluran: coba API → cache → M3U tersimpan. Melempar exception bila semua gagal. */
    fun load(autoRefresh: Boolean): ChannelsResponse {
        if (autoRefresh) {
            try {
                val fresh = api.fetchChannels()
                api.pingDevice()
                return fresh
            } catch (_: Exception) {
                // lanjut ke cache
            }
        }
        val cached = Prefs.cachedChannels(ctx)
        if (cached != null) {
            try {
                val parsed = Models.parseChannels(cached)
                if (parsed.channels.isNotEmpty()) return parsed
            } catch (_: Exception) {
            }
        }
        // Fallback terakhir: M3U tersimpan / URL playlist terakhir
        val m3u = Prefs.cachedM3u(ctx) ?: Prefs.lastPlaylistUrl(ctx)?.let { api.fetchM3u(it) }
        if (!m3u.isNullOrBlank()) {
            val channels = M3UParser.parse(m3u)
            if (channels.isNotEmpty()) {
                return ChannelsResponse(channels, channels.map { it.groupTitle }.distinct(), null, null)
            }
        }
        throw IllegalStateException("Tidak dapat memuat daftar saluran")
    }

    /** Refresh manual dari pengaturan. */
    fun refresh(): ChannelsResponse {
        val fresh = api.fetchChannels()
        api.pingDevice()
        return fresh
    }

    companion object {
        @Volatile private var instance: ChannelRepository? = null
        fun get(ctx: Context): ChannelRepository =
            instance ?: synchronized(this) {
                instance ?: ChannelRepository(ctx.applicationContext).also { instance = it }
            }
    }
}
