package com.jizhang.app.ui.common

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.PhoneIphone
import androidx.compose.material.icons.filled.QrCode
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

/**
 * 账户图标映射。
 * <p>
 * 与分类图标同一套约定：库里存短标识，客户端负责翻译成 Material 图标，
 * 服务端不需要知道我们用什么图标库。
 */
fun accountIconOf(key: String?): ImageVector = when (key) {
    "wallet" -> Icons.Filled.AccountBalanceWallet
    "cash" -> Icons.Filled.Payments
    "card" -> Icons.Filled.CreditCard
    "bank" -> Icons.Filled.AccountBalance
    "online" -> Icons.Filled.QrCode
    "invest" -> Icons.Filled.TrendingUp
    else -> Icons.Filled.Category
}

/** 一个可选的分类图标 */
data class CategoryIconOption(val key: String, val label: String)

/** 新建 / 编辑账户时可挑的图标，key 必须与 [accountIconOf] 的分支对应 */
val ACCOUNT_ICON_OPTIONS: List<CategoryIconOption> = listOf(
    CategoryIconOption("wallet", "钱包"),
    CategoryIconOption("cash", "现金"),
    CategoryIconOption("card", "储蓄卡"),
    CategoryIconOption("bank", "银行"),
    CategoryIconOption("online", "网络支付"),
    CategoryIconOption("invest", "投资"),
    CategoryIconOption("other", "其他")
)

/** 账户类型 → 默认图标（新建账户时按类型预选一个） */
fun defaultAccountIconOf(type: Int): String = when (type) {
    1 -> "cash"
    2 -> "card"
    3 -> "card"
    4, 5 -> "online"
    6 -> "invest"
    else -> "wallet"
}

/**
 * 新建 / 编辑分类时可挑的图标。
 * <p>
 * key 必须与 [categoryIconOf] 的分支对应，否则会全部退化成默认图标。
 */
val CATEGORY_ICON_OPTIONS: List<CategoryIconOption> = listOf(
    CategoryIconOption("restaurant", "餐饮"),
    CategoryIconOption("bus", "交通"),
    CategoryIconOption("shopping", "购物"),
    CategoryIconOption("home", "居住"),
    CategoryIconOption("game", "娱乐"),
    CategoryIconOption("medical", "医疗"),
    CategoryIconOption("book", "学习"),
    CategoryIconOption("phone", "通讯"),
    CategoryIconOption("gift", "礼物"),
    CategoryIconOption("salary", "工资"),
    CategoryIconOption("award", "奖金"),
    CategoryIconOption("trend", "理财"),
    CategoryIconOption("category", "其他")
)
