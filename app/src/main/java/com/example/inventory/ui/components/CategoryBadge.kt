package com.example.inventory.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Chair
import androidx.compose.material.icons.filled.Checkroom
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.Diamond
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Kitchen
import androidx.compose.material.icons.filled.Laptop
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PedalBike
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.TabletAndroid
import androidx.compose.material.icons.filled.Toys
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.Watch
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.inventory.domain.model.CategoryIcon

/**
 * A választható kategóriaikonok (a cashApp mintájára, leltárhoz igazítva). A kulcs kerül az adatbázisba –
 * **meglévő kulcsot ne nevezz át**, újat bármikor hozzá lehet adni. A sorrend = a választóban megjelenő sorrend.
 */
object CategoryIcons {
    val all: List<Pair<String, ImageVector>> = listOf(
        "laptop" to Icons.Filled.Laptop,
        "computer" to Icons.Filled.Computer,
        "phone" to Icons.Filled.PhoneAndroid,
        "tablet" to Icons.Filled.TabletAndroid,
        "watch" to Icons.Filled.Watch,
        "jewelry" to Icons.Filled.Diamond,
        "camera" to Icons.Filled.PhotoCamera,
        "headphones" to Icons.Filled.Headphones,
        "tv" to Icons.Filled.Tv,
        "games" to Icons.Filled.SportsEsports,
        "tools" to Icons.Filled.Build,
        "car" to Icons.Filled.DirectionsCar,
        "bike" to Icons.Filled.PedalBike,
        "sport" to Icons.Filled.FitnessCenter,
        "book" to Icons.AutoMirrored.Filled.MenuBook,
        "clothes" to Icons.Filled.Checkroom,
        "bag" to Icons.Filled.ShoppingBag,
        "furniture" to Icons.Filled.Chair,
        "kitchen" to Icons.Filled.Kitchen,
        "toys" to Icons.Filled.Toys,
        "music" to Icons.Filled.MusicNote,
        "art" to Icons.Filled.Palette,
        "collectible" to Icons.Filled.EmojiEvents,
        "gift" to Icons.Filled.CardGiftcard,
        "box" to Icons.Filled.Inventory2,
        "other" to Icons.Filled.Category,
    )

    private val byKey = all.toMap()

    fun vector(key: String): ImageVector? = byKey[key]
}

/** Kategóriákhoz választható színek (a cashApp palettája). */
object CategoryPalette {
    val colors: List<Long> = listOf(
        0xFFEF5350, 0xFFEC407A, 0xFFAB47BC, 0xFF7E57C2,
        0xFF5C6BC0, 0xFF29B6F6, 0xFF00ACC1, 0xFF26A69A,
        0xFF2E7D32, 0xFF66BB6A, 0xFFFFA726, 0xFFFF7043,
        0xFF8D6E63, 0xFF78909C, 0xFF9E9E9E,
    )
}

/**
 * Színes kör a kategória ikonjával vagy betűjével.
 * Ha az ikon ismeretlen / nincs megadva, a név első betűje jelenik meg.
 */
@Composable
fun CategoryBadge(
    icon: String?,
    color: Color,
    name: String?,
    modifier: Modifier = Modifier,
    size: Dp = 32.dp,
) {
    Box(
        modifier.size(size).background(color.copy(alpha = 0.18f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        CategorySymbol(icon, color, name, size)
    }
}

/** Csak a jel (ikon vagy betű) háttér nélkül. */
@Composable
fun CategorySymbol(icon: String?, tint: Color, name: String?, size: Dp = 32.dp) {
    val parsed = CategoryIcon.parse(icon)
    val vector = (parsed as? CategoryIcon.Vector)?.let { CategoryIcons.vector(it.key) }
    if (vector != null) {
        Icon(vector, contentDescription = null, tint = tint, modifier = Modifier.size(size * 0.58f))
    } else {
        val text = (parsed as? CategoryIcon.Letter)?.text ?: name?.take(1)?.uppercase() ?: "?"
        Text(
            text,
            color = tint,
            fontWeight = FontWeight.Bold,
            fontSize = (size.value * if (text.length > 1) 0.36f else 0.45f).sp,
            maxLines = 1,
        )
    }
}
