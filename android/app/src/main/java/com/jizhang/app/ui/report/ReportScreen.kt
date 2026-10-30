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
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
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
import com.jizhang.app.data.repository.CategorySlice
import com.jizhang.app.data.repository.DayAmount
import com.jizhang.app.data.repository.DayDetail
import com.jizhang.app.data.repository.HabitStats
import com.jizhang.app.data.repository.InvestmentSummary
import com.jizhang.app.data.repository.RankItem
import com.jizhang.app.data.repository.ReportGranularity
import com.jizhang.app.data.repository.ReportOverview
import com.jizhang.app.data.repository.ReportPeriod
import com.jizhang.app.data.repository.WealthProgress
import com.jizhang.app.ui.bill.BillPreset
import com.jizhang.app.ui.common.HeatCell
import com.jizhang.app.ui.common.MonthGrid
import com.jizhang.app.ui.common.SmallAction
import com.jizhang.app.ui.common.WEEKDAY_SHORT
import com.jizhang.app.ui.common.accountIconOf
import com.jizhang.app.ui.common.categoryIconOf
import com.jizhang.app.ui.common.compactAmount
import com.jizhang.app.ui.common.daysAgoText
import com.jizhang.app.ui.common.formatAmount
import com.jizhang.app.ui.common.money
import com.jizhang.app.ui.common.percentText
import com.jizhang.app.ui.common.signedMoney
import com.jizhang.app.ui.common.weekdayShortOf
import com.jizhang.app.ui.theme.AppColors
import com.jizhang.app.ui.theme.JizhangTheme
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberBottom
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
import com.patrykandpatrick.vico.core.cartesian.CartesianMeasuringContext
import com.patrykandpatrick.vico.core.cartesian.axis.Axis
import com.patrykandpatrick.vico.core.cartesian.axis.BaseAxis
import com.patrykandpatrick.vico.core.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.core.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.core.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.core.cartesian.data.CartesianValueFormatter
import com.patrykandpatrick.vico.core.cartesian.data.columnSeries
import com.patrykandpatrick.vico.core.cartesian.data.lineSeries
import com.patrykandpatrick.vico.core.cartesian.layer.ColumnCartesianLayer
import com.patrykandpatrick.vico.core.cartesian.layer.LineCartesianLayer
import com.patrykandpatrick.vico.core.cartesian.marker.CartesianMarker
import com.patrykandpatrick.vico.core.cartesian.marker.CartesianMarkerVisibilityListener
import java.text.DecimalFormat
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import kotlin.math.abs
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
 * 隐私模式（[hideAmounts]）在这里是**全覆盖**的：环形图圆心、排行、日历、趋势纵轴都会打码；
 * 趋势图更进一步 —— 柱子改画「有 / 没有」，净值线不画，否则相对高度 / 走势照样能反推金额。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportScreen(
    bookId: Long,
    shared: Boolean = false,
    /** 金额隐私模式：金额与图表刻度显示成 ****（设置里的开关） */
    hideAmounts: Boolean = false,
    /**
     * 外部数据版本号：导入 / 清空账单后由 MainScreen +1。
     * <p>
     * 本页的聚合全是「查一次」的，而 ViewModel 挂在 Activity 上、切 Tab 回来 `bind()` 会早退，
     * 所以导入这类批量写库之后显式重取一次，避免要重启应用才看到新数字。
     */
    dataVersion: Int = 0,
    /**
     * 财富页关闭后 +1：那边改过提取率假设，卡片上的进度要按新口径重算。
     */
    wealthVersion: Int = 0,
    /** 「资产管理」入口 */
    onOpenAccounts: (() -> Unit)? = null,
    /** 「财务自由」入口：把净资产换成一个"还差多远"的进度 */
    onOpenWealth: (() -> Unit)? = null,
    /** 「投资」入口：持仓品种与市值（资产分布卡片底部） */
    onOpenInvest: (() -> Unit)? = null,
    /** 点分类 / 账户 / 标签看明细：切到账单页并带上筛选条件 */
    onOpenBills: (BillPreset) -> Unit = {},
    /** 日历卡片的「在日历里按月翻看」：切到日历 Tab */
    onOpenCalendar: () -> Unit = {},
    /** 预算卡片的「调整」：设置预算覆盖层挂在 MainScreen 根部，这里只上报意图 */
    onOpenBudgetEditor: () -> Unit = {},
    viewModel: ReportViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(bookId, shared) { viewModel.bind(bookId, shared) }

    // 导入 / 清空账单后重算整页聚合。放在 bind 之后：首次组合时 bookId 已就位。
    LaunchedEffect(dataVersion) {
        if (dataVersion > 0) {
            viewModel.load()
        }
    }

    LaunchedEffect(wealthVersion) {
        if (wealthVersion > 0) {
            viewModel.refreshWealth()
        }
    }

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
                            // 净资产是唯一要在这个位置出现的「存量」：看流量时也得知道身家有多少
                            netAssets = state.assets.netAssets,
                            wealth = state.wealth,
                            onOpenAssets = onOpenAccounts,
                            onOpenWealth = onOpenWealth,
                            // 点收入 / 支出 / 结余 → 账单页只看对应的那部分
                            onOpen = { type -> openBills(type = type) }
                        )
                    }
                    // 预算紧跟总览：本期花得离额度还有多远，是打开统计页的第二诉求
                    item { BudgetCard(state.budget, state.budgetTitle, hideAmounts, onEdit = onOpenBudgetEditor) }
                    item {
                        TrendCard(
                            trend = state.trend,
                            netAssets = state.netAssetTrend,
                            period = state.period,
                            hideAmounts = hideAmounts,
                            onOpenRange = { start, end -> openBills(startMillis = start, endMillis = end - 1) }
                        )
                    }
                    // 日历卡是「当期概览」：周视图已由趋势图逐日铺开，再摆一个月历会和「只看这一周」打架；
                    // 年视图只在有数据时给热力图
                    if (state.period.granularity == ReportGranularity.MONTH ||
                        (state.period.granularity == ReportGranularity.YEAR && state.calendar.isNotEmpty())
                    ) {
                        item {
                            CalendarCard(
                                days = state.calendar,
                                period = state.period,
                                hideAmounts = hideAmounts,
                                dayDetail = state.dayDetail,
                                onPickDay = viewModel::selectDay,
                                onCloseDay = viewModel::clearDayDetail,
                                onOpenCalendar = onOpenCalendar,
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
                    // 账户 / 标签合成一张「排行」卡：原来两张卡各挂一个「支出/收入」开关，
                    // 绑的却是同一个 rankType，切一个另一个跟着变，看着像 bug
                    item {
                        RankCard(
                            accountRanks = state.accountRanks,
                            tagRanks = state.tagRanks,
                            rankType = state.rankType,
                            hideAmounts = hideAmounts,
                            onSwitchType = viewModel::switchRankType,
                            onOpenAccount = { openBills(accountId = it) },
                            onOpenTag = { openBills(keyword = it) }
                        )
                    }
                    item {
                        AssetCard(
                            assets = state.assets,
                            hideAmounts = hideAmounts,
                            onManage = onOpenAccounts,
                            onOpenInvest = onOpenInvest
                        )
                    }
                    // 投资明细紧跟资产分布：资产分布说「有多少钱在投资账户」，
                    // 这张卡说「投了多少、现在值多少、差在哪」，两卡相邻才好对
                    state.investment?.let { investment ->
                        item {
                            InvestmentCard(
                                summary = investment,
                                hideAmounts = hideAmounts,
                                onOpen = onOpenInvest
                            )
                        }
                    }
                    state.habit?.let { habit ->
                        item {
                            HabitCard(
                                habit = habit,
                                hideAmounts = hideAmounts,
                                folded = ReportFoldableCard.HABIT in state.foldedCards,
                                onToggle = { viewModel.toggleCard(ReportFoldableCard.HABIT) }
                            )
                        }
                    }
                    if (state.shared) {
                        item {
                            MemberCard(
                                members = state.members,
                                hideAmounts = hideAmounts,
                                folded = ReportFoldableCard.MEMBERS in state.foldedCards,
                                onToggle = { viewModel.toggleCard(ReportFoldableCard.MEMBERS) },
                                onOpen = { openBills(keyword = it) }
                            )
                        }
                    }
                }
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter)
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
                items = REPORT_GRANULARITY_TABS.map { it.label },
                // 用 indexOf 而不是 ordinal：DAY 仍留在枚举里（单日时间窗要用），不能让它占档位
                selectedIndex = REPORT_GRANULARITY_TABS.indexOf(period.granularity).coerceAtLeast(0),
                onSelect = { index ->
                    viewModel.setGranularity(REPORT_GRANULARITY_TABS[index])
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

/**
 * 统计粒度的筛选档位：周 / 月 / 年。
 * <p>
 * 不用 `ReportGranularity.entries`：日粒度还在枚举里给「单日时间窗」用，但不该占一个档位。
 */
private val REPORT_GRANULARITY_TABS = listOf(
    ReportGranularity.WEEK,
    ReportGranularity.MONTH,
    ReportGranularity.YEAR
)

/** 期间选择面板：按粒度给出年网格 / 年月网格 / 月历（周粒度点某天即落到那一周） */
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

        // 周和日都用月历选：点哪天就落到那天所在周（ReportPeriod 会把锚点推到周一）
        ReportGranularity.WEEK, ReportGranularity.DAY ->
            DayPicker(YearMonth.from(period.anchor), period.anchor, today, onPick)
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
    /** 当前净资产（实时存量，不属于本期）：流量卡里也要给一根「身家」的锚 */
    netAssets: Long,
    /** 财务自由进度：只有进度条要用，缺数据时整行退化成入口 */
    wealth: WealthProgress? = null,
    /** 点净资产 → 资产管理；null 表示这个入口关掉了 */
    onOpenAssets: (() -> Unit)?,
    /** 点「财务自由」→ 财富规划页 */
    onOpenWealth: (() -> Unit)? = null,
    /** 点收入 / 支出 / 结余 → 去账单页看对应那部分（type = 2 收入、1 支出、null 全部） */
    onOpen: (Int?) -> Unit
) {
    CardBox {
        // 周期只在顶部时间轴条写一次：这里再写一遍是重复信息（而且它随时会被滚出屏幕）
        Text("收支总览", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)

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

        // 流量 + 存量放一张卡：本期收入和「我现在有多少钱」本来就是一次扫描里要一起看到的两个数
        Spacer(Modifier.height(10.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .then(
                    if (onOpenAssets != null) {
                        Modifier.clickable(onClick = onOpenAssets)
                    } else {
                        Modifier
                    }
                )
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "当前净资产",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = money(netAssets, hideAmounts),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.weight(1f))
            if (onOpenAssets != null) {
                Text(
                    text = "资产明细 ›",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // 「身家有多少」之后必然接着问「离不用上班还差多远」：
        // 这里不只是一行文字链接，而是一块带底色的入口 —— 报表页信息密度高，
        // 纯文字的小链接会被整屏数字淹没（实测确实没人找到）
        if (onOpenWealth != null) {
            Spacer(Modifier.height(10.dp))
            val hasProgress = wealth != null && wealth.fiNumberFen > 0L && wealth.hasEnoughData
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.07f))
                    .clickable(onClick = onOpenWealth)
                    .padding(horizontal = 10.dp, vertical = 8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.Insights,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = AppColors.accent
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "财务自由测算",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.weight(1f))
                    if (hasProgress && wealth != null) {
                        Text(
                            text = "${(wealth.progress * 100).roundToInt()}%",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = AppColors.accent
                        )
                    }
                    Text(
                        text = " ›",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (hasProgress && wealth != null) {
                    Spacer(Modifier.height(6.dp))
                    LinearProgressIndicator(
                        progress = { wealth.progress.coerceIn(0.0, 1.0).toFloat() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(5.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = AppColors.accent,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "还差 ${money(wealth.gapFen, hideAmounts)} · 点这里看概率与所需年限",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Text(
                        text = "用账本里的真实支出算 FI 资产与达标概率",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
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
        // 三列等宽：金额位数多时降一档字号，宁可小一点也不能折行 —— 折行会让三列基线错开
        val amount = money(value, hideAmounts, suffix = "")
        Text(
            text = amount,
            style = when {
                amount.length >= 12 -> MaterialTheme.typography.bodyMedium
                amount.length >= 10 -> MaterialTheme.typography.titleSmall
                else -> MaterialTheme.typography.titleMedium
            },
            color = color,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            softWrap = false
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
 * 用 Vico 的柱 + 折线组合：支出是柱（看总量），收入是折线（看趋势）。
 * 横轴标签由图表自己按桶中心摆放（[BucketLabelFormatter]），密度按粒度定 ——
 * 周视图 7 天全标，月视图每 5 天一个，不会和柱子错位。
 * <p>
 * 隐私模式**不是只藏刻度**：柱子高低本身就是金额，所以数据整形成「有 / 没有」两档，
 * 净值线直接不画（形状同样会暴露量级）。
 * <p>
 * 图上的柱子 / 折线点 / 下方净值线**都可点**：点中的那一格会在图下方摊开明细（支出 / 收入 / 结余 / 占比），
 * 想进一步看这批账就点「查看这批账单」——带着那一格的时间窗跳到账单页。
 */
@Composable
private fun TrendCard(
    trend: List<TrendItemDto>,
    /** 期末净资产序列（与 [trend] 等长）：存量指标，单独画一条窄线 */
    netAssets: List<Long>,
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
                    ReportGranularity.WEEK -> "按天"
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
        // 单位换成「元」：纵轴刻度不会长到六位数。
        // 隐私模式**不能只藏纵轴刻度**：柱子的相对高度本身就是金额，一比就能估出来，
        // 所以这里直接改喂「有 / 没有」两档，柱高只剩「这天有没有花钱」这一个信息
        LaunchedEffect(trend, hideAmounts) {
            val expenses = trend.map { it.expense.chartValue(hideAmounts) }
            val incomes = trend.map { it.income.chartValue(hideAmounts) }
            modelProducer.runTransaction {
                columnSeries { series(expenses) }
                lineSeries { series(incomes) }
            }
        }

        // 柱/线显式用主题色：不给颜色的话 Vico 会用自带的蓝色，
        // 于是「图例红=支出、绿=收入」和图里的蓝色对不上 —— 这正是之前看不懂的来源
        val expenseColor = AppColors.expense
        val incomeColor = AppColors.income

        // 横轴：交给 Vico 自己画，不再用一行 Text 拼 —— 图的左右边距由纵轴宽度决定，
        // 手拼的一行永远对不准柱子；交给图表，标签就落在桶中心上
        val axisLabels = trend.map { it.period }
        val labelStep = when (period.granularity) {
            // 周视图 7 根柱子，「周一…周日」正好一屏放得下，全标
            ReportGranularity.WEEK -> 1
            // 一天 24 小时太密，每 6 小时一个
            ReportGranularity.DAY -> 6
            // 一个月 28-31 天，5 天一格够定位又不糊
            ReportGranularity.MONTH -> 5
            // 「1 月」很短，12 个月全标也挤得下
            ReportGranularity.YEAR -> 1
        }
        val bottomAxis = HorizontalAxis.rememberBottom(
            itemPlacer = remember(labelStep) {
                HorizontalAxis.ItemPlacer.aligned(spacing = { labelStep }, offset = { 0 })
            },
            valueFormatter = remember(axisLabels) { BucketLabelFormatter(axisLabels) }
        )
        // 纵轴宽度固定：净值窄图用同一个宽度，两张图的横轴才对得上
        val yAxisSize = remember { BaseAxis.Size.Fixed(TREND_Y_AXIS_WIDTH_DP) }
        val yAxisFormatter = remember { YuanAxisFormatter() }

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
                startAxis = if (hideAmounts) null else VerticalAxis.rememberStart(
                    valueFormatter = yAxisFormatter,
                    size = yAxisSize
                ),
                bottomAxis = bottomAxis,
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

        Text(
            text = if (hideAmounts) {
                "柱 / 线只表示「有没有收支」，高度不代表金额"
            } else {
                "柱 = 支出，线 = 收入；两者共用左侧刻度，单位：元"
            },
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        // 净值是存量，量级通常比单格收支大一两个数量级：塞进主图会把柱子压成一条线，
        // 所以单独画一条窄线。横轴与主图共用：纵轴宽度固定成同一个值，两边的格子才对得上；
        // 点这条线也能选中（同一个 marker 回调），不用非得去够上面那根柱子
        if (netAssets.isNotEmpty() && netAssets.any { it != 0L }) {
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("期末净资产", style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.weight(1f))
                Text(
                    text = money(netAssets.last(), hideAmounts),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = AppColors.accent
                )
            }
            Spacer(Modifier.height(2.dp))
            if (hideAmounts) {
                // 线本身也会说话：一段陡升陡降就把「这段时间赚了多少」交代出去了。
                // 隐私模式下宁可不画，只留一句说明，也不留一个能被反推的形状
                Text(
                    text = "隐私模式下不画净值曲线（走势会暴露量级）",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                val netProducer = remember { CartesianChartModelProducer() }
                LaunchedEffect(netAssets) {
                    // 与主图一样换算成「元」，否则纵轴刻度会是六位数的分
                    netProducer.runTransaction {
                        lineSeries { series(netAssets.map { it / 100.0 }) }
                    }
                }
                CartesianChartHost(
                    chart = rememberCartesianChart(
                        rememberLineCartesianLayer(
                            lineProvider = LineCartesianLayer.LineProvider.series(
                                LineCartesianLayer.rememberLine(
                                    fill = LineCartesianLayer.LineFill.single(fill(AppColors.accent)),
                                    stroke = LineCartesianLayer.LineStroke.continuous(thickness = 2.dp)
                                )
                            )
                        ),
                        // 与主图同一个宽度 + 同一个格式化器，横轴才能严丝合缝
                        startAxis = VerticalAxis.rememberStart(
                            valueFormatter = yAxisFormatter,
                            size = yAxisSize
                        ),
                        marker = marker,
                        markerVisibilityListener = markerListener
                    ),
                    modelProducer = netProducer,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(76.dp)
                )
                Text(
                    text = "线 = 期末净资产（截至该周期最后一天）· 可点选",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

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
                netAsset = netAssets.getOrNull(picked),
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
    /** 这一格结束时的净资产；主图没数据时为空 */
    netAsset: Long?,
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
        if (netAsset != null) {
            Text(
                text = "这一格结束时的净资产 ${money(netAsset, hideAmounts)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** 某一格占当期总量的比例；当期是 0 时不给百分比，免得冒出一句「占 0%」 */
private fun shareText(part: Long, total: Long): String =
    if (total <= 0L) "—" else "${(part * 100.0 / total).roundToInt()}%"

/**
 * 趋势图纵轴的固定宽度（dp）。
 * <p>
 * 主图和下面的净值窄图必须一样宽：纵轴宽度就是绘图区的左边界，
 * 两边宽度不同，两条线一眼就能看出错位（各自按自己的标签宽度自适应时必然不同）。
 */
private const val TREND_Y_AXIS_WIDTH_DP = 44f

/**
 * 金额换算成图上喂给 Vico 的值（单位：元）。
 * <p>
 * 隐私模式只保留「有 / 没有」两档：柱子高低、折线起伏都是金额的另一种写法，
 * 光把纵轴刻度藏掉挡不住量级泄露。
 */
private fun Long.chartValue(hideAmounts: Boolean): Double = when {
    !hideAmounts -> this / 100.0
    this == 0L -> 0.0
    else -> 1.0
}

/**
 * 纵轴刻度：折算成「万 / 亿」，保证在 [TREND_Y_AXIS_WIDTH_DP] 里放得下。
 * <p>
 * 折算后**至少留一位小数**（3.2 万不写成 3 万）：刻度是读图时唯一的参照，
 * 这一格取整了，柱子高度就白画了。
 */
private class YuanAxisFormatter : CartesianValueFormatter {
    override fun format(
        context: CartesianMeasuringContext,
        value: Double,
        verticalAxisPosition: Axis.Position.Vertical?
    ): CharSequence {
        val magnitude = abs(value)
        return when {
            magnitude >= 100_000_000 -> "${AXIS_ONE_DECIMAL.format(value / 100_000_000)}亿"
            magnitude >= 10_000 -> "${AXIS_ONE_DECIMAL.format(value / 10_000)}万"
            magnitude >= 0.01 -> AXIS_TWO_DECIMALS.format(value)
            else -> "0"
        }
    }
}

/** 刻度上的小数位：`3` / `3.2` / `3.25`，不留无意义的 0 */
private val AXIS_ONE_DECIMAL: DecimalFormat = DecimalFormat("0.#")
private val AXIS_TWO_DECIMALS: DecimalFormat = DecimalFormat("0.##")

/** 横轴刻度：直接用桶名（周一 / 14 日 / 5 时 / 1 月），越界的 x 留空 */
private class BucketLabelFormatter(private val labels: List<String>) : CartesianValueFormatter {
    override fun format(
        context: CartesianMeasuringContext,
        value: Double,
        verticalAxisPosition: Axis.Position.Vertical?
    ): CharSequence = labels.getOrNull(value.roundToInt()).orEmpty()
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
    /** 看那天的账单 */
    onOpenBills: (LocalDate) -> Unit,
    /** 去日历页按月翻看（这张卡是「当期概览」，翻月是日历页的事） */
    onOpenCalendar: () -> Unit
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
                onOpenBills = { onOpenBills(picked.date) },
                onClose = onCloseDay
            )
        }

        Spacer(Modifier.height(8.dp))
        SmallAction("在日历里按月翻看 ›", onOpenCalendar)
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
        // 日视图已不在筛选档位里，这里只留「看当天账单」这一个出口
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SmallAction("看当天账单", onOpenBills)
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
                            imageVector = categoryIconOf(child.icon, child.name),
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
private fun RankCard(
    accountRanks: List<RankItem>,
    tagRanks: List<RankItem>,
    rankType: Int,
    hideAmounts: Boolean,
    onSwitchType: (Int) -> Unit,
    onOpenAccount: (Long) -> Unit,
    onOpenTag: (String) -> Unit
) {
    // 0 = 账户，1 = 标签。这是「看哪个维度」，与右上角「支出/收入」不是一回事，所以单独一行
    val tab = remember { mutableStateOf(0) }

    CardBox {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("排行", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
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

        // 维度切换用轻量文字 tab：同一个卡里出现两个一模一样的分段控件，主次就分不清了
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            listOf("账户", "标签").forEachIndexed { index, label ->
                val selected = tab.value == index
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (selected) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable { tab.value = index }
                        .padding(horizontal = 4.dp, vertical = 4.dp)
                )
            }
        }
        Spacer(Modifier.height(6.dp))

        val ranks = if (tab.value == 1) tagRanks else accountRanks
        if (ranks.isEmpty()) {
            EmptyHint(
                if (tab.value == 1) {
                    "还没有带标签的账单，记账时加个标签就能在这里看到排行"
                } else {
                    "这段没有账户流水"
                }
            )
        } else {
            ranks.forEachIndexed { index, item ->
                RankRow(
                    index = index,
                    item = item,
                    color = if (rankType == 2) AppColors.income else AppColors.expense,
                    hideAmounts = hideAmounts,
                    amountSuffix = " · ${item.count} 笔",
                    onOpen = {
                        if (tab.value == 1) onOpenTag(item.name) else onOpenAccount(item.key)
                    }
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
private fun HabitCard(
    habit: HabitStats,
    hideAmounts: Boolean,
    folded: Boolean,
    onToggle: () -> Unit
) {
    // 点中的星期（0=周一 … 6=周日），再点一次收起
    var pickedWeekday by remember(habit) { mutableStateOf<Int?>(null) }

    CardBox {
        // 周期同上：只有顶部时间轴条负责展示，卡片里不再重复
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .clickable(onClick = onToggle)
                .padding(vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("记账习惯", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.weight(1f))
            Icon(
                imageVector = if (folded) Icons.Filled.ExpandMore else Icons.Filled.ExpandLess,
                contentDescription = if (folded) "展开" else "收起",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
        }
        if (folded) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = "记账 ${habit.activeDays} 天 · ${habit.txCount} 笔 · " +
                    "日均 ${money(habit.avgAmountPerDay, hideAmounts)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            return@CardBox
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
                text = "周${weekdayShortOf(weekday)} 支出 ${money(amount, hideAmounts)} · " +
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
                    // 周日那根柱子要回到「日」：`WEEKDAY_SHORT[index + 1]` 在 index = 6 时会越界闪退
                    text = weekdayShortOf(index),
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
            // 全页金额统一一档字号：预算这块原来是 24sp，比总览还大，抢走了页面焦点
            Text(
                text = money(budget.totalUsed, hideAmounts, suffix = ""),
                style = MaterialTheme.typography.titleMedium,
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
    folded: Boolean,
    onToggle: () -> Unit,
    onOpen: (String) -> Unit
) {
    CardBox {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .clickable(onClick = onToggle)
                .padding(vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("家庭成员", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.weight(1f))
            members?.let {
                Text(
                    text = "人均支出 ${money(it.items.size.let { size -> if (size == 0) 0L else it.totalExpense / size }, hideAmounts)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.width(4.dp))
            }
            Icon(
                imageVector = if (folded) Icons.Filled.ExpandMore else Icons.Filled.ExpandLess,
                contentDescription = if (folded) "展开" else "收起",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
        }
        if (folded) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = "${members?.items?.size ?: 0} 位成员 · 支出 " +
                    money(members?.totalExpense ?: 0L, hideAmounts),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            return@CardBox
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
    onManage: (() -> Unit)?,
    onOpenInvest: (() -> Unit)? = null
) {
    // 投资账户（type = 6）：有才显示持仓入口，没投资账户的用户不该被这个入口打扰
    val hasInvestAccount = assets.accounts.any { it.type == 6 && it.includeInTotal == 1 }
    CardBox {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("资产分布", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            if (onManage != null) {
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onManage) { Text("资产管理") }
            }
        }

        // 净资产挪到「收支总览」底部了（流量 + 存量一次看完），这里只留资产 / 负债的结构
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
                // 投资账户的两个口径摆在一起：账户页看到的是本金，这里看到的是本金+浮盈。
                // 不写清楚的话，用户会以为 5000 和 4500 里必有一个算错了
                account.investProfitFen?.let { profit ->
                    Text(
                        text = "本金 " + money(account.balance - profit, hideAmounts) +
                            " · 浮动 " + (if (profit >= 0L) "+" else "-") +
                            money(kotlin.math.abs(profit), hideAmounts),
                        style = MaterialTheme.typography.labelSmall,
                        color = when {
                            profit > 0L -> AppColors.income
                            profit < 0L -> AppColors.expense
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.padding(start = 22.dp)
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

        if (hasInvestAccount && onOpenInvest != null) {
            Spacer(Modifier.height(8.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(onClick = onOpenInvest)
                    .padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Filled.TrendingUp,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = AppColors.accent
                )
                Spacer(Modifier.width(8.dp))
                Text("投资持仓", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.weight(1f))
                Text(
                    text = "品种与市值",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Icon(
                    imageVector = Icons.Filled.ChevronRight,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * 投资卡：把「本金 / 市值 / 浮动 / 未分配」四个数放在一起。
 * <p>
 * 数据来自 [InvestmentSummary]，与投资页、账户页共用同一份口径。
 * 这张卡的意义是：不点进投资页也能看明白「我投了多少、现在值多少、差在哪」，
 * 并且四个数之间能互相验算 —— 本金 + 浮动 = 投资资产。
 */
@Composable
private fun InvestmentCard(
    summary: InvestmentSummary,
    hideAmounts: Boolean,
    onOpen: (() -> Unit)?
) {
    val profit = summary.profitFen
    val profitColor = when {
        profit > 0L -> AppColors.income
        profit < 0L -> AppColors.expense
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    CardBox {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("投资", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.weight(1f))
            if (onOpen != null) {
                Text(
                    text = "品种与市值 ›",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(onClick = onOpen)
                        .padding(horizontal = 6.dp, vertical = 4.dp)
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        // 大字是市值：和券商 / 基金 App 里看到的数字对得上
        Text(
            text = money(summary.marketValueFen, hideAmounts),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = signedMoney(profit, hideAmounts) +
                (summary.profitRate?.let { "（" + percentText(it) + "）" } ?: ""),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = profitColor
        )
        Spacer(Modifier.height(10.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
        Spacer(Modifier.height(8.dp))
        InvestRow("本金（累计投入）", money(summary.principalFen, hideAmounts))
        InvestRow("未分配资金", money(summary.unallocatedFen, hideAmounts))
        InvestRow("上次估值", summary.lastValuedAt?.let { daysAgoText(it) } ?: "还没估过")
        Spacer(Modifier.height(6.dp))
        // 把恒等式直接写出来，用户能自己验算，不用猜哪两个数该相等
        Text(
            text = "本金 " + money(summary.principalFen, hideAmounts) +
                " + 浮动 " + signedMoney(profit, hideAmounts) +
                " = 投资资产 " + money(summary.assetFen, hideAmounts),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun InvestRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.weight(1f))
        Text(
            text = value,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium
        )
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
        // 三等分格子里放不下就降一档字号，绝不允许折行（折行会把相邻两格顶得高低不齐）
        Text(
            text = value,
            style = if (value.length >= 12) {
                MaterialTheme.typography.bodySmall
            } else {
                MaterialTheme.typography.bodyMedium
            },
            color = color,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            softWrap = false
        )
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

/*
 * 金额统一走 [com.jizhang.app.ui.common.money] / [formatAmount]：
 * 精确到分 + 千分位 + 去掉无意义的 0（`¥3.2` / `¥12,345.67`），口径全 App 只有这一份。
 * 宽度靠 [ValueBlock] / [StatItem] 的字号降档解决，不再用四舍五入换排版。
 */

private val MONTH_TEXT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy 年 M 月")
private val DAY_TEXT: DateTimeFormatter = DateTimeFormatter.ofPattern("M 月 d 日")
private val TIME_TEXT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

private fun timeText(millis: Long): String =
    java.time.Instant.ofEpochMilli(millis)
        .atZone(java.time.ZoneId.systemDefault())
        .toLocalTime()
        .format(TIME_TEXT)

/** 环比 / 同比的对照期名称 */
private val ReportPeriod.previousLabel: String
    get() = when (granularity) {
        ReportGranularity.WEEK -> "上周"
        ReportGranularity.DAY -> "昨天"
        ReportGranularity.MONTH -> "上月"
        ReportGranularity.YEAR -> "去年"
    }

private val ReportPeriod.lastYearLabel: String
    get() = when (granularity) {
        ReportGranularity.WEEK -> "去年同周"
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
            OverviewCard(
                state.overview,
                state.period,
                hideAmounts = false,
                netAssets = state.assets.netAssets,
                onOpenAssets = {},
                onOpen = {}
            )
            TrendCard(
                state.trend,
                state.netAssetTrend,
                state.period,
                hideAmounts = false,
                onOpenRange = { _, _ -> }
            )
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
            state.habit?.let {
                HabitCard(it, hideAmounts = false, folded = false, onToggle = {})
            }
            RankCard(
                accountRanks = state.accountRanks,
                tagRanks = state.tagRanks,
                rankType = 1,
                hideAmounts = false,
                onSwitchType = {},
                onOpenAccount = {},
                onOpenTag = {}
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
            OverviewCard(
                state.overview,
                state.period,
                hideAmounts = false,
                netAssets = state.assets.netAssets,
                onOpenAssets = {},
                onOpen = {}
            )
            CalendarCard(
                days = state.calendar,
                period = state.period,
                hideAmounts = false,
                dayDetail = state.dayDetail,
                onPickDay = {},
                onCloseDay = {},
                onOpenBills = {},
                onOpenCalendar = {}
            )
        }
    }
}
