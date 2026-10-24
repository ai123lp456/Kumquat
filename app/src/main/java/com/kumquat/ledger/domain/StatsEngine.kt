package com.kumquat.ledger.domain

import com.kumquat.ledger.data.Tx

/** 闭区间日期范围，全应用统一 yyyy-MM-dd 字符串。 */
data class DateRange(val start: String, val end: String) {
    fun contains(date: String): Boolean = date >= start && date <= end

    companion object {
        fun single(date: String): DateRange = DateRange(date, date)
    }
}

data class CategoryAmount(val l1: String, val l2: String, val cents: Long) {
    val displayName: String
        get() = if (l2.isBlank() || l2 == l1) l1 else "$l1 · $l2"
}

/**
 * 统一统计口径的计算结果。
 *
 * 金额单位全部是整数分，且都是正数；方向由字段名和 [exchangeNetCents] 的正负表达。
 */
data class PeriodStats(
    val range: DateRange,
    /** 基础收入：INCOME 合计。 */
    val baseIncomeCents: Long = 0,
    /** 基础支出：EXPENSE 逐笔净额（原金额 - 全部有效关联退款，最低 0）之和。 */
    val baseExpenseCents: Long = 0,
    /** 归属本期账单的退款抵扣合计（退款本身可能发生在别的周期）。 */
    val refundAppliedCents: Long = 0,
    /** 本期实际发生的退款合计（不因原账单跨月而移动到原账单周期）。 */
    val refundOccurredCents: Long = 0,
    val exchangeInCents: Long = 0,
    val exchangeOutCents: Long = 0,
    val expenseRanking: List<CategoryAmount> = emptyList(),
    val incomeRanking: List<CategoryAmount> = emptyList(),
    val refundCountInPeriod: Int = 0,
    /** 当前账本内全部“退款待关联”数量（不受日期范围限制）。 */
    val pendingLinkCount: Int = 0,
    /** 当前账本内全部“历史往来待确认”数量（不受日期范围限制）。 */
    val pendingConfirmCount: Int = 0
) {
    /** 回往净额 =（收回 + 还款）-（代付 + 借出）；退款不在其中。 */
    val exchangeNetCents: Long get() = exchangeInCents - exchangeOutCents

    val exchangeNetIncomeCents: Long get() = exchangeNetCents.coerceAtLeast(0L)

    val exchangeNetExpenseCents: Long get() = (-exchangeNetCents).coerceAtLeast(0L)

    val totalIncomeCents: Long get() = baseIncomeCents + exchangeNetIncomeCents

    val totalExpenseCents: Long get() = baseExpenseCents + exchangeNetExpenseCents

    val netCents: Long get() = totalIncomeCents - totalExpenseCents

    val hasRefundInPeriod: Boolean get() = refundOccurredCents > 0

    val hasRefundAppliedInPeriod: Boolean get() = refundAppliedCents > 0

    val hasPendingHuiWang: Boolean get() = pendingLinkCount > 0 || pendingConfirmCount > 0
}

/**
 * 统计核心：纯函数，不依赖 Android / Room，全部输入输出可单测。
 *
 * 关键规则：
 * - 退款按**原出账账单的日期和分类**抵扣，而不是退款发生日；
 * - 退款不进入回往净额，也不进入收入；
 * - 回往净额为正计入收入，为负计入支出，为 0 不计；
 * - 转账完全忽略；
 * - 待关联/待确认记录不参与统计，但计数保留，UI 必须展示。
 *
 * 调用方应传入**单个账本**的流水；本函数仍会按 ledgerId 再过滤一次，
 * 防止误传全量数据造成跨账本混算。
 */
object StatsEngine {

    fun compute(ledgerId: Long, transactions: List<Tx>, range: DateRange): PeriodStats {
        val scoped = transactions.filter { it.accountId == ledgerId }
        val byId = scoped.associateBy { it.id }

        val refunds = scoped.filter { TxType.parse(it.type) == TxType.REFUND }
        val refundsByExpense: Map<Long, List<Tx>> = refunds
            .mapNotNull { refund -> refund.linkedTxId?.let { it to refund } }
            .groupBy({ it.first }, { it.second })

        var baseIncome = 0L
        var baseExpense = 0L
        var refundApplied = 0L
        var refundOccurred = 0L
        var refundCount = 0
        var exchangeIn = 0L
        var exchangeOut = 0L
        val expenseByCat = LinkedHashMap<Pair<String, String>, Long>()
        val incomeByCat = LinkedHashMap<Pair<String, String>, Long>()

        scoped.forEach { tx ->
            when (TxType.parse(tx.type)) {
                TxType.INCOME -> if (range.contains(tx.date)) {
                    val amount = tx.amountCents.absoluteCents()
                    baseIncome += amount
                    incomeByCat.add(tx.categoryKey(), amount)
                }

                TxType.EXPENSE -> if (range.contains(tx.date)) {
                    val amount = tx.amountCents.absoluteCents()
                    val refunded = refundsByExpense[tx.id].orEmpty()
                        .sumOf { it.amountCents.absoluteCents() }
                    val net = (amount - refunded).coerceAtLeast(0L)
                    baseExpense += net
                    refundApplied += minOf(amount, refunded)
                    if (net > 0L) expenseByCat.add(tx.categoryKey(), net)
                }

                TxType.REFUND -> if (range.contains(tx.date)) {
                    refundOccurred += tx.amountCents.absoluteCents()
                    refundCount++
                }

                TxType.EXCHANGE -> if (range.contains(tx.date)) {
                    val resolved = LegacyHuiWangResolver.resolve(tx, null)
                    if (resolved.countsInExchangeNet) {
                        when (resolved.direction) {
                            FundDirection.IN -> exchangeIn += tx.amountCents.absoluteCents()
                            FundDirection.OUT -> exchangeOut += tx.amountCents.absoluteCents()
                            FundDirection.NONE -> Unit
                        }
                    }
                }

                TxType.TRANSFER, null -> Unit
            }
        }

        // 待处理计数按整个账本统计：历史问题不应因为翻到别的月份就“消失”。
        val pendingLink = refunds.count { refund ->
            LegacyHuiWangResolver.resolve(refund, refund.linkedTxId?.let(byId::get)).status ==
                HuiWangStatus.PENDING_LINK
        }
        val pendingConfirm = scoped.count { tx ->
            TxType.parse(tx.type) == TxType.EXCHANGE &&
                LegacyHuiWangResolver.resolve(tx, null).status == HuiWangStatus.PENDING_CONFIRM
        }

        return PeriodStats(
            range = range,
            baseIncomeCents = baseIncome,
            baseExpenseCents = baseExpense,
            refundAppliedCents = refundApplied,
            refundOccurredCents = refundOccurred,
            exchangeInCents = exchangeIn,
            exchangeOutCents = exchangeOut,
            expenseRanking = expenseByCat.toRanking(),
            incomeRanking = incomeByCat.toRanking(),
            refundCountInPeriod = refundCount,
            pendingLinkCount = pendingLink,
            pendingConfirmCount = pendingConfirm
        )
    }

    fun empty(range: DateRange): PeriodStats = PeriodStats(range = range)

    private fun Long.absoluteCents(): Long = if (this < 0L) -this else this

    private fun Tx.categoryKey(): Pair<String, String> = categoryL1 to categoryL2

    private fun MutableMap<Pair<String, String>, Long>.add(key: Pair<String, String>, cents: Long) {
        if (cents <= 0L) return
        this[key] = (this[key] ?: 0L) + cents
    }

    private fun Map<Pair<String, String>, Long>.toRanking(): List<CategoryAmount> =
        asSequence()
            .filter { it.value > 0L }
            .map { (key, cents) -> CategoryAmount(key.first, key.second, cents) }
            .sortedWith(
                compareByDescending<CategoryAmount> { it.cents }
                    .thenBy { it.l1 }
                    .thenBy { it.l2 }
            )
            .toList()
}
