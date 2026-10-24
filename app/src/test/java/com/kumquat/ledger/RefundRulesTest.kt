package com.kumquat.ledger

import com.kumquat.ledger.data.Tx
import com.kumquat.ledger.domain.HuiWangSubtype
import com.kumquat.ledger.domain.RefundRules
import com.kumquat.ledger.domain.TxType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 退款关联与剩余可退。 */
class RefundRulesTest {

    private fun tx(
        id: Long,
        type: TxType,
        amountCents: Long,
        date: String = "2026-10-01",
        accountId: Long = 1L,
        linkedTxId: Long? = null,
        categoryL1: String = "测试"
    ) = Tx(
        id = id,
        accountId = accountId,
        type = type.name,
        categoryL1 = categoryL1,
        categoryL2 = "",
        amountCents = amountCents,
        date = date,
        linkedTxId = linkedTxId,
        createdAt = 0L,
        updatedAt = 0L
    )

    private fun draft(
        amountCents: Long,
        date: String = "2026-10-05",
        linkedTxId: Long? = 1L,
        accountId: Long = 1L,
        subtype: HuiWangSubtype? = HuiWangSubtype.REFUND_PARTIAL,
        id: Long = 0L
    ) = RefundRules.RefundDraft(accountId, linkedTxId, amountCents, date, subtype, id)

    @Test
    fun summary_accumulates_refunds() {
        val expense = tx(1, TxType.EXPENSE, 15_000)
        val all = listOf(
            expense,
            tx(2, TxType.REFUND, 4_000, linkedTxId = 1),
            tx(3, TxType.REFUND, 6_000, linkedTxId = 1)
        )
        val summary = RefundRules.summarize(expense, all)
        assertEquals(15_000L, summary.originalCents)
        assertEquals(10_000L, summary.refundedCents)
        assertEquals(5_000L, summary.remainingCents)
        assertEquals(2, summary.refundCount)
    }

    @Test
    fun over_refund_is_rejected_with_remaining_amount() {
        val expense = tx(1, TxType.EXPENSE, 15_000)
        val all = listOf(expense, tx(2, TxType.REFUND, 10_000, linkedTxId = 1))
        val error = RefundRules.validate(expense, draft(6_000), all)
        assertNotNull(error)
        assertTrue(error!!.contains("50.00"))
    }

    @Test
    fun full_refund_must_equal_remaining() {
        val expense = tx(1, TxType.EXPENSE, 15_000)
        val all = listOf(expense)
        assertNull(
            RefundRules.validate(expense, draft(15_000, subtype = HuiWangSubtype.REFUND_FULL), all)
        )
        val error = RefundRules.validate(
            expense,
            draft(10_000, subtype = HuiWangSubtype.REFUND_FULL),
            all
        )
        assertNotNull(error)
    }

    @Test
    fun refund_date_cannot_precede_original() {
        val expense = tx(1, TxType.EXPENSE, 15_000, date = "2026-10-10")
        val error = RefundRules.validate(expense, draft(1_000, date = "2026-10-09"), listOf(expense))
        assertNotNull(error)
        assertTrue(error!!.contains("不能早于"))
    }

    @Test
    fun refund_must_target_expense_and_same_ledger() {
        val income = tx(1, TxType.INCOME, 15_000)
        assertNotNull(RefundRules.validate(income, draft(1_000), listOf(income)))

        val expense = tx(1, TxType.EXPENSE, 15_000, accountId = 2L)
        val error = RefundRules.validate(expense, draft(1_000, accountId = 1L), listOf(expense))
        assertNotNull(error)
        assertTrue(error!!.contains("同一账本"))
    }

    @Test
    fun unlinked_refund_requires_link_unless_pending_allowed() {
        val error = RefundRules.validate(null, draft(1_000, linkedTxId = null), emptyList())
        assertNotNull(error)
        assertNull(
            RefundRules.validate(
                null,
                draft(1_000, linkedTxId = null),
                emptyList(),
                allowPending = true
            )
        )
    }

    @Test
    fun editing_refund_excludes_itself_from_used_amount() {
        val expense = tx(1, TxType.EXPENSE, 10_000)
        val refund = tx(2, TxType.REFUND, 10_000, linkedTxId = 1)
        val all = listOf(expense, refund)
        assertNull(
            RefundRules.validate(
                expense,
                draft(10_000, id = 2),
                all
            )
        )
        // 若再加一笔新退款，则剩余额度已经是 0
        assertNotNull(RefundRules.validate(expense, draft(1_000), all))
    }

    @Test
    fun expense_amount_cannot_drop_below_refunded() {
        val expense = tx(1, TxType.EXPENSE, 15_000)
        val refunds = listOf(expense, tx(2, TxType.REFUND, 10_000, linkedTxId = 1))
        assertNotNull(RefundRules.validateExpenseAmount(expense, 9_000, refunds))
        assertNull(RefundRules.validateExpenseAmount(expense, 10_000, refunds))
        assertNull(RefundRules.validateExpenseAmount(expense, 15_000, refunds))
    }

    @Test
    fun deleting_original_with_refunds_is_blocked() {
        val expense = tx(1, TxType.EXPENSE, 15_000)
        val all = listOf(expense, tx(2, TxType.REFUND, 1_000, linkedTxId = 1))
        assertNotNull(RefundRules.deleteBlockedReason(expense, all))
        assertNull(RefundRules.deleteBlockedReason(expense, listOf(expense)))
    }

    @Test
    fun outstanding_amount_only_counts_incoming_collections() {
        val advance = tx(1, TxType.EXCHANGE, 5_000, categoryL1 = "代付")
        val all = listOf(
            advance,
            tx(2, TxType.EXCHANGE, 3_000, linkedTxId = 1, categoryL1 = "收回"),
            tx(3, TxType.EXCHANGE, 1_000, linkedTxId = 1, categoryL1 = "代付")
        )
        assertEquals(2_000L, RefundRules.outstandingCents(advance, all))
    }

    @Test
    fun effective_amount_uses_absolute_value() {
        assertEquals(1_500L, RefundRules.amountOf(tx(1, TxType.REFUND, -1_500)))
    }

    @Test
    fun badges_mark_partial_and_full_refunds() {
        val partial = tx(1, TxType.EXPENSE, 15_000)
        val full = tx(2, TxType.EXPENSE, 500)
        val all = listOf(
            partial,
            full,
            tx(3, TxType.REFUND, 4_000, linkedTxId = 1),
            tx(4, TxType.REFUND, 6_000, linkedTxId = 1),
            tx(5, TxType.REFUND, 500, linkedTxId = 2)
        )
        val badges = RefundRules.badges(all)
        assertEquals(10_000L, badges.getValue(1L).refundedCents)
        assertTrue(!badges.getValue(1L).fully)
        assertTrue(badges.getValue(2L).fully)
    }

    @Test
    fun badges_ignore_cross_ledger_link() {
        val expense = tx(1, TxType.EXPENSE, 5_000, accountId = 1L)
        val refund = tx(2, TxType.REFUND, 5_000, linkedTxId = 1, accountId = 2L)
        assertTrue(RefundRules.badges(listOf(expense, refund)).isEmpty())
    }
}
