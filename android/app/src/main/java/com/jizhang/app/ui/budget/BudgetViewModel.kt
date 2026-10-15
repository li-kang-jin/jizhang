package com.jizhang.app.ui.budget

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jizhang.app.data.remote.dto.BudgetOverviewDto
import com.jizhang.app.data.remote.dto.CategoryStatDto
import com.jizhang.app.data.repository.BudgetCycle
import com.jizhang.app.data.repository.BudgetPeriods
import com.jizhang.app.data.repository.BudgetRepository
import com.jizhang.app.data.repository.ReportRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.math.RoundingMode
import javax.inject.Inject

/**
 * 预算页。
 * <p>
 * 支持每月 / 每周 / 每年三种周期，进入当前周期且没有任何预算时，
 * 会自动沿用上一周期的额度，省去每次重新设置。
 */
@HiltViewModel
class BudgetViewModel @Inject constructor(
    private val budgetRepository: BudgetRepository,
    private val reportRepository: ReportRepository
) : ViewModel() {

    data class UiState(
        val bookId: Long = 0L,
        val cycle: BudgetCycle = BudgetCycle.MONTH,
        val period: String = BudgetPeriods.of(BudgetCycle.MONTH),
        val loading: Boolean = false,
        val overview: BudgetOverviewDto? = null,
        val expenseCategories: List<CategoryStatDto> = emptyList(),
        /** 本次进入自动沿用上一周期的预算条数 */
        val inheritedCount: Int = 0,
        val message: String? = null
    ) {
        val periodLabel: String get() = BudgetPeriods.label(period)

        /** 允许往未来翻，但不得超过当前周期 */
        val canGoNext: Boolean
            get() = BudgetPeriods.isAfter(BudgetPeriods.of(cycle), period)

        val isCurrent: Boolean get() = period == BudgetPeriods.of(cycle)
    }

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private var bound = -1L

    fun bind(bookId: Long) {
        if (bound == bookId) {
            return
        }
        bound = bookId
        _state.update {
            it.copy(
                bookId = bookId,
                cycle = BudgetCycle.MONTH,
                period = BudgetPeriods.of(BudgetCycle.MONTH),
                inheritedCount = 0
            )
        }
        load(autoInherit = true)
    }

    fun switchCycle(cycle: BudgetCycle) {
        if (_state.value.cycle == cycle) {
            return
        }
        _state.update {
            it.copy(cycle = cycle, period = BudgetPeriods.of(cycle), inheritedCount = 0)
        }
        load(autoInherit = true)
    }

    fun switchPeriod(offset: Int) {
        val current = _state.value
        val next = BudgetPeriods.plus(current.period, offset.toLong())
        if (BudgetPeriods.isAfter(next, BudgetPeriods.of(current.cycle))) {
            return
        }
        _state.update { it.copy(period = next, inheritedCount = 0) }
        load(autoInherit = false)
    }

    fun backToCurrent() {
        val current = _state.value
        val period = BudgetPeriods.of(current.cycle)
        if (current.period == period) {
            return
        }
        _state.update { it.copy(period = period, inheritedCount = 0) }
        load(autoInherit = true)
    }

    /**
     * 刷新数据。
     * @param autoInherit 仅当停留在当前周期时才自动沿用上一周期，
     * 翻看历史周期时静默处理，避免无端插入数据。
     */
    fun load(autoInherit: Boolean = false) {
        viewModelScope.launch {
            _state.update { it.copy(loading = true) }
            val current = _state.value

            val inherited = if (autoInherit && current.period == BudgetPeriods.of(current.cycle)) {
                budgetRepository.inheritPrevious(current.bookId, current.period)
            } else {
                0
            }

            val overview = budgetRepository.overview(current.bookId, current.period)
            val categories = reportRepository.expenseCategories(current.bookId)

            _state.update {
                it.copy(
                    loading = false,
                    overview = overview,
                    expenseCategories = categories,
                    inheritedCount = inherited,
                    message = if (inherited > 0) {
                        "已沿用上一周期的 $inherited 条预算"
                    } else {
                        it.message
                    }
                )
            }
        }
    }

    /** 手动沿用上一周期 */
    fun inheritPrevious() {
        val current = _state.value
        viewModelScope.launch {
            val count = budgetRepository.inheritPrevious(current.bookId, current.period)
            if (count > 0) {
                load()
                _state.update { it.copy(message = "已沿用上一周期的 $count 条预算") }
            } else {
                _state.update { it.copy(message = "上一周期没有可沿用的预算") }
            }
        }
    }

    fun saveBudget(categoryId: Long, amountYuan: String, onDone: (String?) -> Unit) {
        val amount = amountYuan.trim().toBigDecimalOrNull()
        if (amount == null) {
            onDone("请输入正确的金额")
            return
        }
        if (amount <= BigDecimal.ZERO) {
            onDone("金额必须大于 0")
            return
        }

        val current = _state.value
        // 元 -> 分，四舍五入规避浮点误差
        val cents = amount.movePointRight(2).setScale(0, RoundingMode.HALF_UP).toLong()

        viewModelScope.launch {
            budgetRepository.save(current.bookId, categoryId, current.period, cents)
            onDone(null)
            load()
        }
    }

    fun deleteBudget(budgetId: Long) {
        viewModelScope.launch {
            budgetRepository.delete(budgetId)
            load()
            _state.update { it.copy(message = "预算已删除") }
        }
    }

    fun consumeMessage() {
        _state.update { it.copy(message = null) }
    }
}
