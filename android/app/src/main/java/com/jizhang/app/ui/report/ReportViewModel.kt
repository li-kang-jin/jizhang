package com.jizhang.app.ui.report

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jizhang.app.data.prefs.SettingsStore
import com.jizhang.app.data.remote.dto.BudgetOverviewDto
import com.jizhang.app.data.remote.dto.CategoryStatDto
import com.jizhang.app.data.remote.dto.MemberReportDto
import com.jizhang.app.data.remote.dto.TrendItemDto
import com.jizhang.app.data.repository.AssetSummary
import com.jizhang.app.data.repository.BudgetCycle
import com.jizhang.app.data.repository.BudgetPeriods
import com.jizhang.app.data.repository.BudgetRepository
import com.jizhang.app.data.repository.CategorySlice
import com.jizhang.app.data.repository.DayAmount
import com.jizhang.app.data.repository.DayDetail
import com.jizhang.app.data.repository.HabitStats
import com.jizhang.app.data.repository.RankItem
import com.jizhang.app.data.repository.ReportGranularity
import com.jizhang.app.data.repository.ReportOverview
import com.jizhang.app.data.repository.ReportPeriod
import com.jizhang.app.data.repository.ReportRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import javax.inject.Inject

/**
 * 统计页状态。
 * <p>
 * 时间维度统一由 [ReportPeriod]（粒度 + 锚点）表达，不再是单一月份；
 * 所有数据都是本地聚合（离线可用），因此切换粒度 / 翻页只触发重新查库，不涉及网络。
 */
@HiltViewModel
class ReportViewModel @Inject constructor(
    private val reportRepository: ReportRepository,
    private val budgetRepository: BudgetRepository,
    private val settings: SettingsStore
) : ViewModel() {

    data class UiState(
        val bookId: Long = 0L,
        /** 当前账本是否为家庭账本（决定是否展示「谁花了多少」） */
        val shared: Boolean = false,
        /** 时间窗：粒度 + 锚点 */
        val period: ReportPeriod = ReportPeriod.current(),
        val loading: Boolean = false,
        /** 下拉刷新中（与首次加载区分开，避免整页闪骨架） */
        val refreshing: Boolean = false,
        /** 是否已经加载过一次，用于决定是否显示骨架屏 */
        val loaded: Boolean = false,
        val overview: ReportOverview? = null,
        val trend: List<TrendItemDto> = emptyList(),
        /** 分类构成：1=支出 2=收入 */
        val categoryType: Int = 1,
        val categories: List<CategorySlice> = emptyList(),
        val categoryTotal: Long = 0L,
        /** 父分类是否归并（关掉就是平铺所有分类） */
        val groupByParent: Boolean = true,
        /** 排行维度：1=支出 2=收入 */
        val rankType: Int = 1,
        val accountRanks: List<RankItem> = emptyList(),
        val tagRanks: List<RankItem> = emptyList(),
        val habit: HabitStats? = null,
        /** 收支日历（只有有记账的天） */
        val calendar: List<DayAmount> = emptyList(),
        /** 点日历某天展开的当天明细；null = 没展开 */
        val dayDetail: DayDetail? = null,
        /** 按成员统计，仅家庭账本有意义 */
        val members: MemberReportDto? = null,
        /** 预算进度（始终按锚点所在月份），未设置时 hasBudget = false */
        val budget: BudgetOverviewDto? = null,
        val assets: AssetSummary = AssetSummary.EMPTY,
        /** 支出分类，供预算设置选择范围 */
        val expenseCategories: List<CategoryStatDto> = emptyList(),
        val message: String? = null
    ) {
        /** 时间轴条能不能往后翻（不允许看未来） */
        val canGoNext: Boolean get() = period.canGoNext

        /** 预算卡片标题：非月粒度时明确写出「哪个月的预算」，避免误以为是当期数据 */
        val budgetTitle: String
            get() = if (period.granularity == ReportGranularity.MONTH) {
                "本月预算"
            } else {
                "${period.anchor.monthValue} 月预算"
            }
    }

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private var bound = -1L
    private var loadJob: Job? = null
    private var dayJob: Job? = null
    private var granularityRestored = false

    fun bind(bookId: Long, shared: Boolean) {
        if (bookId <= 0) {
            return
        }
        if (bound == bookId && _state.value.shared == shared) {
            return
        }
        bound = bookId
        _state.update {
            it.copy(bookId = bookId, shared = shared, period = ReportPeriod.current(it.period.granularity))
        }
        viewModelScope.launch {
            // 粒度记忆：上次看的是「年」，进来还是「年」
            if (!granularityRestored) {
                granularityRestored = true
                val saved = settings.reportGranularity()
                if (saved != _state.value.period.granularity) {
                    _state.update { it.copy(period = it.period.withGranularity(saved)) }
                }
            }
            reload()
        }
    }

    /** 全部数据来自本地聚合，断网也能看 */
    fun load(refresh: Boolean = false) {
        if (refresh) {
            reload(refresh = true)
        } else {
            reload()
        }
    }

    private fun reload(refresh: Boolean = false) {
        val snapshot = _state.value
        if (snapshot.bookId <= 0) {
            return
        }
        // 连续翻页时把上一轮查询掐掉，否则慢的那次会盖掉新结果
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _state.update {
                it.copy(
                    loading = !it.loaded && !refresh,
                    refreshing = refresh,
                    // 周期变了，上一段期间里点开的那一天就不该继续挂着
                    dayDetail = null
                )
            }
            val bookId = snapshot.bookId
            val period = snapshot.period
            val categoryType = snapshot.categoryType
            val rankType = snapshot.rankType
            val grouped = snapshot.groupByParent
            val budgetPeriod = BudgetPeriods.of(BudgetCycle.MONTH, period.anchor)

            try {
                coroutineScope {
                    // 十来次本地聚合，并发跑完通常在一次帧渲染内，翻页不会有明显等待
                    val overview = async { reportRepository.overview(bookId, period) }
                    val trend = async { reportRepository.trend(bookId, period) }
                    val category = async {
                        reportRepository.category(bookId, categoryType, period, grouped)
                    }
                    val calendar = async { reportRepository.calendar(bookId, period) }
                    val habit = async { reportRepository.habit(bookId, period) }
                    val accountRanks = async { reportRepository.accountRank(bookId, period, rankType) }
                    val tagRanks = async { reportRepository.tagRank(bookId, period, rankType) }
                    val members = async {
                        if (snapshot.shared) reportRepository.memberStats(bookId, period) else null
                    }
                    val budget = async { budgetRepository.overview(bookId, budgetPeriod) }
                    val assets = async { reportRepository.assets(bookId) }
                    val expenseCategories = async { reportRepository.expenseCategories(bookId) }

                    val breakdown = category.await()
                    _state.update {
                        it.copy(
                            overview = overview.await(),
                            trend = trend.await(),
                            categories = breakdown.items,
                            categoryTotal = breakdown.total,
                            calendar = calendar.await(),
                            habit = habit.await(),
                            accountRanks = accountRanks.await(),
                            tagRanks = tagRanks.await(),
                            members = members.await(),
                            budget = budget.await(),
                            assets = assets.await(),
                            expenseCategories = expenseCategories.await()
                        )
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(message = "统计数据加载失败：${e.message ?: "未知错误"}") }
            } finally {
                _state.update { it.copy(loading = false, refreshing = false, loaded = true) }
            }
        }
    }

    /* ---------------- 时间维度 ---------------- */

    /** 切换粒度：保留锚点，所以「10 月」切到「年」就是 2026 年 */
    fun setGranularity(granularity: ReportGranularity) {
        if (_state.value.period.granularity == granularity) {
            return
        }
        _state.update { it.copy(period = it.period.withGranularity(granularity)) }
        viewModelScope.launch { settings.setReportGranularity(granularity) }
        reload()
    }

    /** 前后翻页，offset 为 ±1（粒度不同，步长自动变成天 / 月 / 年） */
    fun switchPeriod(offset: Long) {
        val next = _state.value.period.plus(offset)
        if (next.future) {
            return
        }
        _state.update { it.copy(period = next) }
        reload()
    }

    /** 直接跳到某一天（选择器、日历热力图点击都会走这里） */
    fun selectDate(date: LocalDate, granularity: ReportGranularity? = null) {
        val target = granularity ?: _state.value.period.granularity
        val next = ReportPeriod(target, date).let { if (it.future) it.previous() else it }
        _state.update { it.copy(period = next) }
        reload()
    }

    /** 回到今天 / 本月 / 今年 */
    fun backToNow() {
        val now = ReportPeriod.current(_state.value.period.granularity)
        if (now.startDate == _state.value.period.startDate) {
            return
        }
        _state.update { it.copy(period = now) }
        reload()
    }

    /**
     * 点日历里的某一天：在日历卡片内展开当天明细（**不**改变整页粒度）。
     * <p>
     * 再点同一天收起；想真正切到那天的统计，用面板里的「切到该日统计」。
     * 这样在年视图里点一格不会被直接甩到「某一天」，看得见上下文。
     */
    fun selectDay(date: LocalDate) {
        val bookId = _state.value.bookId
        if (bookId <= 0) {
            return
        }
        if (_state.value.dayDetail?.date == date) {
            _state.update { it.copy(dayDetail = null) }
            return
        }
        dayJob?.cancel()
        dayJob = viewModelScope.launch {
            val detail = reportRepository.dayDetail(bookId, date)
            _state.update { it.copy(dayDetail = detail) }
        }
    }

    /** 收起当天明细面板 */
    fun clearDayDetail() {
        dayJob?.cancel()
        _state.update { it.copy(dayDetail = null) }
    }

    /* ---------------- 分类 / 排行 ---------------- */

    fun switchCategoryType(type: Int) {
        if (_state.value.categoryType == type) {
            return
        }
        _state.update { it.copy(categoryType = type) }
        refreshCategory()
    }

    /** 父分类归并开关（子分类金额算到父分类上） */
    fun setGroupByParent(grouped: Boolean) {
        if (_state.value.groupByParent == grouped) {
            return
        }
        _state.update { it.copy(groupByParent = grouped) }
        refreshCategory()
    }

    /** 排行切「支出 / 收入」 */
    fun switchRankType(type: Int) {
        if (_state.value.rankType == type) {
            return
        }
        _state.update { it.copy(rankType = type) }
        refreshRanks()
    }

    private fun refreshCategory() {
        val snapshot = _state.value
        viewModelScope.launch {
            val breakdown = reportRepository.category(
                snapshot.bookId,
                snapshot.categoryType,
                snapshot.period,
                snapshot.groupByParent
            )
            _state.update { it.copy(categories = breakdown.items, categoryTotal = breakdown.total) }
        }
    }

    private fun refreshRanks() {
        val snapshot = _state.value
        viewModelScope.launch {
            val accounts = reportRepository.accountRank(snapshot.bookId, snapshot.period, snapshot.rankType)
            val tags = reportRepository.tagRank(snapshot.bookId, snapshot.period, snapshot.rankType)
            _state.update { it.copy(accountRanks = accounts, tagRanks = tags) }
        }
    }

    /* ---------------- 预算 ---------------- */

    /** 设置总预算（categoryId = 0）或分类预算；始终记在锚点所在月份上 */
    fun saveBudget(categoryId: Long, amountYuan: String, onDone: (String?) -> Unit) {
        val amount = amountYuan.trim().toBigDecimalOrNull()
        when {
            amount == null -> {
                onDone("请输入正确的金额")
                return
            }

            amount <= BigDecimal.ZERO -> {
                onDone("金额必须大于 0")
                return
            }
        }

        val bookId = _state.value.bookId
        val period = BudgetPeriods.of(BudgetCycle.MONTH, _state.value.period.anchor)
        // 元 -> 分，四舍五入规避浮点误差
        val cents = amount.movePointRight(2).setScale(0, RoundingMode.HALF_UP).toLong()

        viewModelScope.launch {
            budgetRepository.save(bookId, categoryId, period, cents)
                .onSuccess {
                    onDone(null)
                    reload()
                }
                .onFailure { onDone(it.message ?: "保存失败") }
        }
    }

    /** 删除预算 */
    fun deleteBudget(budgetId: Long) {
        val bookId = _state.value.bookId
        viewModelScope.launch {
            budgetRepository.delete(budgetId, bookId)
                .onSuccess {
                    _state.update { it.copy(message = "预算已删除") }
                    reload()
                }
                .onFailure { error ->
                    _state.update { it.copy(message = error.message ?: "删除失败") }
                }
        }
    }

    fun consumeMessage() {
        _state.update { it.copy(message = null) }
    }
}
