package com.nsl.tv

import android.app.PictureInPictureParams
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Rational
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Tv
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nsl.tv.data.AppUpdate
import com.nsl.tv.data.Channel
import com.nsl.tv.data.ChannelRepository
import com.nsl.tv.data.Prefs
import com.nsl.tv.ui.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.content.Context
import android.content.ContextWrapper

// ============================================================
// State aplikasi
// ============================================================
sealed interface LoadState {
    data object Loading : LoadState
    data class Ready(val channels: List<Channel>, val categories: List<String>, val serverVersion: String?) : LoadState
    data class Error(val message: String) : LoadState
}

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { NslRoot() }
    }

    fun enterPip() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)
        ) {
            try {
                val params = PictureInPictureParams.Builder()
                    .setAspectRatio(Rational(16, 9))
                    .build()
                enterPictureInPictureMode(params)
            } catch (_: Exception) {}
        }
    }

    var inPip: Boolean
        get() = isInPictureInPictureMode
        set(_) {}

    /**
     * Mode sinematik saat menonton: layar penuh (status bar & nav bar disembunyikan),
     * orientasi landscape, dan layar tidak mati selama menonton.
     */
    fun playerUiMode(enable: Boolean) {
        runCatching {
            if (enable) {
                requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                val c = WindowCompat.getInsetsController(window, window.decorView)
                c.systemBarsBehavior =
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                c.hide(WindowInsetsCompat.Type.systemBars())
            } else {
                requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                WindowCompat.getInsetsController(window, window.decorView)
                    .show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    /** Ganti orientasi manual saat menonton (ikon rotasi di bilah bawah). */
    fun setLandscape(enable: Boolean) {
        runCatching {
            requestedOrientation = if (enable) ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            else ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
        }
    }
}

/** Cari MainActivity dari context apa pun tanpa cast yang bisa crash. */
tailrec fun Context.findMainActivity(): MainActivity? = when (this) {
    is MainActivity -> this
    is ContextWrapper -> baseContext?.findMainActivity()
    else -> null
}

@Composable
fun NslRoot() {
    val activity = androidx.compose.ui.platform.LocalContext.current.findMainActivity()
    val appContext: Context = activity ?: androidx.compose.ui.platform.LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf<LoadState>(LoadState.Loading) }
    var tab by remember { mutableStateOf(0) }
    var search by remember { mutableStateOf("") }
    var activeCategory by remember { mutableStateOf("SEMUA") }
    var favorites by remember { mutableStateOf(Prefs.favorites(appContext)) }
    var playerChannel by remember { mutableStateOf<Channel?>(null) }
    var snackbar by remember { mutableStateOf<String?>(null) }

    // ---- Riwayat navigasi (tombol back sistem): beranda dulu, baru keluar ----
    androidx.activity.compose.BackHandler(
        enabled = playerChannel == null &&
            (search.isNotEmpty() || tab != 0 || activeCategory != "SEMUA")
    ) {
        when {
            search.isNotEmpty() -> search = ""
            tab != 0 -> tab = 0
            else -> activeCategory = "SEMUA"
        }
    }

    fun refreshFavorites() { favorites = Prefs.favorites(appContext) }

    suspend fun load(showToast: Boolean) {
        state = LoadState.Loading
        try {
            // Jaringan WAJIB di IO dispatcher — jangan pernah di main thread!
            val r = withContext(Dispatchers.IO) {
                ChannelRepository.get(appContext).load(Prefs.autoRefreshOnLaunch(appContext))
            }
            state = LoadState.Ready(r.channels, r.categories, r.server?.version)
            if (showToast) snackbar = "Daftar saluran diperbarui"
            // Auto-play saluran terakhir (hanya bila punya sumber)
            if (playerChannel == null && Prefs.autoPlayLast(appContext)) {
                Prefs.lastPlayedChannelId(appContext)?.let { lastId ->
                    r.channels.firstOrNull { it.id == lastId && it.sources.isNotEmpty() }?.let { playerChannel = it }
                }
            }
        } catch (e: Exception) {
            state = LoadState.Error(e.message ?: "Kesalahan tidak diketahui")
        }
    }

    LaunchedEffect(Unit) { load(false) }

    MaterialTheme(
        colorScheme = darkColorScheme(
            background = Palette.Bg,
            surface = Palette.BgElev,
            primary = Palette.Amber,
            onPrimary = Palette.Bg,
            secondary = Palette.AmberDeep,
            onBackground = Palette.Text,
            onSurface = Palette.Text
        )
    ) {
        Surface(Modifier.fillMaxSize(), color = Palette.Bg) {
            if (playerChannel != null) {
                PlayerScreen(
                    channel = playerChannel!!,
                    onDismiss = { playerChannel = null },
                    onSourceChanged = { idx -> /* info sumber */ },
                    onEnterPip = { activity?.enterPip() },
                    inPip = activity?.inPip ?: false,
                    onUiMode = { enable -> activity?.playerUiMode(enable) },
                    onSetLandscape = { landscape -> activity?.setLandscape(landscape) }
                )
            } else {
                Column(Modifier.fillMaxSize().background(Palette.Bg)) {
                    // ---- Top bar ----
                    TopBar(
                        onSearch = { search = it },
                        search = search
                    )

                    when (val s = state) {
                        is LoadState.Loading -> LoadingView()
                        is LoadState.Error -> ErrorView(s.message) { scope.launch { load(false) } }
                        is LoadState.Ready -> {
                            val act = activity
                            if (act != null) {
                                Row(Modifier.weight(1f)) {
                                    Column(Modifier.weight(1f)) {
                                        when (tab) {
                                            0 -> HomeScreen(
                                                channels = s.channels, categories = s.categories,
                                                activeCategory = activeCategory,
                                                onCategory = { activeCategory = it },
                                                search = search, favorites = favorites,
                                                onPlay = { ch -> if (ch.sources.isNotEmpty()) { playerChannel = ch; Prefs.setLastPlayedChannelId(act, ch.id) } else snackbar = "Saluran tanpa sumber" },
                                                onToggleFav = { ch -> Prefs.toggleFavorite(act, ch.id); refreshFavorites() }
                                            )
                                            1 -> EventsScreen(channels = s.channels, onPlay = { ch -> if (ch.sources.isNotEmpty()) { playerChannel = ch; Prefs.setLastPlayedChannelId(act, ch.id) } else snackbar = "Saluran tanpa sumber" })
                                            2 -> FavoritesScreen(
                                                channels = s.channels, favorites = favorites,
                                                onPlay = { ch -> if (ch.sources.isNotEmpty()) { playerChannel = ch; Prefs.setLastPlayedChannelId(act, ch.id) } else snackbar = "Saluran tanpa sumber" },
                                                onToggleFav = { ch -> Prefs.toggleFavorite(act, ch.id); refreshFavorites() }
                                            )
                                            else -> SettingsScreen(
                                                serverVersion = s.serverVersion,
                                                onRefresh = { scope.launch { load(true) } },
                                                onClearFav = { Prefs.clearFavorites(act); refreshFavorites(); snackbar = "Semua favorit dihapus" },
                                                snackbar = { snackbar = it }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // ---- Bottom nav ----
                    NavigationBar(containerColor = Palette.BgElev, tonalElevation = 0.dp) {
                        val items = listOf(
                            Triple("Beranda", Icons.Filled.Tv, Icons.Outlined.Tv),
                            Triple("Acara", Icons.Filled.CalendarMonth, Icons.Outlined.CalendarMonth),
                            Triple("Favorit", Icons.Filled.Favorite, Icons.Outlined.FavoriteBorder),
                            Triple("Setelan", Icons.Filled.Settings, Icons.Outlined.Settings)
                        )
                        items.forEachIndexed { i, (label, filled, outlined) ->
                            NavigationBarItem(
                                selected = tab == i,
                                onClick = { tab = i },
                                icon = { Icon(if (tab == i) filled else outlined, contentDescription = label) },
                                label = { Text(label, fontSize = 11.sp) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = Palette.Amber,
                                    selectedTextColor = Palette.Amber,
                                    indicatorColor = Palette.Amber.copy(alpha = 0.15f),
                                    unselectedIconColor = Palette.TextDim,
                                    unselectedTextColor = Palette.TextDim
                                )
                            )
                        }
                    }
                }
            }

            snackbar?.let { msg ->
                Box(
                    Modifier
                        .fillMaxSize()
                        .navigationBarsPadding(),
                    contentAlignment = Alignment.BottomCenter
                ) {
                    Snackbar(
                        containerColor = Palette.CardSoft,
                        contentColor = Palette.Text,
                        modifier = Modifier.padding(16.dp)
                    ) { Text(msg, fontSize = 13.sp) }
                }
                LaunchedEffect(msg) {
                    kotlinx.coroutines.delay(2200)
                    snackbar = null
                }
            }
        }
    }
}
