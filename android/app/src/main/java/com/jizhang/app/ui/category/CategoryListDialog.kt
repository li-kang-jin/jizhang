package com.jizhang.app.ui.category

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import com.jizhang.app.ui.common.AppFullScreenDialog
import com.jizhang.app.ui.common.categoryIconOf
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
    private val categoryRepository: CategoryRepository
) : ViewModel() {

    data class UiState(
        val bookId: Long = 0L,
        val categories: List<LocalCategory> = emptyList(),
        /** 管理页当前看的一侧：1 支出 2 收入 */
        val type: Int = 1,
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
            }
        }
    }

    fun switchType(type: Int) {
        _state.update { it.copy(type = type) }
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
                        onEdit = {
                            editing = category
                            showEditor = true
                        },
                        onDelete = { deleting = category }
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
    onEdit: () -> Unit,
    onDelete: () -> Unit
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
            Text(
                text = category.name,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
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
