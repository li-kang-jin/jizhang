package com.jizhang.app.ui.record

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jizhang.app.ui.common.accountTypeLabel
import com.jizhang.app.ui.theme.ExpenseColor
import com.jizhang.app.ui.theme.IncomeColor

/**
 * 「记一笔」全屏对话框。写入本地后立即返回，联网时由同步任务推送。
 */
@Composable
fun RecordDialog(
    bookId: Long,
    onDismiss: () -> Unit,
    onSaved: () -> Unit
) {
    val viewModel: RecordViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    var errorMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(bookId) {
        viewModel.reset()
        viewModel.load(bookId)
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize()) {
                // 顶部栏
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Filled.Close, contentDescription = "关闭")
                    }
                    Spacer(Modifier.weight(1f))
                    Text("记一笔", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.weight(1f))
                    Spacer(Modifier.size(48.dp))
                }

                TabRow(selectedTabIndex = if (state.type == 1) 0 else 1) {
                    Tab(
                        selected = state.type == 1,
                        onClick = { viewModel.switchType(bookId, 1) },
                        text = { Text("支出", color = if (state.type == 1) ExpenseColor else MaterialTheme.colorScheme.onSurface) }
                    )
                    Tab(
                        selected = state.type == 2,
                        onClick = { viewModel.switchType(bookId, 2) },
                        text = { Text("收入", color = if (state.type == 2) IncomeColor else MaterialTheme.colorScheme.onSurface) }
                    )
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(16.dp)
                ) {
                    OutlinedTextField(
                        value = state.amountText,
                        onValueChange = viewModel::updateAmount,
                        label = { Text("金额（元）") },
                        prefix = { Text("¥") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(Modifier.height(16.dp))
                    Text("分类", style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.height(8.dp))

                    if (state.categories.isEmpty()) {
                        Text(
                            text = "暂无分类数据，请先同步",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    } else {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(state.categories, key = { it.id }) { category ->
                                FilterChip(
                                    selected = state.selectedCategoryId == category.id,
                                    onClick = { viewModel.selectCategory(category.id) },
                                    label = { Text(category.name) }
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(16.dp))
                    Text("账户", style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.height(8.dp))

                    if (state.accounts.isEmpty()) {
                        Text(
                            text = "暂无账户数据，请先同步",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    } else {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(state.accounts, key = { it.id }) { account ->
                                FilterChip(
                                    selected = state.selectedAccountId == account.id,
                                    onClick = { viewModel.selectAccount(account.id) },
                                    label = { Text("${account.name}（${accountTypeLabel(account.type)}）") }
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(16.dp))
                    OutlinedTextField(
                        value = state.remark,
                        onValueChange = viewModel::updateRemark,
                        label = { Text("备注") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    errorMessage?.let {
                        Spacer(Modifier.height(12.dp))
                        Text(it, color = MaterialTheme.colorScheme.error)
                    }
                }

                Card(modifier = Modifier.fillMaxWidth()) {
                    Button(
                        onClick = {
                            viewModel.save(bookId) { error ->
                                if (error == null) {
                                    onSaved()
                                } else {
                                    errorMessage = error
                                }
                            }
                        },
                        enabled = !state.saving,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Text(
                            text = if (state.type == 1) "保存支出" else "保存收入",
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}
