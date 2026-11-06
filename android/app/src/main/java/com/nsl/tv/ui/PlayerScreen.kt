package com.nsl.tv.ui

import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.ui.PlayerView
import com.nsl.tv.data.Channel
import com.nsl.tv.player.PlayerFactory
import kotlinx.coroutines.delay

private const val BUFFER_TIMEOUT_MS = 20_000L   // streaming macet > 20 dtk → pindah sumber
private const val MAX_ROUNDS = 2                // maksimal 2 putaran penuh semua sumber

@Composable
fun PlayerScreen(
    channel: Channel,
    onDismiss: () -> Unit,
    onSourceChanged: (Int) -> Unit,
    onEnterPip: () -> Unit,
    inPip: Boolean
) {
    val context = LocalContext.current
    val sources = channel.sources

    // ---- Player dibuat dengan aman (anti force-close) ----
    val player = remember(channel.id) { PlayerFactory.createPlayerSafe(context) }

    var sourceIndex by remember(channel.id) { mutableIntStateOf(0) }
    var attempt by remember(channel.id) { mutableIntStateOf(0) }      // memicu percobaan ulang
    var rounds by remember(channel.id) { mutableIntStateOf(0) }       // penghitung putaran sumber
    var playWhenReady by remember { mutableStateOf(true) }
    var isBuffering by remember { mutableStateOf(true) }
    var isReady by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var controlsVisible by remember { mutableStateOf(true) }
    var muted by remember { mutableStateOf(false) }
    var ratioMode by remember { mutableStateOf("FIT") }
    var qualityMode by remember { mutableStateOf("AUTO") }
    var showPlaylist by remember { mutableStateOf(false) }

    // ---- Ganti ke sumber berikutnya (fallback aman) ----
    fun advanceSource() {
        isReady = false
        if (sourceIndex < sources.size - 1) {
            sourceIndex++
            errorMsg = null
        } else {
            rounds++
            if (rounds >= MAX_ROUNDS) {
                errorMsg = "Semua sumber gagal diputar. Coba lagi atau pilih sumber lain."
                isBuffering = false
            } else {
                sourceIndex = 0
                attempt++ // paksa siapkan ulang dari sumber pertama
            }
        }
    }

    // ---- Pasang sumber ke player ----
    if (player != null) {
        DisposableEffect(channel.id, sourceIndex, attempt) {
            val s = sources.getOrNull(sourceIndex)
            errorMsg = null
            isBuffering = true
            isReady = false
            if (s == null) {
                errorMsg = if (sources.isEmpty()) "Tidak ada sumber tersedia" else "Sumber tidak valid"
                isBuffering = false
            } else {
                try {
                    val mediaSource = PlayerFactory.createMediaSource(context, s)
                    player.setMediaSource(mediaSource)
                    player.playWhenReady = true
                    player.prepare()
                } catch (e: Exception) {
                    isBuffering = false
                    advanceSource()
                }
            }
            onDispose { }
        }

        // ---- Listener status player ----
        DisposableEffect(player, channel.id) {
            val listener = object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    isBuffering = playbackState == Player.STATE_BUFFERING
                    when (playbackState) {
                        Player.STATE_READY -> { isReady = true; errorMsg = null }
                        Player.STATE_ENDED -> advanceSource()
                        else -> {}
                    }
                }
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    if (isPlaying) playWhenReady = true
                }
                override fun onPlayerError(error: PlaybackException) {
                    advanceSource()
                }
            }
            player.addListener(listener)
            onDispose {
                runCatching { player.removeListener(listener) }
            }
        }

        // ---- Watchdog: buffering terlalu lama → pindah sumber otomatis ----
        LaunchedEffect(channel.id, sourceIndex, attempt) {
            delay(BUFFER_TIMEOUT_MS)
            if (!isReady) advanceSource()
        }

        // ---- Bersihkan player saat keluar ----
        DisposableEffect(channel.id) {
            onDispose {
                runCatching {
                    player.stop()
                    player.clearMediaItems()
                    player.release()
                }
            }
        }
    } else {
        // Player gagal dibuat sama sekali → tampilkan pesan, jangan crash
        LaunchedEffect(Unit) { errorMsg = "Perangkat tidak mendukung pemutar video" }
    }

    // ---- Sembunyikan kontrol otomatis ----
    LaunchedEffect(controlsVisible, isBuffering) {
        if (controlsVisible && !isBuffering) {
            delay(3800)
            controlsVisible = false
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { controlsVisible = !controlsVisible }
    ) {
        // ---- Permukaan video ----
        if (player != null) {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        layoutParams = FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        useController = false
                        resizeMode = androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT
                        setShutterBackgroundColor(android.graphics.Color.BLACK)
                        this.player = player
                    }
                },
                update = { view ->
                    runCatching {
                        view.resizeMode = when (ratioMode) {
                            "FILL" -> androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FILL
                            "ZOOM" -> androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                            else -> androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT
                        }
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        }

        // ---- Spinner buffering ----
        if (isBuffering && errorMsg == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Palette.Amber, strokeWidth = 3.dp, modifier = Modifier.size(46.dp))
            }
        }

        // ---- Pesan error ----
        errorMsg?.let { msg ->
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("⚠️", fontSize = 34.sp)
                    Spacer(Modifier.height(8.dp))
                    Text(msg, color = Palette.Text, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(14.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = {
                                rounds = 0
                                sourceIndex = 0
                                attempt++
                                errorMsg = null
                                isBuffering = true
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Palette.Amber, contentColor = Palette.Bg),
                            shape = RoundedCornerShape(10.dp)
                        ) { Text("Coba Lagi") }
                        OutlinedButton(
                            onClick = onDismiss,
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Palette.Text)
                        ) { Text("Tutup") }
                    }
                }
            }
        }

        // ---- Overlay kontrol ----
        if (controlsVisible && !inPip) {
            Column(Modifier.fillMaxSize()) {
                // ======== Atas ========
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.55f))
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Filled.Close, contentDescription = "Tutup",
                        tint = Color.White,
                        modifier = Modifier
                            .size(26.dp)
                            .clickable { onDismiss() }
                    )
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            channel.name,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        val s = sources.getOrNull(sourceIndex)
                        Text(
                            buildString {
                                append("Sumber ${sourceIndex + 1}/${sources.size}")
                                s?.let {
                                    append("  •  ${it.manifestType.uppercase()}")
                                    it.drm?.let { d -> append("  •  DRM ${d.type}") }
                                }
                            },
                            color = Palette.TextDim,
                            fontSize = 11.5.sp
                        )
                    }
                    Icon(
                        Icons.Filled.PictureInPictureAlt, contentDescription = "PIP",
                        tint = Color.White,
                        modifier = Modifier
                            .size(24.dp)
                            .clickable { onEnterPip() }
                    )
                }

                // ======== Tengah ========
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Row(horizontalArrangement = Arrangement.spacedBy(26.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Filled.SkipPrevious, contentDescription = "Sumber sebelumnya",
                            tint = Color.White,
                            modifier = Modifier
                                .size(34.dp)
                                .clickable {
                                    rounds = 0; errorMsg = null
                                    if (sourceIndex > 0) sourceIndex-- else sourceIndex = sources.size - 1
                                }
                        )
                        Box(
                            Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.14f))
                                .clickable {
                                    player?.let { p ->
                                        runCatching { p.playWhenReady = !p.playWhenReady }
                                        playWhenReady = p.playWhenReady
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            if (isBuffering) {
                                CircularProgressIndicator(color = Palette.Amber, strokeWidth = 2.5.dp, modifier = Modifier.size(26.dp))
                            } else {
                                Icon(
                                    if (playWhenReady) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                    contentDescription = "Putar/Jeda",
                                    tint = Color.White,
                                    modifier = Modifier.size(34.dp)
                                )
                            }
                        }
                        Icon(
                            Icons.Filled.SkipNext, contentDescription = "Sumber berikutnya",
                            tint = Color.White,
                            modifier = Modifier
                                .size(34.dp)
                                .clickable {
                                    rounds = 0; errorMsg = null
                                    if (sourceIndex < sources.size - 1) sourceIndex++ else sourceIndex = 0
                                }
                        )
                    }
                }

                // ======== Bawah ========
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.55f))
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        qualityMode,
                        color = Palette.Amber,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        modifier = Modifier.clickable {
                            qualityMode = if (qualityMode == "AUTO") "MAX" else "AUTO"
                            player?.let { p ->
                                runCatching {
                                    p.trackSelectionParameters = p.trackSelectionParameters
                                        .buildUpon()
                                        .setForceHighestSupportedBitrate(qualityMode == "MAX")
                                        .build()
                                }
                            }
                        }
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(18.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            ratioMode,
                            color = Palette.TextDim,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp,
                            modifier = Modifier.clickable {
                                ratioMode = when (ratioMode) { "FIT" -> "FILL"; "FILL" -> "ZOOM"; else -> "FIT" }
                            }
                        )
                        Icon(
                            if (muted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                            contentDescription = "Suara",
                            tint = Color.White,
                            modifier = Modifier
                                .size(22.dp)
                                .clickable {
                                    muted = !muted
                                    player?.let { runCatching { it.volume = if (muted) 0f else 1f } }
                                }
                        )
                        Icon(
                            Icons.Filled.Refresh, contentDescription = "Muat ulang",
                            tint = Color.White,
                            modifier = Modifier
                                .size(22.dp)
                                .clickable { rounds = 0; attempt++ }
                        )
                        Icon(
                            Icons.Filled.List, contentDescription = "Daftar sumber",
                            tint = Color.White,
                            modifier = Modifier
                                .size(22.dp)
                                .clickable { showPlaylist = true }
                        )
                    }
                }
            }
        }

        // ---- Sheet daftar sumber ----
        if (showPlaylist) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.6f))
                    .clickable { showPlaylist = false },
                contentAlignment = Alignment.BottomCenter
            ) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                        .background(Palette.BgElev)
                        .clickable(enabled = false) { }
                        .padding(16.dp)
                ) {
                    Text("Pilih Sumber", color = Palette.Text, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Spacer(Modifier.height(8.dp))
                    LazyColumn(Modifier.heightIn(max = 260.dp)) {
                        itemsIndexed(sources) { i, s ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (i == sourceIndex) Palette.Amber.copy(alpha = 0.16f) else Color.Transparent)
                                    .clickable {
                                        rounds = 0; sourceIndex = i; showPlaylist = false
                                    }
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "${i + 1}",
                                    color = if (i == sourceIndex) Palette.Amber else Palette.TextDim,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    modifier = Modifier.width(24.dp)
                                )
                                Column {
                                    Text(
                                        s.resolutionLabel ?: s.manifestType.uppercase(),
                                        color = Palette.Text,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        s.drm?.let { "DRM ${it.type}" } ?: "Tanpa DRM",
                                        color = Palette.TextDim,
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                }
            }
        }
    }
}
