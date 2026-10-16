package com.jizhang.app.ui.report

import android.graphics.Paint
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jizhang.app.data.remote.dto.BudgetOverviewDto
import com.jizhang.app.data.remote.dto.MemberReportDto
import com.jizhang.app.data.remote.dto.MemberStatDto
import com.jizhang.app.data.remote.dto.TrendItemDto
import com.jizhang.app.data.repository.AssetSummary
import com.jizhang.app.data.repository.BudgetCycle
import com.jizhang.app.data.repository.BudgetPeriods
import com.jizhang.app.data.repository.CategorySlice
import com.jizhang.app.data.repository.DayAmount
import com.jizhang.app.data.repository.DayDetail
import com.jizhang.app.data.repository.HabitStats
import com.jizhang.app.data.repository.RankItem
import com.jizhang.app.data.repository.ReportGranularity
import com.jizhang.app.data.repository.ReportOverview
import com.jizhang.app.data.repository.ReportPeriod
import com.jizhang.app.ui.bill.BillPreset
import com.jizhang.app.ui.common.accountIconOf
import com.jizhang.app.ui.common.categoryIconOf
import com.jizhang.app.ui.common.formatAmount
import com.jizhang.app.ui.theme.AppColors
import com.jizhang.app.ui.theme.JizhangTheme
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberStart
import com.patrykandpatrick.vico.compose.cartesian.layer.continuous
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberColumnCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLine
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.common.component.rememberLineComponent
import com.patrykandpatrick.vico.compose.common.fill
import com.patrykandpatrick.vico.compose.common.shape.toVicoShape
import com.patrykandpatrick.vico.core.cartesian.CartesianDrawingContext
import com.patrykandpatrick.vico.core.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.core.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.core.cartesian.data.columnSeries
import com.patrykandpatrick.vico.core.cartesian.data.lineSeries
import com.patrykandpatrick.vico.core.cartesian.layer.ColumnCartesianLayer
import com.patrykandpatrick.vico.core.cartesian.layer.LineCartesianLayer
import com.patrykandpatrick.vico.core.cartesian.marker.CartesianMarker
import com.patrykandpatrick.vico.core.cartesian.marker.CartesianMarkerVisibilityListener
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.roundToInt

/**
 * 统计页。
 * <p>
 * 三段结构：
 * 1. **时间轴条** —— 日 / 月 / 年粒度 + 期间选择，所有卡片跟着它变；
 * 2. **总览与趋势** —— 当期收支、环比同比、粒度自适应的趋势图、收支日历；
 * 3. **维度拆解** —— 分类构成、账户排行、标签排行、记账习惯、预算、成员、资产。
 * <p>
 * 数据全部来自本地 Room 聚合（见 `ReportRepository`），断网 / 未登录一样能看。
 * <p>
 * 交互约定：**能点的地方都要给出对应的数据**——
 * 趋势图的柱子、日历里的某一天、记账习惯的星期柱点下去就地摊开明细；
 * 总览的收入 / 支出 / 结余与各维度排行则带着筛选条件跳到账单页看流水。
 * <p>
 * 隐私模式（[hideAmounts]）在这里是**全覆盖**的：环形图圆心、排行、日历、趋势纵轴都会打码。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportScreen(
    bookId: Long,
    shared: Boolean = false,
    /** 金额隐私模式：金额与图表刻度显示成 ****（设置里的开关） */
    hideAmounts: Boolean = false,
    /** 「资产管理」入口 */
    onOpenAccounts: (() -> Unit)? = null,
    /** 点分类 / 账户 / 标签看明细：切到账单页并带上筛选条件 */
    onOpenBills: (BillPreset) -> Unit = {},
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

    /*
     * 跳账单页的唯一出口：默认带上当前周期（否则「统计看到 8 月、账单显示本月」对不上），
     * 趋势图 / 日历下钻时会传入更窄的时间窗（某天、某小时、某个月），账单页直接显示那批账。
     */
    fun openBills(
        type: Int? = null,
        categoryId: Long? = null,
        accountId: Long? = null,
        keyword: String? = null,
        startMillis: Long = state.period.startMillis,
        endMillis: Long = state.period.endMillis - 1
    ) {
        onOpenBills(
            BillPreset(
                type = type,
                categoryId = categoryId,
                accountId = accountId,
                keyword = keyword,
                startMillis = startMillis,
                endMillis = endMillis
            )
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        PullToRefreshBox(
            isRefreshing = state.refreshing,
            onRefresh = { viewModel.load(refresh = true) },
            modifier = Modifier.fillMaxSize()
        ) {
            if (!state.loaded) {
                ReportSkeleton()
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item { TimeAxisCard(state, viewModel) }
                    item {
                        OverviewCard(
                            overview = state.overview,
                            period = state.period,
                            hideAmounts = hideAmounts,
                            // 点收入 / 支出 / 结余 → 账单页只看对应的那部分
                            onOpen = { type -> openBills(type = type) }
                        )
                    }
                    item {
                        TrendCard(
                            trend = state.trend,
                            period = state.period,
                            hideAmounts = hideAmounts,
                            onOpenRange = { start, end -> openBills(startMillis = start, endMillis = end - 1) }
                        )
                    }
                    if (state.period.granularity != ReportGranularity.YEAR || state.calendar.isNotEmpty()) {
                        item {
                            CalendarCard(
                                days = state.calendar,
                                period = state.period,
                                hideAmounts = hideAmounts,
                                dayDetail = state.dayDetail,
                                onPickDay = viewModel::selectDay,
                                onCloseDay = viewModel::clearDayDetail,
                                // 想真正切到那天的统计：面板里的「切到该日统计」
                                onOpenDay = { viewModel.selectDate(it, ReportGranularity.DAY) },
                                onOpenBills = { date ->
                                    val day = ReportPeriod(ReportGranularity.DAY, date)
                                    openBills(
                                        startMillis = day.startMillis,
                                        endMillis = day.endMillis - 1
                                    )
                                }
                            )
                        }
                    }
                    item {
                        CategoryCard(
                            state = state,
                            hideAmounts = hideAmounts,
                            onSwitch = viewModel::switchCategoryType,
                            onToggleGroup = viewModel::setGroupByParent,
                            onOpenCategory = { openBills(categoryId = it) }
                        )
                    }
                    item {
                        AccountRankCard(
                            ranks = state.accountRanks,
                            rankType = state.rankType,
                            hideAmounts = hideAmounts,
                            onSwitchType = viewModel::switchRankType,
                            onOpen = { openBills(accountId = it) }
                        )
                    }
                    item {
                        TagRankCard(
                            ranks = state.tagRanks,
                            rankType = state.rankType,
                            hideAmounts = hideAmounts,
                            onSwitchType = viewModel::switchRankType,
                            onOpen = { openBills(keyword = it) }
                        )
                    }
                    state.habit?.let { habit ->
                        item { HabitCard(habit, state.period, hideAmounts) }
                    }
                    item { BudgetCard(state.budget, state.budgetTitle, hideAmounts, onEdit = { showBudgetDialog = true }) }
                    if (state.shared) {
                        item {
                            MemberCard(
                                members = state.members,
                                hideAmounts = hideAmounts,
                                onOpen = { openBills(keyword = it) }
                            )
                        }
                    }
                    item { AssetCard(state.assets, hideAmounts, onOpenAccounts) }
                }
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }

    if (showBudgetDialog) {
        BudgetDialog(
            periodLabel = BudgetPeriods.label(
                BudgetPeriods.of(BudgetCycle.MONTH, state.period.anchor)
            ),
            expenseCategories = state.expenseCategories,
            budget = state.budget,
            onSave = viewModel::saveBudget,
            onDelete = viewModel::deleteBudget,
            onDismiss = { showBudgetDialog = false }
        )
    }
}

/** 首屏骨架：只占位不闪白，避免打开统计页先看到一片空 */
@Composable
private fun ReportSkeleton() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        repeat(4) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(if (it == 0) 96.dp else 150.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            )
        }
    }
}

/* ==================== 时间轴条 ==================== */

/**
 * 时间轴条：粒度分段（日 / 月 / 年）+ 期间前后翻页 + 点击标题展开期间选择面板。
 * <p>
 * 选择器做成**卡片内展开**而不是弹窗：一是和现有弹层体系（覆盖层 + 全屏壳）保持距离，
 * 免得又碰上新系统版本下弹窗被裁的老问题；二是频繁切换粒度时不用反复开关弹窗。
 */
@Composable
private fun TimeAxisCard(state: ReportViewModel.UiState, viewModel: ReportViewModel) {
    var expanded by remember { mutableStateOf(false) }
    val period = state.period

    CardBox {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SegmentedTabs(
                items = ReportGranularity.entries.map { it.label },
                selectedIndex = period.granularity.ordinal,
                onSelect = { index ->
                    viewModel.setGranularity(ReportGranularity.entries[index])
                    expanded = false
                },
                modifier = Modifier.width(150.dp)
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = if (period.isCurrent) "当期" else period.granularity.nowLabel,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .clickable(enabled = !period.isCurrent) { viewModel.backToNow() }
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }

        Spacer(Modifier.height(6.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { viewModel.switchPeriod(-1) }) {
                Icon(Icons.Filled.ChevronLeft, contentDescription = "上一期")
            }
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .clickable { expanded = !expanded }
                    .padding(vertical = 6.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = period.label,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.width(4.dp))
                Icon(
                    imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(
                onClick = { viewModel.switchPeriod(1) },
                enabled = state.canGoNext
            ) {
                Icon(
                    Icons.Filled.ChevronRight,
                    contentDescription = "下一期",
                    tint = if (state.canGoNext) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                    }
                )
            }
        }

        AnimatedVisibility(visible = expanded) {
            Column {
                Spacer(Modifier.height(8.dp))
                PeriodPicker(
                    period = period,
                    onPick = { date ->
                        viewModel.selectDate(date)
                        expanded = false
                    }
                )
            }
        }
    }
}

/** 期间选择面板：按粒度给出年网格 / 年月网格 / 月历 */
@Composable
private fun PeriodPicker(period: ReportPeriod, onPick: (LocalDate) -> Unit) {
    val today = remember { LocalDate.now() }
    when (period.granularity) {
        ReportGranularity.YEAR -> YearPicker(period.anchor.year, today) { year ->
            onPick(minOf(LocalDate.of(year, 1, 1), today))
        }

        ReportGranularity.MONTH -> MonthPicker(YearMonth.from(period.anchor), today) { month ->
            onPick(minOf(month.atDay(1), today))
        }

        ReportGranularity.DAY -> DayPicker(YearMonth.from(period.anchor), period.anchor, today, onPick)
    }
}

@Composable
private fun YearPicker(selectedYear: Int, today: LocalDate, onPick: (Int) -> Unit) {
    // 只给到今年：未来年份没有数据，摆出来只会让人点空
    val years = remember(today) { (today.year - 11..today.year).toList() }
    Column {
        years.chunked(4).forEach { row ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { year ->
                    GridCell(
                        text = year.toString(),
                        selected = year == selectedYear,
                        enabled = year <= today.year,
                        modifier = Modifier.weight(1f),
                        onClick = { onPick(year) }
                    )
                }
                // 最后一行不足 4 个时补空位，避免格子被拉伸
                repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun MonthPicker(selected: YearMonth, today: LocalDate, onPick: (YearMonth) -> Unit) {
    // 月份网格的年份游标：默认跟着选中月，翻页只影响网格本身，点月份才真的跳转
    var cursorYear by remember(selected) { mutableIntStateOf(selected.year) }
    val maxYear = today.year

    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(
                onClick = { cursorYear -= 1 },
                enabled = cursorYear > maxYear - 20
            ) {
                Icon(Icons.Filled.ChevronLeft, contentDescription = "上一年")
            }
            Text(
                text = "$cursorYear 年",
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            IconButton(
                onClick = { cursorYear += 1 },
                enabled = cursorYear < maxYear
            ) {
                Icon(Icons.Filled.ChevronRight, contentDescription = "下一年")
            }
        }

        (1..12).chunked(4).forEach { row ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { month ->
                    val target = YearMonth.of(cursorYear, month)
                    GridCell(
                        text = "$month 月",
                        selected = target == selected,
                        enabled = !target.isAfter(YearMonth.from(today)),
                        modifier = Modifier.weight(1f),
                        onClick = { onPick(target) }
                    )
                }
                repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

/** 月历：点哪一天就看那一天 */
@Composable
private fun DayPicker(
    month: YearMonth,
    selected: LocalDate,
    today: LocalDate,
    onPick: (LocalDate) -> Unit
) {
    var cursor by remember(selected) { mutableStateOf(YearMonth.from(selected)) }
    val maxMonth = YearMonth.from(today)
    val firstDay = cursor.atDay(1)
    // 周日开头的日历更符合直觉：把周一…周日的下标挪成「日一二三四五六」
    val leading = firstDay.dayOfWeek.value % 7
    val totalCells = leading + cursor.lengthOfMonth()
    val rows = (totalCells + 6) / 7
    val cells = rows * 7

    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { cursor = cursor.minusMonths(1) }) {
                Icon(Icons.Filled.ChevronLeft, contentDescription = "上个月")
            }
            Text(
                text = cursor.format(MONTH_TEXT),
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            IconButton(
                onClick = { cursor = cursor.plusMonths(1) },
                enabled = cursor.isBefore(maxMonth)
            ) {
                Icon(Icons.Filled.ChevronRight, contentDescription = "下个月")
            }
        }

        Row(modifier = Modifier.fillMaxWidth()) {
            WEEKDAY_SHORT.forEach { label ->
                Text(
                    text = label,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(Modifier.height(4.dp))

        (0 until cells).forEach { index ->
            if (index % 7 == 0) {
                Row(modifier = Modifier.fillMaxWidth()) {
                    repeat(7) { column ->
                        val cellIndex = index + column
                        val day = cellIndex - leading + 1
                        if (day in 1..cursor.lengthOfMonth()) {
                            val date = cursor.atDay(day)
                            DayCell(
                                date = date,
                                selected = date == selected,
                                today = date == today,
                                enabled = !date.isAfter(today),
                                modifier = Modifier.weight(1f),
                                onClick = { onPick(date) }
                            )
                        } else {
                            Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DayCell(
    date: LocalDate,
    selected: Boolean,
    today: Boolean,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .padding(2.dp)
            .aspectRatio(1f)
            .clip(CircleShape)
            .background(if (selected) MaterialTheme.colorScheme.primary else Color.Transparent)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (today && !selected) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(2.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
            )
        }
        Text(
            text = date.dayOfMonth.toString(),
            style = MaterialTheme.typography.bodySmall,
            fontWeight = if (selected || today) FontWeight.Bold else FontWeight.Normal,
            color = when {
                selected -> Color.White
                !enabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                today -> MaterialTheme.colorScheme.primary
                else -> MaterialTheme.colorScheme.onSurface
            }
        )
    }
}

@Composable
private fun GridCell(
    text: String,
    selected: Boolean,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(
                if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                }
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = when {
                selected -> Color.White
                !enabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                else -> MaterialTheme.colorScheme.onSurface
            }
        )
    }
}

/* ==================== 总览 ==================== */

/**
 * 总览卡：当期收入 / 支出 / 结余 + 环比同比 + 日均与笔数。
 * <p>
 * 环比看「上一期」（昨天 / 上月 / 去年），同比看「去年同期」——
 * 粒度越粗，同比越有意义（月粒度下「去年这个月」比「上个月」更值得对比）。
 */
@Composable
private fun OverviewCard(
    overview: ReportOverview?,
    period: ReportPeriod,
    hideAmounts: Boolean,
    /** 点收入 / 支出 / 结余 → 去账单页看对应那部分（type = 2 收入、1 支出、null 全部） */
    onOpen: (Int?) -> Unit
) {
    CardBox {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("收支总览", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.weight(1f))
            Text(
                text = period.label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(Modifier.height(10.dp))

        if (overview == null) {
            EmptyHint()
            return@CardBox
        }

        Row(modifier = Modifier.fillMaxWidth()) {
            ValueBlock(
                label = "收入",
                value = overview.income,
                color = AppColors.income,
                hideAmounts = hideAmounts,
                count = overview.incomeCount,
                modifier = Modifier.weight(1f),
                onClick = { onOpen(2) }
            )
            ValueBlock(
                label = "支出",
                value = overview.expense,
                color = AppColors.expense,
                hideAmounts = hideAmounts,
                count = overview.expenseCount,
                modifier = Modifier.weight(1f),
                onClick = { onOpen(1) }
            )
            ValueBlock(
                label = "结余",
                value = overview.balance,
                color = if (overview.balance >= 0) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    AppColors.expense
                },
                hideAmounts = hideAmounts,
                count = null,
                modifier = Modifier.weight(1f),
                // 结余没有「收入 - 支出」这种单一类型可筛，点进去看全部流水
                onClick = { onOpen(null) }
            )
        }

        Spacer(Modifier.height(10.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            RatioBadge("支出环比", overview.expenseChainRatio, period.previousLabel, Modifier.weight(1f))
            RatioBadge("支出同比", overview.expenseYoYRatio, period.lastYearLabel, Modifier.weight(1f))
        }

        Spacer(Modifier.height(12.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            StatItem("日均支出", money(overview.avgExpensePerDay, hideAmounts), MaterialTheme.colorScheme.onSurface, Modifier.weight(1f))
            StatItem("记账天数", "${overview.activeDays} 天", MaterialTheme.colorScheme.onSurface, Modifier.weight(1f))
            StatItem("笔数", "${overview.txCount}", MaterialTheme.colorScheme.onSurface, Modifier.weight(1f))
        }

        if (overview.transferCount > 0) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = "转账 ${overview.transferCount} 笔 · ${money(overview.transferAmount, hideAmounts)}（不计入收支）",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(Modifier.height(8.dp))
        Text(
            text = "点金额 → 去账单页核对流水",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** 同比 / 环比标签：上期为 0 时显示「—」，不要用 0% 骗人 */
@Composable
private fun RatioBadge(label: String, ratio: Double?, compareLabel: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(2.dp))
        if (ratio == null) {
            Text("—", style = MaterialTheme.typography.bodyMedium)
        } else {
            val up = ratio >= 0
            Text(
                text = (if (up) "↑ " else "↓ ") + "${(kotlin.math.abs(ratio) * 100).roundToInt()}%",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (up) AppColors.expense else AppColors.income
            )
        }
        Text(
            text = "较$compareLabel",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * 总览里的一个金额块。
 * <p>
 * 金额下面跟着笔数：只看「收入 8000」不知道是几笔，带上笔数才知道是「多笔小额」还是「一笔大的」。
 * 整块可点 —— 点了带着这个类型去账单页看流水。
 */
@Composable
private fun ValueBlock(
    label: String,
    value: Long,
    color: Color,
    hideAmounts: Boolean,
    count: Long?,
    modifier: Modifier,
    onClick: () -> Unit
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp, horizontal = 2.dp)
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(2.dp))
        Text(
            text = money(value, hideAmounts, suffix = ""),
            style = MaterialTheme.typography.titleMedium,
            color = color,
            fontWeight = FontWeight.Bold
        )
        if (count != null) {
            Text(
                text = "$count 笔",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/* ==================== 趋势 ==================== */

/**
 * 收支趋势：横轴随粒度变化（小时 / 按天 / 按月）。
 * <p>
 * 用 Vico 的柱 + 折线组合：支出是柱（看总量），收入是折线（看趋势），
 * 纵轴在隐私模式下直接隐藏刻度，避免打码打不干净。
 * <p>
 * 图上的柱子 / 折线点**可点**：点中的那一格会在图下方摊开明细（支出 / 收入 / 结余 / 占比），
 * 想进一步看这批账就点「查看这批账单」——带着那一格的时间窗跳到账单页。
 */
@Composable
private fun TrendCard(
    trend: List<TrendItemDto>,
    period: ReportPeriod,
    hideAmounts: Boolean,
    /** 「查看这批账单」：把选中那格的时间窗（左闭右开毫秒）交给账单页 */
    onOpenRange: (Long, Long) -> Unit
) {
    // 选中的柱子下标。点图上的柱子 / 折线点会更新它，点空白处不动 ——
    // 手指抬起后 Vico 的标记线会消失，但下方明细面板留着，方便照着数字去翻账单
    var selected by remember(trend, period) { mutableStateOf<Int?>(null) }
    val pickLatest = rememberUpdatedState<(Int) -> Unit> { index ->
        if (index in trend.indices) {
            selected = index
        }
    }
    val markerColor = MaterialTheme.colorScheme.primary.toArgb()
    val density = LocalDensity.current.density
    val marker = remember(markerColor, density) { TrendMarker(markerColor, 1.5f * density) }
    val markerListener = remember { TrendMarkerListener { index -> pickLatest.value(index) } }

    CardBox {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("收支趋势", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.width(6.dp))
            Text(
                text = when (period.granularity) {
                    ReportGranularity.DAY -> "按小时"
                    ReportGranularity.MONTH -> "按天"
                    ReportGranularity.YEAR -> "按月"
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.weight(1f))
            BarLegend(AppColors.expense, "支出")
            Spacer(Modifier.width(10.dp))
            LineLegend(AppColors.income, "收入")
        }
        Spacer(Modifier.height(12.dp))

        if (trend.isEmpty() || trend.all { it.income == 0L && it.expense == 0L }) {
            EmptyHint()
            return@CardBox
        }

        val modelProducer = remember { CartesianChartModelProducer() }
        LaunchedEffect(trend) {
            // 单位换成「元」：纵轴刻度不会长到六位数
            val expenses = trend.map { it.expense / 100.0 }
            val incomes = trend.map { it.income / 100.0 }
            modelProducer.runTransaction {
                columnSeries { series(expenses) }
                lineSeries { series(incomes) }
            }
        }

        // 柱/线显式用主题色：不给颜色的话 Vico 会用自带的蓝色，
        // 于是「图例红=支出、绿=收入」和图里的蓝色对不上 —— 这正是之前看不懂的来源
        val expenseColor = AppColors.expense
        val incomeColor = AppColors.income

        CartesianChartHost(
            chart = rememberCartesianChart(
                rememberColumnCartesianLayer(
                    columnProvider = ColumnCartesianLayer.ColumnProvider.series(
                        rememberLineComponent(
                            fill = fill(expenseColor),
                            thickness = 14.dp,
                            shape = RoundedCornerShape(4.dp).toVicoShape()
                        )
                    )
                ),
                rememberLineCartesianLayer(
                    lineProvider = LineCartesianLayer.LineProvider.series(
                        LineCartesianLayer.rememberLine(
                            fill = LineCartesianLayer.LineFill.single(fill(incomeColor)),
                            stroke = LineCartesianLayer.LineStroke.continuous(thickness = 2.5.dp)
                        )
                    )
                ),
                startAxis = if (hideAmounts) null else VerticalAxis.rememberStart(),
                // 点柱子 / 折线点：Vico 把命中的 x 值回调过来，换成下标后摊开明细
                marker = marker,
                markerVisibilityListener = markerListener,
            ),
            modelProducer = modelProducer,
            modifier = Modifier
                .fillMaxWidth()
                .height(170.dp)
        )
        Spacer(Modifier.height(6.dp))

        // 横轴标签只抽样几个：全画会糊成一团，抽首尾 + 中点已经够定位
        val labels = sampledLabels(trend.map { it.period })
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            labels.forEach { label ->
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Text(
            text = buildString {
                append("柱 = 支出，线 = 收入；两者共用左侧刻度")
                append(if (hideAmounts) "（金额已隐藏）" else "，单位：元")
            },
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(8.dp))
        val picked = selected?.takeIf { it in trend.indices }
        if (picked == null) {
            Text(
                text = "点图上的柱子 / 折线点 → 这里摊开那一格的明细",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            TrendBucketDetail(
                item = trend[picked],
                label = period.bucketLabel(picked),
                periodExpense = trend.sumOf { it.expense },
                periodIncome = trend.sumOf { it.income },
                hideAmounts = hideAmounts,
                onOpenBills = {
                    val (start, end) = period.bucketMillis(picked)
                    onOpenRange(start, end)
                }
            )
        }
    }
}

/**
 * 趋势图选中某一格的明细条。
 * <p>
 * 图表只表达量级：具体多少钱、占当期多少、要不要去看那批账，都在这里交代清楚，
 * 省得用户点开账单页才发现不是自己想看的那一格。
 */
@Composable
private fun TrendBucketDetail(
    item: TrendItemDto,
    label: String,
    periodExpense: Long,
    periodIncome: Long,
    hideAmounts: Boolean,
    onOpenBills: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
            .padding(horizontal = 10.dp, vertical = 8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.weight(1f))
            Text(
                text = "查看这批账单 ›",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .clickable(onClick = onOpenBills)
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            )
        }
        Spacer(Modifier.height(6.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            StatItem("支出", money(item.expense, hideAmounts), AppColors.expense, Modifier.weight(1f))
            StatItem("收入", money(item.income, hideAmounts), AppColors.income, Modifier.weight(1f))
            StatItem(
                label = "结余",
                value = money(item.balance, hideAmounts),
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = "占当期支出 ${shareText(item.expense, periodExpense)} · " +
                "占当期收入 ${shareText(item.income, periodIncome)}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** 某一格占当期总量的比例；当期是 0 时不给百分比，免得冒出一句「占 0%」 */
private fun shareText(part: Long, total: Long): String =
    if (total <= 0L) "—" else "${(part * 100.0 / total).roundToInt()}%"

private fun sampledLabels(all: List<String>, count: Int = 4): List<String> {
    if (all.size <= count) {
        return all
    }
    return (0 until count).map { index ->
        all[(index * (all.size - 1) / (count - 1)).coerceIn(0, all.size - 1)]
    }
}

/**
 * 趋势图的选中标记：只在命中的那一格画一条竖线，具体数字交给图下方的明细条。
 * <p>
 * 图库自带的默认标记要连文字组件、值格式化器一起构造（参数一个都不能少），
 * 而这里只需要一条线，于是直接用绘制上下文的 canvas 画，少一层 API 依赖。
 */
private class TrendMarker(
    private val argbColor: Int,
    thicknessPx: Float
) : CartesianMarker {

    private val paint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.STROKE
        strokeWidth = thicknessPx
        color = argbColor
    }

    override fun drawUnderLayers(
        context: CartesianDrawingContext,
        targets: List<CartesianMarker.Target>
    ) = Unit

    override fun drawOverLayers(
        context: CartesianDrawingContext,
        targets: List<CartesianMarker.Target>
    ) {
        val target = targets.firstOrNull() ?: return
        val bounds = context.layerBounds
        context.canvas.drawLine(target.canvasX, bounds.top, target.canvasX, bounds.bottom, paint)
    }
}

/**
 * 把图库回调的命中位置换成列表下标。
 * <p>
 * 列数据没有显式给 x 值时，x 就是下标本身（0,1,2…），所以 [CartesianMarker.Target.x]
 * 直接四舍五入就是 `trend` 的下标；按下与拖动会连续回调，UI 跟着手指走。
 */
private class TrendMarkerListener(
    private val onPick: (Int) -> Unit
) : CartesianMarkerVisibilityListener {

    override fun onShown(marker: CartesianMarker, targets: List<CartesianMarker.Target>) {
        pick(targets)
    }

    override fun onUpdated(marker: CartesianMarker, targets: List<CartesianMarker.Target>) {
        pick(targets)
    }

    /** 抬手时不改选中项：标记线消失可以接受，明细面板要留着 */
    override fun onHidden(marker: CartesianMarker) = Unit

    private fun pick(targets: List<CartesianMarker.Target>) {
        val x = targets.firstOrNull()?.x ?: return
        onPick(x.roundToInt())
    }
}

/* ==================== 收支日历 ==================== */

/**
 * 收支日历热力图。
 * <p>
 * 语义要向用户交代清楚，所以图例给了三档「无记录 / 花得少 / 花得多」，格子里还写了具体的支出金额。
 * 点某一天**不直接跳页**，而是就地展开当天明细（几笔、多少钱、有没有转账），再决定要不要切到日视图 ——
 * 在年视图里点一格就被甩到「某一天」，很容易失去上下文。未来日期不填色也不能点 ——
 * 否则「还没到」和「没花钱」会长得一模一样。
 */
@Composable
private fun CalendarCard(
    days: List<DayAmount>,
    period: ReportPeriod,
    hideAmounts: Boolean,
    /** 已展开的当天明细；null = 没展开 */
    dayDetail: DayDetail?,
    onPickDay: (LocalDate) -> Unit,
    onCloseDay: () -> Unit,
    /** 切到那天的统计（日视图） */
    onOpenDay: (LocalDate) -> Unit,
    /** 看那天的账单 */
    onOpenBills: (LocalDate) -> Unit
) {
    val today = remember { LocalDate.now() }
    val byDate = remember(days) { days.associateBy { it.date } }
    val maxExpense = remember(days) { days.maxOfOrNull { it.expense } ?: 0L }
    val monthTotal = remember(days) { days.sumOf { it.expense } }

    CardBox {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("收支日历", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.weight(1f))
            LegendSwatch(MaterialTheme.colorScheme.surfaceVariant, "无记录")
            Spacer(Modifier.width(4.dp))
            LegendSwatch(AppColors.expense.copy(alpha = 0.3f), "少")
            Spacer(Modifier.width(4.dp))
            LegendSwatch(AppColors.expense, "多")
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = "颜色越深表示当天支出越多 · 合计 ${money(monthTotal, hideAmounts)}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(10.dp))

        when (period.granularity) {
            ReportGranularity.YEAR -> YearHeatmap(
                year = period.anchor.year,
                byDate = byDate,
                maxExpense = maxExpense,
                today = today,
                hideAmounts = hideAmounts,
                // 点开的那天在年视图里也要圈出来，否则不知道面板说的是哪一格
                selected = dayDetail?.date,
                onPickDay = onPickDay
            )
            else -> MonthGrid(
                month = YearMonth.from(period.anchor),
                byDate = byDate,
                maxExpense = maxExpense,
                today = today,
                // 点开的那天就是选中的那天，勾出来才能和下面的明细对上
                selected = dayDetail?.date ?: period.anchor,
                hideAmounts = hideAmounts,
                onPickDay = onPickDay
            )
        }

        Spacer(Modifier.height(8.dp))
        val picked = dayDetail
        if (picked == null) {
            Text(
                text = "点某一天 → 这里摊开当天明细",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            DayDetailPanel(
                detail = picked,
                hideAmounts = hideAmounts,
                onOpenDay = { onOpenDay(picked.date) },
                onOpenBills = { onOpenBills(picked.date) },
                onClose = onCloseDay
            )
        }
    }
}

/**
 * 日历里点开的那一天。
 * <p>
 * 回答「几笔、多少钱、有没有转账」，再给两个出口：切到日视图看完整统计，或直接去账单页看那天的每一笔。
 * 站内不做重复的账单列表 —— 那是账单页的活。
 */
@Composable
private fun DayDetailPanel(
    detail: DayDetail,
    hideAmounts: Boolean,
    onOpenDay: () -> Unit,
    onOpenBills: () -> Unit,
    onClose: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
            .padding(horizontal = 10.dp, vertical = 8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = detail.date.format(DAY_TEXT) + "（" + ReportPeriod.weekdayText(detail.date) + "）",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = "收起",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .clickable(onClick = onClose)
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            )
        }
        Spacer(Modifier.height(6.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            StatItem("支出", money(detail.expense, hideAmounts), AppColors.expense, Modifier.weight(1f))
            StatItem("收入", money(detail.income, hideAmounts), AppColors.income, Modifier.weight(1f))
            StatItem(
                label = "结余",
                value = money(detail.balance, hideAmounts),
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = buildString {
                append("支出 ${detail.expenseCount} 笔 · 收入 ${detail.incomeCount} 笔")
                if (detail.transferCount > 0) {
                    append(" · 转账 ${detail.transferCount} 笔 ${money(detail.transferAmount, hideAmounts)}")
                }
                if (detail.txCount == 0L && detail.transferCount == 0L) {
                    append(" · 这天没有记账")
                }
            },
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SmallAction("切到该日统计", onOpenDay)
            SmallAction("看当天账单", onOpenBills)
        }
    }
}

/** 卡片里的小动作按钮：描边 + 主题色，和「去设置」那种纯文字入口区分开 */
@Composable
private fun SmallAction(text: String, onClick: () -> Unit) {
    val shape = RoundedCornerShape(8.dp)
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .clip(shape)
            .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f), shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 5.dp)
    )
}

@Composable
private fun MonthGrid(
    month: YearMonth,
    byDate: Map<LocalDate, DayAmount>,
    maxExpense: Long,
    today: LocalDate,
    selected: LocalDate,
    hideAmounts: Boolean,
    onPickDay: (LocalDate) -> Unit
) {
    val leading = month.atDay(1).dayOfWeek.value % 7
    val cells = leading + month.lengthOfMonth()
    val rows = (cells + 6) / 7

    Column {
        Row(modifier = Modifier.fillMaxWidth()) {
            WEEKDAY_SHORT.forEach { label ->
                Text(
                    text = label,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(Modifier.height(4.dp))

        (0 until rows).forEach { row ->
            Row(modifier = Modifier.fillMaxWidth()) {
                repeat(7) { column ->
                    val day = row * 7 + column - leading + 1
                    if (day in 1..month.lengthOfMonth()) {
                        val date = month.atDay(day)
                        HeatCell(
                            date = date,
                            expense = byDate[date]?.expense ?: 0L,
                            maxExpense = maxExpense,
                            selected = date == selected,
                            today = date == today,
                            future = date.isAfter(today),
                            hideAmounts = hideAmounts,
                            modifier = Modifier.weight(1f),
                            onClick = { onPickDay(date) }
                        )
                    } else {
                        Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun YearHeatmap(
    year: Int,
    byDate: Map<LocalDate, DayAmount>,
    maxExpense: Long,
    today: LocalDate,
    hideAmounts: Boolean,
    /** 已经点开明细的那一天（没有就传 null） */
    selected: LocalDate?,
    onPickDay: (LocalDate) -> Unit
) {
    val scrollState = rememberScrollState()
    // 从包含 1 月 1 日的那一周（周日起）开始铺，铺满 53 周
    val start = LocalDate.of(year, 1, 1).minusDays((LocalDate.of(year, 1, 1).dayOfWeek.value % 7).toLong())

    Column {
        Row(modifier = Modifier.horizontalScroll(scrollState)) {
            (0 until 53).forEach { week ->
                Column(modifier = Modifier.padding(end = 3.dp)) {
                    repeat(7) { dayOfWeek ->
                        val date = start.plusDays((week * 7 + dayOfWeek).toLong())
                        if (date.year == year) {
                            HeatCell(
                                date = date,
                                expense = byDate[date]?.expense ?: 0L,
                                maxExpense = maxExpense,
                                selected = date == selected,
                                today = date == today,
                                future = date.isAfter(today),
                                hideAmounts = hideAmounts,
                                compact = true,
                                onClick = { onPickDay(date) }
                            )
                        } else {
                            Spacer(Modifier.size(14.dp))
                        }
                    }
                }
            }
        }
    }
}

/**
 * 日历格子。
 * <p>
 * 支出越多底色越深；没记录的日子只有浅灰底，没到的日子连底都没有（不可点）——
 * 这样「没花钱」和「还没到」不会再混淆。今天用描边圈出来，选中的那天用主题色粗描边。
 */
@Composable
private fun HeatCell(
    date: LocalDate,
    expense: Long,
    maxExpense: Long,
    selected: Boolean,
    today: Boolean,
    future: Boolean,
    hideAmounts: Boolean,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    onClick: () -> Unit
) {
    val ratio = if (maxExpense <= 0L) 0f else (expense.toFloat() / maxExpense).coerceIn(0f, 1f)
    // 五档色阶：视觉上能分档，又不至于每笔差一点就换颜色
    val level = if (expense <= 0L) 0 else (ratio * 4).roundToInt().coerceIn(1, 4)
    val alpha = when (level) {
        0 -> 0f
        1 -> 0.28f
        2 -> 0.5f
        3 -> 0.72f
        else -> 1f
    }
    val background = when {
        future -> Color.Transparent
        expense <= 0L -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        else -> AppColors.expense.copy(alpha = alpha)
    }
    val shape = RoundedCornerShape(if (compact) 3.dp else 6.dp)
    val borderColor = when {
        selected -> MaterialTheme.colorScheme.primary
        today -> MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
        else -> Color.Transparent
    }

    Box(
        modifier = modifier
            .padding(if (compact) 1.dp else 2.dp)
            .let { if (compact) it.size(14.dp) else it.aspectRatio(1f) }
            .clip(shape)
            .background(background)
            .border(
                width = if (selected) 2.dp else if (today) 1.5.dp else 0.dp,
                color = borderColor,
                shape = shape
            )
            .clickable(enabled = !future, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (!compact) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = date.dayOfMonth.toString(),
                    style = MaterialTheme.typography.labelSmall,
                    color = when {
                        future -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                        alpha > 0.6f -> Color.White
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    fontWeight = if (selected || today) FontWeight.Bold else FontWeight.Normal
                )
                // 写「几十」而不是完整金额：格子太窄，只给量级，精确数字点进日视图看
                if (!future && expense > 0L) {
                    Text(
                        text = if (hideAmounts) "**" else compactAmount(expense),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (alpha > 0.6f) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

/* ==================== 分类构成 ==================== */

/**
 * 分类占比：环形图 + 明细。
 * <p>
 * 三个可交互点：支出 / 收入切换、父分类归并开关（子分类是否算到父分类上）、
 * 点环形图切片高亮并在圆心看该分类的金额与占比；点明细行跳到账单页看那批账。
 */
@Composable
private fun CategoryCard(
    state: ReportViewModel.UiState,
    hideAmounts: Boolean,
    onSwitch: (Int) -> Unit,
    onToggleGroup: (Boolean) -> Unit,
    onOpenCategory: (Long) -> Unit
) {
    var selectedIndex by remember { mutableStateOf<Int?>(null) }
    val categories = state.categories

    CardBox {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("分类占比", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.weight(1f))
            Text(
                text = if (state.groupByParent) "合并子分类" else "展开子分类",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .clickable { onToggleGroup(!state.groupByParent) }
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            )
        }

        Spacer(Modifier.height(10.dp))
        SegmentedTabs(
            items = listOf("支出", "收入"),
            selectedIndex = if (state.categoryType == 2) 1 else 0,
            onSelect = { onSwitch(if (it == 1) 2 else 1) },
            selectedColor = if (state.categoryType == 2) AppColors.income else AppColors.expense,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(12.dp))
        if (categories.isEmpty()) {
            EmptyHint("这段没有${if (state.categoryType == 2) "收入" else "支出"}记录")
            return@CardBox
        }

        CategoryDonut(
            categories = categories,
            total = state.categoryTotal,
            label = if (state.categoryType == 2) "收入合计" else "支出合计",
            hideAmounts = hideAmounts,
            selectedIndex = selectedIndex,
            onSelect = { selectedIndex = it }
        )
        Spacer(Modifier.height(10.dp))

        categories.forEachIndexed { index, item ->
            val color = donutColor(index)
            CategoryRow(
                slice = item,
                color = color,
                hideAmounts = hideAmounts,
                highlighted = selectedIndex == index,
                onSelect = { selectedIndex = if (selectedIndex == index) null else index },
                onOpen = { item.categoryId?.let(onOpenCategory) }
            )
        }
    }
}

@Composable
private fun CategoryRow(
    slice: CategorySlice,
    color: Color,
    hideAmounts: Boolean,
    highlighted: Boolean,
    onSelect: () -> Unit,
    onOpen: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val ratio = slice.ratio.coerceIn(0f, 1f)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (highlighted) {
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                } else {
                    Color.Transparent
                }
            )
            .padding(horizontal = 4.dp, vertical = 5.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onSelect),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(color)
            )
            Spacer(Modifier.width(6.dp))
            Icon(
                imageVector = categoryIconOf(slice.icon),
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.width(6.dp))
            Text(slice.name, style = MaterialTheme.typography.bodyMedium)
            if (slice.children.isNotEmpty()) {
                Spacer(Modifier.width(4.dp))
                Text(
                    text = "${slice.children.size} 项",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .clickable { expanded = !expanded }
                        .padding(horizontal = 4.dp, vertical = 1.dp)
                )
            }
            Spacer(Modifier.weight(1f))
            Text(
                text = money(slice.amount, hideAmounts, suffix = ""),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "${(slice.ratio * 100).roundToInt()}%",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text = "›",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .clickable(onClick = onOpen)
                    .padding(horizontal = 4.dp)
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

        AnimatedVisibility(visible = expanded) {
            Column(modifier = Modifier.padding(start = 22.dp, top = 6.dp)) {
                slice.children.forEach { child ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = categoryIconOf(child.icon),
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = child.name,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.weight(1f))
                        Text(
                            text = money(child.amount, hideAmounts, suffix = ""),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
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
 * 所以这里用 Compose 的 Canvas 手绘：每片是一段描边圆弧，切片之间留缝，视觉上不会糊成一团。
 * 入场时按 0 → 1 扫一圈，点圆环上的某片会高亮并把圆心换成该片的金额。
 */
@Composable
private fun CategoryDonut(
    categories: List<CategorySlice>,
    total: Long,
    label: String,
    hideAmounts: Boolean,
    selectedIndex: Int?,
    onSelect: (Int?) -> Unit
) {
    val sweepProgress = remember { Animatable(0f) }
    var canvasSize by remember { mutableStateOf(Size.Zero) }

    LaunchedEffect(categories) {
        sweepProgress.snapTo(0f)
        sweepProgress.animateTo(1f, animationSpec = tween(durationMillis = 650))
    }

    val selected = selectedIndex?.let { categories.getOrNull(it) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(
            modifier = Modifier
                .size(148.dp)
                .onSizeChanged { canvasSize = Size(it.width.toFloat(), it.height.toFloat()) }
                .pointerInput(categories) {
                    detectTapGestures { offset ->
                        val center = Offset(canvasSize.width / 2f, canvasSize.height / 2f)
                        val stroke = 18.dp.toPx()
                        val radius = canvasSize.minDimension / 2f
                        val distance = hypot(offset.x - center.x, offset.y - center.y)
                        // 只认圆环本身上的点击，点到圆心或图外一律忽略
                        if (distance < radius - stroke || distance > radius + 2.dp.toPx()) {
                            return@detectTapGestures
                        }
                        var angle = Math.toDegrees(
                            atan2(
                                (offset.y - center.y).toDouble(),
                                (offset.x - center.x).toDouble()
                            )
                        ).toFloat()
                        angle = (angle + 90f + 360f) % 360f
                        var acc = 0f
                        categories.forEachIndexed { index, item ->
                            val fraction = if (total > 0L) item.amount.toFloat() / total else 0f
                            val sweep = fraction * 360f
                            if (angle in acc..(acc + sweep)) {
                                onSelect(if (selectedIndex == index) null else index)
                                return@detectTapGestures
                            }
                            acc += sweep
                        }
                    }
                }
        ) {
            val stroke = 18.dp.toPx()
            val diameter = size.minDimension - stroke
            val topLeft = Offset((size.width - diameter) / 2f, (size.height - diameter) / 2f)
            var startAngle = -90f
            categories.forEachIndexed { index, item ->
                val fraction = if (total > 0L) item.amount.toFloat() / total else 0f
                if (fraction <= 0f) {
                    return@forEachIndexed
                }
                val sweep = fraction * 360f * sweepProgress.value
                val isSelected = selectedIndex == index
                drawArc(
                    color = donutColor(index).copy(alpha = if (selectedIndex == null || isSelected) 1f else 0.35f),
                    startAngle = startAngle,
                    // 留 1.5° 缝隙；占比极小的分类也要看得见，所以保底 1°
                    sweepAngle = (sweep - 1.5f).coerceAtLeast(1f),
                    useCenter = false,
                    style = Stroke(width = if (isSelected) stroke + 4.dp.toPx() else stroke),
                    topLeft = topLeft,
                    size = Size(diameter, diameter)
                )
                startAngle += fraction * 360f
            }
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = selected?.name ?: label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = money(selected?.amount ?: total, hideAmounts),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            if (selected != null) {
                Text(
                    text = "占比 ${(selected.ratio * 100).roundToInt()}%",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/* ==================== 排行 ==================== */

/** 账户排行：钱从哪张卡进出 */
@Composable
private fun AccountRankCard(
    ranks: List<RankItem>,
    rankType: Int,
    hideAmounts: Boolean,
    onSwitchType: (Int) -> Unit,
    onOpen: (Long) -> Unit
) {
    CardBox {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("账户排行", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.weight(1f))
            SegmentedTabs(
                items = listOf("支出", "收入"),
                selectedIndex = if (rankType == 2) 1 else 0,
                onSelect = { onSwitchType(if (it == 1) 2 else 1) },
                selectedColor = if (rankType == 2) AppColors.income else AppColors.expense,
                modifier = Modifier.width(130.dp)
            )
        }
        Spacer(Modifier.height(8.dp))

        if (ranks.isEmpty()) {
            EmptyHint("这段没有账户流水")
        } else {
            ranks.forEachIndexed { index, item ->
                RankRow(
                    index = index,
                    item = item,
                    color = if (rankType == 2) AppColors.income else AppColors.expense,
                    hideAmounts = hideAmounts,
                    amountSuffix = " · ${item.count} 笔",
                    onOpen = { onOpen(item.key) }
                )
            }
        }
    }
}

/** 标签排行：本地标签是逗号串，这里显示的是拆分后的聚合结果 */
@Composable
private fun TagRankCard(
    ranks: List<RankItem>,
    rankType: Int,
    hideAmounts: Boolean,
    onSwitchType: (Int) -> Unit,
    onOpen: (String) -> Unit
) {
    CardBox {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("标签排行", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.weight(1f))
            SegmentedTabs(
                items = listOf("支出", "收入"),
                selectedIndex = if (rankType == 2) 1 else 0,
                onSelect = { onSwitchType(if (it == 1) 2 else 1) },
                selectedColor = if (rankType == 2) AppColors.income else AppColors.expense,
                modifier = Modifier.width(130.dp)
            )
        }
        Spacer(Modifier.height(8.dp))

        if (ranks.isEmpty()) {
            EmptyHint("还没有带标签的账单，记账时加个标签就能在这里看到排行")
        } else {
            ranks.forEachIndexed { index, item ->
                RankRow(
                    index = index,
                    item = item,
                    color = if (rankType == 2) AppColors.income else AppColors.expense,
                    hideAmounts = hideAmounts,
                    amountSuffix = " · ${item.count} 笔",
                    onOpen = { onOpen(item.name) }
                )
            }
        }
    }
}

@Composable
private fun RankRow(
    index: Int,
    item: RankItem,
    color: Color,
    hideAmounts: Boolean,
    amountSuffix: String,
    onOpen: () -> Unit
) {
    val ratio = item.ratio.coerceIn(0f, 1f)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onOpen)
            .padding(horizontal = 4.dp, vertical = 5.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "${index + 1}",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = if (index < 3) color else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(18.dp)
            )
            if (item.icon != null) {
                Icon(
                    imageVector = accountIconOf(item.icon),
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.width(6.dp))
            }
            Text(item.name, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.weight(1f))
            Text(
                text = money(item.amount, hideAmounts, suffix = ""),
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
        Spacer(Modifier.height(2.dp))
        Text(
            text = "占比 ${(item.ratio * 100).roundToInt()}%$amountSuffix",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/* ==================== 记账习惯 ==================== */

/**
 * 记账习惯：把「记了多少、怎么记的」量化出来。
 * <p>
 * 这一块没有图表，全是数字 —— 但它才是「这段时间记账这件事本身」的画像：
 * 记了几天、平均一天几笔、最大单日花在哪天、哪一天最容易超支。
 */
@Composable
private fun HabitCard(habit: HabitStats, period: ReportPeriod, hideAmounts: Boolean) {
    // 点中的星期（0=周一 … 6=周日），再点一次收起
    var pickedWeekday by remember(habit) { mutableStateOf<Int?>(null) }

    CardBox {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("记账习惯", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.weight(1f))
            Text(
                text = period.shortLabel,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.height(10.dp))

        Row(modifier = Modifier.fillMaxWidth()) {
            StatItem("记账天数", "${habit.activeDays} 天", MaterialTheme.colorScheme.onSurface, Modifier.weight(1f))
            StatItem("笔数", "${habit.txCount}", MaterialTheme.colorScheme.onSurface, Modifier.weight(1f))
            StatItem(
                label = "日均笔数",
                value = "%.1f".format(habit.avgTxPerDay),
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
        }
        Spacer(Modifier.height(10.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            StatItem(
                label = "日均流水",
                value = money(habit.avgAmountPerDay, hideAmounts),
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            StatItem(
                label = "单笔均值",
                value = money(habit.avgAmountPerTx, hideAmounts),
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            StatItem(
                label = "最大单日",
                value = money(habit.maxDay?.expense ?: 0L, hideAmounts),
                color = AppColors.expense,
                modifier = Modifier.weight(1f)
            )
        }

        if (habit.maxDay != null) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = "最高支出在 ${habit.maxDay.date.format(DAY_TEXT)}；最省的一天 ${money(habit.minDay?.expense ?: 0L, hideAmounts)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(Modifier.height(14.dp))
        Text(
            text = "星期分布（支出）",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(8.dp))
        WeekdayBars(
            values = habit.weekdayExpense,
            busiestWeekday = habit.busiestWeekday,
            hideAmounts = hideAmounts,
            selectedIndex = pickedWeekday,
            onSelect = { index -> pickedWeekday = if (pickedWeekday == index) null else index }
        )
        Spacer(Modifier.height(6.dp))
        val weekday = pickedWeekday
        val weekdayTotal = habit.weekdayExpense.sum()
        if (weekday == null) {
            Text(
                text = "点某根柱子 → 看这个星期花掉多少",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            val amount = habit.weekdayExpense.getOrElse(weekday) { 0L }
            Text(
                text = "周${WEEKDAY_SHORT[weekday + 1]} 支出 ${money(amount, hideAmounts)} · " +
                    "占当期支出 ${shareText(amount, weekdayTotal)}" +
                    if (weekday == habit.busiestWeekday) " · 当期花钱最多的一天" else "",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (habit.firstRecordTime != null && habit.lastRecordTime != null) {
            Spacer(Modifier.height(10.dp))
            Text(
                text = "最早一笔 ${timeText(habit.firstRecordTime)} · 最晚一笔 ${timeText(habit.lastRecordTime)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** 星期支出柱：周一到周日，最高的一天用实色标出来；点一根柱子把它选中（详情文字在卡片里） */
@Composable
private fun WeekdayBars(
    values: List<Long>,
    busiestWeekday: Int?,
    hideAmounts: Boolean,
    selectedIndex: Int?,
    onSelect: (Int) -> Unit
) {
    val max = values.maxOrNull() ?: 0L
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        values.forEachIndexed { index, value ->
            // busiestWeekday 是 SQLite 口径（0=周日），展示下标是周一起算
            val weekday = (index + 1) % 7
            val isBusiest = busiestWeekday == weekday && value > 0L
            val isSelected = selectedIndex == index
            val fraction = if (max <= 0L) 0f else (value.toFloat() / max).coerceIn(0f, 1f)
            val barShape = RoundedCornerShape(4.dp)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onSelect(index) }
                    .background(
                        if (isSelected) {
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        } else {
                            Color.Transparent
                        }
                    )
                    .padding(vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = if (hideAmounts) "****" else compactAmount(value),
                    style = MaterialTheme.typography.labelSmall,
                    color = when {
                        isBusiest -> AppColors.expense
                        isSelected -> MaterialTheme.colorScheme.onSurface
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
                Spacer(Modifier.height(2.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height((6 + fraction * 42).dp)
                        .clip(barShape)
                        .background(
                            when {
                                isBusiest -> AppColors.expense
                                isSelected -> AppColors.expense.copy(alpha = 0.7f)
                                else -> AppColors.expense.copy(alpha = 0.35f)
                            }
                        )
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = WEEKDAY_SHORT[index + 1],
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
        }
    }
}

/* ==================== 预算 ==================== */

/** 预算进度卡片。未设置预算时展示引导文案 */
@Composable
private fun BudgetCard(
    budget: BudgetOverviewDto?,
    title: String,
    hideAmounts: Boolean,
    onEdit: () -> Unit
) {
    CardBox {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = title,
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
                text = money(budget.totalUsed, hideAmounts, suffix = ""),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = if (budget.exceeded) AppColors.expense else MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text = "/ ${money(budget.totalBudget, hideAmounts)}",
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
                value = money(budget.remaining, hideAmounts),
                color = if (budget.exceeded) AppColors.expense else AppColors.income,
                modifier = Modifier.weight(1f)
            )
            StatItem(
                label = "日均可用",
                value = money(budget.dailyAvailable, hideAmounts),
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
                            text = "${money(item.used, hideAmounts, suffix = "")} / ${money(item.amount, hideAmounts, suffix = "")}",
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
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = if (item.exceeded) AppColors.expense else AppColors.accent,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                }
            }
        }
    }
}

/* ==================== 成员 ==================== */

/** 家庭账本：谁花了多少 / 谁记了多少 */
@Composable
private fun MemberCard(
    members: MemberReportDto?,
    hideAmounts: Boolean,
    onOpen: (String) -> Unit
) {
    CardBox {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("家庭成员", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.weight(1f))
            members?.let {
                Text(
                    text = "人均支出 ${money(it.items.size.let { size -> if (size == 0) 0L else it.totalExpense / size }, hideAmounts)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(Modifier.height(10.dp))

        if (members == null || members.items.isEmpty()) {
            EmptyHint("这段还没有家庭成员记账")
            return@CardBox
        }

        val maxExpense = members.items.maxOf { it.expense }
        members.items.forEach { item: MemberStatDto ->
            val ratio = if (maxExpense <= 0L) 0f else (item.expense.toFloat() / maxExpense).coerceIn(0f, 1f)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onOpen(item.label) }
                    .padding(vertical = 6.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(item.label, style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.weight(1f))
                    Text(
                        text = "支出 ${money(item.expense, hideAmounts, suffix = "")}",
                        style = MaterialTheme.typography.labelSmall,
                        color = AppColors.expense
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = "收入 ${money(item.income, hideAmounts, suffix = "")}",
                        style = MaterialTheme.typography.labelSmall,
                        color = AppColors.income
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = "结余 ${money(item.balance, hideAmounts, suffix = "")}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    LinearProgressIndicator(
                        progress = { ratio },
                        modifier = Modifier
                            .weight(1f)
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = AppColors.expense,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "${item.count} 笔",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/* ==================== 资产 ==================== */

/**
 * 资产分布：先给净资产，再拆资产 / 负债。
 * <p>
 * 信用卡余额为负 = 欠款，单独算负债；只有「计入总资产」的账户才参与合计，
 * 只用来走账的中间账户会在列表里标注出来，不会被算进身家。
 */
@Composable
private fun AssetCard(
    assets: AssetSummary,
    hideAmounts: Boolean,
    onManage: (() -> Unit)?
) {
    CardBox {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("资产分布", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            if (onManage != null) {
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onManage) { Text("资产管理") }
            }
        }

        Spacer(Modifier.height(6.dp))
        Text(
            text = "净资产 ${money(assets.netAssets, hideAmounts)}",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            StatItem(
                label = "资产",
                value = money(assets.totalAssets, hideAmounts),
                color = AppColors.income,
                modifier = Modifier.weight(1f)
            )
            StatItem(
                label = "负债",
                value = money(assets.totalLiabilities, hideAmounts),
                color = AppColors.expense,
                modifier = Modifier.weight(1f)
            )
            StatItem(
                label = "账户数",
                value = "${assets.accounts.size} 个",
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(Modifier.height(10.dp))
        if (assets.accounts.isEmpty()) {
            EmptyHint("暂无账户")
            return@CardBox
        }

        val counted = assets.accounts.filter { it.includeInTotal == 1 }
        val uncounted = assets.accounts.filter { it.includeInTotal != 1 }
        val maxAbs = counted.maxOfOrNull { kotlin.math.abs(it.balance) } ?: 0L

        counted.sortedByDescending { kotlin.math.abs(it.balance) }.forEach { account ->
            val isCredit = account.type == AssetSummary.CREDIT_CARD_TYPE
            val amount = if (isCredit) -account.balance else account.balance
            val ratio = if (maxAbs <= 0L) 0f else (kotlin.math.abs(account.balance).toFloat() / maxAbs).coerceIn(0f, 1f)
            Column(modifier = Modifier.padding(vertical = 5.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = accountIconOf(account.icon),
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(account.name, style = MaterialTheme.typography.bodyMedium)
                    if (isCredit) {
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = "信用",
                            style = MaterialTheme.typography.labelSmall,
                            color = AppColors.expense
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    Text(
                        text = money(amount, hideAmounts, suffix = ""),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = if (isCredit) AppColors.expense else MaterialTheme.colorScheme.onSurface
                    )
                }
                Spacer(Modifier.height(4.dp))
                LinearProgressIndicator(
                    progress = { ratio },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = if (isCredit) AppColors.expense else AppColors.income,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
            }
        }

        if (uncounted.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            Text(
                text = "不计入总资产：" + uncounted.joinToString("、") { it.name },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/* ==================== 通用组件 ==================== */

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

/**
 * 分段控件：一个浅色底槽 + 若干等宽分片。
 * <p>
 * 页面里所有「二选一 / 三选一」都用它（日/月/年、支出/收入），视觉上保持一致。
 */
@Composable
private fun SegmentedTabs(
    items: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    selectedColor: Color = MaterialTheme.colorScheme.primary,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(3.dp)
    ) {
        items.forEachIndexed { index, label ->
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (index == selectedIndex) selectedColor else Color.Transparent)
                    .clickable { onSelect(index) }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = label,
                    color = if (index == selectedIndex) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (index == selectedIndex) FontWeight.Bold else FontWeight.Normal
                )
            }
        }
    }
}

/** 趋势图图例：形状要跟图里一致（竖条=柱、横线=线），只给个色点容易对不上 */
@Composable
private fun BarLegend(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .width(6.dp)
                .height(12.dp)
                .clip(RoundedCornerShape(2.dp))
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

@Composable
private fun LineLegend(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .width(14.dp)
                .height(2.5.dp)
                .clip(RoundedCornerShape(2.dp))
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

@Composable
private fun LegendSwatch(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(RoundedCornerShape(3.dp))
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

@Composable
private fun StatItem(label: String, value: String, color: Color, modifier: Modifier) {
    Column(modifier = modifier) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(2.dp))
        Text(value, style = MaterialTheme.typography.bodyMedium, color = color, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun EmptyHint(text: String = "暂无数据") {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(vertical = 8.dp)
    )
}

/* ==================== 小工具 ==================== */

private val WEEKDAY_SHORT = listOf("日", "一", "二", "三", "四", "五", "六")
private val MONTH_TEXT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy 年 M 月")
private val DAY_TEXT: DateTimeFormatter = DateTimeFormatter.ofPattern("M 月 d 日")
private val TIME_TEXT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

/**
 * 隐私模式下的金额文案。
 * <p>
 * 只给「¥1,200.00」这一种写法 —— 之前写成「¥1,200.00 元」，¥ 和「元」重复得像打错字。
 */
private fun money(value: Long, hideAmounts: Boolean, suffix: String = ""): String =
    if (hideAmounts) "****$suffix" else "¥${formatAmount(value)}$suffix"

/** 星期柱上方的紧凑金额：不到 1000 显示整数，往上用 k / w，免得柱子被长数字撑变形 */
private fun compactAmount(value: Long): String {
    val yuan = value / 100.0
    return when {
        yuan <= 0.0 -> "-"
        yuan < 1000 -> yuan.roundToInt().toString()
        yuan < 10000 -> "%.1fk".format(yuan / 1000)
        else -> "%.1fw".format(yuan / 10000)
    }
}

private fun timeText(millis: Long): String =
    java.time.Instant.ofEpochMilli(millis)
        .atZone(java.time.ZoneId.systemDefault())
        .toLocalTime()
        .format(TIME_TEXT)

/** 环比 / 同比的对照期名称 */
private val ReportPeriod.previousLabel: String
    get() = when (granularity) {
        ReportGranularity.DAY -> "昨天"
        ReportGranularity.MONTH -> "上月"
        ReportGranularity.YEAR -> "去年"
    }

private val ReportPeriod.lastYearLabel: String
    get() = when (granularity) {
        ReportGranularity.DAY -> "去年同期"
        ReportGranularity.MONTH -> "去年同月"
        ReportGranularity.YEAR -> "去年"
    }

/* ==================== 预览 ==================== */

private fun previewPeriod(granularity: ReportGranularity = ReportGranularity.MONTH): ReportPeriod =
    ReportPeriod(granularity, LocalDate.of(2026, 10, 16))

private fun previewState(empty: Boolean = false): ReportViewModel.UiState {
    if (empty) {
        return ReportViewModel.UiState(bookId = 1L, loaded = true, period = previewPeriod())
    }

    val slices = listOf(
        CategorySlice(1L, "餐饮", "restaurant", 128600, 0.42f, listOf(
            CategorySlice(11L, "早餐", "restaurant", 32000, 0.1f),
            CategorySlice(12L, "外卖", "restaurant", 96600, 0.32f)
        )),
        CategorySlice(2L, "交通", "bus", 68200, 0.22f),
        CategorySlice(3L, "购物", "shopping", 54100, 0.18f),
        CategorySlice(4L, "居住", "home", 32000, 0.11f),
        CategorySlice(5L, "娱乐", "game", 21000, 0.07f)
    )

    return ReportViewModel.UiState(
        bookId = 1L,
        loaded = true,
        period = previewPeriod(),
        overview = ReportOverview(
            periodLabel = "2026 年 10 月",
            income = 1560000,
            expense = 303900,
            balance = 1256100,
            incomeCount = 3,
            expenseCount = 28,
            transferCount = 2,
            transferAmount = 500000,
            activeDays = 14,
            avgExpensePerDay = 21707,
            avgIncomePerDay = 111428,
            expenseChainRatio = 0.128,
            incomeChainRatio = -0.05,
            expenseYoYRatio = 0.342,
            incomeYoYRatio = 0.1
        ),
        trend = (1..31).map {
            TrendItemDto(
                period = "$it 日",
                income = if (it == 10) 1560000 else 0,
                expense = if (it % 4 == 0) 68000 else 12000
            )
        },
        categories = slices,
        categoryTotal = 303900,
        calendar = (1..16).map {
            DayAmount(LocalDate.of(2026, 10, it), 0, if (it % 3 == 0) 48000 else 12000)
        },
        accountRanks = listOf(
            RankItem(1L, "招商银行储蓄卡", "bank", 152000, 0.5f, 12),
            RankItem(2L, "支付宝", "online", 84000, 0.28f, 9),
            RankItem(3L, "微信零钱", "online", 41000, 0.14f, 5)
        ),
        tagRanks = listOf(
            RankItem(1L, "通勤", null, 42000, 0.4f, 8),
            RankItem(2L, "聚餐", null, 36000, 0.34f, 3)
        ),
        habit = HabitStats(
            activeDays = 14,
            txCount = 31,
            avgTxPerDay = 2.2,
            avgAmountPerDay = 21707,
            avgAmountPerTx = 9803,
            maxDay = DayAmount(LocalDate.of(2026, 10, 12), 0, 96000),
            minDay = DayAmount(LocalDate.of(2026, 10, 3), 0, 800),
            busiestWeekday = 6,
            weekdayExpense = listOf(28000, 31000, 26000, 42000, 51000, 96000, 30000),
            firstRecordTime = 1759560000000,
            lastRecordTime = 1759640000000
        ),
        budget = BudgetOverviewDto(
            period = "2026-10",
            hasBudget = true,
            totalBudgetId = 99L,
            totalBudget = 500000,
            totalUsed = 303900,
            items = emptyList()
        ),
        assets = AssetSummary(
            totalAssets = 5680000,
            totalLiabilities = 120000,
            netAssets = 5560000,
            accounts = emptyList()
        )
    )
}

/** 单卡预览：统计页依赖 ViewModel，整页预览价值不大，按组预览几张卡更方便调样式 */
@Preview(showBackground = true)
@Composable
private fun ReportOverviewPreview() {
    val state = previewState()
    JizhangTheme {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OverviewCard(state.overview, state.period, hideAmounts = false, onOpen = {})
            TrendCard(state.trend, state.period, hideAmounts = false, onOpenRange = { _, _ -> })
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun ReportBreakdownPreview() {
    val state = previewState()
    JizhangTheme {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            state.habit?.let { HabitCard(it, state.period, hideAmounts = false) }
            AccountRankCard(
                ranks = state.accountRanks,
                rankType = 1,
                hideAmounts = false,
                onSwitchType = {},
                onOpen = {}
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun ReportEmptyPreview() {
    val state = previewState(empty = true)
    JizhangTheme {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OverviewCard(state.overview, state.period, hideAmounts = false, onOpen = {})
            CalendarCard(
                days = state.calendar,
                period = state.period,
                hideAmounts = false,
                dayDetail = state.dayDetail,
                onPickDay = {},
                onCloseDay = {},
                onOpenDay = {},
                onOpenBills = {}
            )
        }
    }
}
