package com.jizhang.app.ui.bill

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jizhang.app.data.local.entity.LocalTransaction
import com.jizhang.app.data.prefs.TokenStore
import com.jizhang.app.data.repository.ReportRepository
import com.jizhang.app.data.repository.SyncManager
import com.jizhang.app.data.repository.TransactionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class BillViewModel @Inject constructor(
    private val tokenStore: TokenStore,
    private val transactionRepository: TransactionRepository,
    private val reportRepository: ReportRepository,
    private val syncManager: SyncManager
) : ViewModel() {

    data class UiState(
        val bookId: Long = 0L,
        val transactions: List<LocalTransaction> = emptyList(),
        val pendingCount: Int = 0,
        val monthIncome: Long = 0L,
        val monthExpense: Long = 0L,
        val loading: Boolean = false,
        val message: String? = null
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val bookId = tokenStore.bookIdFlow.first()
            if (bookId <= 0L) {
                _state.update { it.copy(message = "未获取到账本，请重新登录") }
                return@launch
            }
            _state.update { it.copy(bookId = bookId) }
            observe(bookId)
            sync()
        }
    }

    private fun observe(bookId: Long) {
        viewModelScope.launch {
            transactionRepository.observeTransactions(bookId).collect { list ->
                _state.update { it.copy(transactions = list) }
            }
        }
        viewModelScope.launch {
            transactionRepository.observePendingCount(bookId).collect { count ->
                _state.update { it.copy(pendingCount = count) }
            }
        }
    }

    /** 同步：先推送本地离线记录，再拉取服务端增量 */
    fun sync() {
        val bookId = _state.value.bookId
        if (bookId <= 0L) {
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(loading = true) }
            syncManager.sync(bookId)
                .onSuccess { loadSummary(bookId) }
                .onFailure { e ->
                    _state.update { it.copy(message = "离线模式：${e.message}") }
                }
            _state.update { it.copy(loading = false) }
        }
    }

    private suspend fun loadSummary(bookId: Long) {
        runCatching { reportRepository.overview(bookId) }.onSuccess { data ->
            _state.update { it.copy(monthIncome = data.income, monthExpense = data.expense) }
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
}
