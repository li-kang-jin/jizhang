package com.jizhang.app.ui.record

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jizhang.app.data.local.entity.LocalAccount
import com.jizhang.app.data.local.entity.LocalCategory
import com.jizhang.app.data.repository.LedgerRepository
import com.jizhang.app.data.repository.TransactionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class RecordViewModel @Inject constructor(
    private val ledgerRepository: LedgerRepository,
    private val transactionRepository: TransactionRepository
) : ViewModel() {

    data class UiState(
        /** 1=支出 2=收入 */
        val type: Int = 1,
        val amountText: String = "",
        val remark: String = "",
        val accounts: List<LocalAccount> = emptyList(),
        val categories: List<LocalCategory> = emptyList(),
        val selectedAccountId: Long? = null,
        val selectedCategoryId: Long? = null,
        val saving: Boolean = false
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    fun reset() {
        _state.value = UiState()
    }

    fun load(bookId: Long) {
        viewModelScope.launch {
            val accounts = ledgerRepository.cachedAccounts(bookId)
            _state.update {
                it.copy(
                    accounts = accounts,
                    selectedAccountId = it.selectedAccountId ?: accounts.firstOrNull()?.id
                )
            }
            loadCategories(bookId, _state.value.type)
        }
    }

    private suspend fun loadCategories(bookId: Long, type: Int) {
        val categories = ledgerRepository.cachedCategories(bookId, type)
        _state.update {
            it.copy(categories = categories, selectedCategoryId = categories.firstOrNull()?.id)
        }
    }

    fun switchType(bookId: Long, type: Int) {
        if (_state.value.type == type) {
            return
        }
        _state.update { it.copy(type = type) }
        viewModelScope.launch { loadCategories(bookId, type) }
    }

    fun updateAmount(text: String) {
        // 只保留数字和小数点，且最多两位小数
        val filtered = text.filter { it.isDigit() || it == '.' }
        if (filtered.count { it == '.' } > 1) {
            return
        }
        val decimals = filtered.substringAfter('.', "")
        if (decimals.length > 2) {
            return
        }
        _state.update { it.copy(amountText = filtered) }
    }

    fun updateRemark(text: String) {
        _state.update { it.copy(remark = text) }
    }

    fun selectAccount(id: Long) {
        _state.update { it.copy(selectedAccountId = id) }
    }

    fun selectCategory(id: Long) {
        _state.update { it.copy(selectedCategoryId = id) }
    }

    /** 保存到本地（离线可用），返回错误信息或 null（成功） */
    fun save(bookId: Long, onDone: (String?) -> Unit) {
        val current = _state.value
        val yuan = current.amountText.toDoubleOrNull()
        if (yuan == null || yuan <= 0.0) {
            onDone("请输入有效金额")
            return
        }
        val accountId = current.selectedAccountId
        if (accountId == null) {
            onDone("请先选择账户（可先在服务端创建账户）")
            return
        }

        viewModelScope.launch {
            _state.update { it.copy(saving = true) }
            transactionRepository.addLocal(
                bookId = bookId,
                type = current.type,
                amount = (yuan * 100).toLong(),
                accountId = accountId,
                categoryId = current.selectedCategoryId,
                tradeTime = System.currentTimeMillis(),
                remark = current.remark.ifBlank { null }
            )
            _state.update { it.copy(saving = false, amountText = "", remark = "") }
            onDone(null)
        }
    }
}
