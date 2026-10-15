package com.jizhang.app.ui.report

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jizhang.app.data.remote.dto.AccountAssetDto
import com.jizhang.app.data.remote.dto.BudgetOverviewDto
import com.jizhang.app.data.remote.dto.CategoryStatDto
import com.jizhang.app.data.remote.dto.MemberReportDto
import com.jizhang.app.data.remote.dto.ReportOverviewDto
import com.jizhang.app.data.remote.dto.TrendItemDto
import com.jizhang.app.data.repository.BudgetRepository
import com.jizhang.app.data.repository.ReportRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.YearMonth
import javax.inject.Inject

@HiltViewModel
class ReportViewModel @Inject constructor(
    private val reportRepository: ReportRepository,
    private val budgetRepository: BudgetRepository
) : ViewModel() {

    data class UiState(
        val bookId: Long = 0L,
        /** 当前账本是否为家庭账本（决定是否展示「谁花了多少」） */
        val shared: Boolean = false,
        /** 当前查看的月份，yyyy-MM */
        val month: String = YearMonth.now().toString(),
        val loading: Boolean = false,
        val categoryType: Int = 1,
        val overview: ReportOverviewDto? = null,
        /** 按成员统计，仅家庭账本有意义 */
        val members: MemberReportDto? = null,
        /** 预算进度，未设置预算时 hasBudget = false */
        val budget: BudgetOverviewDto? = null,
        val categories: List<CategoryStatDto> = emptyList(),
        val categoryTotal: Long = 0L,
        val trend: List<TrendItemDto> = emptyList(),
        val totalAssets: Long = 0L,
        val assets: List<AccountAssetDto> = emptyList(),
        /** 支出分类，供预算设置选择范围 */
        val expenseCategories: List<CategoryStatDto> = emptyList(),
        val message: String? = null
    ) {
        /** 是否可以往未来翻月 */
        val canGoNext: Boolean
            get() = YearMonth.parse(month).isBefore(YearMonth.now())
    }

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private var bound = -1L

    fun bind(bookId: Long, shared: Boolean) {
        if (bound == bookId && _state.value.shared == shared) {
            return
        }
        bound = bookId
        _state.update {
            it.copy(bookId = bookId, shared = shared, month = YearMonth.now().toString())
        }
        load()
    }

    /** 全部数据来自本地聚合，断网也能看 */
    fun load() {
        val bookId = _state.value.bookId
        val shared = _state.value.shared
        val month = YearMonth.parse(_state.value.month)
        viewModelScope.launch {
            _state.update { it.copy(loading = true) }

            val overview = reportRepository.overview(bookId, month)
            val budget = budgetRepository.overview(bookId, month.toString())
            val category = reportRepository.category(bookId, _state.value.categoryType, month)
            val trend = reportRepository.trendOfMonth(bookId, month)
            val assets = reportRepository.assets(bookId)
            val expenseCategories = reportRepository.expenseCategories(bookId)
            val members = if (shared) reportRepository.memberStats(bookId, month) else null

            _state.update {
                it.copy(
                    loading = false,
                    overview = overview,
                    budget = budget,
                    categories = category.items,
                    categoryTotal = category.total,
                    trend = trend,
                    totalAssets = assets.totalAssets,
                    assets = assets.accounts,
                    expenseCategories = expenseCategories,
                    members = members
                )
            }
        }
    }

    /** 切换月份，offset 为 -1 上月 / 1 下月 */
    fun switchMonth(offset: Int) {
        val current = YearMonth.parse(_state.value.month)
        val next = current.plusMonths(offset.toLong())
        // 不允许翻到未来月份
        if (next.isAfter(YearMonth.now())) {
            return
        }
        _state.update { it.copy(month = next.toString()) }
        load()
    }

    /** 回到当前月 */
    fun backToCurrentMonth() {
        val now = YearMonth.now().toString()
        if (_state.value.month == now) {
            return
        }
        _state.update { it.copy(month = now) }
        load()
    }

    fun switchCategoryType(type: Int) {
        if (_state.value.categoryType == type) {
            return
        }
        _state.update { it.copy(categoryType = type) }

        val bookId = _state.value.bookId
        val month = YearMonth.parse(_state.value.month)
        viewModelScope.launch {
            val category = reportRepository.category(bookId, type, month)
            _state.update {
                it.copy(categories = category.items, categoryTotal = category.total)
            }
        }
    }

    /** 设置总预算（categoryId = 0）或分类预算 */
    fun saveBudget(categoryId: Long, amountYuan: String, onDone: (String?) -> Unit) {
        val amount = amountYuan.trim().toBigDecimalOrNull()
        when {
            amount == null -> {
                onDone("请输入正确的金额")
                return
            }
            amount <= java.math.BigDecimal.ZERO -> {
                onDone("金额必须大于 0")
                return
            }
        }

        val bookId = _state.value.bookId
        val period = _state.value.month
        // 元 -> 分，四舍五入规避浮点误差
        val cents = amount.movePointRight(2).setScale(0, java.math.RoundingMode.HALF_UP).toLong()

        viewModelScope.launch {
            budgetRepository.save(bookId, categoryId, period, cents)
            onDone(null)
            load()
        }
    }

    /** 删除预算 */
    fun deleteBudget(budgetId: Long) {
        viewModelScope.launch {
            budgetRepository.delete(budgetId)
            _state.update { it.copy(message = "预算已删除") }
            load()
        }
    }

    fun consumeMessage() {
        _state.update { it.copy(message = null) }
    }
}
