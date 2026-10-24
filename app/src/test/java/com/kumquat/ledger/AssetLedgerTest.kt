package com.kumquat.ledger

import com.kumquat.ledger.data.Tx
import com.kumquat.ledger.domain.AssetLedger
import com.kumquat.ledger.domain.HuiWangSubtype
import com.kumquat.ledger.domain.TxType
import org.junit.Assert.assertEquals
import org.junit.Test

/** 资产余额联动。 */
class AssetLedgerTest {

    private fun tx(
        id: Long,
        type: TxType,
        amountCents: Long,
        assetId: Long? = 1L,
        toAssetId: Long? = null,
        subtype: HuiWangSubtype? = null,
        categoryL1: String = "",
        accountId: Long = 1L
    ) = Tx(
        id = id,
        accountId = accountId,
        type = type.name,
        categoryL1 = categoryL1,
        categoryL2 = "",
        amountCents = amountCents,
        date = "2026-10-10",
        assetId = assetId,
        toAssetId = toAssetId,
        huiwangSubtype = subtype?.name,
        createdAt = 0L,
        updatedAt = 0L
    )

    @Test
    fun six_huiwang_directions_affect_balance_with_required_sign() {
        assertEquals(
            10_000L,
            AssetLedger.effectCents(tx(1, TxType.REFUND, 10_000, subtype = HuiWangSubtype.REFUND_FULL))
        )
        assertEquals(
            10_000L,
            AssetLedger.effectCents(tx(2, TxType.REFUND, 10_000, subtype = HuiWangSubtype.REFUND_PARTIAL))
        )
        assertEquals(
            -5_600L,
            AssetLedger.effectCents(tx(3, TxType.EXCHANGE, 5_600, subtype = HuiWangSubtype.ADVANCE_PAY))
        )
        assertEquals(
            -5_600L,
            AssetLedger.effectCents(tx(4, TxType.EXCHANGE, 5_600, subtype = HuiWangSubtype.LEND))
        )
        assertEquals(
            3_000L,
            AssetLedger.effectCents(tx(5, TxType.EXCHANGE, 3_000, subtype = HuiWangSubtype.COLLECT))
        )
        assertEquals(
            3_000L,
            AssetLedger.effectCents(tx(6, TxType.EXCHANGE, 3_000, subtype = HuiWangSubtype.REPAY))
        )
    }

    @Test
    fun unlinked_refund_still_increases_balance() {
        assertEquals(10_000L, AssetLedger.effectCents(tx(1, TxType.REFUND, 10_000)))
    }

    @Test
    fun legacy_known_direction_is_applied() {
        assertEquals(
            -800L,
            AssetLedger.effectCents(tx(1, TxType.EXCHANGE, 800, categoryL1 = "代付"))
        )
    }

    @Test
    fun unknown_legacy_direction_changes_nothing() {
        assertEquals(
            0L,
            AssetLedger.effectCents(tx(1, TxType.EXCHANGE, 800, categoryL1 = "其他往来"))
        )
        assertEquals(
            0L,
            AssetLedger.effectCents(tx(2, TxType.EXCHANGE, 800, categoryL1 = "借入"))
        )
    }

    @Test
    fun transfer_moves_money_between_two_assets() {
        val txs = listOf(
            tx(1, TxType.TRANSFER, 100_000, assetId = 1L, toAssetId = 2L)
        )
        val deltas = AssetLedger.deltas(txs)
        assertEquals(-100_000L, deltas[1L])
        assertEquals(100_000L, deltas[2L])
    }

    @Test
    fun balance_is_initial_plus_all_effects() {
        val txs = listOf(
            tx(1, TxType.INCOME, 5_000, assetId = 1L),
            tx(2, TxType.EXPENSE, 1_500, assetId = 1L),
            tx(3, TxType.REFUND, 500, assetId = 1L),
            tx(4, TxType.EXCHANGE, 300, assetId = 1L, subtype = HuiWangSubtype.ADVANCE_PAY),
            tx(5, TxType.EXCHANGE, 100, assetId = 1L, subtype = HuiWangSubtype.COLLECT)
        )
        assertEquals(3_800L, AssetLedger.balance(0L, 1L, txs))
    }

    @Test
    fun records_without_asset_do_not_change_any_balance() {
        val txs = listOf(tx(1, TxType.EXPENSE, 1_000, assetId = null))
        assertEquals(0L, AssetLedger.balance(0L, 1L, txs))
    }

    @Test
    fun negative_legacy_amount_uses_absolute_value() {
        assertEquals(-500L, AssetLedger.effectCents(tx(1, TxType.EXPENSE, -500)))
        assertEquals(500L, AssetLedger.effectCents(tx(2, TxType.REFUND, -500)))
    }
}
