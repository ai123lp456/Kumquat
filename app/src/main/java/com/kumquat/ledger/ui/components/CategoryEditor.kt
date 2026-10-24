package com.kumquat.ledger.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kumquat.ledger.data.LedgerRepository
import com.kumquat.ledger.ui.theme.CardShape
import com.kumquat.ledger.ui.theme.GlassBorderDark
import com.kumquat.ledger.ui.theme.GlassBorderLight
import com.kumquat.ledger.ui.theme.Ink
import com.kumquat.ledger.ui.theme.InkMuted
import com.kumquat.ledger.ui.theme.PillShape

/**
 * 新建/编辑分类对话框。
 * 图标网格 5 列、单元格 48dp；默认账户默认“不绑定”；父级只读展示。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CategoryEditorDialog(
    title: String,
    parentLabel: String,
    initialName: String,
    initialIcon: String,
    initialDefaultAssetId: Long?,
    assets: List<LedgerRepository.AssetBalance>,
    error: String?,
    onDismiss: () -> Unit,
    onSave: (name: String, icon: String, defaultAssetId: Long?) -> Unit
) {
    var name by remember { mutableStateOf(initialName) }
    var icon by remember { mutableStateOf(initialIcon.ifBlank { DefaultIconKey }) }
    var assetId by remember {
        mutableStateOf(initialDefaultAssetId?.takeIf { id -> assets.any { it.asset.id == id } })
    }
    var localError by remember { mutableStateOf<String?>(null) }
    var assetMenu by remember { mutableStateOf(false) }
    val selectedAssetName = assets.firstOrNull { it.asset.id == assetId }?.asset?.name ?: "不绑定"

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color.White.copy(alpha = 0.98f),
        shape = CardShape,
        title = {
            Column {
                Text(title, fontWeight = FontWeight.Black, fontSize = 17.sp, color = Ink)
                Text(parentLabel, fontSize = 11.sp, color = InkMuted)
            }
        },
        text = {
            Column(
                Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        if (it.length <= 12) {
                            name = it
                            localError = null
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("分类名称（1–12 字）") },
                    singleLine = true,
                    shape = CardShape,
                    colors = OutlinedTextFieldDefaults.colors(cursorColor = Ink, focusedBorderColor = Ink)
                )

                Text("图标", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Ink)
                FlowRow(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    maxItemsInEachRow = 5
                ) {
                    IconLibrary.forEach { option ->
                        val selected = option.key == icon
                        Box(
                            Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(
                                    if (selected) Color.White.copy(alpha = 0.85f)
                                    else Color.White.copy(alpha = 0.35f)
                                )
                                .border(
                                    if (selected) 2.dp else 1.dp,
                                    if (selected) Ink else Color.White.copy(alpha = 0.65f),
                                    RoundedCornerShape(14.dp)
                                )
                                .clickable {
                                    icon = option.key
                                    localError = null
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                categoryIcon(option.key),
                                contentDescription = option.label,
                                tint = Ink,
                                modifier = Modifier.size(22.dp)
                            )
                            if (selected) {
                                Box(
                                    Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(3.dp)
                                        .size(14.dp)
                                        .background(Ink, RoundedCornerShape(50)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Rounded.Check,
                                        contentDescription = "已选中",
                                        tint = Color.White,
                                        modifier = Modifier.size(10.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                Text("默认资产账户", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Ink)
                Box {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(PillShape)
                            .background(Color.White.copy(alpha = 0.4f))
                            .border(1.dp, Brush.verticalGradient(listOf(GlassBorderLight, GlassBorderDark)), PillShape)
                            .clickable { assetMenu = true }
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(selectedAssetName, fontSize = 13.sp, color = Ink, modifier = Modifier.weight(1f))
                        Icon(Icons.Rounded.ExpandMore, contentDescription = "选择默认账户", tint = InkMuted)
                    }
                    DropdownMenu(
                        expanded = assetMenu,
                        onDismissRequest = { assetMenu = false },
                        containerColor = Color.White.copy(alpha = 0.98f),
                        shape = RoundedCornerShape(18.dp)
                    ) {
                        DropdownMenuItem(
                            text = { Text("不绑定", color = Ink) },
                            onClick = {
                                assetId = null
                                assetMenu = false
                            }
                        )
                        assets.forEach { item ->
                            DropdownMenuItem(
                                text = { Text(item.asset.name, color = Ink) },
                                onClick = {
                                    assetId = item.asset.id
                                    assetMenu = false
                                }
                            )
                        }
                    }
                }

                (localError ?: error)?.let {
                    Text(it, color = Color(0xFFD50000), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val trimmed = name.trim()
                when {
                    trimmed.isEmpty() -> localError = "分类名称不能为空"
                    trimmed.length > 12 -> localError = "分类名称最多 12 个字"
                    else -> onSave(trimmed, icon, assetId)
                }
            }) { Text("保存", fontWeight = FontWeight.Black, color = Ink) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消", color = InkMuted) }
        }
    )
}
