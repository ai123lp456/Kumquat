package com.kumquat.ledger

import com.kumquat.ledger.data.Tx
import com.kumquat.ledger.domain.FundDirection
import com.kumquat.ledger.domain.HuiWangStatus
import com.kumquat.ledger.domain.HuiWangSubtype
import com.kumquat.ledger.domain.LegacyHuiWangResolver
import com.kumquat.ledger.domain.TxType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 旧记录方向解析。 */
class HuiWangResolverTest {

    private fun tx(
        id: Long = 0L,
        type: TxType,
        amountCents: Long = 1_000L,
        date: String = "2026-10-01",
        accountId: Long = 1L,
        linkedTxId: Long? = null,
        subtype: HuiWangSubtype? = null,
        categoryL1: String = ""
    ) = Tx(
        id = id,
        accountId = accountId,
        type = type.name,
        categoryL1 = categoryL1,
        categoryL2 = "",
        amountCents = amountCents,
        date = date,
        linkedTxId = linkedTxId,
        huiwangSubtype = subtype?.name,
        createdAt = 0L,
        updatedAt = 0L
    )

    @Test
    fun refund_with_valid_expense_link_is_counted() {
        val expense = tx(id = 10L, type = TxType.EXPENSE, amountCents = 15_000L)
        val refund = tx(
            id = 11L,
            type = TxType.REFUND,
            amountCents = 10_000L,
            linkedTxId = 10L,
            subtype = HuiWangSubtype.REFUND_PARTIAL
        )
        val resolved = LegacyHuiWangResolver.resolve(refund, expense)
        assertEquals(HuiWangStatus.OK, resolved.status)
        assertEquals(HuiWangSubtype.REFUND_PARTIAL, resolved.subtype)
        assertEquals(FundDirection.NONE, resolved.direction)
        assertFalse(resolved.legacy)
        assertFalse(resolved.countsInExchangeNet)
    }

    @Test
    fun refund_without_link_is_pending() {
        val refund = tx(type = TxType.REFUND, linkedTxId = null)
        val resolved = LegacyHuiWangResolver.resolve(refund, null)
        assertEquals(HuiWangStatus.PENDING_LINK, resolved.status)
        assertTrue(resolved.pending)
        assertFalse(resolved.countsInExchangeNet)
    }

    @Test
    fun refund_linked_to_income_is_pending() {
        val income = tx(id = 10L, type = TxType.INCOME)
        val refund = tx(type = TxType.REFUND, linkedTxId = 10L)
        assertEquals(HuiWangStatus.PENDING_LINK, LegacyHuiWangResolver.resolve(refund, income).status)
    }

    @Test
    fun refund_linked_to_other_ledger_is_pending() {
        val expense = tx(id = 10L, type = TxType.EXPENSE, accountId = 2L)
        val refund = tx(type = TxType.REFUND, accountId = 1L, linkedTxId = 10L)
        assertEquals(HuiWangStatus.PENDING_LINK, LegacyHuiWangResolver.resolve(refund, expense).status)
    }

    @Test
    fun legacy_refund_infers_full_or_partial_from_category() {
        val expense = tx(id = 10L, type = TxType.EXPENSE)
        val refund = tx(
            id = 11L,
            type = TxType.REFUND,
            linkedTxId = 10L,
            categoryL1 = "部分退款"
        )
        val resolved = LegacyHuiWangResolver.resolve(refund, expense)
        assertEquals(HuiWangStatus.OK, resolved.status)
        assertEquals(HuiWangSubtype.REFUND_PARTIAL, resolved.subtype)
        assertTrue(resolved.legacy)
    }

    @Test
    fun explicit_exchange_subtype_wins() {
        val tx = tx(type = TxType.EXCHANGE, subtype = HuiWangSubtype.COLLECT, categoryL1 = "代付")
        val resolved = LegacyHuiWangResolver.resolve(tx, null)
        assertEquals(HuiWangStatus.OK, resolved.status)
        assertEquals(HuiWangSubtype.COLLECT, resolved.subtype)
        assertEquals(FundDirection.IN, resolved.direction)
        assertFalse(resolved.legacy)
        assertTrue(resolved.countsInExchangeNet)
    }

    @Test
    fun legacy_exchange_categories_are_recognised() {
        val cases = mapOf(
            "代付" to HuiWangSubtype.ADVANCE_PAY,
            "借出" to HuiWangSubtype.LEND,
            "收回" to HuiWangSubtype.COLLECT,
            "还款" to HuiWangSubtype.REPAY
        )
        cases.forEach { (name, expected) ->
            val resolved = LegacyHuiWangResolver.resolve(
                tx(type = TxType.EXCHANGE, categoryL1 = name), null
            )
            assertEquals("分类 $name", HuiWangStatus.OK, resolved.status)
            assertEquals("分类 $name", expected, resolved.subtype)
            assertTrue("分类 $name 应标记为历史记录", resolved.legacy)
        }
    }

    @Test
    fun unknown_exchange_direction_must_not_be_guessed() {
        listOf("借入", "其他往来", "自定义分类", "").forEach { name ->
            val resolved = LegacyHuiWangResolver.resolve(
                tx(type = TxType.EXCHANGE, categoryL1 = name), null
            )
            assertEquals("分类 $name", HuiWangStatus.PENDING_CONFIRM, resolved.status)
            assertEquals(FundDirection.NONE, resolved.direction)
            assertFalse(resolved.countsInExchangeNet)
        }
    }

    @Test
    fun unknown_subtype_falls_back_to_legacy_category() {
        val raw = tx(type = TxType.EXCHANGE, categoryL1 = "代付").copy(huiwangSubtype = "NOT_A_DIRECTION")
        val resolved = LegacyHuiWangResolver.resolve(raw, null)
        assertEquals(HuiWangStatus.OK, resolved.status)
        assertEquals(HuiWangSubtype.ADVANCE_PAY, resolved.subtype)
        assertTrue(resolved.legacy)
    }

    @Test
    fun refund_subtype_on_exchange_is_ignored_and_category_used() {
        val raw = tx(type = TxType.EXCHANGE, categoryL1 = "收回")
            .copy(huiwangSubtype = HuiWangSubtype.REFUND_FULL.name)
        val resolved = LegacyHuiWangResolver.resolve(raw, null)
        assertEquals(HuiWangSubtype.COLLECT, resolved.subtype)
        assertEquals(FundDirection.IN, resolved.direction)
    }

    @Test
    fun ordinary_types_resolve_to_none() {
        listOf(TxType.INCOME, TxType.EXPENSE, TxType.TRANSFER).forEach { type ->
            val resolved = LegacyHuiWangResolver.resolve(tx(type = type), null)
            assertEquals(HuiWangStatus.OK, resolved.status)
            assertEquals(FundDirection.NONE, resolved.direction)
            assertFalse(resolved.countsInExchangeNet)
        }
    }
}
