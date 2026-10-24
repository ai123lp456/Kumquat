package com.kumquat.ledger.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.SyncAlt
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.kumquat.ledger.data.Tx
import com.kumquat.ledger.domain.HuiWangResolution
import com.kumquat.ledger.domain.HuiWangStatus
import com.kumquat.ledger.domain.HuiWangSubtype
import com.kumquat.ledger.domain.LegacyHuiWangResolver
import com.kumquat.ledger.domain.TxType
import com.kumquat.ledger.ui.theme.CandyLilac
import com.kumquat.ledger.ui.theme.CandyMint
import com.kumquat.ledger.ui.theme.CandyPeach
import com.kumquat.ledger.ui.theme.CandyPink
import com.kumquat.ledger.ui.theme.CandyRose
import com.kumquat.ledger.ui.theme.CandySky
import com.kumquat.ledger.ui.theme.CandyViolet
import com.kumquat.ledger.ui.theme.CandyBlue
import com.kumquat.ledger.ui.theme.Exchange
import com.kumquat.ledger.ui.theme.Expense
import com.kumquat.ledger.ui.theme.Income
import com.kumquat.ledger.ui.theme.Transfer

/**
 * 「记一笔」顶部 4 类。底层仍只有 5 个 TxType：
 * REFUND + EXCHANGE 在 UI 与统计层合并为「回往」，具体方向由 [HuiWangSubtype] 表达。
 */
enum class EntryKind(val label: String) {
    INCOME("入账"),
    EXPENSE("出账"),
    HUIWANG("回往"),
    TRANSFER("转账");

    /** 新增/编辑时写入数据库的顶层类型；回往默认用 EXCHANGE，保存时按 subtype 再细分。 */
    fun baseType(): TxType = when (this) {
        INCOME -> TxType.INCOME
        EXPENSE -> TxType.EXPENSE
        HUIWANG -> TxType.EXCHANGE
        TRANSFER -> TxType.TRANSFER
    }

    companion object {
        val ordered: List<EntryKind> = listOf(INCOME, EXPENSE, HUIWANG, TRANSFER)
    }
}

fun TxType.toEntryKind(): EntryKind = when (this) {
    TxType.INCOME -> EntryKind.INCOME
    TxType.EXPENSE -> EntryKind.EXPENSE
    TxType.REFUND, TxType.EXCHANGE -> EntryKind.HUIWANG
    TxType.TRANSFER -> EntryKind.TRANSFER
}

fun EntryKind.semanticColor(): Color = when (this) {
    EntryKind.INCOME -> Income
    EntryKind.EXPENSE -> Expense
    EntryKind.HUIWANG -> Exchange
    EntryKind.TRANSFER -> Transfer
}

fun EntryKind.semanticGradient(): List<Color> = when (this) {
    EntryKind.INCOME -> listOf(CandyMint, CandySky)
    EntryKind.EXPENSE -> listOf(CandyRose, CandyLilac)
    EntryKind.HUIWANG -> listOf(CandyBlue, CandyViolet)
    EntryKind.TRANSFER -> listOf(CandyPeach, CandyPink)
}

fun EntryKind.semanticIcon(): ImageVector = when (this) {
    EntryKind.INCOME -> Icons.Rounded.ArrowDownward
    EntryKind.EXPENSE -> Icons.Rounded.ArrowUpward
    EntryKind.HUIWANG -> Icons.Rounded.SwapHoriz
    EntryKind.TRANSFER -> Icons.Rounded.SyncAlt
}

fun HuiWangSubtype.hint(): String = when (this) {
    HuiWangSubtype.REFUND_FULL -> "退回全部剩余金额"
    HuiWangSubtype.REFUND_PARTIAL -> "只退回一部分"
    HuiWangSubtype.ADVANCE_PAY -> "先替别人付钱"
    HuiWangSubtype.LEND -> "把钱借给别人"
    HuiWangSubtype.COLLECT -> "收回代付/借出的钱"
    HuiWangSubtype.REPAY -> "归还别人的钱"
}

fun HuiWangSubtype.semanticColor(): Color = when (this) {
    HuiWangSubtype.REFUND_FULL, HuiWangSubtype.REFUND_PARTIAL -> Exchange
    HuiWangSubtype.ADVANCE_PAY, HuiWangSubtype.LEND -> Expense
    HuiWangSubtype.COLLECT, HuiWangSubtype.REPAY -> Income
}

/** 只做展示解析；退款关联是否有效需要原始账单，统计层会用带 linkedTx 的版本。 */
fun Tx.huiWangResolution(): HuiWangResolution = LegacyHuiWangResolver.resolve(this, null)

/** 账单列表/详情的主标题：回往显示方向，其余显示分类名。 */
fun Tx.displayLabel(): String {
    val type = TxType.parse(type) ?: return categoryL1
    if (type != TxType.REFUND && type != TxType.EXCHANGE) {
        return com.kumquat.ledger.domain.LedgerMath.catName(categoryL1, categoryL2)
    }
    val resolved = huiWangResolution()
    val subtype = resolved.subtype
    if (type == TxType.REFUND) {
        val origin = com.kumquat.ledger.domain.LedgerMath.catName(categoryL1, categoryL2)
        val prefix = when {
            subtype == null -> "历史退款"
            resolved.legacy -> "历史退款 · ${subtype.label}"
            else -> subtype.label
        }
        return if (origin.isBlank() || origin == subtype?.label) prefix else "$prefix · $origin"
    }
    return subtype?.label ?: "历史往来"
}

/** 列表右侧/副标题用的状态文案。 */
fun Tx.displayStatus(): String {
    val type = TxType.parse(type) ?: return ""
    return when (type) {
        TxType.REFUND -> when {
            linkedTxId == null -> "待关联 · 暂不计入收支"
            else -> "已冲减原支出"
        }
        TxType.EXCHANGE -> {
            val resolved = huiWangResolution()
            when (resolved.status) {
                HuiWangStatus.PENDING_CONFIRM -> "待确认 · 暂不计入净额"
                HuiWangStatus.OK -> if (resolved.legacy) "历史往来" else "回往"
                HuiWangStatus.PENDING_LINK -> "待确认"
            }
        }
        TxType.INCOME -> "入账"
        TxType.EXPENSE -> if (linkedTxId != null) "出账 · 有关联退款" else "出账"
        TxType.TRANSFER -> "转账 · 不计收支"
    }
}

/** 列表图标用的类型：退款的图标应跟随原出账分类。 */
fun Tx.iconType(): TxType = if (TxType.parse(type) == TxType.REFUND) TxType.EXPENSE else {
    TxType.parse(type) ?: TxType.EXPENSE
}

/** 统计页“回往”行的颜色：负支出色、正收入色、零次要色。 */
fun huiWangAmountColor(cents: Long, muted: Color): Color = when {
    cents > 0L -> Income
    cents < 0L -> Expense
    else -> muted
}

