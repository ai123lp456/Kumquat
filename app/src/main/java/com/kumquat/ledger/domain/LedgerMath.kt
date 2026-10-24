package com.kumquat.ledger.domain

/**
 * 统计口径的纯函数实现（不依赖 Android/Room，便于 JVM 单元测试）。
 *
 * 口径：
 * - 入账 = INCOME 合计；出账 = EXPENSE 合计 - REFUND 合计（回冲抵扣出账，回冲本身不计收入）。
 * - 往来（EXCHANGE）与内部转账（TRANSFER）完全不计入收支，只作为独立视图展示。
 * - 排行只统计 INCOME/EXPENSE 的净额，回冲/往来/转账单列。
 */
object LedgerMath {

    data class TypeTotals(
        val incomeCents: Long = 0,
        val expenseCents: Long = 0,
        val refundCents: Long = 0,
        val exchangeCents: Long = 0,
        val transferCents: Long = 0
    ) {
        /** 净出账：出账金额减去回冲，最低 0（回冲不会把出账冲成收入）。 */
        val netExpenseCents: Long get() = (expenseCents - refundCents).coerceAtLeast(0L)

        /** 净收支 = 入账 - 净出账。转账与往来不参与。 */
        val netCents: Long get() = incomeCents - netExpenseCents
    }

    data class CatAmount(val l1: String, val l2: String, val cents: Long)

    fun totals(rows: List<Pair<TxType, Long>>): TypeTotals {
        var income = 0L
        var expense = 0L
        var refund = 0L
        var exchange = 0L
        var transfer = 0L
        rows.forEach { (type, cents) ->
            when (type) {
                TxType.INCOME -> income += cents
                TxType.EXPENSE -> expense += cents
                TxType.REFUND -> refund += cents
                TxType.EXCHANGE -> exchange += cents
                TxType.TRANSFER -> transfer += cents
            }
        }
        return TypeTotals(income, expense, refund, exchange, transfer)
    }

    /**
     * 净出账分类排行：
     * [expense] 为按分类聚合的出账；[refundLinked] 为已关联原账单的回冲（已按原账单分类聚合）；
     * [refundUnlinked] 为未关联的回冲（按回冲自身分类聚合，通常不参与出账排行，只做信息展示）。
     */
    fun netExpenseByCategory(
        expense: List<CatAmount>,
        refundLinked: List<CatAmount>,
        refundUnlinked: List<CatAmount>
    ): List<CatAmount> {
        val refundMap = HashMap<Pair<String, String>, Long>()
        (refundLinked + refundUnlinked).forEach { r ->
            val key = r.l1 to r.l2
            refundMap[key] = (refundMap[key] ?: 0L) + r.cents
        }
        val result = ArrayList<CatAmount>(expense.size)
        expense.forEach { e ->
            val key = e.l1 to e.l2
            val deducted = (e.cents - (refundMap[key] ?: 0L)).coerceAtLeast(0L)
            if (deducted > 0L) result.add(CatAmount(e.l1, e.l2, deducted))
        }
        return result.sortedByDescending { it.cents }
    }

    /** 分类展示名：没有二级时只显示一级。 */
    fun catName(l1: String, l2: String): String =
        if (l2.isBlank() || l2 == l1) l1 else "$l1 · $l2"

    /** 拆分账本的筛选条件（纯数据）。 */
    data class SplitCriteria(
        val fromDate: String? = null,
        val toDate: String? = null,
        val categoryL1: String? = null,
        val categoryL2: String? = null
    )

    /** 判断一笔账单是否落在拆分条件内。 */
    fun matchesSplit(date: String, l1: String, l2: String, c: SplitCriteria): Boolean {
        if (c.fromDate != null && date < c.fromDate) return false
        if (c.toDate != null && date > c.toDate) return false
        if (!c.categoryL1.isNullOrBlank() && c.categoryL1 != l1) return false
        if (!c.categoryL2.isNullOrBlank() && c.categoryL2 != l2) return false
        return true
    }

    /**
     * 资产账户余额 = 初始余额 + 支出侧增量 + 转入侧增量。
     * 增量全部来自流水实时聚合（见 TxDao.observeAssetOutflow / observeAssetInflow），不落库。
     */
    fun assetBalance(initialBalanceCents: Long, outflowDelta: Long, inflowDelta: Long): Long =
        initialBalanceCents + outflowDelta + inflowDelta

    /** 一笔账单对资产余额的影响（转账方向不同，仅用于单条展示/调试）。 */
    fun assetEffect(type: TxType, cents: Long): Long = when (type) {
        TxType.INCOME -> cents
        TxType.EXPENSE -> -cents
        TxType.REFUND -> cents
        TxType.TRANSFER -> -cents
        TxType.EXCHANGE -> 0L
    }
}
