package com.jizhang.app.ui.account

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.jizhang.app.data.local.entity.AccountWithBalance
import com.jizhang.app.data.repository.AccountRepository
import com.jizhang.app.ui.common.ACCOUNT_ICON_OPTIONS
import com.jizhang.app.ui.common.AppFullScreenDialog
import com.jizhang.app.ui.common.accountIconOf
import com.jizhang.app.ui.common.accountTypeLabel
import com.jizhang.app.ui.common.defaultAccountIconOf
import com.jizhang.app.ui.common.formatAmount
import com.jizhang.app.ui.common.safeDialogContent
import com.jizhang.app.ui.theme.AppColors
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** 信用卡：算「负债」而不是「资产」 */
private const val CREDIT_CARD_TYPE = 3

/** 账户行高度：拖拽排序靠固定行高换算格子，别用自适应高度 */
private val ROW_HEIGHT = 68.dp

/** 归档账户不参与新记账，但余额仍然算进净资产 */
@HiltViewModel
class AccountViewModel @Inject constructor(
    private val accountRepository: AccountRepository
) : ViewModel() {

    data class UiState(
        val bookId: Long = 0L,
        /** 全部账户（含归档），顺序按 sort */
        val accounts: List<AccountWithBalance> = emptyList(),
        val message: String? = null
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private var bound = -1L

    fun bind(bookId: Long) {
        if (bound == bookId) {
            return
        }
        bound = bookId
        _state.update { it.copy(bookId = bookId) }
        viewModelScope.launch {
            accountRepository.observeAccounts(bookId).collect { list ->
                _state.update { it.copy(accounts = list) }
            }
        }
    }

    fun save(
        id: Long?,
        name: String,
        type: Int,
        initialBalance: Long,
        icon: String?,
        note: String?,
        archived: Boolean,
        onDone: () -> Unit
    ) {
        val bookId = _state.value.bookId
        viewModelScope.launch {
            val result = if (id == null) {
                accountRepository.create(bookId, name, type, initialBalance, icon, note)
            } else {
                accountRepository.update(id, bookId, name, type, initialBalance, icon, note, archived)
            }
            result.onSuccess {
                _state.update { it.copy(message = if (id == null) "账户已创建" else "账户已更新") }
                onDone()
            }.onFailure { e ->
                _state.update { it.copy(message = e.message ?: "操作失败") }
            }
        }
    }

    /** 归档 / 取消归档：归档后不再出现在记账页与账单筛选里，历史账单与余额照旧 */
    fun setArchived(id: Long, archived: Boolean) {
        viewModelScope.launch {
            accountRepository.setArchived(id, archived)
                .onSuccess {
                    _state.update { it.copy(message = if (archived) "账户已归档" else "账户已恢复") }
                }
                .onFailure { e -> _state.update { it.copy(message = e.message ?: "操作失败") } }
        }
    }

    fun delete(id: Long) {
        viewModelScope.launch {
            accountRepository.delete(id)
                .onSuccess { _state.update { it.copy(message = "账户已删除") } }
                .onFailure { e -> _state.update { it.copy(message = e.message ?: "删除失败") } }
        }
    }

    /** 落库整本账本的账户顺序（拖拽 / 上下移后调用） */
    fun reorder(orderedIds: List<Long>) {
        viewModelScope.launch { accountRepository.reorder(orderedIds) }
    }

    fun consumeMessage() {
        _state.update { it.copy(message = null) }
    }
}

/**
 * 账户管理。
 * <p>
 * 之前 App 里完全没有账户入口，只能依赖服务端同步过来的账户，
 * 一旦同步不到就陷入「无法记账」的死胡同。
 *
 * 现在这一页承担「资产管理」：顶部净资产摘要，下面按 资产 / 信用 / 已归档 分组，
 * 组内可长按拖动排序，点某一行进账户详情（流水 + 余额校准）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountListDialog(
    bookId: Long,
    onDismiss: () -> Unit,
    /** 打开账户详情（详情覆盖层由页面根部统一挂载） */
    onOpenDetail: (AccountWithBalance) -> Unit,
    /** 金额隐私模式：打开后本页所有金额显示成 **** */
    hideAmounts: Boolean = false,
    onToggleHideAmounts: () -> Unit = {},
    viewModel: AccountViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<AccountWithBalance?>(null) }
    var showEditor by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<AccountWithBalance?>(null) }

    LaunchedEffect(bookId) { viewModel.bind(bookId) }

    // 展示顺序（资产 → 信用 → 已归档）：数据库排序变了就重建，拖动时只改本地
    val orderState = remember { mutableStateOf<List<Long>>(emptyList()) }
    val accounts by rememberUpdatedState(state.accounts)
    LaunchedEffect(state.accounts) {
        orderState.value = canonicalOrder(orderState.value, state.accounts)
    }

    fun moveSlot(id: Long, delta: Int, commit: Boolean) {
        val reordered = swapWithNeighbour(orderState.value, accounts, id, delta) ?: return
        orderState.value = reordered
        if (commit) {
            viewModel.reorder(reordered)
        }
    }

    AppFullScreenDialog(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.fillMaxSize().safeDialogContent()) {
            TopAppBar(
                title = { Text("账户管理", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    // 金额隐私：地铁上翻账户余额不至于被旁边看光
                    IconButton(onClick = onToggleHideAmounts) {
                        Icon(
                            imageVector = if (hideAmounts) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                            contentDescription = if (hideAmounts) "显示金额" else "隐藏金额"
                        )
                    }
                }
            )

            val ordered = orderState.value.mapNotNull { id -> accounts.firstOrNull { it.id == id } }
            val assetAccounts = ordered.filter { it.groupRank() == RANK_ASSET }
            val creditAccounts = ordered.filter { it.groupRank() == RANK_CREDIT }
            val archivedAccounts = ordered.filter { it.groupRank() == RANK_ARCHIVED }
            val totalAsset = ordered.filter { it.type != CREDIT_CARD_TYPE }.sumOf { it.balance }
            val totalDebt = ordered.filter { it.type == CREDIT_CARD_TYPE }.sumOf { maxOf(0L, -it.balance) }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
            ) {
                AssetSummaryCard(
                    totalAsset = totalAsset,
                    totalDebt = totalDebt,
                    hideAmounts = hideAmounts
                )
                Spacer(Modifier.height(14.dp))

                if (ordered.isEmpty()) {
                    Text(
                        text = "还没有账户，点下方按钮新建一个",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 24.dp)
                    )
                }

                AccountGroup(
                    title = "资产账户",
                    subtotal = if (hideAmounts) null else "¥" + formatAmount(totalAsset),
                    accounts = assetAccounts,
                    hideAmounts = hideAmounts,
                    emptyHint = null,
                    onOpenDetail = onOpenDetail,
                    onEdit = { editing = it; showEditor = true },
                    onArchive = { viewModel.setArchived(it.id, !it.archived) },
                    onDelete = { deleting = it },
                    onMoveSlot = { id, delta -> moveSlot(id, delta, commit = true) },
                    onDragFinished = { viewModel.reorder(orderState.value) },
                    onDragSlot = { id, delta -> moveSlot(id, delta, commit = false) }
                )

                AccountGroup(
                    title = "信用账户",
                    subtotal = if (hideAmounts) null else "欠款 ¥" + formatAmount(totalDebt),
                    accounts = creditAccounts,
                    hideAmounts = hideAmounts,
                    emptyHint = "把信用卡等需要还的账户建在这里，欠款会算进负债",
                    onOpenDetail = onOpenDetail,
                    onEdit = { editing = it; showEditor = true },
                    onArchive = { viewModel.setArchived(it.id, !it.archived) },
                    onDelete = { deleting = it },
                    onMoveSlot = { id, delta -> moveSlot(id, delta, commit = true) },
                    onDragFinished = { viewModel.reorder(orderState.value) },
                    onDragSlot = { id, delta -> moveSlot(id, delta, commit = false) }
                )

                if (archivedAccounts.isNotEmpty()) {
                    AccountGroup(
                        title = "已归档",
                        subtotal = null,
                        accounts = archivedAccounts,
                        hideAmounts = hideAmounts,
                        emptyHint = null,
                        onOpenDetail = onOpenDetail,
                        onEdit = { editing = it; showEditor = true },
                        onArchive = { viewModel.setArchived(it.id, !it.archived) },
                        onDelete = { deleting = it },
                        onMoveSlot = { id, delta -> moveSlot(id, delta, commit = true) },
                        onDragFinished = { viewModel.reorder(orderState.value) },
                        onDragSlot = { id, delta -> moveSlot(id, delta, commit = false) }
                    )
                }

                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = {
                        editing = null
                        showEditor = true
                    },
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(6.dp))
                    Text("新建账户")
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }

    if (showEditor) {
        AccountEditor(
            account = editing,
            onDismiss = { showEditor = false },
            onSave = { name, type, initial, icon, note, archived ->
                viewModel.save(editing?.id, name, type, initial, icon, note, archived) {
                    showEditor = false
                }
            }
        )
    }

    deleting?.let { target ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("删除账户") },
            text = {
                Text(
                    "确定删除「${target.name}」吗？该账户下的交易不会一并删除。" +
                        "如果只是不用了，建议改成「归档」。"
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.delete(target.id)
                    deleting = null
                }) { Text("删除") }
            },
            dismissButton = {
                TextButton(onClick = { deleting = null }) { Text("取消") }
            }
        )
    }

    state.message?.let {
        AlertDialog(
            onDismissRequest = viewModel::consumeMessage,
            confirmButton = { TextButton(onClick = viewModel::consumeMessage) { Text("知道了") } },
            title = { Text("提示") },
            text = { Text(it) }
        )
    }
}

/* ==================== 摘要 / 分组 / 行 ==================== */

/** 顶部资产摘要：净资产（大）+ 总资产 / 总负债 */
@Composable
private fun AssetSummaryCard(totalAsset: Long, totalDebt: Long, hideAmounts: Boolean) {
    val net = totalAsset - totalDebt
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(16.dp)
    ) {
        Text(
            text = "净资产",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = money(net, hideAmounts),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = if (net < 0) AppColors.expense else MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.height(12.dp))
        Row {
            SummaryItem("总资产", money(totalAsset, hideAmounts), Modifier.weight(1f))
            SummaryItem(
                "总负债",
                money(totalDebt, hideAmounts),
                Modifier.weight(1f),
                valueColor = if (totalDebt > 0) AppColors.expense else MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun SummaryItem(
    label: String,
    value: String,
    modifier: Modifier,
    valueColor: Color = MaterialTheme.colorScheme.onSurface
) {
    Column(modifier = modifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = valueColor
        )
    }
}

/**
 * 一组账户（资产 / 信用 / 已归档）。
 * <p>
 * 组内的行支持长按拖动排序：行高固定，拖动超过半行就与同组的相邻账户换位，
 * 松手后把整本账本的顺序写回数据库。
 */
@Composable
private fun AccountGroup(
    title: String,
    subtotal: String?,
    accounts: List<AccountWithBalance>,
    hideAmounts: Boolean,
    emptyHint: String?,
    onOpenDetail: (AccountWithBalance) -> Unit,
    onEdit: (AccountWithBalance) -> Unit,
    onArchive: (AccountWithBalance) -> Unit,
    onDelete: (AccountWithBalance) -> Unit,
    onMoveSlot: (Long, Int) -> Unit,
    onDragSlot: (Long, Int) -> Unit,
    onDragFinished: () -> Unit
) {
    if (accounts.isEmpty() && emptyHint == null) {
        return
    }

    var draggingId by remember { mutableStateOf<Long?>(null) }
    var dragOffset by remember { mutableStateOf(0f) }
    val rowHeightPx = with(LocalDensity.current) { ROW_HEIGHT.toPx() }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (subtotal != null) {
            Spacer(Modifier.width(8.dp))
            Text(
                text = subtotal,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    if (accounts.isEmpty()) {
        Text(
            text = emptyHint.orEmpty(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 6.dp)
        )
        return
    }

    accounts.forEach { account ->
        // key：拖动换位后让 remember（菜单开关、拖拽状态）跟着账户走，而不是跟着位置走
        key(account.id) {
            val dragging = draggingId == account.id
            AccountRow(
                account = account,
                hideAmounts = hideAmounts,
                dragging = dragging,
                dragOffset = if (dragging) dragOffset else 0f,
                onOpenDetail = { onOpenDetail(account) },
                onEdit = { onEdit(account) },
                onMoveUp = { onMoveSlot(account.id, -1) },
                onMoveDown = { onMoveSlot(account.id, 1) },
                onArchive = { onArchive(account) },
                onDelete = { onDelete(account) },
                dragModifier = Modifier.pointerInput(account.id) {
                    detectDragGesturesAfterLongPress(
                        onDragStart = {
                            draggingId = account.id
                            dragOffset = 0f
                        },
                        onDrag = { change, amount ->
                            change.consume()
                            dragOffset += amount.y
                            // 越过半行就换一格：一次只挪一格，避免甩一下跳好几格
                            if (dragOffset > rowHeightPx / 2) {
                                onDragSlot(account.id, 1)
                                dragOffset -= rowHeightPx
                            } else if (dragOffset < -rowHeightPx / 2) {
                                onDragSlot(account.id, -1)
                                dragOffset += rowHeightPx
                            }
                        },
                        onDragEnd = {
                            draggingId = null
                            dragOffset = 0f
                            onDragFinished()
                        },
                        onDragCancel = {
                            draggingId = null
                            dragOffset = 0f
                        }
                    )
                }
            )
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun AccountRow(
    account: AccountWithBalance,
    hideAmounts: Boolean,
    dragging: Boolean,
    dragOffset: Float,
    onOpenDetail: () -> Unit,
    onEdit: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onArchive: () -> Unit,
    onDelete: () -> Unit,
    dragModifier: Modifier
) {
    var menuOpen by remember { mutableStateOf(false) }
    val negative = account.balance < 0

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(ROW_HEIGHT)
            .zIndex(if (dragging) 1f else 0f)
            .offset { IntOffset(0, dragOffset.roundToInt()) }
            .then(dragModifier)
            .clip(RoundedCornerShape(16.dp))
            .background(
                if (dragging) MaterialTheme.colorScheme.surfaceVariant
                else MaterialTheme.colorScheme.surface
            )
            .clickable(onClick = onOpenDetail)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(
                    if (account.archived) MaterialTheme.colorScheme.surfaceVariant
                    else MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = accountIconOf(account.icon),
                contentDescription = null,
                tint = if (account.archived) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.primary
                },
                modifier = Modifier.size(18.dp)
            )
        }
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = account.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = if (account.archived) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (account.archived) {
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "已归档",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Text(
                text = account.note?.takeIf { it.isNotBlank() }
                    ?: (accountTypeLabel(account.type) + " · 初始 ¥" + formatAmount(account.initialBalance)),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.width(6.dp))
        Text(
            text = money(account.balance, hideAmounts),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = if (negative) AppColors.expense else MaterialTheme.colorScheme.onSurface
        )
        Box {
            IconButton(onClick = { menuOpen = true }) {
                Icon(
                    Icons.Filled.MoreVert,
                    contentDescription = "更多操作",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                AccountMenuItem("编辑", Icons.Filled.Edit) {
                    menuOpen = false
                    onEdit()
                }
                AccountMenuItem("上移", Icons.Filled.KeyboardArrowUp) {
                    menuOpen = false
                    onMoveUp()
                }
                AccountMenuItem("下移", Icons.Filled.KeyboardArrowDown) {
                    menuOpen = false
                    onMoveDown()
                }
                AccountMenuItem(
                    label = if (account.archived) "取消归档" else "归档",
                    icon = if (account.archived) Icons.Filled.Unarchive else Icons.Filled.Archive
                ) {
                    menuOpen = false
                    onArchive()
                }
                AccountMenuItem("删除", Icons.Filled.DeleteOutline) {
                    menuOpen = false
                    onDelete()
                }
            }
        }
    }
}

@Composable
private fun AccountMenuItem(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    DropdownMenuItem(
        text = { Text(label) },
        leadingIcon = { Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp)) },
        onClick = onClick
    )
}

/* ==================== 新建 / 编辑账户 ==================== */

@Composable
private fun AccountEditor(
    account: AccountWithBalance?,
    onDismiss: () -> Unit,
    onSave: (String, Int, Long, String?, String?, Boolean) -> Unit
) {
    var name by remember { mutableStateOf(account?.name.orEmpty()) }
    var type by remember { mutableStateOf(account?.type ?: 1) }
    var initialText by remember {
        mutableStateOf(account?.let { formatAmount(it.initialBalance) } ?: "0.00")
    }
    var icon by remember {
        mutableStateOf(account?.icon ?: defaultAccountIconOf(account?.type ?: 1))
    }
    var note by remember { mutableStateOf(account?.note.orEmpty()) }
    var archived by remember { mutableStateOf(account?.archived ?: false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (account == null) "新建账户" else "编辑账户") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("账户名称") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = initialText,
                    onValueChange = { initialText = it.filter { c -> c.isDigit() || c == '.' || c == '-' } },
                    label = { Text("初始余额（元）") },
                    supportingText = { Text("信用卡填欠款的负数，例如 -1200") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("备注") },
                    placeholder = { Text("卡号后四位、额度提醒…") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(12.dp))
                Text("账户类型", style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.height(6.dp))
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(listOf(1, 2, 3, 4), listOf(5, 6, 7)).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            row.forEach { value ->
                                FilterChip(
                                    selected = type == value,
                                    onClick = { type = value },
                                    label = { Text(accountTypeLabel(value)) }
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))
                Text("图标", style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.height(6.dp))
                ACCOUNT_ICON_OPTIONS.chunked(4).forEach { row ->
                    Row(
                        modifier = Modifier.padding(bottom = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        row.forEach { option ->
                            val selected = icon == option.key
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (selected) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.surfaceVariant
                                    )
                                    .clickable { icon = option.key },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = accountIconOf(option.key),
                                    contentDescription = option.label,
                                    tint = if (selected) {
                                        MaterialTheme.colorScheme.onPrimary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }

                if (account != null) {
                    Spacer(Modifier.height(6.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("归档停用", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                text = "归档后不再出现在记账与账单筛选里，余额和历史照旧",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(checked = archived, onCheckedChange = { archived = it })
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (name.isBlank()) {
                        return@TextButton
                    }
                    // 支持负数：信用卡的初始余额就是「欠了多少钱」
                    val yuan = initialText.toDoubleOrNull() ?: 0.0
                    onSave(
                        name.trim(),
                        type,
                        (yuan * 100).roundToInt().toLong(),
                        icon,
                        note.trim().ifBlank { null },
                        archived
                    )
                }
            ) { Text("保存") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

/* ==================== 排序工具 ==================== */

private const val RANK_ASSET = 1
private const val RANK_CREDIT = 2
private const val RANK_ARCHIVED = 3

/** 分组归属：资产 / 信用 / 已归档 */
private fun AccountWithBalance.groupRank(): Int = when {
    archived -> RANK_ARCHIVED
    type == CREDIT_CARD_TYPE -> RANK_CREDIT
    else -> RANK_ASSET
}

/**
 * 把「上次的展示顺序」和「数据库最新数据」合成一份新顺序。
 * <p>
 * 组顺序固定（资产 → 信用 → 已归档），组内沿用用户拖过的相对顺序；新账户排在所属分组末尾。
 */
private fun canonicalOrder(
    previous: List<Long>,
    accounts: List<AccountWithBalance>
): List<Long> {
    val ids = accounts.map { it.id }
    val kept = previous.filter { it in ids }
    val combined = kept + ids.filter { it !in kept }
    val byId = accounts.associateBy { it.id }
    return combined.sortedBy { byId[it]?.groupRank() ?: RANK_ASSET }
}

/**
 * 与**同组**的相邻账户交换位置。
 * <p>
 * 跨组交换没有意义（组是固定的），所以中间隔着别的分组时直接跳过，
 * 拖到边界就停住。
 *
 * @return 新顺序；已经到边界时返回 null（表示本次拖动不生效）
 */
private fun swapWithNeighbour(
    order: List<Long>,
    accounts: List<AccountWithBalance>,
    id: Long,
    delta: Int
): List<Long>? {
    if (delta == 0) {
        return null
    }
    val byId = accounts.associateBy { it.id }
    val rank = byId[id]?.groupRank() ?: return null
    val from = order.indexOf(id)
    if (from < 0) {
        return null
    }
    var target = from + delta
    while (target in order.indices && byId[order[target]]?.groupRank() != rank) {
        target += delta
    }
    if (target !in order.indices) {
        return null
    }
    val next = order.toMutableList()
    next[from] = order[target]
    next[target] = id
    return next
}

/** 金额文本：隐私模式下用 **** 代替 */
private fun money(fen: Long, hideAmounts: Boolean): String = when {
    hideAmounts -> "¥ ****"
    fen < 0 -> "-¥" + formatAmount(abs(fen))
    else -> "¥" + formatAmount(fen)
}
