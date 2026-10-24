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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kumquat.ledger.KumquatApp
import com.kumquat.ledger.data.Category
import com.kumquat.ledger.data.CategorySeed
import com.kumquat.ledger.data.LedgerRepository
import com.kumquat.ledger.domain.TxType
import com.kumquat.ledger.ui.components.CategoryEditorDialog
import com.kumquat.ledger.ui.components.CategoryIconBadge
import com.kumquat.ledger.ui.components.GhostPill
import com.kumquat.ledger.ui.components.GlassCard
import com.kumquat.ledger.ui.components.KmTopBar
import com.kumquat.ledger.ui.components.SectionHeader
import com.kumquat.ledger.ui.theme.CardShape
import com.kumquat.ledger.ui.theme.GlassBorderDark
import com.kumquat.ledger.ui.theme.GlassBorderLight
import com.kumquat.ledger.ui.theme.Ink
import com.kumquat.ledger.ui.theme.InkMuted
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class CategoryEditorState(
    val type: TxType = TxType.EXPENSE,
    val category: Category? = null,
    val level: Int = 1,
    val parentId: Long? = null,
    val parentLabel: String = "",
    val error: String? = null
) {
    val editing: Boolean get() = category != null
}

data class CategoryDeleteState(
    val category: Category,
    val usage: LedgerRepository.CategoryUsage,
    val error: String? = null
)

class CategoriesViewModel : ViewModel() {

    private val repo = KumquatApp.instance.repository
    val message = MutableStateFlow<String?>(null)

    val categories: StateFlow<List<Category>> = repo.observeCategories()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val assets: StateFlow<List<LedgerRepository.AssetBalance>> = repo.observeAssetBalances()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _editor = MutableStateFlow<CategoryEditorState?>(null)
    val editor: StateFlow<CategoryEditorState?> = _editor.asStateFlow()

    private val _delete = MutableStateFlow<CategoryDeleteState?>(null)
    val delete: StateFlow<CategoryDeleteState?> = _delete.asStateFlow()

    fun clearMessage() {
        message.value = null
    }

    fun openAdd(type: TxType, level: Int, parentId: Long?, parentLabel: String) {
        _editor.value = CategoryEditorState(
            type = type,
            category = null,
            level = level,
            parentId = parentId,
            parentLabel = parentLabel
        )
    }

    fun openEdit(category: Category, parentLabel: String) {
        _editor.value = CategoryEditorState(
            type = TxType.parse(category.type) ?: TxType.EXPENSE,
            category = category,
            level = category.level,
            parentId = category.parentId,
            parentLabel = parentLabel
        )
    }

    fun closeEditor() {
        _editor.value = null
    }

    fun saveEditor(name: String, icon: String, assetId: Long?) {
        val editor = _editor.value ?: return
        viewModelScope.launch {
            val result = runCatching {
                if (editor.category == null) {
                    repo.addCategory(
                        type = editor.type,
                        level = editor.level,
                        parentId = editor.parentId,
                        name = name,
                        icon = icon,
                        defaultAssetId = assetId
                    )
                } else {
                    repo.editCategory(editor.category, name, icon, assetId)
                }
            }
            result.onSuccess {
                message.value = if (editor.category == null) "已新增分类「${name.trim()}」" else "已保存分类「${name.trim()}」"
                _editor.value = null
            }.onFailure { e ->
                _editor.value = editor.copy(error = e.message ?: "保存失败")
            }
        }
    }

    fun openDelete(category: Category) {
        if (category.name == CategorySeed.UNCATEGORIZED) return
        viewModelScope.launch {
            val usage = repo.categoryUsage(category)
            _delete.value = CategoryDeleteState(category, usage)
        }
    }

    fun closeDelete() {
        _delete.value = null
    }

    fun confirmDelete(deleteBranch: Boolean) {
        val target = _delete.value ?: return
        viewModelScope.launch {
            runCatching { repo.deleteCategory(target.category, deleteBranch) }
                .onSuccess {
                    message.value = "已删除「${target.category.name}」，账单已移到未分类"
                    _delete.value = null
                }
                .onFailure { e ->
                    _delete.value = target.copy(error = e.message ?: "删除失败")
                }
        }
    }

    fun toggleHidden(category: Category) {
        viewModelScope.launch {
            runCatching { repo.setCategoryHidden(category, !category.hidden) }
                .onFailure { message.value = "操作失败：${it.message}" }
        }
    }

    fun swap(a: Category, b: Category) {
        viewModelScope.launch { repo.swapCategory(a, b) }
    }
}

@Composable
fun CategoriesScreen(onBack: () -> Unit, viewModel: CategoriesViewModel = viewModel()) {
    val all by viewModel.categories.collectAsStateWithLifecycle()
    val assets by viewModel.assets.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val editor by viewModel.editor.collectAsStateWithLifecycle()
    val deleteState by viewModel.delete.collectAsStateWithLifecycle()
    var type by remember { mutableStateOf(TxType.EXPENSE) }

    LaunchedEffect(message) {
        if (message != null) {
            kotlinx.coroutines.delay(3000)
            viewModel.clearMessage()
        }
    }

    val l1List = remember(all, type) {
        all.filter { it.type == type.name && it.level == 1 }.sortedBy { it.sortOrder }
    }
    val assetNameById = remember(assets) { assets.associate { it.asset.id to it.asset.name } }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
    ) {
        KmTopBar(
            title = "分类管理",
            subtitle = "所有分类都可删除；账单不会被删除，会移到“未分类”",
            onBack = onBack,
            trailing = {
                TextButton(onClick = { viewModel.openAdd(type, 1, null, "新建一级分类") }) {
                    Icon(Icons.Rounded.Add, null, tint = Ink, modifier = Modifier.size(16.dp))
                    Text("新建", color = Ink, fontWeight = FontWeight.Black, fontSize = 13.sp)
                }
            }
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GhostPill("入账", { type = TxType.INCOME }, selected = type == TxType.INCOME)
            GhostPill("出账", { type = TxType.EXPENSE }, selected = type == TxType.EXPENSE)
        }
        Spacer(Modifier.height(10.dp))

        message?.let {
            GlassCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(12.dp)) {
                Text(it, fontSize = 12.sp, color = Ink, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(8.dp))
        }

        SectionHeader("LEVEL 1", "一级分类")

        if (l1List.isEmpty()) {
            GlassCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(18.dp)) {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("还没有一级分类", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Ink)
                    Spacer(Modifier.height(4.dp))
                    TextButton(onClick = { viewModel.openAdd(type, 1, null, "新建一级分类") }) {
                        Text("新建分类", color = Ink, fontWeight = FontWeight.Black)
                    }
                }
            }
        }

        l1List.forEachIndexed { index, l1 ->
            val children = all.filter { it.level == 2 && it.parentId == l1.id }.sortedBy { it.sortOrder }
            val isSystem = l1.name == CategorySeed.UNCATEGORIZED
            GlassCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CategoryIconBadge(l1.icon, size = 34)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            l1.name + if (l1.hidden) "（已隐藏）" else "",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Black,
                            color = if (l1.hidden) InkMuted else Ink
                        )
                        Text(
                            buildString {
                                append(if (isSystem) "系统兜底分类" else if (l1.isBuiltin) "内置分类" else "自定义分类")
                                l1.defaultAssetId?.let { id ->
                                    assetNameById[id]?.let { append(" · 默认："); append(it) }
                                }
                            },
                            fontSize = 10.sp,
                            color = InkMuted
                        )
                    }
                    Row {
                        if (!isSystem) {
                            SmallIcon(Icons.Rounded.ArrowUpward, "上移", enabled = index > 0) {
                                if (index > 0) viewModel.swap(l1, l1List[index - 1])
                            }
                            SmallIcon(Icons.Rounded.ArrowDownward, "下移", enabled = index < l1List.lastIndex) {
                                if (index < l1List.lastIndex) viewModel.swap(l1, l1List[index + 1])
                            }
                            SmallIcon(
                                if (l1.hidden) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                if (l1.hidden) "显示" else "隐藏"
                            ) { viewModel.toggleHidden(l1) }
                            SmallIcon(Icons.Rounded.Edit, "编辑") {
                                viewModel.openEdit(l1, "编辑一级分类")
                            }
                            SmallIcon(Icons.Rounded.Delete, "删除") { viewModel.openDelete(l1) }
                        }
                    }
                }

                if (children.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    children.forEach { child ->
                        val childSystem = child.name == CategorySeed.UNCATEGORIZED
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = 3.dp)
                                .clip(CardShape)
                                .background(Color.White.copy(alpha = 0.18f))
                                .border(1.dp, Brush.verticalGradient(listOf(GlassBorderLight, GlassBorderDark)), CardShape)
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CategoryIconBadge(child.icon, size = 22)
                            Spacer(Modifier.width(6.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    child.name + if (child.hidden) "（已隐藏）" else "",
                                    fontSize = 12.sp,
                                    color = if (child.hidden) InkMuted else Ink
                                )
                                child.defaultAssetId?.let { id ->
                                    assetNameById[id]?.let {
                                        Text("默认：$it", fontSize = 9.sp, color = InkMuted)
                                    }
                                }
                            }
                            if (!childSystem) {
                                SmallIcon(
                                    if (child.hidden) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                    if (child.hidden) "显示" else "隐藏"
                                ) { viewModel.toggleHidden(child) }
                                SmallIcon(Icons.Rounded.Edit, "编辑") {
                                    viewModel.openEdit(child, "编辑二级分类 · ${l1.name}")
                                }
                                SmallIcon(Icons.Rounded.Delete, "删除") { viewModel.openDelete(child) }
                            }
                        }
                    }
                }

                if (!isSystem) {
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = {
                        viewModel.openAdd(type, 2, l1.id, "在「${l1.name}」下新建二级分类")
                    }) {
                        Icon(Icons.Rounded.Add, null, tint = Ink, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("添加二级分类", fontSize = 12.sp, color = Ink, fontWeight = FontWeight.Bold)
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }

        Spacer(Modifier.height(24.dp))
    }

    editor?.let { ed ->
        CategoryEditorDialog(
            title = if (ed.editing) "编辑分类" else if (ed.level == 1) "新建一级分类" else "新建二级分类",
            parentLabel = ed.parentLabel,
            initialName = ed.category?.name.orEmpty(),
            initialIcon = ed.category?.icon ?: "category",
            initialDefaultAssetId = ed.category?.defaultAssetId,
            assets = assets,
            error = ed.error,
            onDismiss = viewModel::closeEditor,
            onSave = viewModel::saveEditor
        )
    }

    deleteState?.let { del ->
        AlertDialog(
            onDismissRequest = viewModel::closeDelete,
            containerColor = Color.White.copy(alpha = 0.98f),
            shape = CardShape,
            title = { Text("删除分类「${del.category.name}」？", fontWeight = FontWeight.Black, fontSize = 16.sp) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        "层级：${if (del.category.level == 1) "一级分类" else "二级分类"}",
                        fontSize = 12.sp,
                        color = Ink
                    )
                    Text("子分类：${del.usage.childCount} 个", fontSize = 12.sp, color = Ink)
                    Text("关联账单：${del.usage.billCount} 笔", fontSize = 12.sp, color = Ink)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "账单不会被删除，将移到“未分类”。删除整个分支会同时删除子分类。",
                        fontSize = 11.sp,
                        color = InkMuted
                    )
                    del.error?.let {
                        Text(it, fontSize = 11.sp, color = Color(0xFFD50000), fontWeight = FontWeight.Bold)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { viewModel.confirmDelete(deleteBranch = del.usage.childCount > 0) }) {
                    Text(
                        if (del.usage.childCount > 0) "删除整个分支" else "确认删除",
                        color = Color(0xFFD50000),
                        fontWeight = FontWeight.Black
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::closeDelete) { Text("取消", color = InkMuted) }
            }
        )
    }
}

@Composable
private fun SmallIcon(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Box(
        Modifier
            .size(28.dp)
            .clip(RoundedCornerShape(50))
            .background(Color.White.copy(alpha = if (enabled) 0.35f else 0.12f))
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            icon,
            contentDescription = description,
            tint = if (enabled) Ink else InkMuted.copy(alpha = 0.4f),
            modifier = Modifier.size(14.dp)
        )
    }
}
