package com.kumquat.ledger.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kumquat.ledger.data.Tx
import com.kumquat.ledger.domain.Money
import com.kumquat.ledger.domain.RefundRules
import com.kumquat.ledger.domain.TxType
import com.kumquat.ledger.ui.theme.Accent
import com.kumquat.ledger.ui.theme.CardShape
import com.kumquat.ledger.ui.theme.GlassBorderDark
import com.kumquat.ledger.ui.theme.GlassBorderLight
import com.kumquat.ledger.ui.theme.Ink
import com.kumquat.ledger.ui.theme.InkMuted
import com.kumquat.ledger.ui.theme.MoneyRow

/**
 * 账单列表项：半透明白圆角卡 + 左侧分类图标底板 + 中间分类/备注 + 右侧黑色金额。
 * 列表项不做实时模糊（性能），用半透明近似。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TxListRow(
    tx: Tx,
    iconKey: String,
    refundBadge: RefundRules.Badge? = null,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val type = TxType.parse(tx.type) ?: TxType.EXPENSE
    val status = tx.displayStatus()
    val badgeText = when {
        refundBadge?.fully == true -> "已全额退款"
        refundBadge != null -> "已退款 ¥${Money.format(refundBadge.refundedCents)}"
        else -> status
    }
    val pending = badgeText.contains("待")
    Row(
        Modifier
            .fillMaxWidth()
            .shadow(6.dp, CardShape, clip = false, ambientColor = Color.White, spotColor = Color(0xFF9A9A9A))
            .clip(CardShape)
            .background(Color.White.copy(alpha = 0.30f))
            .border(1.dp, Brush.verticalGradient(listOf(GlassBorderLight, GlassBorderDark)), CardShape)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CategoryIconBadge(iconKey, size = 38)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TypeDot(type, 7.dp)
                Spacer(Modifier.width(6.dp))
                Text(
                    tx.displayLabel(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                if (tx.note.isNotBlank()) tx.note else status,
                style = MaterialTheme.typography.bodyMedium,
                color = InkMuted,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                "¥${Money.format(tx.amountCents)}",
                style = MoneyRow,
                color = if (refundBadge?.fully == true) InkMuted else Ink,
                textDecoration = if (refundBadge?.fully == true) TextDecoration.LineThrough else null
            )
            Text(
                badgeText,
                style = MaterialTheme.typography.bodyMedium,
                color = if (pending) Accent else InkMuted,
                fontSize = 10.sp,
                maxLines = 1
            )
        }
    }
}
