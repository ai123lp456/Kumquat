package com.kumquat.ledger.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
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
import androidx.compose.ui.graphics.vector.ImageVector
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
import com.kumquat.ledger.domain.CategoryAmount
import com.kumquat.ledger.domain.DateRange
import com.kumquat.ledger.domain.Money
import com.kumquat.ledger.domain.PeriodStats
import com.kumquat.ledger.domain.StatsEngine
import com.kumquat.ledger.domain.TxType
import com.kumquat.ledger.ui.components.EmptyState
import com.kumquat.ledger.ui.components.GhostPill
import com.kumquat.ledger.ui.components.GlassCard
import com.kumquat.ledger.ui.components.GradientBar
import com.kumquat.ledger.ui.components.KmTopBar
import com.kumquat.ledger.ui.components.SectionHeader
import com.kumquat.ledger.ui.components.SegmentedPills
import com.kumquat.ledger.ui.components.TypeDot
import com.kumquat.ledger.ui.components.categoryIcon
import com.kumquat.ledger.ui.components.huiWangAmountColor
import com.kumquat.ledger.ui.components.pageEnter
import com.kumquat.ledger.ui.components.semanticColor
import com.kumquat.ledger.ui.theme.CardShape
import com.kumquat.ledger.ui.theme.GlassBorderDark
import com.kumquat.ledger.ui.theme.GlassBorderLight
import com.kumquat.ledger.ui.theme.Ink
import com.kumquat.ledger.ui.theme.InkMuted
import com.kumquat.ledger.ui.theme.LabelEn
import com.kumquat.ledger.util.DateX
import java.time.LocalDate
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn

data class StatsState(
    val periodIndex: Int = 0,
    val start: String = DateX.monthStart(DateX.nowMonth()),
    val end: String = DateX.monthEnd(DateX.nowMonth()),
    val stats: PeriodStats = StatsEngine.empty(
        DateRange(DateX.monthStart(DateX.nowMonth()), DateX.monthEnd(DateX.nowMonth()))
    ),
    val categories: List<Category> = emptyList(),
    val account: Account? = null
)

@OptIn(ExperimentalCoroutinesApi::class)
class StatsViewModel : ViewModel() {

    private val repo = KumquatApp.instance.repository
    private val period = MutableStateFlow(0)
    private val customStart = MutableStateFlow(DateX.monthStart(DateX.nowMonth()))
    private val customEnd = MutableStateFlow(DateX.monthEnd(DateX.nowMonth()))

    private data class Range(val start: String, val end: String)

    private val range: StateFlow<Range> = combine(period, customStart, customEnd) { p, s, e ->
        when (p) {
            0 -> Range(DateX.monthStart(DateX.nowMonth()), DateX.monthEnd(DateX.nowMonth()))
            1 -> {
                val prev = DateX.nowMonth().minusMonths(1)
                Range(DateX.monthStart(prev), DateX.monthEnd(prev))
            }
            else -> Range(s, e)
        }
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        Range(DateX.monthStart(DateX.nowMonth()), DateX.monthEnd(DateX.nowMonth()))
    )

    private val accounts: StateFlow<List<Account>> = repo.observeAccounts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private data class Quad(val account: Account?, val range: Range, val period: Int, val accountId: Long)

    val state: StateFlow<StatsState> = combine(
        accounts,
        SessionStore.accountId,
        range,
        period
    ) { list, id, r, p ->
        Quad(list.firstOrNull { it.id == id } ?: list.firstOrNull(), r, p, id)
    }.flatMapLatest { quad ->
        val account = quad.account
        val r = quad.range
        if (account == null || quad.accountId <= 0L) {
            flowOf(StatsState(periodIndex = quad.period, start = r.start, end = r.end))
        } else {
            combine(
                repo.observeStats(account.id, r.start, r.end),
                repo.observeCategories()
            ) { stats, categories ->
                StatsState(
                    periodIndex = quad.period,
                    start = r.start,
                    end = r.end,
                    stats = stats,
                    categories = categories,
                    account = account
                )
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StatsState())

    fun setPeriod(index: Int) {
        period.value = index
    }

    fun setCustomStart(date: String) {
        customStart.value = date
        if (customEnd.value < date) customEnd.value = date
        period.value = 2
    }

    fun setCustomEnd(date: String) {
        customEnd.value = date
        if (customStart.value > date) customStart.value = date
        period.value = 2
    }
}

private data class RankItem(
    val name: String,
    val cents: Long,
    val type: TxType,
    val iconKey: String,
    val isHuiWang: Boolean = false
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(
    onOpenHuiWang: (String, String) -> Unit,
    viewModel: StatsViewModel = viewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var pickerTarget by remember { mutableStateOf(0) }
    var pickerOpen by remember { mutableStateOf(false) }

    fun openPicker(target: Int) {
        pickerTarget = target
        pickerOpen = true
    }

    val stats = state.stats
    val expenseRanking = remember(stats, state.categories) {
        val rows = stats.expenseRanking.map { c ->
            RankItem(
                name = c.displayName,
                cents = c.cents,
                type = TxType.EXPENSE,
                iconKey = iconKeyOf(state.categories, TxType.EXPENSE.name, c)
            )
        } + if (stats.exchangeNetCents < 0L) {
            listOf(
                RankItem(
                    name = "回往",
                    cents = -stats.exchangeNetCents,
                    type = TxType.EXCHANGE,
                    iconKey = "currency_exchange",
                    isHuiWang = true
                )
            )
        } else emptyList()
        rows.sortedWith(compareByDescending<RankItem> { it.cents }.thenBy { it.isHuiWang })
    }
    val incomeRanking = remember(stats, state.categories) {
        val rows = stats.incomeRanking.map { c ->
            RankItem(
                name = c.displayName,
                cents = c.cents,
                type = TxType.INCOME,
                iconKey = iconKeyOf(state.categories, TxType.INCOME.name, c)
            )
        } + if (stats.exchangeNetCents > 0L) {
            listOf(
                RankItem(
                    name = "回往",
                    cents = stats.exchangeNetCents,
                    type = TxType.EXCHANGE,
                    iconKey = "currency_exchange",
                    isHuiWang = true
                )
            )
        } else emptyList()
        rows.sortedWith(compareByDescending<RankItem> { it.cents }.thenBy { it.isHuiWang })
    }

    Column(
        Modifier
            .fillMaxSize()
            .pageEnter()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
    ) {
        KmTopBar(title = "统计", subtitle = state.account?.let { "账本：${it.name}" } ?: null)

        SegmentedPills(
            options = listOf("本月", "上月", "自定义"),
            selectedIndex = state.periodIndex,
            onSelect = viewModel::setPeriod
        )
        if (state.periodIndex == 2) {
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GhostPill("起 ${state.start}", onClick = { openPicker(0) }, modifier = Modifier.weight(1f))
                GhostPill("止 ${state.end}", onClick = { openPicker(1) }, modifier = Modifier.weight(1f))
            }
        }

        Spacer(Modifier.height(12.dp))
        GlassCard(Modifier.fillMaxWidth()) {
            Text("OVERVIEW · 小计", style = LabelEn)
            Spacer(Modifier.height(10.dp))
            StatRow(EntryRow.INCOME, "入账", stats.totalIncomeCents)
            Spacer(Modifier.height(8.dp))
            StatRow(EntryRow.EXPENSE, "出账", stats.totalExpenseCents)
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                TypeDot(TxType.EXCHANGE, 8.dp)
                Spacer(Modifier.width(8.dp))
                Text("回往", style = MaterialTheme.typography.bodyMedium, color = Ink)
                if (stats.exchangeNetCents != 0L) NetBadge()
                Spacer(Modifier.weight(1f))
                Text(
                    Money.formatSignedWithSymbol(stats.exchangeNetCents),
                    style = MaterialTheme.typography.titleMedium,
                    color = huiWangAmountColor(stats.exchangeNetCents, InkMuted),
                    fontWeight = FontWeight.Black
                )
            }
            val hint = when {
                stats.refundAppliedCents > 0L ->
                    "退款抵扣 ¥${Money.format(stats.refundAppliedCents)}，已冲减本期支出"
                stats.refundOccurredCents > 0L ->
                    "本期发生退款 ¥${Money.format(stats.refundOccurredCents)}，已冲减原账单所属月份"
                else -> null
            }
            hint?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, fontSize = 11.sp, color = InkMuted)
            }
            if (stats.hasPendingHuiWang) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "有 ${stats.pendingLinkCount + stats.pendingConfirmCount} 条历史回往待确认，暂未计入统计。",
                    fontSize = 11.sp,
                    color = InkMuted,
                    modifier = Modifier.clickable { onOpenHuiWang(state.start, state.end) }
                )
            }
            Spacer(Modifier.height(10.dp))
            Text(
                "回往按净额排行；退款已从对应支出中冲减，不重复计入。",
                fontSize = 11.sp,
                color = InkMuted
            )
        }

        Spacer(Modifier.height(8.dp))
        SectionHeader("EXPENSE RANKING", "出账排行")
        if (expenseRanking.isEmpty()) {
            EmptyState(title = "这段时间没有出账", hint = "换个月份看看，或先记一笔出账。")
        } else {
            GlassCard(Modifier.fillMaxWidth()) {
                expenseRanking.forEachIndexed { index, item ->
                    RankingRow(
                        rank = index + 1,
                        item = item,
                        total = stats.totalExpenseCents,
                        onClick = if (item.isHuiWang) {
                            { onOpenHuiWang(state.start, state.end) }
                        } else null
                    )
                    if (index != expenseRanking.lastIndex) Spacer(Modifier.height(10.dp))
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        SectionHeader("INCOME RANKING", "入账排行")
        if (incomeRanking.isEmpty()) {
            EmptyState(title = "这段时间没有入账", hint = "生活费、红包、兼职都记在这里。")
        } else {
            GlassCard(Modifier.fillMaxWidth()) {
                incomeRanking.forEachIndexed { index, item ->
                    RankingRow(
                        rank = index + 1,
                        item = item,
                        total = stats.totalIncomeCents,
                        onClick = if (item.isHuiWang) {
                            { onOpenHuiWang(state.start, state.end) }
                        } else null
                    )
                    if (index != incomeRanking.lastIndex) Spacer(Modifier.height(10.dp))
                }
            }
        }

        if (stats.exchangeNetCents != 0L || stats.hasRefundInPeriod) {
            Spacer(Modifier.height(8.dp))
            GlassCard(
                Modifier.fillMaxWidth().clickable { onOpenHuiWang(state.start, state.end) },
                contentPadding = androidx.compose.foundation.layout.PaddingValues(14.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        categoryIcon("currency_exchange"),
                        contentDescription = null,
                        tint = Ink,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        Text("回往明细", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Ink)
                        Text("查看退款与代付/借出/收回/还款全部记录", fontSize = 10.sp, color = InkMuted)
                    }
                    Text("查看", fontSize = 12.sp, color = Ink, fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(Modifier.height(132.dp))
    }

    if (pickerOpen) {
        val initial = if (pickerTarget == 0) state.start else state.end
        val millis = DateX.parse(initial)?.toEpochDay()?.times(86_400_000L)
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = millis)
        DatePickerDialog(
            onDismissRequest = { pickerOpen = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { ms ->
                        val date = LocalDate.ofEpochDay(ms / 86_400_000L).toString()
                        if (pickerTarget == 0) viewModel.setCustomStart(date) else viewModel.setCustomEnd(date)
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

private enum class EntryRow { INCOME, EXPENSE }

@Composable
private fun StatRow(row: EntryRow, label: String, cents: Long) {
    val type = if (row == EntryRow.INCOME) TxType.INCOME else TxType.EXPENSE
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        TypeDot(type, 8.dp)
        Spacer(Modifier.width(8.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium, color = Ink)
        Spacer(Modifier.weight(1f))
        Text(
            "¥${Money.format(cents)}",
            style = MaterialTheme.typography.titleMedium,
            color = Ink,
            fontWeight = FontWeight.Black
        )
    }
}

@Composable
private fun NetBadge() {
    Box(
        Modifier
            .padding(start = 6.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(Color.White.copy(alpha = 0.55f))
            .border(1.dp, Color.White.copy(alpha = 0.8f), RoundedCornerShape(6.dp))
            .padding(horizontal = 5.dp, vertical = 1.dp)
    ) {
        Text("净", fontSize = 9.sp, color = InkMuted, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun RankingRow(
    rank: Int,
    item: RankItem,
    total: Long,
    onClick: (() -> Unit)?
) {
    val fraction = if (total <= 0L) 0f else item.cents.toFloat() / total.toFloat()
    Column(
        Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                rank.toString().padStart(2, '0'),
                fontSize = 11.sp,
                fontWeight = FontWeight.Black,
                color = InkMuted,
                modifier = Modifier.width(22.dp)
            )
            Icon(
                categoryIcon(item.iconKey),
                contentDescription = null,
                tint = Ink,
                modifier = Modifier.size(16.dp)
            )
            Spacer(Modifier.width(6.dp))
            Text(
                item.name,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = Ink,
                modifier = Modifier.weight(1f)
            )
            if (item.isHuiWang) NetBadge()
            Spacer(Modifier.width(6.dp))
            Text("¥${Money.format(item.cents)}", fontSize = 13.sp, fontWeight = FontWeight.Black, color = Ink)
            Spacer(Modifier.width(6.dp))
            Text(
                "${(fraction * 100).toInt()}%",
                fontSize = 10.sp,
                color = InkMuted,
                modifier = Modifier.width(34.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.End
            )
        }
        Spacer(Modifier.height(5.dp))
        GradientBar(
            progress = fraction,
            color = if (item.isHuiWang) TxType.EXCHANGE.semanticColor() else item.type.semanticColor(),
            height = 8.dp
        )
    }
}

private fun iconKeyOf(categories: List<Category>, type: String, amount: CategoryAmount): String {
    if (amount.l2.isNotBlank()) {
        val parent = categories.firstOrNull { it.type == type && it.level == 1 && it.name == amount.l1 }
        val child = categories.firstOrNull {
            it.type == type && it.level == 2 && it.name == amount.l2 && it.parentId == parent?.id
        }
        if (child != null && child.icon.isNotBlank()) return child.icon
    }
    return categories.firstOrNull { it.type == type && it.level == 1 && it.name == amount.l1 }?.icon ?: ""
}
