package com.kumquat.ledger.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.SyncAlt
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.kumquat.ledger.domain.TxType
import com.kumquat.ledger.ui.theme.CandyBlue
import com.kumquat.ledger.ui.theme.CandyLilac
import com.kumquat.ledger.ui.theme.CandyMint
import com.kumquat.ledger.ui.theme.CandyPeach
import com.kumquat.ledger.ui.theme.CandyPink
import com.kumquat.ledger.ui.theme.CandyRose
import com.kumquat.ledger.ui.theme.CandySky
import com.kumquat.ledger.ui.theme.CandyViolet
import com.kumquat.ledger.ui.theme.Exchange
import com.kumquat.ledger.ui.theme.Expense
import com.kumquat.ledger.ui.theme.Income
import com.kumquat.ledger.ui.theme.Refund
import com.kumquat.ledger.ui.theme.Transfer

/** 类型语义色：只用于圆点 / 图标底板 / 进度条，不用于金额数字。 */
fun TxType.semanticColor(): Color = when (this) {
    TxType.INCOME -> Income
    TxType.EXPENSE -> Expense
    TxType.REFUND -> Refund
    TxType.EXCHANGE -> Exchange
    TxType.TRANSFER -> Transfer
}

/**
 * 类型胶囊选中时的渐变色（v1.1 全部改成粉紫 / 蓝紫系的糖果渐变，
 * 不再出现大红、正红、橙红底色；文字统一黑色，对比度足够）。
 */
fun TxType.semanticGradient(): List<Color> = when (this) {
    TxType.INCOME -> listOf(CandyMint, CandySky)
    TxType.EXPENSE -> listOf(CandyRose, CandyLilac)
    TxType.REFUND -> listOf(CandyBlue, CandyViolet)
    TxType.EXCHANGE -> listOf(CandyLilac, CandyViolet)
    TxType.TRANSFER -> listOf(CandyPeach, CandyPink)
}

fun TxType.semanticIcon(): ImageVector = when (this) {
    TxType.INCOME -> Icons.Rounded.ArrowDownward
    TxType.EXPENSE -> Icons.Rounded.ArrowUpward
    TxType.REFUND -> Icons.Rounded.Replay
    TxType.EXCHANGE -> Icons.Rounded.SwapHoriz
    TxType.TRANSFER -> Icons.Rounded.SyncAlt
}

/** 主 CTA 渐变（粉 → 丁香紫 → 蓝紫，没有任何红色成分）。 */
val CtaGradient = listOf(CandyPink, CandyLilac, CandyViolet)
