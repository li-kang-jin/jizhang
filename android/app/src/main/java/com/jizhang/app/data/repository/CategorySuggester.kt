package com.jizhang.app.data.repository

/**
 * 一条历史样本：这条账的备注原文、标签原文，以及它当时落在哪个分类。
 * <p>
 * 备注和标签都可能是空的，但至少有一样 —— 两样都空的账在「猜分类」这件事上没有任何信息量。
 * <p>
 * [tradeTime] 只在「同一个说法被记到过不同分类」时用来裁决：取更近的那次，
 * 因为用户的分类习惯会变（以前把「咖啡」记零食，后来改记餐饮）。
 */
data class NoteSample(
    val remark: String?,
    val tags: String?,
    val categoryId: Long,
    val tradeTime: Long
)

/** 这次猜测的证据来自哪里：备注是手打的，标签是从候选里挑的，两者的可信度不一样 */
enum class GuessSource { REMARK, TAG }

/**
 * 推出来的分类猜测。
 * <p>
 * [matchedText] 是命中的原文（备注整句，或某个标签），只用于向用户解释「凭哪条历史猜的」——
 * 包含匹配时它与用户当前的输入并不相同，把差异摆出来，用户才能判断这次猜得靠不靠谱。
 */
data class CategoryGuess(
    val categoryId: Long,
    val matchedText: String,
    val source: GuessSource,
    /** 字面完全相同（而不是包含关系）—— 命中有多硬 */
    val exact: Boolean
)

/**
 * 「备注 / 标签 → 分类」的猜测（第一版：纯规则，不上模型）。
 *
 * ## 为什么先用规则
 *
 * 记账的备注与标签重复度极高：「午饭牛肉面」「瑞幸」「打车」几乎一字不差地反复出现。
 * 先用「相同 / 包含」把这类命中捞干净，就能量出这个功能到底有多少真实命中率；
 * 命中率不行（写法太散）再上朴素贝叶斯去覆盖变体，够高就没必要上。
 * 反过来先写模型，是拿几天工时换一个没法验证的东西。
 *
 * ## 标签为什么是更硬的证据
 *
 * 标签是用户从候选里挑的、可用词表很小，同一个词几乎总落在同一个分类上；
 * 备注是自由文本，同一个词可能出现在各种语境里。所以字面完全相同时，标签的得分略高于备注。
 * 反过来，包含关系里标签给分更低：标签很短，「餐」「卡」这类片段一旦按包含去匹配，
 * 会命中一大片无关的历史。
 *
 * ## 打分
 *
 * 只取「最好的那一条历史」，而不是做词袋统计：规则少、可解释、错了也容易查。
 * - 完全相同：备注 `1000 + 长度`，标签 `1100 + 长度`
 * - 历史片段 ⊂ 当前输入（`午饭牛肉面` ⊃ `牛肉面`）：备注 `500 + 长度×10`，标签 `420 + 长度×10`
 * - 当前输入 ⊂ 历史（`公司楼下咖啡` ⊃ `咖啡`）：备注 `200 + 长度×10`，标签 `150 + 长度×10`
 * - 同分取更近的一次
 */
object CategorySuggester {

    /** 少于两个字不猜：「水」「饭」这类单字几乎能命中一切，猜错比不猜更烦人 */
    private const val MIN_LENGTH = 2

    private const val SCORE_REMARK_EXACT = 1000
    private const val SCORE_TAG_EXACT = 1100
    private const val SCORE_REMARK_FRAGMENT = 500
    private const val SCORE_TAG_FRAGMENT = 420
    private const val SCORE_REMARK_CONTAINS = 200
    private const val SCORE_TAG_CONTAINS = 150
    private const val LENGTH_WEIGHT = 10

    /**
     * 标签串的切分：与记账页、导入解析同一套分隔符（中英文逗号 / 分号 / 顿号 / 竖线 / 空白）。
     * 这里不去引用 UI 层的那个常量：数据层反过来依赖界面层会把依赖方向弄反。
     */
    private val TAG_SPLITTER = Regex("[,，;；、|/\\s]+")

    /**
     * @param note 当前输入的文本：备注原文，或者刚点上的那个标签
     * @param samples 历史样本（越新越好，但本方法不依赖顺序）
     * @param allowedCategoryIds 当前收支类型下可选的分类：切到「收入」时不该推荐一个支出分类
     * @return 猜不出来（没历史 / 太短 / 只命中别的类型）时为 null
     */
    fun guess(
        note: String,
        samples: List<NoteSample>,
        allowedCategoryIds: Set<Long>
    ): CategoryGuess? {
        val query = normalize(note)
        if (query.length < MIN_LENGTH) return null

        var best: CategoryGuess? = null
        var bestScore = 0
        var bestTime = Long.MIN_VALUE

        for (sample in samples) {
            if (sample.categoryId !in allowedCategoryIds) continue
            val hit = bestHit(query, sample) ?: continue
            val better = hit.score > bestScore ||
                (hit.score == bestScore && sample.tradeTime > bestTime)
            if (better) {
                best = CategoryGuess(
                    categoryId = sample.categoryId,
                    matchedText = hit.text,
                    source = hit.source,
                    exact = hit.exact
                )
                bestScore = hit.score
                bestTime = sample.tradeTime
            }
        }
        return best
    }

    /** 单条样本里最强的那个命中：备注整句，或某个标签 */
    private fun bestHit(query: String, sample: NoteSample): Hit? {
        var best: Hit? = null
        sample.remark?.let { remark ->
            score(query, remark, GuessSource.REMARK)?.let { best = stronger(best, it) }
        }
        sample.tags?.split(TAG_SPLITTER)?.forEach { raw ->
            if (raw.isBlank()) return@forEach
            score(query, raw, GuessSource.TAG)?.let { best = stronger(best, it) }
        }
        return best
    }

    private fun stronger(current: Hit?, candidate: Hit): Hit =
        if (current == null || candidate.score > current.score) candidate else current

    private class Hit(
        val score: Int,
        /** 命中的原文（未归一化），用于向用户解释 */
        val text: String,
        val source: GuessSource,
        val exact: Boolean
    )

    private fun score(query: String, raw: String, source: GuessSource): Hit? {
        val candidate = normalize(raw)
        if (candidate.length < MIN_LENGTH) return null
        val exact = candidate == query
        val tag = source == GuessSource.TAG
        val base = when {
            exact -> if (tag) SCORE_TAG_EXACT else SCORE_REMARK_EXACT
            query.contains(candidate) -> if (tag) SCORE_TAG_FRAGMENT else SCORE_REMARK_FRAGMENT
            candidate.contains(query) -> if (tag) SCORE_TAG_CONTAINS else SCORE_REMARK_CONTAINS
            else -> return null
        }
        // 加长度是为了让更具体的命中赢：备注「牛肉面」比「午饭」更能说明这笔记的是什么
        val meaningful = if (exact || query.contains(candidate)) candidate.length else query.length
        return Hit(base + meaningful * LENGTH_WEIGHT, raw.trim(), source, exact)
    }

    /**
     * 归一化：只留中英文与数字。
     * <p>
     * 中文没有分词也能做包含匹配，代价是空格、标点、大小写这些「打字习惯差异」必须抹掉，
     * 否则「午饭 牛肉面」和「午饭牛肉面」会被当成两件事。表情符号一并丢掉。
     */
    internal fun normalize(raw: String): String =
        raw.filter { it.isLetterOrDigit() }.lowercase()
}
