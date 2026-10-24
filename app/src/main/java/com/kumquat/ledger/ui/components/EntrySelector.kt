package com.kumquat.ledger.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kumquat.ledger.ui.theme.CandyPink
import com.kumquat.ledger.ui.theme.GlassBorderDark
import com.kumquat.ledger.ui.theme.GlassBorderLight
import com.kumquat.ledger.ui.theme.Ink
import com.kumquat.ledger.ui.theme.InkMuted
import com.kumquat.ledger.ui.theme.PillShape

/**
 * 「记一笔」顶部 4 类胶囊：入账 / 出账 / 回往 / 转账。
 * 顺序固定，样式沿用 v1.1.0 的糖果渐变与玻璃底。
 */
@Composable
fun EntryTypeSelector(
    selected: EntryKind,
    onSelect: (EntryKind) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        EntryKind.ordered.forEach { kind ->
            val isSelected = kind == selected
            Box(
                Modifier
                    .weight(1f)
                    .height(44.dp)
                    .then(
                        if (isSelected) {
                            Modifier.shadow(10.dp, PillShape, clip = false, spotColor = CandyPink)
                        } else Modifier
                    )
                    .clip(PillShape)
                    .background(
                        if (isSelected) Brush.linearGradient(kind.semanticGradient())
                        else Brush.linearGradient(
                            listOf(Color.White.copy(alpha = 0.45f), Color.White.copy(alpha = 0.22f))
                        )
                    )
                    .border(1.dp, Brush.verticalGradient(listOf(GlassBorderLight, GlassBorderDark)), PillShape)
                    .semantics {
                        contentDescription = kind.label + if (isSelected) "，已选中" else ""
                        this.selected = isSelected
                    }
                    .clickable { onSelect(kind) },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    kind.label,
                    fontSize = 13.sp,
                    fontWeight = if (isSelected) FontWeight.Black else FontWeight.Medium,
                    color = Ink
                )
            }
        }
    }
}

/** 页面底部常驻保存栏：不放在数字键盘内部，随 imePadding 上移。 */
@Composable
fun SaveBar(
    enabled: Boolean,
    saving: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier
            .fillMaxWidth()
            .height(52.dp)
            .shadow(12.dp, PillShape, clip = false, ambientColor = Color.White, spotColor = CandyPink.copy(alpha = 0.55f))
            .clip(PillShape)
            .background(Brush.linearGradient(CtaGradient))
            .border(1.dp, Color.White.copy(alpha = 0.85f), PillShape)
            .clickable(enabled = enabled && !saving, onClick = onClick)
            .semantics {
                contentDescription = if (saving) "正在保存" else "保存账单"
            },
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (saving) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = Ink
                )
            }
            Text(
                if (saving) "保存中…" else "保存",
                fontSize = 15.sp,
                fontWeight = FontWeight.Black,
                color = if (enabled) Ink else InkMuted
            )
        }
    }
}
