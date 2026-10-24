package com.kumquat.ledger.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Link
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kumquat.ledger.KumquatApp
import com.kumquat.ledger.data.Category
import com.kumquat.ledger.data.LedgerRepository
import com.kumquat.ledger.data.RefundCandidate
import com.kumquat.ledger.data.SessionStore
import com.kumquat.ledger.data.Tx
import com.kumquat.ledger.domain.HuiWangSubtype
import com.kumquat.ledger.domain.LedgerMath
import com.kumquat.ledger.domain.LegacyHuiWangResolver
import com.kumquat.ledger.domain.Money
import com.kumquat.ledger.domain.RefundRules
import com.kumquat.ledger.domain.TxType
import com.kumquat.ledger.ui.components.AmountReadOnlyField
import com.kumquat.ledger.ui.components.CalcKeypad
import com.kumquat.ledger.ui.components.CategoryEditorDialog
import com.kumquat.ledger.ui.components.CategoryIconBadge
import com.kumquat.ledger.ui.components.ConfirmDialog
import com.kumquat.ledger.ui.components.DateRow
import com.kumquat.ledger.ui.components.EntryKind
import com.kumquat.ledger.ui.components.EntryTypeSelector
import com.kumquat.ledger.ui.components.GhostPill
import com.kumquat.ledger.ui.components.GlassIconButton
import com.kumquat.ledger.ui.components.KmTopBar
import com.kumquat.ledger.ui.components.NoteField
import com.kumquat.ledger.ui.components.SaveBar
import com.kumquat.ledger.ui.components.SectionHeader
import com.kumquat.ledger.ui.components.TypeDot
import com.kumquat.ledger.ui.components.hint
import com.kumquat.ledger.ui.components.huiWangIcon
import com.kumquat.ledger.ui.components.semanticColor
import com.kumquat.ledger.ui.components.semanticGradient
import com.kumquat.ledger.ui.components.toEntryKind
import com.kumquat.ledger.ui.theme.CardShape
import com.kumquat.ledger.ui.theme.GlassBorderDark
import com.kumquat.ledger.ui.theme.GlassBorderLight
import com.kumquat.ledger.ui.theme.Ink
import com.kumquat.ledger.ui.theme.InkMuted
import com.kumquat.ledger.util.DateX
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** 分类快捷新建对话框的上下文。 */
data class QuickCategoryEditor(
    val level: Int,
    val parentId: Long?,
    val parentLabel: String,
    val error: String? = null
)

data class AddState(
    val editing: Boolean = false,
    val loading: Boolean = true,
    val entry: EntryKind = EntryKind.EXPENSE,
    val subtype: HuiWangSubtype? = null,
    val amountText: String = "",
    val amountLocked: Boolean = false,
    val date: String = DateX.today(),
    val note: String = "",
    val l1: String = "",
    val l2: String = "",
    val categories: List<Category> = emptyList(),
    val linkedTxId: Long? = null,
    val linkedLabel: String? = null,
    val linkedDate: String? = null,
    val linkedRemainingCents: Long? = null,
    val refundCandidates: List<RefundCandidate> = emptyList(),
    val refundQuery: String = "",
    val showRefundPicker: Boolean = false,
    val exchangeCandidates: List<Tx> = emptyList(),
    val showExchangePicker: Boolean = false,
    val assets: List<LedgerRepository.AssetBalance> = emptyList(),
    val assetId: Long? = null,
    val toAssetId: Long? = null,
    val assetTouched: Boolean = false,
    val assetPickerTarget: Int? = null,
    val pendingOp: Char? = null,
    val leftCents: Long? = null,
    val expression: String = "",
    val keypadVisible: Boolean = true,
    val editor: QuickCategoryEditor? = null,
    val error: String? = null,
    val hint: String? = null,
    val saving: Boolean = false,
    val saved: Boolean = false,
    val deleted: Boolean = false,
    val dirty: Boolean = false
) {
    private val categoryType: String get() = entry.baseType().name

    val l1List: List<Category>
        get() = if (entry == EntryKind.INCOME || entry == EntryKind.EXPENSE) {
            categories.filter { it.type == categoryType && it.level == 1 && !it.hidden }
                .sortedBy { it.sortOrder }
        } else emptyList()

    val selectedL1: Category?
        get() = l1List.firstOrNull { it.name == l1 }

    val l2List: List<Category>
        get() = selectedL1?.let { parent ->
            categories.filter {
                it.type == categoryType && it.level == 2 && it.parentId == parent.id && !it.hidden
            }.sortedBy { it.sortOrder }
        } ?: emptyList()

    val validAmount: Long? get() = Money.parseToCents(amountText)

    val effectiveCents: Long?
        get() {
            val operand = Money.parseToCents(amountText)
            val left = leftCents
            return when {
                left == null -> operand
                operand == null -> left
                pendingOp == '-' -> (left - operand).coerceAtLeast(0L)
                else -> left + operand
            }
        }

    val displayAmount: String
        get() = amountText.ifEmpty { leftCents?.let { Money.toPlainInput(it) } ?: "" }

    val assetName: String?
        get() = assetId?.let { id -> assets.firstOrNull { it.asset.id == id }?.asset?.name }

    val toAssetName: String?
        get() = toAssetId?.let { id -> assets.firstOrNull { it.asset.id == id }?.asset?.name }

    val filteredRefundCandidates: List<RefundCandidate>
        get() {
            val q = refundQuery.trim()
            if (q.isEmpty()) return refundCandidates
            return refundCandidates.filter { c ->
                c.categoryL1.contains(q, ignoreCase = true) ||
                    c.categoryL2.contains(q, ignoreCase = true) ||
                    c.note.contains(q, ignoreCase = true) ||
                    c.date.contains(q) ||
                    Money.format(c.amountCents).contains(q)
            }
        }

    val canSave: Boolean
        get() = !saving && !loading &&
            (effectiveCents?.let { it > 0L } == true) &&
            when (entry) {
                EntryKind.TRANSFER -> assetId != null && toAssetId != null && assetId != toAssetId
                EntryKind.HUIWANG -> subtype != null && (subtype?.isRefund != true || linkedTxId != null)
                else -> l1.isNotBlank() && (l2List.isEmpty() || l2.isNotBlank() || editing)
            }
}

class AddTxViewModel : ViewModel() {

    private val repo = KumquatApp.instance.repository
    private val _state = MutableStateFlow(AddState())
    val state: StateFlow<AddState> = _state.asStateFlow()

    val balances: StateFlow<List<LedgerRepository.AssetBalance>> = repo.observeAssetBalances()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private var editingTx: Tx? = null

    fun start(txId: Long, presetDate: String) {
        viewModelScope.launch {
            val categories = repo.categories()
            val assets = repo.assets().map { LedgerRepository.AssetBalance(it, it.initialBalanceCents) }
            if (txId > 0L) {
                val tx = repo.tx(txId)
                if (tx == null) {
                    _state.value = AddState(loading = false, categories = categories, assets = assets, error = "账单不存在，可能已被删除")
                    return@launch
                }
                editingTx = tx
                val type = TxType.parse(tx.type) ?: TxType.EXPENSE
                val entry = type.toEntryKind()
                val linked = tx.linkedTxId?.let { repo.tx(it) }
                val subtype = HuiWangSubtype.parse(tx.huiwangSubtype)
                    ?: if (entry == EntryKind.HUIWANG) LegacyHuiWangResolver.resolve(tx, linked).subtype else null
                val remaining = if (type == TxType.REFUND && linked != null) {
                    RefundRules.summarize(
                        linked,
                        repo.refundsLinkedTo(linked.id),
                        excludeRefundId = tx.id
                    ).remainingCents
                } else null
                _state.value = AddState(
                    editing = true,
                    loading = false,
                    entry = entry,
                    subtype = subtype,
                    amountText = Money.toPlainInput(tx.amountCents),
                    amountLocked = subtype == HuiWangSubtype.REFUND_FULL,
                    date = tx.date,
                    note = tx.note,
                    l1 = if (type == TxType.REFUND && linked != null) linked.categoryL1 else tx.categoryL1,
                    l2 = if (type == TxType.REFUND && linked != null) linked.categoryL2 else tx.categoryL2,
                    categories = categories,
                    linkedTxId = tx.linkedTxId,
                    linkedLabel = linked?.let {
                        "${LedgerMath.catName(it.categoryL1, it.categoryL2)} · ${it.date} · ¥${Money.format(it.amountCents)}"
                    },
                    linkedDate = linked?.date,
                    linkedRemainingCents = remaining,
                    assets = assets,
                    assetId = tx.assetId,
                    toAssetId = tx.toAssetId,
                    assetTouched = true
                )
            } else {
                val date = presetDate.ifBlank { DateX.today() }
                val defaults = defaultSelection(categories, EntryKind.EXPENSE)
                val base = AddState(
                    loading = false,
                    entry = EntryKind.EXPENSE,
                    date = date,
                    categories = categories,
                    assets = assets,
                    l1 = defaults.first,
                    l2 = defaults.second
                )
                _state.value = applyDefaultAsset(base, announce = false)
            }
        }
    }

    private fun defaultSelection(categories: List<Category>, entry: EntryKind): Pair<String, String> {
        val type = entry.baseType().name
        val l1 = categories.filter { it.type == type && it.level == 1 && !it.hidden }
            .minByOrNull { it.sortOrder } ?: return "" to ""
        val l2 = categories.filter {
            it.type == type && it.level == 2 && it.parentId == l1.id && !it.hidden
        }.minByOrNull { it.sortOrder }
        return l1.name to (l2?.name ?: "")
    }

    private fun selectedCategory(s: AddState): Category? {
        if (s.entry != EntryKind.INCOME && s.entry != EntryKind.EXPENSE) return null
        val type = s.entry.baseType().name
        val parent = s.categories.firstOrNull {
            it.type == type && it.level == 1 && it.name == s.l1 && !it.hidden
        }
        return s.categories.firstOrNull {
            it.type == type && it.level == 2 && it.name == s.l2 && it.parentId == parent?.id
        } ?: parent
    }

    private fun applyDefaultAsset(s: AddState, announce: Boolean): AddState {
        if (s.assetTouched) return s
        val category = selectedCategory(s)
        val bound = category?.defaultAssetId
        var hint: String? = null
        if (bound != null) {
            if (s.assets.any { it.asset.id == bound }) return s.copy(assetId = bound, hint = null)
            hint = "分类默认账户不可用，已使用最近账户"
        }
        val recent = SessionStore.lastAssetId.value
        if (recent > 0L && s.assets.any { it.asset.id == recent }) {
            return s.copy(assetId = recent, hint = if (announce) hint else s.hint)
        }
        if (bound != null && announce) hint = "分类默认账户不可用，请手动选择账户"
        return s.copy(assetId = if (s.assets.any { it.asset.id == s.assetId }) s.assetId else null, hint = if (announce) hint else s.hint)
    }

    fun setEntry(entry: EntryKind) {
        val s = _state.value
        if (s.entry == entry) return
        val defaults = if (entry == EntryKind.INCOME || entry == EntryKind.EXPENSE) {
            defaultSelection(s.categories, entry)
        } else "" to ""
        val next = s.copy(
            entry = entry,
            subtype = if (entry == EntryKind.HUIWANG) s.subtype else null,
            l1 = defaults.first,
            l2 = defaults.second,
            linkedTxId = if (entry == EntryKind.HUIWANG && s.subtype?.isRefund == true) s.linkedTxId else null,
            linkedLabel = if (entry == EntryKind.HUIWANG && s.subtype?.isRefund == true) s.linkedLabel else null,
            linkedDate = if (entry == EntryKind.HUIWANG && s.subtype?.isRefund == true) s.linkedDate else null,
            linkedRemainingCents = if (entry == EntryKind.HUIWANG && s.subtype?.isRefund == true) s.linkedRemainingCents else null,
            amountLocked = entry == EntryKind.HUIWANG && s.subtype == HuiWangSubtype.REFUND_FULL,
            error = null,
            hint = null,
            dirty = true
        )
        _state.value = applyDefaultAsset(next, announce = true)
    }

    fun selectSubtype(subtype: HuiWangSubtype) {
        val s = _state.value
        val keepsLink = s.subtype?.isRefund == true && subtype.isRefund
        val next = s.copy(
            entry = EntryKind.HUIWANG,
            subtype = subtype,
            l1 = "",
            l2 = "",
            amountLocked = subtype == HuiWangSubtype.REFUND_FULL,
            linkedTxId = if (keepsLink) s.linkedTxId else null,
            linkedLabel = if (keepsLink) s.linkedLabel else null,
            linkedDate = if (keepsLink) s.linkedDate else null,
            linkedRemainingCents = if (keepsLink) s.linkedRemainingCents else null,
            showRefundPicker = subtype.isRefund,
            error = null,
            hint = null,
            dirty = true
        )
        _state.value = next
        if (subtype.isRefund) {
            showRefundCandidates()
        } else {
            _state.value = applyDefaultAsset(next, announce = true)
        }
    }

    fun selectL1(name: String) {
        val s = _state.value
        val parent = s.categories.firstOrNull {
            it.type == s.entry.baseType().name && it.level == 1 && it.name == name
        }
        val firstChild = parent?.let { p ->
            s.categories.filter {
                it.type == s.entry.baseType().name && it.level == 2 && it.parentId == p.id && !it.hidden
            }.minByOrNull { it.sortOrder }?.name
        } ?: ""
        val next = s.copy(l1 = name, l2 = firstChild, error = null, hint = null, dirty = true)
        _state.value = applyDefaultAsset(next, announce = true)
    }

    fun selectL2(name: String) {
        val next = _state.value.copy(l2 = name, error = null, hint = null, dirty = true)
        _state.value = applyDefaultAsset(next, announce = true)
    }

    fun onDigit(key: String) {
        val s = _state.value
        if (s.amountLocked) return
        val text = s.amountText
        when (key) {
            "." -> {
                if (text.contains('.')) return
                val next = if (text.isEmpty()) "0." else "$text."
                _state.value = s.copy(amountText = next, error = null, dirty = true)
            }
            else -> {
                val dot = text.indexOf('.')
                if (dot >= 0 && text.length - dot > 2) return
                if (dot < 0 && text.replace(".", "").length >= 9) return
                val next = if (text == "0") key else text + key
                _state.value = s.copy(amountText = next, error = null, dirty = true)
            }
        }
    }

    fun onOperator(op: String) {
        val s = _state.value
        if (s.amountLocked) return
        val total = s.effectiveCents ?: return
        val symbol = if (op == "-") '-' else '+'
        _state.value = s.copy(
            leftCents = total,
            pendingOp = symbol,
            amountText = "",
            expression = "${Money.format(total)} $symbol",
            error = null,
            dirty = true
        )
    }

    fun backspace() {
        val s = _state.value
        if (s.amountLocked) return
        if (s.amountText.isNotEmpty()) {
            _state.value = s.copy(amountText = s.amountText.dropLast(1), error = null, dirty = true)
            return
        }
        val left = s.leftCents ?: return
        _state.value = s.copy(
            amountText = Money.toPlainInput(left),
            leftCents = null,
            pendingOp = null,
            expression = "",
            error = null,
            dirty = true
        )
    }

    fun setKeypadVisible(visible: Boolean) {
        _state.value = _state.value.copy(keypadVisible = visible)
    }

    fun setDate(date: String) {
        _state.value = _state.value.copy(date = date, error = null, dirty = true)
    }

    fun setNote(note: String) {
        _state.value = _state.value.copy(note = note, dirty = true)
    }

    fun openAssetPicker(target: Int) {
        _state.value = _state.value.copy(assetPickerTarget = target)
    }

    fun closeAssetPicker() {
        _state.value = _state.value.copy(assetPickerTarget = null)
    }

    fun syncAssets(list: List<LedgerRepository.AssetBalance>) {
        val s = _state.value
        if (s.assets == list) return
        // 首次订阅时 Flow 会给一个空列表；此时不能用空列表清空已回填的账户。
        if (list.isEmpty()) {
            _state.value = s.copy(assets = list)
            return
        }
        var next = s.copy(assets = list)
        if (next.assetId != null && list.none { it.asset.id == next.assetId }) {
            next = next.copy(assetId = null, hint = "原账户已归档，请重新选择")
        }
        if (next.toAssetId != null && list.none { it.asset.id == next.toAssetId }) {
            next = next.copy(toAssetId = null)
        }
        if (!next.assetTouched && next.assetId == null) next = applyDefaultAsset(next, announce = false)
        _state.value = next
    }

    fun pickAssetDirect(target: Int, assetId: Long?) {
        val s = _state.value
        _state.value = if (target == 0) {
            s.copy(assetId = assetId, assetTouched = true, hint = null)
        } else {
            s.copy(toAssetId = assetId)
        }
        if (target == 0) SessionStore.rememberAsset(assetId)
    }

    fun pickAsset(assetId: Long?) {
        val s = _state.value
        val target = s.assetPickerTarget ?: return
        _state.value = if (target == 0) {
            s.copy(assetId = assetId, assetTouched = true, assetPickerTarget = null, hint = null)
        } else {
            s.copy(toAssetId = assetId, assetPickerTarget = null)
        }
        if (target == 0) SessionStore.rememberAsset(assetId)
    }

    // ---------------- 退款 / 收回还款关联 ----------------

    fun showRefundCandidates() {
        val s = _state.value
        viewModelScope.launch {
            val today = LocalDate.now()
            val list = repo.refundCandidates(
                accountId = SessionStore.accountId.value,
                excludeId = editingTx?.id ?: 0L,
                start = today.minusYears(10).toString(),
                end = today.toString()
            )
            _state.value = s.copy(refundCandidates = list, showRefundPicker = true)
        }
    }

    fun setRefundQuery(query: String) {
        _state.value = _state.value.copy(refundQuery = query)
    }

    fun hideRefundCandidates() {
        _state.value = _state.value.copy(showRefundPicker = false)
    }

    fun pickRefund(candidate: RefundCandidate) {
        val s = _state.value
        val nextAmount = Money.toPlainInput(candidate.remainingCents)
        val nextDate = if (s.date < candidate.date) {
            if (DateX.today() < candidate.date) candidate.date else DateX.today()
        } else s.date
        val next = s.copy(
            linkedTxId = candidate.id,
            linkedLabel = "${LedgerMath.catName(candidate.categoryL1, candidate.categoryL2)} · ${candidate.date} · " +
                "原 ¥${Money.format(candidate.amountCents)}，已退 ¥${Money.format(candidate.refundedCents)}",
            linkedDate = candidate.date,
            linkedRemainingCents = candidate.remainingCents,
            l1 = candidate.categoryL1,
            l2 = candidate.categoryL2,
            amountText = nextAmount,
            amountLocked = s.subtype == HuiWangSubtype.REFUND_FULL,
            leftCents = null,
            pendingOp = null,
            expression = "",
            date = nextDate,
            showRefundPicker = false,
            assetId = if (s.assetTouched) s.assetId else (candidate.assetId ?: s.assetId),
            hint = null,
            error = null,
            dirty = true
        )
        _state.value = next
    }

    fun clearRefundLink() {
        val s = _state.value
        _state.value = s.copy(
            linkedTxId = null,
            linkedLabel = null,
            linkedDate = null,
            linkedRemainingCents = null,
            l1 = "",
            l2 = "",
            dirty = true
        )
    }

    fun showExchangeCandidates() {
        val s = _state.value
        viewModelScope.launch {
            val list = repo.exchangeOrigins(SessionStore.accountId.value)
            _state.value = s.copy(exchangeCandidates = list, showExchangePicker = true)
        }
    }

    fun hideExchangeCandidates() {
        _state.value = _state.value.copy(showExchangePicker = false)
    }

    fun pickExchange(origin: Tx) {
        val s = _state.value
        _state.value = s.copy(
            linkedTxId = origin.id,
            linkedLabel = "${origin.categoryL1} · ${origin.date} · ¥${Money.format(origin.amountCents)}",
            linkedDate = origin.date,
            linkedRemainingCents = null,
            showExchangePicker = false,
            dirty = true
        )
    }

    // ---------------- 快捷新建分类 ----------------

    fun openQuickCreate(level: Int, parentId: Long?) {
        val s = _state.value
        val parentLabel = if (level == 1) {
            "入账/出账根级 · 新建一级分类"
        } else {
            val parent = s.categories.firstOrNull { it.id == parentId }
            "在「${parent?.name ?: s.l1}」下新建二级分类"
        }
        _state.value = s.copy(editor = QuickCategoryEditor(level, parentId, parentLabel))
    }

    fun closeQuickCreate() {
        _state.value = _state.value.copy(editor = null)
    }

    fun saveQuickCategory(name: String, icon: String, defaultAssetId: Long?) {
        val s = _state.value
        val editor = s.editor ?: return
        viewModelScope.launch {
            runCatching {
                repo.addCategory(
                    type = s.entry.baseType(),
                    level = editor.level,
                    parentId = editor.parentId,
                    name = name,
                    icon = icon,
                    defaultAssetId = defaultAssetId
                )
            }.onSuccess { id ->
                val categories = repo.categories()
                val created = categories.firstOrNull { it.id == id }
                var next = s.copy(
                    categories = categories,
                    editor = null,
                    l1 = if (editor.level == 1) created?.name ?: s.l1 else s.l1,
                    l2 = if (editor.level == 2) created?.name ?: s.l2 else s.l2,
                    assetTouched = if (defaultAssetId != null && !s.assetTouched) false else s.assetTouched,
                    dirty = true
                )
                if (editor.level == 1 && created != null) {
                    next = next.copy(
                        l1 = created.name,
                        l2 = categories.filter {
                            it.type == s.entry.baseType().name && it.level == 2 && it.parentId == created.id && !it.hidden
                        }.minByOrNull { it.sortOrder }?.name ?: ""
                    )
                }
                _state.value = applyDefaultAsset(next, announce = true)
            }.onFailure { e ->
                _state.value = s.copy(editor = editor.copy(error = e.message ?: "新建分类失败"))
            }
        }
    }

    // ---------------- 保存 / 删除 ----------------

    private fun validationError(s: AddState, cents: Long): String? = when (s.entry) {
        EntryKind.TRANSFER -> transferError(s)
        EntryKind.HUIWANG -> huiWangError(s, cents)
        else -> categoryError(s)
    }

    private fun transferError(s: AddState): String? = when {
        s.assetId == null || s.toAssetId == null -> "内部转账必须选择转出和转入账户"
        s.assetId == s.toAssetId -> "转出与转入账户不能相同"
        else -> null
    }

    private fun huiWangError(s: AddState, cents: Long): String? {
        val subtype = s.subtype ?: return "请选择回往方向"
        if (!subtype.isRefund) return null
        if (s.linkedTxId == null) return "退款必须关联原出账账单"
        val linkedDate = s.linkedDate
        if (linkedDate != null && s.date < linkedDate) {
            return "退款日期不能早于原账单日期（$linkedDate）"
        }
        val remaining = s.linkedRemainingCents ?: 0L
        if (cents > remaining) return "最多可退 ¥${Money.format(remaining)}"
        if (subtype == HuiWangSubtype.REFUND_FULL && cents != remaining) {
            return "全额退款金额必须等于剩余可退 ¥${Money.format(remaining)}"
        }
        return null
    }

    private fun categoryError(s: AddState): String? = when {
        s.l1.isBlank() -> "请选择分类"
        s.l2List.isNotEmpty() && s.l2.isBlank() && !s.editing -> "请选择二级分类"
        else -> null
    }

    fun save() {
        val s = _state.value
        if (s.saving) return
        val cents = s.effectiveCents
        if (cents == null || cents <= 0L) {
            _state.value = s.copy(error = "请输入大于 0 的金额")
            return
        }
        validationError(s, cents)?.let { message ->
            _state.value = s.copy(error = message)
            return
        }
        if (DateX.parse(s.date) == null) {
            _state.value = s.copy(error = "日期格式不正确")
            return
        }
        val accountId = SessionStore.accountId.value
        if (accountId <= 0L) {
            _state.value = s.copy(error = "账本未初始化，请重启应用")
            return
        }

        val subtype = s.subtype
        val type = when (s.entry) {
            EntryKind.HUIWANG -> if (subtype?.isRefund == true) TxType.REFUND else TxType.EXCHANGE
            else -> s.entry.baseType()
        }
        val categoryL1 = when (s.entry) {
            EntryKind.TRANSFER -> "内部转账"
            EntryKind.HUIWANG -> if (type == TxType.REFUND) {
                s.l1.ifBlank { subtype?.label ?: "退款" }
            } else subtype?.label ?: "回往"
            else -> s.l1
        }
        val categoryL2 = when (s.entry) {
            EntryKind.HUIWANG, EntryKind.TRANSFER -> ""
            else -> s.l2
        }
        val linkedTxId = if (s.entry == EntryKind.HUIWANG) s.linkedTxId else null

        _state.value = s.copy(saving = true, error = null)
        viewModelScope.launch {
            runCatching {
                val tx = editingTx
                if (tx == null) {
                    repo.insertTx(
                        accountId = accountId,
                        type = type,
                        categoryL1 = categoryL1,
                        categoryL2 = categoryL2,
                        amountCents = cents,
                        date = s.date,
                        note = s.note,
                        linkedTxId = linkedTxId,
                        assetId = s.assetId,
                        toAssetId = if (s.entry == EntryKind.TRANSFER) s.toAssetId else null,
                        huiwangSubtype = subtype?.name
                    )
                } else {
                    repo.updateTx(
                        tx.copy(
                            type = type.name,
                            categoryL1 = categoryL1,
                            categoryL2 = categoryL2,
                            amountCents = cents,
                            date = s.date,
                            note = s.note.trim(),
                            linkedTxId = linkedTxId,
                            assetId = s.assetId,
                            toAssetId = if (s.entry == EntryKind.TRANSFER) s.toAssetId else null,
                            huiwangSubtype = subtype?.name
                        )
                    )
                }
            }.onSuccess {
                SessionStore.rememberAsset(s.assetId)
                _state.value = _state.value.copy(saving = false, saved = true)
            }.onFailure { e ->
                _state.value = _state.value.copy(
                    saving = false,
                    error = e.message ?: "保存失败，请重试"
                )
            }
        }
    }

    fun delete() {
        val tx = editingTx ?: return
        viewModelScope.launch {
            runCatching { repo.deleteTx(tx) }
                .onSuccess { _state.value = _state.value.copy(deleted = true) }
                .onFailure { e ->
                    _state.value = _state.value.copy(error = e.message ?: "删除失败")
                }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AddTxScreen(
    txId: Long,
    presetDate: String,
    onDone: () -> Unit,
    onBack: () -> Unit,
    viewModel: AddTxViewModel = viewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val balances by viewModel.balances.collectAsStateWithLifecycle()
    val focusManager = LocalFocusManager.current
    var datePickerOpen by remember { mutableStateOf(false) }
    var deleteConfirm by remember { mutableStateOf(false) }
    var discardConfirm by remember { mutableStateOf(false) }

    LaunchedEffect(txId, presetDate) { viewModel.start(txId, presetDate) }
    LaunchedEffect(state.saved, state.deleted) {
        if (state.saved || state.deleted) onDone()
    }
    LaunchedEffect(balances) { viewModel.syncAssets(balances) }

    val requestBack: () -> Unit = {
        if (state.dirty && !state.saved && !state.deleted) discardConfirm = true else onBack()
    }
    BackHandler(enabled = state.dirty && !state.saving) { discardConfirm = true }

    Column(Modifier.fillMaxSize().imePadding()) {
        KmTopBar(
            title = if (state.editing) "编辑账单" else "记一笔",
            subtitle = when {
                state.editing -> "改动会立即影响统计"
                state.entry == EntryKind.TRANSFER -> "内部转账不计入收支"
                state.entry == EntryKind.HUIWANG -> "回往按净额计入收支，退款只冲减原支出"
                else -> null
            },
            onBack = requestBack,
            trailing = {
                if (state.editing) {
                    GlassIconButton(Icons.Rounded.Delete, "删除", { deleteConfirm = true }, tint = Ink)
                }
            }
        )

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            EntryTypeSelector(selected = state.entry, onSelect = viewModel::setEntry)
            Spacer(Modifier.height(10.dp))

            AmountReadOnlyField(
                amountText = state.displayAmount,
                subLabel = buildString {
                    append("AMOUNT")
                    state.subtype?.let { append(" · "); append(it.label) }
                    if (state.amountLocked) append(" · 自动")
                },
                onClick = {
                    if (!state.amountLocked) {
                        focusManager.clearFocus()
                        viewModel.setKeypadVisible(true)
                    }
                }
            )
            Spacer(Modifier.height(2.dp))
            Row(Modifier.fillMaxWidth().height(16.dp), verticalAlignment = Alignment.CenterVertically) {
                if (state.expression.isNotBlank()) {
                    Text(
                        "${state.expression}  ${state.amountText}",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Ink,
                        modifier = Modifier.weight(1f)
                    )
                } else {
                    Spacer(Modifier.weight(1f))
                }
                Text(
                    when (state.entry) {
                        EntryKind.HUIWANG -> if (state.subtype?.isRefund == true) {
                            "退款金额不超过剩余可退"
                        } else {
                            "代付/借出记支出，收回/还款记收入"
                        }
                        EntryKind.TRANSFER -> "转账只在账户间搬钱，不计收支"
                        else -> "点击金额唤起键盘"
                    },
                    fontSize = 10.sp,
                    color = InkMuted
                )
            }

            Spacer(Modifier.height(6.dp))
            NoteField(
                value = state.note,
                onValueChange = viewModel::setNote,
                modifier = Modifier.onFocusChanged { focus ->
                    viewModel.setKeypadVisible(!focus.isFocused)
                }
            )

            when (state.entry) {
                EntryKind.TRANSFER -> {
                    Spacer(Modifier.height(10.dp))
                    SectionHeader("TRANSFER", "转出 / 转入账户")
                    AssetPickRow("转出账户", state.assetName, onClick = { viewModel.openAssetPicker(0) })
                    Spacer(Modifier.height(6.dp))
                    AssetPickRow("转入账户", state.toAssetName, onClick = { viewModel.openAssetPicker(1) })
                }

                EntryKind.HUIWANG -> {
                    Spacer(Modifier.height(10.dp))
                    HuiWangSection(
                        state = state,
                        onSubtype = viewModel::selectSubtype,
                        onRefund = viewModel::showRefundCandidates,
                        onClearLink = viewModel::clearRefundLink,
                        onExchange = viewModel::showExchangeCandidates,
                        onAsset = { viewModel.openAssetPicker(0) },
                        onClearAsset = { viewModel.pickAssetDirect(0, null) }
                    )
                }

                else -> {
                    Spacer(Modifier.height(10.dp))
                    SectionHeader("CATEGORY", "分类")
                    FlowRow(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        state.l1List.forEach { l1 ->
                            GhostPill(
                                text = l1.name,
                                selected = state.l1 == l1.name,
                                selectedColors = state.entry.semanticGradient(),
                                onClick = { viewModel.selectL1(l1.name) }
                            )
                        }
                        AddCategorySquare("新建一级分类") {
                            viewModel.openQuickCreate(1, null)
                        }
                    }

                    if (state.selectedL1 != null) {
                        Spacer(Modifier.height(8.dp))
                        FlowRow(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            state.l2List.forEach { l2 ->
                                val selected = state.l2 == l2.name
                                Column(
                                    Modifier
                                        .width(78.dp)
                                        .clip(CardShape)
                                        .background(
                                            if (selected) Brush.linearGradient(
                                                listOf(
                                                    state.entry.semanticColor().copy(alpha = 0.45f),
                                                    state.entry.semanticColor().copy(alpha = 0.22f)
                                                )
                                            ) else Brush.linearGradient(
                                                listOf(Color.White.copy(alpha = 0.32f), Color.White.copy(alpha = 0.18f))
                                            )
                                        )
                                        .border(
                                            1.dp,
                                            if (selected) Brush.verticalGradient(
                                                listOf(Color.White, Color.White.copy(alpha = 0.35f))
                                            ) else Brush.verticalGradient(listOf(GlassBorderLight, GlassBorderDark)),
                                            CardShape
                                        )
                                        .clickable { viewModel.selectL2(l2.name) }
                                        .padding(vertical = 8.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    CategoryIconBadge(l2.icon, size = 30)
                                    Text(
                                        l2.name,
                                        fontSize = 11.sp,
                                        fontWeight = if (selected) FontWeight.Black else FontWeight.Medium,
                                        color = Ink
                                    )
                                }
                            }
                            AddCategorySquare("新建二级分类") {
                                val parent = state.selectedL1
                                if (parent != null) viewModel.openQuickCreate(2, parent.id)
                            }
                        }
                    }

                    Spacer(Modifier.height(10.dp))
                    AssetPickRow(
                        label = "资产账户（可选）",
                        value = state.assetName,
                        onClick = { viewModel.openAssetPicker(0) },
                        onClear = if (state.assetId != null) {
                            { viewModel.pickAssetDirect(0, null) }
                        } else null
                    )
                }
            }

            Spacer(Modifier.height(10.dp))
            DateRow(date = state.date, onClick = { datePickerOpen = true })

            state.hint?.let {
                Spacer(Modifier.height(6.dp))
                Text(it, fontSize = 11.sp, color = InkMuted)
            }
            state.error?.let {
                Spacer(Modifier.height(8.dp))
                Text(
                    it,
                    color = Color(0xFFD50000),
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
                )
            }
            Spacer(Modifier.height(16.dp))
        }

        Column(
            Modifier
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            SaveBar(
                enabled = state.canSave,
                saving = state.saving,
                onClick = viewModel::save
            )
            if (state.keypadVisible) {
                Spacer(Modifier.height(6.dp))
                CalcKeypad(
                    onDigit = viewModel::onDigit,
                    onBackspace = viewModel::backspace,
                    onOperator = viewModel::onOperator
                )
            }
        }
    }

    if (datePickerOpen) {
        val millis = DateX.parse(state.date)?.toEpochDay()?.times(86_400_000L)
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = millis)
        DatePickerDialog(
            onDismissRequest = { datePickerOpen = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let {
                        viewModel.setDate(LocalDate.ofEpochDay(it / 86_400_000L).toString())
                    }
                    datePickerOpen = false
                }) { Text("确定", fontWeight = FontWeight.Black, color = Ink) }
            },
            dismissButton = {
                TextButton(onClick = { datePickerOpen = false }) { Text("取消", color = InkMuted) }
            }
        ) { DatePicker(state = pickerState) }
    }

    state.editor?.let { editor ->
        CategoryEditorDialog(
            title = if (editor.level == 1) "新建一级分类" else "新建二级分类",
            parentLabel = editor.parentLabel,
            initialName = "",
            initialIcon = "category",
            initialDefaultAssetId = null,
            assets = state.assets,
            error = editor.error,
            onDismiss = viewModel::closeQuickCreate,
            onSave = viewModel::saveQuickCategory
        )
    }

    state.assetPickerTarget?.let { target ->
        val title = when {
            target == 1 -> "选择转入账户"
            state.entry == EntryKind.HUIWANG && state.subtype?.isRefund == true -> "选择退款账户"
            else -> "选择账户"
        }
        AssetPickerDialog(
            title = title,
            assets = balances,
            allowClear = true,
            onPick = viewModel::pickAsset,
            onDismiss = viewModel::closeAssetPicker
        )
    }

    if (state.showRefundPicker) {
        RefundPickerDialog(
            candidates = state.filteredRefundCandidates,
            query = state.refundQuery,
            onQuery = viewModel::setRefundQuery,
            onPick = viewModel::pickRefund,
            onDismiss = viewModel::hideRefundCandidates
        )
    }

    if (state.showExchangePicker) {
        ExchangePickerDialog(
            candidates = state.exchangeCandidates,
            onPick = viewModel::pickExchange,
            onDismiss = viewModel::hideExchangeCandidates
        )
    }

    if (deleteConfirm) {
        ConfirmDialog(
            title = "删除这笔账单？",
            message = "删除后不可恢复；若这是有退款的原账单，需要先处理退款。",
            confirmText = "删除",
            destructive = true,
            onConfirm = viewModel::delete,
            onDismiss = { deleteConfirm = false }
        )
    }

    if (discardConfirm) {
        ConfirmDialog(
            title = "放弃本次记账？",
            message = "已填写的内容不会保存。",
            confirmText = "放弃",
            destructive = true,
            onConfirm = onBack,
            onDismiss = { discardConfirm = false }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HuiWangSection(
    state: AddState,
    onSubtype: (HuiWangSubtype) -> Unit,
    onRefund: () -> Unit,
    onClearLink: () -> Unit,
    onExchange: () -> Unit,
    onAsset: () -> Unit,
    onClearAsset: () -> Unit
) {
    SectionHeader("HUIWANG", "回往方向")
    FlowRow(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        maxItemsInEachRow = 3
    ) {
        HuiWangSubtype.uiOrder.forEach { subtype ->
            val selected = state.subtype == subtype
            Column(
                Modifier
                    .width(104.dp)
                    .clip(CardShape)
                    .background(
                        if (selected) Brush.linearGradient(
                            listOf(subtype.semanticColor().copy(alpha = 0.45f), Color.White.copy(alpha = 0.35f))
                        ) else Brush.linearGradient(
                            listOf(Color.White.copy(alpha = 0.32f), Color.White.copy(alpha = 0.18f))
                        )
                    )
                    .border(
                        1.dp,
                        if (selected) Brush.verticalGradient(listOf(Color.White, Color.White.copy(alpha = 0.35f)))
                        else Brush.verticalGradient(listOf(GlassBorderLight, GlassBorderDark)),
                        CardShape
                    )
                    .clickable { onSubtype(subtype) }
                    .padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Icon(huiWangIcon(subtype), contentDescription = subtype.label, tint = Ink, modifier = Modifier.size(22.dp))
                Text(
                    subtype.label,
                    fontSize = 12.sp,
                    fontWeight = if (selected) FontWeight.Black else FontWeight.Bold,
                    color = Ink
                )
                Text(subtype.hint(), fontSize = 9.sp, color = InkMuted, maxLines = 1)
            }
        }
    }

    val subtype = state.subtype
    if (subtype != null && subtype.isRefund) {
        Spacer(Modifier.height(10.dp))
        LinkCard(
            title = "关联原出账（必填）",
            value = state.linkedLabel ?: "点击选择要退款的原账单",
            valueColor = if (state.linkedTxId == null) InkMuted else Ink,
            onClear = if (state.linkedTxId != null) onClearLink else null,
            onClick = onRefund
        )
        if (state.linkedRemainingCents != null) {
            Text(
                "剩余可退 ¥${Money.format(state.linkedRemainingCents)}" +
                    if (subtype == HuiWangSubtype.REFUND_FULL) " · 已自动填入" else "",
                fontSize = 11.sp,
                color = InkMuted,
                modifier = Modifier.padding(start = 4.dp, top = 4.dp)
            )
        }
    }

    if (subtype != null && !subtype.isRefund &&
        (subtype == HuiWangSubtype.COLLECT || subtype == HuiWangSubtype.REPAY)
    ) {
        Spacer(Modifier.height(10.dp))
        LinkCard(
            title = "关联原代付/借出（可选）",
            value = state.linkedLabel ?: "用于追溯，不强制关联",
            valueColor = if (state.linkedTxId == null) InkMuted else Ink,
            onClear = if (state.linkedTxId != null) onClearLink else null,
            onClick = onExchange
        )
    }

    Spacer(Modifier.height(10.dp))
    AssetPickRow(
        label = if (subtype?.isRefund == true) "退款账户" else "资产账户（可选）",
        value = state.assetName,
        onClick = onAsset,
        onClear = if (state.assetId != null) onClearAsset else null
    )
}

@Composable
private fun LinkCard(
    title: String,
    value: String,
    valueColor: Color,
    onClear: (() -> Unit)?,
    onClick: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(CardShape)
            .background(Color.White.copy(alpha = 0.28f))
            .border(1.dp, Brush.verticalGradient(listOf(GlassBorderLight, GlassBorderDark)), CardShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Rounded.Link, contentDescription = null, tint = Ink, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Ink)
            Text(value, fontSize = 11.sp, color = valueColor, maxLines = 2)
        }
        if (onClear != null) {
            GlassIconButton(Icons.Rounded.Close, "取消关联", onClear, size = 28)
        }
    }
}

@Composable
private fun AddCategorySquare(description: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(58.dp, 44.dp)
            .clip(CardShape)
            .background(Color.White.copy(alpha = 0.18f))
            .border(1.dp, Brush.verticalGradient(listOf(GlassBorderLight, GlassBorderDark)), CardShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(Icons.Rounded.Add, contentDescription = description, tint = InkMuted, modifier = Modifier.size(20.dp))
    }
}

/** 资产选择行（转账/回往共用）。 */
@Composable
private fun AssetPickRow(
    label: String,
    value: String?,
    onClick: () -> Unit,
    onClear: (() -> Unit)? = null
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(CardShape)
            .background(Color.White.copy(alpha = 0.28f))
            .border(1.dp, Brush.verticalGradient(listOf(GlassBorderLight, GlassBorderDark)), CardShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Rounded.AccountBalanceWallet, contentDescription = null, tint = Ink, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(8.dp))
        Text(label, fontSize = 12.sp, color = InkMuted)
        Spacer(Modifier.weight(1f))
        Text(
            value ?: "未选择",
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = if (value == null) InkMuted else Ink
        )
        if (onClear != null) {
            Spacer(Modifier.width(6.dp))
            GlassIconButton(Icons.Rounded.Close, "清除账户", onClear, size = 24)
        }
    }
}

@Composable
private fun AssetPickerDialog(
    title: String,
    assets: List<LedgerRepository.AssetBalance>,
    allowClear: Boolean,
    onPick: (Long?) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color.White.copy(alpha = 0.98f),
        shape = CardShape,
        title = { Text(title, fontWeight = FontWeight.Black) },
        text = {
            if (assets.isEmpty()) {
                Text("还没有资产账户，先去「我的 → 资产管理」添加。", color = InkMuted)
            } else {
                Column {
                    LazyColumn(Modifier.heightIn(max = 320.dp)) {
                        items(assets.size) { index ->
                            val item = assets[index]
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .clickable { onPick(item.asset.id) }
                                    .padding(vertical = 8.dp, horizontal = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    Modifier
                                        .size(30.dp)
                                        .clip(CircleShape)
                                        .background(Color.White.copy(alpha = 0.45f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Rounded.AccountBalanceWallet,
                                        contentDescription = null,
                                        tint = Ink,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    item.asset.name,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Ink,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    "¥${Money.format(item.balanceCents)}",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Black,
                                    color = Ink
                                )
                            }
                        }
                    }
                    if (allowClear) {
                        TextButton(onClick = { onPick(null) }) {
                            Text("不关联账户", color = InkMuted, fontSize = 12.sp)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("关闭", color = Ink) }
        }
    )
}

@Composable
private fun RefundPickerDialog(
    candidates: List<RefundCandidate>,
    query: String,
    onQuery: (String) -> Unit,
    onPick: (RefundCandidate) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color.White.copy(alpha = 0.98f),
        shape = CardShape,
        title = { Text("选择要退款的原出账", fontWeight = FontWeight.Black) },
        text = {
            Column {
                OutlinedTextField(
                    value = query,
                    onValueChange = onQuery,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("按备注 / 分类 / 金额 / 日期搜索", fontSize = 12.sp, color = InkMuted) },
                    singleLine = true,
                    shape = CardShape,
                    keyboardOptions = KeyboardOptions.Default,
                    colors = OutlinedTextFieldDefaults.colors(cursorColor = Ink)
                )
                Spacer(Modifier.height(6.dp))
                if (candidates.isEmpty()) {
                    Text(
                        if (query.isBlank()) {
                            "没有可退款的原支出\n请先在“出账”中补录原账单，再回来关联退款。"
                        } else {
                            "没有匹配的账单"
                        },
                        color = InkMuted,
                        fontSize = 12.sp
                    )
                } else {
                    LazyColumn(Modifier.heightIn(max = 380.dp)) {
                        items(candidates.size) { index ->
                            val c = candidates[index]
                            Column(
                                Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .clickable { onPick(c) }
                                    .padding(vertical = 8.dp, horizontal = 6.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        c.date,
                                        fontSize = 11.sp,
                                        color = InkMuted,
                                        modifier = Modifier.width(78.dp)
                                    )
                                    Text(
                                        LedgerMath.catName(c.categoryL1, c.categoryL2),
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Ink,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Text(
                                        "¥${Money.format(c.amountCents)}",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Black,
                                        color = Ink
                                    )
                                }
                                Text(
                                    "${if (c.note.isBlank()) "无备注" else c.note} · 已退 ¥${Money.format(c.refundedCents)} · " +
                                        "可退 ¥${Money.format(c.remainingCents)}",
                                    fontSize = 10.sp,
                                    color = InkMuted
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("关闭", color = Ink) }
        }
    )
}

@Composable
private fun ExchangePickerDialog(
    candidates: List<Tx>,
    onPick: (Tx) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color.White.copy(alpha = 0.98f),
        shape = CardShape,
        title = { Text("关联原代付 / 借出（可选）", fontWeight = FontWeight.Black) },
        text = {
            if (candidates.isEmpty()) {
                Text("没有未结清的代付/借出记录", color = InkMuted, fontSize = 12.sp)
            } else {
                LazyColumn(Modifier.heightIn(max = 340.dp)) {
                    items(candidates.size) { index ->
                        val tx = candidates[index]
                        val resolved = LegacyHuiWangResolver.resolve(tx, null)
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .clickable { onPick(tx) }
                                .padding(vertical = 8.dp, horizontal = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TypeDot(TxType.EXCHANGE, 7.dp)
                            Spacer(Modifier.width(8.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    resolved.subtype?.label ?: tx.categoryL1,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Ink
                                )
                                Text(
                                    "${tx.date} · ${tx.note.ifBlank { "无备注" }}",
                                    fontSize = 11.sp,
                                    color = InkMuted
                                )
                            }
                            Text("¥${Money.format(tx.amountCents)}", fontSize = 12.sp, fontWeight = FontWeight.Black, color = Ink)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("关闭", color = Ink) } }
    )
}
