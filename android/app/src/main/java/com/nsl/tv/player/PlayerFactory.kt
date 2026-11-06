package com.nsl.tv.player

import android.content.Context
import android.util.Base64
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.dash.DashMediaSource
import androidx.media3.exoplayer.drm.DefaultDrmSessionManager
import androidx.media3.exoplayer.drm.DrmSessionManager
import androidx.media3.exoplayer.drm.FrameworkMediaDrm
import androidx.media3.exoplayer.drm.HttpMediaDrmCallback
import androidx.media3.exoplayer.drm.LocalMediaDrmCallback
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import com.nsl.tv.data.Source
import okhttp3.OkHttpClient
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Pabrik player & MediaSource — inti perbaikan streaming:
 *  1. Setiap sumber memakai OkHttpDataSource dengan header spesifik saluran (UA/Referer/Origin/Cookie).
 *  2. DRM Widevine → HttpMediaDrmCallback (licenseUrl, atau licenseKey berupa URL).
 *  3. DRM ClearKey → "kid:key" hex dikonversi ke JWK JSON → LocalMediaDrmCallback.
 *  4. manifestType dipetakan tepat: mpd → Dash, hls → Hls (chunkless preparation), ts → Progressive.
 *  5. Buffer stabil: min 15 s, max 50 s, playback 500 ms, rebuffer 1000 ms.
 *  6. Dekoder fallback aktif + paksa kualitas tertinggi.
 */
object PlayerFactory {

    private val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    fun createPlayer(context: Context): ExoPlayer {
        val appCtx = context.applicationContext
        val renderers = DefaultRenderersFactory(appCtx)
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)
            .setEnableDecoderFallback(true)

        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(15_000, 50_000, 500, 1_000)
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()

        return ExoPlayer.Builder(appCtx, renderers)
            .setLoadControl(loadControl)
            .build()
            .apply {
                runCatching {
                    trackSelectionParameters = trackSelectionParameters.buildUpon()
                        .setForceHighestSupportedBitrate(true)
                        .build()
                }
            }
    }

    /**
     * Pembuatan player anti-crash: coba konfigurasi lengkap → fallback player sederhana.
     * Mengembalikan null hanya bila perangkat benar-benar tidak mampu membuat player.
     */
    fun createPlayerSafe(context: Context): ExoPlayer? = try {
        createPlayer(context)
    } catch (_: Throwable) {
        try {
            ExoPlayer.Builder(context.applicationContext).build()
        } catch (_: Throwable) {
            null
        }
    }

    /** DataSource per-sumber: header saluran dijamin terkirim ke CDN. */
    private fun httpFactory(context: Context, source: Source): DefaultDataSource.Factory {
        val ok = OkHttpDataSource.Factory(http)
            .setUserAgent(source.headers["user-agent"] ?: com.nsl.tv.data.Auth.USER_AGENT)
        val extra = source.headers.filterKeys { !it.equals("user-agent", true) }
        if (extra.isNotEmpty()) ok.setDefaultRequestProperties(extra)
        return DefaultDataSource.Factory(context.applicationContext, ok)
    }

    fun createMediaSource(context: Context, source: Source): MediaSource {
        val factory = httpFactory(context, source)
        val drmManager = drmManager(source)

        val item = MediaItem.Builder()
            .setUri(source.url)
            .setMimeType(
                when (source.manifestType) {
                    "mpd" -> MimeTypes.APPLICATION_MPD
                    "ts" -> MimeTypes.APPLICATION_MP4
                    else -> MimeTypes.APPLICATION_M3U8
                }
            )
            .build()

        return when (source.manifestType) {
            "mpd" -> DashMediaSource.Factory(factory)
                .apply { if (drmManager != null) setDrmSessionManagerProvider { drmManager } }
                .createMediaSource(item)
            "ts" -> ProgressiveMediaSource.Factory(factory)
                .apply { if (drmManager != null) setDrmSessionManagerProvider { drmManager } }
                .createMediaSource(item)
            else -> HlsMediaSource.Factory(factory)
                .setAllowChunklessPreparation(true)
                .apply { if (drmManager != null) setDrmSessionManagerProvider { drmManager } }
                .createMediaSource(item)
        }
    }

    /** Bangun DrmSessionManager sesuai tipe DRM sumber. */
    private fun drmManager(source: Source): DrmSessionManager? {
        val drm = source.drm ?: return null
        return try {
            when (drm.type) {
                "CLEARKEY" -> clearKeyManager(drm)
                "PLAYREADY" -> null
                else -> widevineManager(drm)
            }
        } catch (_: Exception) {
            null // tanpa DRM → coba putar jernih bila tersedia
        }
    }

    private fun widevineManager(drm: com.nsl.tv.data.DrmConfig): DrmSessionManager? {
        val url = drm.licenseUrl ?: drm.licenseKey?.takeIf { it.startsWith("http") } ?: return null
        val callback = HttpMediaDrmCallback(url, OkHttpDataSource.Factory(http))
        drm.headers.forEach { (k, v) ->
            try { callback.setKeyRequestProperty(k, v) } catch (_: Exception) {}
        }
        return DefaultDrmSessionManager.Builder()
            .setUuidAndExoMediaDrmProvider(C.WIDEVINE_UUID, FrameworkMediaDrm.DEFAULT_PROVIDER)
            .setMultiSession(true)
            .build(callback)
    }

    /** ClearKey: JWK JSON dipakai langsung; format "kidHex:keyHex" dikonversi ke JWK. */
    private fun clearKeyManager(drm: com.nsl.tv.data.DrmConfig): DrmSessionManager? {
        val json = clearKeyJwk(drm) ?: return null
        val callback = LocalMediaDrmCallback(json.toByteArray(Charsets.UTF_8))
        return DefaultDrmSessionManager.Builder()
            .setUuidAndExoMediaDrmProvider(C.CLEARKEY_UUID, FrameworkMediaDrm.DEFAULT_PROVIDER)
            .build(callback)
    }

    private fun clearKeyJwk(drm: com.nsl.tv.data.DrmConfig): String? {
        val key = drm.licenseKey?.trim() ?: return null
        return when {
            key.startsWith("{") -> key
            key.startsWith("http") -> null // URL ClearKey tidak standar → abaikan
            else -> {
                val parts = key.split(":")
                if (parts.size < 2) return null
                val kid = normalizeHex(parts[0])
                val k = normalizeHex(parts[1])
                if (kid.isEmpty() || k.isEmpty()) return null
                JSONObject()
                    .put("keys", JSONArray().put(JSONObject().put("kty", "oct").put("kid", hexToB64url(kid)).put("k", hexToB64url(k))))
                    .put("type", "temporary")
                    .toString()
            }
        }
    }

    private fun normalizeHex(s: String) =
        s.trim().lowercase().filter { it.isDigit() || it in 'a'..'f' }

    private fun hexToB64url(hex: String): String = Base64.encodeToString(
        hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray(),
        Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP
    )
}
