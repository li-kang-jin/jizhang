package com.jizhang.app.ui.main

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jizhang.app.data.local.entity.LocalTransaction
import com.jizhang.app.ui.AppViewModel
import com.jizhang.app.ui.account.AccountListDialog
import com.jizhang.app.ui.bill.BillScreen
import com.jizhang.app.ui.book.BookListDialog
import com.jizhang.app.ui.book.BookMemberDialog
import com.jizhang.app.ui.book.BookViewModel
import com.jizhang.app.ui.budget.BudgetScreen
import com.jizhang.app.ui.budget.BudgetViewModel
import com.jizhang.app.ui.budget.RecurringExpenseDialog
import com.jizhang.app.ui.category.CategoryListDialog
import com.jizhang.app.ui.importer.ImportDialog
import com.jizhang.app.ui.mine.MineScreen
import com.jizhang.app.ui.record.RecordScreen
import com.jizhang.app.ui.report.ReportScreen
import com.jizhang.app.ui.settings.SettingsDialog

/**
 * 主界面。
 * <p>
 * 「记一笔」的悬浮按钮由这里统一提供 —— 之前它放在账单页内部的第二层 Scaffold 里，
 * 边缘到边缘模式下拿不到系统导航栏高度，导致按钮被压在屏幕底部。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(appViewModel: AppViewModel) {
    val state by appViewModel.state.collectAsStateWithLifecycle()

    var tab by rememberSaveable { mutableIntStateOf(0) }
    var showRecord by remember { mutableStateOf(false) }
    var showAccounts by remember { mutableStateOf(false) }
    var showCategories by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var showImport by remember { mutableStateOf(false) }
    var showBooks by remember { mutableStateOf(false) }
    var showRecurring by remember { mutableStateOf(false) }
    // 非空表示记账页以「编辑这笔账单」的方式打开（账单页点条目进来）
    var editingTransaction by remember { mutableStateOf<LocalTransaction?>(null) }
    // 导入完成后 +1，用来通知「我的」页刷新本地统计
    var importDone by remember { mutableIntStateOf(0) }

    // 账本管理只在 MainScreen 根部持有，列表 / 成员两个覆盖层共用同一份状态
    val bookViewModel: BookViewModel = hiltViewModel()
    val bookState by bookViewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.loggedIn) { bookViewModel.setLoggedIn(state.loggedIn) }

    // 预算 ViewModel 提到根部：固定支出覆盖层要挂在 Scaffold 之后才盖得住底部导航栏，
    // 而它需要预算页的数据（固定支出列表、本期预留、支出分类），
    // hiltViewModel() 在同一个 ViewModelStoreOwner 下拿到的是同一实例，与预算页共享状态
    val budgetViewModel: BudgetViewModel = hiltViewModel()
    val budgetState by budgetViewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            // 全局账本切换器：账单 / 统计 / 预算都跟随「当前账本」
            CenterAlignedTopAppBar(
                title = {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .clickable { showBooks = true }
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (state.currentBook?.shared == true) {
                                Icons.Filled.Group
                            } else {
                                Icons.AutoMirrored.Filled.MenuBook
                            },
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.size(6.dp))
                        Text(
                            text = state.currentBook?.name ?: "账本",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Icon(
                            Icons.Filled.ExpandMore,
                            contentDescription = "切换账本",
                            modifier = Modifier.size(20.dp)
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                NavigationBarItem(
                    selected = tab == 0,
                    onClick = { tab = 0 },
                    icon = { Icon(Icons.AutoMirrored.Filled.ReceiptLong, contentDescription = "账单") },
                    label = { Text("账单") }
                )
                NavigationBarItem(
                    selected = tab == 1,
                    onClick = { tab = 1 },
                    icon = { Icon(Icons.Filled.PieChart, contentDescription = "统计") },
                    label = { Text("统计") }
                )
                NavigationBarItem(
                    selected = tab == 2,
                    onClick = { tab = 2 },
                    icon = { Icon(Icons.Filled.Savings, contentDescription = "预算") },
                    label = { Text("预算") }
                )
                NavigationBarItem(
                    selected = tab == 3,
                    onClick = { tab = 3 },
                    icon = { Icon(Icons.Filled.Person, contentDescription = "我的") },
                    label = { Text("我的") }
                )
            }
        },
        floatingActionButton = {
            if (tab == 0) {
                ExtendedFloatingActionButton(
                    // 从悬浮按钮进来一定是新增，得先清掉上一轮可能残留的编辑目标
                    onClick = {
                        editingTransaction = null
                        showRecord = true
                    },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                    text = { Text("记一笔") }
                )
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            when (tab) {
                0 -> BillScreen(
                    bookId = state.bookId,
                    shared = state.currentBook?.shared == true,
                    billTemplate = state.billTemplate,
                    onOpenAccounts = { showAccounts = true },
                    onOpenCategories = { showCategories = true },
                    onEdit = {
                        editingTransaction = it
                        showRecord = true
                    }
                )

                1 -> ReportScreen(
                    bookId = state.bookId,
                    shared = state.currentBook?.shared == true
                )

                2 -> BudgetScreen(
                    bookId = state.bookId,
                    onOpenRecurring = { showRecurring = true },
                    viewModel = budgetViewModel
                )

                else -> MineScreen(
                    bookId = state.bookId,
                    loggedIn = state.loggedIn,
                    currentBookName = state.currentBook?.name,
                    currentBookShared = state.currentBook?.shared == true,
                    onOpenSettings = { showSettings = true },
                    onOpenImport = { showImport = true },
                    onOpenBooks = { showBooks = true },
                    importDone = importDone,
                    onLoginChanged = { tip ->
                        appViewModel.onLoggedIn(tip)
                        bookViewModel.refreshMyUserId()
                    },
                    onLoggedOut = appViewModel::onLoggedOut
                )
            }
        }
    }

    if (showRecord) {
        RecordScreen(
            bookId = state.bookId,
            hapticEnabled = state.hapticEnabled,
            recordTemplate = state.recordTemplate,
            editTransaction = editingTransaction,
            onDismiss = {
                showRecord = false
                editingTransaction = null
            },
            onSaved = {
                showRecord = false
                editingTransaction = null
            }
        )
    }

    if (showAccounts) {
        AccountListDialog(bookId = state.bookId, onDismiss = { showAccounts = false })
    }

    if (showCategories) {
        CategoryListDialog(bookId = state.bookId, onDismiss = { showCategories = false })
    }

    if (showSettings) {
        SettingsDialog(onDismiss = { showSettings = false })
    }

    if (showImport) {
        ImportDialog(
            bookId = state.bookId,
            onDismiss = { showImport = false },
            onImported = { importDone++ },
            onBillsCleared = { importDone++ }
        )
    }

    // 固定支出管理：全屏覆盖层，必须与 Scaffold 同级才能盖住底部导航栏
    if (showRecurring) {
        RecurringExpenseDialog(
            bookId = state.bookId,
            recurring = budgetState.recurring,
            reservation = budgetState.reservation,
            expenseCategories = budgetState.expenseCategories,
            onSave = budgetViewModel::saveRecurring,
            onDelete = budgetViewModel::deleteRecurring,
            onDismiss = { showRecurring = false }
        )
    }

    state.loginTip?.let { tip ->
        AlertDialog(
            onDismissRequest = appViewModel::consumeTip,
            confirmButton = {
                TextButton(onClick = appViewModel::consumeTip) { Text("好的") }
            },
            title = { Text("登录成功") },
            text = { Text(tip) }
        )
    }

    // 账本列表 / 成员管理：覆盖层必须挂在 Scaffold 之后（页面根部），否则盖不住底部导航栏
    if (showBooks) {
        BookListDialog(
            books = state.books,
            currentBookId = state.bookId,
            onSwitch = { bookId ->
                appViewModel.switchBook(bookId)
                showBooks = false
            },
            onDismiss = { showBooks = false },
            viewModel = bookViewModel
        )
    }

    bookState.memberBook?.let { book ->
        BookMemberDialog(
            book = book,
            onDismiss = bookViewModel::closeMembers,
            viewModel = bookViewModel
        )
    }

    bookState.message?.let { message ->
        AlertDialog(
            onDismissRequest = bookViewModel::consumeMessage,
            confirmButton = {
                TextButton(onClick = bookViewModel::consumeMessage) { Text("知道了") }
            },
            title = { Text("提示") },
            text = { Text(message) }
        )
    }

    state.message?.let { message ->
        AlertDialog(
            onDismissRequest = appViewModel::consumeMessage,
            confirmButton = {
                TextButton(onClick = appViewModel::consumeMessage) { Text("知道了") }
            },
            title = { Text("提示") },
            text = { Text(message) }
        )
    }
}
