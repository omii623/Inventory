package com.example.inventory.ui.itemdetail

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.inventory.R
import com.example.inventory.ui.components.PhotoImage

/** Lapozható fotók az adatlap tetején (látványterv 3. képernyő); koppintásra teljes képernyős nézet. */
@Composable
fun PhotoPager(photos: List<String>, modifier: Modifier = Modifier) {
    var fullscreenFrom by remember { mutableStateOf<Int?>(null) }
    Surface(color = MaterialTheme.colorScheme.primaryContainer, modifier = modifier.fillMaxWidth().height(280.dp)) {
        if (photos.isEmpty()) {
            PhotoImage(null, Modifier.fillMaxSize(), placeholderSize = 40.dp)
            return@Surface
        }
        val pager = rememberPagerState { photos.size }
        Box {
            HorizontalPager(state = pager, modifier = Modifier.fillMaxSize()) { page ->
                PhotoImage(
                    photos[page],
                    Modifier.fillMaxSize().clickable { fullscreenFrom = page },
                    maxSide = 1440,
                    placeholderSize = 40.dp,
                )
            }
            if (photos.size > 1) PageDots(photos.size, pager.currentPage, Modifier.align(Alignment.BottomCenter).padding(bottom = 14.dp))
        }
    }
    fullscreenFrom?.let { start -> FullscreenPhotos(photos, start, onClose = { fullscreenFrom = null }) }
}

@Composable
private fun PageDots(count: Int, current: Int, modifier: Modifier = Modifier) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically, modifier = modifier) {
        repeat(count) { index ->
            val active = index == current
            Box(
                Modifier
                    .height(6.dp)
                    .width(if (active) 16.dp else 6.dp)
                    .background(Color.White.copy(alpha = if (active) 1f else 0.6f), RoundedCornerShape(3.dp))
            )
        }
    }
}

@Composable
private fun FullscreenPhotos(photos: List<String>, start: Int, onClose: () -> Unit) {
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        val pager = rememberPagerState(initialPage = start) { photos.size }
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            HorizontalPager(state = pager, modifier = Modifier.fillMaxSize()) { page ->
                PhotoImage(photos[page], Modifier.fillMaxSize(), maxSide = 2048, contentScale = ContentScale.Fit, placeholderSize = 40.dp)
            }
            IconButton(onClick = onClose, modifier = Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(8.dp)) {
                Icon(Icons.Filled.Close, stringResource(R.string.close), tint = Color.White)
            }
            if (photos.size > 1) {
                Text(
                    "${pager.currentPage + 1} / ${photos.size}",
                    color = Color.White,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 32.dp),
                )
            }
        }
    }
}
