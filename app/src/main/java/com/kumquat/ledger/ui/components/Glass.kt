package com.kumquat.ledger.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kumquat.ledger.ui.theme.BgGradEnd
import com.kumquat.ledger.ui.theme.BgGradMid
import com.kumquat.ledger.ui.theme.BgGradStart
import com.kumquat.ledger.ui.theme.CardShape
import com.kumquat.ledger.ui.theme.GlassBorderDark
import com.kumquat.ledger.ui.theme.GlassBorderLight
import com.kumquat.ledger.ui.theme.GlassFill

/**
 * 全局 Y2K 背景：对角三色渐变 + 两团静态柔光（用径向渐变模拟模糊色块，零逐帧开销）。
 */
@Composable
fun KumquatBackground(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier
            .fillMaxSize()
            .background(Brush.linearGradient(listOf(BgGradStart, BgGradMid, BgGradEnd)))
    ) {
        // 静态柔光块：左上粉白、右下天蓝白（不用 blur，避免 GPU 成本）
        Box(
            Modifier
                .size(360.dp)
                .offset(x = (-120).dp, y = (-80).dp)
                .background(
                    Brush.radialGradient(listOf(Color.White.copy(alpha = 0.38f), Color.Transparent)),
                    CircleShape
                )
        )
        Box(
            Modifier
                .size(420.dp)
                .align(Alignment.BottomEnd)
                .offset(x = 140.dp, y = 120.dp)
                .background(
                    Brush.radialGradient(listOf(Color(0xFF8EC5FC).copy(alpha = 0.45f), Color.Transparent)),
                    CircleShape
                )
        )
        content()
    }
}

/**
 * 玻璃卡片：半透明白 + 1dp 白→透明渐变描边 + 24dp 圆角 + 白色外阴影 + 顶部内高光。
 * 列表里也不要逐项实时模糊，统一用这个"半透明近似"版本。
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    shape: Shape = CardShape,
    fill: Color = GlassFill,
    elevation: Dp = 14.dp,
    contentPadding: PaddingValues = PaddingValues(18.dp),
    content: @Composable ColumnScope.() -> Unit
) {
    Box(
        modifier
            .shadow(elevation, shape, clip = false, ambientColor = Color.White, spotColor = Color(0xFF8A8A8A))
            .clip(shape)
            .background(fill)
            .border(1.dp, Brush.verticalGradient(listOf(GlassBorderLight, GlassBorderDark)), shape)
    ) {
        Column(Modifier.padding(contentPadding), content = content)
        // 顶部内高光（1.5dp）
        Box(
            Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .height(1.5.dp)
                .background(
                    Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.75f), Color.Transparent))
                )
        )
    }
}

/** 极薄的玻璃分隔线。 */
@Composable
fun GlassDivider(modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(
                Brush.horizontalGradient(
                    listOf(Color.Transparent, Color.White.copy(alpha = 0.7f), Color.Transparent)
                )
            )
    )
}

/** 胶囊小标签。 */
@Composable
fun GlassPill(
    modifier: Modifier = Modifier,
    fill: Color = Color.White.copy(alpha = 0.3f),
    content: @Composable () -> Unit
) {
    Box(
        modifier
            .clip(RoundedCornerShape(50))
            .background(fill)
            .border(1.dp, Brush.verticalGradient(listOf(GlassBorderLight, GlassBorderDark)), RoundedCornerShape(50))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) { content() }
}

