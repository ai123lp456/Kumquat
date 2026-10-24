package com.kumquat.ledger.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kumquat.ledger.ui.theme.Accent
import com.kumquat.ledger.ui.theme.CardShape
import com.kumquat.ledger.ui.theme.GlassBorderDark
import com.kumquat.ledger.ui.theme.GlassBorderLight
import com.kumquat.ledger.ui.theme.Ink
import com.kumquat.ledger.ui.theme.PillShape

/**
 * 主 CTA：糖果渐变胶囊 + 左上高光 + Chrome Gloss 扫光（600ms，一次，不循环）。
 */
@Composable
fun GradientButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: List<Color> = CtaGradient,
    icon: ImageVector? = null
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.96f else 1f, label = "cta-scale")
    val gloss = remember { Animatable(-0.8f) }
    var clicks by remember { mutableStateOf(0) }

    LaunchedEffect(clicks) {
        gloss.snapTo(-0.8f)
        gloss.animateTo(1.7f, animationSpec = tween(600, easing = LinearEasing))
    }

    BoxWithConstraints(
        modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .shadow(
                elevation = 14.dp,
                shape = PillShape,
                clip = false,
                ambientColor = Color.White,
                spotColor = Accent.copy(alpha = 0.65f)
            )
            .clip(PillShape)
            .background(Brush.linearGradient(colors))
            .clickable(
                interactionSource = interaction,
                indication = ripple(color = Color.White.copy(alpha = 0.4f)),
                enabled = enabled
            ) {
                clicks++
                onClick()
            }
            .heightIn(min = 52.dp)
            .alpha(if (enabled) 1f else 0.45f),
        contentAlignment = Alignment.Center
    ) {
        Row(
            Modifier.padding(horizontal = 24.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (icon != null) Icon(icon, contentDescription = null, tint = Ink, modifier = Modifier.size(20.dp))
            Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black, color = Ink)
        }
        Box(
            Modifier
                .align(Alignment.CenterStart)
                .offset(x = maxWidth * gloss.value)
                .width(maxWidth * 0.42f)
                .fillMaxHeight()
                .graphicsLayer { rotationZ = 16f }
                .background(
                    Brush.horizontalGradient(
                        listOf(Color.Transparent, Color.White.copy(alpha = 0.65f), Color.Transparent)
                    )
                )
        )
    }
}

/** 小型胶囊按钮（白玻璃底），用于"生成提示词""管理"等次级操作。 */
@Composable
fun GhostPill(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    selected: Boolean = false,
    selectedColors: List<Color> = CtaGradient
) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier
            .clip(PillShape)
            .background(
                if (selected) Brush.linearGradient(selectedColors)
                else Brush.linearGradient(listOf(Color.White.copy(alpha = 0.45f), Color.White.copy(alpha = 0.22f)))
            )
            .border(
                1.dp,
                Brush.verticalGradient(listOf(GlassBorderLight, GlassBorderDark)),
                PillShape
            )
            .clickable(interactionSource = interaction, indication = ripple(color = Ink.copy(alpha = 0.12f)), onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (icon != null) Icon(icon, contentDescription = null, tint = Ink, modifier = Modifier.size(16.dp))
            Text(text, style = MaterialTheme.typography.labelLarge, color = Ink, fontWeight = FontWeight.Bold)
        }
    }
}

/** 圆形图标按钮（返回、更多等）。 */
@Composable
fun GlassIconButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = Ink,
    size: Int = 40
) {
    Box(
        modifier
            .size(size.dp)
            .clip(RoundedCornerShape(50))
            .background(Color.White.copy(alpha = 0.35f))
            .border(1.dp, Brush.verticalGradient(listOf(GlassBorderLight, GlassBorderDark)), RoundedCornerShape(50))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = contentDescription, tint = tint, modifier = Modifier.size((size * 0.5f).dp))
    }
}

/** 卡片式容器按钮（整块可点）。 */
@Composable
fun TapCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit
) {
    GlassCard(
        modifier = modifier.clickable(onClick = onClick),
        shape = CardShape
    ) { content() }
}
