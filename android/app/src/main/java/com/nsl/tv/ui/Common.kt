package com.nsl.tv.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
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

private val logoCache = object : LinkedHashMap<String, Bitmap>(64, 0.75f, true) {
    override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Bitmap>): Boolean = size > 256
}

/** Logo saluran async dengan cache memori + placeholder inisial. */
@Composable
fun ChannelLogo(url: String?, name: String, size: Dp, modifier: Modifier = Modifier) {
    var bitmap by remember(url) { mutableStateOf<Bitmap?>(logoCache[url]) }

    LaunchedEffect(url) {
        if (url != null && bitmap == null) {
            val b = withContext(Dispatchers.IO) {
                try {
                    val bmp = logoHttp.newCall(Request.Builder().url(url).header("User-Agent", "Mozilla/5.0").build())
                        .execute().use { r ->
                            if (r.isSuccessful) r.body?.byteStream()?.use { BitmapFactory.decodeStream(it) } else null
                        }
                    bmp?.let { if (logoCache.size > 256) logoCache.clear(); logoCache[url] = it }
                    bmp
                } catch (_: Exception) { null }
            }
            bitmap = b
        }
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
