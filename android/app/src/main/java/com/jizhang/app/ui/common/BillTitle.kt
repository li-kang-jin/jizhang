package com.jizhang.app.ui.common

import com.jizhang.app.data.local.entity.LocalTransaction

/**
 * 账单条目的标题：备注 → 标签 → 分类名 → 收支类型。
 * <p>
 * 标签排在分类名之前，是因为两者回答的不是同一个问题：分类是大类（餐饮 / 购物），
 * 标签才是「这笔钱具体用在哪」（外卖 / 楼下小馆）。只打标签、不写备注时，
 * 若标题退回分类名，同一个分类下的十笔账看起来一模一样，得点进详情才能分辨。
 * <p>
 * 这同时照顾了从别的记账软件迁移过来的老账：那边导出的账单只有备注、没有标签，
 * 新账可能只有标签、没有备注——两边在同一个列表里读起来仍是同一个粒度。
 */
fun billTitle(item: LocalTransaction, categoryName: String?): String {
    item.remark?.takeIf { it.isNotBlank() }?.let { return it }
    val tags = parseTagList(item.tags)
    if (tags.isNotEmpty()) return tags.joinToString(" · ")
    return categoryName?.takeIf { it.isNotBlank() } ?: transactionTypeLabel(item.type)
}

/**
 * 标签已经顶上标题了：列表里的那排标签芯片就不再重复一遍。
 * <p>
 * 同一串字在一行里出现两次很吵；但备注当标题时标签仍要显示——那是额外信息，不是重复。
 */
fun tagsUsedAsTitle(item: LocalTransaction): Boolean =
    item.remark.isNullOrBlank() && parseTagList(item.tags).isNotEmpty()
