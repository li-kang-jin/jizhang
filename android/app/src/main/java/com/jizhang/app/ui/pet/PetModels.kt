package com.jizhang.app.ui.pet

/** 可选的宠物形象 */
enum class PetType(val key: String, val label: String, val emoji: String) {
    CAT("cat", "小猫", "🐱"),
    DINO("dino", "小恐龙", "🦖"),
    PIGGY("piggy", "存钱罐", "🐷");

    companion object {
        fun fromKey(key: String?): PetType = entries.firstOrNull { it.key == key } ?: CAT
    }
}

/** 宠物心情，由收支状况驱动 */
enum class PetMood {
    /** 有结余、状态良好 */
    HAPPY,

    /** 普通 */
    NORMAL,

    /** 本月超支，担心 */
    WORRIED,

    /** 达成目标（记账 / 升级） */
    EXCITED
}

/**
 * 宠物状态。全部存在本地，不涉及后端。
 */
data class PetState(
    val name: String = DEFAULT_NAME,
    val type: PetType = PetType.CAT,
    /** 金币：记一笔 +10 */
    val coins: Int = 0,
    /** 连续记账天数 */
    val streakDays: Int = 0,
    /** 最后一次记账日期，格式 yyyy-MM-dd */
    val lastRecordDate: String = ""
) {
    /** 等级：每 50 金币升一级 */
    val level: Int get() = coins / COINS_PER_LEVEL + 1

    /** 当前等级内的进度 0f..1f */
    val levelProgress: Float get() = (coins % COINS_PER_LEVEL) / COINS_PER_LEVEL.toFloat()

    /** 距离下一级还差多少金币 */
    val coinsToNextLevel: Int get() = COINS_PER_LEVEL - (coins % COINS_PER_LEVEL)

    companion object {
        const val DEFAULT_NAME = "小锦锦"
        const val COINS_PER_LEVEL = 50
        const val COINS_PER_RECORD = 10
    }
}
