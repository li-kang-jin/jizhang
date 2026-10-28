package com.jizhang.app.ui.mine

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.jizhang.app.BuildConfig
import com.jizhang.app.data.imports.BillCsvWriter
import com.jizhang.app.data.prefs.SettingsStore
import com.jizhang.app.data.prefs.TokenStore
import com.jizhang.app.data.repository.AuthRepository
import com.jizhang.app.data.repository.BudgetCycle
import com.jizhang.app.data.repository.BudgetPeriods
import com.jizhang.app.data.repository.LedgerRepository
import com.jizhang.app.data.repository.PetInsightRepository
import com.jizhang.app.data.repository.PetRepository
import com.jizhang.app.data.repository.ReportGranularity
import com.jizhang.app.data.repository.ReportPeriod
import com.jizhang.app.data.repository.SyncManager
import com.jizhang.app.data.repository.TransactionRepository
import com.jizhang.app.ui.common.DateRangeDialog
import com.jizhang.app.ui.login.LoginDialog
import com.jizhang.app.ui.pet.PetAvatar
import com.jizhang.app.ui.pet.PetInsight
import com.jizhang.app.ui.pet.PetState
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

/** 导出账单的时间范围：决定取数区间与默认文件名 */
enum class ExportRange(val label: String, val hint: String) {
    /** ISO 周（周一起），与预算模块的周口径一致 */
    THIS_WEEK("本周", "本周一 00:00 到现在"),
    THIS_MONTH("本月", "本月 1 号到现在"),
    CUSTOM("自定义区间", "自己选起止日期"),
    ALL("全部账单", "整本账本，不限时间")
}

@HiltViewModel
class MineViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val tokenStore: TokenStore,
    private val transactionRepository: TransactionRepository,
    private val ledgerRepository: LedgerRepository,
    private val petRepository: PetRepository,
    private val petInsightRepository: PetInsightRepository,
    private val syncManager: SyncManager,
    private val authRepository: AuthRepository,
    private val settingsStore: SettingsStore
) : ViewModel() {

    data class UiState(
        val bookId: Long = 0L,
        val loggedIn: Boolean = false,
        val nickname: String = "",
        val pendingCount: Int = 0,
        val localCount: Int = 0,
        val pet: PetState = PetState(),
        /** 宠物读完账的结论：这里只用来决定表情，跟账单页同源 */
        val insight: PetInsight = PetInsight.EMPTY,
        val syncing: Boolean = false,
        val message: String? = null
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private var bound = -1L

    fun bind(bookId: Long, loggedIn: Boolean) {
        _state.update { it.copy(bookId = bookId, loggedIn = loggedIn) }
        if (bound == bookId) {
            return
        }
        bound = bookId
        viewModelScope.launch {
            launch {
                transactionRepository.observePendingCount(bookId).collect { count ->
                    _state.update { it.copy(pendingCount = count) }
                }
            }
            launch {
                petRepository.state.collect { pet -> _state.update { it.copy(pet = pet) } }
            }
            launch {
                petInsightRepository.observe(bookId).collect { insight ->
                    _state.update { it.copy(insight = insight) }
                }
            }
            launch {
                tokenStore.nicknameFlow.collect { name -> _state.update { it.copy(nickname = name) } }
            }
            launch {
                /*
                 * 「账单总数」也是查一次的聚合：记账 / 删除 / 导入 / 同步之后要跟着变。
                 * 只在 bind 里算一次是不够的 —— 切回本页时 bind 会早退，数字就停在旧的。
                 */
                transactionRepository.observeTransactions(bookId).collect { list ->
                    _state.update { it.copy(localCount = list.size) }
                }
            }
        }
    }

    private suspend fun refresh() {
        val bookId = _state.value.bookId
        val count = transactionRepository.listLocal(bookId).size
        _state.update { it.copy(localCount = count) }
    }

    fun refreshLocal() {
        viewModelScope.launch { refresh() }
    }

    /* ---------------- 导出账单 ---------------- */

    /** 待写盘的导出内容与条数：用户选好保存位置后一次性写出去，中间不重新查库 */
    private var pendingExport: ByteArray? = null
    private var pendingExportCount = 0

    /**
     * 生成待导出的 CSV，并把默认文件名交给调用方去启动系统「新建文件」选择器。
     * <p>
     * 先算内容再让用户选位置：这样不会出现「选完位置才发现没有数据」的尴尬，
     * 真正的写盘（[saveExport]）也就只剩一次 IO。
     *
     * @param range 导出范围：本周 / 本月 / 自定义区间 / 全部
     * @param customStart 自定义区间的起始日 0 点（[range] 为 CUSTOM 时必填）
     * @param customEnd 自定义区间的结束日 0 点（闭区间，内部会 +1 天换成左闭右开）
     * @param bookName 账本名，只写进「所属账本」列方便日后认出出处
     */
    fun prepareExport(
        range: ExportRange,
        customStart: Long?,
        customEnd: Long?,
        bookName: String?,
        onReady: (String) -> Unit
    ) {
        val bookId = _state.value.bookId
        if (bookId <= 0L) {
            _state.update { it.copy(message = "账本还没准备好，请稍后再试") }
            return
        }
        val bounds = exportBounds(range, customStart, customEnd)
        viewModelScope.launch {
            val rows = runCatching { buildExportRows(bookId, bounds, bookName) }
                .getOrElse { error ->
                    _state.update { it.copy(message = "导出失败：${error.message ?: "未知错误"}") }
                    return@launch
                }
            if (rows.isEmpty()) {
                _state.update { it.copy(message = "这个范围里没有账单可以导出") }
                return@launch
            }
            pendingExport = BillCsvWriter.writeBytes(rows)
            pendingExportCount = rows.size
            onReady(defaultExportName(range, bounds))
        }
    }

    /**
     * 范围 → 查询区间（左闭右开）。null 表示不限，即整本账本。
     * <p>
     * 「本周」按 ISO 周（周一起），与预算模块的周口径一致；
     * 「本月」复用统计页的 [ReportPeriod]；自定义区间由日期选择器给两个自然日 0 点，
     * 止日要 +1 天，否则当天的账单会被漏掉。
     */
    private fun exportBounds(
        range: ExportRange,
        customStart: Long?,
        customEnd: Long?
    ): Pair<Long, Long>? = when (range) {
        ExportRange.ALL -> null
        ExportRange.THIS_WEEK -> {
            val week = BudgetPeriods.of(BudgetCycle.WEEK)
            BudgetPeriods.startMillis(week) to BudgetPeriods.endMillis(week)
        }
        ExportRange.THIS_MONTH -> {
            val month = ReportPeriod(ReportGranularity.MONTH, LocalDate.now())
            month.startMillis to month.endMillis
        }
        ExportRange.CUSTOM -> {
            val start = customStart
            val end = customEnd
            if (start == null || end == null) {
                // 理论上不会走到：自定义区间一定带着选择器的结果进来
                null
            } else {
                start to startOfNextDay(end)
            }
        }
    }

    /** 用户在系统选择器里定好位置后写文件（SAF 给的 Uri 直接往 contentResolver 里写） */
    fun saveExport(uri: Uri) {
        val bytes = pendingExport
        if (bytes == null) {
            _state.update { it.copy(message = "没有待导出的内容，请重新导出") }
            return
        }
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
                        ?: error("无法写入所选位置")
                }
            }.onSuccess {
                pendingExport = null
                _state.update { it.copy(message = "已导出 $pendingExportCount 笔账单") }
            }.onFailure { error ->
                _state.update { it.copy(message = "导出失败：${error.message ?: "未知错误"}") }
            }
        }
    }

    /**
     * 组装导出行：账户 / 分类 id 翻成名字。
     * <p>
     * 分类还原本地的两级：挂父分类下的，一级列写父名、二级列写自己，
     * 与 [com.jizhang.app.data.imports.BillCsvParser] 的字段一一对应。
     */
    private suspend fun buildExportRows(
        bookId: Long,
        bounds: Pair<Long, Long>?,
        bookName: String?
    ): List<BillCsvWriter.Row> {
        // 区间过滤推到 SQL：原来是全量取回再内存筛，账单多的时候既慢又占内存
        val transactions = if (bounds == null) {
            transactionRepository.listLocal(bookId)
        } else {
            transactionRepository.listBetween(bookId, bounds.first, bounds.second)
        }
        if (transactions.isEmpty()) {
            return emptyList()
        }

        // 账户要含归档：老账单可能记在后来归档的账户上，否则「收支账户」列会空着
        val accounts = ledgerRepository.cachedAccounts(bookId, includeArchived = true)
            .associateBy { it.id }
        val categories = ledgerRepository.allCategories(bookId).associateBy { it.id }

        return transactions.map { item ->
            val category = item.categoryId?.let { categories[it] }
            val parent = category?.parentId?.takeIf { it != 0L }?.let { categories[it] }
            BillCsvWriter.Row(
                tradeTime = item.tradeTime,
                type = item.type,
                amount = item.amount,
                // 转账本来就没有业务分类，写个占位词让表格读起来不空着
                category = parent?.name ?: category?.name ?: if (item.type == TRANSFER_TYPE) "转账" else null,
                subCategory = if (parent != null) category?.name else null,
                bookName = bookName,
                account = accounts[item.accountId]?.name,
                remark = item.remark,
                tags = item.tags,
                toAccount = item.toAccountId?.let { accounts[it]?.name }
            )
        }
    }

    /** 默认文件名：带上范围与日期，重复导出不会互相覆盖 */
    private fun defaultExportName(range: ExportRange, bounds: Pair<Long, Long>?): String {
        val today = LocalDate.now()
        val span = bounds?.let { (start, endExclusive) ->
            val startDate = Instant.ofEpochMilli(start).atZone(ZoneId.systemDefault()).toLocalDate()
            val endDate = Instant.ofEpochMilli(endExclusive)
                .atZone(ZoneId.systemDefault())
                .toLocalDate()
                .minusDays(1)
            if (startDate == endDate) "$startDate" else "${startDate}_$endDate"
        }
        return when (range) {
            ExportRange.ALL -> "记账账单_全部_$today.csv"
            // 按月导出沿用「年-月」，与统计页的月份口径一致
            ExportRange.THIS_MONTH ->
                "记账账单_${today.year}-${"%02d".format(today.monthValue)}.csv"
            ExportRange.THIS_WEEK -> "记账账单_本周_${span ?: today}.csv"
            ExportRange.CUSTOM -> "记账账单_${span ?: today}.csv"
        }
    }

    /** 某天 0 点 → 次日 0 点：把日期选择器的闭区间换成查询用的右开边界 */
    private fun startOfNextDay(millis: Long): Long =
        Instant.ofEpochMilli(millis)
            .atZone(ZoneId.systemDefault())
            .toLocalDate()
            .plusDays(1)
            .atStartOfDay(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()

    /** 同步全部账本（个人账本 + 所有家庭账本） */
    fun sync() {
        if (!_state.value.loggedIn) {
            _state.update { it.copy(message = "登录后即可同步到云端") }
            return
        }
        viewModelScope.launch {
            if (settingsStore.offlineMode()) {
                _state.update { it.copy(message = "离线模式已开启，已跳过同步") }
                return@launch
            }
            _state.update { it.copy(syncing = true) }
            syncManager.syncAll()
                .onSuccess { summary ->
                    refresh()
                    val failed = if (summary.failedBooks.isEmpty()) {
                        ""
                    } else {
                        "；失败：${summary.failedBooks.joinToString("、")}"
                    }
                    // 冲突 = 服务端版本更新，已按服务端覆盖本地；不提示会让人以为同步没生效
                    val conflict = if (summary.conflicts > 0) {
                        "，${summary.conflicts} 条冲突已采用服务端版本"
                    } else {
                        ""
                    }
                    _state.update {
                        it.copy(
                            syncing = false,
                            message = "同步完成：上传 ${summary.pushed} 条，" +
                                "删除 ${summary.deleted} 条，拉取 ${summary.pulled} 条" +
                                "$conflict$failed"
                        )
                    }
                }
                .onFailure { e ->
                    _state.update { it.copy(syncing = false, message = "同步失败：${e.message}") }
                }
        }
    }

    fun logout() {
        viewModelScope.launch {
            authRepository.logout()
            _state.update { it.copy(loggedIn = false) }
        }
    }

    fun consumeMessage() {
        _state.update { it.copy(message = null) }
    }

    private companion object {
        /** 转账：没有业务分类，导出时「类别」列写个占位词 */
        const val TRANSFER_TYPE = 3
    }
}

@Composable
fun MineScreen(
    bookId: Long,
    loggedIn: Boolean,
    offlineMode: Boolean,
    currentBookName: String?,
    currentBookShared: Boolean,
    onOpenSettings: () -> Unit,
    onOpenImport: () -> Unit,
    onOpenBooks: () -> Unit,
    /** 「财务自由测算」入口：全屏页挂在 MainScreen 根部 */
    onOpenWealth: () -> Unit = {},
    /** 「投资持仓」入口：同样挂在 MainScreen 根部 */
    onOpenInvest: () -> Unit = {},
    importDone: Int,
    onLoginChanged: (String?) -> Unit,
    onLoggedOut: () -> Unit,
    viewModel: MineViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showLogin by remember { mutableStateOf(false) }
    var confirmLogout by remember { mutableStateOf(false) }
    // 导出范围对话框 / 自定义区间选择器，两个开关分开：先选范围，再按需收日期
    var showExport by remember { mutableStateOf(false) }
    var showExportRange by remember { mutableStateOf(false) }

    /*
     * 导出走系统「新建文件」选择器：不用申请存储权限，用户自己决定存到哪
     * （下载目录 / 微信 / 网盘都行）。取消时不提示，免得像出错了。
     */
    val exportLauncher = rememberLauncherForActivityResult(
        // 指定 MIME：系统据此把扩展名固定成 .csv，避免存成没有后缀的文件
        contract = ActivityResultContracts.CreateDocument(EXPORT_MIME)
    ) { uri ->
        if (uri != null) viewModel.saveExport(uri)
    }

    // 先让 ViewModel 备好内容（顺带告知有没有数据），拿到默认文件名再弹出选择器
    fun startExport(range: ExportRange, customStart: Long? = null, customEnd: Long? = null) {
        viewModel.prepareExport(range, customStart, customEnd, currentBookName) { fileName ->
            exportLauncher.launch(fileName)
        }
    }

    LaunchedEffect(bookId, loggedIn) { viewModel.bind(bookId, loggedIn) }

    // 导入页由 MainScreen 以全屏覆盖层展示（弹窗窗口在本工程不可用），
    // 导入完成后用它通知这里刷新本地统计
    LaunchedEffect(importDone) { if (importDone > 0) viewModel.refreshLocal() }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            SectionBox {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PetAvatar(
                        type = state.pet.type,
                        // 跟账单页同一份结论：超支时这里也是担心的脸，不会这边笑那边皱眉
                        mood = state.insight.mood,
                        modifier = Modifier.size(72.dp)
                    )
                    Spacer(Modifier.size(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = state.pet.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = "已连续记账 ${state.pet.streakDays} 天",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .clickable(onClick = onOpenSettings)
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Filled.Palette,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.size(4.dp))
                        Text("个性化", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }

        item {
            SectionBox {
                Text("账本", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(10.dp))
                InfoRow("当前账本", currentBookName ?: "本地账本")
                Spacer(Modifier.height(8.dp))
                InfoRow(
                    "账本类型",
                    if (currentBookShared) "家庭账本 · 家人互相可见" else "个人账本 · 仅自己可见"
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "家庭账本全体成员都能看到彼此的收支；私人开销请记在个人账本里。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = onOpenBooks,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.AutoMirrored.Filled.MenuBook, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(8.dp))
                    Text("账本与成员管理")
                }
            }
        }

        item {
            SectionBox {
                Text("财富规划", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "用账本里的真实支出推算 FI 资产与达标概率：不是设一个目标数字，" +
                        "而是看「按现在的节奏还差多远」。投资持仓则回答「钱押在哪些品种上、现在值多少」。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))
                OutlinedButton(
                    onClick = onOpenWealth,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("财务自由测算")
                }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = onOpenInvest,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("投资持仓")
                }
            }
        }

        item {
            SectionBox {
                Text("云同步", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(10.dp))

                InfoRow(
                    "当前状态",
                    when {
                        offlineMode && state.loggedIn -> "离线模式 · 已登录（${state.nickname}）"
                        offlineMode -> "离线模式（仅本地）"
                        state.loggedIn -> "已登录 · ${state.nickname}"
                        else -> "本地模式（未登录）"
                    }
                )
                Spacer(Modifier.height(8.dp))
                InfoRow("账单总数", "${state.localCount} 笔")
                Spacer(Modifier.height(8.dp))
                InfoRow("待同步", "${state.pendingCount} 笔")

                Spacer(Modifier.height(14.dp))
                if (state.loggedIn) {
                    Button(
                        onClick = viewModel::sync,
                        enabled = !state.syncing && !offlineMode,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Filled.CloudSync, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.size(8.dp))
                        Text(
                            when {
                                offlineMode -> "离线模式中"
                                state.syncing -> "同步中…"
                                else -> "立即同步"
                            }
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = { confirmLogout = true },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("退出登录")
                    }
                } else {
                    Text(
                        text = "不登录也能正常记账；登录后可以把账单同步到云端，多台设备共享。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(12.dp))
                    if (offlineMode) {
                        // 离线模式不联网，登录按钮留着只会点了报错，直接给出说明
                        Text(
                            text = "离线模式下不访问网络，关闭「个性化 → 网络 → 离线模式」后即可登录。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Button(
                            onClick = { showLogin = true },
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.Login,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.size(8.dp))
                            Text("登录并开启云同步")
                        }
                    }
                }
            }
        }

        item {
            SectionBox {
                Text("数据管理", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "从其他记账 App 导出的 CSV 账单可以直接导入本账本，账户与分类会自动匹配或创建；" +
                        "本账本的账单也能导出成同一种 CSV，用 Excel 打开或再导回来。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = onOpenImport,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Filled.UploadFile, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(8.dp))
                    Text("导入账单")
                }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { showExport = true },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(8.dp))
                    Text("导出账单")
                }
            }
        }

        item {
            SectionBox {
                Text("关于", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(10.dp))
                InfoRow("后端地址", BuildConfig.API_BASE_URL)
                Spacer(Modifier.height(8.dp))
                InfoRow("数据存储", "本地优先，登录后同步至云端")
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.Visibility,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.size(6.dp))
                    Text(
                        text = "所有金额均以「分」为单位存储，不会有浮点误差",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }

    // 导出范围：四个选项直接铺开（本周 / 本月 / 自定义 / 全部），选完才唤起系统文件选择器
    if (showExport) {
        AlertDialog(
            onDismissRequest = { showExport = false },
            title = { Text("导出账单") },
            text = {
                Column {
                    Text("导出为 CSV 文件，可用 Excel / WPS 打开，也能再导回本 App。选择导出范围：")
                    Spacer(Modifier.height(6.dp))
                    ExportRange.entries.forEach { range ->
                        ExportRangeOption(
                            range = range,
                            onClick = {
                                showExport = false
                                if (range == ExportRange.CUSTOM) {
                                    // 自定义区间先收日期，拿到起止再算内容
                                    showExportRange = true
                                } else {
                                    startExport(range)
                                }
                            }
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showExport = false }) { Text("取消") }
            }
        )
    }

    if (showExportRange) {
        DateRangeDialog(
            onConfirm = { start, end ->
                showExportRange = false
                startExport(ExportRange.CUSTOM, start, end)
            },
            onDismiss = { showExportRange = false }
        )
    }

    if (showLogin) {
        LoginDialog(
            onDismiss = { showLogin = false },
            onLoggedIn = { summary ->
                showLogin = false
                val tip = if (summary.hasData) {
                    "已把本地 ${summary.transactions} 笔账单迁移到云端（新建账户 ${summary.createdAccounts} 个）"
                } else {
                    null
                }
                onLoginChanged(tip)
                viewModel.refreshLocal()
            }
        )
    }

    if (confirmLogout) {
        AlertDialog(
            onDismissRequest = { confirmLogout = false },
            title = { Text("退出登录") },
            text = { Text("退出后回到本地模式，已有的云端账单会保留在本地，重新登录即可继续同步。") },
            confirmButton = {
                TextButton(onClick = {
                    confirmLogout = false
                    viewModel.logout()
                    onLoggedOut()
                }) { Text("退出") }
            },
            dismissButton = {
                TextButton(onClick = { confirmLogout = false }) { Text("取消") }
            }
        )
    }

    state.message?.let { message ->
        AlertDialog(
            onDismissRequest = viewModel::consumeMessage,
            confirmButton = { TextButton(onClick = viewModel::consumeMessage) { Text("知道了") } },
            title = { Text("提示") },
            text = { Text(message) }
        )
    }
}

@Composable
private fun SectionBox(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(16.dp)
    ) {
        content()
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

/** 导出范围对话框里的一行：标题 + 一句说明，整行可点 */
@Composable
private fun ExportRangeOption(range: ExportRange, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 9.dp)
    ) {
        Text(range.label, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(2.dp))
        Text(
            text = range.hint,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** 导出文件的 MIME：交给系统选择器后，扩展名会被固定成 .csv */
private const val EXPORT_MIME = "text/csv"
