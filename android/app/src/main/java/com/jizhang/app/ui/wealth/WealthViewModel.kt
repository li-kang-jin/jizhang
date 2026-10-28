package com.jizhang.app.ui.wealth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jizhang.app.data.prefs.SettingsStore
import com.jizhang.app.data.repository.LeverageOption
import com.jizhang.app.data.repository.StressResult
import com.jizhang.app.data.repository.WealthPlan
import com.jizhang.app.data.repository.WealthPlanner
import com.jizhang.app.data.repository.WealthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * 财富规划页状态。
 * <p>
 * 三个假设（提取率 / 实际收益率 / 目标年数）是「用户的选择」，落 DataStore；
 * 其余（年支出、储蓄率、净资产）是「账本的事实」，每次进来重算，不接受手填 ——
 * 手填的支出数字会让整页结论变成另一个 app 的结论。
 */
@HiltViewModel
class WealthViewModel @Inject constructor(
    private val wealthRepository: WealthRepository,
    private val settings: SettingsStore
) : ViewModel() {

    data class UiState(
        val loading: Boolean = true,
        val plan: WealthPlan? = null,
        /** 杠杆榜：哪个动作最值钱，按提前年数降序 */
        val leverage: List<LeverageOption> = emptyList(),
        /** 压力测试：失业 / 熊市 / 支出膨胀下结论退多少 */
        val stress: List<StressResult> = emptyList(),
        val error: String? = null,
        val withdrawalRate: Double = WealthPlanner.DEFAULT_WITHDRAWAL_RATE,
        val realReturn: Double = WealthPlanner.DEFAULT_REAL_RETURN,
        val targetYears: Int = DEFAULT_TARGET_YEARS
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private var bookId: Long = 0L
    private var loadedFor: Long = 0L
    private var computeJob: Job? = null
    private var persistJob: Job? = null

    fun bind(bookId: Long) {
        this.bookId = bookId
        viewModelScope.launch {
            // 每次打开都重读假设：用户可能在别处改过，也可能刚记了新账
            if (loadedFor != bookId) {
                loadedFor = bookId
                val rate = settings.fiWithdrawalRate()
                val realReturn = settings.fiRealReturn()
                val targetYears = settings.fiTargetYears()
                _state.update {
                    it.copy(
                        withdrawalRate = rate,
                        realReturn = realReturn,
                        targetYears = targetYears
                    )
                }
            }
            recompute()
        }
    }

    fun setWithdrawalRate(value: Double) {
        _state.update { it.copy(withdrawalRate = value.coerceIn(MIN_RATE, MAX_RATE)) }
        persist()
        recompute()
    }

    fun setRealReturn(value: Double) {
        _state.update { it.copy(realReturn = value.coerceIn(MIN_RETURN, MAX_RETURN)) }
        persist()
        recompute()
    }

    fun setTargetYears(value: Int) {
        _state.update { it.copy(targetYears = value.coerceIn(MIN_TARGET_YEARS, MAX_TARGET_YEARS)) }
        persist()
        recompute()
    }

    /** 重新抽一次样 + 重跑模拟（下拉刷新 / 从设置回来时用） */
    fun refresh() = recompute()

    private fun recompute() {
        if (bookId == 0L) return
        computeJob?.cancel()
        computeJob = viewModelScope.launch {
            // 拖动滑块会连续触发：等 120ms 再算，只跑最后那一档，
            // 否则一串任务各自跑满 5000 条路径，CPU 白烧还把结果写乱
            delay(120)
            val snapshot = _state.value
            _state.update { it.copy(loading = true, error = null) }
            val result = runCatching {
                withContext(Dispatchers.Default) {
                    wealthRepository.plan(
                        bookId = bookId,
                        withdrawalRate = snapshot.withdrawalRate,
                        realReturn = snapshot.realReturn,
                        targetYears = snapshot.targetYears
                    )
                }
            }
            _state.update {
                val planning = result.getOrNull()
                it.copy(
                    loading = false,
                    plan = planning?.plan ?: it.plan,
                    leverage = planning?.leverage ?: it.leverage,
                    stress = planning?.stress ?: it.stress,
                    error = result.exceptionOrNull()?.message
                )
            }
        }
    }

    /**
     * 假设落盘：拖动滑块时每帧都写一次 DataStore 太重，等手停下来 400ms 再写。
     * 中途被杀进程最多丢最后一次拖动，不影响账单本身。
     */
    private fun persist() {
        persistJob?.cancel()
        persistJob = viewModelScope.launch {
            delay(400)
            val snapshot = _state.value
            settings.setFiWithdrawalRate(snapshot.withdrawalRate)
            settings.setFiRealReturn(snapshot.realReturn)
            settings.setFiTargetYears(snapshot.targetYears)
        }
    }

    private companion object {
        const val DEFAULT_TARGET_YEARS = 20

        // 允许的区间：低于 3% 提取率的方案在多数市场近似"永不失败"，高于 4.5% 则风险明显偏高
        const val MIN_RATE = 0.025
        const val MAX_RATE = 0.05
        const val MIN_RETURN = 0.0
        const val MAX_RETURN = 0.08
        const val MIN_TARGET_YEARS = 5
        const val MAX_TARGET_YEARS = 40
    }
}
