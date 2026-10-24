package com.kumquat.ledger.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.FileUpload
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Upload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kumquat.ledger.KumquatApp
import com.kumquat.ledger.data.Account
import com.kumquat.ledger.data.ImportPreview
import com.kumquat.ledger.data.ImportValidator
import com.kumquat.ledger.data.LedgerCodec
import com.kumquat.ledger.data.PromptText
import com.kumquat.ledger.data.SessionStore
import com.kumquat.ledger.domain.Money
import com.kumquat.ledger.ui.components.GhostPill
import com.kumquat.ledger.ui.components.GlassCard
import com.kumquat.ledger.ui.components.GradientButton
import com.kumquat.ledger.ui.components.KmTopBar
import com.kumquat.ledger.ui.components.SectionHeader
import com.kumquat.ledger.ui.components.SegmentedPills
import com.kumquat.ledger.ui.theme.CardShape
import com.kumquat.ledger.ui.theme.Ink
import com.kumquat.ledger.ui.theme.InkMuted
import com.kumquat.ledger.ui.theme.LabelEn
import com.kumquat.ledger.ui.theme.Warning
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class ImportExportViewModel : ViewModel() {

    private val repo = KumquatApp.instance.repository

    val accounts: StateFlow<List<Account>> = repo.observeAccounts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val text = MutableStateFlow("")
    val preview = MutableStateFlow<ImportPreview?>(null)
    val status = MutableStateFlow<String?>(null)
    val allAccounts = MutableStateFlow(false)

    fun setText(value: String) {
        text.value = value
        preview.value = null
    }

    fun setStatus(value: String?) {
        status.value = value
    }

    fun validate() {
        preview.value = ImportValidator.validateText(text.value)
    }

    fun clear() {
        text.value = ""
        preview.value = null
        status.value = null
    }

    /** v1.1：生成提示词时把输入框里的原文一起嵌进去，省去手动粘贴。 */
    fun prompt(): String = PromptText.build(text.value)

    suspend fun exportJson(all: Boolean): String {
        val ids = if (all) null else listOf(SessionStore.accountId.value)
        return LedgerCodec.encode(repo.export(ids))
    }

    suspend fun importNow(): Boolean {
        val p = preview.value
        if (p == null || !p.ok || p.file == null) {
            status.value = "请先校验通过再导入"
            return false
        }
        return runCatching { repo.applyImport(p.file) }.fold(
            onSuccess = { result ->
                status.value = buildString {
                    append("导入成功：${result.insertedTx} 条账单，新建 ${result.createdAccounts} 个账本 / ")
                    append("${result.createdCategories} 个分类 / ${result.createdAssets} 个资产账户")
                    if (result.pendingRefundCount > 0) append("；${result.pendingRefundCount} 条退款待关联")
                    if (result.unmappedCategoryDefaults > 0) {
                        append("；${result.unmappedCategoryDefaults} 个分类默认账户未迁移")
                    }
                }
                text.value = ""
                preview.value = null
                true
            },
            onFailure = {
                status.value = "导入失败（已回滚）：${it.message}"
                false
            }
        )
    }
}

@Composable
fun ImportExportScreen(onBack: () -> Unit, viewModel: ImportExportViewModel = viewModel()) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val accounts by viewModel.accounts.collectAsStateWithLifecycle()
    val text by viewModel.text.collectAsStateWithLifecycle()
    val preview by viewModel.preview.collectAsStateWithLifecycle()
    val status by viewModel.status.collectAsStateWithLifecycle()
    val allAccounts by viewModel.allAccounts.collectAsStateWithLifecycle()

    var pendingExport by remember { mutableStateOf<String?>(null) }
    var promptOpen by remember { mutableStateOf(false) }
    var promptSnapshot by remember { mutableStateOf("") }
    var inputExpanded by remember { mutableStateOf(false) }

    val createDoc = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        val content = pendingExport
        if (uri != null && content != null) {
            scope.launch {
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri)
                        ?.use { it.write(content.toByteArray(Charsets.UTF_8)) }
                }
                viewModel.setStatus("已导出到文件（${content.length} 字符）")
            }
        }
    }

    val openDoc = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                val content = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                }
                if (content.isNullOrBlank()) {
                    viewModel.setStatus("文件读取失败或内容为空")
                } else {
                    viewModel.setText(content)
                    viewModel.validate()
                    viewModel.setStatus("已读取文件，请检查校验结果")
                }
            }
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
    ) {
        KmTopBar(
            title = "导入导出",
            subtitle = if (allAccounts) "范围：全部账本" else "范围：当前账本",
            onBack = onBack
        )

        GlassCard(Modifier.fillMaxWidth()) {
            Text("EXPORT · 导出", style = LabelEn)
            Spacer(Modifier.height(8.dp))
            SegmentedPills(
                options = listOf("当前账本", "全部账本"),
                selectedIndex = if (allAccounts) 1 else 0,
                onSelect = { viewModel.allAccounts.value = it == 1 }
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                GradientButton(
                    text = "保存 JSON 文件",
                    icon = Icons.Rounded.FileDownload,
                    onClick = {
                        scope.launch {
                            val json = viewModel.exportJson(allAccounts)
                            pendingExport = json
                            val stamp = LocalDateTime.now()
                                .format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmm"))
                            createDoc.launch("kumquat_$stamp.json")
                        }
                    },
                    modifier = Modifier.weight(1f)
                )
                GhostPill(
                    "复制",
                    {
                        scope.launch {
                            val json = viewModel.exportJson(allAccounts)
                            clipboard.setText(AnnotatedString(json))
                            viewModel.setStatus("JSON 已复制到剪贴板")
                        }
                    },
                    icon = Icons.Rounded.ContentCopy
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                GhostPill(
                    "生成导入提示词",
                    {
                        val generated = viewModel.prompt()
                        promptSnapshot = generated
                        promptOpen = true
                        clipboard.setText(AnnotatedString(generated))
                        viewModel.setStatus(
                            if (text.isBlank()) "提示词已复制（输入框为空，可先写一句描述再生成）"
                            else "提示词已复制，已包含输入框里的原文"
                        )
                    },
                    modifier = Modifier.weight(1f)
                )
                Text(
                    "${accounts.size} 个账本",
                    fontSize = 11.sp,
                    color = InkMuted,
                    modifier = Modifier.padding(top = 10.dp)
                )
            }
        }

        Spacer(Modifier.height(8.dp))
        SectionHeader("IMPORT", "导入 JSON")
        GlassCard(Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = text,
                onValueChange = viewModel::setText,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(if (inputExpanded) 360.dp else 150.dp),
                placeholder = {
                    Text(
                        "把 AI 生成的 JSON 粘贴到这里，先点「校验预览」再导入。\n支持 30KB 以上的长文本，框内可上下滚动。",
                        fontSize = 12.sp,
                        color = InkMuted
                    )
                },
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = Ink),
                shape = CardShape,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = Color.White.copy(alpha = 0.25f),
                    unfocusedContainerColor = Color.White.copy(alpha = 0.2f),
                    focusedBorderColor = Color.White,
                    unfocusedBorderColor = Color.White.copy(alpha = 0.55f),
                    cursorColor = Ink
                )
            )
            // 左下角：展开 / 折叠 + 字数（长文本查看用）
            Row(
                Modifier.fillMaxWidth().padding(top = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    Modifier
                        .clickable { inputExpanded = !inputExpanded }
                        .padding(horizontal = 4.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        if (inputExpanded) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown,
                        contentDescription = if (inputExpanded) "折叠" else "展开",
                        tint = Ink,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        if (inputExpanded) "折叠" else "展开",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Ink
                    )
                }
                Spacer(Modifier.weight(1f))
                Text("${text.length} 字符", fontSize = 10.sp, color = InkMuted)
            }
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                GhostPill(
                    "从文件读取",
                    { openDoc.launch(arrayOf("application/json", "text/plain", "*/*")) },
                    icon = Icons.Rounded.FileUpload,
                    modifier = Modifier.weight(1f)
                )
                GhostPill(
                    "校验预览",
                    { viewModel.validate() },
                    icon = Icons.Rounded.Upload,
                    modifier = Modifier.weight(1f)
                )
                GhostPill("清空", { viewModel.clear() })
            }

            preview?.let { p ->
                Spacer(Modifier.height(10.dp))
                PreviewCard(p)
                Spacer(Modifier.height(10.dp))
                GradientButton(
                    text = "确认导入",
                    onClick = {
                        scope.launch {
                            val ok = viewModel.importNow()
                            if (ok) viewModel.setStatus("导入完成，可返回账单查看")
                        }
                    },
                    enabled = p.ok,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            status?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, fontSize = 12.sp, color = Ink, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(Modifier.height(8.dp))
        GlassCard(Modifier.fillMaxWidth()) {
            Text("FORMAT · 格式约定", style = LabelEn)
            Spacer(Modifier.height(6.dp))
            Text(
                "金额一律整数分；日期 yyyy-MM-dd；type 只能是 INCOME / EXPENSE / REFUND / EXCHANGE / TRANSFER；" +
                    "linkedIndex 指向同批次 transactions 下标，用于回往关联原出账；" +
                    "转账用 assetName（转出）+ toAssetName（转入）。解析或校验失败不会写入任何数据。",
                fontSize = 12.sp,
                color = Ink
            )
        }
        Spacer(Modifier.height(120.dp))
    }

    if (promptOpen) {
        AlertDialog(
            onDismissRequest = { promptOpen = false },
            containerColor = Color.White.copy(alpha = 0.98f),
            shape = CardShape,
            title = { Text("导入提示词（已复制）", fontWeight = FontWeight.Black) },
            text = {
                SelectionContainer {
                    Text(
                        promptSnapshot,
                        fontSize = 11.sp,
                        color = Ink,
                        modifier = Modifier.heightIn(max = 380.dp).verticalScroll(rememberScrollState())
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    clipboard.setText(AnnotatedString(promptSnapshot))
                    promptOpen = false
                }) { Text("再复制一次", fontWeight = FontWeight.Black, color = Ink) }
            },
            dismissButton = {
                TextButton(onClick = { promptOpen = false }) { Text("关闭", color = InkMuted) }
            }
        )
    }
}

@Composable
private fun PreviewCard(p: ImportPreview) {
    GlassCard(
        Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(14.dp)
    ) {
        Text("PREVIEW · 预览", style = LabelEn)
        Spacer(Modifier.height(6.dp))
        Text(
            "账单 ${p.txCount} 条 · 账本 ${p.accountCount} 个 · 分类 ${p.categoryCount} 个 · " +
                "资产 ${p.assetCount} 个 · 合计 ¥${Money.format(p.totalCents)}",
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = Ink
        )
        if (p.totals.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            Text(ImportValidator.totalsText(p.totals), fontSize = 11.sp, color = InkMuted)
        }
        Spacer(Modifier.height(8.dp))
        if (p.ok) {
            Text("校验通过，可以导入。", fontSize = 12.sp, color = Ink, fontWeight = FontWeight.Bold)
            if (p.warnings.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    "有 ${p.warnings.size} 条提示（不阻塞导入）：",
                    fontSize = 11.sp,
                    color = InkMuted
                )
                p.warnings.take(4).forEach { warn ->
                    Text(warn.display(), fontSize = 10.sp, color = InkMuted)
                }
            }
        } else {
            Text(
                "发现 ${p.errors.size} 个问题，修复后才能导入：",
                fontSize = 12.sp,
                color = Ink,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(6.dp))
            p.errors.take(10).forEach { err ->
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp)
                        .background(Warning.copy(alpha = 0.45f), CardShape)
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(err.display(), fontSize = 11.sp, color = Ink)
                }
            }
            if (p.errors.size > 10) {
                Text("… 还有 ${p.errors.size - 10} 个问题", fontSize = 11.sp, color = InkMuted)
            }
        }
    }
}
