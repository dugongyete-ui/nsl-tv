package com.nsl.tv.data

import android.content.Context

/** Preferensi aplikasi (paritas dopamine_tv_prefs). */
object Prefs {
    private fun p(ctx: Context) = ctx.getSharedPreferences(Auth.PREFS, Context.MODE_PRIVATE)

    // ---- Channel cache ----
    fun saveCachedChannels(ctx: Context, json: String) =
        p(ctx).edit().putString("cached_channels_json", json).apply()

    fun cachedChannels(ctx: Context): String? = p(ctx).getString("cached_channels_json", null)

    // ---- Playlist M3U ----
    fun lastPlaylistUrl(ctx: Context): String? = p(ctx).getString("last_playlist_url", null)

    fun setLastPlaylistUrl(ctx: Context, v: String?) = p(ctx).edit().putString("last_playlist_url", v).apply()

    fun saveCachedM3u(ctx: Context, content: String) =
        p(ctx).edit().putString("cached_m3u_content", content).apply()

    fun cachedM3u(ctx: Context): String? = p(ctx).getString("cached_m3u_content", null)

    // ---- Favorit ----
    fun favorites(ctx: Context): MutableSet<String> =
        p(ctx).getStringSet("favorite_channel_ids", emptySet())?.toMutableSet() ?: mutableSetOf()

    fun toggleFavorite(ctx: Context, id: String) {
        val s = favorites(ctx)
        if (!s.add(id)) s.remove(id)
        p(ctx).edit().putStringSet("favorite_channel_ids", s).apply()
    }

    fun isFavorite(ctx: Context, id: String): Boolean = favorites(ctx).contains(id)

    fun clearFavorites(ctx: Context) = p(ctx).edit().putStringSet("favorite_channel_ids", emptySet()).apply()

    // ---- Pengaturan ----
    fun autoPlayLast(ctx: Context): Boolean = p(ctx).getBoolean("auto_play_last_channel", true)

    fun setAutoPlayLast(ctx: Context, v: Boolean) = p(ctx).edit().putBoolean("auto_play_last_channel", v).apply()

    fun autoStartBoot(ctx: Context): Boolean = p(ctx).getBoolean("auto_start_boot", false)

    fun setAutoStartBoot(ctx: Context, v: Boolean) = p(ctx).edit().putBoolean("auto_start_boot", v).apply()

    fun autoRefreshOnLaunch(ctx: Context): Boolean = p(ctx).getBoolean("auto_update_playlist_launch", true)

    fun setAutoRefreshOnLaunch(ctx: Context, v: Boolean) = p(ctx).edit().putBoolean("auto_update_playlist_launch", v).apply()

    fun lastPlayedChannelId(ctx: Context): String? = p(ctx).getString("last_played_channel_id", null)

    fun setLastPlayedChannelId(ctx: Context, v: String?) = p(ctx).edit().putString("last_played_channel_id", v).apply()
}
