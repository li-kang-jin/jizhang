package com.jizhang.app.ui.report

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import com.jizhang.app.ui.common.AppChip
import com.jizhang.app.ui.common.AppFullScreenDialog
import com.jizhang.app.ui.common.categoryColorOf
import com.jizhang.app.ui.common.formatAmount
import com.jizhang.app.ui.common.safeDialogContent
import com.jizhang.app.ui.theme.AppColors
import com.jizhang.app.ui.theme.JizhangTheme
import java.math.BigDecimal
import java.math.RoundingMode

/** 总预算在接口约定中用 0 表示 */
private const val TOTAL_CATEGORY_ID = 0L
private const val TOTAL_CATEGORY_NAME = "总预算"

/** 分 → 输入框里的「元」文本：整数就写整数，别塞一串 .00 进输入框 */
private fun centsToYuanInput(cents: Long): String =
    BigDecimal(cents).movePointLeft(2).stripTrailingZeros().toPlainString()

/**
 * 预算设置页（全屏覆盖层）。
 * <p>
 * 支持设置「账本总预算」或某个支出分类的预算；同一范围重复设置即为修改额度。
 * <p>
 * 为什么是全屏覆盖层而不是 `AlertDialog`：见 [AppFullScreenDialog] 的说明 ——
 * Android 15+ 弹窗窗口被强制 edge-to-edge 且高度 `WRAP_CONTENT`，内容被硬裁、
 * 内部滚动也会失效。**调用点必须在 `MainScreen` 根部**，否则盖不住底部导航栏。
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun BudgetDialog(
    periodLabel: String,
    expenseCategories: List<CategoryStatDto>,
    budget: BudgetOverviewDto?,
    onSave: (categoryId: Long, amountYuan: String, onResult: (String?) -> Unit) -> Unit,
    onDelete: (budgetId: Long) -> Unit,
    onDismiss: () -> Unit,
    /**
     * 本期固定支出里**没被分类预算覆盖**的部分（分）。
     * <p>
     * 建议总预算 = 分类预算合计 + 这个数。房租这类钱常常「分类预算里设了 760、
     * 固定支出里又登记 760」，是同一笔钱，直接相加会重复；所以调用方先做去重
     * （见 [com.jizhang.app.data.repository.uncoveredFixedTotal]）。
     */
    uncoveredFixed: Long = 0L,
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

    // 总预算与分类预算在数据层是两条独立记录，这里只做「建议值」辅助（不自动改写额度）：
    // 建议值 = 分类预算合计 + 本期未到期的固定支出，填得比它少时提醒一句。
    val isTotalScope = selectedCategoryId == TOTAL_CATEGORY_ID
    val categoryItems = budget?.items.orEmpty()
    val categorySum = categoryItems.sumOf { it.amount }
    val suggestTotal = categorySum + uncoveredFixed
    val inputCents = amountText.trim().toBigDecimalOrNull()
        ?.takeIf { it > BigDecimal.ZERO }
        ?.movePointRight(2)
        ?.setScale(0, RoundingMode.HALF_UP)
        ?.toLong()

    fun save() {
        if (saving) return
        saving = true
        errorText = null
        onSave(selectedCategoryId, amountText) { error ->
            saving = false
            if (error == null) {
                onDismiss()
            } else {
                errorText = error
            }
        }
    }

    AppFullScreenDialog(onDismissRequest = onDismiss) {
        // imePadding 在整页根部：键盘弹起时底部按钮行整体上移
        Column(modifier = Modifier.fillMaxSize().imePadding()) {
            TopAppBar(
                title = { Text("设置预算", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )

            // 中部滚动区：顶栏之后的子节点一律 weight(1f)，fillMaxSize 会超出屏幕被裁
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 4.dp)
            ) {
                Text(
                    text = periodLabel,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(16.dp))

                Text(
                    text = "预算范围",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AppChip(
                        label = TOTAL_CATEGORY_NAME,
                        selected = selectedCategoryId == TOTAL_CATEGORY_ID,
                        onClick = {
                            selectedCategoryId = TOTAL_CATEGORY_ID
                            amountText = ""
                            errorText = null
                        }
                    )
                    expenseCategories.forEach { category ->
                        val id = category.categoryId ?: return@forEach
                        // 预算接口只给分类名，按名称取同一套分类色（餐饮橙、交通蓝…）
                        AppChip(
                            label = category.categoryName,
                            selected = selectedCategoryId == id,
                            onClick = {
                                selectedCategoryId = id
                                amountText = ""
                                errorText = null
                            },
                            tint = categoryColorOf(null, null, category.categoryName)
                        )
                    }
                }

                Spacer(Modifier.height(20.dp))
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

                Spacer(Modifier.height(8.dp))
                Text(
                    text = existingAmount?.let { "当前额度 ${formatAmount(it)} 元" }
                        ?: "这个范围还没设置预算",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (isTotalScope && suggestTotal > 0L) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "建议总预算 ${formatAmount(suggestTotal)} 元",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = buildString {
                                    if (categorySum > 0L) {
                                        append("分类预算 ${formatAmount(categorySum)} 元")
                                        if (categoryItems.isNotEmpty()) {
                                            append("（${categoryItems.size} 项）")
                                        }
                                    }
                                    if (uncoveredFixed > 0L) {
                                        if (categorySum > 0L) append(" + ")
                                        append("没算进分类预算的固定支出 ${formatAmount(uncoveredFixed)} 元")
                                    }
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        TextButton(
                            onClick = {
                                amountText = centsToYuanInput(suggestTotal)
                                errorText = null
                            },
                            modifier = Modifier.height(48.dp)
                        ) {
                            Text("填入")
                        }
                    }
                    if (inputCents != null && inputCents < suggestTotal) {
                        Text(
                            text = "比建议额度少 ${formatAmount(suggestTotal - inputCents)} 元：" +
                                "分类可能都没超，加上固定支出总预算却先超了",
                            style = MaterialTheme.typography.labelSmall,
                            color = AppColors.expense
                        )
                    }
                }

                errorText?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = it,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                Spacer(Modifier.height(8.dp))

                if (deletableId != null) {
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
                            },
                            modifier = Modifier.height(48.dp)
                        ) {
                            Text("删除该预算", color = AppColors.expense)
                        }
                    }
                }

                if (onInherit != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Filled.ContentCopy,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.height(18.dp)
                        )
                        TextButton(
                            onClick = {
                                onInherit()
                                onDismiss()
                            },
                            modifier = Modifier.height(48.dp)
                        ) {
                            Text("沿用上一周期")
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))
            }

            // 底部固定区：先 background 再 safeDialogContent，背景才能铺到导航栏底下
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.background)
                    .safeDialogContent()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp)
                ) {
                    Text("取消")
                }
                Button(
                    onClick = { save() },
                    enabled = !saving,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp)
                ) {
                    Text(if (saving) "保存中…" else "保存")
                }
            }
        }
    }
}

/* ==================== 预览 ==================== */

@Preview(name = "预算设置页", showBackground = true, widthDp = 380, heightDp = 720)
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
            uncoveredFixed = 150000,
            onSave = { _, _, _ -> },
            onDelete = { },
            onDismiss = { }
        )
    }
}
