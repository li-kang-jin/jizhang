package com.jizhang.app.ui.importer

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jizhang.app.data.imports.CsvParseResult
import com.jizhang.app.data.imports.ParsedBill
import com.jizhang.app.ui.common.AppFullScreenDialog
import com.jizhang.app.ui.common.formatDate
import com.jizhang.app.ui.common.safeDialogContent
import com.jizhang.app.ui.common.formatSignedAmount
import com.jizhang.app.ui.common.transactionTypeLabel
import com.jizhang.app.ui.theme.AppColors

/**
 * 账单导入弹层：选择 CSV → 预览解析结果 → 确认导入 → 结果汇报。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportDialog(
    bookId: Long,
    onDismiss: () -> Unit,
    onImported: () -> Unit,
    /** 清空账单后也会回调，用于刷新「我的」页统计 */
    onBillsCleared: () -> Unit = {},
    viewModel: ImportViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) viewModel.selectFile(uri)
    }

    LaunchedEffect(bookId) { viewModel.bind(bookId) }

    val parsed = state.parsed
    val summary = state.summary
    val cleared = state.cleared
    val busy = state.parsing || state.importing || state.clearing
    val showActions = parsed != null && summary == null && !busy
    var confirmClear by remember { mutableStateOf(false) }

    LaunchedEffect(cleared) { if (cleared != null) onBillsCleared() }

    AppFullScreenDialog(onDismissRequest = onDismiss) {
        // safeDialogContent：底部让出系统手势区，否则「确认导入」按钮会被压住
        Column(modifier = Modifier.fillMaxSize().safeDialogContent()) {
            TopAppBar(
                title = { Text("导入账单", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = {
                        viewModel.reset()
                        onDismiss()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp)
            ) {
                when {
                    summary != null -> ResultCard(summary.imported, summary.duplicates, summary.invalid,
                        summary.createdAccounts, summary.createdCategories) {
                        viewModel.reset()
                        onImported()
                        onDismiss()
                    }

                    cleared != null -> ClearedCard(
                        count = cleared.count,
                        synced = cleared.synced,
                        onPick = { launcher.launch(OPEN_DOCUMENT_MIME) },
                        onDone = viewModel::consumeCleared
                    )

                    busy -> LoadingCard(
                        when {
                            state.parsing -> "正在解析文件…"
                            state.importing -> "正在导入…"
                            else -> "正在清空账单…"
                        }
                    )

                    parsed != null -> PreviewContent(fileName = state.fileName, parsed = parsed)

                    else -> GuideCard(
                        fileName = state.fileName,
                        onPick = { launcher.launch(OPEN_DOCUMENT_MIME) }
                    )
                }

                // 导入的源数据有问题时的兜底：先清空本账本账单，再导入修正后的文件
                if (state.billCount > 0 && !busy) {
                    Spacer(Modifier.height(14.dp))
                    ClearCard(count = state.billCount, onClear = { confirmClear = true })
                }

                state.error?.let { message ->
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = message,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            // 核心操作固定在底部，不随列表滚动，小屏也永远可点
            if (showActions) {
                Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 6.dp)) {
                    Button(
                        onClick = viewModel::confirmImport,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                    ) {
                        Text("确认导入 ${parsed!!.bills.size} 条", fontWeight = FontWeight.SemiBold)
                    }
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = { launcher.launch(OPEN_DOCUMENT_MIME) },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("重新选择文件")
                    }
                }
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("清空本账本账单") },
            text = {
                Text(
                    "将删除当前账本的 ${state.billCount} 条账单；已同步到云端的会一并删除，" +
                        "账户余额会自动回滚。此操作不可撤销。"
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmClear = false
                    viewModel.clearBills()
                }) {
                    Text("确认清空", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) { Text("取消") }
            }
        )
    }
}

/** 清空本账本账单：导入源数据有问题时的兜底入口 */
@Composable
private fun ClearCard(count: Int, onClear: () -> Unit) {
    SectionCard("重新导入前清空旧数据") {
        Text(
            text = "当前账本已有 $count 条账单。若旧数据的时间、金额不对，可以先清空再导入修正后的文件；" +
                "已同步到云端的账单会一并删除，账户余额自动回滚。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(12.dp))
        OutlinedButton(
            onClick = onClear,
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = MaterialTheme.colorScheme.error
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Filled.DeleteSweep, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.size(8.dp))
            Text("清空本账本账单（$count 条）")
        }
    }
}

/** 清空完成后的汇报 */
@Composable
private fun ClearedCard(count: Int, synced: Boolean, onPick: () -> Unit, onDone: () -> Unit) {
    SectionCard("已清空") {
        InfoRow("删除账单", "$count 条")
        Spacer(Modifier.height(10.dp))
        Text(
            text = if (synced) {
                "本地与云端的账单都已删除，账户余额已回滚。现在可以导入修正后的 CSV 了。"
            } else {
                "本地已清空；云端删除暂未同步成功（可能网络不通），下次同步会自动补推。"
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = onPick,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Filled.UploadFile, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.size(8.dp))
            Text("选择 CSV 文件")
        }
        Spacer(Modifier.height(4.dp))
        TextButton(onClick = onDone, modifier = Modifier.fillMaxWidth()) { Text("知道了") }
    }
}

@Composable
private fun GuideCard(fileName: String?, onPick: () -> Unit) {
    SectionCard("支持的文件格式") {
        Text(
            text = "第三方记账 App 导出的 CSV 账单，表头包含：\n" +
                "日期、收支类型、金额、优惠、类别、二级分类、所属账本、收支账户、备注、标签、地址、转入账户",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = "· 列顺序可不同，按表头名称自动识别\n" +
                "· 仅支持 CSV 文本（Excel 请另存为「CSV UTF-8（逗号分隔）」）\n" +
                "· 编码支持 UTF-8 与 GBK\n" +
                "· 日期列可带时间（如 2026/10/15 16:20），不带时间按当天 12:00 计\n" +
                "· 账户、分类不存在时会自动新建\n" +
                "· 重复导入同一份文件不会产生重复账单",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (fileName != null) {
            Spacer(Modifier.height(12.dp))
            InfoRow("已选文件", fileName)
        }
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = onPick,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Filled.UploadFile, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.size(8.dp))
            Text("选择 CSV 文件")
        }
    }
}

@Composable
private fun LoadingCard(text: String) {
    SectionCard(text) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
            Spacer(Modifier.size(12.dp))
            Text("请稍候", style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun PreviewContent(
    fileName: String?,
    parsed: CsvParseResult
) {
    SectionCard("解析结果") {
        fileName?.let {
            InfoRow("文件", it)
            Spacer(Modifier.height(8.dp))
        }
        InfoRow("识别编码", parsed.encoding)
        Spacer(Modifier.height(8.dp))
        InfoRow("可导入账单", "${parsed.bills.size} 条")
        if (parsed.skipped.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            InfoRow("跳过的行", "${parsed.skipped.size} 条")
        }

        if (parsed.skipped.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            parsed.skipped.take(SKIPPED_PREVIEW).forEach { skipped ->
                Text(
                    text = "第 ${skipped.line} 行：${skipped.reason}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }

    Spacer(Modifier.height(14.dp))

    val preview = parsed.bills.take(PREVIEW_LIMIT)
    SectionCard("预览（前 ${preview.size} / ${parsed.bills.size} 条）") {
        preview.forEachIndexed { index, bill ->
            if (index > 0) {
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant
                )
            }
            BillRow(bill)
        }
    }
}

@Composable
private fun ResultCard(
    imported: Int,
    duplicates: Int,
    invalid: Int,
    createdAccounts: Int,
    createdCategories: Int,
    onDone: () -> Unit
) {
    SectionCard("导入完成") {
        InfoRow("成功导入", "$imported 条")
        Spacer(Modifier.height(8.dp))
        InfoRow("重复跳过", "$duplicates 条")
        if (invalid > 0) {
            Spacer(Modifier.height(8.dp))
            InfoRow("无法导入", "$invalid 条")
        }
        Spacer(Modifier.height(8.dp))
        InfoRow("新建账户", "$createdAccounts 个")
        Spacer(Modifier.height(8.dp))
        InfoRow("新建分类", "$createdCategories 个")

        Spacer(Modifier.height(14.dp))
        Text(
            text = "账单已存入本地账本；登录后会在下次同步时推送到云端。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = onDone,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("完成")
        }
    }
}

@Composable
private fun BillRow(bill: ParsedBill) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Column(modifier = Modifier.weight(1f)) {
            val title = bill.remark
                ?: bill.categoryName
                ?: transactionTypeLabel(bill.type)
            Text(title, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(2.dp))
            Text(
                text = buildString {
                    append(formatDate(bill.tradeTime))
                    bill.accountName?.let { append(" · $it") }
                    bill.toAccountName?.let { append(" → $it") }
                    bill.categoryName?.takeIf { it != title }?.let { append(" · $it") }
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (bill.tags.isNotEmpty()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = bill.tags.joinToString(" ") { "#$it" },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
        Spacer(Modifier.size(12.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = formatSignedAmount(bill.type, bill.amount),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = when (bill.type) {
                    1 -> AppColors.expense
                    2 -> AppColors.income
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = transactionTypeLabel(bill.type),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
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
        Spacer(Modifier.height(12.dp))
        content()
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

/** OpenDocument 的 MIME 过滤：第三方导出常见 text/csv 与 xlsx 伪装，放开为全部由用户选择 */
private val OPEN_DOCUMENT_MIME = arrayOf(
    "text/csv",
    "text/comma-separated-values",
    "text/plain",
    "application/vnd.ms-excel",
    "*/*"
)

private const val PREVIEW_LIMIT = 20
private const val SKIPPED_PREVIEW = 3
