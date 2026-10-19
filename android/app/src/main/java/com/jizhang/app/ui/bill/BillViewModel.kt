package com.jizhang.app.ui.bill

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jizhang.app.data.local.entity.LocalAccount
import com.jizhang.app.data.local.entity.LocalCategory
import com.jizhang.app.data.local.entity.LocalTransaction
import com.jizhang.app.data.prefs.SettingsStore
import com.jizhang.app.data.repository.BookRepository
import com.jizhang.app.data.repository.LedgerRepository
import com.jizhang.app.data.repository.PetRepository
import com.jizhang.app.data.repository.ReportRepository
import com.jizhang.app.data.repository.SyncManager
import com.jizhang.app.data.repository.TransactionRepository
import com.jizhang.app.ui.pet.PetMood
import com.jizhang.app.ui.pet.PetState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject

@HiltViewModel
class BillViewModel @Inject constructor(
    private val transactionRepository: TransactionRepository,
    private val reportRepository: ReportRepository,
    private val bookRepository: BookRepository,
    private val petRepository: PetRepository,
    private val ledgerRepository: LedgerRepository,
    private val syncManager: SyncManager,
    private val settingsStore: SettingsStore
) : ViewModel() {

    data class UiState(
        val bookId: Long = 0L,
        /** 账本全量账单（按时间倒序，来自 Room） */
        val transactions: List<LocalTransaction> = emptyList(),
        /** 应用筛选 + 排序后的结果 */
        val filteredTransactions: List<LocalTransaction> = emptyList(),
        /** 按日分组（新日期在前），列表直接消费它 */
        val dayGroups: List<BillDayGroup> = emptyList(),
        val summary: BillSummary = BillSummary(),
        val chart: BillChart = BillChart(),
        /** 当前筛选条件 */
        val filter: BillFilter = BillFilter(),
        /** 筛选下拉的数据源 */
        val accounts: List<LocalAccount> = emptyList(),
        val categories: List<LocalCategory> = emptyList(),
        /** 分类 id → 分类（列表要给每条账单显示分类图标） */
        val categoryById: Map<Long, LocalCategory> = emptyMap(),
        val pendingCount: Int = 0,
        val monthIncome: Long = 0L,
        val monthExpense: Long = 0L,
        val pet: PetState = PetState(),
        val petMood: PetMood = PetMood.NORMAL,
        /** 记账人 userId → 展示名（家庭账本里显示「谁记的」） */
        val memberLabels: Map<Long, String> = emptyMap(),
        /** 账户 id → 名称（列表里显示「从哪个账户出的」） */
        val accountLabels: Map<Long, String> = emptyMap(),
        val syncing: Boolean = false,
        val message: String? = null
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private var observeJob: Job? = null
    private var lastBookId: Long = -1L

    /** 账本切换（登录 / 退出登录）时重新绑定数据源 */
    fun bind(bookId: Long) {
        if (bookId == lastBookId) {
            return
        }
        lastBookId = bookId
        _state.update { it.copy(bookId = bookId) }

        observeJob?.cancel()
        observeJob = viewModelScope.launch {
            launch {
                var first = true
                transactionRepository.observeTransactions(bookId).collect { list ->
                    _state.update { it.copy(transactions = list).withDerived() }
                    // 列表自己会刷新，但顶部卡片是本月的聚合数字（查一次），
                    // 清理数据 / 导入 / 同步之后必须跟着重算，否则要重启应用才更新。
                    // 首帧跳过：bind() 里刚查过一次。
                    if (first) {
                        first = false
                    } else {
                        refresh()
                    }
                }
            }
            launch {
                transactionRepository.observePendingCount(bookId).collect { count ->
                    _state.update { it.copy(pendingCount = count) }
                }
            }
            launch {
                petRepository.state.collect { pet ->
                    _state.update { it.copy(pet = pet) }
                }
            }
            launch {
                bookRepository.observeMembers(bookId).collect { members ->
                    _state.update { it.copy(memberLabels = members.associate { it.userId to it.label }) }
                }
            }
            launch {
                // 筛选下拉的数据源：只含未归档账户。
                // 用 Flow 而不是一次性读取：在「账户管理 / 分类管理」里新建之后要立刻出现在筛选里
                ledgerRepository.observeAccounts(bookId).collect { accounts ->
                    _state.update { it.copy(accounts = accounts).withDerived() }
                }
            }
            launch {
                // 列表里「这一笔是从哪个账户出的」：取全量（含归档）。
                // 归档过的账户不再出现在筛选里，但老账单还挂在它上面，得能显示名字
                ledgerRepository.observeAllAccounts(bookId).collect { accounts ->
                    _state.update {
                        it.copy(accountLabels = accounts.associate { account -> account.id to account.name })
                    }
                }
            }
            launch {
                ledgerRepository.observeCategories(bookId).collect { categories ->
                    _state.update { it.copy(categories = categories).withDerived() }
                }
            }
        }

        refresh()
        sync()
    }

    /** 重新计算本月收支与宠物心情 */
    fun refresh() {
        val bookId = _state.value.bookId
        viewModelScope.launch {
            // 失败要说话：这个协程里抛出去没人接，界面就会停在旧数字上，
            // 看起来正是「卡片不刷新」那个毛病，排查时完全没线索。
            val overview = runCatching { reportRepository.overview(bookId) }
                .getOrElse { error ->
                    _state.update { it.copy(message = "本月汇总刷新失败：${error.message ?: "未知错误"}") }
                    return@launch
                }
            val mood = when {
                overview.balance < 0 -> PetMood.WORRIED
                overview.income > 0 -> PetMood.HAPPY
                else -> PetMood.NORMAL
            }
            _state.update {
                it.copy(
                    monthIncome = overview.income,
                    monthExpense = overview.expense,
                    petMood = mood
                )
            }
        }
    }

    /* ---------------- 筛选 ---------------- */

    private fun applyFilter(transform: (BillFilter) -> BillFilter) {
        _state.update { it.copy(filter = transform(it.filter)).withDerived() }
    }

    fun setDateRange(range: BillDateRange) = applyFilter {
        it.copy(range = range, customStart = null, customEnd = null)
    }

    /**
     * 自定义日期区间。
     *
     * @param start 起日当天 0 点
     * @param end 止日当天 0 点（止日整天都要算进去，换算成开区间上界见 [BillFilter.withDayRange]）
     */
    fun setCustomRange(start: Long, end: Long) = applyFilter { it.withDayRange(start, end) }

    fun setTypeFilter(type: Int) = applyFilter { it.copy(type = type) }

    fun setAccountFilter(accountId: Long?) = applyFilter { it.copy(accountId = accountId) }

    fun toggleCategory(categoryId: Long) = applyFilter {
        val next = if (categoryId in it.categoryIds) it.categoryIds - categoryId else it.categoryIds + categoryId
        it.copy(categoryIds = next)
    }

    fun clearCategories() = applyFilter { it.copy(categoryIds = emptySet()) }

    fun setKeyword(keyword: String) = applyFilter { it.copy(keyword = keyword) }

    fun setSort(sort: BillSort) = applyFilter { it.copy(sort = sort) }

    fun clearFilter() = applyFilter { BillFilter() }

    /**
     * 应用外部传入的筛选预设（统计页点分类 / 账户 / 标签跳过来）。
     * <p>
     * 与 [applyFilter] 的区别：这里是**整份替换**而不是叠加 —— 用户从统计页带着明确意图过来，
     * 上一次遗留的筛选条件只会让结果对不上，所以先清空再套用。
     */
    fun applyPreset(preset: BillPreset) {
        _state.update { current ->
            current.copy(
                filter = BillFilter(
                    type = preset.type ?: 0,
                    accountId = preset.accountId,
                    categoryIds = preset.categoryId?.let { setOf(it) } ?: emptySet(),
                    keyword = preset.keyword.orEmpty()
                )
                    // 预设带的是闭区间（止日 23:59:59.999），筛选条件存左闭右开，
                    // 直接塞进去会把止日的后一天也框进来（选 16 日看到 16、17 两天）
                    .withInclusiveRange(preset.startMillis, preset.endMillis)
            ).withDerived()
        }
    }

    /* ---------------- 派生数据 ---------------- */

    /**
     * 由「全量账单 + 筛选条件」重算列表、分组、汇总与图表。
     * 每次 transactions / filter / accounts / categories 变化后都要调一次。
     */
    private fun UiState.withDerived(): UiState {
        val bounds = filter.timeBounds()
        val filtered = transactions
            .filter { filter.matches(it, bounds) }
            .let { list ->
                when (filter.sort) {
                    BillSort.TIME_DESC -> list.sortedWith(
                        compareByDescending<LocalTransaction> { it.tradeTime }.thenByDescending { it.id }
                    )

                    BillSort.AMOUNT_DESC -> list.sortedWith(
                        compareByDescending<LocalTransaction> { it.amount }
                            .thenByDescending { it.tradeTime }
                    )
                }
            }

        val categoryMap = categories.associateBy { it.id }

        return copy(
            filteredTransactions = filtered,
            dayGroups = groupByDay(filtered),
            summary = BillSummary(
                count = filtered.size,
                income = filtered.filter { it.type == 2 }.sumOf { it.amount },
                expense = filtered.filter { it.type == 1 }.sumOf { it.amount }
            ),
            categoryById = categoryMap,
            // 类型筛选选了收入就看收入分布，否则默认看支出
            chart = buildChart(filtered, categoryMap, if (filter.type == 2) 2 else 1)
        )
    }

    private fun groupByDay(items: List<LocalTransaction>): List<BillDayGroup> {
        val zone = ZoneId.systemDefault()
        return items
            .groupBy { Instant.ofEpochMilli(it.tradeTime).atZone(zone).toLocalDate() }
            .map { (date, list) ->
                BillDayGroup(
                    date = date,
                    income = list.filter { it.type == 2 }.sumOf { it.amount },
                    expense = list.filter { it.type == 1 }.sumOf { it.amount },
                    items = list
                )
            }
            .sortedByDescending { it.date }
    }

    /** 迷你图表：筛选范围内「支出（或收入）在近 14 天的分布 + 分类占比 Top5」 */
    private fun buildChart(
        items: List<LocalTransaction>,
        categoryMap: Map<Long, LocalCategory>,
        chartType: Int
    ): BillChart {
        val typed = items.filter { it.type == chartType }
        if (typed.isEmpty()) {
            return BillChart(type = chartType)
        }

        val zone = ZoneId.systemDefault()
        val days = typed
            .groupBy { Instant.ofEpochMilli(it.tradeTime).atZone(zone).toLocalDate() }
            .map { (date, list) -> BillChartDay(date, list.sumOf { it.amount }) }
            .sortedBy { it.date }
            .takeLast(CHART_DAYS)

        val total = typed.sumOf { it.amount }
        val topCategories = typed
            .groupBy { it.categoryId }
            .map { (categoryId, list) ->
                val amount = list.sumOf { it.amount }
                BillCategorySlice(
                    categoryId = categoryId,
                    name = categoryId?.let { categoryMap[it]?.name } ?: "未分类",
                    icon = categoryId?.let { categoryMap[it]?.icon },
                    amount = amount,
                    ratio = if (total == 0L) 0f else amount.toFloat() / total
                )
            }
            .sortedByDescending { it.amount }
            .take(CHART_CATEGORIES)

        return BillChart(type = chartType, days = days, topCategories = topCategories, total = total)
    }

    /** 记账成功：喂宠物 + 刷新汇总 */
    fun onRecorded() {
        viewModelScope.launch {
            petRepository.onTransactionRecorded()
            refresh()
        }
    }

    fun sync() {
        val bookId = _state.value.bookId
        viewModelScope.launch {
            // 离线模式：不发起请求，也不转圈，直接给一句说明
            if (settingsStore.offlineMode()) {
                _state.update { it.copy(message = "离线模式已开启，已跳过同步") }
                return@launch
            }
            _state.update { it.copy(syncing = true) }
            syncManager.sync(bookId)
                .onSuccess { refresh() }
                .onFailure {
                    // 未登录 / 离线时静默失败，不打扰用户
                    _state.update { it.copy(message = null, syncing = false) }
                    return@launch
                }
            _state.update { it.copy(syncing = false) }
        }
    }

    fun delete(id: Long) {
        viewModelScope.launch {
            transactionRepository.deleteLocal(id)
            _state.update { it.copy(message = "已删除") }
        }
    }

    fun consumeMessage() {
        _state.update { it.copy(message = null) }
    }

    private companion object {
        /** 图表最多展示最近多少天 */
        const val CHART_DAYS = 14

        /** 图表最多展示几个分类 */
        const val CHART_CATEGORIES = 5
    }
}
