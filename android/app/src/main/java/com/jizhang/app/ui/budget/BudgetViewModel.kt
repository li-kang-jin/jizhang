package com.jizhang.app.ui.budget

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jizhang.app.data.local.entity.LocalRecurringExpense
import com.jizhang.app.data.remote.dto.BudgetOverviewDto
import com.jizhang.app.data.remote.dto.CategoryStatDto
import com.jizhang.app.data.repository.BudgetCycle
import com.jizhang.app.data.repository.BudgetForecast
import com.jizhang.app.data.repository.BudgetPeriods
import com.jizhang.app.data.repository.BudgetRepository
import com.jizhang.app.data.repository.FixedReservation
import com.jizhang.app.data.repository.ReportRepository
import com.jizhang.app.data.repository.TransactionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.math.BigDecimal
import java.math.RoundingMode
import javax.inject.Inject

/**
 * 预算页。
 * <p>
 * 支持每月 / 每周 / 每年三种周期，进入当前周期且没有任何预算时，
 * 会自动沿用上一周期的额度，省去每次重新设置。
 * <p>
 * 固定支出只做「预算内预留」：不自动生成账单，但会把本期还没到期的房租 / 房贷
 * 从每日可花里扣掉（见 [BudgetRepository.reservedFixed]）。
 */
@HiltViewModel
class BudgetViewModel @Inject constructor(
    private val budgetRepository: BudgetRepository,
    private val reportRepository: ReportRepository,
    private val transactionRepository: TransactionRepository
) : ViewModel() {

    data class UiState(
        val bookId: Long = 0L,
        val cycle: BudgetCycle = BudgetCycle.MONTH,
        val period: String = BudgetPeriods.of(BudgetCycle.MONTH),
        val loading: Boolean = false,
        val overview: BudgetOverviewDto? = null,
        val expenseCategories: List<CategoryStatDto> = emptyList(),
        /** 全部固定支出（含已停用） */
        val recurring: List<LocalRecurringExpense> = emptyList(),
        /** 本期还没到期的固定支出预留明细 */
        val reservation: FixedReservation = FixedReservation(),
        /** 本次进入自动沿用上一周期的预算条数 */
        val inheritedCount: Int = 0,
        /** 趋势 / 超支概率预测；没设总预算时为 null */
        val forecast: BudgetForecast? = null,
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
    private var recurringJob: Job? = null
    private var dataWatchJob: Job? = null

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

        // 用 Flow 监听而不是一次性读取：在固定支出弹窗里增删后这里要立刻刷新
        recurringJob?.cancel()
        recurringJob = viewModelScope.launch {
            budgetRepository.observeRecurring(bookId).collect { list ->
                _state.update { it.copy(recurring = list) }
            }
        }

        /*
         * 本期「已花 / 剩余」是按账单现算的聚合，本身没有观察者：
         * 记一笔 / 删除 / 导入 / 同步之后不重算的话，预算页会一直停在旧数字上，
         * 必须切走再切回来（bind 早退，也不会重查）。
         * drop(1) 跳过订阅首帧：那份数据紧接着的 load() 就会取。
         */
        dataWatchJob?.cancel()
        dataWatchJob = viewModelScope.launch {
            transactionRepository.observeTransactions(bookId).drop(1).collect {
                // 不沿用上一周期：那是一次写入，不该被账单变化反复触发
                load()
            }
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
            val reservation = budgetRepository.reservedFixed(current.bookId, current.period)
            val categories = reportRepository.expenseCategories(current.bookId)
            // 2000 条蒙特卡洛路径在 Default 上跑，别占主线程；没预算时内部直接返回 null
            val forecast = withContext(Dispatchers.Default) {
                budgetRepository.forecast(current.bookId, current.period, overview, reservation)
            }

            /*
             * 落地前确认没切过账本 / 周期。
             * 冷启动时 bookId 先从默认的本地账本变成真实账本，两次 load 会并发跑：
             * 旧的那次如果后完成，会把整页刷成「没有预算、没有支出」的空状态。
             */
            if (_state.value.bookId != current.bookId || _state.value.period != current.period) {
                return@launch
            }

            _state.update {
                it.copy(
                    loading = false,
                    overview = overview,
                    expenseCategories = categories,
                    reservation = reservation,
                    forecast = forecast,
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
            // 离线优先：本地写完就成功，服务端由后续同步补推（所以这里不再拿网络错误打扰用户）
            budgetRepository.save(current.bookId, categoryId, current.period, cents)
                .onSuccess {
                    onDone(null)
                    load()
                    _state.update { it.copy(message = "预算已保存") }
                }
                .onFailure { onDone(it.message ?: "保存失败") }
        }
    }

    fun deleteBudget(budgetId: Long) {
        val bookId = _state.value.bookId
        viewModelScope.launch {
            budgetRepository.delete(budgetId, bookId)
                .onSuccess {
                    load()
                    _state.update { it.copy(message = "预算已删除") }
                }
                .onFailure { error ->
                    _state.update { it.copy(message = error.message ?: "删除失败") }
                }
        }
    }

    /* ---------------- 固定支出 ---------------- */

    fun saveRecurring(item: LocalRecurringExpense) {
        viewModelScope.launch {
            budgetRepository.saveRecurring(item)
                .onSuccess {
                    load()
                    _state.update { it.copy(message = "已保存") }
                }
                .onFailure { error ->
                    _state.update { it.copy(message = error.message ?: "保存失败") }
                }
        }
    }

    fun deleteRecurring(id: Long) {
        val bookId = _state.value.bookId
        viewModelScope.launch {
            budgetRepository.deleteRecurring(id, bookId)
                .onSuccess {
                    load()
                    _state.update { it.copy(message = "已删除") }
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
