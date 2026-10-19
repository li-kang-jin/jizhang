package com.jizhang.app.ui.common

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.Apartment
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.CardGiftcard
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.Checkroom
import androidx.compose.material.icons.outlined.ChildCare
import androidx.compose.material.icons.outlined.ContentCut
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material.icons.outlined.CreditScore
import androidx.compose.material.icons.outlined.Devices
import androidx.compose.material.icons.outlined.DirectionsBus
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.Fastfood
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material.icons.outlined.Flight
import androidx.compose.material.icons.outlined.Gavel
import androidx.compose.material.icons.outlined.Handyman
import androidx.compose.material.icons.outlined.HealthAndSafety
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LocalBar
import androidx.compose.material.icons.outlined.LocalCafe
import androidx.compose.material.icons.outlined.LocalGroceryStore
import androidx.compose.material.icons.outlined.LocalHospital
import androidx.compose.material.icons.outlined.LocalShipping
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.Pets
import androidx.compose.material.icons.outlined.QrCode
import androidx.compose.material.icons.outlined.Receipt
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.Redeem
import androidx.compose.material.icons.outlined.Replay
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.RocketLaunch
import androidx.compose.material.icons.outlined.Savings
import androidx.compose.material.icons.outlined.ShoppingBag
import androidx.compose.material.icons.outlined.Smartphone
import androidx.compose.material.icons.outlined.SmokingRooms
import androidx.compose.material.icons.outlined.SportsEsports
import androidx.compose.material.icons.outlined.Subscriptions
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material.icons.outlined.TrendingUp
import androidx.compose.material.icons.outlined.VolunteerActivism
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material.icons.outlined.WorkOutline
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * 分类图标体系。
 *
 * 三件事在这里统一收口，其他页面只管调：
 * 1. 图标全部用 **Outlined（线性）**：记账页是密排的九宫格，实心图标堆在一起会糊成色块，
 *    线性图标在 38dp 的圆里更透气，也更接近「记账工具」而不是「游戏」的观感。
 * 2. 每个分类有自己的**低饱和色**：UI 用色相区分分类，靠色相记忆比靠图形更快
 *    （餐饮橙、交通蓝、购物紫…），但饱和度压得很低，20 个格子铺满也不会花。
 * 3. 兼容历史数据：服务端早期写的是 Material 原名（`directions_bus` / `shopping_cart`），
 *    本地新建写的是短名（`bus` / `shopping`），两套都要认，否则整屏会退化成一个默认图标。
 */
data class CategoryVisual(val icon: ImageVector, val color: Color)

/**
 * 图标 + 配色的总表。key 就是库里存的短标识。
 *
 * 配色的选择原则：同色系不相邻（餐饮橙 / 交通蓝 / 购物紫 / 居住青），
 * 黄绿这类高明度色只给"娱乐、水果"等少数分类，避免整屏发亮。
 */
private val VISUALS: Map<String, CategoryVisual> = linkedMapOf(
    // —— 支出
    "restaurant" to CategoryVisual(Icons.Outlined.Restaurant, Color(0xFFFF8A65)),
    "fastfood" to CategoryVisual(Icons.Outlined.Fastfood, Color(0xFFFFA270)),
    "cafe" to CategoryVisual(Icons.Outlined.LocalCafe, Color(0xFFB08968)),
    "grocery" to CategoryVisual(Icons.Outlined.LocalGroceryStore, Color(0xFF7BC96F)),
    "shopping" to CategoryVisual(Icons.Outlined.ShoppingBag, Color(0xFFB07CFF)),
    "clothing" to CategoryVisual(Icons.Outlined.Checkroom, Color(0xFFF48FB1)),
    "digital" to CategoryVisual(Icons.Outlined.Devices, Color(0xFF78909C)),
    "bus" to CategoryVisual(Icons.Outlined.DirectionsBus, Color(0xFF5C9DFF)),
    "car" to CategoryVisual(Icons.Outlined.DirectionsCar, Color(0xFF4E8FE8)),
    "travel" to CategoryVisual(Icons.Outlined.Flight, Color(0xFF5AB0E8)),
    "express" to CategoryVisual(Icons.Outlined.LocalShipping, Color(0xFFF0A35E)),
    "home" to CategoryVisual(Icons.Outlined.Home, Color(0xFF4FC3A1)),
    "rent" to CategoryVisual(Icons.Outlined.Apartment, Color(0xFF3FB8A0)),
    "water" to CategoryVisual(Icons.Outlined.WaterDrop, Color(0xFF5BC8E8)),
    "electric" to CategoryVisual(Icons.Outlined.Bolt, Color(0xFFFFB65C)),
    "game" to CategoryVisual(Icons.Outlined.SportsEsports, Color(0xFFFFC24B)),
    "sport" to CategoryVisual(Icons.Outlined.FitnessCenter, Color(0xFFFF9E7A)),
    "medical" to CategoryVisual(Icons.Outlined.LocalHospital, Color(0xFFFF7B7B)),
    "insurance" to CategoryVisual(Icons.Outlined.HealthAndSafety, Color(0xFF4DB6AC)),
    "book" to CategoryVisual(Icons.Outlined.MenuBook, Color(0xFF7C8BFF)),
    "subscription" to CategoryVisual(Icons.Outlined.Subscriptions, Color(0xFF90A4AE)),
    "phone" to CategoryVisual(Icons.Outlined.Smartphone, Color(0xFF4FC3F7)),
    "gift" to CategoryVisual(Icons.Outlined.Redeem, Color(0xFFFF8FC7)),
    "beauty" to CategoryVisual(Icons.Outlined.ContentCut, Color(0xFFE58BC4)),
    "baby" to CategoryVisual(Icons.Outlined.ChildCare, Color(0xFFFFB3C1)),
    "pet" to CategoryVisual(Icons.Outlined.Pets, Color(0xFFC6A15B)),
    "bar" to CategoryVisual(Icons.Outlined.LocalBar, Color(0xFFA1887F)),
    "smoking" to CategoryVisual(Icons.Outlined.SmokingRooms, Color(0xFF8D8D8D)),
    "repay" to CategoryVisual(Icons.Outlined.CreditScore, Color(0xFF8E9AAF)),
    "repair" to CategoryVisual(Icons.Outlined.Handyman, Color(0xFF9E9E9E)),
    "sidejob" to CategoryVisual(Icons.Outlined.WorkOutline, Color(0xFF6C8EBF)),
    "startup" to CategoryVisual(Icons.Outlined.RocketLaunch, Color(0xFFE07BE0)),
    "tax" to CategoryVisual(Icons.Outlined.Receipt, Color(0xFFB39DDB)),
    "donate" to CategoryVisual(Icons.Outlined.VolunteerActivism, Color(0xFFFF8A80)),
    "fine" to CategoryVisual(Icons.Outlined.Gavel, Color(0xFFEF9A9A)),
    // —— 收入
    "salary" to CategoryVisual(Icons.Outlined.Payments, Color(0xFF5FD3A0)),
    "award" to CategoryVisual(Icons.Outlined.EmojiEvents, Color(0xFFFFB65C)),
    "invest" to CategoryVisual(Icons.Outlined.TrendingUp, Color(0xFF4DD0E1)),
    "interest" to CategoryVisual(Icons.Outlined.Savings, Color(0xFF4DB6AC)),
    "redpacket" to CategoryVisual(Icons.Outlined.CardGiftcard, Color(0xFFFF7B7B)),
    "refund" to CategoryVisual(Icons.Outlined.Replay, Color(0xFF80CBC4)),
    "reimburse" to CategoryVisual(Icons.Outlined.ReceiptLong, Color(0xFF9FA8DA)),
    // —— 兜底
    "other" to CategoryVisual(Icons.Outlined.MoreHoriz, Color(0xFF9AA4B2))
)

/** 服务端用的 Material 原名 → 本地的短标识 */
private val ALIASES: Map<String, String> = mapOf(
    "directions_bus" to "bus",
    "directions_car" to "car",
    "shopping_cart" to "shopping",
    "shopping_bag" to "shopping",
    "sports_esports" to "game",
    "sports_bar" to "bar",
    "local_hospital" to "medical",
    "local_cafe" to "cafe",
    "local_grocery_store" to "grocery",
    "local_shipping" to "express",
    "local_bar" to "bar",
    "school" to "book",
    "menu_book" to "book",
    "phone_iphone" to "phone",
    "smartphone" to "phone",
    "card_giftcard" to "gift",
    "redeem" to "redpacket",
    "more_horiz" to "other",
    "more" to "other",
    "payments" to "salary",
    "emoji_events" to "award",
    "trending_up" to "invest",
    "trend" to "invest",
    "water_drop" to "water",
    "bolt" to "electric",
    "apartment" to "rent",
    "house" to "home",
    "work" to "sidejob",
    "work_outline" to "sidejob",
    "rocket_launch" to "startup",
    "health_and_safety" to "insurance",
    "flight" to "travel",
    "fitness_center" to "sport",
    "content_cut" to "beauty",
    "child_care" to "baby",
    "credit_score" to "repay",
    "handyman" to "repair",
    "receipt_long" to "reimburse",
    "savings" to "interest",
    "checkroom" to "clothing",
    "devices" to "digital",
    "subscriptions" to "subscription",
    "volunteer_activism" to "donate",
    "smoking_rooms" to "smoking",
    "fastfood" to "fastfood",
    "restaurant_menu" to "restaurant"
)

/**
 * 名称关键词兜底。
 *
 * 用户自建的分类（"水果""水电费""副业收支""医疗保险和失业保险"）通常没有 icon 字段，
 * 与其让它们长成同一个占位图标，不如按名字猜一个——顺序有意义，先命中的更具体。
 */
private val NAME_RULES: List<Pair<String, List<String>>> = listOf(
    "restaurant" to listOf("早餐", "午餐", "晚餐", "餐饮", "外卖", "食堂", "夜宵", "餐", "饭"),
    "cafe" to listOf("咖啡", "奶茶", "饮品"),
    "grocery" to listOf("水果", "买菜", "菜场", "生鲜", "零食", "超市"),
    "shopping" to listOf("购物", "日用", "百货"),
    "clothing" to listOf("服装", "衣", "鞋"),
    "digital" to listOf("数码", "电子", "电脑"),
    "bus" to listOf("交通", "地铁", "公交", "打车", "出租", "通勤"),
    "car" to listOf("油费", "停车", "洗车", "违章", "汽车"),
    "travel" to listOf("旅行", "旅游", "机票", "酒店"),
    "express" to listOf("快递", "物流"),
    "rent" to listOf("房租", "物业", "房贷", "租金"),
    "home" to listOf("居住", "家具", "装修"),
    "water" to listOf("水费", "自来水"),
    "electric" to listOf("电费", "水电", "燃气", "取暖"),
    "game" to listOf("娱乐", "游戏", "电影", "唱歌", "会员"),
    "sport" to listOf("健身", "运动", "球"),
    // 「保险」要排在「医疗」前：像「医疗保险和失业保险」这种名字，保险才是它的语义
    "insurance" to listOf("保险", "社保", "医保"),
    "medical" to listOf("医疗", "看病", "药"),
    "book" to listOf("教育", "学习", "书", "培训", "学费"),
    "subscription" to listOf("订阅", "服务费"),
    "phone" to listOf("通讯", "话费", "手机", "流量", "宽带"),
    "gift" to listOf("人情", "礼品", "礼物", "往来"),
    "beauty" to listOf("理发", "美容", "护肤"),
    "baby" to listOf("育儿", "宝宝", "母婴", "奶粉"),
    "pet" to listOf("宠物", "猫", "狗"),
    "bar" to listOf("酒"),
    "smoking" to listOf("烟"),
    "repay" to listOf("还款", "信用卡还款", "借"),
    "repair" to listOf("维修", "修理"),
    "tax" to listOf("税", "手续费"),
    "donate" to listOf("捐赠", "捐款"),
    "fine" to listOf("罚款"),
    "salary" to listOf("工资", "薪水", "薪资"),
    "award" to listOf("奖金", "年终奖", "提成"),
    "invest" to listOf("投资", "理财", "股票", "基金"),
    "sidejob" to listOf("副业", "兼职"),
    "startup" to listOf("创业"),
    "interest" to listOf("利息", "分红"),
    "redpacket" to listOf("红包"),
    "refund" to listOf("退款", "退货"),
    "reimburse" to listOf("报销"),
    "balance" to listOf("余额", "调整"),
    "other" to listOf("其他", "其它")
)

/** 库里存的标识 → 规范短名（认不出就给 other） */
fun categoryKeyOf(icon: String?, name: String? = null): String {
    val raw = icon?.trim()?.lowercase()?.replace(Regex("[^a-z0-9]+"), "_")?.trim('_')
    if (!raw.isNullOrBlank()) {
        val key = ALIASES[raw] ?: raw
        if (VISUALS.containsKey(key)) {
            return key
        }
    }
    val label = name?.trim().orEmpty()
    if (label.isNotEmpty()) {
        NAME_RULES.forEach { (key, keywords) ->
            if (keywords.any { label.contains(it) }) {
                return key
            }
        }
    }
    return "other"
}

/**
 * 分类图标。
 *
 * @param icon 库里存的标识，可以是短名也可以是服务端的 Material 原名
 * @param name 分类名，标识认不出时用它兜底（自建分类基本都走这条）
 */
fun categoryIconOf(icon: String?, name: String? = null): ImageVector =
    (VISUALS[categoryKeyOf(icon, name)] ?: VISUALS.getValue("other")).icon

/**
 * 分类配色：优先用库里存的色值，缺失就按图标/名称取一套预设色。
 *
 * @param color 形如 `#FF7043` 的字符串，解析失败视为缺失
 */
fun categoryColorOf(color: String?, icon: String? = null, name: String? = null): Color =
    parseHexColor(color) ?: (VISUALS[categoryKeyOf(icon, name)] ?: VISUALS.getValue("other")).color

/** 未选中时图标用的色：高明度色（黄/绿）压暗一档，否则在浅底上看不清 */
fun categoryTintOn(color: Color): Color =
    if (color.luminance() > 0.68f) lerp(color, Color.Black, 0.34f) else color

/** 实心底上用哪种图标色：亮色底配深字，暗色底配白字，保证任何分类色都可读 */
fun categoryContentOn(color: Color): Color =
    if (color.luminance() > 0.62f) lerp(color, Color.Black, 0.72f) else Color.White

private fun parseHexColor(raw: String?): Color? {
    val value = raw?.trim()?.removePrefix("#") ?: return null
    if (value.length != 6) {
        return null
    }
    val rgb = value.toLongOrNull(16) ?: return null
    return Color(0xFF000000L or rgb)
}

/**
 * 新建 / 编辑分类时可挑的图标。
 *
 * key 必须与 [VISUALS] 的分支对应，否则会退化成默认图标。
 */
val CATEGORY_ICON_OPTIONS: List<CategoryIconOption> = listOf(
    "restaurant" to "餐饮",
    "fastfood" to "外卖",
    "cafe" to "咖啡",
    "grocery" to "买菜",
    "shopping" to "购物",
    "clothing" to "服饰",
    "digital" to "数码",
    "bus" to "交通",
    "car" to "用车",
    "travel" to "旅行",
    "express" to "快递",
    "home" to "居住",
    "rent" to "房租",
    "water" to "水费",
    "electric" to "电费",
    "game" to "娱乐",
    "sport" to "运动",
    "medical" to "医疗",
    "insurance" to "保险",
    "book" to "学习",
    "subscription" to "订阅",
    "phone" to "通讯",
    "gift" to "人情",
    "beauty" to "美容",
    "baby" to "育儿",
    "pet" to "宠物",
    "bar" to "酒水",
    "smoking" to "烟",
    "repay" to "还款",
    "repair" to "维修",
    "tax" to "税费",
    "donate" to "捐赠",
    "fine" to "罚款",
    "salary" to "工资",
    "award" to "奖金",
    "invest" to "理财",
    "sidejob" to "副业",
    "startup" to "创业",
    "interest" to "利息",
    "redpacket" to "红包",
    "refund" to "退款",
    "reimburse" to "报销",
    "balance" to "余额",
    "other" to "其他"
).map { (key, label) -> CategoryIconOption(key, label) }

/** 一个可选的分类图标 */
data class CategoryIconOption(val key: String, val label: String)

/**
 * 账户图标映射。
 *
 * 与分类同一套约定：库里存短标识，客户端翻译成图标。账户是"钱从哪来"，
 * 所以统一用钱包/银行卡/二维码这类具象符号，不用抽象图形。
 */
fun accountIconOf(key: String?): ImageVector = when (key) {
    "wallet" -> Icons.Outlined.AccountBalanceWallet
    "cash" -> Icons.Outlined.Payments
    "card" -> Icons.Outlined.CreditCard
    "bank" -> Icons.Outlined.AccountBalance
    "online" -> Icons.Outlined.QrCode
    "invest" -> Icons.Outlined.TrendingUp
    else -> Icons.Outlined.AccountBalanceWallet
}

/**
 * 账户配色：按「钱放在哪」的语义取色，而不是按图标。
 *
 * 现金绿、储蓄卡蓝、信用卡紫、支付宝蓝、微信绿、投资橙、其他灰 ——
 * 这样一屏账户扫下来，颜色本身就是分组信息（资产 / 负债 / 投资）。
 *
 * @param type 账户类型，信用卡优先用紫色，避免和储蓄卡同图标又同色
 */
fun accountColorOf(icon: String?, type: Int): Color = when (type) {
    3 -> Color(0xFFB07CFF)
    1 -> Color(0xFF5FD3A0)
    2 -> Color(0xFF4A90E2)
    4 -> Color(0xFF4FC3F7)
    5 -> Color(0xFF5FD3A0)
    6 -> Color(0xFFFFB65C)
    else -> when (icon) {
        "cash" -> Color(0xFF5FD3A0)
        "card" -> Color(0xFF4A90E2)
        "bank" -> Color(0xFF7C8BFF)
        "online" -> Color(0xFF4FC3F7)
        "invest" -> Color(0xFFFFB65C)
        "wallet" -> Color(0xFF5C9DFF)
        else -> Color(0xFF9AA4B2)
    }
}

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

/** 默认分类图标（按名称取，种子数据与自建分类共用） */
internal fun defaultCategoryIconOf(name: String): String = categoryKeyOf(null, name)

/** 默认分类色（按名称取，导入的旧数据没有色值时用） */
internal fun defaultCategoryColorOf(name: String): Color =
    (VISUALS[categoryKeyOf(null, name)] ?: VISUALS.getValue("other")).color
