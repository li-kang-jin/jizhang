package com.jizhang.app.ui.book

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PersonRemove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jizhang.app.data.local.entity.LocalBook
import com.jizhang.app.data.remote.dto.BookInviteDto
import com.jizhang.app.data.remote.dto.BookMemberDto
import com.jizhang.app.ui.common.AppFullScreenDialog
import com.jizhang.app.ui.common.safeDialogContent

/**
 * 家庭成员管理：成员列表 + 邀请码。
 *
 * 只有账本所有者能邀请 / 移除成员；其他成员可以看「家里都有谁在记账」。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookMemberDialog(
    book: LocalBook,
    onDismiss: () -> Unit,
    viewModel: BookViewModel
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var aliasTarget by remember { mutableStateOf<BookMemberDto?>(null) }
    var aliasInput by remember { mutableStateOf("") }
    var removeTarget by remember { mutableStateOf<BookMemberDto?>(null) }

    AppFullScreenDialog(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.fillMaxSize().safeDialogContent()) {
            TopAppBar(
                title = { Text("成员管理", fontWeight = FontWeight.Bold) },
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
                SectionCard(book.name) {
                    Text(
                        text = if (book.shared) {
                            "家庭账本里的每一笔收支，所有成员都能看到。"
                        } else {
                            "个人账本只有自己可见。"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                SectionCard("家庭成员（${state.members.size}）") {
                    if (state.members.isEmpty()) {
                        Text(
                            text = if (state.busy) "加载中…" else "还没有成员数据",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        state.members.forEachIndexed { index, member ->
                            if (index > 0) {
                                Spacer(Modifier.height(10.dp))
                            }
                            MemberRow(
                                member = member,
                                canManage = state.isOwner && !member.owner,
                                onEditAlias = {
                                    aliasTarget = member
                                    aliasInput = member.alias.orEmpty()
                                },
                                onRemove = { removeTarget = member }
                            )
                        }
                    }
                }

                if (book.shared && state.isOwner) {
                    SectionCard("邀请家人") {
                        Text(
                            text = "把邀请码发给家人，对方在「账本 → 加入家庭账本」里输入即可加入。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(12.dp))
                        Button(
                            onClick = { viewModel.createInvite() },
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Filled.PersonAdd, null, Modifier.size(18.dp))
                            Spacer(Modifier.size(8.dp))
                            Text("生成邀请码（7 天有效，1 次）")
                        }

                        if (state.invites.isNotEmpty()) {
                            Spacer(Modifier.height(14.dp))
                            Text(
                                text = "有效邀请码",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(8.dp))
                            state.invites.forEach { invite ->
                                InviteRow(
                                    invite = invite,
                                    onCopy = { copyCode(context, invite.code) },
                                    onRevoke = { viewModel.revokeInvite(invite.code) }
                                )
                                Spacer(Modifier.height(8.dp))
                            }
                        }
                    }
                } else if (book.shared) {
                    SectionCard("邀请家人") {
                        Text(
                            text = "只有账本所有者可以邀请新成员。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    SectionCard("想和家人一起记账？") {
                        Text(
                            text = "个人账本不能邀请成员。请回到账本列表新建一个「家庭」账本，" +
                                "再把它分享给家人 —— 家庭账本里的收支对所有成员可见。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }

    aliasTarget?.let { member ->
        AlertDialog(
            onDismissRequest = { aliasTarget = null },
            title = { Text("家庭内称呼") },
            text = {
                Column {
                    Text(
                        text = "设置后，家庭账本里会显示成这个称呼（例如「爸爸」）。",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = aliasInput,
                        onValueChange = { aliasInput = it.take(32) },
                        label = { Text("称呼") },
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.updateMember(member.userId, alias = aliasInput)
                    aliasTarget = null
                }) { Text("保存") }
            },
            dismissButton = {
                TextButton(onClick = { aliasTarget = null }) { Text("取消") }
            }
        )
    }

    removeTarget?.let { member ->
        AlertDialog(
            onDismissRequest = { removeTarget = null },
            title = { Text("移除成员") },
            text = { Text("移除「${member.label}」后，该成员将无法再看到本账本的收支。") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.removeMember(member.userId)
                    removeTarget = null
                }) { Text("移除") }
            },
            dismissButton = {
                TextButton(onClick = { removeTarget = null }) { Text("取消") }
            }
        )
    }
}

@Composable
private fun MemberRow(
    member: BookMemberDto,
    canManage: Boolean,
    onEditAlias: () -> Unit,
    onRemove: () -> Unit
) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = member.label,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
                if (member.owner) {
                    Spacer(Modifier.size(6.dp))
                    Text(
                        text = "所有者",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            Text(
                text = roleLabel(member.role),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (canManage) {
            OutlinedButton(onClick = onEditAlias, shape = RoundedCornerShape(12.dp)) {
                Text("称呼", style = MaterialTheme.typography.labelMedium)
            }
            Spacer(Modifier.size(6.dp))
            IconButton(onClick = onRemove) {
                Icon(
                    Icons.Filled.PersonRemove,
                    contentDescription = "移除",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
private fun InviteRow(
    invite: BookInviteDto,
    onCopy: () -> Unit,
    onRevoke: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = invite.code,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "已使用 ${invite.usedCount}/${if (invite.maxUses == 0) "不限" else invite.maxUses} 次",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        IconButton(onClick = onCopy) {
            Icon(Icons.Filled.ContentCopy, contentDescription = "复制")
        }
        TextButton(onClick = onRevoke) { Text("作废") }
    }
}

private fun roleLabel(role: Int): String = when (role) {
    0 -> "只读 · 只能看"
    2 -> "管理员 · 可改所有账目"
    else -> "成员 · 可记账"
}

private fun copyCode(context: Context, code: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
    clipboard.setPrimaryClip(ClipData.newPlainText("家庭账本邀请码", code))
}

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
