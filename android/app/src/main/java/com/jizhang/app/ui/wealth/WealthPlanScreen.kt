package com.jizhang.app.ui.wealth

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jizhang.app.data.repository.LeverageOption
import com.jizhang.app.data.repository.StressResult
import com.jizhang.app.data.repository.WealthPlan
import com.jizhang.app.ui.common.AppFullScreenDialog
import com.jizhang.app.ui.common.money
import com.jizhang.app.ui.common.safeDialogContent
import com.jizhang.app.ui.theme.AppColors
import java.time.Year
import kotlin.math.roundToInt

/**
 * 财务自由页。
 * <p>
 * 结构（从上到下就是思考顺序）：
 * 1. **目标与进度**：FI 数字、还差多少、还能撑几个月、Coast FI；结论用概率说，不给单一年份；
 * 2. **资本路径**：P10–P90 扇形带 + 中位数 + 算术均值 + FI 目标线，先看"大概会在哪一年穿过"；
 * 3. **增长解剖**：本金与复利两条腿、飞轮启动年 —— 回答"第几年换挡"（结构问题，确定性骨架）；
 * 4. **达标年份分布**：直方图 —— 回答"会不会永远达不到"（风险画像，中位数会掩盖双峰）；
 * 5. **假设**：提取率 / 实际收益率 / 目标年数，拖动即时重算（假设落库，下次进来还在）；
 * 6. **杠杆榜 / 压力测试**：哪根旋钮最值钱，以及坏运气会让你退多少；
 * 7. **口径**：年支出从哪来、样本几个月、剔除了什么 —— 用户要能自己复核这些数字。
 * <p>
 * 页面遵循全屏覆盖层约定：调用点在 [com.jizhang.app.ui.main.MainScreen] 根部，
 * 顶栏用默认 windowInsets，内容区自己 `safeDialogContent()`。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WealthPlanScreen(
    bookId: Long,
    hideAmounts: Boolean = false,
    onDismiss: () -> Unit,
    viewModel: WealthViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(bookId) { viewModel.bind(bookId) }

    AppFullScreenDialog(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.fillMaxSize().safeDialogContent()) {
            TopAppBar(
                title = { Text("财务自由", fontWeight = FontWeight.Bold) },
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
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                state.plan?.let { plan ->
                    HeroCard(plan = plan, hideAmounts = hideAmounts)
                    if (plan.fanYears.size > 1) {
                        FanCard(plan = plan, hideAmounts = hideAmounts)
                    }
                    if (plan.anatomy.contributed.size > 1) {
                        AnatomyCard(plan = plan, hideAmounts = hideAmounts)
                    }
                    if (plan.reachedHistogram.size > 1) {
                        ReachedCard(plan = plan)
                    }
                    if (!plan.hasEnoughData) {
                        HintCard(
                            title = "数据还不够",
                            body = "账本里只有 ${plan.sampleMonths} 个月的记录。先记满 3 个月，年支出与储蓄率才站得住脚；" +
                                "现在的数字可以看趋势，但别当成决定。"
                        )
                    }
                    // 滑块读 state 而不是 plan：重算有 120ms 防抖，
                    // 读 plan 会在算完之前把滑块拽回旧值，拖起来像卡住
                    AssumptionCard(
                        withdrawalRate = state.withdrawalRate,
                        realReturn = state.realReturn,
                        targetYears = state.targetYears,
                        onWithdrawalRate = viewModel::setWithdrawalRate,
                        onRealReturn = viewModel::setRealReturn,
                        onTargetYears = viewModel::setTargetYears
                    )
                    LeverageCard(options = state.leverage)
                    StressCard(items = state.stress)
                    BasisCard(plan = plan, hideAmounts = hideAmounts)
                }
                if (state.plan == null) {
                    HintCard(
                        title = if (state.loading) "正在算…" else "算不出来",
                        body = state.error ?: "账本里还没有可用于估算的支出记录。"
                    )
                }
                DisclaimerCard()
            }
        }
    }
}

@Composable
private fun HeroCard(plan: WealthPlan, hideAmounts: Boolean) {
    CardBox {
        Text(
            text = "FI 资产 · 按 ${percentText(plan.withdrawalRate)} 提取率",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = money(plan.fiNumberFen, hideAmounts),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(10.dp))
        LinearProgressIndicator(
            progress = { (plan.progress / 1.0).coerceIn(0.0, 1.0).toFloat() },
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp)),
            color = AppColors.accent,
            trackColor = MaterialTheme.colorScheme.surfaceVariant
        )
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "已走 ${percentText(plan.progress)}",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = if (plan.gapFen > 0L) {
                    "还差 ${money(plan.gapFen, hideAmounts)}"
                } else {
                    "已达标"
                },
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(Modifier.height(12.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
        Spacer(Modifier.height(12.dp))

        // 结论用概率说：不承诺具体年份，给中位数与区间
        val median = plan.medianYears
        val p10 = plan.p10Years
        val p90 = plan.p90Years
        val now = Year.now().value
        Text(
            text = when {
                median == null -> "按当前节奏，${plan.maxYears} 年内达标的概率不足一半"
                median == 0 -> "已经越过 FI 线，理论上可以让钱替你工作"
                else -> "50% 概率在 ${now + median} 年前后达标"
            },
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold
        )
        if (p10 != null && p90 != null && median != null && median > 0) {
            Spacer(Modifier.height(2.dp))
            Text(
                text = "区间：${now + p10} 年（顺利）～ ${now + p90} 年（不顺）",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        plan.successProbability?.let { probability ->
            Spacer(Modifier.height(2.dp))
            Text(
                text = "${plan.targetYears ?: plan.maxYears} 年内达标概率 ${percentText(probability)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        plan.freedomMonths?.let { months ->
            Spacer(Modifier.height(6.dp))
            Text(
                text = "自由度：净资产可覆盖 ${months.roundToInt()} 个月支出",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Coast FI：从「必须继续攒」到「工作可选」的那条线
        plan.coastFiNumberFen?.let { coast ->
            Spacer(Modifier.height(12.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Coast FI",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "不再追加投入、只靠复利到期达标所需的今天金额",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.width(12.dp))
                Text(
                    text = money(coast, hideAmounts),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = if (plan.netWorthFen >= coast) {
                    "已越过 · 之后的投入决定的是「能多早」，而不是「能不能」"
                } else {
                    "还差 ${money(coast - plan.netWorthFen, hideAmounts)}"
                },
                style = MaterialTheme.typography.labelSmall,
                color = if (plan.netWorthFen >= coast) AppColors.accent else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * 资本路径（扇形图）。
 * <p>
 * 三条线：P10 / P50 / P90；阴影是 P10–P90 的区间，虚线是 FI 目标。
 * 纵轴在隐私模式下换成「× 目标倍数」——形状仍然可读，但看不出金额量级。
 */
@Composable
private fun FanCard(plan: WealthPlan, hideAmounts: Boolean) {
    val accent = AppColors.accent
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    // 均值可能高于 P90：Jensen 不等式下算术均值天然高于中位数与上分位，
    // 不算进纵轴范围的话，那条线会被裁在顶边上，看起来像"没画"
    val yMax = maxOf(
        plan.fanP90.max(),
        plan.fanMean.maxOrNull() ?: 0L,
        plan.fiNumberFen
    ).coerceAtLeast(1L)
    val lastYear = plan.fanYears.last()

    CardBox {
        Text("资本路径", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(2.dp))
        Text(
            text = "阴影 = P10–P90 区间，实线 = 中位数，灰色虚线 = 算术均值，横向虚线 = FI 目标",
            style = MaterialTheme.typography.labelSmall,
            color = muted
        )
        Spacer(Modifier.height(10.dp))

        Row(modifier = Modifier.fillMaxWidth().height(150.dp)) {
            Column(
                modifier = Modifier.width(46.dp).fillMaxHeight(),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = if (hideAmounts) {
                        "${multipleText(yMax, plan.fiNumberFen)}×"
                    } else {
                        compactYuan(yMax)
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = muted
                )
                Text("0", style = MaterialTheme.typography.labelSmall, color = muted)
            }
            Spacer(Modifier.width(6.dp))
            Canvas(modifier = Modifier.weight(1f).fillMaxHeight()) {
                val w = size.width
                val h = size.height
                if (w <= 0f || h <= 0f) return@Canvas
                val lastIndex = plan.fanYears.lastIndex.coerceAtLeast(1)
                fun x(index: Int) = w * index / lastIndex
                fun y(value: Long) = h - (value.toDouble() / yMax).coerceIn(0.0, 1.0).toFloat() * h

                // 区间带：先沿 P90 正向走，再沿 P10 反向回来
                val band = Path()
                plan.fanP90.forEachIndexed { index, value ->
                    if (index == 0) band.moveTo(x(index), y(value)) else band.lineTo(x(index), y(value))
                }
                for (index in plan.fanP10.indices.reversed()) {
                    band.lineTo(x(index), y(plan.fanP10[index]))
                }
                band.close()
                drawPath(band, color = accent.copy(alpha = 0.16f))

                // FI 目标：一条虚线，越过它就意味着"可以不再为钱工作"
                if (plan.fiNumberFen > 0L) {
                    drawLine(
                        color = muted.copy(alpha = 0.7f),
                        start = Offset(0f, y(plan.fiNumberFen)),
                        end = Offset(w, y(plan.fiNumberFen)),
                        strokeWidth = 1.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f))
                    )
                }

                val median = Path()
                plan.fanP50.forEachIndexed { index, value ->
                    if (index == 0) median.moveTo(x(index), y(value)) else median.lineTo(x(index), y(value))
                }
                drawPath(median, color = accent, style = Stroke(width = 2.dp.toPx()))

                // 均值路径：与中位数取自同一批路径，两条线之间的空隙就是「波动税」。
                // 按平均收益规划出来的钱，一半概率拿不到——这条线必须画出来，
                // 否则用户会以为中位数那条"算少了"。
                if (plan.fanMean.size == plan.fanP50.size) {
                    val mean = Path()
                    plan.fanMean.forEachIndexed { index, value ->
                        if (index == 0) mean.moveTo(x(index), y(value)) else mean.lineTo(x(index), y(value))
                    }
                    drawPath(
                        mean,
                        color = muted.copy(alpha = 0.85f),
                        style = Stroke(
                            width = 1.5.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 6f))
                        )
                    )
                }
            }
        }

        Spacer(Modifier.height(6.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            // 与画布左边界对齐：左边是纵轴刻度列（46dp）+ 6dp 间距
            Spacer(Modifier.width(52.dp))
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                val ticks = listOf(0, lastYear / 4, lastYear / 2, lastYear * 3 / 4, lastYear)
                ticks.distinct().forEach { year ->
                    Text(
                        text = if (year == 0) "现在" else "$year 年",
                        style = MaterialTheme.typography.labelSmall,
                        color = muted
                    )
                }
            }
        }

        // 裂口 = 均值 / 中位数 − 1，理论上等于 e^{σ²T/2} − 1。
        // 两个末点来自同一批路径，所以这是实测值，不是假设值。
        val meanEnd = plan.fanMean.lastOrNull() ?: 0L
        val medianEnd = plan.fanP50.lastOrNull() ?: 0L
        if (meanEnd > 0L && medianEnd > 0L) {
            val gap = meanEnd.toDouble() / medianEnd - 1.0
            if (gap > 0.02) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "均值比中位数高 ${percentText(gap)}：这部分钱只在「每年都恰好赚到平均收益」的" +
                        "那条路径上存在，别把它算进计划。",
                    style = MaterialTheme.typography.labelSmall,
                    color = muted
                )
            }
        }
    }
}

/**
 * 增长解剖：把净资产拆成「你投进去的」和「钱自己长出来的」两条腿。
 * <p>
 * 与资本路径不同，这张图画的是**确定性骨架**（收益恰好等于设定值、支出不增长），
 * 因为它回答的是结构问题而不是概率问题：第几年开始，复利的年增量会超过当年结余。
 * 交叉点由 \(\ln\frac{2S}{rW_0+S}/\ln(1+r)\) 给出；\(W_0=0\) 时退化为 \(\ln2/\ln(1+r)\) ——
 * 与存多少钱无关，只由收益率决定（与放射衰变的半衰期同构）。
 */
@Composable
private fun AnatomyCard(plan: WealthPlan, hideAmounts: Boolean) {
    val anatomy = plan.anatomy
    val contributed = anatomy.contributed
    val compounded = anatomy.compounded
    if (contributed.size < 2 || contributed.size != compounded.size) return

    val accent = AppColors.accent
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val lastIndex = contributed.lastIndex
    // 复利可能为负（早期亏损），画的时候按 0 起算，别让浅色层穿到深色层下面
    val peak = contributed.indices.maxOf { contributed[it] + compounded[it].coerceAtLeast(0L) }
    if (peak <= 0L) return

    CardBox {
        Text("增长解剖", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(2.dp))
        Text(
            text = "深色 = 你累计投进去的本金，浅色 = 这些钱自己长出来的部分（按设定收益，不含波动）",
            style = MaterialTheme.typography.labelSmall,
            color = muted
        )
        Spacer(Modifier.height(10.dp))

        Row(modifier = Modifier.fillMaxWidth().height(150.dp)) {
            Column(
                modifier = Modifier.width(46.dp).fillMaxHeight(),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = if (hideAmounts) {
                        "${multipleText(peak, plan.fiNumberFen)}×"
                    } else {
                        compactYuan(peak)
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = muted
                )
                Text("0", style = MaterialTheme.typography.labelSmall, color = muted)
            }
            Spacer(Modifier.width(6.dp))
            Canvas(modifier = Modifier.weight(1f).fillMaxHeight()) {
                val w = size.width
                val h = size.height
                if (w <= 0f || h <= 0f) return@Canvas
                fun x(index: Int) = w * index / lastIndex
                fun y(value: Long) = h - (value.toDouble() / peak).coerceIn(0.0, 1.0).toFloat() * h

                // 底层：累计投入（直线）
                val invested = Path()
                invested.moveTo(x(0), h)
                contributed.forEachIndexed { index, value -> invested.lineTo(x(index), y(value)) }
                invested.lineTo(x(lastIndex), h)
                invested.close()
                drawPath(invested, color = muted.copy(alpha = 0.22f))

                // 上层：累计复利。先沿「投入 + 复利」正向走，再沿「投入」反向回来
                val growth = Path()
                contributed.indices.forEach { index ->
                    val total = contributed[index] + compounded[index].coerceAtLeast(0L)
                    if (index == 0) growth.moveTo(x(index), y(total)) else growth.lineTo(x(index), y(total))
                }
                for (index in contributed.indices.reversed()) {
                    growth.lineTo(x(index), y(contributed[index]))
                }
                growth.close()
                drawPath(growth, color = accent.copy(alpha = 0.42f))

                // 飞轮启动年：两条腿的发力点从这一天起换人
                anatomy.flywheelYear?.let { year ->
                    if (year in 0..lastIndex) {
                        drawLine(
                            color = accent,
                            start = Offset(x(year), 0f),
                            end = Offset(x(year), h),
                            strokeWidth = 1.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 5f))
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(6.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            Spacer(Modifier.width(52.dp))
            Row(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.SpaceBetween) {
                listOf(0, lastIndex / 2, lastIndex).distinct().forEach { year ->
                    Text(
                        text = if (year == 0) "现在" else "$year 年",
                        style = MaterialTheme.typography.labelSmall,
                        color = muted
                    )
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        Text(
            text = storyOf(plan, hideAmounts),
            style = MaterialTheme.typography.labelSmall,
            color = muted
        )
    }
}

/** 解剖卡的结论句：把「第几年换挡」翻译成人话，并给出临界资产 */
private fun storyOf(plan: WealthPlan, hideAmounts: Boolean): String {
    val anatomy = plan.anatomy
    val year = anatomy.flywheelYear
    return when {
        year == null && plan.annualSavingFen <= 0L ->
            "年结余为负，复利永远追不上储蓄：先把结余转正，这张图才有意义。"
        year == null ->
            "按当前假设，${plan.maxYears} 年内复利都追不上你的储蓄速度。"
        year == 0 ->
            "复利已经超过你的储蓄速度：现在一年的账面增长，比新存进来的钱还多。"
        else -> {
            // 复利的年增量 = 相邻两年的累计复利之差（把复利当期新增的钱单独拎出来）
            val yearly = anatomy.compounded.getOrNull(year)?.let { now ->
                now - (anatomy.compounded.getOrNull(year - 1) ?: 0L)
            } ?: 0L
            "第 $year 年起，一年的复利能长出 ${money(yearly, hideAmounts)}，" +
                "超过你一年的结余 ${money(plan.annualSavingFen, hideAmounts)}——之前靠自律，之后靠时间。" +
                "这个换挡点只由收益率决定（起点为 0 时恰好是 ln2/ln(1+r) 年），与你存多少钱无关。"
        }
    }
}

/**
 * 达标年份分布。
 * <p>
 * 只给 P10/P50/P90 三个数字会掩盖双峰：常见形态是「一大半在 15 年内，一小撮永远达不到」，
 * 单一中位数会把后者完全抹掉。柱状图 + 永不达标的比例，才是完整的风险画像。
 */
@Composable
private fun ReachedCard(plan: WealthPlan) {
    val raw = plan.reachedHistogram
    if (raw.size < 2) return
    val total = plan.simulations.coerceAtLeast(1)
    // 60 年的窗口画 60 根柱子会糊成一片：合并成不超过 24 根
    val step = ((raw.size + 23) / 24).coerceAtLeast(1)
    val bins = raw.chunked(step) { it.sum() }
    val peak = bins.maxOrNull()?.coerceAtLeast(1) ?: 1
    val accent = AppColors.accent
    val muted = MaterialTheme.colorScheme.onSurfaceVariant

    CardBox {
        Text("达标年份分布", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(2.dp))
        Text(
            text = "柱高 = 落在这一年的比例；这根柱子比中位数更能说明「到底有多稳」",
            style = MaterialTheme.typography.labelSmall,
            color = muted
        )
        Spacer(Modifier.height(10.dp))

        Canvas(modifier = Modifier.fillMaxWidth().height(110.dp)) {
            val w = size.width
            val h = size.height
            val slot = w / bins.size
            val gap = (slot * 0.18f).coerceAtMost(2.dp.toPx())
            bins.forEachIndexed { index, count ->
                if (count <= 0) return@forEachIndexed
                val barHeight = (count.toDouble() / peak).toFloat() * h
                drawRect(
                    color = accent.copy(alpha = 0.72f),
                    topLeft = Offset(index * slot + gap, h - barHeight),
                    size = Size(slot - gap * 2, barHeight)
                )
            }
            // 中位数落点：整张图最该被一眼看到的位置
            plan.medianYears?.let { median ->
                val index = (median / step).coerceIn(0, bins.lastIndex)
                val center = index * slot + slot / 2f
                drawLine(
                    color = muted.copy(alpha = 0.8f),
                    start = Offset(center, 0f),
                    end = Offset(center, h),
                    strokeWidth = 1.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 4f))
                )
            }
        }

        Spacer(Modifier.height(4.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            val marks = listOf(0, bins.lastIndex / 2, bins.lastIndex).distinct()
            marks.forEach { index ->
                Text(
                    text = if (index == 0) "现在" else "${index * step} 年",
                    style = MaterialTheme.typography.labelSmall,
                    color = muted
                )
            }
        }

        Spacer(Modifier.height(8.dp))
        val reachedRatio = bins.sum().toDouble() / total
        Text(
            text = buildString {
                // 中位数为 null 表示「一半以上的路径在窗口内没达到」，不能拿 maxYears 冒充
                append(plan.medianYears?.let { "中位数第 $it 年" } ?: "过半路径在 ${plan.maxYears} 年内没达到")
                append("；${plan.maxYears} 年内达标 ${percentText(reachedRatio)}")
                append("；始终没达到 ${percentText(plan.neverReaches)}")
            },
            style = MaterialTheme.typography.labelSmall,
            color = muted
        )
    }
}

@Composable
private fun AssumptionCard(
    withdrawalRate: Double,
    realReturn: Double,
    targetYears: Int,
    onWithdrawalRate: (Double) -> Unit,
    onRealReturn: (Double) -> Unit,
    onTargetYears: (Int) -> Unit
) {
    CardBox {
        Text("假设", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(2.dp))
        Text(
            text = "拖动即时重算；假设会记住，不用每次重设",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        SliderRow(
            label = "安全提取率",
            value = percentText(withdrawalRate),
            hint = "每年从本金里取出多少。4% 来自 30 年期的历史回测，越保守越低",
            sliderValue = withdrawalRate.toFloat(),
            range = 0.025f..0.05f,
            steps = 9,
            onChange = { onWithdrawalRate(it.toDouble()) }
        )
        SliderRow(
            label = "实际收益率",
            value = percentText(realReturn),
            hint = "已扣通胀的长期复合收益（几何年化），不是某一年的账面涨幅；把它当成「未来 20 年的平均复合增速」",
            sliderValue = realReturn.toFloat(),
            range = 0.0f..0.08f,
            steps = 15,
            onChange = { onRealReturn(it.toDouble()) }
        )
        SliderRow(
            label = "目标年数",
            value = "$targetYears 年",
            hint = "用来算「在这个时间点之前达标」的概率",
            sliderValue = targetYears.toFloat(),
            range = 5f..40f,
            steps = 34,
            onChange = { onTargetYears(it.roundToInt()) }
        )
    }
}

@Composable
private fun SliderRow(
    label: String,
    value: String,
    hint: String,
    sliderValue: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
    onChange: (Float) -> Unit
) {
    Column(modifier = Modifier.padding(top = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = label, style = MaterialTheme.typography.labelMedium)
            Spacer(Modifier.weight(1f))
            Text(
                text = value,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = AppColors.accent
            )
        }
        Slider(
            value = sliderValue.coerceIn(range.start, range.endInclusive),
            onValueChange = onChange,
            valueRange = range,
            steps = steps,
            modifier = Modifier.height(32.dp)
        )
        Text(
            text = hint,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * 杠杆榜：同样的模拟口径下，哪个动作能把中位数达标时间提前最多。
 * <p>
 * 这是整页唯一"给建议"的地方，所以要用可比的口径：每个情景都重跑一遍模拟，
 * 而不是拿一个拍脑袋的公式做示意。
 */
@Composable
private fun LeverageCard(options: List<LeverageOption>) {
    if (options.isEmpty()) return
    CardBox {
        Text("哪根杠杆最值钱", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(2.dp))
        Text(
            text = "每个动作单独重跑一遍模拟，看中位数达标时间提前多少年",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(6.dp))
        options.forEach { option ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = option.label,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = option.detail,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.width(12.dp))
                Text(
                    text = yearsSavedText(option.yearsSaved),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (option.yearsSaved >= 0.05) {
                        AppColors.accent
                    } else if (option.yearsSaved <= -0.05) {
                        AppColors.expense
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
        }
    }
}

/**
 * 压力测试：三种坏运气各自的代价。
 * <p>
 * 和杠杆榜相反，这张卡不给建议，只负责把「均值假设看不见的风险」摆出来：
 * 失业、序列风险、生活方式膨胀，任何一项都能把年份推后几年。
 */
@Composable
private fun StressCard(items: List<StressResult>) {
    if (items.isEmpty()) return
    CardBox {
        Text("压力测试", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(2.dp))
        Text(
            text = "把三种坏运气分别塞进同一条收益序列，看结论退多少",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(6.dp))
        items.forEach { item ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.label,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = item.detail,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = item.yearsDelayed?.let { yearsDelayText(it) } ?: "窗口内未达标",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = if (item.yearsDelayed == null) {
                            AppColors.expense
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        }
                    )
                    item.successDrop?.let { drop ->
                        Text(
                            text = if (drop < 0.005) {
                                "概率不变"
                            } else {
                                "概率 −${(drop * 100).roundToInt()}%"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BasisCard(plan: WealthPlan, hideAmounts: Boolean) {
    CardBox {
        Text("口径", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        InfoRow("年支出", money(plan.annualExpenseFen, hideAmounts) + "（近 ${plan.sampleMonths} 个月中位数 × 12）")
        InfoRow("当前净资产", money(plan.netWorthFen, hideAmounts))
        InfoRow(
            "储蓄率",
            plan.savingsRate?.let { percentText(it) } ?: "没有收入记录"
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "样本剔除了「余额调整」这类一次性校准；支出为 0 的月份不计入中位数。" +
                "收益按对数正态、年度独立同分布模拟 ${plan.simulations} 条路径（固定随机种子，结果可复现）。" +
                "「实际收益率」按复合（几何）年化理解，不是某一年的账面涨幅：中位数终值只由它决定，" +
                "波动只扩大离散程度、抬高算术均值。每年的结余在年末投入（收入逐月到手），" +
                "与「确定性年数」同一套递推，两张表的数字可以对上。",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun DisclaimerCard() {
    CardBox {
        Text(
            text = "这是算术推演，不是收益承诺：模型不含税费、房产、养老金与一次性大额事件；" +
                "真实市场会出现连续多年低于均值的情况。把它当成「按现在的节奏还差多远」的一把尺子。",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun HintCard(title: String, body: String) {
    CardBox {
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(4.dp))
        Text(
            text = body,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.width(12.dp))
        Spacer(Modifier.weight(1f))
        Text(
            text = value,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun CardBox(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(16.dp)
    ) {
        content()
    }
}

/** 压力测试右侧的「推迟 X 年」：整数年，0 表示几乎没影响 */
private fun yearsDelayText(years: Int): String =
    if (years <= 0) "几乎不变" else "推迟 $years 年"

/** 杠杆榜右侧的「提前 / 推迟 X 年」 */
private fun yearsSavedText(years: Double): String {
    val magnitude = kotlin.math.abs(years)
    if (magnitude < 0.05) return "≈ 0 年"
    val text = "${(magnitude * 10).roundToInt() / 10.0} 年"
    return if (years > 0) "提前 $text" else "推迟 $text"
}

/** 图表纵轴的紧凑金额（分 → 万 / 亿），避免「¥1,234,567」把坐标轴撑爆 */
private fun compactYuan(fen: Long): String {
    val yuan = fen / 100.0
    return when {
        kotlin.math.abs(yuan) >= 100_000_000 -> "${(yuan / 100_000_000).roundToInt()}亿"
        kotlin.math.abs(yuan) >= 10_000 -> "${(yuan / 10_000).roundToInt()}万"
        else -> yuan.roundToInt().toString()
    }
}

/** 隐私模式的纵轴：倍数（相对 FI 目标），只给形状不给量级 */
private fun multipleText(value: Long, base: Long): String {
    if (base <= 0L) return "—"
    return "${(value.toDouble() / base * 10).roundToInt() / 10.0}"
}

/** 百分比：整数就写整数，带小数才保留一位（12.4% / 50%） */
private fun percentText(value: Double): String {
    val percent = value * 100.0
    val rounded = percent.roundToInt()
    return if (kotlin.math.abs(percent - rounded) < 0.05) {
        "$rounded%"
    } else {
        "${(percent * 10).roundToInt() / 10.0}%"
    }
}
