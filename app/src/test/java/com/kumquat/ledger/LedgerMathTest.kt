package com.kumquat.ledger

import com.kumquat.ledger.domain.LedgerMath
import com.kumquat.ledger.domain.TxType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 净额记账规则——统计口径的核心回归测试。 */
class LedgerMathTest {

    @Test
    fun expense_150_refund_100_net_50() {
        val totals = LedgerMath.totals(
            listOf(
                TxType.EXPENSE to 15_000L,
                TxType.REFUND to 10_000L
            )
        )
        assertEquals(15_000L, totals.expenseCents)
        assertEquals(10_000L, totals.refundCents)
        assertEquals(5_000L, totals.netExpenseCents)
        assertEquals(0L, totals.incomeCents)
        // 净收支 = 入账 - 净出账
        assertEquals(-5_000L, totals.netCents)
    }

    @Test
    fun exchange_never_affects_balance() {
        val totals = LedgerMath.totals(
            listOf(
                TxType.EXCHANGE to 10_000L,
                TxType.EXCHANGE to 10_000L,
                TxType.INCOME to 5_000L
            )
        )
        assertEquals(20_000L, totals.exchangeCents)
        assertEquals(5_000L, totals.netCents)
        assertEquals(0L, totals.netExpenseCents)
    }

    @Test
    fun refund_cannot_turn_expense_negative() {
        val totals = LedgerMath.totals(
            listOf(
                TxType.EXPENSE to 1_000L,
                TxType.REFUND to 3_000L
            )
        )
        assertEquals(0L, totals.netExpenseCents)
    }

    @Test
    fun category_ranking_deducts_linked_refund() {
        val expense = listOf(
            LedgerMath.CatAmount("饮食", "正餐", 15_000L),
            LedgerMath.CatAmount("购物", "数码电器", 5_000L)
        )
        val linked = listOf(LedgerMath.CatAmount("饮食", "正餐", 10_000L))
        val ranking = LedgerMath.netExpenseByCategory(expense, linked, emptyList())

        assertEquals(2, ranking.size)
        assertEquals(5_000L, ranking.first { it.l1 == "饮食" }.cents)
        assertEquals(5_000L, ranking.first { it.l1 == "购物" }.cents)
        // 排序：金额降序
        assertTrue(ranking[0].cents >= ranking[1].cents)
    }

    @Test
    fun fully_refunded_category_disappears_from_ranking() {
        val expense = listOf(LedgerMath.CatAmount("饮食", "正餐", 2_000L))
        val linked = listOf(LedgerMath.CatAmount("饮食", "正餐", 2_000L))
        assertTrue(LedgerMath.netExpenseByCategory(expense, linked, emptyList()).isEmpty())
    }

    @Test
    fun split_matching_by_date_and_category() {
        val c = LedgerMath.SplitCriteria(fromDate = "2026-10-01", toDate = "2026-10-31")
        assertTrue(LedgerMath.matchesSplit("2026-10-20", "饮食", "正餐", c))
        assertTrue(!LedgerMath.matchesSplit("2026-09-30", "饮食", "正餐", c))

        val c2 = LedgerMath.SplitCriteria(categoryL1 = "饮食", categoryL2 = "正餐")
        assertTrue(LedgerMath.matchesSplit("2026-01-01", "饮食", "正餐", c2))
        assertTrue(!LedgerMath.matchesSplit("2026-01-01", "饮食", "水果", c2))
        assertTrue(!LedgerMath.matchesSplit("2026-01-01", "购物", "正餐", c2))
    }

    @Test
    fun display_name_handles_flat_category() {
        assertEquals("红包", LedgerMath.catName("红包", ""))
        assertEquals("饮食 · 正餐", LedgerMath.catName("饮食", "正餐"))
    }

    @Test
    fun transfer_never_counts_as_income_or_expense() {
        val totals = LedgerMath.totals(
            listOf(
                TxType.TRANSFER to 50_000L,
                TxType.INCOME to 10_000L,
                TxType.EXPENSE to 3_000L
            )
        )
        assertEquals(50_000L, totals.transferCents)
        assertEquals(3_000L, totals.expenseCents)
        assertEquals(3_000L, totals.netExpenseCents)
        assertEquals(7_000L, totals.netCents)
    }

    @Test
    fun asset_balance_is_initial_plus_deltas() {
        // 初始 1000 元，支出 15 元，回冲 5 元，转出 300 元
        assertEquals(69_000L, LedgerMath.assetBalance(100_000L, -31_000L, 0L))
        // 转入侧单独累计
        assertEquals(30_000L, LedgerMath.assetBalance(0L, 0L, 30_000L))
    }

    @Test
    fun asset_effect_ignores_exchange_and_handles_transfer() {
        assertEquals(-1_500L, LedgerMath.assetEffect(TxType.TRANSFER, 1_500L))
        assertEquals(-1_500L, LedgerMath.assetEffect(TxType.EXPENSE, 1_500L))
        assertEquals(1_500L, LedgerMath.assetEffect(TxType.REFUND, 1_500L))
        assertEquals(0L, LedgerMath.assetEffect(TxType.EXCHANGE, 1_500L))
    }
}
