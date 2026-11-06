package com.nsl.tv.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nsl.tv.data.Channel

@Composable
fun FavoritesScreen(
    channels: List<Channel>,
    favorites: Set<String>,
    onPlay: (Channel) -> Unit,
    onToggleFav: (Channel) -> Unit
) {
    val favChannels = channels.filter { favorites.contains(it.id) }
    Column(Modifier.fillMaxSize()) {
        Text(
            "Favorit",
            color = Palette.Text,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 18.sp,
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp)
        )
        if (favChannels.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("💛", fontSize = 40.sp)
                    Spacer(Modifier.height(10.dp))
                    Text("Belum ada favorit", color = Palette.Text, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                    Spacer(Modifier.height(4.dp))
                    Text("Tahan pada kartu saluran untuk menandai", color = Palette.TextDim, fontSize = 13.sp)
                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 96.dp),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(favChannels, key = { it.id }) { ch ->
                    ChannelCard(
                        channel = ch,
                        favorite = true,
                        onPlay = { onPlay(ch) },
                        onToggleFav = { onToggleFav(ch) }
                    )
                }
            }
        }
    }
}
