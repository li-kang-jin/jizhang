package com.jizhang.app.ui.report

import androidx.compose.foundation.Canvas
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
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.tooling.preview.Preview
import com.jizhang.app.data.remote.dto.AccountAssetDto
import com.jizhang.app.data.remote.dto.BudgetOverviewDto
import com.jizhang.app.data.remote.dto.CategoryStatDto
import com.jizhang.app.data.remote.dto.MemberReportDto
import com.jizhang.app.data.remote.dto.ReportOverviewDto
import com.jizhang.app.data.remote.dto.TrendItemDto
import com.jizhang.app.data.repository.BudgetPeriods
import com.jizhang.app.ui.common.formatAmount
import com.jizhang.app.ui.theme.AppColors
import com.jizhang.app.ui.theme.JizhangTheme
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberStart
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberColumnCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.core.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.core.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.core.cartesian.data.columnSeries
import com.patrykandpatrick.vico.core.cartesian.data.lineSeries
import java.time.YearMonth
import kotlin.math.roundToInt

@Composable
fun ReportScreen(
    bookId: Long,
    shared: Boolean = false,
    viewModel: ReportViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var showBudgetDialog by remember { mutableStateOf(false) }

    LaunchedEffect(bookId, shared) { viewModel.bind(bookId, shared) }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeMessage()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item { MonthSwitcher(state, viewModel::switchMonth, viewModel::backToCurrentMonth) }
            item { OverviewCard(state, onRefresh = viewModel::load) }
            // 家庭账本专属：谁花了多少
            if (state.shared) {
                item { MemberCard(state.members) }
            }
            item { BudgetCard(state.budget, onEdit = { showBudgetDialog = true }) }
            item { CategoryCard(state, onSwitch = viewModel::switchCategoryType) }
            item { TrendCard(state.trend) }
            item { AssetCard(state) }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }

    if (showBudgetDialog) {
        BudgetDialog(
            periodLabel = BudgetPeriods.label(state.month),
            expenseCategories = state.expenseCategories,
            budget = state.budget,
            onSave = viewModel::saveBudget,
            onDelete = viewModel::deleteBudget,
            onDismiss = { showBudgetDialog = false }
        )
    }
}

/** 月份切换：默认当前月，不允许翻到未来 */
@Composable
private fun MonthSwitcher(
    state: ReportViewModel.UiState,
    onSwitch: (Int) -> Unit,
    onBackToday: () -> Unit
) {
    val month = remember(state.month) { YearMonth.parse(state.month) }
    val isCurrent = state.month == YearMonth.now().toString()

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = { onSwitch(-1) }) {
            Icon(Icons.Filled.ChevronLeft, contentDescription = "上个月")
        }
        Text(
            text = "${month.year} 年 ${month.monthValue} 月",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        if (!isCurrent) {
            Spacer(Modifier.width(8.dp))
            Text(
                text = "回到本月",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .clickable(onClick = onBackToday)
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            )
        }
        Spacer(Modifier.weight(1f))
        IconButton(onClick = { onSwitch(1) }, enabled = state.canGoNext) {
            Icon(
                Icons.Filled.ChevronRight,
                contentDescription = "下个月",
                tint = if (state.canGoNext) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.outline
                }
            )
        }
    }
}

/** 预算进度卡片。未设置预算时展示引导文案 */
@Composable
private fun BudgetCard(budget: BudgetOverviewDto?, onEdit: () -> Unit) {
    CardBox {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "本月预算",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = if (budget?.hasBudget == true) "调整" else "去设置",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .clickable(onClick = onEdit)
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            )
        }

        Spacer(Modifier.height(10.dp))

        if (budget == null || !budget.hasBudget) {
            Text(
                text = "还没设置预算。设一个额度，超支时这里会提醒你。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            return@CardBox
        }

        val ratio = (budget.usedRatio ?: 0.0).toFloat().coerceIn(0f, 1f)
        val barColor = if (budget.exceeded) AppColors.expense else AppColors.accent

        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = formatAmount(budget.totalUsed),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = if (budget.exceeded) AppColors.expense else MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text = "/ ${formatAmount(budget.totalBudget)} 元",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = "${((budget.usedRatio ?: 0.0) * 100).roundToInt()}%",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = barColor
            )
        }

        Spacer(Modifier.height(8.dp))
        LinearProgressIndicator(
            progress = { ratio },
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp)),
            color = barColor,
            trackColor = MaterialTheme.colorScheme.surfaceVariant
        )

        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth()) {
            StatItem(
                label = "剩余",
                value = "${formatAmount(budget.remaining)} 元",
                color = if (budget.exceeded) AppColors.expense else AppColors.income,
                modifier = Modifier.weight(1f)
            )
            StatItem(
                label = "日均可用",
                value = "${formatAmount(budget.dailyAvailable)} 元",
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            StatItem(
                label = "剩余天数",
                value = "${budget.daysLeft} 天",
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
        }

        if (budget.overruns.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            Text(
                text = "已超支：" + budget.overruns.joinToString("、") { it.categoryName },
                style = MaterialTheme.typography.labelSmall,
                color = AppColors.expense
            )
        }

        if (budget.items.isNotEmpty()) {
            Spacer(Modifier.height(14.dp))
            Text(
                text = "分类预算",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(6.dp))
            budget.items.forEach { item ->
                val itemRatio = (item.usedRatio ?: 0.0).toFloat().coerceIn(0f, 1f)
                Column(Modifier.padding(vertical = 4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(item.categoryName, style = MaterialTheme.typography.bodyMedium)
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
                    Spacer(Modifier.height(4.dp))
                    LinearProgressIndicator(
                        progress = { itemRatio },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(5.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = if (item.exceeded) AppColors.expense else AppColors.accent,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun StatItem(label: String, value: String, color: Color, modifier: Modifier) {
    Column(modifier = modifier) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(2.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = color,
            fontWeight = FontWeight.SemiBold
        )
    }
}

/**
 * 家庭账本专属卡片：这个月家里每个人花了多少。
 *
 * 「家庭成员可以看到所有人的收支」在这里落到具体数字上 —— 谁记的账、花了多少一目了然。
 */
@Composable
private fun MemberCard(report: MemberReportDto?) {
    CardBox {
        Text(
            text = "家庭成员收支",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(4.dp))

        if (report == null || report.items.isEmpty()) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = "这个月还没有家庭成员记账",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            return@CardBox
        }

        Spacer(Modifier.height(8.dp))
        report.items.forEachIndexed { index, item ->
            if (index > 0) {
                Spacer(Modifier.height(10.dp))
            }
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = item.label,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        text = "${item.count} 笔",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.height(2.dp))
                Row {
                    Text(
                        text = "支出 ${formatAmount(item.expense)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = AppColors.expense
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = "收入 ${formatAmount(item.income)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = AppColors.income
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = "结余 ${formatAmount(item.balance)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
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
private fun OverviewCard(state: ReportViewModel.UiState, onRefresh: () -> Unit) {
    val overview = state.overview
    CardBox {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = overview?.month?.let { "$it 收支" } ?: "本月收支",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onRefresh) {
                Icon(Icons.Filled.Refresh, contentDescription = "刷新")
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth()) {
            ValueBlock("收入", overview?.income ?: 0L, AppColors.income, Modifier.weight(1f))
            ValueBlock("支出", overview?.expense ?: 0L, AppColors.expense, Modifier.weight(1f))
            ValueBlock("结余", overview?.balance ?: 0L, MaterialTheme.colorScheme.onSurface, Modifier.weight(1f))
        }
        overview?.expenseRatio?.let { ratio ->
            Spacer(Modifier.height(10.dp))
            Text(
                text = "支出环比 ${if (ratio >= 0) "+" else ""}${(ratio * 100).roundToInt()}%",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ValueBlock(label: String, value: Long, color: Color, modifier: Modifier) {
    Column(modifier = modifier) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(2.dp))
        Text(
            text = formatAmount(value),
            style = MaterialTheme.typography.titleMedium,
            color = color,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun CategoryCard(state: ReportViewModel.UiState, onSwitch: (Int) -> Unit) {
    CardBox {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("分类占比", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.weight(1f))
            Text("合计 ${formatAmount(state.categoryTotal)}", style = MaterialTheme.typography.labelSmall)
        }

        Spacer(Modifier.height(10.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(3.dp)
        ) {
            MiniTab("支出", state.categoryType == 1, AppColors.expense, Modifier.weight(1f)) { onSwitch(1) }
            MiniTab("收入", state.categoryType == 2, AppColors.income, Modifier.weight(1f)) { onSwitch(2) }
        }

        Spacer(Modifier.height(12.dp))
        if (state.categories.isEmpty()) {
            Text(
                text = "本月暂无数据",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall
            )
        } else {
            // 环形图先给个整体的「钱都去哪了」，下面的明细负责精确数字
            CategoryDonut(
                categories = state.categories,
                total = state.categoryTotal,
                centerLabel = if (state.categoryType == 1) "本月支出" else "本月收入"
            )
            Spacer(Modifier.height(10.dp))

            state.categories.forEachIndexed { index, item ->
                val ratio = item.ratio.toFloat().coerceIn(0f, 1f)
                val color = donutColor(index)
                Column(Modifier.padding(vertical = 5.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // 色点与环形图切片同色，两边能一一对上
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(color)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(item.categoryName, style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.weight(1f))
                        Text(
                            text = formatAmount(item.amount),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "${(item.ratio * 100).roundToInt()}%",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    LinearProgressIndicator(
                        progress = { ratio },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = color,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun MiniTab(label: String, selected: Boolean, color: Color, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) color else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
        )
    }
}

/** 环形图配色：切片与明细进度条共用，分类多于颜色数时循环取用 */
private val DONUT_COLORS = listOf(
    Color(0xFFEF6C57),
    Color(0xFFF2A93B),
    Color(0xFF4CAF93),
    Color(0xFF5B8DEF),
    Color(0xFF9B7EDE),
    Color(0xFFE06C9F)
)

private fun donutColor(index: Int): Color = DONUT_COLORS[index % DONUT_COLORS.size]

/**
 * 分类占比环形图。
 * <p>
 * 统计图库（Vico）只画直角坐标系图表（折线 / 柱状 / K 线），饼图不在它的能力范围内，
 * 所以这里用 Compose 的 Canvas 手绘：每片是一段描边圆弧，切片之间留缝，视觉上不会糊成一团；
 * 圆心放合计金额，省掉一行重复的「合计」文案。
 */
@Composable
private fun CategoryDonut(categories: List<CategoryStatDto>, total: Long, centerLabel: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(148.dp)) {
            val stroke = 18.dp.toPx()
            val diameter = size.minDimension - stroke
            val topLeft = Offset((size.width - diameter) / 2f, (size.height - diameter) / 2f)
            // 从 12 点方向顺时针铺开
            var startAngle = -90f
            categories.forEachIndexed { index, item ->
                val fraction = if (total > 0L) item.amount.toFloat() / total else 0f
                if (fraction <= 0f) {
                    return@forEachIndexed
                }
                val sweep = fraction * 360f
                drawArc(
                    color = donutColor(index),
                    startAngle = startAngle,
                    // 留 1.5° 缝隙；占比极小的分类也要看得见，所以保底 1°
                    sweepAngle = (sweep - 1.5f).coerceAtLeast(1f),
                    useCenter = false,
                    style = Stroke(width = stroke),
                    topLeft = topLeft,
                    size = Size(diameter, diameter)
                )
                startAngle += sweep
            }
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = centerLabel,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "¥${formatAmount(total)}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

/** 图例：小圆点 + 文案 */
@Composable
private fun ChartLegend(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * 近 14 天收支趋势。
 * <p>
 * 用 Vico 的组合图替换原来手写的色块：支出走柱状、收入走折线，纵轴给金额刻度，
 * 比一排等比缩放的方块更容易读出「哪天花超了」。
 */
@Composable
private fun TrendCard(trend: List<TrendItemDto>) {
    val points = remember(trend) { trend.takeLast(14) }
    CardBox {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("近 14 天趋势", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.weight(1f))
            ChartLegend(AppColors.expense, "支出")
            Spacer(Modifier.width(10.dp))
            ChartLegend(AppColors.income, "收入")
        }
        Spacer(Modifier.height(12.dp))

        if (points.isEmpty()) {
            Text(
                text = "暂无数据",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall
            )
        } else {
            // Vico 的数据要先装进 model producer；必须 remember，否则每次重组都会重建、数据空掉
            val modelProducer = remember { CartesianChartModelProducer() }
            LaunchedEffect(points) {
                // 单位换成「元」：纵轴刻度不会长到六位数
                val expenses = points.map { it.expense / 100.0 }
                val incomes = points.map { it.income / 100.0 }
                modelProducer.runTransaction {
                    columnSeries { series(expenses) }
                    lineSeries { series(incomes) }
                }
            }
            CartesianChartHost(
                chart = rememberCartesianChart(
                    rememberColumnCartesianLayer(),
                    rememberLineCartesianLayer(),
                    startAxis = VerticalAxis.rememberStart(),
                ),
                modelProducer = modelProducer,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(170.dp)
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "${points.first().period} ~ ${points.last().period}（单位：元）",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun AssetCard(state: ReportViewModel.UiState) {
    CardBox {
        Text("资产分布", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp))
        Text(
            text = "总资产 ¥${formatAmount(state.totalAssets)}",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(10.dp))
        if (state.assets.isEmpty()) {
            Text(
                text = "暂无账户",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall
            )
        } else {
            state.assets.forEach { account ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(account.name, style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.weight(1f))
                    Text(
                        text = formatAmount(account.balance),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

/* ==================== 预览 ==================== */

/** 造一份接近真实的假数据，覆盖有数据 / 空数据、支出 / 收入三种分支 */
private fun previewReportState(
    categoryType: Int = 1,
    empty: Boolean = false
): ReportViewModel.UiState {
    if (empty) {
        return ReportViewModel.UiState(bookId = 1L, categoryType = categoryType)
    }

    val categories = if (categoryType == 1) {
        listOf(
            CategoryStatDto(categoryId = 1L, categoryName = "餐饮", amount = 486000, ratio = 0.39),
            CategoryStatDto(categoryId = 2L, categoryName = "交通", amount = 235000, ratio = 0.19),
            CategoryStatDto(categoryId = 3L, categoryName = "购物", amount = 198500, ratio = 0.16),
            CategoryStatDto(categoryId = 4L, categoryName = "居住", amount = 168000, ratio = 0.14),
            CategoryStatDto(categoryId = 5L, categoryName = "娱乐", amount = 82000, ratio = 0.07),
            CategoryStatDto(categoryId = 6L, categoryName = "医疗", amount = 65000, ratio = 0.05)
        )
    } else {
        listOf(
            CategoryStatDto(categoryId = 10L, categoryName = "工资", amount = 1850000, ratio = 0.95),
            CategoryStatDto(categoryId = 11L, categoryName = "理财", amount = 96000, ratio = 0.05)
        )
    }

    return ReportViewModel.UiState(
        bookId = 1L,
        categoryType = categoryType,
        overview = ReportOverviewDto(
            month = "2026-10",
            income = 1850000,
            expense = 1234500,
            balance = 615500,
            incomeRatio = -0.05,
            expenseRatio = 0.12
        ),
        categories = categories,
        categoryTotal = categories.sumOf { it.amount },
        trend = listOf(
            32000L, 0L, 15600L, 88000L, 42000L, 0L, 23500L,
            61000L, 12800L, 0L, 45500L, 96000L, 22000L, 38500L
        ).mapIndexed { index, amount ->
            TrendItemDto(period = "2026-10-${(index + 1).toString().padStart(2, '0')}", expense = amount)
        },
        totalAssets = 5680000,
        assets = listOf(
            AccountAssetDto(accountId = 1L, name = "招商银行", type = 2, balance = 3200000),
            AccountAssetDto(accountId = 2L, name = "支付宝", type = 4, balance = 1350000),
            AccountAssetDto(accountId = 3L, name = "微信钱包", type = 5, balance = 620000),
            AccountAssetDto(accountId = 4L, name = "现金", type = 1, balance = 510000)
        )
    )
}

/** 直接复用页面里的卡片，避免为预览再维护一套 UI */
@Composable
private fun ReportPreviewContent(state: ReportViewModel.UiState) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { OverviewCard(state, onRefresh = {}) }
        item { CategoryCard(state, onSwitch = {}) }
        item { TrendCard(state.trend) }
        item { AssetCard(state) }
    }
}

@Preview(name = "统计页 · 支出", showBackground = true, widthDp = 380, heightDp = 900)
@Composable
private fun ReportExpensePreview() {
    JizhangTheme { ReportPreviewContent(previewReportState(1)) }
}

@Preview(name = "统计页 · 收入", showBackground = true, widthDp = 380, heightDp = 900)
@Composable
private fun ReportIncomePreview() {
    JizhangTheme { ReportPreviewContent(previewReportState(2)) }
}

@Preview(name = "统计页 · 空数据", showBackground = true, widthDp = 380, heightDp = 560)
@Composable
private fun ReportEmptyPreview() {
    JizhangTheme { ReportPreviewContent(previewReportState(1, empty = true)) }
}

@Preview(name = "统计页 · 深色", showBackground = true, widthDp = 380, heightDp = 900)
@Composable
private fun ReportDarkPreview() {
    JizhangTheme(darkTheme = true) { ReportPreviewContent(previewReportState(1)) }
}
