package com.kumquat.ledger.domain

import com.kumquat.ledger.data.Tx

/** 资金方向。退款虽然让钱回到账户，但不参与回往净额，因此单独标为 [NONE]。 */
enum class FundDirection { IN, OUT, NONE }

/**
 * 回往的六个方向。
 * 底层 type 仍沿用 REFUND / EXCHANGE，具体方向只存在 [Tx.huiwangSubtype]。
 */
enum class HuiWangSubtype(
    val label: String,
    val direction: FundDirection,
    val isRefund: Boolean
) {
    REFUND_FULL("全额退款", FundDirection.NONE, true),
    REFUND_PARTIAL("部分退款", FundDirection.NONE, true),
    ADVANCE_PAY("代付", FundDirection.OUT, false),
    LEND("借出", FundDirection.OUT, false),
    COLLECT("收回", FundDirection.IN, false),
    REPAY("还款", FundDirection.IN, false);

    companion object {
        /** UI 顺序固定：全额退款、部分退款、代付、借出、收回、还款。 */
        val uiOrder: List<HuiWangSubtype> = listOf(
            REFUND_FULL, REFUND_PARTIAL, ADVANCE_PAY, LEND, COLLECT, REPAY
        )

        fun parse(raw: String?): HuiWangSubtype? {
            val value = raw?.trim()?.uppercase().orEmpty()
            if (value.isEmpty()) return null
            return entries.firstOrNull { it.name == value }
        }

        /**
         * 旧 v1.1 记录的 EXCHANGE 方向只能从分类名推断。
         * 只认明确的四个方向词，识别不了就返回 null，绝不猜方向。
         */
        fun fromLegacyCategory(categoryL1: String): HuiWangSubtype? {
            val name = categoryL1.trim()
            if (name.isEmpty()) return null
            return when {
                "代付" in name -> ADVANCE_PAY
                "借出" in name -> LEND
                "收回" in name -> COLLECT
                "还款" in name -> REPAY
                else -> null
            }
        }

        /** 从旧分类名推断退款细分；无法判断时返回 null（仍按“历史退款”处理）。 */
        fun inferRefund(categoryL1: String): HuiWangSubtype? {
            val name = categoryL1.trim()
            return when {
                "全额" in name -> REFUND_FULL
                "部分" in name -> REFUND_PARTIAL
                else -> null
            }
        }

        fun inferFor(type: TxType, categoryL1: String): HuiWangSubtype? = when (type) {
            TxType.REFUND -> inferRefund(categoryL1)
            TxType.EXCHANGE -> fromLegacyCategory(categoryL1)
            else -> null
        }
    }
}

enum class HuiWangStatus {
    /** 方向明确，可参与统计。 */
    OK,

    /** 退款没有有效原账单：待关联，不参与统计。 */
    PENDING_LINK,

    /** 旧往来无法判断方向：待确认，不参与净额。 */
    PENDING_CONFIRM
}

data class HuiWangResolution(
    val status: HuiWangStatus,
    val subtype: HuiWangSubtype?,
    val direction: FundDirection,
    /** 方向来自旧分类推断或缺少 subtype 字段。 */
    val legacy: Boolean
) {
    val pending: Boolean get() = status != HuiWangStatus.OK

    /** 是否参与回往净额：退款不参与；待关联/待确认不参与。 */
    val countsInExchangeNet: Boolean
        get() = status == HuiWangStatus.OK &&
            subtype?.isRefund == false &&
            direction != FundDirection.NONE
}

/**
 * 旧记录解析器。
 *
 * - REFUND + 有效原出账账单：保留原金额，按净额冲减原支出；缺 subtype 时从旧分类名推断。
 * - REFUND 无关联/关联失效/跨账本：待关联，不计入统计。
 * - EXCHANGE + 明确 subtype：按 subtype。
 * - EXCHANGE 缺 subtype：尝试旧分类名推断；仍无法判断则待确认。
 *
 * 绝不根据金额正负猜测方向；旧金额先取绝对值。
 */
object LegacyHuiWangResolver {

    fun resolve(tx: Tx, linkedTx: Tx?): HuiWangResolution = when (TxType.parse(tx.type)) {
        TxType.REFUND -> resolveRefund(tx, linkedTx)
        TxType.EXCHANGE -> resolveExchange(tx)
        else -> HuiWangResolution(HuiWangStatus.OK, null, FundDirection.NONE, legacy = false)
    }

    private fun resolveRefund(tx: Tx, linkedTx: Tx?): HuiWangResolution {
        val declared = HuiWangSubtype.parse(tx.huiwangSubtype)?.takeIf { it.isRefund }
        val subtype = declared ?: HuiWangSubtype.inferRefund(tx.categoryL1)
        val legacy = declared == null
        val linkValid = linkedTx != null &&
            TxType.parse(linkedTx.type) == TxType.EXPENSE &&
            linkedTx.accountId == tx.accountId
        return if (linkValid) {
            HuiWangResolution(HuiWangStatus.OK, subtype, FundDirection.NONE, legacy)
        } else {
            HuiWangResolution(HuiWangStatus.PENDING_LINK, subtype, FundDirection.NONE, legacy)
        }
    }

    private fun resolveExchange(tx: Tx): HuiWangResolution {
        val raw = tx.huiwangSubtype?.trim().orEmpty()
        val declared = HuiWangSubtype.parse(raw)?.takeIf { !it.isRefund }
        if (declared != null) {
            return HuiWangResolution(HuiWangStatus.OK, declared, declared.direction, legacy = false)
        }
        val inferred = HuiWangSubtype.fromLegacyCategory(tx.categoryL1)
        if (inferred != null) {
            return HuiWangResolution(HuiWangStatus.OK, inferred, inferred.direction, legacy = true)
        }
        return HuiWangResolution(
            status = HuiWangStatus.PENDING_CONFIRM,
            subtype = null,
            direction = FundDirection.NONE,
            legacy = raw.isEmpty()
        )
    }
}
