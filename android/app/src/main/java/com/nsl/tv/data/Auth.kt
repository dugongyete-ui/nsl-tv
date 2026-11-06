package com.nsl.tv.data

import android.content.Context
import android.os.Build
import android.provider.Settings
import java.security.MessageDigest
import java.util.UUID

/**
 * Identitas perangkat & tanda tangan permintaan (paritas Dopamine TV):
 * signature = hex(SHA-256(deviceId + ":" + timestampMs + ":" + APP_SECRET))
 */
object Auth {
    const val APP_SECRET = "DopamineTV_SecKey_2026_X9#"
    const val PREFS = "dopamine_tv_prefs"
    const val KEY_DEVICE_ID = "unique_device_id_v1"
    const val APP_VERSION = "1.0.5"
    const val USER_AGENT = "NSLTV/1.0.5 (Android; ARM) Kodi/21.0"
    const val BASE_URL = "https://dopaminetv.mikrofast.my.id"

    fun deviceId(ctx: Context): String {
        val p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        p.getString(KEY_DEVICE_ID, null)?.let { return it }
        val id = (Settings.Secure.getString(ctx.contentResolver, Settings.Secure.ANDROID_ID)
            ?: "").trim().lowercase().takeIf { it.length >= 8 }
            ?: UUID.randomUUID().toString().replace("-", "").take(16)
        p.edit().putString(KEY_DEVICE_ID, id).apply()
        return id
    }

    fun signature(deviceId: String, timestampMs: Long): String {
        val data = "$deviceId:$timestampMs:$APP_SECRET"
        val digest = MessageDigest.getInstance("SHA-256").digest(data.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    fun isTvBox(ctx: Context): Boolean {
        val ui = ctx.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_TYPE_MASK
        if (ui == android.content.res.Configuration.UI_MODE_TYPE_TELEVISION) return true
        val model = "${Build.MANUFACTURER} ${Build.MODEL}".lowercase()
        return listOf("tv", "box", "stb", "zte", "fiberhome", "chromecast", "shield").any { model.contains(it) }
    }

    fun deviceModel(): String = "${Build.MANUFACTURER} ${Build.MODEL}".trim()

    /** Header autentikasi lengkap untuk setiap panggilan API. */
    fun headers(ctx: Context): Map<String, String> {
        val ts = System.currentTimeMillis()
        val dev = deviceId(ctx)
        val h = LinkedHashMap<String, String>()
        h["User-Agent"] = USER_AGENT
        h["X-Device-Id"] = dev
        h["X-Device-Model"] = deviceModel()
        h["X-Device-Type"] = if (isTvBox(ctx)) "TV Box" else "Smartphone"
        h["X-Device-Android"] = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"
        h["X-App-Version"] = APP_VERSION
        h["X-App-Timestamp"] = ts.toString()
        h["X-App-Signature"] = signature(dev, ts)
        h["X-App-Capabilities"] = ""
        h["X-Device-Exposure"] = ""
        return h
    }
}
