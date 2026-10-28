package com.jizhang.app.ui.pet

import com.jizhang.app.data.local.entity.LocalTransaction
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.util.Locale
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/** 建议的性质：决定卡片上的排序先后与配色 */
enum class PetTipKind {
    /** 预警：钱正在流出，且幅度已经不正常 */
    ALERT,

    /** 省钱：找到了明确的节流切口 */
    SAVING,

    /** 正向反馈：做得好的地方 */
    CHEER,

    /** 记账习惯 */
    HABIT
}

/** 一条建议。文案在引擎里就拼好，界面只负责排 */
data class PetTip(val kind: PetTipKind, val text: String)

/**
 * 某个维度上的金额切片。
 *
 * @param amount 金额（分）；在 [PetBillFacts.topRising] 里指的是「比上月同期多花了多少」
 * @param ratio 占比或增幅（0f..1f，增幅可以大于 1f）
 */
data class PetSlice(
    val name: String,
    val amount: Long,
    val ratio: Float = 0f
)

/**
 * 宠物「看账」得到的事实表。
 *
 * 全部由本地流水算出来：**每句话都能对着某笔账复盘**，不猜测、不联网、不接大模型。
 * 结论不含价值判断，只回答三个问题：本月花到哪了、比之前快了多少、其中最该动的一刀在哪里。
 */
data class PetBillFacts(
    val today: LocalDate,
    /** 本月已过天数（含今天） */
    val elapsedDays: Int,
    /** 本月总天数 */
    val daysInMonth: Int,
    /** 本月收入（分，不含转账） */
    val monthIncome: Long = 0L,
    /** 本月支出（分，不含转账） */
    val monthExpense: Long = 0L,
    /** 上月整月支出，用来给「月底会花到多少」做参照 */
    val prevMonthExpense: Long = 0L,
    /** 上月同期（1 号到同样的天数）支出，环比只跟它比才公平 */
    val prevPaceExpense: Long = 0L,
    /** 本月支出笔数 */
    val expenseCount: Int = 0,
    /** 本月有流水的天数 */
    val activeDays: Int = 0,
    /** 今天记了几笔 */
    val todayCount: Int = 0,
    /** 本月支出最多的分类 */
    val topCategory: PetSlice? = null,
    /** 比上月同期涨得最猛的分类，[PetSlice.amount] 是增量 */
    val topRising: PetSlice? = null,
    /** 本月最大的一笔支出 */
    val biggest: PetSlice? = null,
    /** 深夜（22 点后到次日 5 点前）账单笔数 */
    val lateNightCount: Int = 0,
    /** 深夜账单合计（分） */
    val lateNightAmount: Long = 0L,
    /** 连续记账天数 */
    val streakDays: Int = 0,
    /** 整个账本的流水笔数：用来区分「从没记过」和「只是本月没记」 */
    val everCount: Int = 0
)

/** 一次看账的结果 */
data class PetInsight(
    val mood: PetMood,
    val tips: List<PetTip>,
    /** 一行客观近况（日均 / 剩余天数 / 连续天数），放在名字下面 */
    val statusLine: String
) {
    companion object {
        /** 账还没读出来的初始值：此时卡片不该渲染气泡 */
        val EMPTY = PetInsight(PetMood.NORMAL, emptyList(), "")
    }
}

/**
 * 宠物的本地理财建议引擎（纯 Kotlin，可单测，不联网）。
 *
 * 设计取舍：
 * - **规则引擎而不是黑箱**：每条建议背后都是一条讲得清楚理由的规则，
 *   用户追问「凭什么」时必须答得上来 —— 钱的用途上，说不清出处的结论没有说服力；
 * - **只对账单说话**：输入是 [factsOf] 算出来的事实表，规则之间不互相依赖，
 *   既方便单测，也方便以后加/删某一条而不牵动其他；
 * - **宁缺毋滥**：样本不足（月初、上周同期没有参照）就不输出对比结论，
 *   与其给一句「可能超支」的废话，不如闭嘴。
 */
object PetAdvice {

    private const val EXPENSE = 1
    private const val INCOME = 2
    private const val TRANSFER = 3

    /** 至少过了这么多天才敢谈「这个月比上月花得快」，月初两笔说明不了问题 */
    private const val PACE_MIN_DAYS = 4

    /** 预测月底比上月多花超过这个比例才预警 */
    private const val FORECAST_ALERT_RATIO = 1.15

    /** 预测月底比上月少花到这个比例以内才夸 */
    private const val FORECAST_CHEER_RATIO = 0.85

    /** 分类要同时满足「占比」和「绝对额」才值得单独点名：小额高占比没意义 */
    private const val TOP_CATEGORY_RATIO = 0.4f
    private const val TOP_CATEGORY_MIN_FEN = 30_000L

    /** 单笔大额：占比与绝对额双重门槛 */
    private const val BIG_TX_RATIO = 0.25f
    private const val BIG_TX_MIN_FEN = 50_000L

    /** 深夜账单：够多才值得说 */
    private const val NIGHT_MIN_COUNT = 3
    private const val NIGHT_FROM_HOUR = 22
    private const val NIGHT_TO_HOUR = 5

    /** 分类环比：参照额与增量都要够，再加 20% 的涨幅门槛，避免把 10 块的波动当趋势 */
    private const val RISING_MIN_BASE = 10_000L
    private const val RISING_MIN_DELTA = 10_000L
    private const val RISING_MIN_RATIO = 0.2

    /** 连续记账到这个天数值得夸 */
    private const val STREAK_CHEER_DAYS = 7
    private const val STREAK_HINT_DAYS = 3

    /** 本月漏记超过这么多天才提醒 */
    private const val MISSING_DAYS_MIN = 3

    /** 卡片里最多给几条：再多没人看，改成「换一句」轮着看 */
    private const val MAX_TIPS = 5

    /**
     * 从账本流水里算出看账要用的事实。
     *
     * @param categoryNames 分类 id → 名称；没传到的分类按「未分类」处理
     *                      （分类是按账单当时挂的那一级统计，不做父分类归并）
     * @param today 注入的「今天」：所有按时间对齐的比较都以它为基准，测试里必须写死
     * @param streakDays 连续记账天数，由 DataStore 侧的宠物状态提供，不属于流水本身
     */
    fun factsOf(
        transactions: List<LocalTransaction>,
        categoryNames: Map<Long, String>,
        today: LocalDate = LocalDate.now(),
        streakDays: Int = 0,
        zone: ZoneId = ZoneId.systemDefault()
    ): PetBillFacts {
        // 转账是账户之间挪钱，不是收支；墓碑行也不该参与任何计算
        val bills = transactions.filterNot { it.pendingDelete || it.type == TRANSFER }

        val monthStart = today.withDayOfMonth(1)
        val monthStartMs = millisOf(monthStart, zone)
        val monthEndMs = millisOf(monthStart.plusMonths(1), zone)
        val prevStart = monthStart.minusMonths(1)
        val prevStartMs = millisOf(prevStart, zone)

        val elapsedDays = today.dayOfMonth
        val daysInMonth = YearMonth.from(monthStart).lengthOfMonth()
        // 上月同期：上月 1 号到「与本月同样的天数」，天数不够就取满月（比如今天是 31 号而上月只有 30 天）
        val paceEndMs = millisOf(
            prevStart.plusDays(minOf(elapsedDays, YearMonth.from(prevStart).lengthOfMonth()).toLong()),
            zone
        )

        val monthTx = bills.filter { it.tradeTime in monthStartMs until monthEndMs }
        val prevTx = bills.filter { it.tradeTime in prevStartMs until monthStartMs }
        val paceTx = prevTx.filter { it.tradeTime < paceEndMs }
        val todayTx = monthTx.filter { dateOf(it.tradeTime, zone) == today }

        val monthExpenseTx = monthTx.filter { it.type == EXPENSE }
        val monthExpense = monthExpenseTx.sumOf { it.amount }
        val night = monthExpenseTx.filter { isLateNight(it.tradeTime, zone) }

        val curByCategory = categoryTotals(monthExpenseTx, categoryNames)
        val paceByCategory = categoryTotals(paceTx.filter { it.type == EXPENSE }, categoryNames)

        return PetBillFacts(
            today = today,
            elapsedDays = elapsedDays,
            daysInMonth = daysInMonth,
            monthIncome = monthTx.filter { it.type == INCOME }.sumOf { it.amount },
            monthExpense = monthExpense,
            prevMonthExpense = prevTx.filter { it.type == EXPENSE }.sumOf { it.amount },
            prevPaceExpense = paceTx.filter { it.type == EXPENSE }.sumOf { it.amount },
            expenseCount = monthExpenseTx.size,
            activeDays = monthTx.map { dateOf(it.tradeTime, zone) }.distinct().size,
            todayCount = todayTx.size,
            topCategory = curByCategory
                .maxByOrNull { it.value }
                ?.let { (name, amount) -> PetSlice(name, amount, ratio(amount, monthExpense)) },
            topRising = risingCategory(curByCategory, paceByCategory),
            biggest = monthExpenseTx
                .maxByOrNull { it.amount }
                ?.let {
                    PetSlice(
                        name = categoryNames[it.categoryId] ?: "未分类",
                        amount = it.amount,
                        ratio = ratio(it.amount, monthExpense)
                    )
                },
            lateNightCount = night.size,
            lateNightAmount = night.sumOf { it.amount },
            streakDays = streakDays,
            everCount = bills.size
        )
    }

    /**
     * 从事实表生成建议。
     *
     * 顺序即优先级：先看风险（钱流出的方向不对），再找可省的切口，
     * 然后是做得好的地方，最后才轮到记账习惯 —— 用户打开卡片是想处理问题，不是听夸奖。
     */
    fun analyze(facts: PetBillFacts): PetInsight {
        // 本月还没有任何流水：不比较、不预测，给一句入口就够了
        if (facts.expenseCount == 0 && facts.monthIncome == 0L) {
            val firstTip = if (facts.everCount == 0) {
                PetTip(PetTipKind.HABIT, "还没有任何账单，记第一笔它就有得分析了～")
            } else {
                PetTip(PetTipKind.HABIT, "本月还没开账：先把今天的补上，它才知道钱去哪了")
            }
            return PetInsight(
                mood = PetMood.NORMAL,
                tips = listOf(firstTip),
                statusLine = "本月还没有账"
            )
        }

        val tips = buildList {
            addAll(alerts(facts))
            addAll(savings(facts))
            addAll(cheers(facts))
            addAll(habits(facts))
        }
            .take(MAX_TIPS)
            // 理论上每条规则都可能不触发（数据量太少），留一句兜底，别让卡片空着
            .ifEmpty {
                listOf(PetTip(PetTipKind.HABIT, "账还不够多，再多记几天它就能看出你的花钱趋势"))
            }
        return PetInsight(mood = moodOf(facts, tips), tips = tips, statusLine = statusLine(facts))
    }

    /* ==================== 规则 ==================== */

    /** 预警类：钱已经在以不正常的速度流出 */
    private fun alerts(f: PetBillFacts): List<PetTip> = buildList {
        val balance = f.monthIncome - f.monthExpense
        // 只有记了收入才有「入不敷出」这回事：本月没记工资不代表真的没收入
        if (f.monthIncome > 0 && balance < 0) {
            add(
                PetTip(
                    PetTipKind.ALERT,
                    "本月支出 ${yuan(f.monthExpense)} 已经超过收入 ${yuan(f.monthIncome)}，" +
                        "倒挂 ${yuan(-balance)}"
                )
            )
        }

        // 按当前日均推演月底：比上月多出一截才预警
        if (f.elapsedDays >= PACE_MIN_DAYS && f.monthExpense > 0 && f.prevMonthExpense > 0) {
            val forecast = forecastOf(f)
            if (forecast > f.prevMonthExpense * FORECAST_ALERT_RATIO) {
                add(
                    PetTip(
                        PetTipKind.ALERT,
                        "照这几天日均 ${yuan(dailyOf(f))} 的速度，月底要花到 ${yuan(forecast)}，" +
                            "比上月多 ${yuan(forecast - f.prevMonthExpense)}"
                    )
                )
            }
        }

        // 单个分类突然变贵：这是最具体、最容易改的一条
        f.topRising?.let { rising ->
            add(
                PetTip(
                    PetTipKind.ALERT,
                    "「${rising.name}」比上月同期多花了 ${yuan(rising.amount)}（${pct(rising.ratio)}），" +
                        "本月已经 ${yuan(f.monthExpense)}"
                )
            )
        }
    }

    /** 省钱类：明确的节流切口 */
    private fun savings(f: PetBillFacts): List<PetTip> = buildList {
        f.topCategory?.let { top ->
            if (top.ratio >= TOP_CATEGORY_RATIO && top.amount >= TOP_CATEGORY_MIN_FEN) {
                add(
                    PetTip(
                        PetTipKind.SAVING,
                        "「${top.name}」占了本月支出的 ${pct(top.ratio)}（${yuan(top.amount)}），" +
                            "想省钱先从它下手"
                    )
                )
            }
        }

        f.biggest?.let { big ->
            if (big.ratio >= BIG_TX_RATIO && big.amount >= BIG_TX_MIN_FEN) {
                add(
                    PetTip(
                        PetTipKind.SAVING,
                        "本月最大一笔是「${big.name}」${yuan(big.amount)}，" +
                            "一笔就占了本月支出的 ${pct(big.ratio)}"
                    )
                )
            }
        }

        if (f.lateNightCount >= NIGHT_MIN_COUNT && f.lateNightAmount > 0) {
            add(
                PetTip(
                    PetTipKind.SAVING,
                    "本月有 ${f.lateNightCount} 笔是 22 点以后记的，共 ${yuan(f.lateNightAmount)}，" +
                        "深夜单最容易省"
                )
            )
        }
    }

    /** 正向反馈：把「花的少 / 存得多 / 坚持记」讲成具体的钱 */
    private fun cheers(f: PetBillFacts): List<PetTip> = buildList {
        val balance = f.monthIncome - f.monthExpense
        if (f.monthIncome > 0 && balance > 0) {
            val rate = (balance.toDouble() / f.monthIncome * 100).roundToInt()
            add(
                PetTip(
                    PetTipKind.CHEER,
                    "本月结余 ${yuan(balance)}，收入的 $rate% 存下来了"
                )
            )
        }

        // 月底预测比上月省：提前告诉用户，比月末算<｜hy_place▁holder▁no▁813｜>更有用
        if (f.elapsedDays >= PACE_MIN_DAYS && f.monthExpense > 0 && f.prevMonthExpense > 0) {
            val forecast = forecastOf(f)
            if (forecast < f.prevMonthExpense * FORECAST_CHEER_RATIO) {
                add(
                    PetTip(
                        PetTipKind.CHEER,
                        "照这速度月底约 ${yuan(forecast)}，比上月省下 ${yuan(f.prevMonthExpense - forecast)}"
                    )
                )
            }
        }

        if (f.streakDays >= STREAK_CHEER_DAYS) {
            add(PetTip(PetTipKind.CHEER, "已经连续记账 ${f.streakDays} 天了，稳住"))
        }
    }

    /** 习惯类：让下一次记账更容易发生 */
    private fun habits(f: PetBillFacts): List<PetTip> = buildList {
        if (f.todayCount == 0) {
            add(PetTip(PetTipKind.HABIT, "今天还没记账，趁记得住赶紧补一笔"))
        }

        val missing = f.elapsedDays - f.activeDays
        if (missing >= MISSING_DAYS_MIN) {
            add(
                PetTip(
                    PetTipKind.HABIT,
                    "本月 ${f.elapsedDays} 天里只有 ${f.activeDays} 天有记录，" +
                        "缺的 $missing 天记得补上"
                )
            )
        }

        if (f.streakDays in STREAK_HINT_DAYS until STREAK_CHEER_DAYS) {
            add(PetTip(PetTipKind.HABIT, "已经连记 ${f.streakDays} 天，今天别忘了收尾"))
        }
    }

    /* ==================== 派生 ==================== */

    /** 本月日均支出（按已过天数摊，含今天） */
    private fun dailyOf(f: PetBillFacts): Long = f.monthExpense / f.elapsedDays.coerceAtLeast(1)

    /** 按当前日均推演到月底的支出总额 */
    private fun forecastOf(f: PetBillFacts): Long = dailyOf(f) * f.daysInMonth

    private fun moodOf(f: PetBillFacts, tips: List<PetTip>): PetMood = when {
        tips.any { it.kind == PetTipKind.ALERT } -> PetMood.WORRIED
        f.streakDays >= STREAK_CHEER_DAYS && f.monthIncome - f.monthExpense > 0 -> PetMood.EXCITED
        f.monthIncome - f.monthExpense > 0 -> PetMood.HAPPY
        else -> PetMood.NORMAL
    }

    private fun statusLine(f: PetBillFacts): String {
        val parts = mutableListOf<String>()
        if (f.monthExpense > 0) {
            parts += "本月日均支出 ${yuan(dailyOf(f))}"
            val left = f.daysInMonth - f.elapsedDays
            if (left > 0) {
                parts += "还剩 $left 天"
            }
        }
        if (f.streakDays > 0) {
            parts += "连续记账 ${f.streakDays} 天"
        }
        return parts.joinToString(" · ")
    }

    /**
     * 比上月同期涨得最猛的分类。
     *
     * 只认有参照的分类（上月同期也花过），增量要同时过「绝对额」和「涨幅」两道门槛 ——
     * 参照额很小时，10% 的涨幅也可能是随手一笔。
     */
    private fun risingCategory(cur: Map<String, Long>, prev: Map<String, Long>): PetSlice? =
        cur.mapNotNull { (name, amount) ->
            val base = prev[name] ?: return@mapNotNull null
            val delta = amount - base
            val enough = base >= RISING_MIN_BASE &&
                delta >= RISING_MIN_DELTA &&
                delta >= (base * RISING_MIN_RATIO).roundToLong()
            if (!enough) null else PetSlice(name, delta, (delta.toDouble() / base).toFloat())
        }.maxByOrNull { it.amount }

    private fun categoryTotals(
        items: List<LocalTransaction>,
        names: Map<Long, String>
    ): Map<String, Long> {
        val totals = mutableMapOf<String, Long>()
        items.forEach { tx ->
            val name = tx.categoryId?.let { names[it] } ?: "未分类"
            totals[name] = (totals[name] ?: 0L) + tx.amount
        }
        return totals
    }

    private fun millisOf(date: LocalDate, zone: ZoneId): Long =
        date.atStartOfDay(zone).toInstant().toEpochMilli()

    private fun dateOf(millis: Long, zone: ZoneId): LocalDate =
        Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()

    private fun isLateNight(tradeTime: Long, zone: ZoneId): Boolean {
        val hour = Instant.ofEpochMilli(tradeTime).atZone(zone).hour
        return hour >= NIGHT_FROM_HOUR || hour < NIGHT_TO_HOUR
    }

    private fun ratio(part: Long, total: Long): Float =
        if (total <= 0L) 0f else part.toDouble().div(total).toFloat()

    private fun pct(value: Float): String = "${(value * 100).roundToInt()}%"

    /**
     * 分 → 「¥1,234.00」。
     *
     * 不复用 ui.common 的 `formatAmount`：它持有的是静态 DecimalFormat，
     * 而这个引擎可能在后台线程被调用（ViewModel 的派生计算），静态方法会互相污染。
     */
    private fun yuan(fen: Long): String = String.format(Locale.CHINA, "¥%,.2f", fen / 100.0)
}
