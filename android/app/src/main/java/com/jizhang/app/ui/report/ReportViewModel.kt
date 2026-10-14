package com.jizhang.app.ui.report

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jizhang.app.data.prefs.TokenStore
import com.jizhang.app.data.remote.dto.AccountAssetDto
import com.jizhang.app.data.remote.dto.CategoryStatDto
import com.jizhang.app.data.remote.dto.ReportOverviewDto
import com.jizhang.app.data.remote.dto.TrendItemDto
import com.jizhang.app.data.repository.ReportRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ReportViewModel @Inject constructor(
    private val tokenStore: TokenStore,
    private val reportRepository: ReportRepository
) : ViewModel() {

    data class UiState(
        val bookId: Long = 0L,
        val loading: Boolean = false,
        val categoryType: Int = 1,
        val overview: ReportOverviewDto? = null,
        val categories: List<CategoryStatDto> = emptyList(),
        val categoryTotal: Long = 0L,
        val trend: List<TrendItemDto> = emptyList(),
        val totalAssets: Long = 0L,
        val assets: List<AccountAssetDto> = emptyList(),
        val message: String? = null
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val bookId = tokenStore.bookIdFlow.first()
            if (bookId <= 0L) {
                _state.update { it.copy(message = "未获取到账本") }
                return@launch
            }
            _state.update { it.copy(bookId = bookId) }
            load()
        }
    }

    fun load() {
        val bookId = _state.value.bookId
        if (bookId <= 0L) {
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(loading = true) }
            runCatching {
                val overview = reportRepository.overview(bookId)
                val category = reportRepository.category(bookId, _state.value.categoryType)
                val trend = reportRepository.trend(bookId, "day")
                val assets = reportRepository.assets(bookId)
                Triple(overview, category, trend) to assets
            }.onSuccess { (report, assets) ->
                val (overview, category, trend) = report
                _state.update {
                    it.copy(
                        loading = false,
                        overview = overview,
                        categories = category.items,
                        categoryTotal = category.total,
                        trend = trend,
                        totalAssets = assets.totalAssets,
                        assets = assets.accounts
                    )
                }
            }.onFailure { e ->
                _state.update { it.copy(loading = false, message = "统计加载失败：${e.message}") }
            }
        }
    }

    fun switchCategoryType(type: Int) {
        if (_state.value.categoryType == type) {
            return
        }
        _state.update { it.copy(categoryType = type) }
        val bookId = _state.value.bookId
        viewModelScope.launch {
            runCatching { reportRepository.category(bookId, type) }
                .onSuccess { data ->
                    _state.update { it.copy(categories = data.items, categoryTotal = data.total) }
                }
        }
    }

    fun consumeMessage() {
        _state.update { it.copy(message = null) }
    }
}
