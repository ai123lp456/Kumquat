package com.kumquat.ledger.ui.components

import android.graphics.Color as AColor
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBackIosNew
import androidx.compose.material.icons.rounded.Inbox
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kumquat.ledger.domain.Money
import com.kumquat.ledger.domain.TxType
import com.kumquat.ledger.ui.theme.Accent
import com.kumquat.ledger.ui.theme.BgGradMid
import com.kumquat.ledger.ui.theme.BgGradStart
import com.kumquat.ledger.ui.theme.CardShape
import com.kumquat.ledger.ui.theme.GlassBorderDark
import com.kumquat.ledger.ui.theme.GlassBorderLight
import com.kumquat.ledger.ui.theme.Ink
import com.kumquat.ledger.ui.theme.InkMuted
import com.kumquat.ledger.ui.theme.LabelEn
import com.kumquat.ledger.ui.theme.MoneyHero
import com.kumquat.ledger.ui.theme.MoneyRow
import com.kumquat.ledger.ui.theme.PillShape

/** 账本封面 -> 渐变画刷。coverType: color / gradient；gradient 形如 "#AABBCC,#DDEEFF"。 */
fun coverBrush(coverType: String, coverValue: String): Brush {
    fun parse(hex: String, fallback: Color): Color = try {
        Color(AColor.parseColor(hex.trim()))
    } catch (_: Exception) {
        fallback
    }

    return if (coverType == "gradient") {
        val parts = coverValue.split(",")
        val first = parse(parts.getOrNull(0) ?: "", BgGradStart)
        val second = parse(parts.getOrNull(1) ?: "", BgGradMid)
        Brush.linearGradient(listOf(first, second))
    } else {
        val solid = parse(coverValue, BgGradStart)
        Brush.linearGradient(listOf(solid, solid.copy(alpha = 0.72f)))
    }
}

/** 小账本封面方块。 */
@Composable
fun BookCoverBadge(coverType: String, coverValue: String, modifier: Modifier = Modifier, size: Dp = 22.dp) {
    Box(
        modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.3f))
            .background(coverBrush(coverType, coverValue))
            .border(1.dp, Color.White.copy(alpha = 0.8f), RoundedCornerShape(size * 0.3f))
    )
}

/** 大写英文小标签 + 中文标题（账单分组、统计分区等）。 */
@Composable
fun SectionHeader(en: String, cn: String, modifier: Modifier = Modifier, trailing: @Composable (() -> Unit)? = null) {
    Row(
        modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(en.uppercase(), style = LabelEn)
        Spacer(Modifier.width(8.dp))
        Text(cn, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
        Spacer(Modifier.weight(1f))
        trailing?.invoke()
    }
}

/** 顶部栏：返回 + 标题 + 右侧动作。 */
@Composable
fun KmTopBar(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    trailing: @Composable RowScope.() -> Unit = {}
) {
    Row(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (onBack != null) {
            GlassIconButton(Icons.Rounded.ArrowBackIosNew, "返回", onBack, size = 38)
            Spacer(Modifier.width(10.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleLarge, color = Ink, fontWeight = FontWeight.Black)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = InkMuted)
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), content = trailing)
    }
}

/** 空状态玻璃卡。 */
@Composable
fun EmptyState(
    title: String,
    hint: String,
    modifier: Modifier = Modifier,
    icon: ImageVector = Icons.Rounded.Inbox
) {
    GlassCard(modifier.fillMaxWidth()) {
        Column(
            Modifier.fillMaxWidth().padding(vertical = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                Modifier
                    .size(56.dp)
                    .background(Color.White.copy(alpha = 0.45f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = InkMuted, modifier = Modifier.size(26.dp))
            }
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Ink)
            Text(
                hint,
                style = MaterialTheme.typography.bodyMedium,
                color = InkMuted,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 24.dp)
            )
        }
    }
}

/** 渐变进度条：分类色 -> 白，用于统计排行占比。 */
@Composable
fun GradientBar(progress: Float, color: Color, modifier: Modifier = Modifier, height: Dp = 10.dp) {
    Box(
        modifier
            .fillMaxWidth()
            .height(height)
            .clip(PillShape)
            .background(Color.White.copy(alpha = 0.4f))
    ) {
        Box(
            Modifier
                .fillMaxHeight()
                .fillMaxWidth(progress.coerceIn(0.02f, 1f))
                .background(Brush.horizontalGradient(listOf(color, color.copy(alpha = 0.85f), Color.White)))
        )
    }
}

/** 类型语义色圆点（带微弱外发光）。 */
@Composable
fun TypeDot(type: TxType, size: Dp = 8.dp) {
    Box(
        Modifier
            .size(size)
            .shadow(5.dp, CircleShape, clip = false, spotColor = type.semanticColor())
            .background(type.semanticColor(), CircleShape)
    )
}

/** 金额文本：统一黑色高对比。 */
@Composable
fun MoneyText(
    cents: Long,
    style: TextStyle = MoneyRow,
    prefix: String = "¥",
    modifier: Modifier = Modifier
) {
    Text("$prefix${Money.format(cents)}", style = style, color = Ink, modifier = modifier)
}

/** 概览卡里的一行小计。 */
@Composable
fun TypeTotalRow(
    type: TxType,
    label: String,
    cents: Long,
    modifier: Modifier = Modifier
) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        TypeDot(type)
        Spacer(Modifier.width(8.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium, color = Ink)
        Spacer(Modifier.weight(1f))
        Text(
            "¥${Money.format(cents)}",
            style = MaterialTheme.typography.titleMedium,
            color = Ink,
            fontWeight = FontWeight.Black
        )
    }
}

/** 统一确认弹窗（玻璃感容器）。 */
@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    confirmText: String = "确定",
    dismissText: String = "取消",
    destructive: Boolean = false
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color.White.copy(alpha = 0.96f),
        shape = CardShape,
        title = { Text(title, fontWeight = FontWeight.Black, color = Ink) },
        text = { Text(message, style = MaterialTheme.typography.bodyLarge, color = Ink) },
        confirmButton = {
            TextButton(onClick = { onConfirm(); onDismiss() }) {
                Text(confirmText, color = if (destructive) Accent else Ink, fontWeight = FontWeight.Black)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(dismissText, color = InkMuted) }
        }
    )
}

/** 占满剩余空间的居中提示（加载 / 出错）。 */
@Composable
fun CenteredHint(text: String, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text, style = MaterialTheme.typography.bodyLarge, color = InkMuted)
    }
}

/** 大号金额（概览卡主数字）。 */
@Composable
fun HeroMoney(cents: Long, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.Bottom) {
        Text("¥", style = MoneyHero.copy(fontSize = MoneyHero.fontSize * 0.6f), color = Ink)
        Spacer(Modifier.width(2.dp))
        Text(Money.format(cents), style = MoneyHero, color = Ink)
    }
}
