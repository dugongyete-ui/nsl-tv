package com.nsl.tv.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nsl.tv.R

@Composable
fun TopBar(search: String, onSearch: (String) -> Unit) {
    Column(Modifier.fillMaxWidth().background(Palette.Bg).padding(horizontal = 16.dp, vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Image(
                painter = painterResource(R.mipmap.ic_launcher),
                contentDescription = "Logo NSL TV",
                contentScale = ContentScale.Fit,
                modifier = Modifier.size(34.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text("NSL ", color = Palette.Text, fontWeight = FontWeight.ExtraBold, fontSize = 22.sp)
            Text("TV", color = Palette.Amber, fontWeight = FontWeight.ExtraBold, fontSize = 22.sp)
        }
        Spacer(Modifier.height(10.dp))
        TextField(
            value = search,
            onValueChange = onSearch,
            singleLine = true,
            placeholder = { Text("Cari saluran…", color = Palette.TextDim, fontSize = 14.sp) },
            shape = RoundedCornerShape(14.dp),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Palette.Card,
                unfocusedContainerColor = Palette.Card,
                focusedIndicatorColor = Palette.Amber.copy(alpha = 0.4f),
                unfocusedIndicatorColor = Color.Transparent,
                cursorColor = Palette.Amber,
                focusedTextColor = Palette.Text,
                unfocusedTextColor = Palette.Text
            ),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
fun LoadingView() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            androidx.compose.material3.CircularProgressIndicator(color = Palette.Amber, strokeWidth = 3.dp)
            Spacer(Modifier.height(14.dp))
            Text("Menyambung ke server…", color = Palette.TextDim, fontSize = 14.sp)
        }
    }
}

@Composable
fun ErrorView(message: String, onRetry: () -> Unit) {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("😕", fontSize = 42.sp)
            Spacer(Modifier.height(10.dp))
            Text("Gagal memuat", color = Palette.Text, fontWeight = FontWeight.Bold, fontSize = 17.sp)
            Spacer(Modifier.height(6.dp))
            Text(message, color = Palette.TextDim, fontSize = 13.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            Spacer(Modifier.height(18.dp))
            androidx.compose.material3.Button(
                onClick = onRetry,
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = Palette.Amber, contentColor = Palette.Bg),
                shape = RoundedCornerShape(12.dp)
            ) { Text("Coba Lagi", fontWeight = FontWeight.SemiBold) }
        }
    }
}
