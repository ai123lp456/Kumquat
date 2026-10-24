package com.kumquat.ledger.domain

import com.kumquat.ledger.data.Tx

/**
 * 资产余额的唯一计算实现。
 *
 * 余额不落库，始终由「初始余额 + 全部流水增量」实时算出。
 * 因此编辑/删除回往记录不需要手工“撤销旧余额”，重算即可，天然避免双写不一致。
 *
 * 方向：
 * - INCOME + / EXPENSE - / REFUND + / TRANSFER 转出 -、转入 +
 * - EXCHANGE：代付、借出 -；收回、还款 +；待确认方向一律 0（不猜）
 */
object AssetLedger {

    /** 单笔账单对资产的影响；转账请用 [deltas]，因为一笔转账影响两个账户。 */
    fun effectCents(tx: Tx): Long {
        val amount = if (tx.amountCents < 0L) -tx.amountCents else tx.amountCents
        return when (TxType.parse(tx.type)) {
            TxType.INCOME -> amount
            TxType.EXPENSE -> -amount
            TxType.REFUND -> amount
            TxType.TRANSFER -> -amount
            TxType.EXCHANGE -> when (LegacyHuiWangResolver.resolve(tx, null).direction) {
                FundDirection.IN -> amount
                FundDirection.OUT -> -amount
                FundDirection.NONE -> 0L
            }
            null -> 0L
        }
    }

    /** 全部账单对各资产账户的净增量；转账会同时写转出与转入两个账户。 */
    fun deltas(transactions: List<Tx>): Map<Long, Long> {
        val result = HashMap<Long, Long>()
        transactions.forEach { tx ->
            if (TxType.parse(tx.type) == TxType.TRANSFER) {
                tx.assetId?.let { result.add(it, -tx.amountCents.absolute()) }
                tx.toAssetId?.let { result.add(it, tx.amountCents.absolute()) }
            } else {
                tx.assetId?.let { result.add(it, effectCents(tx)) }
            }
        }
        return result
    }

    fun balance(initialBalanceCents: Long, assetId: Long, transactions: List<Tx>): Long =
        initialBalanceCents + (deltas(transactions)[assetId] ?: 0L)

    private fun Long.absolute(): Long = if (this < 0L) -this else this

    private fun MutableMap<Long, Long>.add(assetId: Long, delta: Long) {
        if (delta == 0L) return
        this[assetId] = (this[assetId] ?: 0L) + delta
    }
}
