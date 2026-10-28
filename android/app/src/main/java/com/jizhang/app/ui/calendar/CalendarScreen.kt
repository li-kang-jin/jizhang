package com.jizhang.app.ui.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jizhang.app.data.local.entity.LocalCategory
import com.jizhang.app.data.local.entity.LocalTransaction
import com.jizhang.app.data.repository.ReportGranularity
import com.jizhang.app.data.repository.ReportPeriod
import com.jizhang.app.ui.bill.BillPreset
import com.jizhang.app.ui.common.CategoryBadge
import com.jizhang.app.ui.common.MonthGrid
import com.jizhang.app.ui.common.SmallAction
import com.jizhang.app.ui.common.WEEKDAY_SHORT
import com.jizhang.app.ui.common.billTitle
import com.jizhang.app.ui.common.formatSignedAmount
import com.jizhang.app.ui.common.inlineTags
import com.jizhang.app.ui.common.money
import com.jizhang.app.ui.common.tagsUsedAsTitle
import com.jizhang.app.ui.common.typeColor
import com.jizhang.app.ui.theme.AppColors
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

/**
 * 日历页。
 * <p>
 * 按月在月历上看每日开支：色越深花得越多，点某一天在下方摊开当天的每一笔，
 * 左右滑动（或点箭头）切月，任何一个月都能秒开 —— 每翻一个月只查一次库，点某天不再查。
 * <p>
 * 页面分两张卡（和统计页一样的卡片语言）：
 * 上面一张是「这个月」，下面一张是「选中的这一天」—— 上卡选日期、下卡出明细，关系一眼能看出来。
 * 补记历史账、看某天的账、跳账单页核对，都在下卡里。
 */
@Composable
fun CalendarScreen(
    bookId: Long,
    hideAmounts: Boolean,
    /** 「在账单页看这一天」：带上当天的时间窗（左闭右闭）跳账单页 */
    onOpenBills: (BillPreset) -> Unit,
    /** 点当天某一笔 → 打开编辑（复用账单页那套编辑入口） */
    onEdit: (LocalTransaction) -> Unit,
    /** 「这天记一笔」：打开记账页并预填这个日期 */
    onAddBill: (LocalDate) -> Unit,
    viewModel: CalendarViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(bookId) { viewModel.bind(bookId) }

    if (!state.ready) {
        // 只在「最早一笔账单」查完之前出现，通常一两帧就过去
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    val pages = state.pages
    /*
     * 分页页码是「相对 pages.first」的，范围一变页码含义就变，
     * 所以按范围重建分页状态 —— 范围只在绑定账本时确定一次，之后不会再动。
     */
    key(pages.first, pages.last) {
        CalendarBody(
            state = state,
            pages = pages,
            hideAmounts = hideAmounts,
            viewModel = viewModel,
            onOpenBills = onOpenBills,
            onEdit = onEdit,
            onAddBill = onAddBill
        )
    }
}

@Composable
private fun CalendarBody(
    state: CalendarViewModel.UiState,
    pages: IntRange,
    hideAmounts: Boolean,
    viewModel: CalendarViewModel,
    onOpenBills: (BillPreset) -> Unit,
    onEdit: (LocalTransaction) -> Unit,
    onAddBill: (LocalDate) -> Unit
) {
    val scope = rememberCoroutineScope()
    val today = remember { LocalDate.now() }
    val lastPage = (pages.last - pages.first).coerceAtLeast(0)

    val pagerState = rememberPagerState(initialPage = lastPage, pageCount = { pages.last - pages.first + 1 })

    // 标题、概览、格子都跟着「当前这一页」走：滑动过程中就更新，不用等停稳
    val visibleMonth = CalendarMonths.monthOf(pages.first + pagerState.currentPage)
    val visibleData = state.months[visibleMonth]

    // 停到哪一页就取哪个月，顺带预取相邻月，滑动时才不会先看到一片灰
    LaunchedEffect(pagerState, pages.first, pages.last) {
        snapshotFlow { pagerState.currentPage }.collect { page ->
            viewModel.onPageChanged(pages.first + page)
        }
    }

    val scrollState = rememberScrollState()
    // 明细卡顶边在滚动内容里的位置：点了某一天要滚过去，用户不用自己找
    val detailTop = remember { mutableIntStateOf(0) }

    /*
     * 整页可滚动，而不是「月历按内容、明细抢剩余空间」：
     * 后者在手机上算下来明细只剩一两行的高度，当天的账根本看不全。
     * 现在两张卡都按内容自然展开 —— 明细有多少笔就多高，往上滑就能看完一整天。
     */
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        CalendarCard {
            MonthHeader(
                month = visibleMonth,
                canGoPrev = pagerState.currentPage > 0,
                canGoNext = pagerState.currentPage < lastPage,
                onPrev = { scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) } },
                onNext = { scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) } },
                onToday = {
                    viewModel.selectToday()
                    scope.launch { pagerState.animateScrollToPage(lastPage) }
                }
            )
            Spacer(Modifier.height(8.dp))
            MonthSummary(
                data = visibleData,
                loading = visibleMonth in state.loading,
                hideAmounts = hideAmounts
            )
            Spacer(Modifier.height(8.dp))

            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxWidth(),
                // 相邻页提前组合好，滑动时高度和内容都已经就位
                beyondViewportPageCount = 1
            ) { page ->
                val month = CalendarMonths.monthOf(pages.first + page)
                val data = state.months[month]
                MonthGrid(
                    month = month,
                    byDate = data?.days ?: emptyMap(),
                    maxExpense = data?.maxExpense ?: 0L,
                    today = today,
                    selected = state.selectedDate?.takeIf { CalendarMonths.isInMonth(it, month) },
                    hideAmounts = hideAmounts,
                    // 固定 6 行：月份之间高度一致，滑动时不会上下跳
                    minRows = GRID_ROWS,
                    onPickDay = viewModel::selectDate
                )
            }

            Spacer(Modifier.height(10.dp))
            HeatLegend()
        }

        CalendarCard(
            modifier = Modifier.onGloballyPositioned { coords ->
                detailTop.intValue = coords.positionInParent().y.roundToInt()
            }
        ) {
            DayPanel(
                date = state.selectedDate,
                bills = state.selectedBills,
                loading = state.selectedLoading,
                today = today,
                categoryById = state.categoryById,
                hideAmounts = hideAmounts,
                onEdit = onEdit,
                onAddBill = onAddBill,
                onOpenBills = onOpenBills
            )
        }
    }

    // 点了某一天：把明细卡滚到顶部，当天的账一屏看全（明细长度会变，每次选都重算一次边界）
    LaunchedEffect(state.selectedDate) {
        if (state.selectedDate != null && detailTop.intValue > 0) {
            scrollState.animateScrollTo(detailTop.intValue.coerceIn(0, scrollState.maxValue))
        }
    }
}

/** 日历页的卡片：和统计页同一套卡片语言（20dp 圆角 + surface 底） */
@Composable
private fun CalendarCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(14.dp),
        content = content
    )
}

/** 月份标题栏：左右切月 + 一键回到今天 */
@Composable
private fun MonthHeader(
    month: YearMonth,
    canGoPrev: Boolean,
    canGoNext: Boolean,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onToday: () -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        ArrowButton(Icons.Filled.ChevronLeft, "上个月", canGoPrev, onPrev)
        Text(
            text = MONTH_TEXT.format(month),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f)
        )
        ArrowButton(Icons.Filled.ChevronRight, "下个月", canGoNext, onNext)
        Spacer(Modifier.width(6.dp))
        SmallAction("今天", onToday)
    }
}

@Composable
private fun ArrowButton(
    icon: ImageVector,
    description: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    IconButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(32.dp)) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = if (enabled) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
            }
        )
    }
}

/** 月概览：这个月花了多少、进了多少、日均多少、记了几天 */
@Composable
private fun MonthSummary(
    data: CalendarViewModel.MonthData?,
    loading: Boolean,
    hideAmounts: Boolean
) {
    val summary = data?.summary
    if (summary == null) {
        Text(
            text = if (loading) "正在算这个月的账…" else "这个月还没有账",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        return
    }

    Column {
        // 支出 / 收入一行，其余口径一行：窄屏上全挤一行会各自换行，看着更乱
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "支出 ${money(summary.expense, hideAmounts)}",
                style = MaterialTheme.typography.titleSmall,
                color = AppColors.expense,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = "收入 ${money(summary.income, hideAmounts)}",
                style = MaterialTheme.typography.titleSmall,
                color = AppColors.income,
                fontWeight = FontWeight.SemiBold
            )
        }
        Spacer(Modifier.height(2.dp))
        Text(
            text = buildString {
                append("结余 ${money(summary.balance, hideAmounts)}")
                append(" · 日均 ${money(summary.avgExpensePerDay, hideAmounts)}")
                append(" · 记账 ${summary.activeDays} 天")
            },
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** 色阶图例：把「深浅」和「格子里的数字」一次说清楚，省得用户猜 */
@Composable
private fun HeatLegend() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(9.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
        )
        Spacer(Modifier.width(3.dp))
        LEGEND_ALPHAS.forEach { alpha ->
            Box(
                modifier = Modifier
                    .size(9.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(AppColors.expense.copy(alpha = alpha))
            )
            Spacer(Modifier.width(3.dp))
        }
        Spacer(Modifier.width(3.dp))
        Text(
            // 一行放得下才不会被省略号截断（截一半比不写更难看）
            text = "灰=没记账 · 深=支出多 · 绿=收入",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * 选中那天的明细。
 * <p>
 * 没选时只提示一句「点某天」；选了就把当天每一笔列出来，并给两个出口：
 * 这天要补记一笔历史账、或去账单页核对/批量处理。
 */
@Composable
private fun DayPanel(
    date: LocalDate?,
    bills: List<LocalTransaction>,
    loading: Boolean,
    today: LocalDate,
    categoryById: Map<Long, LocalCategory>,
    hideAmounts: Boolean,
    onEdit: (LocalTransaction) -> Unit,
    onAddBill: (LocalDate) -> Unit,
    onOpenBills: (BillPreset) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        if (date == null) {
            Column {
                Text(
                    text = "点某一天 → 这里摊开当天的每一笔",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "左右滑动（或点箭头）切换月份，点「今天」回到本月",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            return@Column
        }

        val figures = figuresOf(bills)

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = DAY_TEXT.format(date) + "（" + WEEKDAY_SHORT[date.dayOfWeek.value % 7] + "）",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            if (date == today) {
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "今天",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Spacer(Modifier.weight(1f))
            Text(
                text = "支出 ${money(figures.expense, hideAmounts)}",
                style = MaterialTheme.typography.titleSmall,
                color = AppColors.expense,
                fontWeight = FontWeight.SemiBold
            )
        }
        Spacer(Modifier.height(2.dp))
        Text(
            text = buildString {
                append("收入 ${money(figures.income, hideAmounts)}")
                append(" · 结余 ${money(figures.balance, hideAmounts)}")
                append(" · ${figures.txCount} 笔")
                if (figures.transferCount > 0) {
                    append(" · 转账 ${figures.transferCount} 笔")
                }
            },
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(8.dp))

        when {
            loading && bills.isEmpty() -> Hint("正在加载…")
            bills.isEmpty() -> Hint("这天没有记账，可以在下面补记一笔")
            // 当天最多几十笔，直接铺开：外层已经是滚动容器，
            // 再套一层滚动列表会出现「两截滚动条」互相抢手势，也更难「一屏看全」
            else -> Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                bills.forEach { item ->
                    DayBillRow(
                        item = item,
                        category = item.categoryId?.let { categoryById[it] },
                        hideAmounts = hideAmounts,
                        onEdit = onEdit
                    )
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SmallAction(
                text = "这天记一笔",
                onClick = { onAddBill(date) },
                modifier = Modifier.weight(1f)
            )
            SmallAction(
                text = "在账单页看这一天",
                onClick = {
                    // 口径与统计页一致：日期 → 时间窗一律走 ReportPeriod，不在界面里另写时区换算
                    val day = ReportPeriod(ReportGranularity.DAY, date)
                    // 账单页的区间是左闭右闭（含端点），所以末毫秒要减 1
                    onOpenBills(BillPreset(startMillis = day.startMillis, endMillis = day.endMillis - 1))
                },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

/** 当天的一笔：时间 + 分类 + 备注 + 金额；点一下就进编辑 */
@Composable
private fun DayBillRow(
    item: LocalTransaction,
    category: LocalCategory?,
    hideAmounts: Boolean,
    onEdit: (LocalTransaction) -> Unit
) {
    val categoryName = category?.name?.takeIf { it.isNotBlank() }
    // 标题口径与账单页一致：备注 → 标签 → 分类名；副标题里别再重复一遍同样的字
    val title = billTitle(item, categoryName)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { onEdit(item) }
            // 行压扁一点：日历页下面还要留地方列当天的账，能多露一行是一行
            .padding(horizontal = 8.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CategoryBadge(type = item.type, category = category, size = 30.dp)
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(1.dp))
            Text(
                text = buildString {
                    append(TIME_TEXT.format(Instant.ofEpochMilli(item.tradeTime).atZone(ZoneId.systemDefault())))
                    if (title != categoryName) {
                        categoryName?.let { append(" · $it") }
                    }
                    // 标签加 # 前缀：和分类名、账户名区分开，一眼能看出这是标签
                    // （标签已经当标题时不再重复）
                    if (!tagsUsedAsTitle(item)) {
                        inlineTags(item.tags)?.let { append(" · $it") }
                    }
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Text(
            text = if (hideAmounts) "****" else formatSignedAmount(item.type, item.amount),
            style = MaterialTheme.typography.titleSmall,
            color = typeColor(item.type),
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun Hint(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(vertical = 10.dp)
    )
}

/** 日历固定画 6 行：不同月份高度一致，滑动切月时不会上下跳 */
private const val GRID_ROWS = 6

/** 图例里的四档色阶，与 HeatCell 的档位保持一致 */
private val LEGEND_ALPHAS = listOf(0.28f, 0.5f, 0.72f, 1f)

private val MONTH_TEXT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy 年 M 月")
private val DAY_TEXT: DateTimeFormatter = DateTimeFormatter.ofPattern("M 月 d 日")
private val TIME_TEXT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
