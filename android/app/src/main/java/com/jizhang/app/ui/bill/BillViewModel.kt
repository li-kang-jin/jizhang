package com.jizhang.app.ui.bill

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jizhang.app.data.local.entity.LocalTransaction
import com.jizhang.app.data.repository.BookRepository
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
import javax.inject.Inject

@HiltViewModel
class BillViewModel @Inject constructor(
    private val transactionRepository: TransactionRepository,
    private val reportRepository: ReportRepository,
    private val bookRepository: BookRepository,
    private val petRepository: PetRepository,
    private val syncManager: SyncManager
) : ViewModel() {

    data class UiState(
        val bookId: Long = 0L,
        val transactions: List<LocalTransaction> = emptyList(),
        val pendingCount: Int = 0,
        val monthIncome: Long = 0L,
        val monthExpense: Long = 0L,
        val pet: PetState = PetState(),
        val petMood: PetMood = PetMood.NORMAL,
        /** 记账人 userId → 展示名（家庭账本里显示「谁记的」） */
        val memberLabels: Map<Long, String> = emptyMap(),
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
                transactionRepository.observeTransactions(bookId).collect { list ->
                    _state.update { it.copy(transactions = list) }
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
        }

        refresh()
        sync()
    }

    /** 重新计算本月收支与宠物心情 */
    fun refresh() {
        val bookId = _state.value.bookId
        viewModelScope.launch {
            val overview = reportRepository.overview(bookId)
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
            _state.update { it.copy(syncing = true) }
            syncManager.sync(bookId)
                .onSuccess { refresh() }
                .onFailure { e ->
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
}
