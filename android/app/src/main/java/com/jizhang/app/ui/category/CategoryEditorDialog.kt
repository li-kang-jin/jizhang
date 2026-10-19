package com.jizhang.app.ui.category

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jizhang.app.data.local.entity.LocalCategory
import com.jizhang.app.ui.common.AppChip
import com.jizhang.app.ui.common.CATEGORY_ICON_OPTIONS
import com.jizhang.app.ui.common.CategoryIconOption
import com.jizhang.app.ui.common.categoryColorOf
import com.jizhang.app.ui.common.categoryContentOn
import com.jizhang.app.ui.common.categoryIconOf
import com.jizhang.app.ui.common.categoryKeyOf
import com.jizhang.app.ui.common.categoryTintOn
import com.jizhang.app.ui.theme.AppColors

/** 图标清单里「其他」那一项，也是未知图标的兜底（与 VISUALS 的 key 一致） */
private const val FALLBACK_ICON = "other"

/**
 * 分类的新建 / 编辑弹窗。
 *
 * 账单页（分类管理）与记账页（分类区「＋新建」）共用同一个弹窗，
 * 差别只在 [lockType]：记账页必须锁定收支类型，否则新建的分类不会出现在当前分类列表里。
 *
 * `onSave` 用回调返回结果：`done(null)` 表示成功（由调用方关闭弹窗），
 * `done(错误文案)` 则把错误**留在弹窗里**显示，方便用户改完重试（例如只读成员没权限）。
 */
@Composable
fun CategoryEditorDialog(
    category: LocalCategory? = null,
    defaultType: Int = 1,
    lockType: Boolean = false,
    onDismiss: () -> Unit,
    onSave: (
        name: String,
        type: Int,
        icon: String,
        done: (String?) -> Unit
    ) -> Unit
) {
    var name by remember { mutableStateOf(category?.name.orEmpty()) }
    var type by remember { mutableStateOf(category?.type ?: defaultType) }
    var icon by remember {
        // 老数据里「其他」存 more、「交通」存 directions_bus，先归一化再对清单，
        // 否则编辑已有分类时图标会被重置成兜底那一个
        val initial = categoryKeyOf(category?.icon, category?.name)
        mutableStateOf(CATEGORY_ICON_OPTIONS.firstOrNull { it.key == initial }?.key ?: FALLBACK_ICON)
    }
    var error by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        title = {
            Text(
                text = if (category == null) "新建分类" else "编辑分类",
                fontWeight = FontWeight.SemiBold
            )
        },
        text = {
            // 顺序不能反：heightIn 必须在外层限定「视口高度」，verticalScroll 在内层才能
            // 拿到无限高约束、进而产生滚动余量。写成 verticalScroll().heightIn() 的话，
            // 视口会被内容高度撑满，等于没有可滚的余量 —— 手势划不动就是这个原因。
            Column(
                modifier = Modifier
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        error = null
                    },
                    label = { Text("分类名称") },
                    singleLine = true,
                    isError = error != null,
                    modifier = Modifier.fillMaxWidth()
                )
                error?.let {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = it,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.labelSmall
                    )
                }

                Spacer(Modifier.height(14.dp))
                Text("收支类型", style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.height(6.dp))
                if (lockType) {
                    Text(
                        text = if (type == 1) "支出" else "收入",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AppChip(
                            label = "支出",
                            selected = type == 1,
                            onClick = { type = 1 },
                            tint = AppColors.expense
                        )
                        AppChip(
                            label = "收入",
                            selected = type == 2,
                            onClick = { type = 2 },
                            tint = AppColors.income
                        )
                    }
                }

                Spacer(Modifier.height(14.dp))
                Text("图标", style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.height(4.dp))
                CATEGORY_ICON_OPTIONS.chunked(4).forEach { row ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        row.forEach { option ->
                            IconChoice(
                                option = option,
                                selected = icon == option.key,
                                modifier = Modifier.weight(1f),
                                onClick = { icon = option.key }
                            )
                        }
                        repeat(4 - row.size) {
                            Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !saving,
                onClick = {
                    val trimmed = name.trim()
                    if (trimmed.isEmpty()) {
                        error = "请输入分类名称"
                        return@TextButton
                    }
                    saving = true
                    onSave(trimmed, type, icon) { message ->
                        saving = false
                        error = message
                    }
                }
            ) { Text(if (saving) "保存中…" else "保存") }
        },
        dismissButton = {
            TextButton(enabled = !saving, onClick = onDismiss) { Text("取消") }
        }
    )
}

@Composable
private fun IconChoice(
    option: CategoryIconOption,
    selected: Boolean,
    modifier: Modifier,
    onClick: () -> Unit
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 选择器里就用分类的真实配色预览：选完回到网格，颜色和这里看到的一致
        val optionColor = categoryColorOf(null, option.key)
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(
                    if (selected) {
                        optionColor
                    } else {
                        optionColor.copy(alpha = 0.13f)
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = categoryIconOf(option.key),
                contentDescription = option.label,
                tint = if (selected) {
                    categoryContentOn(optionColor)
                } else {
                    categoryTintOn(optionColor)
                },
                modifier = Modifier.size(18.dp)
            )
        }
        Spacer(Modifier.height(2.dp))
        Text(
            text = option.label,
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
