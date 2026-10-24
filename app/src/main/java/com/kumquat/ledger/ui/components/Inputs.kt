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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Backspace
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kumquat.ledger.domain.TxType
import com.kumquat.ledger.ui.theme.CandyPink
import com.kumquat.ledger.ui.theme.GlassBorderDark
import com.kumquat.ledger.ui.theme.GlassBorderLight
import com.kumquat.ledger.ui.theme.Ink
import com.kumquat.ledger.ui.theme.InkMuted
import com.kumquat.ledger.ui.theme.KeyShape
import com.kumquat.ledger.ui.theme.PillShape

/**
 * 账目类型糖果胶囊选择器（不用传统 Tab）：
 * 未选中为白 30% 透明底 + 黑字；选中为对应语义色渐变 + 外发光 + 黑字加粗。
 * v1.1 起五类（新增转账），渐变统一换成粉紫 / 蓝紫糖果色。
 */
@Composable
fun TypeSelector(
    selected: TxType,
    onSelect: (TxType) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        TxType.ordered.forEach { type ->
            val isSelected = type == selected
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
                        if (isSelected) Brush.linearGradient(type.semanticGradient())
                        else Brush.linearGradient(
                            listOf(Color.White.copy(alpha = 0.45f), Color.White.copy(alpha = 0.22f))
                        )
                    )
                    .border(1.dp, Brush.verticalGradient(listOf(GlassBorderLight, GlassBorderDark)), PillShape)
                    .clickable { onSelect(type) },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    type.label,
                    fontSize = 13.sp,
                    fontWeight = if (isSelected) FontWeight.Black else FontWeight.Medium,
                    color = Ink
                )
            }
        }
    }
}

/**
 * v1.1.1 记一笔紧凑键盘（约占屏幕 1/4 高度）：
 * 左 3 列为数字九宫格 + 小数点 + 退格；右 1 列为「+」「-」计算符。
 * 保存按钮已移出键盘，改为页面底部常驻 [SaveBar]。
 */
@Composable
fun CalcKeypad(
    onDigit: (String) -> Unit,
    onBackspace: () -> Unit,
    onOperator: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp
    val padHeight = (screenHeight * 0.22f).coerceIn(160.dp, 210.dp)

    Row(
        modifier
            .fillMaxWidth()
            .height(padHeight),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Column(
            Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            DigitRow(listOf("1", "2", "3"), onDigit, onBackspace, Modifier.weight(1f))
            DigitRow(listOf("4", "5", "6"), onDigit, onBackspace, Modifier.weight(1f))
            DigitRow(listOf("7", "8", "9"), onDigit, onBackspace, Modifier.weight(1f))
            DigitRow(listOf(".", "0", "⌫"), onDigit, onBackspace, Modifier.weight(1f))
        }
        Column(
            Modifier.width(74.dp).fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            PadKey(
                key = "+",
                modifier = Modifier.fillMaxWidth().weight(1f),
                onClick = { onOperator("+") }
            )
            PadKey(
                key = "-",
                modifier = Modifier.fillMaxWidth().weight(1f),
                onClick = { onOperator("-") }
            )
        }
    }
}

@Composable
private fun DigitRow(
    keys: List<String>,
    onDigit: (String) -> Unit,
    onBackspace: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        keys.forEach { key ->
            PadKey(
                key = key,
                modifier = Modifier.weight(1f).fillMaxHeight(),
                onClick = { if (key == "⌫") onBackspace() else onDigit(key) }
            )
        }
    }
}

@Composable
private fun PadKey(key: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.96f else 1f, label = "pad-key")

    val isOperator = key == "+" || key == "-"
    Box(
        modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                translationY = if (pressed) 3f else 0f
            }
            .shadow(5.dp, KeyShape, clip = false, ambientColor = Color.White, spotColor = Color(0xFF9E9E9E))
            .clip(KeyShape)
            .background(
                if (isOperator) {
                    Brush.verticalGradient(listOf(CandyPink.copy(alpha = 0.55f), CandyPink.copy(alpha = 0.28f)))
                } else {
                    Brush.verticalGradient(
                        listOf(Color.White.copy(alpha = 0.62f), Color.White.copy(alpha = 0.30f))
                    )
                }
            )
            .border(1.dp, Brush.verticalGradient(listOf(GlassBorderLight, GlassBorderDark)), KeyShape)
            .clickable(
                interactionSource = interaction,
                indication = ripple(color = Ink.copy(alpha = 0.12f)),
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        when (key) {
            "⌫" -> Icon(
                Icons.Rounded.Backspace,
                contentDescription = "删除",
                tint = Ink,
                modifier = Modifier.size(22.dp)
            )
            else -> Text(key, fontSize = 20.sp, fontWeight = FontWeight.Black, color = Ink)
        }
    }
}

/**
 * 备注输入：玻璃感单行输入框（v1.1 去掉 60 字限制）。
 */
@Composable
fun NoteField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "备注（可选）"
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        placeholder = { Text(placeholder, color = InkMuted, style = MaterialTheme.typography.bodyMedium) },
        singleLine = true,
        shape = KeyShape,
        textStyle = MaterialTheme.typography.bodyMedium.copy(color = Ink),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = Color.White.copy(alpha = 0.30f),
            unfocusedContainerColor = Color.White.copy(alpha = 0.25f),
            focusedBorderColor = Color.White.copy(alpha = 0.95f),
            unfocusedBorderColor = Color.White.copy(alpha = 0.6f),
            cursorColor = Ink
        )
    )
}

/** 日期行：点击弹系统日期选择器。 */
@Composable
fun DateRow(date: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .clip(KeyShape)
            .background(Color.White.copy(alpha = 0.28f))
            .border(1.dp, Brush.verticalGradient(listOf(GlassBorderLight, GlassBorderDark)), KeyShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Rounded.CalendarMonth, contentDescription = null, tint = Ink, modifier = Modifier.size(16.dp))
        Box(Modifier.width(8.dp))
        Text(date, style = MaterialTheme.typography.bodyMedium, color = Ink, fontWeight = FontWeight.Bold)
        Box(Modifier.weight(1f))
        Icon(Icons.Rounded.Edit, contentDescription = "修改日期", tint = InkMuted, modifier = Modifier.size(14.dp))
    }
}

/** 分段胶囊（本月 / 上月 / 自定义）：选中项走到糖果渐变，文字黑色。 */
@Composable
fun SegmentedPills(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier
            .fillMaxWidth()
            .clip(PillShape)
            .background(Color.White.copy(alpha = 0.28f))
            .border(1.dp, Brush.verticalGradient(listOf(GlassBorderLight, GlassBorderDark)), PillShape)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        options.forEachIndexed { index, text ->
            val selected = index == selectedIndex
            Box(
                Modifier
                    .weight(1f)
                    .clip(CircleShape)
                    .background(
                        if (selected) Brush.linearGradient(CtaGradient)
                        else Brush.linearGradient(listOf(Color.Transparent, Color.Transparent))
                    )
                    .clickable { onSelect(index) }
                    .padding(vertical = 9.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text,
                    fontSize = 13.sp,
                    fontWeight = if (selected) FontWeight.Black else FontWeight.Medium,
                    color = Ink,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

/** 分类图标底板（白色 35% + 圆角 + 图标）。 */
@Composable
fun CategoryIconBadge(iconKey: String, modifier: Modifier = Modifier, size: Int = 40) {
    val shape = RoundedCornerShape((size * 0.32f).dp)
    Box(
        modifier
            .size(size.dp)
            .clip(shape)
            .background(Color.White.copy(alpha = 0.35f))
            .border(1.dp, Brush.verticalGradient(listOf(GlassBorderLight, GlassBorderDark)), shape),
        contentAlignment = Alignment.Center
    ) {
        Icon(categoryIcon(iconKey), contentDescription = null, tint = Ink, modifier = Modifier.size((size * 0.48f).dp))
    }
}

/** 金额只读展示框：点击唤出自定义键盘（由调用方处理焦点）。 */
@Composable
fun AmountReadOnlyField(
    amountText: String,
    subLabel: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier
            .fillMaxWidth()
            .clip(KeyShape)
            .background(Color.White.copy(alpha = 0.32f))
            .border(1.dp, Brush.verticalGradient(listOf(GlassBorderLight, GlassBorderDark)), KeyShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "¥",
            fontSize = 22.sp,
            fontWeight = FontWeight.Black,
            color = Ink,
            modifier = Modifier.padding(end = 4.dp)
        )
        Text(
            text = amountText.ifEmpty { "0.00" },
            fontSize = 34.sp,
            fontWeight = FontWeight.Black,
            color = if (amountText.isEmpty()) InkMuted else Ink,
            modifier = Modifier.weight(1f)
        )
        Text(subLabel, fontSize = 10.sp, color = InkMuted)
    }
}
