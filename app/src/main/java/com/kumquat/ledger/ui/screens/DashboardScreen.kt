package com.kumquat.ledger.ui.screens

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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import com.kumquat.ledger.data.CategorySeed
import com.kumquat.ledger.data.SessionStore
import com.kumquat.ledger.data.Tx
import com.kumquat.ledger.domain.DateRange
import com.kumquat.ledger.domain.LedgerMath
import com.kumquat.ledger.domain.Money
import com.kumquat.ledger.domain.PeriodStats
import com.kumquat.ledger.domain.RefundRules
import com.kumquat.ledger.domain.StatsEngine
import com.kumquat.ledger.domain.TxType
import com.kumquat.ledger.ui.components.BookCoverBadge
import com.kumquat.ledger.ui.components.ConfirmDialog
import com.kumquat.ledger.ui.components.EmptyState
import com.kumquat.ledger.ui.components.EntryKind
import com.kumquat.ledger.ui.components.GhostPill
import com.kumquat.ledger.ui.components.GlassCard
import com.kumquat.ledger.ui.components.GlassIconButton
import com.kumquat.ledger.ui.components.GlassPill
import com.kumquat.ledger.ui.components.HeroMoney
import com.kumquat.ledger.ui.components.SectionHeader
import com.kumquat.ledger.ui.components.TxListRow
import com.kumquat.ledger.ui.components.TypeDot
import com.kumquat.ledger.ui.components.categoryIconKey
import com.kumquat.ledger.ui.components.huiWangAmountColor
import com.kumquat.ledger.ui.components.iconType
import com.kumquat.ledger.ui.components.pageEnter
import com.kumquat.ledger.ui.components.semanticColor
import com.kumquat.ledger.ui.theme.HeroCardShape
import com.kumquat.ledger.ui.theme.Ink
import com.kumquat.ledger.ui.theme.InkMuted
import com.kumquat.ledger.ui.theme.LabelEn
import com.kumquat.ledger.util.DateX
import java.time.YearMonth
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class DayGroup(val date: String, val txs: List<Tx>, val netCents: Long)

data class DashboardState(
    val month: YearMonth = DateX.nowMonth(),
    val account: Account? = null,
    val stats: PeriodStats = StatsEngine.empty(
        DateRange(DateX.monthStart(DateX.nowMonth()), DateX.monthEnd(DateX.nowMonth()))
    ),
    val groups: List<DayGroup> = emptyList(),
    val categories: List<Category> = emptyList(),
    val filterKind: EntryKind? = null,
    val filterCat: String? = null,
    val message: String? = null,
    val refundBadges: Map<Long, RefundRules.Badge> = emptyMap()
)

@OptIn(ExperimentalCoroutinesApi::class)
class DashboardViewModel : ViewModel() {

    private val repo = KumquatApp.instance.repository
    private val month = MutableStateFlow(DateX.nowMonth())
    private val filterKind = MutableStateFlow<EntryKind?>(null)
    private val filterCat = MutableStateFlow<String?>(null)
    private val message = MutableStateFlow<String?>(null)

    private data class Filtered(
        val account: Account?,
        val month: YearMonth,
        val kind: EntryKind?,
        val cat: String?
    )

    val accounts: StateFlow<List<Account>> = repo.observeAccounts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val state: StateFlow<DashboardState> = combine(
        accounts,
        SessionStore.accountId,
        month,
        filterKind
    ) { list, id, ym, kind ->
        Filtered(list.firstOrNull { it.id == id } ?: list.firstOrNull(), ym, kind, null)
    }.combine(filterCat) { f, cat ->
        f.copy(cat = cat)
    }.combine(message) { f, msg ->
        f to msg
    }.flatMapLatest { (f, msg) ->
        val account = f.account
        if (account == null) {
            flowOf(
                DashboardState(
                    month = f.month,
                    filterKind = f.kind,
                    filterCat = f.cat,
                    message = msg
                )
            )
        } else {
            combine(
                repo.observeAllTx(account.id),
                repo.observeCategories()
            ) { txs, categories ->
                buildState(account, f, txs, categories, msg)
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DashboardState())

    init {
        viewModelScope.launch {
            repo.observeAccounts().collect { list ->
                if (list.isNotEmpty() && list.none { it.id == SessionStore.accountId.value }) {
                    SessionStore.select(list.first().id)
                }
            }
        }
        viewModelScope.launch {
            message.collect { msg ->
                if (msg != null) {
                    kotlinx.coroutines.delay(3000)
                    if (message.value == msg) message.value = null
                }
            }
        }
    }

    private fun buildState(
        account: Account,
        f: Filtered,
        txs: List<Tx>,
        categories: List<Category>,
        msg: String?
    ): DashboardState {
        val start = DateX.monthStart(f.month)
        val end = DateX.monthEnd(f.month)
        val stats = StatsEngine.compute(account.id, txs, DateRange(start, end))
        val visible = txs.filter { tx ->
            tx.date in start..end &&
                matchesKind(tx, f.kind) &&
                (f.cat == null || tx.categoryL1 == f.cat)
        }
        val groups = visible.groupBy { it.date }
            .toSortedMap(compareByDescending { it })
            .map { (date, list) ->
                DayGroup(
                    date = date,
                    txs = list.sortedByDescending { it.id },
                    netCents = StatsEngine.compute(account.id, txs, DateRange.single(date)).netCents
                )
            }
        return DashboardState(
            month = f.month,
            account = account,
            stats = stats,
            groups = groups,
            categories = categories,
            filterKind = f.kind,
            filterCat = f.cat,
            message = msg,
            refundBadges = RefundRules.badges(txs)
        )
    }

    private fun matchesKind(tx: Tx, kind: EntryKind?): Boolean {
        if (kind == null) return true
        val type = TxType.parse(tx.type) ?: return false
        return when (kind) {
            EntryKind.HUIWANG -> type == TxType.REFUND || type == TxType.EXCHANGE
            else -> type == kind.baseType()
        }
    }

    fun prevMonth() {
        month.value = month.value.minusMonths(1)
    }

    fun nextMonth() {
        month.value = month.value.plusMonths(1)
    }

    fun selectAccount(id: Long) = SessionStore.select(id)

    fun setFilterKind(kind: EntryKind?) {
        filterKind.value = kind
        filterCat.value = null
    }

    fun setFilterCat(cat: String?) {
        filterCat.value = cat
    }

    fun delete(tx: Tx) {
        viewModelScope.launch {
            runCatching { repo.deleteTx(tx) }
                .onFailure { message.value = it.message ?: "删除失败" }
        }
    }
}

@Composable
fun DashboardScreen(
    onAdd: (String?) -> Unit,
    onOpenTx: (Long) -> Unit,
    onManageBooks: () -> Unit,
    onOpenDay: (String) -> Unit,
    onOpenImportExport: () -> Unit,
    viewModel: DashboardViewModel = viewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val accounts by viewModel.accounts.collectAsStateWithLifecycle()
    var menuOpen by remember { mutableStateOf(false) }
    var catMenuOpen by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<Tx?>(null) }

    val catOptions = remember(state.categories, state.filterKind) {
        val typeName = state.filterKind
            ?.takeIf { it != EntryKind.HUIWANG }
            ?.baseType()?.name
        state.categories
            .filter {
                it.level == 1 && !it.hidden &&
                    (typeName == null || it.type == typeName) &&
                    it.name != CategorySeed.UNCATEGORIZED
            }
            .map { it.name }
            .distinct()
    }

    LazyColumn(
        Modifier.fillMaxSize().pageEnter(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 132.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item(key = "top") {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box {
                    GlassPill(Modifier.clickable { menuOpen = true }) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            BookCoverBadge(
                                state.account?.coverType ?: "color",
                                state.account?.coverValue ?: "#FFD1E8"
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                state.account?.name ?: "账本",
                                fontWeight = FontWeight.Bold,
                                color = Ink,
                                fontSize = 14.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.widthIn(max = 150.dp)
                            )
                            Icon(
                                Icons.Rounded.KeyboardArrowDown,
                                contentDescription = "切换账本",
                                tint = Ink,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                    DropdownMenu(
                        expanded = menuOpen,
                        onDismissRequest = { menuOpen = false },
                        containerColor = Color.White.copy(alpha = 0.98f),
                        shape = RoundedCornerShape(18.dp)
                    ) {
                        accounts.forEach { account ->
                            DropdownMenuItem(
                                text = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        BookCoverBadge(account.coverType, account.coverValue, size = 18.dp)
                                        Spacer(Modifier.width(8.dp))
                                        Text(account.name, color = Ink, fontWeight = FontWeight.Bold)
                                    }
                                },
                                onClick = {
                                    viewModel.selectAccount(account.id)
                                    menuOpen = false
                                }
                            )
                        }
                        DropdownMenuItem(
                            text = { Text("管理账本…", color = Ink) },
                            onClick = {
                                menuOpen = false
                                onManageBooks()
                            }
                        )
                    }
                }
                Spacer(Modifier.weight(1f))
                GhostPill("导入导出", onClick = onOpenImportExport, icon = Icons.Rounded.SwapVert)
            }
        }

        state.message?.let { msg ->
            item(key = "message") {
                GlassCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(12.dp)) {
                    Text(msg, fontSize = 12.sp, color = Ink, fontWeight = FontWeight.Bold)
                }
            }
        }

        item(key = "overview") {
            GlassCard(shape = HeroCardShape, elevation = 18.dp, contentPadding = PaddingValues(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("THIS MONTH", style = LabelEn)
                        Text(
                            DateX.monthLabel(state.month),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Black
                        )
                    }
                    GlassIconButton(Icons.Rounded.ChevronLeft, "上个月", viewModel::prevMonth, size = 34)
                    Spacer(Modifier.width(6.dp))
                    GlassIconButton(Icons.Rounded.ChevronRight, "下个月", viewModel::nextMonth, size = 34)
                }
                Spacer(Modifier.height(12.dp))
                Text("NET BALANCE · 净收支", style = LabelEn)
                Spacer(Modifier.height(2.dp))
                HeroMoney(state.stats.netCents)
                Spacer(Modifier.height(16.dp))
                SummaryRow(EntryKind.INCOME, "入账", state.stats.totalIncomeCents)
                Spacer(Modifier.height(8.dp))
                SummaryRow(EntryKind.EXPENSE, "出账", state.stats.totalExpenseCents)
                Spacer(Modifier.height(8.dp))
                HuiWangSummaryRow(state.stats.exchangeNetCents)
                val refundHint = when {
                    state.stats.refundAppliedCents > 0L ->
                        "退款抵扣 ¥${Money.format(state.stats.refundAppliedCents)}"
                    state.stats.refundOccurredCents > 0L ->
                        "本期发生退款 ¥${Money.format(state.stats.refundOccurredCents)}（已冲减原账单月份）"
                    else -> null
                }
                refundHint?.let {
                    Spacer(Modifier.height(6.dp))
                    Text(it, fontSize = 11.sp, color = InkMuted)
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    "回往净额已并入收支；退款已抵扣原支出；完全抵消时不计入。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = InkMuted,
                    fontSize = 11.sp
                )
                if (state.stats.pendingLinkCount + state.stats.pendingConfirmCount > 0) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "有 ${state.stats.pendingLinkCount + state.stats.pendingConfirmCount} 条历史回往待处理，暂未计入统计。",
                        fontSize = 10.sp,
                        color = InkMuted
                    )
                }
            }
        }

        item(key = "header") {
            SectionHeader("RECENT", "最近账单")
        }

        item(key = "filters") {
            Column {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    GhostPill(
                        "全部",
                        { viewModel.setFilterKind(null) },
                        selected = state.filterKind == null && state.filterCat == null
                    )
                    EntryKind.ordered.forEach { kind ->
                        GhostPill(
                            kind.label,
                            { viewModel.setFilterKind(kind) },
                            selected = state.filterKind == kind
                        )
                    }
                    Box {
                        GhostPill(
                            state.filterCat ?: "分类",
                            { catMenuOpen = true },
                            icon = Icons.Rounded.ArrowDropDown,
                            selected = state.filterCat != null
                        )
                        DropdownMenu(
                            expanded = catMenuOpen,
                            onDismissRequest = { catMenuOpen = false },
                            containerColor = Color.White.copy(alpha = 0.98f),
                            shape = RoundedCornerShape(18.dp)
                        ) {
                            DropdownMenuItem(
                                text = { Text("全部类别", color = Ink) },
                                onClick = {
                                    viewModel.setFilterCat(null)
                                    catMenuOpen = false
                                }
                            )
                            catOptions.forEach { name ->
                                DropdownMenuItem(
                                    text = { Text(name, color = Ink) },
                                    onClick = {
                                        viewModel.setFilterCat(name)
                                        catMenuOpen = false
                                    }
                                )
                            }
                        }
                    }
                }
                if (state.filterKind != null || state.filterCat != null) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "已筛选：${state.filterKind?.label ?: "全部类型"}" +
                            (state.filterCat?.let { " · $it" } ?: "") +
                            "（点「全部」清除）",
                        fontSize = 10.sp,
                        color = InkMuted
                    )
                }
            }
        }

        if (state.groups.isEmpty()) {
            val filtering = state.filterKind != null || state.filterCat != null
            item(key = "empty") {
                EmptyState(
                    title = if (filtering) "没有符合条件的账单" else "这个月还没有账单",
                    hint = if (filtering) "换一个类型/分类，或点「全部」清除筛选。" else "点底部中间的 + 记第一笔，或从「导入导出」粘贴 JSON 导入。",
                    icon = Icons.Rounded.SwapVert
                )
            }
        }

        state.groups.forEach { group ->
            item(key = "day-${group.date}") {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { onOpenDay(group.date) }
                        .padding(horizontal = 4.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(DateX.dayHeaderEn(group.date), style = LabelEn)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "${DateX.dayLabel(group.date)} · ${DateX.cnWeekday(group.date)}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = Ink
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        "¥${Money.format(group.netCents)}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Black,
                        color = Ink
                    )
                }
            }
            items(group.txs, key = { it.id }) { tx ->
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

    pendingDelete?.let { tx ->
        ConfirmDialog(
            title = "删除这笔账单？",
            message = "${LedgerMath.catName(tx.categoryL1, tx.categoryL2)} · ¥${Money.format(tx.amountCents)}（${tx.date}）删除后不可恢复。",
            confirmText = "删除",
            destructive = true,
            onConfirm = { viewModel.delete(tx) },
            onDismiss = { pendingDelete = null }
        )
    }
}

@Composable
private fun SummaryRow(kind: EntryKind, label: String, cents: Long) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        TypeDot(kind.baseType(), 8.dp)
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
private fun HuiWangSummaryRow(netCents: Long) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        TypeDot(TxType.EXCHANGE, 8.dp)
        Spacer(Modifier.width(8.dp))
        Text("回往", style = MaterialTheme.typography.bodyMedium, color = Ink)
        Spacer(Modifier.weight(1f))
        Text(
            Money.formatSignedWithSymbol(netCents),
            style = MaterialTheme.typography.titleMedium,
            color = huiWangAmountColor(netCents, InkMuted),
            fontWeight = FontWeight.Black
        )
    }
}
