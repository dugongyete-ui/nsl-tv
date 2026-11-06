package com.nsl.tv.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

private val logoHttp = OkHttpClient.Builder()
    .connectTimeout(8, TimeUnit.SECONDS)
    .readTimeout(8, TimeUnit.SECONDS)
    .build()

/**
 * Cache logo thread-safe (android.util.LruCache aman dari akses multithread).
 * Bitmap di-downsample ke maksimal 256 px — mencegah OutOfMemoryError saat
 * menelusuri ratusan saluran. Ukuran cache dibatasi ~1/16 heap aplikasi.
 */
private object LogoCache {
    private const val MAX_DIM = 256

    private val cache = LruCache<String, Bitmap>(
        (Runtime.getRuntime().maxMemory() / 16 / 1024).toInt().coerceIn(512, 8192) // KB
    )

    fun get(url: String): Bitmap? = try { cache.get(url) } catch (_: Throwable) { null }

    fun put(url: String, bmp: Bitmap) {
        try { cache.put(url, bmp) } catch (_: Throwable) {}
    }

    /** Dipanggil sistem saat memori menipis — buang semua logo, akan dimuat ulang. */
    fun trim() {
        try { cache.evictAll() } catch (_: Throwable) {}
    }

    /** Decode dengan downsampling: bitmap > 256 px dibuat perkecil via inSampleSize. */
    fun decode(bytes: ByteArray): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        var w = bounds.outWidth
        var h = bounds.outHeight
        while (w / 2 >= MAX_DIM && h / 2 >= MAX_DIM) {
            w /= 2; h /= 2; sample *= 2
        }
        val opts = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.RGB_565 // logo: hemat memori 50%, tanpa alpha penting
        }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
    }
}

/** Trim cache logo dari Application.onTrimMemory. */
fun trimLogoCache() = LogoCache.trim()

/** Logo saluran async dengan cache memori aman-thread + placeholder inisial. */
@Composable
fun ChannelLogo(url: String?, name: String, size: Dp, modifier: Modifier = Modifier) {
    var bitmap by remember(url) { mutableStateOf(url?.let { LogoCache.get(it) }) }
    var failed by remember(url) { mutableStateOf(false) }

    LaunchedEffect(url) {
        if (url == null) return@LaunchedEffect
        if (bitmap != null || failed) return@LaunchedEffect
        val b = withContext(Dispatchers.IO) {
            try {
                val bytes = logoHttp.newCall(
                    Request.Builder().url(url).header("User-Agent", "Mozilla/5.0").build()
                ).execute().use { r ->
                    if (r.isSuccessful) r.body?.bytes() else null
                }
                bytes?.let { LogoCache.decode(it) }?.also { LogoCache.put(url, it) }
            } catch (_: Throwable) { null }
        }
        if (b != null) bitmap = b else failed = true
    }

    if (bitmap != null) {
        Image(
            bitmap = bitmap!!.asImageBitmap(),
            contentDescription = "Logo $name",
            contentScale = ContentScale.Fit,
            modifier = modifier.size(size)
        )
    } else {
        Box(
            modifier = modifier
                .size(size)
                .clip(RoundedCornerShape(10.dp))
                .background(Palette.CardSoft),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = name.take(1).uppercase(),
                color = Palette.Amber,
                fontSize = (size.value / 2.2f).sp,
                textAlign = TextAlign.Center
            )
        }
    }
}
