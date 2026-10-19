package com.jizhang.app.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** 标签是逗号分隔的字符串（中英文逗号都认），解析成列表并去掉空白项 */
fun parseTagList(raw: String?): List<String> =
    raw.orEmpty()
        .split(',', '，')
        .map { it.trim() }
        .filter { it.isNotEmpty() }

/**
 * 账单条目上的标签：`#出差` 这种小胶囊。
 * <p>
 * 最多显示 [max] 个，多出来的收成 `+N` —— 一条账单的标签不该比金额还占地方。
 * 传 [inline] 时只输出纯文本（给紧凑型单行模板用）。
 */
@Composable
fun TagChips(
    tags: List<String>,
    modifier: Modifier = Modifier,
    max: Int = 3
) {
    if (tags.isEmpty()) return
    val shown = tags.take(max)
    val rest = tags.size - shown.size
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        shown.forEach { TagPill("#$it") }
        if (rest > 0) TagPill("+$rest")
    }
}

@Composable
private fun TagPill(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Medium,
        color = MaterialTheme.colorScheme.primary,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .clip(RoundedCornerShape(5.dp))
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f))
            .padding(horizontal = 5.dp, vertical = 1.dp)
    )
}

/** 紧凑模板用：把标签拼成 `#a #b`，没有标签时返回 null */
fun inlineTags(raw: String?): String? {
    val tags = parseTagList(raw)
    return if (tags.isEmpty()) null else tags.joinToString(" ") { "#$it" }
}
