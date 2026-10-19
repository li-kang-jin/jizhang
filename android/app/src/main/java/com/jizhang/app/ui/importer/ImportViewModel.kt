package com.jizhang.app.ui.importer

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jizhang.app.data.imports.BillCsvParser
import com.jizhang.app.data.imports.BillImporter
import com.jizhang.app.data.imports.CsvParseResult
import com.jizhang.app.data.imports.ImportSummary
import com.jizhang.app.data.prefs.SettingsStore
import com.jizhang.app.data.repository.SyncManager
import com.jizhang.app.data.repository.TransactionRepository
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

/** 清空账本账单的结果 */
data class ClearOutcome(
    /** 清掉的条数 */
    val count: Int,
    /** 服务端的删除是否已同步完成（本地账本无需同步，恒为 true） */
    val synced: Boolean
)

/**
 * 账单导入流程：
 * 选择文件 → 解析预览 → 确认导入 → 结果汇报。
 * <p>
 * 另外提供「清空本账本账单」：导入的源文件有问题时，先把脏数据清掉再重新导入。
 */
@HiltViewModel
class ImportViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val billImporter: BillImporter,
    private val transactionRepository: TransactionRepository,
    private val syncManager: SyncManager,
    private val settingsStore: SettingsStore
) : ViewModel() {

    data class UiState(
        val bookId: Long = 0L,
        val fileName: String? = null,
        /** 当前账本已有的账单数（清空前给用户一个明确的数量） */
        val billCount: Int = 0,
        val parsing: Boolean = false,
        /** 解析后的完整结果（预览只展示前若干条，导入用全量） */
        val parsed: CsvParseResult? = null,
        val importing: Boolean = false,
        val summary: ImportSummary? = null,
        val clearing: Boolean = false,
        val cleared: ClearOutcome? = null,
        val error: String? = null
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    fun bind(bookId: Long) {
        _state.update { it.copy(bookId = bookId) }
        viewModelScope.launch { refreshBillCount() }
    }

    fun selectFile(uri: Uri) {
        viewModelScope.launch {
            _state.update {
                it.copy(
                    parsing = true,
                    fileName = fileNameOf(uri),
                    parsed = null,
                    summary = null,
                    cleared = null,
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
                    refreshBillCount()
                }
                .onFailure { error ->
                    _state.update { it.copy(importing = false, error = "导入失败：${error.message}") }
                }
        }
    }

    /**
     * 清空当前账本的全部账单。
     * <p>
     * 本地只打待删除标记，紧接着推一次同步，让服务端一并逻辑删除并回滚账户余额；
     * 推送失败也不影响本地（数据已不可见），下次自动同步会补推。
     */
    fun clearBills() {
        val bookId = _state.value.bookId
        viewModelScope.launch {
            _state.update { it.copy(clearing = true, error = null, summary = null, cleared = null) }
            runCatching {
                val result = transactionRepository.clearBook(bookId)
                // 离线模式不发起请求：删除标记留在本地，关掉离线后会自动补推
                val synced = when {
                    !result.needSync -> true
                    settingsStore.offlineMode() -> false
                    else -> syncManager.sync(bookId).isSuccess
                }
                result to synced
            }.onSuccess { (result, synced) ->
                _state.update {
                    it.copy(
                        clearing = false,
                        cleared = ClearOutcome(result.count, synced),
                        billCount = 0,
                        // 清空后不保留上一次的解析结果，避免误导入刚清掉的那份旧文件
                        parsed = null
                    )
                }
            }.onFailure { error ->
                _state.update { it.copy(clearing = false, error = "清空失败：${error.message}") }
            }
        }
    }

    fun consumeCleared() {
        _state.update { it.copy(cleared = null) }
    }

    fun consumeError() {
        _state.update { it.copy(error = null) }
    }

    fun reset() {
        _state.value = UiState(bookId = _state.value.bookId, billCount = _state.value.billCount)
    }

    private suspend fun refreshBillCount() {
        val count = runCatching { transactionRepository.countLocal(_state.value.bookId) }
            .getOrDefault(_state.value.billCount)
        _state.update { it.copy(billCount = count) }
    }

    private fun fileNameOf(uri: Uri): String =
        uri.lastPathSegment?.substringAfterLast('/')?.takeIf { it.isNotBlank() } ?: "所选文件"
}
