package com.kumquat.ledger.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kumquat.ledger.KumquatApp
import com.kumquat.ledger.data.Category
import com.kumquat.ledger.data.SessionStore
import com.kumquat.ledger.data.Tx
import com.kumquat.ledger.domain.DateRange
import com.kumquat.ledger.domain.Money
import com.kumquat.ledger.domain.PeriodStats
import com.kumquat.ledger.domain.RefundRules
import com.kumquat.ledger.domain.StatsEngine
import com.kumquat.ledger.domain.TxType
import com.kumquat.ledger.ui.components.ConfirmDialog
import com.kumquat.ledger.ui.components.EmptyState
import com.kumquat.ledger.ui.components.GhostPill
import com.kumquat.ledger.ui.components.GlassCard
import com.kumquat.ledger.ui.components.KmTopBar
import com.kumquat.ledger.ui.components.TxListRow
import com.kumquat.ledger.ui.components.TypeDot
import com.kumquat.ledger.ui.components.categoryIconKey
import com.kumquat.ledger.ui.components.huiWangAmountColor
import com.kumquat.ledger.ui.components.iconType
import com.kumquat.ledger.ui.theme.Ink
import com.kumquat.ledger.ui.theme.InkMuted
import com.kumquat.ledger.ui.theme.LabelEn
import com.kumquat.ledger.util.DateX
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class DayState(
    val date: String = DateX.today(),
    val txs: List<Tx> = emptyList(),
    val categories: List<Category> = emptyList(),
    val stats: PeriodStats = StatsEngine.empty(DateRange(DateX.today(), DateX.today())),
    val refundBadges: Map<Long, RefundRules.Badge> = emptyMap(),
    val message: String? = null
)

@OptIn(ExperimentalCoroutinesApi::class)
class DayViewModel : ViewModel() {

    private val repo = KumquatApp.instance.repository
    private val date = MutableStateFlow(DateX.today())
    private val message = MutableStateFlow<String?>(null)

    val state: StateFlow<DayState> = combine(SessionStore.accountId, date) { id, d -> id to d }
        .flatMapLatest { (id, d) ->
            combine(
                repo.observeAllTx(id),
                repo.observeStats(id, d, d),
                repo.observeCategories(),
                message
            ) { all, stats, cats, msg ->
                DayState(
                    date = d,
                    txs = all.filter { it.date == d }.sortedByDescending { it.id },
                    categories = cats,
                    stats = stats,
                    refundBadges = RefundRules.badges(all),
                    message = msg
                )
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DayState())

    fun setDate(value: String) {
        date.value = value
    }

    fun delete(tx: Tx) {
        viewModelScope.launch {
            runCatching { repo.deleteTx(tx) }
                .onFailure { message.value = it.message ?: "删除失败" }
        }
    }
}

@Composable
fun DayListScreen(
    date: String,
    onBack: () -> Unit,
    onAdd: (String) -> Unit,
    onOpenTx: (Long) -> Unit,
    viewModel: DayViewModel = viewModel()
) {
    LaunchedEffect(date) { viewModel.setDate(date) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    var pendingDelete by remember { mutableStateOf<Tx?>(null) }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        KmTopBar(
            title = "${DateX.dayLabel(date)} · ${DateX.cnWeekday(date)}",
            subtitle = DateX.dayHeaderEn(date),
            onBack = onBack,
            trailing = { GhostPill("记一笔", onClick = { onAdd(date) }) }
        )

        GlassCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(16.dp)) {
            Text("DAILY TOTAL · 当日小计", style = LabelEn)
            Spacer(Modifier.height(8.dp))
            DayStatRow("入账", TxType.INCOME, Money.format(state.stats.totalIncomeCents), Ink)
            Spacer(Modifier.height(6.dp))
            DayStatRow("出账", TxType.EXPENSE, Money.format(state.stats.totalExpenseCents), Ink)
            Spacer(Modifier.height(6.dp))
            DayStatRow(
                "回往",
                TxType.EXCHANGE,
                Money.formatSignedWithSymbol(state.stats.exchangeNetCents),
                huiWangAmountColor(state.stats.exchangeNetCents, InkMuted)
            )
        }

        state.message?.let {
            Spacer(Modifier.height(6.dp))
            Text(it, fontSize = 12.sp, color = androidx.compose.ui.graphics.Color(0xFFD50000))
        }

        Spacer(Modifier.height(10.dp))
        if (state.txs.isEmpty()) {
            EmptyState(title = "这一天没有账单", hint = "点右上角「记一笔」补记当天账目。")
        } else {
            LazyColumn(
                Modifier.weight(1f),
                contentPadding = PaddingValues(bottom = 40.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(state.txs, key = { it.id }) { tx ->
                    TxListRow(
                        tx = tx,
                        iconKey = categoryIconKey(
                            state.categories,
                            tx.iconType().name,
                            tx.categoryL1,
                            tx.categoryL2
                        ),
                        refundBadge = state.refundBadges[tx.id],
                        onClick = { onOpenTx(tx.id) },
                        onLongClick = { pendingDelete = tx }
                    )
                }
            }
        }
        Text(
            "长按账单可删除；点击可编辑。",
            style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
            color = InkMuted,
            modifier = Modifier.padding(vertical = 6.dp)
        )
    }

    pendingDelete?.let { tx ->
        ConfirmDialog(
            title = "删除这笔账单？",
            message = "删除后不可恢复。",
            confirmText = "删除",
            destructive = true,
            onConfirm = { viewModel.delete(tx) },
            onDismiss = { pendingDelete = null }
        )
    }
}

@Composable
private fun DayStatRow(label: String, type: TxType, value: String, color: androidx.compose.ui.graphics.Color) {
    androidx.compose.foundation.layout.Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
    ) {
        TypeDot(type, 7.dp)
        Text(label, fontSize = 13.sp, color = Ink, modifier = Modifier.padding(start = 8.dp))
        Spacer(Modifier.weight(1f))
        Text(value, fontSize = 14.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Black, color = color)
    }
}
