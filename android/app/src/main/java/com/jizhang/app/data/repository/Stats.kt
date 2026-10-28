package com.jizhang.app.data.repository

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.roundToLong
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * 抽样与分位数工具：预算预测（[BudgetForecaster]）与财富规划（[WealthPlanner]）共用一套。
 * <p>
 * 这些函数以前是 [BudgetForecaster] 里的 private 方法，财富规划要用时只能再抄一份 ——
 * 两份实现迟早会漂移（比如缩尾口径、分位数取整方式不同），所以收到这里统一维护。
 */
internal object Stats {

    /** 排序后数组的分位数（最近邻，避免插值引入假的精度） */
    fun quantile(sorted: LongArray, q: Double): Long {
        if (sorted.isEmpty()) return 0L
        val idx = ((sorted.size - 1) * q).roundToLong().toInt().coerceIn(0, sorted.lastIndex)
        return sorted[idx]
    }

    /** 排序后数组的分位数（浮点版） */
    fun quantile(sorted: DoubleArray, q: Double): Double {
        if (sorted.isEmpty()) return 0.0
        val idx = ((sorted.size - 1) * q).roundToLong().toInt().coerceIn(0, sorted.lastIndex)
        return sorted[idx]
    }

    /** Box–Muller 标准正态 */
    fun nextGaussian(rnd: Random): Double {
        val u1 = rnd.nextDouble().coerceAtLeast(1e-12)
        val u2 = rnd.nextDouble()
        return sqrt(-2.0 * ln(u1)) * cos(2.0 * PI * u2)
    }

    /** Marsaglia–Tsang 法抽 Gamma（shape 允许非整数） */
    fun nextGamma(rnd: Random, shape: Double, scale: Double): Double {
        if (shape <= 0.0 || scale <= 0.0) return 0.0
        if (shape < 1.0) {
            val u = rnd.nextDouble().coerceAtLeast(1e-12)
            return nextGamma(rnd, shape + 1.0, scale) * u.pow(1.0 / shape)
        }
        val d = shape - 1.0 / 3.0
        val c = 1.0 / sqrt(9.0 * d)
        while (true) {
            val x = nextGaussian(rnd)
            val v = (1.0 + c * x).let { it * it * it }
            if (v <= 0.0) continue
            val u = rnd.nextDouble()
            if (u < 1.0 - 0.0331 * x * x * x * x) return d * v * scale
            if (ln(u.coerceAtLeast(1e-12)) < 0.5 * x * x + d * (1.0 - v + ln(v))) {
                return d * v * scale
            }
        }
    }

    /** 标准正态 CDF（Abramowitz & Stegun 26.2.17，误差 < 1e-7） */
    fun normalCdf(z: Double): Double {
        val t = 1.0 / (1.0 + 0.2316419 * abs(z))
        val d = 0.3989422804 * exp(-z * z / 2.0)
        val p = d * t * (0.3193815 + t * (-0.3565638 + t * (1.781478 + t * (-1.821256 + t * 1.330274))))
        return if (z > 0) 1.0 - p else p
    }

    /** 正态分位：直接二分反解 CDF，避免再抄一张系数表 */
    fun normalQuantile(p: Double): Double {
        if (p <= 0.5) return 0.0
        var lo = 0.0
        var hi = 8.0
        repeat(64) {
            val mid = (lo + hi) / 2.0
            if (normalCdf(mid) < p) lo = mid else hi = mid
        }
        return (lo + hi) / 2.0
    }
}
