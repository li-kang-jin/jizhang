package com.jizhang.app.ui.category

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import com.jizhang.app.data.local.entity.LocalCategory
import com.jizhang.app.data.repository.CategoryRepository
import com.jizhang.app.data.repository.TransactionRepository
import com.jizhang.app.ui.common.AppFullScreenDialog
import com.jizhang.app.ui.common.categoryIconOf
import com.jizhang.app.ui.common.formatAmount
import com.jizhang.app.ui.common.safeDialogContent
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import com.jizhang.app.ui.theme.AppColors
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CategoryViewModel @Inject constructor(
    private val categoryRepository: CategoryRepository,
    private val transactionRepository: TransactionRepository
) : ViewModel() {

    /** 一个分类的用量：管理页显示「12 笔 · ¥2,340」，也是判断该合并哪个的依据 */
    data class Usage(val count: Int, val totalFen: Long)

    data class UiState(
        val bookId: Long = 0L,
        val categories: List<LocalCategory> = emptyList(),
        /** 管理页当前看的一侧：1 支出 2 收入 */
        val type: Int = 1,
        /** 分类 id → 用量，只含当前这一侧 */
        val usage: Map<Long, Usage> = emptyMap(),
        val message: String? = null
    ) {
        val visible: List<LocalCategory> get() = categories.filter { it.type == type }
    }

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
            categoryRepository.observeCategories(bookId).collect { list ->
                _state.update { it.copy(categories = list) }
                // 分类列表一变（新建 / 删除 / 同步回来）就顺手刷新用量：管理页的笔数不能是旧值
                refreshUsage()
            }
        }
    }

    fun switchType(type: Int) {
        _state.update { it.copy(type = type) }
        viewModelScope.launch { refreshUsage() }
    }

    /**
     * 把一个分类合并到另一个：账单整体搬过去，源分类删掉。
     * <p>
     * 这里不自己做「搬家 + 删除」的两步，交给仓库：顺序（先搬后删）与失败时的口径都在那里，
     * 页面只需要把结果讲清楚。
     */
    fun merge(source: LocalCategory, target: LocalCategory) {
        val bookId = _state.value.bookId
        viewModelScope.launch {
            categoryRepository.mergeInto(source.id, target.id, bookId)
                .onSuccess { moved ->
                    _state.update {
                        it.copy(
                            message = "已把 $moved 笔账单改到「${target.name}」，并删除「${source.name}」"
                        )
                    }
                }
                .onFailure { e ->
                    _state.update { it.copy(message = e.message ?: "合并失败") }
                }
        }
    }

    /**
     * 刷当前这一侧的用量。
     * <p>
     * 结果回来时如果用户已经切到另一侧，直接丢弃：否则「支出」的笔数会被写进「收入」页签。
     */
    private suspend fun refreshUsage() {
        val bookId = _state.value.bookId
        val type = _state.value.type
        if (bookId <= 0L) return
        val usage = runCatching { transactionRepository.categoryUsageDetail(bookId, type) }
            .getOrDefault(emptyList())
            .associate { it.categoryId to Usage(count = it.cnt.toInt(), totalFen = it.total) }
        _state.update { if (it.type == type) it.copy(usage = usage) else it }
    }

    fun save(id: Long?, name: String, type: Int, icon: String, done: (String?) -> Unit) {
        val bookId = _state.value.bookId
        viewModelScope.launch {
            val result: Result<Unit> = if (id == null) {
                categoryRepository.create(bookId, name, type, icon).map { }
            } else {
                categoryRepository.update(id, bookId, name, type, icon)
            }
            result
                .onSuccess {
                    _state.update {
                        it.copy(
                            message = if (id == null) "分类已创建" else "分类已更新",
                            // 跟着切到新建 / 编辑的那一侧，让用户马上看到结果
                            type = type
                        )
                    }
                    done(null)
                }
                .onFailure { done(it.message ?: "操作失败") }
        }
    }

    fun delete(id: Long) {
        val bookId = _state.value.bookId
        viewModelScope.launch {
            categoryRepository.delete(id, bookId)
                .onSuccess { _state.update { it.copy(message = "分类已删除") } }
                .onFailure { e ->
                    _state.update { it.copy(message = e.message ?: "删除失败") }
                }
        }
    }

    fun consumeMessage() {
        _state.update { it.copy(message = null) }
    }
}

/**
 * 分类管理。
 * <p>
 * 此前分类只能来自服务端同步，用户想加一个「宠物」「宠物医疗」这种自己的分类没办法 ——
 * 记账时只能硬塞进「其他」。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryListDialog(
    bookId: Long,
    onDismiss: () -> Unit,
    viewModel: CategoryViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<LocalCategory?>(null) }
    var showEditor by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<LocalCategory?>(null) }
    var merging by remember { mutableStateOf<LocalCategory?>(null) }

    LaunchedEffect(bookId) { viewModel.bind(bookId) }

    AppFullScreenDialog(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.fillMaxSize().safeDialogContent()) {
            TopAppBar(
                title = { Text("分类管理", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )

            // 与记账页同一个类型分段：选中直接填充收支色，比两个灰边 FilterChip 干净
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(3.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                ScopeTab("支出", state.type == 1, AppColors.expense, Modifier.weight(1f)) {
                    viewModel.switchType(1)
                }
                ScopeTab("收入", state.type == 2, AppColors.income, Modifier.weight(1f)) {
                    viewModel.switchType(2)
                }
            }

            // weight(1f)：顶栏与分段按钮之外的剩余空间（fillMaxSize 会超出屏幕被裁）
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (state.visible.isEmpty()) {
                    item {
                        Text(
                            text = "这一侧还没有分类，点下面按钮新建一个",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 24.dp)
                        )
                    }
                }

                items(state.visible, key = { it.id }) { category ->
                    CategoryRow(
                        category = category,
                        usage = state.usage[category.id],
                        onEdit = {
                            editing = category
                            showEditor = true
                        },
                        onDelete = { deleting = category },
                        onMerge = { merging = category }
                    )
                }

                item {
                    Spacer(Modifier.height(6.dp))
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
                        Text("新建分类")
                    }
                }
            }
        }
    }

    if (showEditor) {
        CategoryEditorDialog(
            category = editing,
            defaultType = state.type,
            onDismiss = { showEditor = false },
            onSave = { name, type, icon, done ->
                viewModel.save(editing?.id, name, type, icon, done)
            }
        )
    }

    deleting?.let { target ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("删除分类") },
            text = { Text("确定删除「${target.name}」吗？该分类下的账单不会一并删除。") },
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

    merging?.let { source ->
        MergeTargetDialog(
            source = source,
            candidates = mergeCandidates(state.visible, state.usage, source.id),
            usage = state.usage,
            onPick = { target ->
                merging = null
                viewModel.merge(source, target)
            },
            onDismiss = { merging = null }
        )
    }

    state.message?.let {
        AlertDialog(
            onDismissRequest = viewModel::consumeMessage,
            confirmButton = {
                TextButton(onClick = viewModel::consumeMessage) { Text("知道了") }
            },
            title = { Text("提示") },
            text = { Text(it) }
        )
    }
}

/** 支出 / 收入分段里的单个页签 */
@Composable
private fun ScopeTab(
    label: String,
    selected: Boolean,
    selectedColor: Color,
    modifier: Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(if (selected) selectedColor else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(vertical = 7.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}

@Composable
private fun CategoryRow(
    category: LocalCategory,
    usage: CategoryViewModel.Usage?,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onMerge: () -> Unit
) {
    val accent = if (category.type == 2) AppColors.income else AppColors.expense

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(accent.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = categoryIconOf(category.icon, category.name),
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(19.dp)
                )
            }
            Spacer(Modifier.size(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = category.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = usageText(usage),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            // 一笔账单都没有的分类没什么可合并的，直接删就行，别多一个按钮占地方
            if ((usage?.count ?: 0) > 0) {
                TextButton(
                    onClick = onMerge,
                    contentPadding = PaddingValues(horizontal = 8.dp)
                ) {
                    Text("合并", style = MaterialTheme.typography.labelMedium)
                }
            }
            IconButton(onClick = onEdit) {
                Icon(
                    Icons.Filled.Edit,
                    contentDescription = "编辑",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Filled.DeleteOutline,
                    contentDescription = "删除",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/** 「12 笔 · ¥2,340.00」；一笔都没有时说明白，别显示「0 笔 · ¥0.00」 */
private fun usageText(usage: CategoryViewModel.Usage?): String =
    if (usage == null || usage.count == 0) {
        "还没有账单"
    } else {
        "${usage.count} 笔 · ¥${formatAmount(usage.totalFen)}"
    }

/**
 * 合并候选：同一侧的其他分类，用量大的排前面。
 * <p>
 * 用量大的通常是「正经分类」，把它排在前面用户一下就能点到；导入留下的细分项一般笔数少，
 * 自然沉到后面。笔数相同时按金额排——一笔 3000 的房租比三笔 3 元的补差更该当目标。
 * 不做模糊搜索：分类总数一般就十几个，滚动看看比打字快。
 */
internal fun mergeCandidates(
    categories: List<LocalCategory>,
    usage: Map<Long, CategoryViewModel.Usage>,
    excludeId: Long
): List<LocalCategory> = categories
    .filter { it.id != excludeId }
    .sortedWith(
        compareByDescending<LocalCategory> { usage[it.id]?.count ?: 0 }
            .thenByDescending { usage[it.id]?.totalFen ?: 0L }
    )

/**
 * 选合并目标。
 * <p>
 * 一眼要能看出「这一下会动多少笔钱」，所以标题里带上源分类，正文里写清笔数与不可撤销，
 * 每个候选后面也标它的用量——挑目标时看的就是这个。
 */
@Composable
private fun MergeTargetDialog(
    source: LocalCategory,
    candidates: List<LocalCategory>,
    usage: Map<Long, CategoryViewModel.Usage>,
    onPick: (LocalCategory) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("把「${source.name}」合并到") },
        text = {
            Column {
                Text(
                    text = "这 ${usage[source.id]?.count ?: 0} 笔账单会整体改到目标分类，" +
                        "然后删除「${source.name}」。此操作不可撤销。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(10.dp))
                if (candidates.isEmpty()) {
                    Text(
                        text = "同一侧没有别的分类可以合并",
                        style = MaterialTheme.typography.bodyMedium
                    )
                } else {
                    Column(
                        modifier = Modifier
                            .heightIn(max = 320.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        candidates.forEach { target ->
                            MergeTargetRow(
                                target = target,
                                usage = usage[target.id],
                                onClick = { onPick(target) }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

@Composable
private fun MergeTargetRow(
    target: LocalCategory,
    usage: CategoryViewModel.Usage?,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = target.name,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = usageText(usage),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
