package com.jizhang.app.ui.invest

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jizhang.app.data.local.entity.PositionWithValue
import com.jizhang.app.data.repository.InvestmentOverview
import com.jizhang.app.data.repository.InvestmentRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.roundToInt

/**
 * 投资页状态。
 * <p>
 * 页面上只有「持仓」是本地数据，总市值 / 成本 / 未分配资金全部由仓库实时聚合 ——
 * 账户余额变了（转账、校准），未分配资金立刻跟着变，不需要任何额外维护。
 */
@HiltViewModel
class InvestViewModel @Inject constructor(
    private val repository: InvestmentRepository
) : ViewModel() {

    data class UiState(
        val overview: InvestmentOverview = InvestmentOverview(),
        val loading: Boolean = true,
        /** 品种编辑弹窗是否打开（新建或编辑共用） */
        val editorOpen: Boolean = false,
        /** 正在编辑的品种；null = 新建 */
        val editing: PositionWithValue? = null,
        /** 新建时预填的成本（用于一句「把未分配资金归到这个新品种」） */
        val costPrefillFen: Long? = null,
        /** 正在更新市值的品种 */
        val valuing: PositionWithValue? = null
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private var bookId: Long = 0L
    private var observeJob: Job? = null

    fun bind(id: Long) {
        if (bookId == id && observeJob != null) return
        bookId = id
        observeJob?.cancel()
        observeJob = viewModelScope.launch {
            repository.observe(id).collect { overview ->
                _state.update { it.copy(overview = overview, loading = false) }
            }
        }
    }

    /** [costPrefillFen] 非空时把成本预填进去：未分配资金一键认领 */
    fun openCreate(costPrefillFen: Long? = null) {
        _state.update {
            it.copy(editorOpen = true, editing = null, costPrefillFen = costPrefillFen)
        }
    }

    fun openEdit(position: PositionWithValue) {
        _state.update { it.copy(editorOpen = true, editing = position, costPrefillFen = null) }
    }

    fun closeEditor() {
        _state.update { it.copy(editorOpen = false, editing = null, costPrefillFen = null) }
    }

    fun openValuing(position: PositionWithValue) {
        _state.update { it.copy(valuing = position) }
    }

    fun closeValuing() {
        _state.update { it.copy(valuing = null) }
    }

    fun save(
        accountId: Long,
        name: String,
        code: String,
        kind: Int,
        costText: String,
        valueText: String,
        note: String
    ) {
        val editing = _state.value.editing
        if (name.isBlank() || accountId <= 0L || bookId <= 0L) return
        viewModelScope.launch {
            repository.savePosition(
                id = editing?.id,
                bookId = bookId,
                accountId = accountId,
                name = name,
                code = code,
                kind = kind,
                costFen = parseFen(costText),
                // 只有新建时允许顺手填市值：编辑时改市值应该走「更新」，否则会多出一条快照
                initialValueFen = if (editing == null && valueText.isNotBlank()) {
                    parseFen(valueText)
                } else {
                    null
                },
                note = note
            )
            closeEditor()
        }
    }

    fun updateValue(position: PositionWithValue, text: String) {
        if (text.isBlank() || bookId <= 0L) return
        viewModelScope.launch {
            repository.updateValue(bookId, position.id, parseFen(text))
            closeValuing()
        }
    }

    fun delete(position: PositionWithValue) {
        viewModelScope.launch {
            repository.deletePosition(position.id)
            closeEditor()
            _state.update { it.copy(valuing = null) }
        }
    }

    /** 元字符串 → 分。空串与非法输入都按 0 处理（新建品种允许先不填钱） */
    private fun parseFen(text: String): Long =
        ((text.toDoubleOrNull() ?: 0.0) * 100).roundToInt().toLong().coerceAtLeast(0L)
}
