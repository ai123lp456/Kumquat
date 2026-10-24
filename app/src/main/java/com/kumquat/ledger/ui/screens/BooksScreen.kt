package com.kumquat.ledger.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CallMerge
import androidx.compose.material.icons.rounded.CallSplit
import androidx.compose.material.icons.rounded.Check
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
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kumquat.ledger.KumquatApp
import com.kumquat.ledger.data.Account
import com.kumquat.ledger.data.Category
import com.kumquat.ledger.data.SessionStore
import com.kumquat.ledger.ui.components.BookCoverBadge
import com.kumquat.ledger.ui.components.ConfirmDialog
import com.kumquat.ledger.ui.components.CtaGradient
import com.kumquat.ledger.ui.components.GhostPill
import com.kumquat.ledger.ui.components.GlassCard
import com.kumquat.ledger.ui.components.GlassIconButton
import com.kumquat.ledger.ui.components.KmTopBar
import com.kumquat.ledger.ui.components.SectionHeader
import com.kumquat.ledger.ui.components.coverBrush
import com.kumquat.ledger.ui.theme.CandyLilac
import com.kumquat.ledger.ui.theme.CardShape
import com.kumquat.ledger.ui.theme.Ink
import com.kumquat.ledger.ui.theme.InkMuted
import com.kumquat.ledger.ui.theme.LabelEn
import com.kumquat.ledger.util.DateX
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class BooksState(
    val accounts: List<Account> = emptyList(),
    val currentId: Long = 0L,
    val categories: List<Category> = emptyList()
)

class BooksViewModel : ViewModel() {

    private val repo = KumquatApp.instance.repository
    val message = MutableStateFlow<String?>(null)

    val state: StateFlow<BooksState> = combine(
        repo.observeAccounts(),
        SessionStore.accountId,
        repo.observeCategories()
    ) { accounts, id, categories ->
        BooksState(accounts, id, categories)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BooksState())

    fun select(id: Long) = SessionStore.select(id)

    fun clearMessage() {
        message.value = null
    }

    fun create(name: String, coverType: String, coverValue: String) {
        if (name.isBlank()) {
            message.value = "账本名不能为空"
            return
        }
        viewModelScope.launch {
            val id = repo.createAccount(name, coverType, coverValue)
            SessionStore.select(id)
            message.value = "已创建账本「${name.trim()}」"
        }
    }

    fun update(account: Account, name: String, coverType: String, coverValue: String) {
        if (name.isBlank()) {
            message.value = "账本名不能为空"
            return
        }
        viewModelScope.launch {
            repo.updateAccount(account, name, coverType, coverValue)
            message.value = "已保存「${name.trim()}」"
        }
    }

    /** 删除账本：只允许删非当前账本；该账本下的账单会一并删除（二次确认由界面负责）。 */
    fun delete(account: Account, currentId: Long) {
        if (account.id == currentId) {
            message.value = "当前正在使用的账本不能删除，请先切换到其他账本"
            return
        }
        viewModelScope.launch {
            runCatching { repo.deleteAccount(account.id) }
                .onSuccess { message.value = "已删除「${account.name}」，同时删除 $it 条账单" }
                .onFailure { message.value = "删除失败：${it.message}" }
        }
    }

    fun merge(targetId: Long, sources: List<Long>) {
        viewModelScope.launch {
            runCatching { repo.mergeAccounts(targetId, sources) }
                .onSuccess { moved ->
                    SessionStore.select(targetId)
                    message.value = "已合并 ${sources.size} 个账本，迁移 $moved 条账单"
                }
                .onFailure { message.value = "合并失败：${it.message}" }
        }
    }

    fun splitByDate(sourceId: Long, newName: String, start: String, end: String) {
        viewModelScope.launch {
            runCatching {
                repo.splitByDate(sourceId, newName.ifBlank { "拆分账本" }, "color", "#8EC5FC", start, end)
            }.onSuccess { message.value = "已拆分出 $it 条账单" }
                .onFailure { message.value = "拆分失败：${it.message}" }
        }
    }

    fun splitByCategory(sourceId: Long, newName: String, l1: String, l2: String) {
        viewModelScope.launch {
            runCatching {
                repo.splitByCategory(sourceId, newName.ifBlank { "拆分账本" }, "color", "#8EC5FC", l1, l2)
            }.onSuccess { message.value = "已拆分出 $it 条账单" }
                .onFailure { message.value = "拆分失败：${it.message}" }
        }
    }
}

private val colorPresets = listOf(
    "#FFD1E8", "#E0C3FC", "#8EC5FC", "#FFEA00", "#00E676", "#FF9100", "#FF9F1C", "#A77BFF"
)
private val gradientPresets = listOf(
    "#FFD1E8,#8EC5FC", "#E0C3FC,#FF007F", "#FFEA00,#FF9100", "#00E676,#8EC5FC"
)

@Composable
fun BooksScreen(onBack: () -> Unit, viewModel: BooksViewModel = viewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val selected = remember { mutableStateListOf<Long>() }
    var editTarget by remember { mutableStateOf<Account?>(null) }
    var deleteTarget by remember { mutableStateOf<Account?>(null) }
    var createOpen by remember { mutableStateOf(false) }
    var mergeOpen by remember { mutableStateOf(false) }
    var splitOpen by remember { mutableStateOf(false) }

    LaunchedEffect(message) {
        if (message != null) {
            kotlinx.coroutines.delay(3200)
            viewModel.clearMessage()
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
    ) {
        KmTopBar(title = "账本管理", onBack = onBack)

        message?.let {
            GlassCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(12.dp)) {
                Text(it, fontSize = 12.sp, color = Ink, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(10.dp))
        }

        GlassCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(14.dp)) {
            Text("RULES · 规则", style = LabelEn)
            Spacer(Modifier.height(6.dp))
            Text(
                "合并：把多个账本的明细全部迁到一个账本，按时间排序，事务保证。\n" +
                    "拆分：按时间范围或分类，把明细原子地迁到新账本，失败整体回滚。\n" +
                    "删除：只能删「非当前」账本，二次确认后连同账本内的账单一起删除。",
                fontSize = 12.sp,
                color = Ink
            )
        }

        Spacer(Modifier.height(10.dp))
        SectionHeader("BOOKS", "我的账本", trailing = {
            Text("${state.accounts.size} 个", fontSize = 11.sp, color = InkMuted)
        })

        state.accounts.forEach { account ->
            val isCurrent = account.id == state.currentId
            val checked = selected.contains(account.id)
            GlassCard(
                Modifier
                    .fillMaxWidth()
                    .clickable { viewModel.select(account.id) },
                contentPadding = PaddingValues(14.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    BookCoverBadge(account.coverType, account.coverValue, size = 38.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                account.name,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Black,
                                color = Ink,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            if (isCurrent) {
                                Spacer(Modifier.width(6.dp))
                                Box(
                                    Modifier
                                        .clip(CircleShape)
                                        .background(CandyLilac.copy(alpha = 0.38f))
                                        .padding(horizontal = 8.dp, vertical = 2.dp)
                                ) {
                                    Text("当前", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Ink)
                                }
                            }
                        }
                        Text(
                            if (account.coverType == "gradient") "渐变封面" else "纯色封面 ${account.coverValue}",
                            fontSize = 11.sp,
                            color = InkMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    GlassIconButton(Icons.Rounded.Edit, "编辑账本", { editTarget = account }, size = 32)
                    if (!isCurrent) {
                        Spacer(Modifier.width(4.dp))
                        GlassIconButton(Icons.Rounded.Delete, "删除账本", { deleteTarget = account }, size = 32)
                    }
                    Spacer(Modifier.width(4.dp))
                    Box(
                        Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .then(
                                if (checked) {
                                    Modifier.background(Brush.linearGradient(CtaGradient))
                                } else {
                                    Modifier.background(Color.White.copy(alpha = 0.35f))
                                }
                            )
                            .border(1.dp, Color.White.copy(alpha = 0.7f), CircleShape)
                            .clickable {
                                if (checked) selected.remove(account.id) else selected.add(account.id)
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        if (checked) {
                            Icon(Icons.Rounded.Check, "已选", tint = Ink, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }

        Spacer(Modifier.height(4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            GhostPill("新建账本", { createOpen = true }, icon = Icons.Rounded.Add, modifier = Modifier.weight(1f))
            GhostPill(
                "合并所选 (${selected.size})",
                { if (selected.size >= 2) mergeOpen = true },
                icon = Icons.Rounded.CallMerge,
                modifier = Modifier.weight(1f)
            )
        }
        Spacer(Modifier.height(8.dp))
        GhostPill(
            "拆分当前账本",
            { splitOpen = true },
            icon = Icons.Rounded.CallSplit,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(130.dp))
    }

    if (createOpen) {
        BookEditDialog(
            title = "新建账本",
            initialName = "",
            initialCoverType = "color",
            initialCoverValue = "#FFD1E8",
            onDismiss = { createOpen = false },
            onConfirm = { name, type, value ->
                viewModel.create(name, type, value)
                createOpen = false
            }
        )
    }

    editTarget?.let { account ->
        BookEditDialog(
            title = "编辑账本",
            initialName = account.name,
            initialCoverType = account.coverType,
            initialCoverValue = account.coverValue,
            onDismiss = { editTarget = null },
            onConfirm = { name, type, value ->
                viewModel.update(account, name, type, value)
                editTarget = null
            }
        )
    }

    deleteTarget?.let { account ->
        ConfirmDialog(
            title = "删除账本「${account.name}」？",
            message = "该账本下的所有账单会一起删除，且无法恢复。建议先导出备份；删除后剩余账本不受影响。",
            confirmText = "删除",
            destructive = true,
            onConfirm = {
                viewModel.delete(account, state.currentId)
                selected.remove(account.id)
            },
            onDismiss = { deleteTarget = null }
        )
    }

    if (mergeOpen) {
        val target = state.accounts.firstOrNull { it.id == selected.firstOrNull() }
        AlertDialog(
            onDismissRequest = { mergeOpen = false },
            containerColor = Color.White.copy(alpha = 0.98f),
            shape = CardShape,
            title = { Text("合并账本？", fontWeight = FontWeight.Black) },
            text = {
                Text(
                    "将把 ${selected.size} 个账本的明细全部合并到「${target?.name ?: "第一个选中账本"}」，" +
                        "其余账本会被移除。此操作不可撤销，建议先导出备份。",
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val list = selected.toList()
                    if (list.isNotEmpty()) viewModel.merge(list.first(), list.drop(1))
                    selected.clear()
                    mergeOpen = false
                }) { Text("合并", fontWeight = FontWeight.Black, color = Ink) }
            },
            dismissButton = {
                TextButton(onClick = { mergeOpen = false }) { Text("取消", color = InkMuted) }
            }
        )
    }

    if (splitOpen) {
        val current = state.accounts.firstOrNull { it.id == state.currentId }
        if (current == null) {
            splitOpen = false
        } else {
            SplitDialog(
                account = current,
                categories = state.categories,
                onDismiss = { splitOpen = false },
                onByDate = { name, start, end ->
                    viewModel.splitByDate(current.id, name, start, end)
                    splitOpen = false
                },
                onByCategory = { name, l1, l2 ->
                    viewModel.splitByCategory(current.id, name, l1, l2)
                    splitOpen = false
                }
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BookEditDialog(
    title: String,
    initialName: String,
    initialCoverType: String,
    initialCoverValue: String,
    onDismiss: () -> Unit,
    onConfirm: (String, String, String) -> Unit
) {
    var name by remember { mutableStateOf(initialName) }
    var coverType by remember { mutableStateOf(initialCoverType) }
    var coverValue by remember { mutableStateOf(initialCoverValue) }

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
                    label = { Text("账本名称") },
                    singleLine = false,
                    maxLines = 4,
                    shape = CardShape,
                    supportingText = { Text("不限字数，可写「2026-2027-1学期」这类长名称", fontSize = 10.sp) },
                    colors = OutlinedTextFieldDefaults.colors(cursorColor = Ink)
                )
                Spacer(Modifier.height(12.dp))
                Text("封面", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = InkMuted)
                Spacer(Modifier.height(6.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    colorPresets.forEach { hex ->
                        CoverSwatch("color", hex, coverType == "color" && coverValue == hex) {
                            coverType = "color"
                            coverValue = hex
                        }
                    }
                    gradientPresets.forEach { value ->
                        CoverSwatch("gradient", value, coverType == "gradient" && coverValue == value) {
                            coverType = "gradient"
                            coverValue = value
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name, coverType, coverValue) }) {
                Text("保存", fontWeight = FontWeight.Black, color = Ink)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消", color = InkMuted) }
        }
    )
}

@Composable
private fun CoverSwatch(type: String, value: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(34.dp)
            .clip(CircleShape)
            .background(coverBrush(type, value))
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) Ink else Color.White,
                shape = CircleShape
            )
            .clickable(onClick = onClick)
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun SplitDialog(
    account: Account,
    categories: List<Category>,
    onDismiss: () -> Unit,
    onByDate: (String, String, String) -> Unit,
    onByCategory: (String, String, String) -> Unit
) {
    var mode by remember { mutableStateOf(0) } // 0 按时间，1 按分类
    var name by remember { mutableStateOf("${account.name}-拆分") }
    var start by remember { mutableStateOf(DateX.monthStart(DateX.nowMonth())) }
    var end by remember { mutableStateOf(DateX.today()) }
    var selectedL1 by remember { mutableStateOf<String?>(null) }
    var pickerOpen by remember { mutableStateOf(false) }
    var pickerTarget by remember { mutableStateOf(0) }

    val l1Options = categories.filter { it.level == 1 }.map { "${it.type}|${it.name}" }.distinct()

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color.White.copy(alpha = 0.98f),
        shape = CardShape,
        title = { Text("拆分「${account.name}」", fontWeight = FontWeight.Black) },
        text = {
            Column {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GhostPill("按时间", { mode = 0 }, selected = mode == 0, modifier = Modifier.weight(1f))
                    GhostPill("按分类", { mode = 1 }, selected = mode == 1, modifier = Modifier.weight(1f))
                }
                Spacer(Modifier.height(10.dp))
                if (mode == 0) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        GhostPill(start, {
                            pickerTarget = 0
                            pickerOpen = true
                        }, modifier = Modifier.weight(1f))
                        GhostPill(end, {
                            pickerTarget = 1
                            pickerOpen = true
                        }, modifier = Modifier.weight(1f))
                    }
                } else {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        l1Options.forEach { key ->
                            GhostPill(key.substringAfter("|"), {
                                selectedL1 = key
                            }, selected = selectedL1 == key)
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("新账本名称") },
                    singleLine = true,
                    shape = CardShape,
                    colors = OutlinedTextFieldDefaults.colors(cursorColor = Ink)
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (mode == 0) {
                    onByDate(name, start, end)
                } else {
                    val key = selectedL1 ?: return@TextButton
                    onByCategory(name, key.substringBefore("|"), key.substringAfter("|"))
                }
            }) { Text("拆分", fontWeight = FontWeight.Black, color = Ink) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消", color = InkMuted) }
        }
    )

    if (pickerOpen) {
        val initial = if (pickerTarget == 0) start else end
        val millis = DateX.parse(initial)?.toEpochDay()?.times(86_400_000L)
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = millis)
        DatePickerDialog(
            onDismissRequest = { pickerOpen = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { ms ->
                        val picked = LocalDate.ofEpochDay(ms / 86_400_000L).toString()
                        if (pickerTarget == 0) start = picked else end = picked
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
