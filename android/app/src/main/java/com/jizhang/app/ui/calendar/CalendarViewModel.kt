package com.jizhang.app.ui.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jizhang.app.data.local.entity.LocalCategory
import com.jizhang.app.data.local.entity.LocalTransaction
import com.jizhang.app.data.repository.DayAmount
import com.jizhang.app.data.repository.LedgerRepository
import com.jizhang.app.data.repository.ReportGranularity
import com.jizhang.app.data.repository.ReportOverview
import com.jizhang.app.data.repository.ReportPeriod
import com.jizhang.app.data.repository.ReportRepository
import com.jizhang.app.data.repository.TransactionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import javax.inject.Inject

/**
 * 日历页的状态。
 * <p>
 * 取数策略是「按月整取 + 内存分组」：翻到某个月就把整月一次拿回来（按天聚合给格子，
 * 明细按天分好组），之后点某一天、看当天每一笔都不再碰数据库 —— 翻月是唯一会查库的动作。
 * <p>
 * 相邻月会预取（滑动时不会先看到一片灰），缓存按「离当前月远近」淘汰。
 */
@HiltViewModel
class CalendarViewModel @Inject constructor(
    private val reportRepository: ReportRepository,
    private val transactionRepository: TransactionRepository,
    private val ledgerRepository: LedgerRepository
) : ViewModel() {

    /** 一个月的数据：格子要的按天聚合 + 当月明细（按天分好组） */
    data class MonthData(
        val month: YearMonth,
        val days: Map<LocalDate, DayAmount>,
        val summary: ReportOverview?,
        val bills: Map<LocalDate, List<LocalTransaction>>
    ) {
        /** 色阶归一用的当月最大单日支出 */
        val maxExpense: Long get() = days.values.maxOfOrNull { it.expense } ?: 0L
    }

    data class UiState(
        val bookId: Long = 0L,
        /** 可分页范围（绝对页码，闭区间）；[ready] 为 false 时还没算出来 */
        val pages: IntRange = 0..0,
        /** 最早一笔账单查完之前不建分页：页码范围中途变化会让月份列表跳一下 */
        val ready: Boolean = false,
        val visibleMonth: YearMonth = YearMonth.now(),
        val months: Map<YearMonth, MonthData> = emptyMap(),
        /** 正在取的月份（界面据此区分「加载中」和「这天没记账」） */
        val loading: Set<YearMonth> = emptySet(),
        /** 选中的那天；null = 还没选，提示用户点一格 */
        val selectedDate: LocalDate? = null,
        val categoryById: Map<Long, LocalCategory> = emptyMap()
    ) {
        val visibleData: MonthData? get() = months[visibleMonth]

        val selectedData: MonthData? get() = selectedDate?.let { months[YearMonth.from(it)] }

        val selectedBills: List<LocalTransaction> get() = selectedDate?.let { selectedData?.bills?.get(it) }.orEmpty()

        val selectedLoading: Boolean get() = selectedDate?.let { YearMonth.from(it) in loading } ?: false
    }

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private var lastBookId = -1L
    private var watchJob: Job? = null
    private var earliestJob: Job? = null

    /** 正在取数的月份：同一个月的重复请求直接吞掉，滑动时不会越翻越卡 */
    private val inFlight = mutableSetOf<YearMonth>()

    /** 账本切换（换账本 / 登录 / 退出）时重新绑定数据源 */
    fun bind(bookId: Long) {
        if (bookId <= 0L || bookId == lastBookId) {
            return
        }
        lastBookId = bookId
        inFlight.clear()
        _state.value = UiState(bookId = bookId, selectedDate = LocalDate.now())

        watchJob?.cancel()
        watchJob = viewModelScope.launch {
            launch {
                ledgerRepository.observeCategories(bookId).collect { list ->
                    _state.update { it.copy(categoryById = list.associateBy { category -> category.id }) }
                }
            }
            launch {
                /*
                 * 账单是会随时变的（记账 / 编辑 / 删除 / 同步拉取）。
                 * 用全量账单的 Flow 当「失效信号」：一变就把已缓存的月份重取一遍，
                 * 否则在别处记一笔回来，日历还停在旧数字上。
                 */
                transactionRepository.observeTransactions(bookId).collect {
                    refreshCached()
                }
            }
        }

        // 下界用「最早一笔账单」而不是固定值：老用户能一直往回翻，新用户也不会一路滑到 1970 年
        earliestJob?.cancel()
        earliestJob = viewModelScope.launch {
            val earliest = transactionRepository.minTradeTime(bookId)?.let { CalendarMonths.monthOfMillis(it) }
            _state.update {
                it.copy(pages = CalendarMonths.range(YearMonth.now(), earliest), ready = true)
            }
            ensureMonth(_state.value.visibleMonth)
        }
    }

    /** 分页落到哪一页：更新当前月、把选中项收敛到这个月，并取回当月与相邻月 */
    fun onPageChanged(page: Int) {
        val snapshot = _state.value
        if (!snapshot.ready) {
            return
        }
        val month = CalendarMonths.monthOf(page)
        _state.update { current ->
            current.copy(
                visibleMonth = month,
                // 选中的那天已经被翻走了就清掉，免得下面还挂着上个月的明细
                selectedDate = current.selectedDate?.takeIf { CalendarMonths.isInMonth(it, month) }
            )
        }

        ensureMonth(month)
        listOf(page - 1, page + 1)
            .filter { it in snapshot.pages }
            .forEach { ensureMonth(CalendarMonths.monthOf(it)) }
    }

    /** 点某一天的格子：只改选中项，明细已经在缓存里，不再查库 */
    fun selectDate(date: LocalDate) {
        _state.update { it.copy(selectedDate = date) }
    }

    /** 「今天」按钮：连月份带选中项一起跳回今天 */
    fun selectToday() {
        _state.update { it.copy(selectedDate = LocalDate.now(), visibleMonth = YearMonth.now()) }
    }

    /** 取某个月（已缓存且不强制刷新时直接返回） */
    fun ensureMonth(month: YearMonth, force: Boolean = false) {
        val snapshot = _state.value
        val bookId = snapshot.bookId
        if (bookId <= 0L || !snapshot.ready) {
            return
        }
        if (!force && snapshot.months.containsKey(month)) {
            return
        }
        // 同一个月的请求合并成一次：翻回去再翻回来不会重复查库
        if (!inFlight.add(month)) {
            return
        }
        _state.update { it.copy(loading = it.loading + month) }

        viewModelScope.launch {
            val data = runCatching { loadMonth(bookId, month) }.getOrNull()
            inFlight.remove(month)
            _state.update { current ->
                if (current.bookId != bookId) {
                    // 账本已经换了，这次结果作废
                    current
                } else {
                    current.copy(
                        months = if (data == null) {
                            current.months
                        } else {
                            trim(current.months + (month to data), current.visibleMonth)
                        },
                        loading = current.loading - month
                    )
                }
            }
        }
    }

    /** 已缓存的月份全部重取（账单数据变了时用），当前月不在缓存里也补上 */
    private fun refreshCached() {
        val snapshot = _state.value
        if (snapshot.bookId <= 0L) {
            return
        }
        (snapshot.months.keys + snapshot.visibleMonth).forEach { ensureMonth(it, force = true) }
    }

    private suspend fun loadMonth(bookId: Long, month: YearMonth): MonthData {
        /*
         * 锚点必须落在这个月里；而当月**要用「今天」当锚点**：
         * `ReportPeriod.daysCounted` 是按锚点的日号算「已过天数」的，拿 1 号当锚点会把它当成
         * 「刚过 1 天」，日均就变成「支出总额」（真机上表现为「日均 = 支出」）。
         */
        val anchor = if (month == YearMonth.now()) LocalDate.now() else month.atDay(1)
        val period = ReportPeriod(ReportGranularity.MONTH, anchor)
        val days = reportRepository.calendar(bookId, period)
        val bills = transactionRepository.listBetween(bookId, period.startMillis, period.endMillis)
        return MonthData(
            month = month,
            days = days.associateBy { it.date },
            // 月概览复用统计页那套口径（日均按「已过天数」摊），不另写一份 SQL
            summary = reportRepository.overview(bookId, period),
            bills = bills.groupBy { instantToDate(it.tradeTime) }
        )
    }

    private fun instantToDate(millis: Long): LocalDate =
        Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate()

    /** 只保留离当前月最近的若干个月，长时间翻来翻去也不会一直涨内存 */
    private fun trim(months: Map<YearMonth, MonthData>, visible: YearMonth): Map<YearMonth, MonthData> {
        if (months.size <= CACHE_MONTHS) {
            return months
        }
        return months.entries
            .sortedBy { CalendarMonths.distance(visible, it.key) }
            .take(CACHE_MONTHS)
            .associate { it.key to it.value }
    }

    private companion object {
        /** 缓存几个月：当前月 + 前后各一页够用，多留几页应付来回滑 */
        const val CACHE_MONTHS = 8
    }
}
