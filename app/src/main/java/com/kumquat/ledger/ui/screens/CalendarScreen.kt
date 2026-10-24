package com.kumquat.ledger.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kumquat.ledger.KumquatApp
import com.kumquat.ledger.data.Account
import com.kumquat.ledger.data.SessionStore
import com.kumquat.ledger.domain.DateRange
import com.kumquat.ledger.domain.Money
import com.kumquat.ledger.domain.PeriodStats
import com.kumquat.ledger.domain.StatsEngine
import com.kumquat.ledger.domain.TxType
import com.kumquat.ledger.ui.components.EntryKind
import com.kumquat.ledger.ui.components.GlassCard
import com.kumquat.ledger.ui.components.GlassDivider
import com.kumquat.ledger.ui.components.GlassIconButton
import com.kumquat.ledger.ui.components.KmTopBar
import com.kumquat.ledger.ui.components.TypeDot
import com.kumquat.ledger.ui.components.huiWangAmountColor
import com.kumquat.ledger.ui.components.pageEnter
import com.kumquat.ledger.ui.components.semanticColor
import com.kumquat.ledger.ui.components.toEntryKind
import com.kumquat.ledger.ui.theme.CandyPink
import com.kumquat.ledger.ui.theme.CardShape
import com.kumquat.ledger.ui.theme.Ink
import com.kumquat.ledger.ui.theme.InkMuted
import com.kumquat.ledger.ui.theme.LabelEn
import com.kumquat.ledger.util.DateX
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn

data class CalendarState(
    val month: YearMonth = DateX.nowMonth(),
    val marks: Map<String, Set<EntryKind>> = emptyMap(),
    val stats: PeriodStats = StatsEngine.empty(
        DateRange(DateX.monthStart(DateX.nowMonth()), DateX.monthEnd(DateX.nowMonth()))
    ),
    val account: Account? = null
)

@OptIn(ExperimentalCoroutinesApi::class)
class CalendarViewModel : ViewModel() {

    private val repo = KumquatApp.instance.repository
    private val month = MutableStateFlow(DateX.nowMonth())

    val accounts: StateFlow<List<Account>> = repo.observeAccounts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val state: StateFlow<CalendarState> = combine(
        accounts,
        SessionStore.accountId,
        month
    ) { list, id, ym ->
        Triple(list.firstOrNull { it.id == id } ?: list.firstOrNull(), ym, id)
    }.flatMapLatest { (account, ym, _) ->
        if (account == null) {
            flowOf(CalendarState(month = ym))
        } else {
            val start = DateX.monthStart(ym)
            val end = DateX.monthEnd(ym)
            combine(
                repo.observeDayTypes(account.id, start, end),
                repo.observeStats(account.id, start, end)
            ) { days, stats ->
                val marks = HashMap<String, MutableSet<EntryKind>>()
                days.forEach { row ->
                    TxType.parse(row.type)?.let { type ->
                        marks.getOrPut(row.date) { linkedSetOf() }.add(type.toEntryKind())
                    }
                }
                CalendarState(ym, marks, stats, account)
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CalendarState())

    fun prevMonth() {
        month.value = month.value.minusMonths(1)
    }

    fun nextMonth() {
        month.value = month.value.plusMonths(1)
    }

    fun jumpToday() {
        month.value = DateX.nowMonth()
    }
}

@Composable
fun CalendarScreen(
    onOpenDay: (String) -> Unit,
    viewModel: CalendarViewModel = viewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val today = remember { DateX.today() }
    val cells = remember(state.month) { DateX.monthCells(state.month) }

    Column(
        Modifier
            .fillMaxSize()
            .pageEnter()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
    ) {
        KmTopBar(
            title = "日历",
            subtitle = state.account?.let { "账本：${it.name}" } ?: null,
            trailing = {
                androidx.compose.material3.TextButton(onClick = viewModel::jumpToday) {
                    Text("今天", color = Ink, fontWeight = FontWeight.Black, fontSize = 13.sp)
                }
            }
        )

        GlassCard(
            shape = CardShape,
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .pointerInput(Unit) {
                    var drag = 0f
                    detectHorizontalDragGestures(
                        onDragStart = { drag = 0f },
                        onHorizontalDrag = { _, amount -> drag += amount },
                        onDragEnd = {
                            if (drag < -90f) viewModel.nextMonth() else if (drag > 90f) viewModel.prevMonth()
                        }
                    )
                }
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(DateX.monthShort(state.month), style = LabelEn)
                    Text(
                        "${DateX.monthLabel(state.month)}",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Black
                    )
                }
                GlassIconButton(Icons.Rounded.ChevronLeft, "上个月", viewModel::prevMonth, size = 34)
                Spacer(Modifier.width(6.dp))
                GlassIconButton(Icons.Rounded.ChevronRight, "下个月", viewModel::nextMonth, size = 34)
            }

            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth()) {
                DateX.weekHeaders().forEach { w ->
                    Text(
                        w,
                        Modifier.weight(1f),
                        textAlign = TextAlign.Center,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = InkMuted
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            cells.chunked(7).forEach { week ->
                Row(Modifier.fillMaxWidth()) {
                    week.forEach { date ->
                        DayCell(
                            date = date,
                            kinds = date?.let { state.marks[it.toString()] } ?: emptySet(),
                            isToday = date != null && date.toString() == today,
                            modifier = Modifier.weight(1f),
                            onClick = { date?.let { onOpenDay(it.toString()) } }
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            GlassDivider()
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                EntryKind.ordered.forEach { kind ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(kind.semanticColor())
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(kind.label, fontSize = 10.sp, color = InkMuted)
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        GlassCard(Modifier.fillMaxWidth()) {
            Text("MONTH SUMMARY · 本月小计", style = LabelEn)
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                TypeDot(TxType.INCOME, 7.dp)
                Spacer(Modifier.width(8.dp))
                Text("入账", fontSize = 13.sp, color = Ink)
                Spacer(Modifier.weight(1f))
                Text("¥${Money.format(state.stats.totalIncomeCents)}", fontSize = 14.sp, fontWeight = FontWeight.Black, color = Ink)
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                TypeDot(TxType.EXPENSE, 7.dp)
                Spacer(Modifier.width(8.dp))
                Text("出账", fontSize = 13.sp, color = Ink)
                Spacer(Modifier.weight(1f))
                Text("¥${Money.format(state.stats.totalExpenseCents)}", fontSize = 14.sp, fontWeight = FontWeight.Black, color = Ink)
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                TypeDot(TxType.EXCHANGE, 7.dp)
                Spacer(Modifier.width(8.dp))
                Text("回往", fontSize = 13.sp, color = Ink)
                Spacer(Modifier.weight(1f))
                Text(
                    Money.formatSignedWithSymbol(state.stats.exchangeNetCents),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Black,
                    color = huiWangAmountColor(state.stats.exchangeNetCents, InkMuted)
                )
            }
        }
        Spacer(Modifier.height(132.dp))
    }
}

@Composable
private fun DayCell(
    date: LocalDate?,
    kinds: Set<EntryKind>,
    isToday: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier
            .height(52.dp)
            .padding(2.dp)
            .clip(CardShape)
            .then(
                if (date != null) Modifier
                    .background(Color.White.copy(alpha = 0.18f))
                    .border(
                        1.dp,
                        if (isToday) Brush.linearGradient(listOf(CandyPink, CandyPink.copy(alpha = 0.4f)))
                        else Brush.linearGradient(listOf(Color.White.copy(alpha = 0.5f), Color.Transparent)),
                        CardShape
                    )
                else Modifier
            )
            .then(if (date != null) Modifier.clickable { onClick() } else Modifier),
        contentAlignment = Alignment.Center
    ) {
        if (date != null) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    Modifier.then(
                        if (isToday) Modifier
                            .size(24.dp)
                            .shadow(8.dp, CircleShape, clip = false, spotColor = CandyPink)
                            .background(Color.White.copy(alpha = 0.9f), CircleShape)
                        else Modifier
                    ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = date.dayOfMonth.toString(),
                        fontSize = 13.sp,
                        fontWeight = if (isToday) FontWeight.Black else FontWeight.Medium,
                        color = Ink
                    )
                }
                Spacer(Modifier.height(2.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    EntryKind.ordered.forEach { kind ->
                        if (kinds.contains(kind)) {
                            Box(
                                Modifier
                                    .size(4.dp)
                                    .shadow(4.dp, CircleShape, clip = false, spotColor = kind.semanticColor())
                                    .background(kind.semanticColor(), CircleShape)
                            )
                        }
                    }
                }
            }
        }
    }
}
