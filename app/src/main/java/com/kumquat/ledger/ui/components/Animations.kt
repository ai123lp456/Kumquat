package com.kumquat.ledger.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp

/**
 * 页面进入动效：淡入 + 上移 12dp，220ms，FastOutSlowInEasing。
 * 只跑一次，不做循环动画。
 */
@Composable
fun Modifier.pageEnter(): Modifier {
    var started by remember { mutableStateOf(false) }
    val alpha by animateFloatAsState(
        targetValue = if (started) 1f else 0f,
        animationSpec = tween(220, easing = FastOutSlowInEasing),
        label = "page-alpha"
    )
    val offset by animateDpAsState(
        targetValue = if (started) 0.dp else 12.dp,
        animationSpec = tween(220, easing = FastOutSlowInEasing),
        label = "page-offset"
    )
    LaunchedEffect(Unit) { started = true }
    return this.graphicsLayer {
        this.alpha = alpha
        translationY = offset.toPx()
    }
}
