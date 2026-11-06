package com.nsl.tv.data

import android.content.Context
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/** Klien HTTP untuk API Dopamine TV + penyimpanan cache saluran. */
class ApiClient(private val ctx: Context) {

    private val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    /** GET /api/v1/channels — daftar saluran + kategori + info server. */
    fun fetchChannels(): ChannelsResponse {
        val req = Request.Builder()
            .url("${Auth.BASE_URL}/api/v1/channels")
            .apply { Auth.headers(ctx).forEach { (k, v) -> header(k, v) } }
            .get()
            .build()
        http.newCall(req).execute().use { resp ->
            val body = resp.body?.string() ?: throw IllegalStateException("Respons kosong")
            if (!resp.isSuccessful) throw IllegalStateException("HTTP ${resp.code}: ${resp.message}")
            val parsed = Models.parseChannels(body)
            if (parsed.channels.isEmpty()) throw IllegalStateException("Daftar saluran kosong")
            Prefs.saveCachedChannels(ctx, body)
            return parsed
        }
    }

    /** POST /api/v1/devices/ping — heartbeat perangkat (tanpa UI). */
    fun pingDevice() {
        try {
            val req = Request.Builder()
                .url("${Auth.BASE_URL}/api/v1/devices/ping")
                .apply { Auth.headers(ctx).forEach { (k, v) -> header(k, v) } }
                .post("{}".toRequestBody("application/json".toMediaType()))
                .build()
            http.newCall(req).execute().use { }
        } catch (_: Exception) {
        }
    }

    /** Unduh konten M3U dari URL eksternal (dukungan playlist khusus). */
    fun fetchM3u(url: String): String {
        val req = Request.Builder().url(url)
            .header("User-Agent", Auth.USER_AGENT)
            .get().build()
        http.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw IllegalStateException("HTTP ${resp.code}")
            return resp.body?.string() ?: throw IllegalStateException("M3U kosong")
        }
    }

    companion object {
        @Volatile private var instance: ApiClient? = null
        fun get(ctx: Context): ApiClient =
            instance ?: synchronized(this) { instance ?: ApiClient(ctx.applicationContext).also { instance = it } }
    }
}
