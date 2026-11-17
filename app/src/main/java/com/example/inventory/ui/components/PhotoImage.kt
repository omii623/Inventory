package com.example.inventory.ui.components

import android.graphics.Bitmap
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.inventory.InventoryApplication
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Memóriagyorsítótár a megjelenített képekhez (a memória 1/8-a), kulcs: fájlnév + méret. */
private object BitmapCache {
    private val cache = object : LruCache<String, Bitmap>((Runtime.getRuntime().maxMemory() / 8 / 1024).toInt()) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount / 1024
    }

    fun get(key: String): Bitmap? = cache.get(key)
    fun put(key: String, bitmap: Bitmap) { cache.put(key, bitmap) }
}

/**
 * Tételfotó a belső tárhelyről, háttérszálon betöltve és [maxSide] px-re kicsinyítve.
 * Ha nincs fotó ([fileName] null) vagy még töltődik, halvány helyőrző ikon látszik.
 */
@Composable
fun PhotoImage(
    fileName: String?,
    modifier: Modifier = Modifier,
    maxSide: Int = 256,
    contentScale: ContentScale = ContentScale.Crop,
    placeholderSize: Dp = 24.dp,
    contentDescription: String? = null,
) {
    val storage = (LocalContext.current.applicationContext as InventoryApplication).container.photoStorage
    val bitmap by produceState<Bitmap?>(initialValue = fileName?.let { BitmapCache.get("$it@$maxSide") }, fileName, maxSide) {
        if (fileName == null) { value = null; return@produceState }
        val key = "$fileName@$maxSide"
        value = BitmapCache.get(key) ?: withContext(Dispatchers.IO) {
            storage.loadBitmap(fileName, maxSide)?.also { BitmapCache.put(key, it) }
        }
    }
    val image = bitmap
    if (image != null) {
        Image(image.asImageBitmap(), contentDescription = contentDescription, contentScale = contentScale, modifier = modifier)
    } else {
        Box(modifier, contentAlignment = Alignment.Center) {
            Icon(Icons.Outlined.Image, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(placeholderSize))
        }
    }
}
