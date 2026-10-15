package com.jizhang.app.ui.common

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.PhoneIphone
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * 分类图标映射。
 * <p>
 * 服务端 / 本地存的分类图标是一个短标识（如 restaurant），
 * 这里统一映射成 Material 图标，服务端不需要关心客户端用什么图标库。
 */
fun categoryIconOf(key: String?): ImageVector = when (key) {
    "restaurant" -> Icons.Filled.Restaurant
    "bus" -> Icons.Filled.DirectionsBus
    "shopping" -> Icons.Filled.ShoppingCart
    "home" -> Icons.Filled.Home
    "game" -> Icons.Filled.SportsEsports
    "medical" -> Icons.Filled.LocalHospital
    "book" -> Icons.Filled.School
    "phone" -> Icons.Filled.PhoneIphone
    "gift" -> Icons.Filled.CardGiftcard
    "salary" -> Icons.Filled.Payments
    "award" -> Icons.Filled.EmojiEvents
    "trend" -> Icons.Filled.TrendingUp
    else -> Icons.Filled.Category
}
