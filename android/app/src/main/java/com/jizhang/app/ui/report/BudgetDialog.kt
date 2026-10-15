package com.jizhang.app.ui.report

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.jizhang.app.data.remote.dto.BudgetDto
import com.jizhang.app.data.remote.dto.BudgetOverviewDto
import com.jizhang.app.data.remote.dto.CategoryStatDto
import com.jizhang.app.ui.common.formatAmount
import com.jizhang.app.ui.theme.AppColors
import com.jizhang.app.ui.theme.JizhangTheme

/** 总预算在接口约定中用 0 表示 */
private const val TOTAL_CATEGORY_ID = 0L
private const val TOTAL_CATEGORY_NAME = "总预算"

/**
 * 预算设置弹窗。
 * <p>
 * 支持设置「账本总预算」或某个支出分类的预算；同一范围重复设置即为修改额度。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BudgetDialog(
    periodLabel: String,
    expenseCategories: List<CategoryStatDto>,
    budget: BudgetOverviewDto?,
    onSave: (categoryId: Long, amountYuan: String, onResult: (String?) -> Unit) -> Unit,
    onDelete: (budgetId: Long) -> Unit,
    onDismiss: () -> Unit,
    /** 打开时默认选中的预算范围，0 表示总预算 */
    initialCategoryId: Long = TOTAL_CATEGORY_ID,
    /** 提供后才显示「沿用上一周期」入口 */
    onInherit: (() -> Unit)? = null
) {
    var selectedCategoryId by remember(initialCategoryId) { mutableStateOf(initialCategoryId) }
    var amountText by remember { mutableStateOf("") }
    var errorText by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }

    // 当前选中范围已设置的预算：总预算不在 items 里，需要单独取值
    val existingAmount: Long? = remember(selectedCategoryId, budget) {
        if (selectedCategoryId == TOTAL_CATEGORY_ID) {
            budget?.takeIf { it.hasBudget }?.totalBudget
        } else {
            budget?.items?.firstOrNull { it.categoryId == selectedCategoryId }?.amount
        }
    }

    val deletableId: Long? = remember(selectedCategoryId, budget) {
        if (selectedCategoryId == TOTAL_CATEGORY_ID) {
            // 只有真正存在的总预算才可删除
            if (budget?.hasBudget == true) budget.totalBudgetId ?: -1L else null
        } else {
            budget?.items?.firstOrNull { it.categoryId == selectedCategoryId }?.id
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("设置预算 · $periodLabel", fontWeight = FontWeight.SemiBold) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 400.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = "预算范围",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))

                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(
                        selected = selectedCategoryId == TOTAL_CATEGORY_ID,
                        onClick = {
                            selectedCategoryId = TOTAL_CATEGORY_ID
                            amountText = ""
                            errorText = null
                        },
                        label = { Text(TOTAL_CATEGORY_NAME) }
                    )
                    expenseCategories.forEach { category ->
                        val id = category.categoryId ?: return@forEach
                        FilterChip(
                            selected = selectedCategoryId == id,
                            onClick = {
                                selectedCategoryId = id
                                amountText = ""
                                errorText = null
                            },
                            label = { Text(category.categoryName) }
                        )
                    }
                }

                existingAmount?.let {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = "当前额度 ${formatAmount(it)} 元",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(Modifier.height(14.dp))
                OutlinedTextField(
                    value = amountText,
                    onValueChange = {
                        amountText = it
                        errorText = null
                    },
                    label = { Text("预算金额") },
                    prefix = { Text("¥") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )

                errorText?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = it,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                if (deletableId != null) {
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Filled.DeleteOutline,
                            contentDescription = null,
                            tint = AppColors.expense,
                            modifier = Modifier.height(18.dp)
                        )
                        TextButton(
                            onClick = {
                                if (deletableId > 0) {
                                    onDelete(deletableId)
                                    onDismiss()
                                } else {
                                    errorText = "该预算尚未同步，请稍后重试"
                                }
                            }
                        ) {
                            Text("删除该预算", color = AppColors.expense)
                        }
                    }
                }

                if (onInherit != null) {
                    Spacer(Modifier.height(2.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Filled.ContentCopy,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.height(18.dp)
                        )
                        TextButton(onClick = {
                            onInherit()
                            onDismiss()
                        }) {
                            Text("沿用上一周期")
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !saving,
                onClick = {
                    saving = true
                    onSave(selectedCategoryId, amountText) { error ->
                        saving = false
                        if (error == null) {
                            onDismiss()
                        } else {
                            errorText = error
                        }
                    }
                }
            ) {
                Text("保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

/* ==================== 预览 ==================== */

@Preview(name = "预算设置弹窗", showBackground = true, widthDp = 380, heightDp = 620)
@Composable
private fun BudgetDialogPreview() {
    JizhangTheme {
        BudgetDialog(
            periodLabel = "2026 年 10 月",
            expenseCategories = listOf(
                CategoryStatDto(categoryId = 1L, categoryName = "餐饮"),
                CategoryStatDto(categoryId = 2L, categoryName = "交通"),
                CategoryStatDto(categoryId = 3L, categoryName = "购物"),
                CategoryStatDto(categoryId = 4L, categoryName = "居住"),
                CategoryStatDto(categoryId = 5L, categoryName = "娱乐")
            ),
            budget = BudgetOverviewDto(
                period = "2026-10",
                hasBudget = true,
                totalBudgetId = 99L,
                totalBudget = 500000,
                totalUsed = 1234500,
                items = listOf(
                    BudgetDto(
                        id = 1L, categoryId = 1L, categoryName = "餐饮",
                        amount = 100000, used = 125000, exceeded = true
                    )
                )
            ),
            onSave = { _, _, _ -> },
            onDelete = { },
            onDismiss = { }
        )
    }
}
