package com.kumquat.ledger.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CurrencyExchange
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import com.kumquat.ledger.data.Account
import com.kumquat.ledger.data.Category
import com.kumquat.ledger.data.SessionStore
import com.kumquat.ledger.data.Tx
import com.kumquat.ledger.domain.DateRange
import com.kumquat.ledger.domain.HuiWangStatus
import com.kumquat.ledger.domain.HuiWangSubtype
import com.kumquat.ledger.domain.LegacyHuiWangResolver
import com.kumquat.ledger.domain.Money
import com.kumquat.ledger.domain.PeriodStats
import com.kumquat.ledger.domain.StatsEngine
import com.kumquat.ledger.domain.TxType
import com.kumquat.ledger.ui.components.EmptyState
import com.kumquat.ledger.ui.components.GhostPill
import com.kumquat.ledger.ui.components.GlassCard
import com.kumquat.ledger.ui.components.KmTopBar
import com.kumquat.ledger.ui.components.categoryIcon
import com.kumquat.ledger.ui.components.huiWangAmountColor
import com.kumquat.ledger.ui.components.huiWangIcon
import com.kumquat.ledger.ui.components.iconType
import com.kumquat.ledger.ui.components.pageEnter
import com.kumquat.ledger.ui.components.semanticColor
import com.kumquat.ledger.ui.components.categoryIconKey
import com.kumquat.ledger.ui.theme.CardShape
import com.kumquat.ledger.ui.theme.GlassBorderDark
import com.kumquat.ledger.ui.theme.GlassBorderLight
import com.kumquat.ledger.ui.theme.Ink
import com.kumquat.ledger.ui.theme.InkMuted
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn

enum class DetailFilter(val label: String) {
    ALL("全部"),
    REFUND("退款"),
    ADVANCE_PAY("代付"),
    LEND("借出"),
    COLLECT("收回"),
    REPAY("还款")
}

data class HuiWangItem(
    val tx: Tx,
    val subtype: HuiWangSubtype?,
    val linked: Tx?,
    val pending: Boolean,
    val legacy: Boolean
)

data class HuiWangDetailState(
    val start: String,
    val end: String,
    val account: Account? = null,
    val stats: PeriodStats = StatsEngine.empty(DateRange(start, end)),
    val items: List<HuiWangItem> = emptyList(),
    val categories: List<Category> = emptyList(),
    val filter: DetailFilter = DetailFilter.ALL
)

@OptIn(ExperimentalCoroutinesApi::class)
class HuiWangDetailViewModel : ViewModel() {

    private val repo = KumquatApp.instance.repository
    private val start = MutableStateFlow("")
    private val end = MutableStateFlow("")
    private val filter = MutableStateFlow(DetailFilter.ALL)

    @OptIn(ExperimentalCoroutinesApi::class)
    val state: StateFlow<HuiWangDetailState> = combine(
        repo.observeAccounts(),
        SessionStore.accountId,
        start,
        end,
        filter
    ) { accounts, id, s, e, f ->
        val account = accounts.firstOrNull { it.id == id } ?: accounts.firstOrNull()
        Input(account, s, e, f)
    }.flatMapLatest { input ->
        val account = input.account
        if (account == null || input.start.isBlank() || input.end.isBlank()) {
            flowOf(HuiWangDetailState(start = input.start, end = input.end, filter = input.filter))
        } else {
            combine(
                repo.observeAllTx(account.id),
                repo.observeCategories()
            ) { txs, categories -> build(account, input, txs, categories) }
        }
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        HuiWangDetailState(start = "", end = "")
    )

    private data class Input(
        val account: Account?,
        val start: String,
        val end: String,
        val filter: DetailFilter
    )

    private fun build(
        account: Account,
        input: Input,
        txs: List<Tx>,
        categories: List<Category>
    ): HuiWangDetailState {
        val byId = txs.associateBy { it.id }
        val all = txs.mapNotNull { tx ->
            val type = TxType.parse(tx.type) ?: return@mapNotNull null
            if (type != TxType.REFUND && type != TxType.EXCHANGE) return@mapNotNull null
            if (tx.date !in input.start..input.end) return@mapNotNull null
            val linked = tx.linkedTxId?.let { byId[it] }
            val resolved = LegacyHuiWangResolver.resolve(tx, linked)
            HuiWangItem(
                tx = tx,
                subtype = resolved.subtype,
                linked = linked,
                pending = resolved.status != HuiWangStatus.OK,
                legacy = resolved.legacy
            )
        }.sortedWith(compareByDescending<HuiWangItem> { it.tx.date }.thenByDescending { it.tx.id })

        val filtered = when (input.filter) {
            DetailFilter.ALL -> all
            DetailFilter.REFUND -> all.filter { TxType.parse(it.tx.type) == TxType.REFUND }
            DetailFilter.ADVANCE_PAY -> all.filter { it.subtype == HuiWangSubtype.ADVANCE_PAY }
            DetailFilter.LEND -> all.filter { it.subtype == HuiWangSubtype.LEND }
            DetailFilter.COLLECT -> all.filter { it.subtype == HuiWangSubtype.COLLECT }
            DetailFilter.REPAY -> all.filter { it.subtype == HuiWangSubtype.REPAY }
        }
        return HuiWangDetailState(
            start = input.start,
            end = input.end,
            account = account,
            stats = StatsEngine.compute(account.id, txs, DateRange(input.start, input.end)),
            items = filtered,
            categories = categories,
            filter = input.filter
        )
    }

    fun load(startDate: String, endDate: String) {
        if (start.value.isEmpty()) start.value = startDate
        if (end.value.isEmpty()) end.value = endDate
    }

    fun setFilter(value: DetailFilter) {
        filter.value = value
    }
}

@Composable
fun HuiWangDetailScreen(
    start: String,
    end: String,
    onBack: () -> Unit,
    onOpenTx: (Long) -> Unit,
    viewModel: HuiWangDetailViewModel = viewModel()
) {
    LaunchedEffect(start, end) { viewModel.load(start, end) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val stats = state.stats

    LazyColumn(
        Modifier.fillMaxSize().pageEnter(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item(key = "top") {
            KmTopBar(
                title = "回往明细",
                subtitle = "${state.start} ~ ${state.end}",
                onBack = onBack
            )
        }

        item(key = "summary") {
            GlassCard(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("回往净额", fontSize = 11.sp, color = InkMuted)
                        Text(
                            Money.formatSignedWithSymbol(stats.exchangeNetCents),
                            fontSize = 26.sp,
                            fontWeight = FontWeight.Black,
                            color = huiWangAmountColor(stats.exchangeNetCents, InkMuted)
                        )
                    }
                    if (stats.refundOccurredCents > 0L) {
                        Column(horizontalAlignment = Alignment.End) {
                            Text("本期发生退款", fontSize = 11.sp, color = InkMuted)
                            Text(
                                "¥${Money.format(stats.refundOccurredCents)}",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Black,
                                color = Ink
                            )
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "退款按原账单所属月份冲减支出，因此“本期发生退款”不一定等于本期支出减少额。",
                    fontSize = 10.sp,
                    color = InkMuted
                )
                if (stats.pendingLinkCount + stats.pendingConfirmCount > 0) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "有 ${stats.pendingLinkCount + stats.pendingConfirmCount} 条记录待关联/待确认，暂未计入净额。",
                        fontSize = 10.sp,
                        color = Color(0xFFD50000)
                    )
                }
            }
        }

        item(key = "filters") {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                DetailFilter.entries.forEach { f ->
                    GhostPill(
                        text = f.label,
                        onClick = { viewModel.setFilter(f) },
                        selected = state.filter == f
                    )
                }
            }
        }

        if (state.items.isEmpty()) {
            item(key = "empty") {
                EmptyState(
                    title = "没有回往记录",
                    hint = "这个时间段没有退款、代付、借出、收回或还款。",
                    icon = Icons.Rounded.CurrencyExchange
                )
            }
        }

        items(state.items, key = { it.tx.id }) { item ->
            HuiWangRecordRow(
                item = item,
                categories = state.categories,
                onClick = { onOpenTx(item.tx.id) }
            )
        }
    }
}

@Composable
private fun HuiWangRecordRow(
    item: HuiWangItem,
    categories: List<Category>,
    onClick: () -> Unit
) {
    val tx = item.tx
    val type = TxType.parse(tx.type) ?: TxType.EXCHANGE
    val icon = if (type == TxType.REFUND) {
        categoryIcon(categoryIconKey(categories, TxType.EXPENSE.name, tx.categoryL1, tx.categoryL2))
    } else {
        item.subtype?.let { huiWangIcon(it) } ?: Icons.Rounded.CurrencyExchange
    }
    val title = when {
        type == TxType.REFUND -> {
            val base = when (item.subtype) {
                HuiWangSubtype.REFUND_FULL -> "全额退款"
                HuiWangSubtype.REFUND_PARTIAL -> "部分退款"
                else -> "退款"
            }
            val label = if (item.legacy) "历史退款 · $base" else base
            "$label · ${tx.categoryL1.ifBlank { "原支出" }}"
        }
        else -> item.subtype?.label ?: "历史往来"
    }
    val subtitle = buildString {
        append(tx.date)
        if (tx.note.isNotBlank()) append(" · ").append(tx.note)
        val linked = item.linked
        if (type == TxType.REFUND && linked != null) {
            append(" · 已冲减 ").append(linked.date.take(7)).append(" 支出")
        } else if (linked != null) {
            append(" · 关联 ").append(linked.categoryL1).append(" ").append(linked.date)
        }
    }
    val badge = when {
        item.pending -> if (type == TxType.REFUND) "待关联" else "待确认"
        item.legacy -> if (type == TxType.REFUND) "历史退款" else "历史往来"
        type == TxType.REFUND -> if (item.subtype == HuiWangSubtype.REFUND_FULL) "已全额退款" else "已冲减原支出"
        else -> "回往"
    }

    Row(
        Modifier
            .fillMaxWidth()
            .clip(CardShape)
            .background(Color.White.copy(alpha = 0.30f))
            .border(1.dp, Brush.verticalGradient(listOf(GlassBorderLight, GlassBorderDark)), CardShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color.White.copy(alpha = 0.35f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = Ink, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Ink, maxLines = 1)
            Text(subtitle, fontSize = 10.sp, color = InkMuted, maxLines = 1)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                "¥${Money.format(tx.amountCents)}",
                fontSize = 14.sp,
                fontWeight = FontWeight.Black,
                color = Ink
            )
            Text(
                badge,
                fontSize = 10.sp,
                color = if (item.pending) Color(0xFFD50000) else InkMuted
            )
        }
    }
}
