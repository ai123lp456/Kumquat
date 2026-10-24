package com.kumquat.ledger.domain

import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

/**
 * 金额工具：内部一律用整数分（Long），展示/解析时转元，绝不走浮点。
 */
object Money {

    private val yuanFormat = DecimalFormat("#,##0.00", DecimalFormatSymbols(Locale.CHINA))

    /** 分 -> "1,234.56"（不含货币符号） */
    fun format(cents: Long): String {
        val negative = cents < 0
        val abs = if (negative) -cents else cents
        val s = yuanFormat.format(abs.toDouble() / 100.0)
        return if (negative) "-$s" else s
    }

    /** 分 -> "¥1,234.56" */
    fun formatWithSymbol(cents: Long): String = "¥" + format(cents)

    /**
     * 分 -> 带方向的展示："¥-2.60" / "¥+3.00" / "¥0.00"。
     * 用于“回往净额”等需要显式正负号的场景；-0 归一化为 0.00。
     */
    fun formatSignedWithSymbol(cents: Long): String = when {
        cents > 0L -> "¥+" + format(cents)
        cents < 0L -> "¥" + format(cents)
        else -> "¥" + format(0L)
    }

    /** 分 -> 用于输入框回填的 "1234.5" / "1234" 形式（无千分位） */
    fun toPlainInput(cents: Long): String {
        if (cents <= 0L) return ""
        val yuan = cents / 100
        val fen = (cents % 100).toInt()
        return when {
            fen == 0 -> yuan.toString()
            fen % 10 == 0 -> "$yuan.${fen / 10}"
            else -> "$yuan.${fen.toString().padStart(2, '0')}"
        }
    }

    /**
     * 解析用户输入的金额字符串为分。
     * 规则：只允许数字与最多一个小数点，小数位最多 2 位；不允许负数、空白、科学计数法。
     * 非法输入返回 null；小于等于 0 也返回 null（记账金额必须为正）。
     */
    fun parseToCents(input: String): Long? {
        val cents = parseSignedToCents(input) ?: return null
        return if (cents <= 0L) null else cents
    }

    /**
     * 解析「可正可负、可为 0」的金额（资产初始余额需要支持 0 与负值，如信用卡欠款）。
     * 允许前置 "-"；非法输入返回 null。
     */
    fun parseSignedToCents(input: String): Long? {
        var s = input.trim().replace(",", "")
        if (s.isEmpty()) return null
        val negative = s.startsWith("-")
        if (negative) s = s.substring(1)
        if (s.isEmpty()) return null
        if (s.count { it == '.' } > 1) return null
        if (!s.all { it.isDigit() || it == '.' }) return null

        val parts = s.split('.')
        val intPart = parts[0]
        val fracPart = if (parts.size == 2) parts[1] else ""
        if (fracPart.length > 2) return null
        if (intPart.isEmpty() && fracPart.isEmpty()) return null
        // 长度保护：整数位最大 9 位（约 10 亿分 = 1000 万元）
        if (intPart.length > 9) return null

        val yuan = if (intPart.isEmpty()) 0L else intPart.toLongOrNull() ?: return null
        val fen = when (fracPart.length) {
            0 -> 0L
            1 -> (fracPart.toLongOrNull() ?: return null) * 10
            2 -> fracPart.toLongOrNull() ?: return null
            else -> return null
        }
        val cents = yuan * 100 + fen
        return if (negative) -cents else cents
    }
}
