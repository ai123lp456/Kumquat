package com.kumquat.ledger.util

import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

/** 日期工具：全应用统一 yyyy-MM-dd 字符串（与数据库/导入导出一致）。 */
object DateX {

    private val iso: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE
    private val cnWeek = arrayOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")
    private val enWeek = arrayOf("MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN")
    private val enMonth = arrayOf(
        "JAN", "FEB", "MAR", "APR", "MAY", "JUN", "JUL", "AUG", "SEP", "OCT", "NOV", "DEC"
    )

    fun today(): String = LocalDate.now().format(iso)

    fun nowMonth(): YearMonth = YearMonth.now()

    fun parse(date: String): LocalDate? = try {
        LocalDate.parse(date, iso)
    } catch (_: Exception) {
        null
    }

    fun monthStart(ym: YearMonth): String = ym.atDay(1).format(iso)

    fun monthEnd(ym: YearMonth): String = ym.atEndOfMonth().format(iso)

    fun monthLabel(ym: YearMonth): String = "${ym.year} 年 ${ym.monthValue} 月"

    fun monthShort(ym: YearMonth): String = "${enMonth[ym.monthValue - 1]} ${ym.year}"

    fun cnWeekday(date: String): String {
        val d = parse(date) ?: return ""
        return cnWeek[d.dayOfWeek.value - 1]
    }

    fun enWeekday(date: String): String {
        val d = parse(date) ?: return ""
        return enWeek[d.dayOfWeek.value - 1]
    }

    fun dayLabel(date: String): String {
        val d = parse(date) ?: return date
        return "${d.monthValue} 月 ${d.dayOfMonth} 日"
    }

    fun dayHeaderEn(date: String): String {
        val d = parse(date) ?: return ""
        return "${enMonth[d.monthValue - 1]} ${d.dayOfMonth}"
    }

    fun full(date: String): String {
        val d = parse(date) ?: return date
        val m = d.monthValue.toString().padStart(2, '0')
        val day = d.dayOfMonth.toString().padStart(2, '0')
        return "${d.year}-$m-$day"
    }

    /** 日历格子：从周一开始的 6 行 × 7 列，不足补空。 */
    fun monthCells(ym: YearMonth): List<LocalDate?> {
        val first = ym.atDay(1)
        val lead = first.dayOfWeek.value - 1
        val days = ym.lengthOfMonth()
        val cells = ArrayList<LocalDate?>(42)
        repeat(lead) { cells.add(null) }
        for (d in 1..days) cells.add(ym.atDay(d))
        while (cells.size % 7 != 0) cells.add(null)
        return cells
    }

    fun weekHeaders(): List<String> = listOf("一", "二", "三", "四", "五", "六", "日")

    fun pretty(d: LocalDate): String = d.format(iso)

    fun localeCn(): Locale = Locale.CHINA
}
