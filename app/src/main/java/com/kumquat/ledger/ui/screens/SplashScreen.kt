package com.kumquat.ledger.ui.screens

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.animateLottieCompositionAsState
import com.airbnb.lottie.compose.rememberLottieComposition
import com.kumquat.ledger.ui.theme.Brand
import com.kumquat.ledger.ui.theme.Ink
import com.kumquat.ledger.ui.theme.SplashCream
import kotlinx.coroutines.delay

/**
 * 开屏内容：播放 assets/splash_kumquat.json（已定稿，不改动画）。
 * JSON 缺失/解析失败时走属性动画兜底；无论如何 3 秒内一定进入主页。
 */
@Composable
fun SplashContent(onDone: () -> Unit) {
    val composition by rememberLottieComposition(LottieCompositionSpec.Asset("splash_kumquat.json"))
    val progress by animateLottieCompositionAsState(
        composition = composition,
        iterations = 1,
        isPlaying = composition != null,
        restartOnPlay = false
    )
    var finished by rememberSaveable { mutableStateOf(false) }

    fun finish() {
        if (!finished) {
            finished = true
            onDone()
        }
    }

    if (composition != null && progress >= 0.999f) {
        androidx.compose.runtime.LaunchedEffect(Unit) { finish() }
    }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        delay(3000)
        finish()
    }

    Box(Modifier.fillMaxSize().background(SplashCream)) {
        val comp = composition
        if (comp != null) {
            LottieAnimation(
                composition = comp,
                progress = { progress },
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            FallbackSplash()
        }
    }
}

/** 兜底开屏：缩放 + 透明度呼吸的金桔，不依赖任何外部资源。 */
@Composable
private fun FallbackSplash() {
    val transition = rememberInfiniteTransition(label = "fallback-splash")
    val scale by transition.animateFloat(
        initialValue = 0.82f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
        label = "fallback-scale"
    )
    val alpha by transition.animateFloat(
        initialValue = 0.75f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
        label = "fallback-alpha"
    )

    Column(
        Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            Modifier
                .size(160.dp)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    this.alpha = alpha
                }
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        listOf(Color(0xFFFFD166), Brand, Color(0xFFE07A00))
                    )
                )
        ) {
            Box(
                Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 34.dp, top = 28.dp)
                    .size(48.dp, 28.dp)
                    .background(
                        Brush.radialGradient(listOf(Color.White.copy(alpha = 0.85f), Color.Transparent)),
                        RoundedCornerShape(50)
                    )
            )
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 4.dp, end = 34.dp)
                    .size(42.dp, 22.dp)
                    .background(Color(0xFF2E7D32), RoundedCornerShape(topStart = 22.dp, bottomEnd = 22.dp))
            )
        }
        Spacer(Modifier.height(28.dp))
        Text("小金桔 · kumquat", fontSize = 20.sp, fontWeight = FontWeight.Black, color = Ink)
        Spacer(Modifier.height(6.dp))
        Text("正在打开账本…", fontSize = 12.sp, color = Ink.copy(alpha = 0.55f))
        Spacer(Modifier.width(1.dp))
    }
}
