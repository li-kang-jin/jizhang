package com.jizhang.app.ui.common

/**
 * 金额算式求值：让「成本 1500 又追加 250」可以直接输入 `1500+250`。
 * <p>
 * 为什么需要：投资模块的成本是累计值，每次加仓都要用户先在脑子里（或在计算器里）
 * 加完再填，是这块最高频的摩擦点。支持算式之后，输入框退化成「计算器」，
 * 用户按记账时的思维直接打 `1500+250` 就行，不用先算出结果。
 * <p>
 * 只支持 **加减乘除 + 小数**，按先乘除后加减求值，不支持括号与函数：
 * 括号会让「输入到一半」的预览频繁变红，而金额场景里也用不上。
 * 求值失败（含非法字符、除数为 0、结果不是有限数）一律返回 null，由调用方决定回退值。
 * <p>
 * 全角字符（＋－×÷）与千分位逗号会先归一化，中文输入法下直接打也不会报错。
 */
fun evaluateAmount(text: String?): Double? {
    val normalized = text.orEmpty()
        .replace("，", "")
        .replace(",", "")
        .replace("＋", "+")
        .replace("－", "-")
        .replace("−", "-")
        .replace("×", "*")
        .replace("÷", "/")
        .replace(" ", "")
        .trim()
    if (normalized.isEmpty()) return null
    val parser = ExpressionParser(normalized)
    val value = parser.parse()
    return value?.takeIf { it.isFinite() }
}

/**
 * 带运算符时的预览文案；纯数字或非法输入返回 null。
 * <p>
 * 只有「确实能算出另一个数」时才提示，否则用户每打一个数字都会看到一行多余的字。
 */
fun amountExpressionPreview(text: String?): String? {
    val raw = text.orEmpty().trim()
    if (raw.none { it in "+-*/＋－×÷" }) return null
    val value = evaluateAmount(raw) ?: return null
    return "= ¥" + formatAmount((value * 100).toLong())
}

private class ExpressionParser(private val source: String) {

    private var pos = 0

    fun parse(): Double? {
        val value = expression() ?: return null
        skipSpaces()
        return if (pos == source.length) value else null
    }

    /** 加减：左结合 */
    private fun expression(): Double? {
        var left = term() ?: return null
        while (true) {
            skipSpaces()
            when (peek()) {
                '+' -> {
                    pos++
                    left += term() ?: return null
                }

                '-' -> {
                    pos++
                    left -= term() ?: return null
                }

                else -> return left
            }
        }
    }

    /** 乘除：比加减优先级高 */
    private fun term(): Double? {
        var left = factor() ?: return null
        while (true) {
            skipSpaces()
            when (peek()) {
                '*' -> {
                    pos++
                    left *= factor() ?: return null
                }

                '/' -> {
                    pos++
                    val right = factor() ?: return null
                    if (right == 0.0) return null
                    left /= right
                }

                else -> return left
            }
        }
    }

    /** 一元正负号 + 数字 */
    private fun factor(): Double? {
        skipSpaces()
        when (peek()) {
            '+' -> {
                pos++
                return factor()
            }

            '-' -> {
                pos++
                val value = factor() ?: return null
                return -value
            }
        }
        return number()
    }

    private fun number(): Double? {
        skipSpaces()
        val start = pos
        while (pos < source.length && (source[pos].isDigit() || source[pos] == '.')) {
            pos++
        }
        if (pos == start) return null
        val slice = source.substring(start, pos)
        // 单个 "." 或 "1.2.3" 这类都交给 toDoubleOrNull 拒绝
        return slice.toDoubleOrNull()
    }

    private fun peek(): Char? = if (pos < source.length) source[pos] else null

    private fun skipSpaces() {
        while (pos < source.length && source[pos] == ' ') pos++
    }
}
