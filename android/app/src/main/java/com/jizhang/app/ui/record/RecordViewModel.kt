package com.jizhang.app.ui.record

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jizhang.app.data.local.entity.LocalAccount
import com.jizhang.app.data.local.entity.LocalCategory
import com.jizhang.app.data.local.entity.LocalTransaction
import com.jizhang.app.data.repository.CategoryRepository
import com.jizhang.app.data.repository.LedgerRepository
import com.jizhang.app.data.repository.TransactionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
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
    private val transactionRepository: TransactionRepository,
    private val categoryRepository: CategoryRepository
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
        val saving: Boolean = false,
        /** 非空表示正在改这笔账单（保存时走更新而不是新增） */
        val editing: LocalTransaction? = null,
        /** 最近用过的分类（最新在前），经典模板把它做成「常用」快捷行 */
        val recentCategoryIds: List<Long> = emptyList()
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

    private companion object {
        /** 「常用」快捷行最多展示几个分类 */
        const val RECENT_CATEGORY_LIMIT = 4
    }

    /**
     * 打开记账页时加载账户与分类。
     *
     * **不能**因为「还是同一个账本」就早退：用户可能刚在账单页的「分类管理」里加了分类，
     * 再打开记账页必须能看到它。只有切账本时才需要把表单整体重置。
     */
    fun load(bookId: Long) {
        val bookChanged = loadedBookId != bookId
        loadedBookId = bookId
        // 明确清掉编辑目标：从「编辑」切到「记一笔」时表单可能被保留（同一账本），
        // 若 editing 残留，下一次保存会去改上一笔账
        _state.update {
            if (bookChanged) UiState(bookId = bookId) else it.copy(bookId = bookId, editing = null)
        }

        viewModelScope.launch {
            val type = _state.value.type
            val accounts = ledgerRepository.cachedAccounts(bookId)
            val categories = ledgerRepository.cachedCategories(bookId, type)
            _state.update { current ->
                current.copy(
                    accounts = accounts,
                    // 保留仍然有效的选中项；被删掉（或在别的账本里）时回落到第一项
                    selectedAccountId = current.selectedAccountId
                        ?.takeIf { id -> accounts.any { it.id == id } }
                        ?: accounts.firstOrNull()?.id,
                    categories = categories,
                    selectedCategoryId = current.selectedCategoryId
                        ?.takeIf { id -> categories.any { it.id == id } }
                        ?: categories.firstOrNull()?.id
                )
            }
        }
    }

    /**
     * 进入「编辑账单」态：把已有账单回填进表单。
     * <p>
     * 回填顺序有讲究：分类列表是按收支类型缓存的，`switchType` / `loadCategories` 会把选中项
     * 重置成第一个，所以必须先落好 type + categoryId，再拉列表，最后用「列表里确实存在才保留」
     * 的方式兜住原值（原来的分类被删掉时回落到第一个）。
     */
    fun startEdit(bookId: Long, transaction: LocalTransaction) {
        loadedBookId = bookId
        _state.update {
            it.copy(
                bookId = bookId,
                editing = transaction,
                type = transaction.type,
                // 金额回填成一个数字，用户可以接着用计算器改（例如「2500+300」）
                tokens = listOf(CalcNum(yuanPlainText(transaction.amount))),
                selectedAccountId = transaction.accountId,
                selectedToAccountId = transaction.toAccountId,
                selectedCategoryId = transaction.categoryId,
                tradeDate = Instant.ofEpochMilli(transaction.tradeTime)
                    .atZone(ZoneId.systemDefault())
                    .toLocalDate(),
                remark = transaction.remark.orEmpty(),
                tags = transaction.tags.orEmpty()
            )
        }

        viewModelScope.launch {
            val accounts = ledgerRepository.cachedAccounts(bookId)
            val categories = ledgerRepository.cachedCategories(bookId, transaction.type)
            _state.update { current ->
                current.copy(
                    accounts = accounts,
                    selectedAccountId = current.selectedAccountId
                        ?.takeIf { id -> accounts.any { it.id == id } }
                        ?: accounts.firstOrNull()?.id,
                    categories = categories,
                    selectedCategoryId = current.selectedCategoryId
                        ?.takeIf { id -> categories.any { it.id == id } }
                        ?: categories.firstOrNull()?.id
                )
            }
        }
    }

    /** 「分」→ 计算器输入用的「元」文本（去掉多余的零，2500 而不是 2500.00） */
    private fun yuanPlainText(fen: Long): String =
        BigDecimal.valueOf(fen, 2).stripTrailingZeros().toPlainString()

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
            val editing = current.editing
            val originalDate = editing?.let {
                Instant.ofEpochMilli(it.tradeTime).atZone(ZoneId.systemDefault()).toLocalDate()
            }
            // 时间规则：今天的账用「保存这一刻」的真实时间；补记历史日期的账统一记当天 12:00
            //（与导入缺时间时的规则一致，避免列表里整屏 00:00）。
            // 编辑时没动日期就沿用原发生时间，免得「只改个备注」把记账时刻也挪走了。
            val millis = when {
                editing != null && originalDate == current.tradeDate -> editing.tradeTime
                current.tradeDate == LocalDate.now() -> System.currentTimeMillis()
                else -> current.tradeDate
                    .atTime(12, 0)
                    .atZone(ZoneId.systemDefault())
                    .toInstant()
                    .toEpochMilli()
            }

            val accountId = current.selectedAccountId!!
            val toAccountId = if (current.type == 3) current.selectedToAccountId else null
            val categoryId = if (current.type == 3) null else current.selectedCategoryId
            if (editing == null) {
                transactionRepository.addLocal(
                    bookId = current.bookId,
                    type = current.type,
                    amount = fen,
                    accountId = accountId,
                    toAccountId = toAccountId,
                    categoryId = categoryId,
                    tradeTime = millis,
                    remark = current.remark.ifBlank { null },
                    tags = current.tags.ifBlank { null }
                )
            } else {
                val updated = transactionRepository.updateLocal(
                    id = editing.id,
                    type = current.type,
                    amount = fen,
                    accountId = accountId,
                    toAccountId = toAccountId,
                    categoryId = categoryId,
                    tradeTime = millis,
                    remark = current.remark.ifBlank { null },
                    tags = current.tags.ifBlank { null }
                )
                if (!updated) {
                    _state.update { it.copy(saving = false) }
                    onDone("这笔账单已被删除，无法保存修改")
                    return@launch
                }
            }
            rememberCategory(current.type, current.selectedCategoryId)
            _state.update { it.copy(saving = false) }
            onDone(null)
        }
    }

    /**
     * 记账页分类区的「＋新建」：在当前收支类型下建一个分类，成功后自动选中它。
     *
     * 类型跟随当前记账类型而不是让用户选 —— 否则新建的分类不在当前分类列表里，看起来像没建成功。
     *
     * @param onDone 返回错误文案，null 表示成功
     */
    fun addCategory(name: String, icon: String, onDone: (String?) -> Unit) {
        val bookId = _state.value.bookId
        val type = _state.value.type
        if (bookId <= 0L) {
            onDone("账本还没准备好，稍后再试")
            return
        }
        viewModelScope.launch {
            categoryRepository.create(bookId, name, type, icon)
                .onSuccess { created ->
                    // 先按类型重查（会默认选中第一个），再覆盖成本次新建的那个
                    loadCategories(bookId, type)
                    _state.update { it.copy(selectedCategoryId = created.id) }
                    onDone(null)
                }
                .onFailure { onDone(it.message ?: "创建分类失败") }
        }
    }

    /** 记住刚用过的分类，供经典模板的「常用」快捷行使用 */
    private fun rememberCategory(type: Int, categoryId: Long?) {
        if (type == 3 || categoryId == null) {
            return
        }
        val next = (listOf(categoryId) + _state.value.recentCategoryIds.filter { it != categoryId })
            .take(RECENT_CATEGORY_LIMIT)
        _state.update { it.copy(recentCategoryIds = next) }
    }

    /**
     * 清空表单。
     *
     * 记账页是 Activity 作用域的常驻 ViewModel（工程没有 Navigation，见 [com.jizhang.app.ui.common.AppFullScreenDialog]），
     * 页面只是反复 show/hide 覆盖层，**同一个 VM 实例存活全程**。所以每次关闭记账页都必须显式复位，
     * 否则下次打开会看到上一次的金额表达式、备注、标签、分类选择（「记完账旧数据没清零」）。
     *
     * @param preserveType 保留「支出/收入」与分类选择（极简模板连续记同类账时更顺手）；
     *                     为 false 时回到默认：支出 + 第一个分类。
     */
    fun resetForm(preserveType: Boolean = false) {
        val snapshot = _state.value
        val type = if (preserveType) snapshot.type else 1
        val keepCategoryId = if (preserveType) snapshot.selectedCategoryId else null

        _state.update {
            it.copy(
                type = type,
                tokens = emptyList(),
                remark = "",
                tags = "",
                selectedToAccountId = null,
                selectedCategoryId = keepCategoryId,
                tradeDate = LocalDate.now(),
                editing = null
            )
        }

        // 分类是按类型缓存的：类型变了必须重查一次，否则保留的 categoryId 会落在错误的类型上
        val bookId = snapshot.bookId
        if (bookId <= 0L) {
            return
        }
        viewModelScope.launch {
            val categories = ledgerRepository.cachedCategories(bookId, type)
            val kept = categories.firstOrNull { it.id == keepCategoryId }?.id
            _state.update {
                it.copy(
                    categories = categories,
                    selectedCategoryId = kept ?: categories.firstOrNull()?.id
                )
            }
        }
    }
}
