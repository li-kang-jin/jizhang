package com.jizhang.app.ui.record

import java.math.BigDecimal
import java.math.RoundingMode

/** 计算器输入单元 */
sealed interface CalcToken {
    val text: String
}

/** 输入中的数字（单位：元），保留用户原始输入便于继续编辑 */
data class CalcNum(val raw: String) : CalcToken {
    override val text: String get() = raw
}

/** 四则运算符 */
data class CalcOp(val symbol: Char) : CalcToken {
    // 内部统一存 ASCII '-'，展示时换成排版减号（U+2212），跟键盘上的「−」保持一致
    override val text: String get() = if (symbol == '-') "−" else symbol.toString()
}

/**
 * 记账计算器（纯 Kotlin，可单测）。
 *
 * 规则：
 * - 只支持正数与 `+ − × ÷`，乘除优先于加减；
 * - 单个数字最多 9 位、小数最多两位，运算符可连续替换；
 * - 除法保留两位小数（四舍五入），除零视为无效；
 * - 最终结果统一换算为「分」。
 */
object CalcEngine {

    private const val MAX_TOKENS = 21
    private const val MAX_DIGITS = 9
    private const val MAX_DECIMALS = 2
    private val OPS = charArrayOf('+', '-', '×', '÷')

    /** 键盘上印的是排版减号（U+2212），输入法/粘贴还可能带连字符变体，统一归一成 ASCII '-' */
    private val MINUS_ALIASES = charArrayOf('−', '–', '—')

    private fun canonicalOp(op: Char): Char = if (op in MINUS_ALIASES) '-' else op

    fun appendDigit(tokens: List<CalcToken>, digit: Char): List<CalcToken> {
        if (!digit.isDigit() && digit != '.') return tokens
        val next = when (val last = tokens.lastOrNull()) {
            null -> listOf(CalcNum(digit.toString()))
            is CalcNum -> {
                val raw = appendToNumber(last.raw, digit) ?: return tokens
                tokens.dropLast(1) + CalcNum(raw)
            }
            is CalcOp -> tokens + CalcNum(digit.toString())
        }
        return next.take(MAX_TOKENS)
    }

    private fun appendToNumber(raw: String, digit: Char): String? {
        if (digit == '.') {
            return when {
                raw.contains('.') -> null
                raw.isEmpty() -> "0."
                else -> raw + "."
            }
        }
        if (raw.contains('.')) {
            if (raw.substringAfter('.').length >= MAX_DECIMALS) return null
        } else if (raw.replace(".", "").length >= MAX_DIGITS) {
            return null
        }
        // 前导零只保留一位
        return if (raw == "0") digit.toString() else raw + digit
    }

    fun appendOp(tokens: List<CalcToken>, op: Char): List<CalcToken> {
        val symbol = canonicalOp(op)
        if (symbol !in OPS || tokens.isEmpty()) return tokens
        return when (tokens.last()) {
            is CalcNum -> (tokens + CalcOp(symbol)).take(MAX_TOKENS)
            // 运算符可直接替换，方便改错
            is CalcOp -> tokens.dropLast(1) + CalcOp(symbol)
        }
    }

    fun backspace(tokens: List<CalcToken>): List<CalcToken> {
        return when (val last = tokens.lastOrNull()) {
            null -> tokens
            is CalcNum -> {
                val rest = last.raw.dropLast(1)
                if (rest.isEmpty()) tokens.dropLast(1) else tokens.dropLast(1) + CalcNum(rest)
            }
            is CalcOp -> tokens.dropLast(1)
        }
    }

    fun clear(): List<CalcToken> = emptyList()

    /** 当前表达式的结果；表达式不完整（如以运算符结尾）或除零时返回 null */
    fun evaluate(tokens: List<CalcToken>): BigDecimal? {
        if (tokens.isEmpty() || tokens.last() is CalcOp) return null

        // 第一遍：先算乘除
        val reduced = ArrayDeque<CalcToken>()
        var i = 0
        while (i < tokens.size) {
            val token = tokens[i]
            when {
                token is CalcNum -> {
                    reduced.addLast(token)
                    i++
                }

                token is CalcOp && (token.symbol == '×' || token.symbol == '÷') -> {
                    val left = reduced.removeLastOrNull() as? CalcNum ?: return null
                    val right = tokens.getOrNull(i + 1) as? CalcNum ?: return null
                    val value = compute(left, token.symbol, right) ?: return null
                    reduced.addLast(CalcNum(value.toPlainString()))
                    i += 2
                }

                else -> {
                    reduced.addLast(token)
                    i++
                }
            }
        }

        // 第二遍：再算加减
        var result = (reduced.removeFirstOrNull() as? CalcNum)
            ?.raw?.toBigDecimalOrNull() ?: return null
        while (reduced.isNotEmpty()) {
            val op = reduced.removeFirstOrNull() as? CalcOp ?: return null
            val right = reduced.removeFirstOrNull() as? CalcNum ?: return null
            result = when (op.symbol) {
                '+' -> result.add(right.raw.toBigDecimal())
                '-' -> result.subtract(right.raw.toBigDecimal())
                else -> return null
            }
        }
        return result
    }

    /** 结果换算为「分」；无有效结果或非正数时返回 null */
    fun resultFen(tokens: List<CalcToken>): Long? {
        val yuan = evaluate(tokens) ?: return null
        if (yuan.compareTo(BigDecimal.ZERO) <= 0) return null
        return yuan.multiply(BigDecimal(100)).setScale(0, RoundingMode.HALF_UP).toLong()
    }

    /** 等号：计算成功则把结果作为唯一输入，继续参与下一次运算 */
    fun applyEquals(tokens: List<CalcToken>): List<CalcToken> {
        val value = evaluate(tokens) ?: return tokens
        return listOf(CalcNum(value.stripTrailingZeros().toPlainString()))
    }

    fun expressionText(tokens: List<CalcToken>): String =
        tokens.joinToString(" ") { it.text }

    private fun compute(left: CalcNum, op: Char, right: CalcNum): BigDecimal? {
        val a = left.raw.toBigDecimalOrNull() ?: return null
        val b = right.raw.toBigDecimalOrNull() ?: return null
        return when (op) {
            '×' -> a.multiply(b)
            '÷' -> {
                if (b.compareTo(BigDecimal.ZERO) == 0) return null
                runCatching { a.divide(b, 2, RoundingMode.HALF_UP) }.getOrNull()
            }

            else -> null
        }
    }
}
