package com.kumquat.ledger.domain

/**
 * 五种账目类型。数据层固定枚举，UI 显示名走 [label]，不得混用。
 * TRANSFER（内部转账）为 v1.1 新增：只搬钱、不计收支。
 */
enum class TxType(val label: String) {
    INCOME("入账"),
    EXPENSE("出账"),
    REFUND("回冲"),
    EXCHANGE("往来"),
    TRANSFER("转账");

    /** 是否计入收支统计（只有入账/出账计入；回冲用于抵扣出账，往来与转账完全不计）。 */
    val affectsBalance: Boolean get() = this == INCOME || this == EXPENSE

    /** 是否为「资产账户之间的内部转账」：必须同时有转出/转入账户，绝不计入收支。 */
    val isTransfer: Boolean get() = this == TRANSFER

    companion object {
        fun parse(raw: String?): TxType? {
            val v = raw?.trim()?.uppercase() ?: return null
            return entries.firstOrNull { it.name == v }
        }

        val ordered: List<TxType> = listOf(INCOME, EXPENSE, REFUND, EXCHANGE, TRANSFER)
    }
}
