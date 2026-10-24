package com.kumquat.ledger

import com.kumquat.ledger.data.Tx
import com.kumquat.ledger.domain.DateRange
import com.kumquat.ledger.domain.HuiWangSubtype
import com.kumquat.ledger.domain.StatsEngine
import com.kumquat.ledger.domain.TxType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 统计口径的回归测试。 */
class StatsEngineTest {

    private val ledger = 1L
    private val sep = DateRange("2026-09-01", "2026-09-30")
    private val oct = DateRange("2026-10-01", "2026-10-31")

    private fun tx(
        id: Long,
        type: TxType,
        amountCents: Long,
        date: String = "2026-10-10",
        linkedTxId: Long? = null,
        subtype: HuiWangSubtype? = null,
        categoryL1: String = "测试",
        categoryL2: String = "",
        accountId: Long = 1L
    ) = Tx(
        id = id,
        accountId = accountId,
        type = type.name,
        categoryL1 = categoryL1,
        categoryL2 = categoryL2,
        amountCents = amountCents,
        date = date,
        linkedTxId = linkedTxId,
        huiwangSubtype = subtype?.name,
        createdAt = 0L,
        updatedAt = 0L
    )

    private fun compute(vararg txs: Tx, range: DateRange = oct) =
        StatsEngine.compute(ledger, txs.toList(), range)

    @Test
    fun e1_advance_560_collect_300_net_out_260() {
        val stats = compute(
            tx(1, TxType.EXCHANGE, 560, subtype = HuiWangSubtype.ADVANCE_PAY),
            tx(2, TxType.EXCHANGE, 300, subtype = HuiWangSubtype.COLLECT)
        )
        assertEquals(-260L, stats.exchangeNetCents)
        assertEquals(0L, stats.exchangeNetIncomeCents)
        assertEquals(260L, stats.exchangeNetExpenseCents)
        assertEquals(260L, stats.totalExpenseCents)
        assertEquals(0L, stats.totalIncomeCents)
    }

    @Test
    fun e2_advance_300_collect_500_net_in_200() {
        val stats = compute(
            tx(1, TxType.EXCHANGE, 300, subtype = HuiWangSubtype.ADVANCE_PAY),
            tx(2, TxType.EXCHANGE, 500, subtype = HuiWangSubtype.COLLECT)
        )
        assertEquals(200L, stats.exchangeNetCents)
        assertEquals(200L, stats.totalIncomeCents)
        assertEquals(0L, stats.totalExpenseCents)
    }

    @Test
    fun e3_complete_offset_counts_nothing() {
        val stats = compute(
            tx(1, TxType.EXCHANGE, 500, subtype = HuiWangSubtype.ADVANCE_PAY),
            tx(2, TxType.EXCHANGE, 500, subtype = HuiWangSubtype.COLLECT)
        )
        assertEquals(0L, stats.exchangeNetCents)
        assertEquals(0L, stats.totalIncomeCents)
        assertEquals(0L, stats.totalExpenseCents)
    }

    @Test
    fun e4_expense_150_refund_100_net_50() {
        val stats = compute(
            tx(1, TxType.EXPENSE, 15_000, categoryL1 = "购物", categoryL2 = "数码"),
            tx(2, TxType.REFUND, 10_000, linkedTxId = 1, subtype = HuiWangSubtype.REFUND_PARTIAL)
        )
        assertEquals(5_000L, stats.baseExpenseCents)
        assertEquals(5_000L, stats.totalExpenseCents)
        assertEquals(0L, stats.totalIncomeCents)
        assertEquals(10_000L, stats.refundAppliedCents)
        assertEquals(10_000L, stats.refundOccurredCents)
        assertEquals(1, stats.expenseRanking.size)
        assertEquals(5_000L, stats.expenseRanking.first().cents)
    }

    @Test
    fun e5_full_refund_hides_category_but_keeps_zero_expense() {
        val stats = compute(
            tx(1, TxType.EXPENSE, 500, categoryL1 = "饮食"),
            tx(2, TxType.REFUND, 500, linkedTxId = 1, subtype = HuiWangSubtype.REFUND_FULL)
        )
        assertEquals(0L, stats.baseExpenseCents)
        assertEquals(0L, stats.totalExpenseCents)
        assertTrue(stats.expenseRanking.isEmpty())
    }

    @Test
    fun e6_multiple_refunds_accumulate() {
        val stats = compute(
            tx(1, TxType.EXPENSE, 15_000),
            tx(2, TxType.REFUND, 4_000, linkedTxId = 1, subtype = HuiWangSubtype.REFUND_PARTIAL),
            tx(3, TxType.REFUND, 6_000, linkedTxId = 1, subtype = HuiWangSubtype.REFUND_PARTIAL)
        )
        assertEquals(5_000L, stats.baseExpenseCents)
        assertEquals(10_000L, stats.refundAppliedCents)
    }

    @Test
    fun e7_cross_month_refund_belongs_to_original_expense_month() {
        val txs = arrayOf(
            tx(1, TxType.EXPENSE, 15_000, date = "2026-09-12"),
            tx(2, TxType.REFUND, 10_000, date = "2026-10-03", linkedTxId = 1)
        )
        val september = compute(*txs, range = sep)
        assertEquals(5_000L, september.baseExpenseCents)
        assertEquals(10_000L, september.refundAppliedCents)
        assertEquals(0L, september.refundOccurredCents)

        val october = compute(*txs, range = oct)
        assertEquals(0L, october.baseExpenseCents)
        assertEquals(0L, october.totalExpenseCents)
        assertEquals(0L, october.refundAppliedCents)
        assertEquals(10_000L, october.refundOccurredCents)
        assertFalse(october.hasRefundAppliedInPeriod)
        assertTrue(october.hasRefundInPeriod)
    }

    @Test
    fun e8_unlinked_refund_is_pending_and_not_deducted() {
        val stats = compute(
            tx(1, TxType.EXPENSE, 15_000),
            tx(2, TxType.REFUND, 10_000, linkedTxId = null, subtype = HuiWangSubtype.REFUND_PARTIAL)
        )
        assertEquals(15_000L, stats.baseExpenseCents)
        assertEquals(0L, stats.refundAppliedCents)
        assertEquals(10_000L, stats.refundOccurredCents)
        assertEquals(1, stats.pendingLinkCount)
    }

    @Test
    fun e9_refund_and_exchange_are_not_double_counted() {
        val stats = compute(
            tx(1, TxType.EXPENSE, 15_000),
            tx(2, TxType.REFUND, 10_000, linkedTxId = 1, subtype = HuiWangSubtype.REFUND_PARTIAL),
            tx(3, TxType.EXCHANGE, 560, subtype = HuiWangSubtype.ADVANCE_PAY),
            tx(4, TxType.EXCHANGE, 300, subtype = HuiWangSubtype.COLLECT)
        )
        assertEquals(5_000L, stats.baseExpenseCents)
        assertEquals(260L, stats.totalExpenseCents - stats.baseExpenseCents)
        assertEquals(5_260L, stats.totalExpenseCents)
        assertEquals(-260L, stats.exchangeNetCents)
        assertEquals(0L, stats.totalIncomeCents)
    }

    @Test
    fun e10_positive_exchange_goes_to_income() {
        val stats = compute(
            tx(1, TxType.EXPENSE, 15_000),
            tx(2, TxType.REFUND, 10_000, linkedTxId = 1, subtype = HuiWangSubtype.REFUND_PARTIAL),
            tx(3, TxType.EXCHANGE, 300, subtype = HuiWangSubtype.ADVANCE_PAY),
            tx(4, TxType.EXCHANGE, 500, subtype = HuiWangSubtype.COLLECT)
        )
        assertEquals(200L, stats.totalIncomeCents)
        assertEquals(5_000L, stats.totalExpenseCents)
        assertEquals(200L, stats.exchangeNetCents)
    }

    @Test
    fun a10_legacy_records_resolve_or_stay_pending() {
        val stats = compute(
            tx(1, TxType.EXPENSE, 15_000),
            tx(2, TxType.REFUND, 10_000, linkedTxId = 1, categoryL1 = "部分退款"),
            tx(3, TxType.EXCHANGE, 560, categoryL1 = "代付"),
            tx(4, TxType.EXCHANGE, 300, categoryL1 = "其他往来")
        )
        assertEquals(5_000L, stats.baseExpenseCents)
        assertEquals(-560L, stats.exchangeNetCents)
        assertEquals(1, stats.pendingConfirmCount)
        assertEquals(0, stats.pendingLinkCount)
    }

    @Test
    fun a11_transfer_never_counts_as_income_or_expense() {
        val stats = compute(
            tx(1, TxType.TRANSFER, 100_000),
            tx(2, TxType.INCOME, 3_000),
            tx(3, TxType.EXPENSE, 1_000)
        )
        assertEquals(3_000L, stats.baseIncomeCents)
        assertEquals(1_000L, stats.baseExpenseCents)
        assertEquals(3_000L, stats.totalIncomeCents)
        assertEquals(1_000L, stats.totalExpenseCents)
        assertEquals(0L, stats.exchangeNetCents)
    }

    @Test
    fun a15_cross_ledger_refund_does_not_deduct() {
        val expense = tx(1, TxType.EXPENSE, 15_000, accountId = 1L)
        val refund = tx(2, TxType.REFUND, 10_000, linkedTxId = 1, accountId = 2L)

        val first = compute(expense, refund, range = oct)
        assertEquals(15_000L, first.baseExpenseCents)
        assertEquals(0, first.pendingLinkCount)

        val second = StatsEngine.compute(2L, listOf(expense, refund), oct)
        assertEquals(0L, second.baseExpenseCents)
        assertEquals(10_000L, second.refundOccurredCents)
        assertEquals(1, second.pendingLinkCount)
    }

    @Test
    fun refund_cannot_make_expense_negative() {
        val stats = compute(
            tx(1, TxType.EXPENSE, 500),
            tx(2, TxType.REFUND, 1_000, linkedTxId = 1, subtype = HuiWangSubtype.REFUND_PARTIAL)
        )
        assertEquals(0L, stats.baseExpenseCents)
        assertEquals(500L, stats.refundAppliedCents)
        assertTrue(stats.expenseRanking.isEmpty())
    }

    @Test
    fun amounts_are_normalised_to_positive() {
        val stats = compute(
            tx(1, TxType.EXPENSE, -1_000),
            tx(2, TxType.REFUND, -500, linkedTxId = 1)
        )
        assertEquals(500L, stats.baseExpenseCents)
        assertEquals(500L, stats.refundAppliedCents)
    }

    @Test
    fun expense_ranking_is_net_and_sorted() {
        val stats = compute(
            tx(1, TxType.EXPENSE, 10_000, categoryL1 = "饮食"),
            tx(2, TxType.EXPENSE, 3_000, categoryL1 = "购物"),
            tx(3, TxType.REFUND, 4_000, linkedTxId = 1),
            tx(4, TxType.INCOME, 8_000, categoryL1 = "红包")
        )
        assertEquals(2, stats.expenseRanking.size)
        assertEquals("饮食", stats.expenseRanking[0].l1)
        assertEquals(6_000L, stats.expenseRanking[0].cents)
        assertEquals(3_000L, stats.expenseRanking[1].cents)
        assertEquals(8_000L, stats.incomeRanking.single().cents)
    }

    @Test
    fun pending_counts_are_ledger_wide_not_only_current_month() {
        val stats = compute(
            tx(1, TxType.REFUND, 1_000, date = "2026-01-01"),
            tx(2, TxType.EXCHANGE, 1_000, date = "2026-01-02", categoryL1 = "其他往来")
        )
        assertEquals(1, stats.pendingLinkCount)
        assertEquals(1, stats.pendingConfirmCount)
    }

    @Test
    fun invalid_link_to_refund_is_not_deducted() {
        val stats = compute(
            tx(1, TxType.EXPENSE, 10_000),
            tx(2, TxType.REFUND, 10_000, linkedTxId = 1),
            tx(3, TxType.REFUND, 5_000, linkedTxId = 2)
        )
        // 链到退款上的退款不抵扣，且只算“本期发生退款”，同时进入待关联计数
        assertEquals(0L, stats.baseExpenseCents)
        assertEquals(15_000L, stats.refundOccurredCents)
        assertEquals(1, stats.pendingLinkCount)
    }
}
