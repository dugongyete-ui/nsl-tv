package com.nsl.tv

import android.app.PictureInPictureParams
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
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

// ============================================================
// State aplikasi
// ============================================================
sealed interface LoadState {
    data object Loading : LoadState
    data class Ready(val channels: List<Channel>, val categories: List<String>, val serverVersion: String?) : LoadState
    data class Error(val message: String) : LoadState
}

class MainActivity : ComponentActivity() {

    private var pipRequest: (() -> Unit)? = null

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
}

@Composable
fun NslRoot(activity: MainActivity = androidx.compose.ui.platform.LocalContext.current as MainActivity) {
    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf<LoadState>(LoadState.Loading) }
    var tab by remember { mutableStateOf(0) }
    var search by remember { mutableStateOf("") }
    var activeCategory by remember { mutableStateOf("SEMUA") }
    var favorites by remember { mutableStateOf(Prefs.favorites(activity)) }
    var playerChannel by remember { mutableStateOf<Channel?>(null) }
    var snackbar by remember { mutableStateOf<String?>(null) }
    var pipHint by remember { mutableStateOf(false) }

    fun refreshFavorites() { favorites = Prefs.favorites(activity) }

    suspend fun load(showToast: Boolean) {
        state = LoadState.Loading
        try {
            val r = ChannelRepository.get(activity).load(Prefs.autoRefreshOnLaunch(activity))
            state = LoadState.Ready(r.channels, r.categories, r.server?.version)
            if (showToast) snackbar = "Daftar saluran diperbarui"
            // Auto-play saluran terakhir
            if (playerChannel == null && Prefs.autoPlayLast(activity)) {
                Prefs.lastPlayedChannelId(activity)?.let { lastId ->
                    r.channels.firstOrNull { it.id == lastId }?.let { playerChannel = it }
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
                    onEnterPip = { activity.enterPip() },
                    inPip = activity.inPip
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
                            Row(Modifier.weight(1f)) {
                                Column(Modifier.weight(1f)) {
                                    when (tab) {
                                        0 -> HomeScreen(
                                            channels = s.channels, categories = s.categories,
                                            activeCategory = activeCategory,
                                            onCategory = { activeCategory = it },
                                            search = search, favorites = favorites,
                                            onPlay = { ch -> playerChannel = ch; Prefs.setLastPlayedChannelId(activity, ch.id) },
                                            onToggleFav = { ch -> Prefs.toggleFavorite(activity, ch.id); refreshFavorites() }
                                        )
                                        1 -> EventsScreen(channels = s.channels, onPlay = { ch -> playerChannel = ch; Prefs.setLastPlayedChannelId(activity, ch.id) })
                                        2 -> FavoritesScreen(
                                            channels = s.channels, favorites = favorites,
                                            onPlay = { ch -> playerChannel = ch; Prefs.setLastPlayedChannelId(activity, ch.id) },
                                            onToggleFav = { ch -> Prefs.toggleFavorite(activity, ch.id); refreshFavorites() }
                                        )
                                        else -> SettingsScreen(
                                            serverVersion = s.serverVersion,
                                            onRefresh = { scope.launch { load(true) } },
                                            onClearFav = { Prefs.clearFavorites(activity); refreshFavorites(); snackbar = "Semua favorit dihapus" },
                                            snackbar = { snackbar = it }
                                        )
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
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
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
