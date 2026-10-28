package com.jizhang.app.ui.record

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jizhang.app.data.local.entity.LocalAccount
import com.jizhang.app.data.local.entity.LocalCategory
import com.jizhang.app.data.local.entity.LocalInvestPosition
import com.jizhang.app.data.local.entity.LocalTransaction
import com.jizhang.app.data.prefs.SettingsStore
import com.jizhang.app.data.prefs.SuggestionEvent
import com.jizhang.app.data.repository.AccountRepository
import com.jizhang.app.data.repository.CategoryGuess
import com.jizhang.app.data.repository.CategoryRepository
import com.jizhang.app.data.repository.CategorySuggester
import com.jizhang.app.data.repository.InvestmentRepository
import com.jizhang.app.data.repository.LedgerRepository
import com.jizhang.app.data.repository.NoteSample
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
    private val investmentRepository: InvestmentRepository,
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
        /**
         * 转入账户下的投资品种（只有转入「投资」账户时才非空）。
         * 选中某个品种 = 这笔转账计入它的成本；不选也照样能记，只是钱落在「未分配」里。
         */
        val positions: List<LocalInvestPosition> = emptyList(),
        val selectedPositionId: Long? = null,
        val selectedCategoryId: Long? = null,
        val tradeDate: LocalDate = LocalDate.now(),
        val remark: String = "",
        val tags: String = "",
        val saving: Boolean = false,
        /** 非空表示正在改这笔账单（保存时走更新而不是新增） */
        val editing: LocalTransaction? = null,
        /** 标签的快捷候选：历史高频标签在前，后面跟几个内置兜底 */
        val tagSuggestions: List<String> = emptyList(),
        /**
         * 由备注或标签推出来的分类（见 [CategorySuggester]）。
         * 非空且与当前选中不同时，分类区下方出一次提示。
         * <p>
         * 不是「一定要采纳的建议」，只是一次可省点击的机会：用户自己选过分类后就不再提示。
         */
        val categorySuggestion: CategoryGuess? = null
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

    /**
     * 「备注 / 标签 → 分类」的样本缓存：打开记账页时取一次，保存后再刷新。
     * <p>
     * 不做逐字查询：备注是弹窗确认、标签是点一下就落地，一次操作最多算一次，
     * 放在内存里反而省掉一次数据库往返。
     */
    private var noteSamples: List<NoteSample> = emptyList()

    /** 用户自己点过分类：本次表单里不再用备注去猜，免得刚选好又被提示改回去 */
    private var categoryTouched = false

    private companion object {
        /** 计算「常用标签」时回看多少条带标签的历史账单 */
        const val TAG_HISTORY_LIMIT = 200

        /** 快捷标签最多给几个（含内置兜底） */
        const val TAG_SUGGESTION_LIMIT = 8

        /** 猜分类时回看多少条带备注或标签的历史账单：再往前的写法参考价值很小 */
        const val NOTE_HISTORY_LIMIT = 300
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
            val defaultCategory = defaultCategoryId(bookId, type, categories)
            refreshNoteSamples(bookId)
            categoryTouched = false
            _state.update { current ->
                current.copy(
                    accounts = accounts,
                    accountUsage = usage,
                    lastAccountId = remembered,
                    // 保留仍然有效的选中项 → 记住的账户 → 最常用的账户 → 第一项；被删掉/归档的自动跳过。
                    // 兜底也不能直接取列表第一项：那往往是没人用过的「现金」，选中它等于把它硬塞进
                    // 常用账户芯片里，还要顶掉一个真正常用的账户。
                    selectedAccountId = current.selectedAccountId
                        ?.takeIf { id -> accounts.any { it.id == id } }
                        ?: remembered?.takeIf { id -> accounts.any { it.id == id } }
                        ?: pickQuickAccounts(accounts, usage, null, 1).firstOrNull()?.id,

                    categories = categories,
                    selectedCategoryId = current.selectedCategoryId
                        ?.takeIf { id -> categories.any { it.id == id } }
                        ?: defaultCategory,
                    categorySuggestion = null
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
                tags = transaction.tags.orEmpty(),
                // 编辑已有账单时不猜分类：这笔账的分类是用户当初定的，不要劝他改
                categorySuggestion = null
            )
        }

        viewModelScope.launch {
            // 带上归档账户：这笔账可能就记在已归档的账户上，过滤掉会在保存时被悄悄改成别的账户
            val accounts = ledgerRepository.cachedAccounts(bookId, includeArchived = true)
            val categories = ledgerRepository.cachedCategories(bookId, transaction.type)
            val remembered = runCatching { settingsStore.lastAccountId(bookId) }.getOrNull()
            val usage = loadAccountUsage(bookId)
            val defaultCategory = defaultCategoryId(bookId, transaction.type, categories)
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
                        ?: defaultCategory
                )
            }
            // 编辑态同样要刷新：用户可能刚在账单页改过标签、或新记了一笔
            refreshTagSuggestions()

            // 编辑一笔「转入投资账户」的转账时，把它挂过的品种回填出来。
            // 品种可能已被删除 —— 那就留空，并让这笔转账退回到「未分配」。
            val toAccountId = transaction.toAccountId
            if (transaction.type == 3 && toAccountId != null) {
                val list = runCatching { investmentRepository.positionsOfAccount(toAccountId) }
                    .getOrDefault(emptyList())
                val attached = runCatching {
                    investmentRepository.positionIdOfTransaction(transaction.id)
                }.getOrNull()?.takeIf { id -> list.any { it.id == id } }
                _state.update { it.copy(positions = list, selectedPositionId = attached) }
            }
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

    /**
     * 拉一份「备注 / 标签 → 当时记的分类」的样本。
     * <p>
     * 「余额调整」的备注（余额校准自动生成的那些）要剔掉：它们不是用户为消费写的备注，
     * 让「补差额」这类词去参与推测，只会把下一笔账往校准单上带。
     * 查询失败当成「没有历史」：猜不出来只是少省一次点击，不该挡住记账。
     */
    private suspend fun refreshNoteSamples(bookId: Long) {
        noteSamples = runCatching {
            val adjustId = ledgerRepository.allCategories(bookId)
                .firstOrNull { it.name == AccountRepository.ADJUST_CATEGORY_NAME }?.id
            transactionRepository.noteSamples(bookId, NOTE_HISTORY_LIMIT)
                .filter { it.categoryId != adjustId }
        }.getOrDefault(emptyList())
    }

    /**
     * 算一次「这笔像记在哪个分类」，结果只用于提示，不改任何已选值。
     * <p>
     * [note] 是刚写上来的那段文本：备注原文，或者刚点上的那个标签 —— 两者走同一套匹配规则，
     * 区别只在 [CategoryGuess.source]（用户看文案时能分辨依据是备注还是标签）。
     * <p>
     * 四种情况不猜：用户已经亲手点过分类（[categoryTouched]）、正在改旧账（分类是他当初定的）、
     * 转账（没有分类）、以及刚清空了备注。猜到与当前选中同一个分类时也不提示——那就等于什么也没省。
     */
    private fun suggestCategory(note: String) {
        val current = _state.value
        val guess = if (categoryTouched || current.editing != null || current.type == 3) {
            null
        } else {
            CategorySuggester.guess(
                note = note,
                samples = noteSamples,
                allowedCategoryIds = current.categories.mapTo(HashSet()) { it.id }
            )
        }
        val shown = guess?.takeIf { it.categoryId != current.selectedCategoryId }
        if (shown != current.categorySuggestion) {
            _state.update { it.copy(categorySuggestion = shown) }
        }
        if (shown != null) {
            track(SuggestionEvent.SHOWN)
        }
    }

    /**
     * 记一次统计（设置页「记账建议」卡片读的就是这三个计数）：只累加次数，不记录备注原文。
     * <p>
     * 丢到协程里做、失败也不管：统计坏了不该影响记账，何况落盘延迟一点无所谓。
     */
    private fun track(event: SuggestionEvent) {
        viewModelScope.launch { runCatching { settingsStore.recordSuggestion(event) } }
    }

    /**
     * 新建一笔时的默认分类：该收支类型下**历史上用得最多**的那个。
     * <p>
     * 分类列表是按分类管理页的手工顺序排的，第一项常常整年没人用，拿它兜底等于每笔都多点一次
     * ——和账户区「列表第一项往往是没人用的现金」是同一个坑，所以这里用频次而不是顺序。
     * 挑选规则见 [pickDefaultCategoryId]；查询失败不该挡住记账，失败就当成「都没用过」，
     * 退回列表第一项（新账本的行为与改动前一致）。
     */
    private suspend fun defaultCategoryId(
        bookId: Long,
        type: Int,
        categories: List<LocalCategory>
    ): Long? = pickDefaultCategoryId(
        categories = categories,
        usage = runCatching { transactionRepository.categoryUsage(bookId, type) }
            .getOrDefault(emptyMap()),
        excludedName = AccountRepository.ADJUST_CATEGORY_NAME
    )

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
        val defaultCategory = defaultCategoryId(bookId, type, categories)
        _state.update {
            it.copy(
                categories = categories,
                selectedCategoryId = defaultCategory,
                // 分类集合换了，旧的猜测可能指向别的类型，直接作废
                categorySuggestion = null
            )
        }
    }

    fun switchType(type: Int) {
        if (_state.value.type == type) {
            return
        }
        // 换了收支类型等于重新做一次选择：之前「用户已亲手选过分类」的判断作废
        categoryTouched = false
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

    /**
     * 选择转入账户。
     * <p>
     * 转入的是「投资」账户时，顺手把它名下可选的品种拉出来：让用户在这一步就把钱归到
     * 具体基金 / 股票上，成本自动累计，不必事后再去投资页手填一遍。
     */
    fun selectToAccount(id: Long) {
        _state.update {
            it.copy(selectedToAccountId = id, selectedPositionId = null, positions = emptyList())
        }
        viewModelScope.launch {
            val list = runCatching { investmentRepository.positionsOfAccount(id) }
                .getOrDefault(emptyList())
            // 加载期间用户可能又换了账户：只落在仍选中的那个上，否则会把 A 的品种挂到 B 上
            _state.update { if (it.selectedToAccountId == id) it.copy(positions = list) else it }
        }
    }

    fun selectPosition(id: Long?) {
        _state.update { it.copy(selectedPositionId = id) }
    }

    fun selectCategory(id: Long) {
        if (_state.value.selectedCategoryId == id) return
        val suggestion = _state.value.categorySuggestion
        // 用户亲手选过分类：本次表单不再猜，否则刚选完又被提示「要不改成餐饮」很讨厌
        categoryTouched = true
        _state.update { it.copy(selectedCategoryId = id, categorySuggestion = null) }
        if (suggestion != null) {
            // 命中率口径：点的正是猜的那个算「采纳」，点了别的算「改选」。
            // 两个数一除就知道这套规则值不值得留着，也是决定要不要上模型的那把尺子
            track(
                if (suggestion.categoryId == id) SuggestionEvent.ACCEPTED
                else SuggestionEvent.OVERRIDDEN
            )
        }
        // 换分类就换一套标签候选：同类的标签直接铺在表单上，一下就能点到
        viewModelScope.launch { refreshTagSuggestions() }
    }

    /**
     * 点「按备注记为 XX」。
     * <p>
     * 专门走 [selectCategory]，不自己复制一份选中逻辑：埋点口径必须与「点分类格子」完全一致，
     * 否则统计出来的采纳率是假的。
     */
    fun acceptCategorySuggestion() {
        _state.value.categorySuggestion?.let { selectCategory(it.categoryId) }
    }

    fun setTradeDate(date: LocalDate) {
        _state.update { it.copy(tradeDate = date) }
    }

    /**
     * 备注（从弹窗确认）变了就重算一次分类猜测。
     * <p>
     * 备注是弹窗点「确定」才写回状态，不是边打边存，所以这里不需要防抖：
     * 一次输入最多算一次，纯内存计算，比再开一条协程去 debounce 更简单。
     */
    fun setRemark(text: String) {
        _state.update { it.copy(remark = text) }
        suggestCategory(text)
    }

    /**
     * 标签编辑弹窗确认。
     * <p>
     * 这里**不重新猜**：用户手改的是一整段文本，可能是加一个、删一个、或者全删重打，
     * 拿整段去匹配只会得到「包含某个片段」这种含糊结论，不如只认「点标签」这一个明确动作。
     */
    fun setTags(text: String) {
        _state.update { it.copy(tags = text) }
    }

    /**
     * 快捷标签：点一下加上、再点一下去掉。
     * <p>
     * 表单里的标签就是一段逗号分隔的文本，所以这里直接按文本增删，不再引一份「已选集合」状态
     * —— 否则用户手动改过标签文本之后，点选状态会和实际内容对不上。
     * 文本增删的具体规则与页面里的标签弹窗共用（[toggleTagText]）。
     * <p>
     * 点上一个标签顺带猜一次分类：标签是用户从候选里挑的、语义受控，比自由文本硬得多。
     * 只在「加上」时猜——点掉一个标签往往是在清理，此时弹一个分类建议只会碍事。
     */
    fun toggleTag(tag: String) {
        _state.update { it.copy(tags = toggleTagText(it.tags, tag)) }
        if (tag in parseTags(_state.value.tags)) {
            suggestCategory(tag)
        } else {
            clearSuggestion()
        }
    }

    /** 新输入不足以支撑猜测时，把上一次的提示撤掉，别让旧结论挂在那儿 */
    private fun clearSuggestion() {
        if (_state.value.categorySuggestion != null) {
            _state.update { it.copy(categorySuggestion = null) }
        }
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
                // 同理，这笔的备注与标签马上成为下一条的参考样本（连着记同类账时最有用）
                refreshNoteSamples(current.bookId)
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
        val transactionId = if (editing == null) {
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
            editing.id
        }

        // 挂到投资品种上：买入还是卖出由转出/转入账户判定，用户只需要选品种。
        // 这一步失败不影响记账本身（钱已经记对了），最多是成本没自动累计 ——
        // 用户仍能去投资页手动改，所以不把异常抛给保存流程。
        runCatching {
            investmentRepository.syncTransfer(
                bookId = current.bookId,
                transactionId = transactionId,
                positionId = current.selectedPositionId,
                type = current.type,
                amountFen = fen,
                accountId = accountId,
                toAccountId = toAccountId,
                occurredAt = millis
            )
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
                    // 先按类型重查（会默认选中常用分类），再覆盖成本次新建的那个
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
     *                     为 false 时回到默认：支出 + 常用的分类。
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
                // 品种是跟着「转入账户」走的：转入账户清空了，它也必须一起清，否则会把
                // 上一笔的品种悄悄挂到下一笔转账上
                positions = emptyList(),
                selectedPositionId = null,
                // **故意**保留 selectedAccountId：记账是连着记的，钱包/银行卡通常不变，
                // 每次都回落到第一个账户反而要多点一下。账户被删除或归档时由 load() /
                // startEdit() 的「列表里存在才保留」逻辑兜住。
                selectedCategoryId = keepCategoryId,
                // 这里**不**重置记账日期：切「支出/收入/转账」不该把用户刚选的日期（比如日历里
                // 补记的那天）偷偷改回今天；下次打开记账页时 load() 会重新落到今天
                editing = null,
                // 页面关闭时可能还有一次保存在飞：清掉转圈，别把它带进下一次打开
                saving = false,
                // 备注被清空了，基于它的猜测与「已亲手选过」都该归零
                categorySuggestion = null
            )
        }
        categoryTouched = false

        // 分类是按类型缓存的：类型变了必须重查一次，否则保留的 categoryId 会落在错误的类型上
        val bookId = snapshot.bookId
        if (bookId <= 0L) {
            return
        }
        viewModelScope.launch {
            val categories = ledgerRepository.cachedCategories(bookId, type)
            val kept = categories.firstOrNull { it.id == keepCategoryId }?.id
            val defaultCategory = defaultCategoryId(bookId, type, categories)
            _state.update {
                it.copy(
                    categories = categories,
                    selectedCategoryId = kept ?: defaultCategory
                )
            }
        }
    }
}

/* -------------------- 默认分类 -------------------- */

/**
 * 按历史使用次数挑一个默认分类（纯函数，便于单测）。
 *
 * 规则只有两条，但都必须说清楚：
 * 1. **次数最多者胜**；次数相同时取列表里靠前的那个（[maxByOrNull] 返回第一个最大值），
 *    即保持分类管理页的手工顺序，用户对「常点的分类在哪」有肌肉记忆；
 * 2. `excludedName` 命中时直接排除 —— 「余额调整」是余额校准自动生成的分类，
 *    被选成默认会让下一笔账莫名其妙记成一笔校准单。
 *    整张列表都被排除时（只建了这么一个分类）不去钻牛角尖，仍然返回它，否则就没有默认值了。
 *
 * @return 没有可选分类时为 null
 */
internal fun pickDefaultCategoryId(
    categories: List<LocalCategory>,
    usage: Map<Long, Long>,
    excludedName: String? = null
): Long? {
    val candidates = categories
        .filterNot { excludedName != null && it.name == excludedName }
        .ifEmpty { categories }
    return candidates.maxByOrNull { usage[it.id] ?: 0L }?.id
}

/* -------------------- 常用账户 -------------------- */

/**
 * 记账页铺哪几个账户芯片（纯函数，便于单测）。规则按优先级：
 *
 * 1. **只有真正记过账的账户才占位置**：从没被用过的账户不参与竞争，否则账户管理里排在
 *    前面的闲置账户（账本初始化时自动建的那几个）会白白补进芯片行，看着像「常用」其实一次没用过；
 * 2. **用得多的在前**，次数相同保持账户管理里的手工顺序（[sortedByDescending] 是稳定排序）；
 * 3. **当前选中的账户必须可见**：从「更多」里挑过冷门账户后，前三里没有它就顶掉最后一名，
 *    否则用户会以为没选上；
 * 4. 一笔都没用过（新账本、刚导入还没记）时退回手工顺序的前 [limit] 个 ——
 *    宁可回到老行为，也不要一个芯片都不给。
 *
 * 归档账户不占芯片位；但它如果是当前选中（编辑旧账），由规则 3 补回来，不能把原账户藏掉。
 */
internal fun pickQuickAccounts(
    accounts: List<LocalAccount>,
    usage: Map<Long, Long>,
    selectedAccountId: Long?,
    limit: Int
): List<LocalAccount> {
    if (limit <= 0) return emptyList()
    val candidates = accounts.filterNot { it.archived }
    val used = candidates
        .filter { (usage[it.id] ?: 0L) > 0L }
        .sortedByDescending { usage[it.id] ?: 0L }
    val top = (used.ifEmpty { candidates }).take(limit).toMutableList()
    val selected = accounts.firstOrNull { it.id == selectedAccountId }
    if (selected != null && top.none { it.id == selected.id }) {
        if (top.size >= limit) {
            top[top.lastIndex] = selected
        } else {
            top.add(selected)
        }
    }
    return top
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
