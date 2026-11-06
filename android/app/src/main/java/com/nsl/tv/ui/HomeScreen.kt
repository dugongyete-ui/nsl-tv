package com.nsl.tv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nsl.tv.data.Channel

@Composable
private fun CategoryChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(if (selected) Palette.Amber else Palette.Card)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 7.dp)
    ) {
        Text(
            label,
            color = if (selected) Palette.Bg else Palette.TextDim,
            fontSize = 12.5.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ChannelCard(
    channel: Channel,
    favorite: Boolean,
    onPlay: () -> Unit,
    onToggleFav: () -> Unit,
    trailingBadge: (@Composable () -> Unit)? = null
) {
    Column(
        Modifier
            .clip(RoundedCornerShape(18.dp))
            .background(Palette.Card)
            .combinedClickable(onClick = onPlay, onLongClick = onToggleFav)
            .padding(vertical = 12.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box {
            ChannelLogo(url = channel.logoUrl, name = channel.name, size = 56.dp)
            if (favorite) {
                Icon(
                    Icons.Filled.Favorite,
                    contentDescription = "Favorit",
                    tint = Palette.Red,
                    modifier = Modifier.align(Alignment.TopEnd).size(14.dp)
                )
            }
            trailingBadge?.invoke()
        }
        Spacer(Modifier.height(8.dp))
        Text(
            channel.name,
            color = Palette.Text,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
fun HomeScreen(
    channels: List<Channel>,
    categories: List<String>,
    activeCategory: String,
    onCategory: (String) -> Unit,
    search: String,
    favorites: Set<String>,
    onPlay: (Channel) -> Unit,
    onToggleFav: (Channel) -> Unit
) {
    val filtered = channels.filter { c ->
        (search.isBlank() || c.name.contains(search, ignoreCase = true)) &&
            (activeCategory == "SEMUA" || c.groupTitle == activeCategory)
    }

    Column(Modifier.fillMaxSize()) {
        // ---- Chips kategori ----
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp)
        ) {
            items(listOf("SEMUA") + categories.filter { it != "⭐ FAVORITES" }) { cat ->
                CategoryChip(cat, selected = activeCategory == cat) { onCategory(cat) }
            }
        }

        Text(
            "${filtered.size} saluran",
            color = Palette.TextDim,
            fontSize = 12.sp,
            modifier = Modifier.padding(horizontal = 18.dp)
        )
        Spacer(Modifier.height(6.dp))

        if (filtered.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Tidak ada saluran cocok", color = Palette.TextDim, fontSize = 14.sp)
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 96.dp),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(filtered, key = { it.id }) { ch ->
                    ChannelCard(
                        channel = ch,
                        favorite = favorites.contains(ch.id),
                        onPlay = { onPlay(ch) },
                        onToggleFav = { onToggleFav(ch) }
                    )
                }
            }
        }
    }
}
