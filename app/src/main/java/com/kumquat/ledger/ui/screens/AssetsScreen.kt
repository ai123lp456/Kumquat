package com.kumquat.ledger.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kumquat.ledger.KumquatApp
import com.kumquat.ledger.data.Asset
import com.kumquat.ledger.data.LedgerRepository
import com.kumquat.ledger.domain.Money
import com.kumquat.ledger.ui.components.ConfirmDialog
import com.kumquat.ledger.ui.components.GhostPill
import com.kumquat.ledger.ui.components.GlassCard
import com.kumquat.ledger.ui.components.GlassIconButton
import com.kumquat.ledger.ui.components.GradientButton
import com.kumquat.ledger.ui.components.KmTopBar
import com.kumquat.ledger.ui.components.SectionHeader
import com.kumquat.ledger.ui.theme.CandyLilac
import com.kumquat.ledger.ui.theme.CardShape
import com.kumquat.ledger.ui.theme.GlassBorderDark
import com.kumquat.ledger.ui.theme.GlassBorderLight
import com.kumquat.ledger.ui.theme.Ink
import com.kumquat.ledger.ui.theme.InkMuted
import com.kumquat.ledger.ui.theme.LabelEn
import com.kumquat.ledger.ui.theme.MoneyRow
import com.kumquat.ledger.util.DateX
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AssetsViewModel : ViewModel() {

    private val repo = KumquatApp.instance.repository

    val balances: StateFlow<List<LedgerRepository.AssetBalance>> = repo.observeAssetBalances()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val message = MutableStateFlow<String?>(null)

    fun clearMessage() {
        message.value = null
    }

    fun create(name: String, initialCents: Long, initialDate: String) {
        viewModelScope.launch {
            runCatching { repo.createAsset(name, initialCents, initialDate) }
                .onSuccess { message.value = "已添加账户「${name.trim()}」" }
                .onFailure { message.value = "添加失败：${it.message}" }
        }
    }

    fun update(asset: Asset, name: String, initialCents: Long, initialDate: String) {
        viewModelScope.launch {
            runCatching { repo.updateAsset(asset, name, initialCents, initialDate) }
                .onSuccess { message.value = "已保存「${name.trim()}」" }
                .onFailure { message.value = "保存失败：${it.message}" }
        }
    }

    fun delete(asset: Asset) {
        viewModelScope.launch {
            runCatching { repo.deleteAsset(asset) }
                .onSuccess { message.value = "已删除「${asset.name}」，历史账单保留" }
                .onFailure { message.value = "删除失败：${it.message}" }
        }
    }
}

@Composable
fun AssetsScreen(onBack: () -> Unit, viewModel: AssetsViewModel = viewModel()) {
    val balances by viewModel.balances.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    var createOpen by remember { mutableStateOf(false) }
    var editTarget by remember { mutableStateOf<Asset?>(null) }
    var deleteTarget by remember { mutableStateOf<Asset?>(null) }

    LaunchedEffect(message) {
        if (message != null) {
            kotlinx.coroutines.delay(3200)
            viewModel.clearMessage()
        }
    }

    val totalCents = balances.sumOf { it.balanceCents }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
    ) {
        KmTopBar(
            title = "资产管理",
            subtitle = "余额 = 初始余额 + 关联流水",
            onBack = onBack
        )

        message?.let {
            GlassCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(12.dp)) {
                Text(it, fontSize = 12.sp, color = Ink, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(8.dp))
        }

        GlassCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(18.dp)) {
            Text("TOTAL ASSET · 总资产", style = LabelEn)
            Spacer(Modifier.height(4.dp))
            Text(
                "¥${Money.format(totalCents)}",
                fontSize = 34.sp,
                fontWeight = FontWeight.Black,
                color = Ink
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "${balances.size} 个账户 · 内部转账不计收支，回往按净额计入",
                fontSize = 11.sp,
                color = InkMuted
            )
        }

        Spacer(Modifier.height(10.dp))
        SectionHeader("ACCOUNTS", "账户", trailing = {
            Text("${balances.size} 个", fontSize = 11.sp, color = InkMuted)
        })

        if (balances.isEmpty()) {
            GlassCard(Modifier.fillMaxWidth()) {
                Column(
                    Modifier.fillMaxWidth().padding(vertical = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        Icons.Rounded.AccountBalanceWallet,
                        contentDescription = null,
                        tint = InkMuted,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(Modifier.height(8.dp))
                    Text("还没有资产账户", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Ink)
                    Text(
                        "添加微信零钱、银行卡、现金等，记账时就能关联账户看余额。",
                        fontSize = 11.sp,
                        color = InkMuted
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
        }

        balances.forEach { item ->
            AssetCard(
                item = item,
                onEdit = { editTarget = item.asset },
                onDelete = { deleteTarget = item.asset }
            )
            Spacer(Modifier.height(8.dp))
        }

        Spacer(Modifier.height(4.dp))
        GradientButton(
            text = "添加账户",
            icon = Icons.Rounded.Add,
            onClick = { createOpen = true },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(140.dp))
    }

    if (createOpen) {
        AssetEditDialog(
            title = "添加账户",
            initialName = "",
            initialBalanceText = "",
            initialDate = DateX.today(),
            onDismiss = { createOpen = false },
            onConfirm = { name, cents, date ->
                viewModel.create(name, cents, date)
                createOpen = false
            }
        )
    }

    editTarget?.let { asset ->
        AssetEditDialog(
            title = "编辑账户",
            initialName = asset.name,
            initialBalanceText = Money.toPlainInput(asset.initialBalanceCents)
                .ifEmpty { if (asset.initialBalanceCents == 0L) "0" else "" },
            initialDate = asset.initialDate,
            onDismiss = { editTarget = null },
            onConfirm = { name, cents, date ->
                viewModel.update(asset, name, cents, date)
                editTarget = null
            }
        )
    }

    deleteTarget?.let { asset ->
        ConfirmDialog(
            title = "删除账户「${asset.name}」？",
            message = "删除后不再参与资产统计，已记账单保留（可重新添加同名账户）。此操作不可撤销。",
            confirmText = "删除",
            destructive = true,
            onConfirm = { viewModel.delete(asset) },
            onDismiss = { deleteTarget = null }
        )
    }
}

@Composable
private fun AssetCard(
    item: LedgerRepository.AssetBalance,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    GlassCard(
        Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(CandyLilac.copy(alpha = 0.55f), Color.White.copy(alpha = 0.25f))
                        )
                    )
                    .border(1.dp, Brush.verticalGradient(listOf(GlassBorderLight, GlassBorderDark)), RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Rounded.AccountBalanceWallet,
                    contentDescription = null,
                    tint = Ink,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(item.asset.name, fontSize = 15.sp, fontWeight = FontWeight.Black, color = Ink)
                Text(
                    "初始 ¥${Money.format(item.asset.initialBalanceCents)} · ${item.asset.initialDate}",
                    fontSize = 10.sp,
                    color = InkMuted
                )
            }
            Text("¥${Money.format(item.balanceCents)}", style = MoneyRow, color = Ink)
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("实时余额", fontSize = 10.sp, color = InkMuted)
            Spacer(Modifier.weight(1f))
            GhostPill("编辑", onClick = onEdit, icon = Icons.Rounded.Edit)
            Spacer(Modifier.width(6.dp))
            GlassIconButton(Icons.Rounded.Delete, "删除账户", onDelete, size = 32)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AssetEditDialog(
    title: String,
    initialName: String,
    initialBalanceText: String,
    initialDate: String,
    onDismiss: () -> Unit,
    onConfirm: (String, Long, String) -> Unit
) {
    var name by remember { mutableStateOf(initialName) }
    var balanceText by remember { mutableStateOf(initialBalanceText) }
    var date by remember { mutableStateOf(initialDate) }
    var pickerOpen by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color.White.copy(alpha = 0.98f),
        shape = CardShape,
        title = { Text(title, fontWeight = FontWeight.Black) },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("账户名称（如微信零钱）") },
                    singleLine = true,
                    shape = CardShape,
                    colors = OutlinedTextFieldDefaults.colors(cursorColor = Ink)
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = balanceText,
                    onValueChange = { balanceText = it },
                    label = { Text("初始余额（元，可为负数）") },
                    singleLine = true,
                    shape = CardShape,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    colors = OutlinedTextFieldDefaults.colors(cursorColor = Ink)
                )
                Spacer(Modifier.height(10.dp))
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(CardShape)
                        .background(Color.White.copy(alpha = 0.4f))
                        .border(1.dp, Color.White.copy(alpha = 0.7f), CardShape)
                        .clickable { pickerOpen = true }
                        .padding(horizontal = 12.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Rounded.CalendarMonth,
                        contentDescription = null,
                        tint = Ink,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("初始日期", fontSize = 12.sp, color = InkMuted)
                    Spacer(Modifier.weight(1f))
                    Text(date, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Ink)
                }
                error?.let {
                    Spacer(Modifier.height(6.dp))
                    Text(it, fontSize = 11.sp, color = Color(0xFFD50000), fontWeight = FontWeight.Bold)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val trimmed = name.trim()
                if (trimmed.isEmpty()) {
                    error = "账户名称不能为空"
                    return@TextButton
                }
                val cents = if (balanceText.isBlank()) 0L else Money.parseSignedToCents(balanceText)
                if (cents == null) {
                    error = "初始余额格式不正确（最多两位小数）"
                    return@TextButton
                }
                onConfirm(trimmed, cents, date)
            }) { Text("保存", fontWeight = FontWeight.Black, color = Ink) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消", color = InkMuted) }
        }
    )

    if (pickerOpen) {
        val millis = DateX.parse(date)?.toEpochDay()?.times(86_400_000L)
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = millis)
        DatePickerDialog(
            onDismissRequest = { pickerOpen = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let {
                        date = LocalDate.ofEpochDay(it / 86_400_000L).toString()
                    }
                    pickerOpen = false
                }) { Text("确定", fontWeight = FontWeight.Black, color = Ink) }
            },
            dismissButton = {
                TextButton(onClick = { pickerOpen = false }) { Text("取消", color = InkMuted) }
            }
        ) { DatePicker(state = pickerState) }
    }
}
