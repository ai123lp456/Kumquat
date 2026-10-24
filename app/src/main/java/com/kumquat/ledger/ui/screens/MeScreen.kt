package com.kumquat.ledger.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kumquat.ledger.BuildConfig
import com.kumquat.ledger.KumquatApp
import com.kumquat.ledger.data.Account
import com.kumquat.ledger.data.SessionStore
import com.kumquat.ledger.ui.components.BookCoverBadge
import com.kumquat.ledger.ui.components.GlassCard
import com.kumquat.ledger.ui.components.KmTopBar
import com.kumquat.ledger.ui.components.TapCard
import com.kumquat.ledger.ui.components.pageEnter
import com.kumquat.ledger.ui.theme.Ink
import com.kumquat.ledger.ui.theme.InkMuted
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

class MeViewModel : ViewModel() {
    private val repo = KumquatApp.instance.repository

    val account: StateFlow<Account?> = combine(
        repo.observeAccounts(),
        SessionStore.accountId
    ) { list, id ->
        list.firstOrNull { it.id == id } ?: list.firstOrNull()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val assetCount: StateFlow<Int> = repo.observeAssets()
        .map { it.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)
}

@Composable
fun MeScreen(
    onBooks: () -> Unit,
    onAssets: () -> Unit,
    onCategories: () -> Unit,
    onImportExport: () -> Unit,
    onAbout: () -> Unit,
    viewModel: MeViewModel = viewModel()
) {
    val account by viewModel.account.collectAsStateWithLifecycle()
    val assetCount by viewModel.assetCount.collectAsStateWithLifecycle()

    Column(
        Modifier
            .fillMaxSize()
            .pageEnter()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
    ) {
        KmTopBar(title = "我的")

        GlassCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                BookCoverBadge(
                    account?.coverType ?: "color",
                    account?.coverValue ?: "#FFD1E8",
                    size = 46.dp
                )
                Spacer(Modifier.size(14.dp))
                Column(Modifier.weight(1f)) {
                    Text("小金桔 · kumquat", fontSize = 18.sp, fontWeight = FontWeight.Black, color = Ink)
                    Text(
                        "当前账本：${account?.name ?: "—"} · v${BuildConfig.VERSION_NAME}",
                        fontSize = 12.sp,
                        color = InkMuted
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(
                "纯本地记账，不联网、不要账号。数据只在这台手机上，记得定期导出备份。",
                fontSize = 12.sp,
                color = InkMuted
            )
        }

        Spacer(Modifier.height(12.dp))
        MeEntry("资产管理", "$assetCount 个账户 · 余额、内部转账", Icons.Rounded.AccountBalanceWallet, onAssets)
        Spacer(Modifier.height(10.dp))
        MeEntry("账本管理", "创建、切换、改名、删除、合并、拆分", Icons.Rounded.Settings, onBooks)
        Spacer(Modifier.height(10.dp))
        MeEntry("分类管理", "内置分类可隐藏，自定义分类可增删改", Icons.Rounded.Category, onCategories)
        Spacer(Modifier.height(10.dp))
        MeEntry("导入导出 / 备份", "JSON 导入导出、校验预览、生成提示词", Icons.Rounded.SwapVert, onImportExport)
        Spacer(Modifier.height(10.dp))
        MeEntry("关于 / 开源许可", "版本信息与第三方库", Icons.Rounded.Info, onAbout)
        Spacer(Modifier.height(150.dp))
    }
}

@Composable
private fun MeEntry(title: String, subtitle: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    TapCard(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            androidx.compose.foundation.layout.Box(
                Modifier
                    .size(42.dp)
                    .background(Color.White.copy(alpha = 0.35f), RoundedCornerShape(16.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = Ink, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.size(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black, color = Ink)
                Text(subtitle, fontSize = 11.sp, color = InkMuted)
            }
            Text("›", fontSize = 22.sp, color = InkMuted)
        }
    }
}
