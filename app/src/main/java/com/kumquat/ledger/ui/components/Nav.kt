package com.kumquat.ledger.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kumquat.ledger.ui.theme.CandyPink
import com.kumquat.ledger.ui.theme.GlassBorderDark
import com.kumquat.ledger.ui.theme.GlassBorderLight
import com.kumquat.ledger.ui.theme.Ink
import com.kumquat.ledger.ui.theme.InkMuted

data class NavItem(val label: String, val icon: ImageVector)

/**
 * 悬浮式半透明底部导航（v1.1.1）：
 * - 形态恢复 v1.1.0 的 68dp 胶囊，不再是整宽白色条；
 * - 白色底 alpha = 0.72（v1.1.0 是 0.55），比原版更压得住文字，但仍能透出背景渐变；
 * - 不做整体 blur、不加整宽渐变遮罩，避免又变成“不透明色块”；
 * - 高度与点击区域保持 v1.1.0 原样，安全区由根布局的 navigationBarsPadding 负责。
 */
@Composable
fun BottomGlassNav(
    items: List<NavItem>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    onAdd: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier
            .fillMaxWidth()
            .height(68.dp)
            .shadow(
                elevation = 18.dp,
                shape = RoundedCornerShape(30.dp),
                clip = false,
                ambientColor = Color.White,
                spotColor = Color(0xFF8A8A8A)
            )
            .clip(RoundedCornerShape(30.dp))
            .background(Color.White.copy(alpha = 0.72f))
            .border(
                1.dp,
                Brush.verticalGradient(listOf(GlassBorderLight, GlassBorderDark)),
                RoundedCornerShape(30.dp)
            )
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        items.forEachIndexed { index, item ->
            // 第 3 位插「记一笔」
            if (index == 2) {
                AddNavCell(
                    onClick = onAdd,
                    modifier = Modifier.weight(1f)
                )
            }
            NavCell(
                item = item,
                selected = index == selectedIndex,
                modifier = Modifier.weight(1f),
                onClick = { onSelect(index) }
            )
        }
    }
}

/**
 * 记一笔：44dp 圆角方框 + 糖果渐变 + 左上高光，尺寸与普通图标同一量级。
 */
@Composable
fun AddNavCell(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.92f else 1f, label = "add-cell-scale")

    Box(modifier, contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .size(44.dp)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
                .shadow(
                    elevation = 8.dp,
                    shape = RoundedCornerShape(16.dp),
                    clip = false,
                    ambientColor = Color.White,
                    spotColor = CandyPink.copy(alpha = 0.55f)
                )
                .clip(RoundedCornerShape(16.dp))
                .background(Brush.linearGradient(CtaGradient))
                .border(1.dp, Color.White.copy(alpha = 0.85f), RoundedCornerShape(16.dp))
                .clickable(
                    interactionSource = interaction,
                    indication = ripple(color = Ink.copy(alpha = 0.12f)),
                    onClick = onClick
                ),
            contentAlignment = Alignment.Center
        ) {
            // 左上高光
            Box(
                Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 6.dp, top = 4.dp)
                    .size(18.dp, 10.dp)
                    .background(
                        Brush.radialGradient(listOf(Color.White.copy(alpha = 0.9f), Color.Transparent)),
                        RoundedCornerShape(50)
                    )
            )
            Icon(Icons.Rounded.Add, contentDescription = "记一笔", tint = Ink, modifier = Modifier.size(24.dp))
        }
    }
}

@Composable
private fun NavCell(
    item: NavItem,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier
            .clip(RoundedCornerShape(22.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Icon(
                item.icon,
                contentDescription = item.label,
                tint = if (selected) Ink else InkMuted,
                modifier = Modifier.size(if (selected) 24.dp else 22.dp)
            )
            Text(
                text = item.label,
                fontSize = 10.sp,
                fontWeight = if (selected) FontWeight.Black else FontWeight.Medium,
                color = if (selected) Ink else InkMuted
            )
            // 选中项下方 4dp 霓虹点
            Box(
                Modifier
                    .size(4.dp)
                    .then(
                        if (selected) Modifier
                            .shadow(6.dp, CircleShape, clip = false, spotColor = CandyPink)
                            .background(CandyPink, CircleShape)
                        else Modifier
                    )
            )
        }
    }
}
