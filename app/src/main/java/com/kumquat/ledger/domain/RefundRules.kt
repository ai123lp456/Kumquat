package com.kumquat.ledger.domain

import com.kumquat.ledger.data.Tx

/**
 * 退款关联规则的唯一实现。
 *
 * UI 与仓库层都调用这里，避免“界面拦一次、数据库再写一套”。
 * 所有方法都是纯函数，返回 null 表示通过，否则返回可直接展示的中文错误。
 */
object RefundRules {

    /** 列表用：原出账已退款金额与是否全额。 */
    data class Badge(val refundedCents: Long, val fully: Boolean)

    /** 按原出账汇总关联退款；不同账本的关联不计入。 */
    fun badges(transactions: List<Tx>): Map<Long, Badge> {
        val refunds = transactions
            .filter { TxType.parse(it.type) == TxType.REFUND && it.linkedTxId != null }
            .groupBy { it.linkedTxId!! }
        return buildMap {
            transactions.forEach { tx ->
                if (TxType.parse(tx.type) != TxType.EXPENSE) return@forEach
                val list = refunds[tx.id].orEmpty().filter { it.accountId == tx.accountId }
                if (list.isEmpty()) return@forEach
                val refunded = list.sumOf { amountOf(it) }
                val amount = amountOf(tx)
                put(tx.id, Badge(minOf(refunded, amount), refunded >= amount))
            }
        }
    }

    data class RefundSummary(
        val originalCents: Long,
        val refundedCents: Long,
        val remainingCents: Long,
        val refundCount: Int
    ) {
        val fullyRefunded: Boolean get() = originalCents > 0L && remainingCents <= 0L
    }

    /** 退款草稿。金额一律正数；[linkedTxId] 为空表示“待关联”。 */
    data class RefundDraft(
        val accountId: Long,
        val linkedTxId: Long?,
        val amountCents: Long,
        val date: String,
        val subtype: HuiWangSubtype? = null,
        /** 编辑已有退款时传自身 id，用于把自身从“已退金额”里排除。 */
        val id: Long = 0L
    )

    fun amountOf(tx: Tx): Long = if (tx.amountCents < 0L) -tx.amountCents else tx.amountCents

    /** 某笔原账单的有效关联退款（同账本、正数、排除指定退款）。 */
    fun linkedRefunds(expenseId: Long, all: List<Tx>, excludeRefundId: Long? = null): List<Tx> =
        all.filter { tx ->
            TxType.parse(tx.type) == TxType.REFUND &&
                tx.linkedTxId == expenseId &&
                (excludeRefundId == null || tx.id != excludeRefundId)
        }

    fun summarize(expense: Tx, all: List<Tx>, excludeRefundId: Long? = null): RefundSummary {
        val refunds = linkedRefunds(expense.id, all, excludeRefundId)
            .filter { it.accountId == expense.accountId }
        val refunded = refunds.sumOf { amountOf(it) }
        val original = amountOf(expense)
        return RefundSummary(
            originalCents = original,
            refundedCents = refunded,
            remainingCents = (original - refunded).coerceAtLeast(0L),
            refundCount = refunds.size
        )
    }

    /**
     * 校验一笔退款。
     *
     * [allowPending] 为 true 时允许“无关联原账单”保存为待关联；
     * 但一旦提供了原账单，就必须满足同账本、原类型为出账、金额不超剩余、日期不早于原账单。
     */
    fun validate(
        original: Tx?,
        draft: RefundDraft,
        all: List<Tx>,
        allowPending: Boolean = false
    ): String? {
        if (draft.linkedTxId == null) {
            return if (allowPending) null else "退款必须关联原出账账单"
        }
        if (original == null) return "原账单不存在或已被删除，请重新选择"
        if (TxType.parse(original.type) != TxType.EXPENSE) return "退款只能关联出账账单"
        if (original.accountId != draft.accountId) return "退款与原账单不在同一账本"
        if (draft.amountCents <= 0L) return "退款金额必须大于 0"
        if (draft.date < original.date) return "退款日期不能早于原账单日期（${original.date}）"
        draft.subtype?.let {
            if (!it.isRefund) return "退款方向只能是全额退款或部分退款"
        }

        val summary = summarize(original, all, excludeRefundId = draft.id.takeIf { it > 0L })
        if (summary.remainingCents <= 0L) return "该账单已全额退款，不能再退"
        if (draft.amountCents > summary.remainingCents) {
            return "最多可退 ¥${Money.format(summary.remainingCents)}"
        }
        if (draft.subtype == HuiWangSubtype.REFUND_FULL && draft.amountCents != summary.remainingCents) {
            return "全额退款金额必须等于剩余可退金额 ¥${Money.format(summary.remainingCents)}"
        }
        return null
    }

    /** 编辑原出账金额时的校验：不能低于已退款合计，否则退款会变成“负支出”。 */
    fun validateExpenseAmount(expense: Tx, newAmountCents: Long, all: List<Tx>): String? {
        if (newAmountCents <= 0L) return "出账金额必须大于 0"
        val refunded = summarize(expense, all).refundedCents
        if (refunded > newAmountCents) {
            return "该账单已退款 ¥${Money.format(refunded)}，金额不能低于已退金额"
        }
        return null
    }

    /** 删除原出账账单时的拦截原因；没有关联退款返回 null。 */
    fun deleteBlockedReason(expense: Tx, all: List<Tx>): String? {
        val summary = summarize(expense, all)
        if (summary.refundCount == 0) return null
        return "该账单有 ${summary.refundCount} 笔关联退款，请先删除或调整退款后再删除"
    }

    /** 收回/还款关联原代付/借出时的剩余未收回金额，仅用于提示，不强制拦截。 */
    fun outstandingCents(origin: Tx, all: List<Tx>, excludeTxId: Long? = null): Long {
        val collected = all.filter { tx ->
            TxType.parse(tx.type) == TxType.EXCHANGE &&
                tx.linkedTxId == origin.id &&
                tx.accountId == origin.accountId &&
                (excludeTxId == null || tx.id != excludeTxId) &&
                LegacyHuiWangResolver.resolve(tx, null).direction == FundDirection.IN
        }.sumOf { amountOf(it) }
        return (amountOf(origin) - collected).coerceAtLeast(0L)
    }
}
