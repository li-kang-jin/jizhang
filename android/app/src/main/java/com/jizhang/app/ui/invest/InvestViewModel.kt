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
        val valuing: PositionWithValue? = null,
        /** 「更新全部市值」弹窗是否打开 */
        val valuingAll: Boolean = false
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

    fun openValuingAll() {
        _state.update { it.copy(valuingAll = true) }
    }

    fun closeValuingAll() {
        _state.update { it.copy(valuingAll = false) }
    }

    /**
     * 批量更新市值（「更新全部市值」）。
     * <p>
     * 只提交和当前显示不一样的品种：给每个品种都补一条快照的话，没改过的那些也会在
     * 历史曲线上多一个点，看不出哪天是真的核对过。
     */
    fun updateAllValues(texts: Map<Long, String>) {
        val positions = _state.value.overview.positions
        if (bookId <= 0L || positions.isEmpty()) return
        viewModelScope.launch {
            val updates = positions.mapNotNull { position ->
                val text = texts[position.id]
                if (text.isNullOrBlank()) return@mapNotNull null
                val fen = parseFen(text)
                if (fen == position.marketValueFen) null else position.id to fen
            }.toMap()
            repository.updateValues(bookId, updates)
            closeValuingAll()
        }
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
                // 留空表示不动市值：编辑时把旧值预填进去再保存，会平白多出一条快照
                initialValueFen = valueText.takeIf { it.isNotBlank() }?.let(::parseFen),
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
