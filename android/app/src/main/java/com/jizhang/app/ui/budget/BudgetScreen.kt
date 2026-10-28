package com.jizhang.app.ui.budget

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jizhang.app.data.local.entity.LocalRecurringExpense
import com.jizhang.app.data.remote.dto.BudgetDto
import com.jizhang.app.data.remote.dto.BudgetOverviewDto
import com.jizhang.app.data.repository.BudgetAdvice
import com.jizhang.app.data.repository.BudgetAdvisor
import com.jizhang.app.data.repository.BudgetConfidence
import com.jizhang.app.data.repository.BudgetCycle
import com.jizhang.app.data.repository.FixedReservation
import com.jizhang.app.data.repository.uncoveredFixedTotal
import com.jizhang.app.ui.common.AppChip
import com.jizhang.app.ui.common.formatAmount
import com.jizhang.app.ui.theme.AppColors
import kotlin.math.roundToInt

/**
 * 预算页：周期切换、总预算进度、分类预算、超支提示。
 */
@Composable
fun BudgetScreen(
    bookId: Long,
    onOpenRecurring: () -> Unit,
    /**
     * 打开「设置预算」覆盖层，参数是预算范围（0 表示总预算）。
     * 覆盖层本身挂在 MainScreen 根部 —— 塞在本页内部会盖不住底部导航栏，还会被裁剪。
     */
    onOpenBudgetEditor: (Long) -> Unit,
    /**
     * 外部数据版本号：导入 / 清空账单后由 MainScreen +1。
     * 本页 ViewModel 是 Activity 级的，切 Tab 回来 `bind()` 会早退，这里显式重算一次。
     */
    dataVersion: Int = 0,
    viewModel: BudgetViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(bookId) { viewModel.bind(bookId) }

    // 导入 / 清空账单后重算本期「已花 / 剩余」
    LaunchedEffect(dataVersion) {
        if (dataVersion > 0) {
            viewModel.load()
        }
    }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeMessage()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                PeriodBar(
                    state = state,
                    onCycle = viewModel::switchCycle,
                    onPrev = { viewModel.switchPeriod(-1) },
                    onNext = { viewModel.switchPeriod(1) },
                    onCurrent = viewModel::backToCurrent
                )
            }
            if (state.inheritedCount > 0) {
                item { InheritHint(state.inheritedCount) }
            }
            item {
                TotalBudgetCard(
                    overview = state.overview,
                    reservation = state.reservation,
                    onEdit = { onOpenBudgetEditor(0L) }
                )
            }
            item {
                BudgetAdviceCard(
                    advice = state.advice,
                    loading = state.adviceLoading,
                    savingsRate = state.savingsRate,
                    onGenerate = viewModel::suggestBudget,
                    onApplyAll = viewModel::applyAdvice,
                    onApplyItem = viewModel::applyAdviceItem,
                    onRateChange = viewModel::setSavingsRate,
                    onDismiss = viewModel::dismissAdvice
                )
            }
            // 趋势图只在设了总预算时才有意义：没有额度就谈不上超支概率
            state.forecast?.let { forecast ->
                item { BudgetTrendCard(forecast) }
            }
            item {
                FixedExpenseCard(
                    recurring = state.recurring,
                    reservation = state.reservation,
                    // 已在分类预算里给过额度的部分：只提示、不再计入建议总预算
                    coveredByCategoryBudget = state.reservation.total - uncoveredFixedTotal(
                        state.overview?.items.orEmpty(),
                        state.reservation
                    ),
                    onManage = onOpenRecurring
                )
            }
            item {
                CategoryBudgetCard(
                    overview = state.overview,
                    onEdit = { categoryId -> onOpenBudgetEditor(categoryId) }
                )
            }
            item { TipsCard() }
        }

        ExtendedFloatingActionButton(
            onClick = { onOpenBudgetEditor(0L) },
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            icon = { Icon(Icons.Filled.Savings, contentDescription = null) },
            text = { Text("设置预算") },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
        )

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}

/** 周期选择 + 时间前后翻页 */
@Composable
private fun PeriodBar(
    state: BudgetViewModel.UiState,
    onCycle: (BudgetCycle) -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onCurrent: () -> Unit
) {
    Column {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BudgetCycle.entries.forEach { cycle ->
                AppChip(
                    label = cycle.label,
                    selected = state.cycle == cycle,
                    onClick = { onCycle(cycle) }
                )
            }
        }

        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onPrev) {
                Icon(Icons.Filled.ChevronLeft, contentDescription = "上一周期")
            }
            Text(
                text = state.periodLabel,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            if (!state.isCurrent) {
                Spacer(Modifier.width(4.dp))
                // 触摸目标按 48dp 给：原来只有两行小字，手指点不准
                TextButton(
                    onClick = onCurrent,
                    modifier = Modifier.height(48.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp)
                ) {
                    Text("回到本期", style = MaterialTheme.typography.labelMedium)
                }
            }
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onNext, enabled = state.canGoNext) {
                Icon(
                    Icons.Filled.ChevronRight,
                    contentDescription = "下一周期",
                    tint = if (state.canGoNext) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.outline
                    }
                )
            }
        }
    }
}

/**
 * 智能预算建议卡片。
 *
 * 收起态只放一句「按什么算」，点一下才算 —— 预算页的核心仍然是当前进度，
 * 上来就塞八行建议会把它挤到屏幕外。算完再展开成可逐条采用的清单。
 */
@Composable
private fun BudgetAdviceCard(
    advice: BudgetAdvice?,
    loading: Boolean,
    savingsRate: Int,
    onGenerate: () -> Unit,
    onApplyAll: () -> Unit,
    onApplyItem: (Long) -> Unit,
    onRateChange: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    CardBox {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "智能建议",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "按近 ${BudgetAdvisor.HISTORY_MONTHS} 个月账单 + $savingsRate% 储蓄率推算",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            when {
                loading -> CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp
                )

                advice == null -> TextButton(
                    onClick = onGenerate,
                    modifier = Modifier.height(48.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp)
                ) {
                    Text("算一下")
                }

                else -> TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.height(48.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp)
                ) {
                    Text("收起")
                }
            }
        }

        if (advice == null) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = "只做建议，不会自动改你现有的预算。至少需要一个月以上的完整账单。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            return@CardBox
        }

        // 目标储蓄率：直接改「支出上限 = 收入 × (1 − 储蓄率)」，所以放在最上面
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(10, 20, 30, 40).forEach { rate ->
                AppChip(
                    label = "$rate%",
                    selected = savingsRate == rate,
                    onClick = { onRateChange(rate) }
                )
            }
        }

        if (advice.items.isEmpty()) {
            Spacer(Modifier.height(10.dp))
            Text(
                text = advice.note ?: "还没有足够的支出记录",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            return@CardBox
        }

        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "建议总预算",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "${formatAmount(advice.totalFen)} 元",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                advice.incomeFen?.let { income ->
                    Text(
                        text = "收入基准 ${formatAmount(income)} 元",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                advice.ceilingFen?.let { ceiling ->
                    Text(
                        text = "支出上限 ${formatAmount(ceiling)} 元",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.height(2.dp))
                ConfidenceTag(advice.confidence)
            }
        }

        Spacer(Modifier.height(10.dp))
        advice.items.forEach { item ->
            Row(
                modifier = Modifier.padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = item.name,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(Modifier.width(6.dp))
                        ConfidenceTag(item.confidence, compact = true)
                    }
                    Text(
                        text = item.reason,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    text = formatAmount(item.amountFen),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
                TextButton(
                    onClick = { onApplyItem(item.categoryId) },
                    modifier = Modifier.height(40.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp)
                ) {
                    Text("采用", style = MaterialTheme.typography.labelMedium)
                }
            }
        }

        advice.note?.let { note ->
            Spacer(Modifier.height(6.dp))
            Text(
                text = note,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Button(
                onClick = onApplyAll,
                modifier = Modifier.weight(1f)
            ) {
                Text("全部采用")
            }
            Spacer(Modifier.width(8.dp))
            TextButton(onClick = onGenerate) {
                Text("重算")
            }
        }
    }
}

/** 可信度标签：给用户一个「该信多少」的提示，而不是让数字显得比实际更确定 */
@Composable
private fun ConfidenceTag(confidence: BudgetConfidence, compact: Boolean = false) {
    val color = when (confidence) {
        BudgetConfidence.HIGH -> AppColors.income
        BudgetConfidence.MEDIUM -> AppColors.accent
        BudgetConfidence.LOW -> MaterialTheme.colorScheme.outline
    }
    val label = when (confidence) {
        BudgetConfidence.HIGH -> if (compact) "可信" else "可信度高"
        BudgetConfidence.MEDIUM -> "参考"
        BudgetConfidence.LOW -> "样本少"
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = color
        )
    }
}

@Composable
private fun InheritHint(count: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(AppColors.accent.copy(alpha = 0.14f))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "本期已自动沿用上一周期的 $count 条预算，可直接调整。",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun TotalBudgetCard(
    overview: BudgetOverviewDto?,
    reservation: FixedReservation,
    onEdit: () -> Unit
) {
    CardBox {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "总预算",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.weight(1f))
            TextButton(
                onClick = onEdit,
                modifier = Modifier.height(48.dp),
                contentPadding = PaddingValues(horizontal = 12.dp)
            ) {
                Text(if (overview?.hasBudget == true) "调整" else "去设置")
            }
        }

        Spacer(Modifier.height(10.dp))

        if (overview == null || !overview.hasBudget) {
            Text(
                text = "还没设置预算。设一个额度，超支时这里会提醒你。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            // 有分类预算 / 固定支出却没总预算时，两者之和就是最合理的默认值。
            // 房租这类两边都登记的钱要先去重，否则建议值会把这笔钱算两遍。
            val categorySum = overview?.items.orEmpty().sumOf { it.amount }
            val uncovered = uncoveredFixedTotal(overview?.items.orEmpty(), reservation)
            val suggestTotal = categorySum + uncovered
            if (suggestTotal > 0L) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = buildString {
                        append("建议总预算 ${formatAmount(suggestTotal)} 元 = ")
                        if (categorySum > 0L) append("分类 ${formatAmount(categorySum)} 元")
                        if (uncovered > 0L) {
                            if (categorySum > 0L) append(" + ")
                            append("没算进分类的固定支出 ${formatAmount(uncovered)} 元")
                        }
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(Modifier.height(12.dp))
            Text(
                text = "本周期已支出 ${formatAmount(overview?.monthExpense ?: 0)} 元",
                style = MaterialTheme.typography.bodyMedium
            )
            return@CardBox
        }

        val ratio = (overview.usedRatio ?: 0.0).toFloat().coerceIn(0f, 1f)
        val barColor = if (overview.exceeded) AppColors.expense else AppColors.accent

        // 预测刻度：按当前消耗速度，期末会花到总预算的哪个位置
        val forecastRatio = overview.forecastExpense
            ?.takeIf { overview.totalBudget > 0L }
            ?.let { (it.toFloat() / overview.totalBudget).coerceIn(0f, 1f) }
        val showForecastMark = forecastRatio != null && forecastRatio > ratio + 0.01f
        // 今天还能不能花：日均可用减去今天已经花掉的
        val todayRemaining = (overview.dailyAvailable - overview.todayExpense).coerceAtLeast(0L)

        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = formatAmount(overview.totalUsed),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = if (overview.exceeded) AppColors.expense else MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text = "/ ${formatAmount(overview.totalBudget)} 元",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = "${((overview.usedRatio ?: 0.0) * 100).roundToInt()}%",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = barColor
            )
        }

        Spacer(Modifier.height(8.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
        ) {
            LinearProgressIndicator(
                progress = { ratio },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp)),
                color = barColor,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )

            // 还没到期的固定支出：在进度条上补一段浅色，表示这些额度已经被占住。
            // 它们不自动记账，所以不计入「已用」，但真实能花的钱必须扣掉 —— 光靠文字说明容易漏看。
            if (reservation.total > 0L && overview.totalBudget > 0L) {
                val usedFraction = overview.totalUsed.toFloat() / overview.totalBudget
                val reservedFraction =
                    ((overview.totalUsed + reservation.total).toFloat() / overview.totalBudget)
                        .coerceIn(0f, 1f)
                if (reservedFraction > usedFraction) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(reservedFraction)
                            .height(8.dp),
                        contentAlignment = Alignment.CenterEnd
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth((reservedFraction - usedFraction) / reservedFraction)
                                .height(8.dp)
                                .background(AppColors.accent.copy(alpha = 0.45f))
                        )
                    }
                }
            }

            if (showForecastMark && forecastRatio != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(forecastRatio)
                        .height(8.dp),
                    contentAlignment = Alignment.CenterEnd
                ) {
                    Box(
                        Modifier
                            .width(3.dp)
                            .height(8.dp)
                            .background(MaterialTheme.colorScheme.onSurfaceVariant)
                    )
                }
            }
        }

        // 一行三个数：剩余是主角，日均可用 / 今天还能花跟在后面。
        // 原来铺了两张三列小卡片（剩余 / 日均可用 / 剩余天数 + 今日已花 / 今天还能花 / 固定支出预留），
        // 同一个「每天能花多少」被讲了两遍，字号还一样大，反而看不出重点。
        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Bottom
        ) {
            StatItem(
                label = "剩余",
                value = "${formatAmount(overview.remaining)} 元",
                color = if (overview.exceeded) AppColors.expense else AppColors.income,
                modifier = Modifier.weight(1.15f),
                emphasized = true
            )
            StatItem(
                label = "日均可用",
                value = "${formatAmount(overview.dailyAvailable)} 元",
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            StatItem(
                label = "今天还能花",
                value = "${formatAmount(todayRemaining)} 元",
                color = if (todayRemaining == 0L) AppColors.expense else AppColors.income,
                modifier = Modifier.weight(1f)
            )
        }

        // 次要信息压成一行灰色小字；「预计期末」交给下面的趋势卡，
        // 这里不再用旧的线性外推，免得同一个「期末支出」出现两个数字
        Spacer(Modifier.height(8.dp))
        Text(
            text = "剩余 ${overview.daysLeft} 天 · 今日已花 ${formatAmount(overview.todayExpense)} 元",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        // 固定支出：不自动记账，所以「已用」里看不到它们；总预算必须显式扣一遍，并给出扣完后的真实结论
        if (reservation.total > 0L) {
            val afterReserved = overview.remaining - reservation.total
            val shortfall = afterReserved < 0L
            Spacer(Modifier.height(10.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                        if (shortfall) {
                            AppColors.expense.copy(alpha = 0.12f)
                        } else {
                            AppColors.accent.copy(alpha = 0.12f)
                        }
                    )
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Text(
                    text = "本期还有 ${reservation.items.size} 笔固定支出未扣" +
                        "（${formatAmount(reservation.total)} 元），不计入「已用」",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = if (shortfall) {
                        "扣掉后已超预算 ${formatAmount(-afterReserved)} 元"
                    } else {
                        "扣掉后实际可花 ${formatAmount(afterReserved)} 元"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (shortfall) AppColors.expense else AppColors.income
                )
            }
        }

        // 只留「结论」（超支 / 可能超支）；正常情况的预测数字已经在上面那行了
        val prediction = predictionText(overview)
        if (prediction != null) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = prediction.first,
                style = MaterialTheme.typography.labelMedium,
                color = prediction.second
            )
        }

    }
}

/**
 * 事实层面的结论：已经超支。预测类结论（可能会超多少）交给趋势卡的概率与分位，
 * 两边各说一个数只会让人不知道该信哪个。
 */
@Composable
private fun predictionText(overview: BudgetOverviewDto): Pair<String, Color>? =
    if (overview.status == BudgetOverviewDto.STATUS_OVER) {
        "已超支 ${formatAmount(overview.totalUsed - overview.totalBudget)} 元" to AppColors.expense
    } else {
        null
    }

/** 固定支出卡片：只做预算内预留，不自动记账 */
@Composable
private fun FixedExpenseCard(
    recurring: List<LocalRecurringExpense>,
    reservation: FixedReservation,
    /** 本期固定支出里已包含在分类预算中的金额（分） */
    coveredByCategoryBudget: Long,
    onManage: () -> Unit
) {
    CardBox {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "固定支出",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.weight(1f))
            TextButton(
                onClick = onManage,
                modifier = Modifier.height(48.dp),
                contentPadding = PaddingValues(horizontal = 12.dp)
            ) {
                Text("管理")
            }
        }

        Spacer(Modifier.height(10.dp))

        if (recurring.isEmpty()) {
            Text(
                text = "房租、房贷、会员费这类每期都要花的钱，登记成固定支出后，" +
                    "本期还没到期的部分会先从预算里预留出来，不会把「每天可花」挤成 0。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            return@CardBox
        }

        val enabledCount = recurring.count { it.enabled }
        Text(
            text = "生效中 $enabledCount 项 · 本期预留 ${formatAmount(reservation.total)} 元",
            style = MaterialTheme.typography.bodyMedium
        )
        if (coveredByCategoryBudget > 0L) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = "其中 ${formatAmount(coveredByCategoryBudget)} 元已经算在分类预算里了，" +
                    "不会再重复加进总预算建议",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (reservation.items.isEmpty()) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = "本期没有还没到期的固定支出",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            return@CardBox
        }

        Spacer(Modifier.height(8.dp))
        reservation.items.take(3).forEach { item ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = item.expense.name,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "${item.nextDate.monthValue}/${item.nextDate.dayOfMonth} 扣 " +
                        "${formatAmount(item.amount)} 元",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (reservation.items.size > 3) {
            Spacer(Modifier.height(3.dp))
            Text(
                text = "还有 ${reservation.items.size - 3} 笔本期预留",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun CategoryBudgetCard(overview: BudgetOverviewDto?, onEdit: (Long) -> Unit) {
    CardBox {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "分类预算",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.weight(1f))
            TextButton(
                onClick = { onEdit(0L) },
                modifier = Modifier.height(48.dp),
                contentPadding = PaddingValues(horizontal = 12.dp)
            ) {
                Text("添加")
            }
        }

        Spacer(Modifier.height(10.dp))

        val items = overview?.items.orEmpty()
        if (items.isEmpty()) {
            Text(
                text = "还没有分类预算。给「餐饮」「购物」这类容易超支的分类单独设个额度。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            return@CardBox
        }

        // 分类额度各自独立，合计只是参考：超过总预算时明确说出来，别让用户自己去加
        val categorySum = items.sumOf { it.amount }
        val totalBudget = overview?.takeIf { it.hasBudget }?.totalBudget ?: 0L
        val overflow = if (totalBudget > 0L) categorySum - totalBudget else 0L
        Text(
            text = buildString {
                append("共 ${items.size} 项，合计 ${formatAmount(categorySum)} 元")
                if (overflow > 0L) {
                    append("，比总预算多 ${formatAmount(overflow)} 元")
                }
            },
            style = MaterialTheme.typography.labelSmall,
            color = if (overflow > 0L) {
                AppColors.expense
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }
        )

        Spacer(Modifier.height(10.dp))

        items.forEachIndexed { index, item ->
            if (index > 0) Spacer(Modifier.height(12.dp))
            CategoryBudgetRow(item = item, onClick = { onEdit(item.categoryId) })
        }
    }
}

@Composable
private fun CategoryBudgetRow(item: BudgetDto, onClick: () -> Unit) {
    val ratio = (item.usedRatio ?: 0.0).toFloat().coerceIn(0f, 1f)
    val barColor = if (item.exceeded) AppColors.expense else AppColors.accent

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 2.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(item.categoryName, style = MaterialTheme.typography.bodyMedium)
            if (item.exceeded) {
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "超支",
                    style = MaterialTheme.typography.labelSmall,
                    color = AppColors.expense
                )
            }
            Spacer(Modifier.weight(1f))
            Text(
                text = "${formatAmount(item.used)} / ${formatAmount(item.amount)}",
                style = MaterialTheme.typography.labelSmall,
                color = if (item.exceeded) {
                    AppColors.expense
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
        }
        Spacer(Modifier.height(6.dp))
        LinearProgressIndicator(
            progress = { ratio },
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp)),
            color = barColor,
            trackColor = MaterialTheme.colorScheme.surfaceVariant
        )
    }
}

@Composable
private fun TipsCard() {
    CardBox {
        Text(
            text = "预算说明",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "· 总预算统计本期全部支出，分类预算只统计该分类\n" +
                "· 进入新周期时会自动沿用上一周期的额度，可随时调整\n" +
                "· 超支时总预算与对应分类都会标红提醒\n" +
                "· 预算数据保存在本地，断网也能正常查看",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** 统一卡片容器：大圆角 + 无描边，靠留白分层 */
@Composable
private fun CardBox(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(16.dp)
    ) {
        content()
    }
}

@Composable
private fun StatItem(
    label: String,
    value: String,
    color: Color,
    modifier: Modifier,
    /** 主指标（剩余）：字号放大一级，形成主次层级 */
    emphasized: Boolean = false
) {
    Column(modifier = modifier) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = value,
            style = if (emphasized) {
                MaterialTheme.typography.titleLarge
            } else {
                MaterialTheme.typography.bodyMedium
            },
            color = color,
            fontWeight = if (emphasized) FontWeight.Bold else FontWeight.SemiBold
        )
    }
}
