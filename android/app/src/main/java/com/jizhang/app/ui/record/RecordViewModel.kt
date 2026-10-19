package com.jizhang.app.ui.record

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jizhang.app.data.local.entity.LocalAccount
import com.jizhang.app.data.local.entity.LocalCategory
import com.jizhang.app.data.local.entity.LocalTransaction
import com.jizhang.app.data.prefs.SettingsStore
import com.jizhang.app.data.repository.CategoryRepository
import com.jizhang.app.data.repository.LedgerRepository
import com.jizhang.app.data.repository.TransactionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

/**
 * 「记一笔」全屏页的 ViewModel。
 * <p>
 * 金额不再走系统键盘，而是维护一段四则运算表达式（[CalcEngine]），
 * 分类 / 账户 / 日期 / 备注 / 标签全部在一个界面内完成。
 */
@HiltViewModel
class RecordViewModel @Inject constructor(
    private val ledgerRepository: LedgerRepository,
    private val transactionRepository: TransactionRepository,
    private val categoryRepository: CategoryRepository,
    private val settingsStore: SettingsStore
) : ViewModel() {

    data class UiState(
        val bookId: Long = 0L,
        /** 1=支出 2=收入 3=转账 */
        val type: Int = 1,
        /** 计算器表达式（单位：元） */
        val tokens: List<CalcToken> = emptyList(),
        val accounts: List<LocalAccount> = emptyList(),
        /** 各账户被用过的次数：记账页的账户区只铺三个常用账户，按它排序 */
        val accountUsage: Map<Long, Long> = emptyMap(),
        val categories: List<LocalCategory> = emptyList(),
        val selectedAccountId: Long? = null,
        /** 上次保存时用的账户（按账本记忆）：账户选择器里排在第二，仅次于当前选中 */
        val lastAccountId: Long? = null,
        /** 转账的转入账户 */
        val selectedToAccountId: Long? = null,
        val selectedCategoryId: Long? = null,
        val tradeDate: LocalDate = LocalDate.now(),
        val remark: String = "",
        val tags: String = "",
        val saving: Boolean = false,
        /** 非空表示正在改这笔账单（保存时走更新而不是新增） */
        val editing: LocalTransaction? = null,
        /** 标签的快捷候选：历史高频标签在前，后面跟几个内置兜底 */
        val tagSuggestions: List<String> = emptyList()
    ) {
        val expressionText: String get() = CalcEngine.expressionText(tokens)
        val resultYuan: BigDecimal? get() = CalcEngine.evaluate(tokens)

        /** 金额、账户（转账还需转入账户）都齐了才允许保存 */
        val canSave: Boolean
            get() = CalcEngine.resultFen(tokens) != null &&
                selectedAccountId != null &&
                (type != 3 || selectedToAccountId != null)
    }

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private var loadedBookId = -1L

    private companion object {
        /** 计算「常用标签」时回看多少条带标签的历史账单 */
        const val TAG_HISTORY_LIMIT = 200

        /** 快捷标签最多给几个（含内置兜底） */
        const val TAG_SUGGESTION_LIMIT = 8
    }

    /** 历史标签还不够用时的兜底候选，保证新用户第一次进来也有得点 */
    private val defaultExpenseTags = listOf("午餐", "晚餐", "早餐", "饮料", "打车", "超市")
    private val defaultIncomeTags = listOf("工资", "奖金", "红包", "报销")

    /**
     * 分类名关键词 → 该分类下常见的标签。
     * <p>
     * 标签在库里只是账单上的逗号串，与分类没有归属关系；分类一换，候选里全是别的分类的词就很碍事
     * （记「交通」时刷出「晚餐」）。所以先用分类名匹配这套小字典，再拿历史词频兜住。
     * 候选顺序：**这个分类的历史高频 → 分类字典 → 全量历史高频 → 内置兜底**。
     */
    private val categoryTagDictionary = listOf(
        listOf("餐", "饭", "食", "外卖", "饮") to
            listOf("早餐", "午餐", "晚餐", "饮料", "外卖"),
        listOf("交通", "出行", "打车", "地铁", "公交", "加油", "停车") to
            listOf("打车", "地铁", "公交", "加油", "停车"),
        listOf("购物", "超市", "日用", "网购") to
            listOf("超市", "日用", "网购", "衣服"),
        listOf("娱乐", "电影", "游戏", "聚会", "旅游") to
            listOf("电影", "游戏", "聚会", "门票"),
        listOf("医疗", "药", "健康") to
            listOf("药品", "门诊", "体检"),
        listOf("居住", "房租", "水电", "物业") to
            listOf("房租", "水电", "物业", "宽带"),
        listOf("学习", "教育", "书", "培训") to
            listOf("图书", "课程", "文具"),
        listOf("通讯", "话费", "流量") to
            listOf("话费", "流量", "宽带"),
        listOf("宠物") to
            listOf("狗粮", "猫砂", "宠物医院")
    )

    /**
     * 打开记账页时加载账户与分类。
     *
     * **不能**因为「还是同一个账本」就早退：用户可能刚在账单页的「分类管理」里加了分类，
     * 再打开记账页必须能看到它。只有切账本时才需要把表单整体重置。
     */
    fun load(bookId: Long, initialDate: LocalDate? = null) {
        val bookChanged = loadedBookId != bookId
        loadedBookId = bookId
        // 记账日期默认今天；从日历页「这天记一笔」进来时预填那一天（补记历史账）。
        // 保存时对历史日期有专门规则（记当天 12:00），见 save()。
        val tradeDate = initialDate ?: LocalDate.now()
        // 明确清掉编辑目标：从「编辑」切到「记一笔」时表单可能被保留（同一账本），
        // 若 editing 残留，下一次保存会去改上一笔账
        _state.update {
            if (bookChanged) {
                UiState(bookId = bookId, tradeDate = tradeDate)
            } else {
                it.copy(bookId = bookId, editing = null, tradeDate = tradeDate)
            }
        }

        viewModelScope.launch {
            val type = _state.value.type
            val accounts = ledgerRepository.cachedAccounts(bookId)
            val categories = ledgerRepository.cachedCategories(bookId, type)
            // 上次用过的账户：进程重启、切回账本后仍然是它，不用每次点开选择器
            //（列表第一项往往是没人用的「现金」，靠它兜底等于每笔多点一次）
            val remembered = runCatching { settingsStore.lastAccountId(bookId) }.getOrNull()
            val usage = loadAccountUsage(bookId)
            _state.update { current ->
                current.copy(
                    accounts = accounts,
                    accountUsage = usage,
                    lastAccountId = remembered,
                    // 保留仍然有效的选中项 → 记住的账户 → 第一项；被删掉/归档的自动跳过
                    selectedAccountId = current.selectedAccountId
                        ?.takeIf { id -> accounts.any { it.id == id } }
                        ?: remembered?.takeIf { id -> accounts.any { it.id == id } }
                        ?: accounts.firstOrNull()?.id,
                    categories = categories,
                    selectedCategoryId = current.selectedCategoryId
                        ?.takeIf { id -> categories.any { it.id == id } }
                        ?: categories.firstOrNull()?.id
                )
            }
            refreshTagSuggestions()
        }
    }

    /**
     * 进入「编辑账单」态：把已有账单回填进表单。
     * <p>
     * 回填顺序有讲究：分类列表是按收支类型缓存的，`switchType` / `loadCategories` 会把选中项
     * 重置成第一个，所以必须先落好 type + categoryId，再拉列表，最后用「列表里确实存在才保留」
     * 的方式兜住原值（原来的分类被删掉时回落到第一个）。
     */
    fun startEdit(bookId: Long, transaction: LocalTransaction) {
        loadedBookId = bookId
        _state.update {
            it.copy(
                bookId = bookId,
                editing = transaction,
                type = transaction.type,
                // 金额回填成一个数字，用户可以接着用计算器改（例如「2500+300」）
                tokens = listOf(CalcNum(yuanPlainText(transaction.amount))),
                selectedAccountId = transaction.accountId,
                selectedToAccountId = transaction.toAccountId,
                selectedCategoryId = transaction.categoryId,
                tradeDate = Instant.ofEpochMilli(transaction.tradeTime)
                    .atZone(ZoneId.systemDefault())
                    .toLocalDate(),
                remark = transaction.remark.orEmpty(),
                tags = transaction.tags.orEmpty()
            )
        }

        viewModelScope.launch {
            // 带上归档账户：这笔账可能就记在已归档的账户上，过滤掉会在保存时被悄悄改成别的账户
            val accounts = ledgerRepository.cachedAccounts(bookId, includeArchived = true)
            val categories = ledgerRepository.cachedCategories(bookId, transaction.type)
            val remembered = runCatching { settingsStore.lastAccountId(bookId) }.getOrNull()
            val usage = loadAccountUsage(bookId)
            _state.update { current ->
                current.copy(
                    accounts = accounts,
                    accountUsage = usage,
                    lastAccountId = remembered,
                    // 原账单的账户优先；它被删了才回落到记住的账户，最后才是第一项
                    selectedAccountId = current.selectedAccountId
                        ?.takeIf { id -> accounts.any { it.id == id } }
                        ?: remembered?.takeIf { id -> accounts.any { it.id == id } }
                        ?: accounts.firstOrNull()?.id,
                    categories = categories,
                    selectedCategoryId = current.selectedCategoryId
                        ?.takeIf { id -> categories.any { it.id == id } }
                        ?: categories.firstOrNull()?.id
                )
            }
            // 编辑态同样要刷新：用户可能刚在账单页改过标签、或新记了一笔
            refreshTagSuggestions()
        }
    }

    /** 「分」→ 计算器输入用的「元」文本（去掉多余的零，2500 而不是 2500.00） */
    private fun yuanPlainText(fen: Long): String =
        BigDecimal.valueOf(fen, 2).stripTrailingZeros().toPlainString()

    /**
     * 刷新页面上的「顺手选项」：当前分类的常用标签候选。
     * <p>
     * 记账是高频动作，「少打几个字」比「标签体系更规范」更重要，所以直接对最近若干条带标签的
     * 账单做词频统计，不让用户先维护一套标签体系；再叠加一层按分类名匹配的小字典，
     * 保证切换分类时候选跟着变（记「交通」不会再刷出「晚餐」）。
     * <p>
     * 标签随时可能被别处改掉（账单页删一笔、导入、换账本），所以打开页面、切换分类与每次保存后
     * 都重新拉一遍，不做缓存。
     *
     * @param pruneTags 换分类时传 true：顺手清掉「明显属于别的分类」的已选标签（见 [keepRelevantTags]），
     *                  只在分类切换时做，保存后 / 打开页面时不碰用户已经选好的标签。
     */
    private suspend fun refreshTagSuggestions(pruneTags: Boolean = false) {
        val current = _state.value
        val categoryId = current.selectedCategoryId
        val categoryName = current.categories.firstOrNull { it.id == categoryId }?.name

        val byCategory = categoryId?.let { id ->
            runCatching { transactionRepository.recentTagsByCategory(current.bookId, id, TAG_HISTORY_LIMIT) }
                .getOrDefault(emptyList())
        }.orEmpty().rankByFrequency()

        val byHistory = runCatching { transactionRepository.recentTags(current.bookId, TAG_HISTORY_LIMIT) }
            .getOrDefault(emptyList())
            .rankByFrequency()

        val dictionaryTags = tagsForCategory(categoryName)
        val dictionary = dictionaryTags + if (current.type == 2) defaultIncomeTags else defaultExpenseTags

        _state.update { state ->
            // 连点分类时先发后到的旧结果不能覆盖当前分类：结果回来时分类已经变了就丢弃
            if (state.selectedCategoryId != categoryId) {
                state
            } else {
                state.copy(
                    tags = if (pruneTags) {
                        keepRelevantTags(state.tags, byCategory, dictionaryTags, byHistory)
                    } else {
                        state.tags
                    },
                    tagSuggestions = (byCategory + dictionary + byHistory)
                        .distinct()
                        .take(TAG_SUGGESTION_LIMIT)
                )
            }
        }
    }

    /**
     * 换分类时清洗已选标签，规则是「只删明显不相关的，别删用户刚打的」：
     * 1. 这个分类打过（[categoryTags]）或在该分类字典里（[dictionaryTags]）→ 留下；
     * 2. 历史里从来没出现过（用户刚手打的新词）→ 留下；
     * 3. 只在别的分类出现过 → 清掉，例如记「交通」时残留的「晚餐」。
     *
     * 新分类既没有历史也没有字典命中时（自定义分类）无从判断，原样保留。
     */
    private fun keepRelevantTags(
        raw: String,
        categoryTags: List<String>,
        dictionaryTags: List<String>,
        globalTags: List<String>
    ): String {
        if (raw.isBlank()) return raw
        val allowed = (categoryTags + dictionaryTags).toSet()
        if (allowed.isEmpty()) return raw
        val known = globalTags.toSet()
        return parseTags(raw)
            .filter { it in allowed || it !in known }
            .joinToString(",")
    }

    /**
     * 账户使用次数：记账页据此只铺三个常用账户。
     * 查询失败不该挡住记账，失败就当成「都没用过」，退回账户管理里的手工顺序。
     */
    private suspend fun loadAccountUsage(bookId: Long): Map<Long, Long> =
        runCatching { transactionRepository.accountUsage(bookId) }.getOrDefault(emptyMap())

    /** 标签原文列表 → 按出现次数降序的词表 */
    private fun List<String>.rankByFrequency(): List<String> = flatMap { parseTags(it) }
        .groupingBy { it }
        .eachCount()
        .entries
        .sortedByDescending { it.value }
        .map { it.key }

    private fun tagsForCategory(categoryName: String?): List<String> {
        val name = categoryName.orEmpty()
        if (name.isBlank()) return emptyList()
        return categoryTagDictionary
            .firstOrNull { (keywords, _) -> keywords.any { name.contains(it) } }
            ?.second
            .orEmpty()
    }

    private suspend fun loadCategories(bookId: Long, type: Int) {
        val categories = ledgerRepository.cachedCategories(bookId, type)
        _state.update {
            it.copy(
                categories = categories,
                selectedCategoryId = categories.firstOrNull()?.id
            )
        }
    }

    fun switchType(type: Int) {
        if (_state.value.type == type) {
            return
        }
        _state.update { it.copy(type = type) }
        viewModelScope.launch {
            loadCategories(_state.value.bookId, type)
            // 分类列表重置成第一个了，标签候选与已选标签要跟着换成新分类的
            refreshTagSuggestions(pruneTags = true)
        }
    }

    /* ---------------- 计算器 ---------------- */

    fun pressDigit(digit: Char) = updateTokens { CalcEngine.appendDigit(it, digit) }

    fun pressOp(op: Char) = updateTokens { CalcEngine.appendOp(it, op) }

    fun pressBackspace() = updateTokens { CalcEngine.backspace(it) }

    fun pressClear() = updateTokens { CalcEngine.clear() }

    fun pressEquals() = updateTokens { CalcEngine.applyEquals(it) }

    private fun updateTokens(transform: (List<CalcToken>) -> List<CalcToken>) {
        _state.update { it.copy(tokens = transform(it.tokens)) }
    }

    /* ---------------- 选项 ---------------- */

    fun selectAccount(id: Long) {
        _state.update { it.copy(selectedAccountId = id) }
    }

    fun selectToAccount(id: Long) {
        _state.update { it.copy(selectedToAccountId = id) }
    }

    fun selectCategory(id: Long) {
        if (_state.value.selectedCategoryId == id) return
        _state.update { it.copy(selectedCategoryId = id) }
        // 换分类就换一套标签候选：同类的标签直接铺在表单上，一下就能点到
        viewModelScope.launch { refreshTagSuggestions() }
    }

    fun setTradeDate(date: LocalDate) {
        _state.update { it.copy(tradeDate = date) }
    }

    fun setRemark(text: String) {
        _state.update { it.copy(remark = text) }
    }

    fun setTags(text: String) {
        _state.update { it.copy(tags = text) }
    }

    /**
     * 快捷标签：点一下加上、再点一下去掉。
     * <p>
     * 表单里的标签就是一段逗号分隔的文本，所以这里直接按文本增删，不再引一份「已选集合」状态
     * —— 否则用户手动改过标签文本之后，点选状态会和实际内容对不上。
     * 文本增删的具体规则与页面里的标签弹窗共用（[toggleTagText]）。
     */
    fun toggleTag(tag: String) {
        _state.update { it.copy(tags = toggleTagText(it.tags, tag)) }
    }

    /** 保存到本地（离线可用），返回错误信息或 null（成功） */
    fun save(onDone: (String?) -> Unit) {
        val current = _state.value
        val fen = CalcEngine.resultFen(current.tokens)
        when {
            fen == null -> {
                onDone("请输入金额")
                return
            }

            current.selectedAccountId == null -> {
                onDone("请选择账户")
                return
            }

            current.type == 3 && current.selectedToAccountId == null -> {
                onDone("请选择转入账户")
                return
            }

            current.type == 3 && current.selectedToAccountId == current.selectedAccountId -> {
                onDone("转出与转入账户不能相同")
                return
            }
        }

        viewModelScope.launch {
            _state.update { it.copy(saving = true) }
            // saving 必须无条件复位（含抛异常 / 协程取消）：它是常驻 VM 上的状态，
            // 一旦卡在 true，本次保存按钮永远转圈，而且关掉页面后还会被下一次打开继承。
            val error = try {
                persist(current, fen)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (t: Exception) {
                t.message ?: "保存失败，请重试"
            } finally {
                _state.update { it.copy(saving = false) }
            }
            if (error == null) {
                // 记住这次用的账户：下次进记账页直接就是它（按账本存，多账本不串号）。
                // 写失败不影响记账本身，吞掉异常即可
                current.selectedAccountId?.let { id ->
                    runCatching { settingsStore.setLastAccount(current.bookId, id) }
                    _state.update { it.copy(lastAccountId = id) }
                }
                // 这一笔会改变「常用账户」排序：重算一次，下次打开首页就是最新的三个
                val usage = loadAccountUsage(current.bookId)
                _state.update { it.copy(accountUsage = usage) }
                // 刚记完的这笔立刻进「常用标签 / 最近记过」：连着记下一笔时不用重新打
                refreshTagSuggestions()
            }
            onDone(error)
        }
    }

    /** 真正落库（新增 / 更新），返回错误文案，null 表示成功 */
    private suspend fun persist(current: UiState, fen: Long): String? {
        val editing = current.editing
        val originalDate = editing?.let {
            Instant.ofEpochMilli(it.tradeTime).atZone(ZoneId.systemDefault()).toLocalDate()
        }
        // 时间规则：今天的账用「保存这一刻」的真实时间；补记历史日期的账统一记当天 12:00
        //（与导入缺时间时的规则一致，避免列表里整屏 00:00）。
        // 编辑时没动日期就沿用原发生时间，免得「只改个备注」把记账时刻也挪走了。
        val millis = when {
            editing != null && originalDate == current.tradeDate -> editing.tradeTime
            current.tradeDate == LocalDate.now() -> System.currentTimeMillis()
            else -> current.tradeDate
                .atTime(12, 0)
                .atZone(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli()
        }

        val accountId = current.selectedAccountId!!
        val toAccountId = if (current.type == 3) current.selectedToAccountId else null
        val categoryId = if (current.type == 3) null else current.selectedCategoryId
        if (editing == null) {
            transactionRepository.addLocal(
                bookId = current.bookId,
                type = current.type,
                amount = fen,
                accountId = accountId,
                toAccountId = toAccountId,
                categoryId = categoryId,
                tradeTime = millis,
                remark = current.remark.ifBlank { null },
                tags = current.tags.ifBlank { null }
            )
        } else {
            val updated = transactionRepository.updateLocal(
                id = editing.id,
                type = current.type,
                amount = fen,
                accountId = accountId,
                toAccountId = toAccountId,
                categoryId = categoryId,
                tradeTime = millis,
                remark = current.remark.ifBlank { null },
                tags = current.tags.ifBlank { null }
            )
            if (!updated) {
                return "这笔账单已被删除，无法保存修改"
            }
        }
        return null
    }

    /**
     * 记账页分类区的「＋新建」：在当前收支类型下建一个分类，成功后自动选中它。
     *
     * 类型跟随当前记账类型而不是让用户选 —— 否则新建的分类不在当前分类列表里，看起来像没建成功。
     *
     * @param onDone 返回错误文案，null 表示成功
     */
    fun addCategory(name: String, icon: String, onDone: (String?) -> Unit) {
        val bookId = _state.value.bookId
        val type = _state.value.type
        if (bookId <= 0L) {
            onDone("账本还没准备好，稍后再试")
            return
        }
        viewModelScope.launch {
            categoryRepository.create(bookId, name, type, icon)
                .onSuccess { created ->
                    // 先按类型重查（会默认选中第一个），再覆盖成本次新建的那个
                    loadCategories(bookId, type)
                    _state.update { it.copy(selectedCategoryId = created.id) }
                    onDone(null)
                }
                .onFailure { onDone(it.message ?: "创建分类失败") }
        }
    }

    /**
     * 清空表单。
     *
     * 记账页是 Activity 作用域的常驻 ViewModel（工程没有 Navigation，见 [com.jizhang.app.ui.common.AppFullScreenDialog]），
     * 页面只是反复 show/hide 覆盖层，**同一个 VM 实例存活全程**。所以每次关闭记账页都必须显式复位，
     * 否则下次打开会看到上一次的金额表达式、备注、标签、分类选择（「记完账旧数据没清零」）。
     *
     * @param preserveType 保留「支出/收入」与分类选择（连续记同类账时更顺手）；
     *                     为 false 时回到默认：支出 + 第一个分类。
     */
    fun resetForm(preserveType: Boolean = false) {
        val snapshot = _state.value
        val type = if (preserveType) snapshot.type else 1
        val keepCategoryId = if (preserveType) snapshot.selectedCategoryId else null

        _state.update {
            it.copy(
                type = type,
                tokens = emptyList(),
                remark = "",
                tags = "",
                selectedToAccountId = null,
                // **故意**保留 selectedAccountId：记账是连着记的，钱包/银行卡通常不变，
                // 每次都回落到第一个账户反而要多点一下。账户被删除或归档时由 load() /
                // startEdit() 的「列表里存在才保留」逻辑兜住。
                selectedCategoryId = keepCategoryId,
                // 这里**不**重置记账日期：切「支出/收入/转账」不该把用户刚选的日期（比如日历里
                // 补记的那天）偷偷改回今天；下次打开记账页时 load() 会重新落到今天
                editing = null,
                // 页面关闭时可能还有一次保存在飞：清掉转圈，别把它带进下一次打开
                saving = false
            )
        }

        // 分类是按类型缓存的：类型变了必须重查一次，否则保留的 categoryId 会落在错误的类型上
        val bookId = snapshot.bookId
        if (bookId <= 0L) {
            return
        }
        viewModelScope.launch {
            val categories = ledgerRepository.cachedCategories(bookId, type)
            val kept = categories.firstOrNull { it.id == keepCategoryId }?.id
            _state.update {
                it.copy(
                    categories = categories,
                    selectedCategoryId = kept ?: categories.firstOrNull()?.id
                )
            }
        }
    }
}

/* -------------------- 标签文本 ⇄ 标签列表 -------------------- */

/** 标签文本的分隔符：中英文逗号 / 分号 / 顿号 / 竖线（与导入解析 `BillCsvParser` 口径一致） */
internal val TAG_SEPARATOR = Regex("[,，;；、|/]+")

/**
 * 「逗号分隔的标签文本」→ 标签列表。
 *
 * 表单里的标签从来不是一份独立数据结构，就是一段文本；所以「这个标签有没有选」一律按这段
 * 文本现算。记账页（快捷标签芯片）与 [RecordViewModel] 共用同一套切分规则。
 */
internal fun parseTags(raw: String): List<String> =
    raw.split(TAG_SEPARATOR).map { it.trim() }.filter { it.isNotEmpty() }

/** 快捷标签点选：文本里已有就删掉，没有就追加到末尾，返回新的标签文本 */
internal fun toggleTagText(raw: String, tag: String): String {
    val picked = parseTags(raw).toMutableList()
    if (!picked.remove(tag)) {
        picked.add(tag)
    }
    return picked.joinToString(",")
}
