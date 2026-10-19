package com.jizhang.app.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import com.jizhang.app.ui.theme.AppColors

/** 类型配色：支出红 / 收入绿 / 转账走中性色 */
@Composable
fun typeColor(type: Int): Color = when (type) {
    1 -> AppColors.expense
    2 -> AppColors.income
    else -> MaterialTheme.colorScheme.onSurface
}

/**
 * 分类图标徽标：有分类就按分类的图标 + 分类色，没有分类（转账 / 无分类）才退回收支色。
 * <p>
 * 账单页、日历页与记账页共用一套「分类即色相」的约定，同一个「餐饮」在哪里都是橙色餐具，
 * 这样用户靠颜色和图形就能扫出分类，不用读文字。
 */
@Composable
fun CategoryBadge(type: Int, icon: String?, size: Dp, name: String? = null, color: String? = null) {
    val hasCategory = icon != null || name != null
    val tint = if (hasCategory) categoryTintOn(categoryColorOf(color, icon, name)) else typeColor(type)
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(
                if (hasCategory) categoryColorOf(color, icon, name).copy(alpha = 0.13f)
                else tint.copy(alpha = 0.12f)
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = categoryIconOf(icon, name),
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(size * 0.5f)
        )
    }
}

/** 分类徽标的重载：直接传分类实体，避免每个调用点都拆 icon / name / color */
@Composable
fun CategoryBadge(type: Int, category: com.jizhang.app.data.local.entity.LocalCategory?, size: Dp) {
    CategoryBadge(type = type, icon = category?.icon, size = size, name = category?.name, color = category?.color)
}
