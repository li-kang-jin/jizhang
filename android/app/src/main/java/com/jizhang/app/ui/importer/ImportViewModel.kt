package com.jizhang.app.ui.importer

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jizhang.app.data.imports.BillCsvParser
import com.jizhang.app.data.imports.BillImporter
import com.jizhang.app.data.imports.CsvParseResult
import com.jizhang.app.data.imports.ImportSummary
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * 账单导入流程：
 * 选择文件 → 解析预览 → 确认导入 → 结果汇报。
 */
@HiltViewModel
class ImportViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val billImporter: BillImporter
) : ViewModel() {

    data class UiState(
        val bookId: Long = 0L,
        val fileName: String? = null,
        val parsing: Boolean = false,
        /** 解析后的完整结果（预览只展示前若干条，导入用全量） */
        val parsed: CsvParseResult? = null,
        val importing: Boolean = false,
        val summary: ImportSummary? = null,
        val error: String? = null
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    fun bind(bookId: Long) {
        _state.update { it.copy(bookId = bookId) }
    }

    fun selectFile(uri: Uri) {
        viewModelScope.launch {
            _state.update {
                it.copy(
                    parsing = true,
                    fileName = fileNameOf(uri),
                    parsed = null,
                    summary = null,
                    error = null
                )
            }
            runCatching {
                withContext(Dispatchers.IO) {
                    val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                        ?: error("无法读取所选文件")
                    BillCsvParser.parse(bytes)
                }
            }.onSuccess { result ->
                _state.update {
                    it.copy(
                        parsing = false,
                        parsed = result,
                        error = if (result.bills.isEmpty()) {
                            "没有解析到可导入的账单，请确认文件格式"
                        } else {
                            null
                        }
                    )
                }
            }.onFailure { error ->
                _state.update { it.copy(parsing = false, error = "解析失败：${error.message}") }
            }
        }
    }

    fun confirmImport() {
        val parsed = _state.value.parsed ?: return
        val bookId = _state.value.bookId
        viewModelScope.launch {
            _state.update { it.copy(importing = true) }
            runCatching { billImporter.import(bookId, parsed.bills) }
                .onSuccess { summary ->
                    _state.update { it.copy(importing = false, summary = summary, parsed = null) }
                }
                .onFailure { error ->
                    _state.update { it.copy(importing = false, error = "导入失败：${error.message}") }
                }
        }
    }

    fun consumeError() {
        _state.update { it.copy(error = null) }
    }

    fun reset() {
        _state.value = UiState(bookId = _state.value.bookId)
    }

    private fun fileNameOf(uri: Uri): String =
        uri.lastPathSegment?.substringAfterLast('/')?.takeIf { it.isNotBlank() } ?: "所选文件"
}
