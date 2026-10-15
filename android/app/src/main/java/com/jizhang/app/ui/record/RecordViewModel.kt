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
import java.math.BigDecimal
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

/**
 * 「记一笔」全屏页的 ViewModel。
 * <p>
 * 金额不再走系统键盘，而是维护一段四则运算表达式（[CalcEngine]），
 * 分类 / 账户 / 日期 / 备注 / 标签全部在一个界面内完成。
 */
@HiltViewModel
class RecordViewModel @Inject constructor(
    private val ledgerRepository: LedgerRepository,
    private val transactionRepository: TransactionRepository
) : ViewModel() {

    data class UiState(
        val bookId: Long = 0L,
        /** 1=支出 2=收入 3=转账 */
        val type: Int = 1,
        /** 计算器表达式（单位：元） */
        val tokens: List<CalcToken> = emptyList(),
        val accounts: List<LocalAccount> = emptyList(),
        val categories: List<LocalCategory> = emptyList(),
        val selectedAccountId: Long? = null,
        /** 转账的转入账户 */
        val selectedToAccountId: Long? = null,
        val selectedCategoryId: Long? = null,
        val tradeDate: LocalDate = LocalDate.now(),
        val remark: String = "",
        val tags: String = "",
        val saving: Boolean = false
    ) {
        val expressionText: String get() = CalcEngine.expressionText(tokens)
        val resultYuan: BigDecimal? get() = CalcEngine.evaluate(tokens)

        /** 金额、账户（转账还需转入账户）都齐了才允许保存 */
        val canSave: Boolean
            get() = CalcEngine.resultFen(tokens) != null &&
                selectedAccountId != null &&
                (type != 3 || selectedToAccountId != null)
    }

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private var loadedBookId = -1L

    fun load(bookId: Long) {
        if (loadedBookId == bookId) {
            return
        }
        loadedBookId = bookId
        _state.update { it.copy(bookId = bookId) }
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
            it.copy(
                categories = categories,
                selectedCategoryId = categories.firstOrNull()?.id
            )
        }
    }

    fun switchType(type: Int) {
        if (_state.value.type == type) {
            return
        }
        _state.update { it.copy(type = type) }
        viewModelScope.launch { loadCategories(_state.value.bookId, type) }
    }

    /* ---------------- 计算器 ---------------- */

    fun pressDigit(digit: Char) = updateTokens { CalcEngine.appendDigit(it, digit) }

    fun pressOp(op: Char) = updateTokens { CalcEngine.appendOp(it, op) }

    fun pressBackspace() = updateTokens { CalcEngine.backspace(it) }

    fun pressClear() = updateTokens { CalcEngine.clear() }

    fun pressEquals() = updateTokens { CalcEngine.applyEquals(it) }

    private fun updateTokens(transform: (List<CalcToken>) -> List<CalcToken>) {
        _state.update { it.copy(tokens = transform(it.tokens)) }
    }

    /* ---------------- 选项 ---------------- */

    fun selectAccount(id: Long) {
        _state.update { it.copy(selectedAccountId = id) }
    }

    fun selectToAccount(id: Long) {
        _state.update { it.copy(selectedToAccountId = id) }
    }

    fun selectCategory(id: Long) {
        _state.update { it.copy(selectedCategoryId = id) }
    }

    fun setTradeDate(date: LocalDate) {
        _state.update { it.copy(tradeDate = date) }
    }

    fun setRemark(text: String) {
        _state.update { it.copy(remark = text) }
    }

    fun setTags(text: String) {
        _state.update { it.copy(tags = text) }
    }

    /** 保存到本地（离线可用），返回错误信息或 null（成功） */
    fun save(onDone: (String?) -> Unit) {
        val current = _state.value
        val fen = CalcEngine.resultFen(current.tokens)
        when {
            fen == null -> {
                onDone("请输入金额")
                return
            }

            current.selectedAccountId == null -> {
                onDone("请选择账户")
                return
            }

            current.type == 3 && current.selectedToAccountId == null -> {
                onDone("请选择转入账户")
                return
            }

            current.type == 3 && current.selectedToAccountId == current.selectedAccountId -> {
                onDone("转出与转入账户不能相同")
                return
            }
        }

        viewModelScope.launch {
            _state.update { it.copy(saving = true) }
            val millis = current.tradeDate
                .atStartOfDay(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli()

            transactionRepository.addLocal(
                bookId = current.bookId,
                type = current.type,
                amount = fen,
                accountId = current.selectedAccountId!!,
                toAccountId = if (current.type == 3) current.selectedToAccountId else null,
                categoryId = if (current.type == 3) null else current.selectedCategoryId,
                tradeTime = millis,
                remark = current.remark.ifBlank { null },
                tags = current.tags.ifBlank { null }
            )
            _state.update { it.copy(saving = false) }
            onDone(null)
        }
    }
}
