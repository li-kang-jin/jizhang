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
 *
 * 只有「连续记账天数」这一个指标：它是真实的记账习惯。
 * 之前还记过金币 / 等级，但一个靠记多少笔算出来的数字对用户没有任何用处，已废弃。
 */
data class PetState(
    val name: String = DEFAULT_NAME,
    val type: PetType = PetType.CAT,
    /** 连续记账天数 */
    val streakDays: Int = 0,
    /** 最后一次记账日期，格式 yyyy-MM-dd */
    val lastRecordDate: String = ""
) {
    companion object {
        const val DEFAULT_NAME = "小锦锦"
    }
}
