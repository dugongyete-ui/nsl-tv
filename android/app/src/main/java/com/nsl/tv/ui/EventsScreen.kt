package com.nsl.tv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nsl.tv.data.Channel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun EventsScreen(channels: List<Channel>, onPlay: (Channel) -> Unit) {
    val events = channels.filter { it.isEvent }.sortedBy { it.eventStartEpoch ?: 0L }
    if (events.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("📅", fontSize = 40.sp)
                Spacer(Modifier.height(10.dp))
                Text("Belum ada acara langsung", color = Palette.Text, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                Spacer(Modifier.height(4.dp))
                Text("Acara olahraga akan tampil di sini", color = Palette.TextDim, fontSize = 13.sp)
            }
        }
        return
    }
    Column(Modifier.fillMaxSize()) {
        Text(
            "Acara Langsung",
            color = Palette.Text,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 18.sp,
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp)
        )
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 150.dp),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(events, key = { it.id }) { ev ->
                Column(
                    Modifier
                        .clip(RoundedCornerShape(18.dp))
                        .background(Palette.Card)
                        .clickable { onPlay(ev) }
                        .padding(12.dp)
                ) {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        ChannelLogo(url = ev.sportIcon ?: ev.logoUrl, name = ev.name, size = 64.dp)
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        ev.name,
                        color = Palette.Text,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        lineHeight = 16.sp
                    )
                    Spacer(Modifier.height(4.dp))
                    ev.league?.let {
                        Text(it, color = Palette.Amber, fontSize = 11.sp, maxLines = 1)
                        Spacer(Modifier.height(2.dp))
                    }
                    ev.eventStartEpoch?.let { t ->
                        val time = SimpleDateFormat("EEE, d MMM • HH:mm", Locale("id")).format(Date(t))
                        Text(time, color = Palette.TextDim, fontSize = 11.sp)
                    }
                }
            }
        }
    }
}
