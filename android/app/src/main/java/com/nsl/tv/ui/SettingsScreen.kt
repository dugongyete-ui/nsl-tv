package com.nsl.tv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import com.nsl.tv.NslApp
import com.nsl.tv.data.Prefs

@Composable
private fun SettingToggle(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Palette.Card)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = Palette.Text, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            Spacer(Modifier.height(2.dp))
            Text(subtitle, color = Palette.TextDim, fontSize = 12.sp)
        }
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Palette.Amber,
                checkedTrackColor = Palette.Amber.copy(alpha = 0.35f),
                uncheckedThumbColor = Palette.TextDim,
                uncheckedTrackColor = Palette.CardSoft
            )
        )
    }
}

@Composable
private fun SettingButton(title: String, onClick: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Palette.Card)
            .clickable(onClick = onClick)
            .padding(vertical = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(title, color = Palette.Text, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
    }
}


/** Ringkas laporan panjang: tampilkan baris pertama + baris terakhir. */
private fun String.lineCount(max: Int): String {
    val lines = lines()
    return if (lines.size <= max * 2) this
    else lines.take(max).joinToString("\n") + "\n…\n" + lines.takeLast(max).joinToString("\n")
}

@Composable
fun SettingsScreen(
    serverVersion: String?,
    onRefresh: () -> Unit,
    onClearFav: () -> Unit,
    snackbar: (String) -> Unit
) {
    val context = LocalContext.current
    var autoLast by remember { mutableStateOf(Prefs.autoPlayLast(context)) }
    var autoBoot by remember { mutableStateOf(Prefs.autoStartBoot(context)) }
    var autoRefresh by remember { mutableStateOf(Prefs.autoRefreshOnLaunch(context)) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            "Setelan",
            color = Palette.Text,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 18.sp,
            modifier = Modifier.padding(vertical = 6.dp)
        )

        SettingToggle(
            "Lanjutkan saluran terakhir",
            "Otomatis membuka saluran yang terakhir ditonton",
            autoLast
        ) { autoLast = it; Prefs.setAutoPlayLast(context, it) }

        SettingToggle(
            "Mulai otomatis saat perangkat dinyalakan",
            "Buka NSL TV otomatis (cocok untuk TV Box)",
            autoBoot
        ) { autoBoot = it; Prefs.setAutoStartBoot(context, it) }

        SettingToggle(
            "Perbarui daftar saat dibuka",
            "Ambil daftar saluran terbaru setiap aplikasi dibuka",
            autoRefresh
        ) { autoRefresh = it; Prefs.setAutoRefreshOnLaunch(context, it) }

        SettingButton("Segarkan Daftar Saluran") {
            onRefresh()
            snackbar("Memuat saluran terbaru…")
        }
        SettingButton("Hapus Semua Favorit") { onClearFav() }

        // ---- Diagnostik: laporan error terakhir (bila pernah terjadi crash) ----
        val crashReport = remember { NslApp.lastCrashReport(context) }
        if (!crashReport.isNullOrBlank()) {
            Text(
                "Laporan error terakhir",
                color = Palette.TextDim,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
                modifier = Modifier.padding(top = 6.dp)
            )
            Text(
                crashReport.lineCount(8),
                color = Palette.TextDim,
                fontSize = 11.sp,
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(Palette.CardSoft)
                    .padding(10.dp)
            )
            SettingButton("Salin Laporan Error") {
                runCatching {
                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                    cm?.setPrimaryClip(ClipData.newPlainText("NSL TV Crash", crashReport))
                    snackbar("Laporan error disalin ke papan klip")
                }
            }
            SettingButton("Hapus Laporan Error") {
                NslApp.clearCrashReport(context)
                snackbar("Laporan error dihapus")
            }
        }

        Spacer(Modifier.height(8.dp))
        Text(
            "NSL TV v${com.nsl.tv.data.Auth.APP_VERSION}" + (serverVersion?.let { "  •  Server $it" } ?: ""),
            color = Palette.TextDim,
            fontSize = 12.sp,
            modifier = Modifier.align(Alignment.CenterHorizontally).padding(bottom = 16.dp)
        )
    }
}
