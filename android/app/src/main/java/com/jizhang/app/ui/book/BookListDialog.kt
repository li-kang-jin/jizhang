package com.jizhang.app.ui.book

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jizhang.app.data.local.entity.LocalBook
import com.jizhang.app.ui.common.AppChip
import com.jizhang.app.ui.common.AppFullScreenDialog
import com.jizhang.app.ui.common.safeDialogContent

/**
 * 账本列表 / 切换器。
 *
 * 「个人账本」与「家庭账本」在同一张列表里，用户感知上就是「我的账本」和「全家账本」，
 * 不需要切换什么「个人模式 / 家庭模式」。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookListDialog(
    books: List<LocalBook>,
    currentBookId: Long,
    onSwitch: (Long) -> Unit,
    onDismiss: () -> Unit,
    viewModel: BookViewModel
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    var newName by remember { mutableStateOf("") }
    var newType by remember { mutableStateOf(1) }
    var joinCode by remember { mutableStateOf("") }
    var renameTarget by remember { mutableStateOf<LocalBook?>(null) }
    var renameInput by remember { mutableStateOf("") }
    var deleteTarget by remember { mutableStateOf<LocalBook?>(null) }

    AppFullScreenDialog(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.fillMaxSize().safeDialogContent()) {
            TopAppBar(
                title = { Text("账本", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                BookGroup(
                    title = "我的账本",
                    books = books.filter { !it.shared },
                    currentBookId = currentBookId,
                    myUserId = state.myUserId,
                    onSwitch = onSwitch,
                    onOpenMembers = viewModel::openMembers,
                    onRename = { renameTarget = it; renameInput = it.name },
                    onDelete = { deleteTarget = it }
                )

                BookGroup(
                    title = "家庭 / 共享账本",
                    books = books.filter { it.shared },
                    currentBookId = currentBookId,
                    myUserId = state.myUserId,
                    onSwitch = onSwitch,
                    onOpenMembers = viewModel::openMembers,
                    onRename = { renameTarget = it; renameInput = it.name },
                    onDelete = { deleteTarget = it }
                )

                if (books.any { it.shared }) {
                    Text(
                        text = "家庭 / 共享账本里的每一笔收支，所有成员都能看到。私人开销请记在「我的账本」里。",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(Modifier.height(4.dp))

                SectionCard("新建账本") {
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        label = { Text("账本名称") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(10.dp))
                    /*
                     * 三种类型：个人 / 家庭 / 共享。
                     * 「家庭」与「共享」在服务端目前是同一套多人账本逻辑（成员表 + 邀请码 + 角色权限），
                     * 区别只在用户怎么理解它：家里人用家庭，同事 / 朋友 / 项目用共享。
                     */
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        AppChip(
                            label = "个人",
                            selected = newType == 0,
                            onClick = { newType = 0 },
                            leadingIcon = Icons.Outlined.Person
                        )
                        AppChip(
                            label = "家庭",
                            selected = newType == 1,
                            onClick = { newType = 1 },
                            leadingIcon = Icons.Outlined.Group
                        )
                        AppChip(
                            label = "共享",
                            selected = newType == 2,
                            onClick = { newType = 2 },
                            leadingIcon = Icons.Outlined.Share
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = when (newType) {
                            0 -> "个人账本只有自己能看到"
                            1 -> "家庭账本可邀请家人一起记账，全家都能看到"
                            else -> "共享账本给同事 / 朋友 / 项目一起记，成员都能看到"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = {
                            viewModel.createBook(newName, newType) { newName = "" }
                        },
                        enabled = !state.busy && state.loggedIn,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Filled.Add, null, Modifier.size(18.dp))
                        Spacer(Modifier.size(8.dp))
                        Text("创建")
                    }
                    if (!state.loggedIn) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = "登录后才能创建云端账本",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }

                SectionCard("加入家庭 / 共享账本") {
                    Text(
                        text = "让家人或同事把邀请码发给你，输入后即可一起记账。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = joinCode,
                        onValueChange = { joinCode = it.uppercase().take(8) },
                        label = { Text("邀请码") },
                        placeholder = { Text("例如 FAM8K3QA") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = { viewModel.joinByCode(joinCode) { joinCode = "" } },
                        enabled = !state.busy && state.loggedIn,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (state.busy) "处理中…" else "加入")
                    }
                }
            }
        }
    }

    renameTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text("重命名账本") },
            text = {
                OutlinedTextField(
                    value = renameInput,
                    onValueChange = { renameInput = it },
                    label = { Text("账本名称") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.renameBook(target.id, renameInput) { renameTarget = null }
                }) { Text("保存") }
            },
            dismissButton = {
                TextButton(onClick = { renameTarget = null }) { Text("取消") }
            }
        )
    }

    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("删除账本") },
            text = { Text("删除「${target.name}」后，成员将看不到该账本。账本内的账单数据会保留，便于误删恢复。") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteBook(target.id)
                    deleteTarget = null
                }) { Text("删除") }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("取消") }
            }
        )
    }
}

@Composable
private fun BookGroup(
    title: String,
    books: List<LocalBook>,
    currentBookId: Long,
    myUserId: Long,
    onSwitch: (Long) -> Unit,
    onOpenMembers: (LocalBook) -> Unit,
    onRename: (LocalBook) -> Unit,
    onDelete: (LocalBook) -> Unit
) {
    if (books.isEmpty()) {
        return
    }
    SectionCard(title) {
        books.forEachIndexed { index, book ->
            if (index > 0) {
                Spacer(Modifier.height(8.dp))
            }
            BookRow(
                book = book,
                current = book.id == currentBookId,
                mine = book.ownerId == myUserId,
                onSwitch = { onSwitch(book.id) },
                onOpenMembers = { onOpenMembers(book) },
                onRename = { onRename(book) },
                onDelete = { onDelete(book) }
            )
        }
    }
}

@Composable
private fun BookRow(
    book: LocalBook,
    current: Boolean,
    mine: Boolean,
    onSwitch: () -> Unit,
    onOpenMembers: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit
) {
    var menu by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onSwitch)
            .background(
                if (current) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
                } else {
                    MaterialTheme.colorScheme.surface
                }
            )
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (book.shared) Icons.Filled.Group else Icons.AutoMirrored.Filled.MenuBook,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.size(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = book.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (current) FontWeight.Bold else FontWeight.Normal
                )
                if (current) {
                    Spacer(Modifier.size(6.dp))
                    Text(
                        text = "当前",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            Text(
                text = book.subtitle(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (book.remote) {
            IconButton(onClick = { menu = true }) {
                Icon(Icons.Filled.MoreVert, contentDescription = "更多")
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                if (!current) {
                    DropdownMenuItem(
                        text = { Text("切换到这个账本") },
                        onClick = { menu = false; onSwitch() }
                    )
                }
                DropdownMenuItem(
                    text = { Text("成员管理") },
                    onClick = { menu = false; onOpenMembers() }
                )
                if (mine) {
                    DropdownMenuItem(
                        text = { Text("重命名") },
                        onClick = { menu = false; onRename() }
                    )
                    DropdownMenuItem(
                        text = { Text("删除账本") },
                        onClick = { menu = false; onDelete() }
                    )
                }
            }
        }
    }
}

private fun LocalBook.subtitle(): String = when {
    !remote -> "本地账本 · 未登录，仅本机可见"
    !shared -> "个人账本 · 仅自己可见"
    myRole == 0 -> "${bookTypeLabel()} · $memberCount 位成员 · 只读"
    else -> "${bookTypeLabel()} · $memberCount 位成员 · 互相可见"
}

/** 多人账本要按实际类型称呼：type = 2 是共享账本，其余（1）是家庭账本 */
private fun LocalBook.bookTypeLabel(): String = if (type == 2) "共享账本" else "家庭账本"

@Composable
private fun SectionCard(title: String, content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(16.dp)
    ) {
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(10.dp))
        content()
    }
}
