package com.kumquat.ledger.data

import androidx.room.withTransaction
import com.kumquat.ledger.domain.AssetLedger
import com.kumquat.ledger.domain.DateRange
import com.kumquat.ledger.domain.HuiWangSubtype
import com.kumquat.ledger.domain.LedgerMath
import com.kumquat.ledger.domain.PeriodStats
import com.kumquat.ledger.domain.RefundRules
import com.kumquat.ledger.domain.StatsEngine
import com.kumquat.ledger.domain.TxType
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

/**
 * 唯一数据入口：所有写操作走事务。
 * v1.1.1 起统计统一交给 [StatsEngine]，资产余额统一交给 [AssetLedger]，
 * 仓库层只负责取数、校验和事务，不再另写一套口径。
 */
class LedgerRepository(private val db: AppDatabase) {

    private val accountDao = db.accountDao()
    private val txDao = db.txDao()
    private val categoryDao = db.categoryDao()
    private val assetDao = db.assetDao()

    /** 资产 + 实时余额（余额 = 初始余额 + 流水增量，不落库）。 */
    data class AssetBalance(val asset: Asset, val balanceCents: Long)

    // ---------------- 初始化 ----------------

    /** 初始化：内置分类 + 默认账本。返回默认账本 id。 */
    suspend fun ensureSeeded(): Long {
        CategorySeed.seed(categoryDao)
        // v1.0 -> v1.1 老库分类非空，seed 会跳过；这里补「内部转账」分类，保证转账可记
        CategorySeed.ensureTransferSeeded(categoryDao)
        // v1.1.1：入账/出账各补一个系统兜底分类，删除分类时账单移入这里
        CategorySeed.ensureUncategorizedSeeded(categoryDao)
        if (accountDao.count() == 0) {
            val now = System.currentTimeMillis()
            accountDao.insert(
                Account(
                    name = "日常",
                    coverType = "color",
                    coverValue = "#FFD1E8",
                    createdAt = now,
                    updatedAt = now
                )
            )
        }
        return accountDao.getAll().first().id
    }

    // ---------------- 账本 ----------------

    fun observeAccounts(): Flow<List<Account>> = accountDao.observeAll()

    suspend fun accounts(): List<Account> = accountDao.getAll()

    suspend fun account(id: Long): Account? = accountDao.byId(id)

    suspend fun createAccount(name: String, coverType: String, coverValue: String): Long {
        val now = System.currentTimeMillis()
        return accountDao.insert(
            Account(name = name.trim(), coverType = coverType, coverValue = coverValue, createdAt = now, updatedAt = now)
        )
    }

    suspend fun updateAccount(account: Account, name: String, coverType: String, coverValue: String) {
        accountDao.update(
            account.copy(name = name.trim(), coverType = coverType, coverValue = coverValue, updatedAt = System.currentTimeMillis())
        )
    }

    /**
     * 删除账本：连同该账本下的账单一起删除（事务）。返回被删掉的账单条数。
     * 「当前账本不能删」由界面保证，这里只兜底"至少保留一个账本"。
     */
    suspend fun deleteAccount(accountId: Long): Int = db.withTransaction {
        if (accountDao.count() <= 1) error("至少保留一个账本")
        val count = txDao.countByAccount(accountId)
        txDao.deleteByAccount(accountId)
        accountDao.deleteById(accountId)
        count
    }

    suspend fun txCountOfAccount(accountId: Long): Int = txDao.countByAccount(accountId)

    /** 合并账本：明细整体迁到 [targetId]，其余账本删除。事务保证原子。 */
    suspend fun mergeAccounts(targetId: Long, sourceIds: List<Long>): Int = db.withTransaction {
        val sources = sourceIds.filter { it != targetId }
        if (sources.isEmpty()) return@withTransaction 0
        val before = sources.sumOf { txDao.countByAccount(it) }
        txDao.moveAccounts(targetId, sources, System.currentTimeMillis())
        sources.forEach { accountDao.deleteById(it) }
        accountDao.update(
            (accountDao.byId(targetId) ?: return@withTransaction 0)
                .copy(updatedAt = System.currentTimeMillis())
        )
        before
    }

    /** 按日期区间拆分：把 [sourceId] 中落入区间的明细迁到新账本。返回迁移条数。 */
    suspend fun splitByDate(
        sourceId: Long,
        newName: String,
        coverType: String,
        coverValue: String,
        start: String,
        end: String
    ): Int = db.withTransaction {
        val count = txDao.countRange(sourceId, start, end)
        check(count > 0) { "该时间范围内没有账单，无法拆分" }
        val newId = createAccount(newName, coverType, coverValue)
        txDao.moveByDate(newId, sourceId, start, end, System.currentTimeMillis())
        count
    }

    /** 按分类拆分：把 [sourceId] 中属于该分类的明细迁到新账本。返回迁移条数。 */
    suspend fun splitByCategory(
        sourceId: Long,
        newName: String,
        coverType: String,
        coverValue: String,
        l1: String,
        l2: String
    ): Int = db.withTransaction {
        val count = txDao.countCategory(sourceId, l1, l2)
        check(count > 0) { "该分类下没有账单，无法拆分" }
        val newId = createAccount(newName, coverType, coverValue)
        txDao.moveByCategory(newId, sourceId, l1, l2, System.currentTimeMillis())
        count
    }

    // ---------------- 资产账户（v1.1） ----------------

    fun observeAssets(): Flow<List<Asset>> = assetDao.observeActive()

    suspend fun assets(): List<Asset> = assetDao.active()

    suspend fun asset(id: Long): Asset? = assetDao.byId(id)

    fun observeAssetBalances(): Flow<List<AssetBalance>> = combine(
        assetDao.observeActive(),
        txDao.observeAllRowsFlow()
    ) { assets, txs ->
        val deltas = AssetLedger.deltas(txs)
        assets.map { asset ->
            AssetBalance(
                asset = asset,
                balanceCents = asset.initialBalanceCents + (deltas[asset.id] ?: 0L)
            )
        }
    }.flowOn(Dispatchers.Default)

    suspend fun createAsset(name: String, initialBalanceCents: Long, initialDate: String): Long {
        val trimmed = name.trim()
        check(trimmed.isNotEmpty()) { "资产名称不能为空" }
        check(assetDao.byName(trimmed) == null) { "已存在同名资产账户" }
        return assetDao.insert(
            Asset(
                name = trimmed,
                initialBalanceCents = initialBalanceCents,
                initialDate = initialDate.ifBlank { LocalDate.now().toString() },
                isDeleted = false
            )
        )
    }

    suspend fun updateAsset(asset: Asset, name: String, initialBalanceCents: Long, initialDate: String) {
        val trimmed = name.trim()
        check(trimmed.isNotEmpty()) { "资产名称不能为空" }
        val sameName = assetDao.byName(trimmed)
        check(sameName == null || sameName.id == asset.id) { "已存在同名资产账户" }
        assetDao.updateMeta(
            id = asset.id,
            name = trimmed,
            initialBalanceCents = initialBalanceCents,
            initialDate = initialDate.ifBlank { asset.initialDate }
        )
    }

    /**
     * 软删除（归档）：历史账单保留对该资产的引用，余额统计自然停用。
     * 同一事务内清空所有分类对它的默认绑定，避免记账时选到已归档账户。
     */
    suspend fun deleteAsset(asset: Asset) = db.withTransaction {
        assetDao.softDelete(asset.id)
        categoryDao.clearDefaultAsset(asset.id)
    }

    // ---------------- 账单 ----------------

    fun observeRange(accountId: Long, start: String, end: String): Flow<List<Tx>> =
        txDao.observeRange(accountId, start, end)

    /** 账单列表筛选（类型 / 一级分类，null 表示不过滤）。 */
    fun observeFiltered(
        accountId: Long,
        start: String,
        end: String,
        type: TxType?,
        categoryL1: String?
    ): Flow<List<Tx>> = txDao.observeFiltered(
        accountId = accountId,
        start = start,
        end = end,
        type = type?.name ?: "",
        cat = categoryL1.orEmpty()
    )

    fun observeByDate(accountId: Long, date: String): Flow<List<Tx>> = txDao.observeByDate(accountId, date)

    fun observeAllTx(accountId: Long): Flow<List<Tx>> = txDao.observeAll(accountId)

    fun observeById(id: Long): Flow<Tx?> = txDao.observeById(id)

    suspend fun tx(id: Long): Tx? = txDao.byId(id)

    /**
     * 新增账单。
     *
     * [huiwangSubtype] 由新 UI 显式传入；旧 UI 未传时保持 null，
     * 由 [com.kumquat.ledger.domain.LegacyHuiWangResolver] 按旧分类名兼容解析。
     * 显式传入 subtype 时，退款会强制校验剩余可退金额与日期。
     */
    suspend fun insertTx(
        accountId: Long,
        type: TxType,
        categoryL1: String,
        categoryL2: String,
        amountCents: Long,
        date: String,
        note: String,
        linkedTxId: Long?,
        assetId: Long? = null,
        toAssetId: Long? = null,
        huiwangSubtype: String? = null
    ): Long = db.withTransaction {
        require(amountCents > 0L) { "金额必须大于 0" }
        val subtype = parseSubtype(type, huiwangSubtype)
        if (type == TxType.REFUND) {
            validateRefundLink(
                accountId = accountId,
                linkedTxId = linkedTxId,
                amountCents = amountCents,
                date = date,
                subtype = subtype,
                refundId = 0L,
                strict = subtype != null
            )
        }
        val now = System.currentTimeMillis()
        txDao.insert(
            Tx(
                accountId = accountId,
                type = type.name,
                categoryL1 = categoryL1,
                categoryL2 = categoryL2,
                amountCents = amountCents,
                date = date,
                note = note.trim(),
                linkedTxId = linkedTxId,
                assetId = assetId,
                toAssetId = if (type.isTransfer) toAssetId else null,
                huiwangSubtype = subtype?.name,
                createdAt = now,
                updatedAt = now
            )
        )
    }

    /**
     * 编辑账单。原账单金额不能低于已退款合计；存在关联退款时不能改成其他类型。
     */
    suspend fun updateTx(tx: Tx) = db.withTransaction {
        require(tx.amountCents > 0L) { "金额必须大于 0" }
        val current = txDao.byId(tx.id) ?: error("账单不存在，可能已被删除")
        val currentType = TxType.parse(current.type) ?: error("非法类型：${current.type}")
        val newType = TxType.parse(tx.type) ?: error("非法类型：${tx.type}")
        val subtype = parseSubtype(newType, tx.huiwangSubtype)

        if (currentType == TxType.EXPENSE && newType != TxType.EXPENSE) {
            val refunds = txDao.refundsLinkedTo(current.id)
            check(refunds.isEmpty()) { "该账单有 ${refunds.size} 笔关联退款，不能改为其他类型" }
        }
        if (newType == TxType.EXPENSE) {
            val refunds = txDao.refundsLinkedTo(current.id)
            if (refunds.isNotEmpty()) {
                RefundRules.validateExpenseAmount(current, tx.amountCents, refunds)?.let { error(it) }
            }
        }
        if (newType == TxType.REFUND) {
            validateRefundLink(
                accountId = tx.accountId,
                linkedTxId = tx.linkedTxId,
                amountCents = tx.amountCents,
                date = tx.date,
                subtype = subtype,
                refundId = tx.id,
                strict = subtype != null
            )
        }
        txDao.update(
            tx.copy(
                huiwangSubtype = subtype?.name,
                toAssetId = if (newType.isTransfer) tx.toAssetId else null,
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    /**
     * 删除账单。删除原出账时若存在关联退款必须阻止，绝不级联删除退款。
     * 资产余额由流水实时重算，删除后无需手工回滚。
     */
    suspend fun deleteTx(tx: Tx) = db.withTransaction {
        val current = txDao.byId(tx.id) ?: return@withTransaction
        if (TxType.parse(current.type) == TxType.EXPENSE) {
            RefundRules.deleteBlockedReason(current, txDao.refundsLinkedTo(current.id))?.let { error(it) }
        }
        txDao.clearLinksTo(current.id)
        txDao.delete(current)
    }

    /** 校验退款关联的结构合法性；[strict] 为 true 时额外校验金额与日期（新 UI 路径）。 */
    private suspend fun validateRefundLink(
        accountId: Long,
        linkedTxId: Long?,
        amountCents: Long,
        date: String,
        subtype: HuiWangSubtype?,
        refundId: Long,
        strict: Boolean
    ) {
        if (linkedTxId == null) return
        val original = txDao.byId(linkedTxId) ?: error("原账单不存在或已被删除")
        check(TxType.parse(original.type) == TxType.EXPENSE) { "退款只能关联出账账单" }
        check(original.accountId == accountId) { "退款与原账单不在同一账本" }
        if (!strict) return
        val draft = RefundRules.RefundDraft(
            accountId = accountId,
            linkedTxId = linkedTxId,
            amountCents = amountCents,
            date = date,
            subtype = subtype,
            id = refundId
        )
        RefundRules.validate(
            original = original,
            draft = draft,
            all = txDao.refundsLinkedTo(original.id),
            allowPending = true
        )?.let { error(it) }
    }

    private fun parseSubtype(type: TxType, raw: String?): HuiWangSubtype? {
        val value = raw?.trim().orEmpty()
        if (value.isEmpty()) return null
        val parsed = HuiWangSubtype.parse(value) ?: error("未知的回往方向：$value")
        return when {
            type == TxType.REFUND && !parsed.isRefund -> error("退款方向只能是全额退款或部分退款")
            type == TxType.EXCHANGE && parsed.isRefund -> error("代付/借出/收回/还款不能使用退款方向")
            type != TxType.REFUND && type != TxType.EXCHANGE -> error("只有回往记录才能设置回往方向")
            else -> parsed
        }
    }

    suspend fun candidates(accountId: Long, type: TxType, excludeId: Long, start: String, end: String): List<TxBrief> {
        val targetType = if (type == TxType.REFUND) TxType.EXPENSE else type
        return txDao.candidates(accountId, targetType.name, excludeId, start, end)
    }

    /** 可退款的原出账候选（已剔除剩余可退为 0 的账单）。 */
    suspend fun refundCandidates(
        accountId: Long,
        excludeId: Long = 0L,
        start: String,
        end: String
    ): List<RefundCandidate> = txDao.refundCandidates(accountId, excludeId, start, end)

    suspend fun refundsLinkedTo(expenseId: Long): List<Tx> = txDao.refundsLinkedTo(expenseId)

    /** 收回/还款可关联的原代付/借出（只列出仍有未收回金额的）。 */
    suspend fun exchangeOrigins(accountId: Long): List<Tx> {
        val all = txDao.getAll(accountId)
        return all.filter { tx ->
            TxType.parse(tx.type) == TxType.EXCHANGE &&
                com.kumquat.ledger.domain.LegacyHuiWangResolver.resolve(tx, null).direction ==
                com.kumquat.ledger.domain.FundDirection.OUT &&
                com.kumquat.ledger.domain.RefundRules.outstandingCents(tx, all) > 0L
        }
    }

    suspend fun latestDate(accountId: Long): String? = txDao.latestDate(accountId)

    // ---------------- 统一统计（v1.1.1） ----------------

    /**
     * 首页/统计/日历/日列表共用的唯一统计入口。
     * 计算在后台线程完成，任何账单变动都会让 Flow 重新发射。
     */
    fun observeStats(accountId: Long, start: String, end: String): Flow<PeriodStats> =
        txDao.observeAll(accountId)
            .map { txs -> StatsEngine.compute(accountId, txs, DateRange(start, end)) }
            .flowOn(Dispatchers.Default)

    suspend fun stats(accountId: Long, start: String, end: String): PeriodStats =
        StatsEngine.compute(accountId, txDao.getAll(accountId), DateRange(start, end))

    // ---------------- 统计 ----------------

    fun observeTypeTotals(accountId: Long, start: String, end: String): Flow<LedgerMath.TypeTotals> =
        txDao.observeTypeTotals(accountId, start, end).map { rows ->
            LedgerMath.totals(
                rows.mapNotNull { r ->
                    TxType.parse(r.type)?.let { it to r.total }
                }
            )
        }

    /** 支出排行（净额：已关联的回冲会抵扣到原账单分类）。 */
    fun observeExpenseRanking(accountId: Long, start: String, end: String): Flow<List<LedgerMath.CatAmount>> =
        combine(
            txDao.observeCatTotals(accountId, TxType.EXPENSE.name, start, end),
            txDao.observeRefundByLinkedCategory(accountId, start, end)
        ) { expense, refundLinked ->
            LedgerMath.netExpenseByCategory(
                expense = expense.map { LedgerMath.CatAmount(it.categoryL1, it.categoryL2, it.total) },
                refundLinked = refundLinked.map { LedgerMath.CatAmount(it.categoryL1, it.categoryL2, it.total) },
                refundUnlinked = emptyList()
            )
        }

    /** 收入排行。 */
    fun observeIncomeRanking(accountId: Long, start: String, end: String): Flow<List<LedgerMath.CatAmount>> =
        txDao.observeCatTotals(accountId, TxType.INCOME.name, start, end).map { rows ->
            rows.map { LedgerMath.CatAmount(it.categoryL1, it.categoryL2, it.total) }
                .sortedByDescending { it.cents }
        }

    /** 回冲 / 往来 / 转账分类小计（单列展示，不与收支混排）。 */
    fun observeOtherRanking(accountId: Long, type: TxType, start: String, end: String): Flow<List<LedgerMath.CatAmount>> =
        txDao.observeCatTotals(accountId, type.name, start, end).map { rows ->
            rows.map { LedgerMath.CatAmount(it.categoryL1, it.categoryL2, it.total) }
                .sortedByDescending { it.cents }
        }

    fun observeDayTypes(accountId: Long, start: String, end: String): Flow<List<DayTypeRow>> =
        txDao.observeDayTypes(accountId, start, end)

    // ---------------- 分类 ----------------

    fun observeCategories(): Flow<List<Category>> = categoryDao.observeAll()

    suspend fun categories(): List<Category> = categoryDao.getAll()

    suspend fun addCategory(
        type: TxType,
        level: Int,
        parentId: Long?,
        name: String,
        icon: String,
        defaultAssetId: Long? = null
    ): Long {
        val trimmed = name.trim()
        check(trimmed.isNotEmpty()) { "分类名称不能为空" }
        check(trimmed.length <= 12) { "分类名称最多 12 个字" }
        check(trimmed != CategorySeed.UNCATEGORIZED) { "“未分类”是系统保留名称" }
        check(level in 1..2) { "分类层级不合法" }
        if (level == 1) {
            check(parentId == null) { "一级分类不能有父分类" }
        } else {
            val parent = parentId?.let { categoryDao.byId(it) } ?: error("父分类不存在")
            check(parent.level == level - 1 && parent.type == type.name) { "父分类层级或类型不匹配" }
        }
        check(categoryDao.findByName(type.name, level, trimmed, parentId) == null) {
            "同一父分类下已有同名分类"
        }
        validateDefaultAsset(defaultAssetId)
        val order = (categoryDao.maxSortOrder(type.name, level, parentId) ?: -1) + 1
        return categoryDao.insert(
            Category(
                type = type.name,
                level = level,
                parentId = parentId,
                name = trimmed,
                icon = icon,
                sortOrder = order,
                isBuiltin = false,
                defaultAssetId = defaultAssetId
            )
        )
    }

    /** 编辑分类名称/图标/默认账户；父级关系保持不变。 */
    suspend fun editCategory(
        category: Category,
        name: String,
        icon: String,
        defaultAssetId: Long?
    ) = db.withTransaction {
        val trimmed = name.trim()
        check(trimmed.isNotEmpty()) { "分类名称不能为空" }
        check(trimmed.length <= 12) { "分类名称最多 12 个字" }
        check(category.name != CategorySeed.UNCATEGORIZED) { "未分类是系统分类，不能重命名" }
        check(trimmed != CategorySeed.UNCATEGORIZED) { "“未分类”是系统保留名称" }
        val same = categoryDao.findByName(category.type, category.level, trimmed, category.parentId)
        check(same == null || same.id == category.id) { "同一父分类下已有同名分类" }
        validateDefaultAsset(defaultAssetId)
        if (trimmed != category.name) {
            val now = System.currentTimeMillis()
            when (category.level) {
                1 -> txDao.renameBillsL1(category.type, category.name, trimmed, now)
                2 -> {
                    val parentName = category.parentId?.let { categoryDao.byId(it)?.name } ?: ""
                    if (parentName.isNotEmpty()) {
                        txDao.renameBillsL2(category.type, parentName, category.name, trimmed, now)
                    }
                }
            }
        }
        categoryDao.update(
            category.copy(name = trimmed, icon = icon, defaultAssetId = defaultAssetId)
        )
    }

    suspend fun setCategoryDefaultAsset(category: Category, assetId: Long?) {
        validateDefaultAsset(assetId)
        categoryDao.update(category.copy(defaultAssetId = assetId))
    }

    private suspend fun validateDefaultAsset(assetId: Long?) {
        if (assetId == null) return
        val asset = assetDao.byId(assetId) ?: error("默认账户不存在")
        check(!asset.isDeleted) { "默认账户已归档，请重新选择" }
    }

    /** 分类引用统计：供删除确认框展示“子分类 N 个 / 账单 M 笔”。 */
    data class CategoryUsage(val childCount: Int, val billCount: Int)

    suspend fun categoryUsage(category: Category): CategoryUsage {
        val children = if (category.level == 1) categoryDao.childCount(category.id) else 0
        val bills = when (category.level) {
            1 -> txDao.countBillsByL1(category.type, category.name)
            2 -> {
                val parentName = category.parentId?.let { categoryDao.byId(it)?.name } ?: ""
                if (parentName.isEmpty()) 0 else txDao.countBillsByL2(category.type, parentName, category.name)
            }
            else -> 0
        }
        return CategoryUsage(children, bills)
    }

    /**
     * 分类重命名：账单存的是分类名，必须同步改历史账单，否则统计/展示会对不上。
     * 名称不允许改成系统兜底分类，也不允许同级重名。
     */
    suspend fun renameCategory(category: Category, newName: String) {
        editCategory(category, newName, category.icon, category.defaultAssetId)
    }

    suspend fun setCategoryHidden(category: Category, hidden: Boolean) {
        categoryDao.update(category.copy(hidden = hidden))
    }

    suspend fun moveCategory(category: Category, delta: Int) {
        categoryDao.update(category.copy(sortOrder = (category.sortOrder + delta).coerceAtLeast(0)))
    }

    /** 相邻分类交换顺序（保持 sortOrder 不重复）。 */
    suspend fun swapCategory(a: Category, b: Category) = db.withTransaction {
        categoryDao.update(a.copy(sortOrder = b.sortOrder))
        categoryDao.update(b.copy(sortOrder = a.sortOrder))
    }

    /**
     * 删除分类并迁移账单：账单绝不删除，全部移到“未分类”。
     * [deleteBranch] 为 false 时，若存在子分类则拒绝删除。
     */
    suspend fun deleteCategory(category: Category, deleteBranch: Boolean = true): CategoryUsage =
        db.withTransaction {
            check(category.name != CategorySeed.UNCATEGORIZED) { "未分类是系统分类，不能删除" }
            CategorySeed.ensureUncategorizedSeeded(categoryDao)
            val children = if (category.level == 1) {
                categoryDao.level2(category.type, category.id)
            } else {
                emptyList()
            }
            check(children.isEmpty() || deleteBranch) {
                "该分类下有 ${children.size} 个子分类，请先处理子分类或选择“删除整个分支”"
            }
            val usage = categoryUsage(category)
            val now = System.currentTimeMillis()
            when (category.level) {
                1 -> txDao.moveBillsFromL1(
                    type = category.type,
                    oldName = category.name,
                    targetName = CategorySeed.UNCATEGORIZED,
                    now = now
                )
                2 -> {
                    val parentName = category.parentId?.let { categoryDao.byId(it)?.name } ?: ""
                    if (parentName.isNotEmpty()) {
                        txDao.moveBillsFromL2(
                            type = category.type,
                            parentName = parentName,
                            oldName = category.name,
                            targetName = CategorySeed.UNCATEGORIZED,
                            now = now
                        )
                    }
                }
            }
            children.forEach { categoryDao.delete(it) }
            categoryDao.delete(category)
            usage
        }

    // ---------------- 导出 / 导入 ----------------

    /** 导出：不传 [accountIds] 表示全量导出。 */
    suspend fun export(accountIds: List<Long>? = null): LedgerFile {
        val accounts = accountDao.getAll().filter { accountIds == null || it.id in accountIds }
        val accountMap = accounts.associateBy { it.id }
        val txs = txDao.getAllRows().filter { it.accountId in accountMap.keys }
        val categories = categoryDao.getAll()
        val assets = assetDao.all()
        val assetNameById = assets.associate { it.id to it.name }

        // 事务按下标关联：先建立 id -> 下标
        val indexOf = HashMap<Long, Int>()
        txs.forEachIndexed { i, tx -> indexOf[tx.id] = i }

        return LedgerFile(
            version = 1,
            exportedAt = java.time.LocalDateTime.now().withNano(0).toString(),
            accounts = accounts.map { AccountJson(it.name, it.coverType, it.coverValue) },
            categories = categories.filter { !it.hidden }.map { c ->
                val parentName = c.parentId?.let { pid -> categories.firstOrNull { it.id == pid }?.name }
                CategoryJson(
                    type = c.type,
                    level = c.level,
                    name = c.name,
                    parentName = parentName,
                    icon = c.icon.ifBlank { null },
                    defaultAssetName = c.defaultAssetId?.let { assetNameById[it] }
                )
            },
            assets = assets.map {
                AssetJson(
                    name = it.name,
                    initialBalanceCents = it.initialBalanceCents,
                    initialDate = it.initialDate,
                    deleted = it.isDeleted
                )
            },
            transactions = txs.map { tx ->
                TxJson(
                    accountName = accountMap[tx.accountId]?.name ?: "未命名账本",
                    type = tx.type,
                    categoryL1 = tx.categoryL1,
                    categoryL2 = tx.categoryL2,
                    amountCents = tx.amountCents,
                    date = tx.date,
                    note = tx.note,
                    linkedIndex = tx.linkedTxId?.let { indexOf[it] },
                    assetName = tx.assetId?.let { assetNameById[it] },
                    toAssetName = tx.toAssetId?.let { assetNameById[it] },
                    huiwangSubtype = tx.huiwangSubtype
                )
            }
        )
    }

    /** 导入结果。新增计数用于提示“有关联/默认账户未迁移”，不阻塞导入。 */
    data class ImportResult(
        val insertedTx: Int,
        val createdAccounts: Int,
        val createdCategories: Int,
        val createdAssets: Int,
        val firstAccountId: Long?,
        val pendingRefundCount: Int = 0,
        val unmappedCategoryDefaults: Int = 0
    )

    /**
     * 写入导入数据（事务）。调用前必须先通过 [ImportValidator.validate]；
     * 这里仍会对类型做一次防御性断言，异常时整批回滚。
     */
    suspend fun applyImport(file: LedgerFile): ImportResult = db.withTransaction {
        var createdAccounts = 0
        var createdCategories = 0
        var createdAssets = 0

        // 1. 账本：按名称匹配，找不到就新建
        val accountIdByName = HashMap<String, Long>()
        accountDao.getAll().forEach { accountIdByName[it.name] = it.id }
        file.accounts.forEach { a ->
            val name = a.name.trim()
            if (!accountIdByName.containsKey(name)) {
                val now = System.currentTimeMillis()
                val id = accountDao.insert(
                    Account(
                        name = name,
                        coverType = a.coverType,
                        coverValue = a.coverValue,
                        createdAt = now,
                        updatedAt = now
                    )
                )
                accountIdByName[name] = id
                createdAccounts++
            }
        }

        // 1.5 资产账户：按名称匹配（含软删除的），找不到就新建
        val assetIdByName = HashMap<String, Long>()
        assetDao.all().forEach { assetIdByName[it.name] = it.id }
        file.assets.forEach { a ->
            val name = a.name.trim()
            if (name.isEmpty() || assetIdByName.containsKey(name)) return@forEach
            val id = assetDao.insert(
                Asset(
                    name = name,
                    initialBalanceCents = a.initialBalanceCents,
                    initialDate = a.initialDate.ifBlank { LocalDate.now().toString() },
                    isDeleted = a.deleted
                )
            )
            assetIdByName[name] = id
            createdAssets++
        }

        // 2. 分类：一级先建，二级按 parentName 挂接
        val existing = categoryDao.getAll()
        val l1Map = HashMap<String, Long>() // type|name -> id
        existing.filter { it.level == 1 }.forEach { l1Map["${it.type}|${it.name}"] = it.id }
        file.categories.filter { it.level == 1 }.forEach { c ->
            val type = TxType.parse(c.type) ?: return@forEach
            val key = "${type.name}|${c.name.trim()}"
            if (!l1Map.containsKey(key)) {
                val id = categoryDao.insert(
                    Category(
                        type = type.name,
                        level = 1,
                        name = c.name.trim(),
                        icon = c.icon.orEmpty(),
                        sortOrder = (categoryDao.maxSortOrder(type.name, 1, null) ?: -1) + 1,
                        isBuiltin = false
                    )
                )
                l1Map[key] = id
                createdCategories++
            }
        }
        file.categories.filter { it.level == 2 }.forEach { c ->
            val type = TxType.parse(c.type) ?: return@forEach
            val parentKey = "${type.name}|${c.parentName?.trim().orEmpty()}"
            val parentId = l1Map[parentKey] ?: return@forEach
            val exists = categoryDao.findByName(type.name, 2, c.name.trim(), parentId)
            if (exists == null) {
                categoryDao.insert(
                    Category(
                        type = type.name,
                        level = 2,
                        parentId = parentId,
                        name = c.name.trim(),
                        icon = c.icon.orEmpty(),
                        sortOrder = (categoryDao.maxSortOrder(type.name, 2, parentId) ?: -1) + 1,
                        isBuiltin = false
                    )
                )
                createdCategories++
            }
        }

        // 2.5 分类默认资产账户：按名称重映射；目标账户不存在时置空并计数
        var unmappedDefaults = 0
        file.categories.forEach { c ->
            val type = TxType.parse(c.type) ?: return@forEach
            val assetName = c.defaultAssetName?.trim().orEmpty()
            if (assetName.isEmpty()) return@forEach
            val categoryId = when (c.level) {
                1 -> l1Map["${type.name}|${c.name.trim()}"]
                2 -> {
                    val parentId = l1Map["${type.name}|${c.parentName?.trim().orEmpty()}"]
                        ?: return@forEach
                    categoryDao.findByName(type.name, 2, c.name.trim(), parentId)?.id
                }
                else -> null
            } ?: return@forEach
            val assetId = assetIdByName[assetName]
            if (assetId == null) unmappedDefaults++
            val category = categoryDao.byId(categoryId) ?: return@forEach
            if (category.defaultAssetId != assetId) {
                categoryDao.update(category.copy(defaultAssetId = assetId))
            }
        }

        // 3. 账单：先全部插入，再按 linkedIndex 建立关联
        val now = System.currentTimeMillis()
        val ids = ArrayList<Long>(file.transactions.size)
        val pendingRefunds = file.transactions.withIndex().count { (i, t) ->
            TxType.parse(t.type) == TxType.REFUND && !hasValidImportLink(i, t, file.transactions)
        }
        file.transactions.forEach { t ->
            val type = TxType.parse(t.type) ?: error("非法类型：${t.type}")
            val accountId = accountIdByName[t.accountName.trim()] ?: error("未知账本：${t.accountName}")
            ids.add(
                txDao.insert(
                    Tx(
                        accountId = accountId,
                        type = type.name,
                        categoryL1 = t.categoryL1.trim(),
                        categoryL2 = t.categoryL2.trim(),
                        amountCents = t.amountCents,
                        date = t.date,
                        note = t.note.trim(),
                        linkedTxId = null,
                        assetId = t.assetName?.trim()?.takeIf { it.isNotEmpty() }?.let { assetIdByName[it] },
                        toAssetId = if (type.isTransfer) {
                            t.toAssetName?.trim()?.takeIf { it.isNotEmpty() }?.let { assetIdByName[it] }
                        } else null,
                        huiwangSubtype = parseImportSubtype(type, t.huiwangSubtype)?.name,
                        createdAt = now,
                        updatedAt = now
                    )
                )
            )
        }
        file.transactions.forEachIndexed { i, t ->
            if (!hasValidImportLink(i, t, file.transactions)) return@forEachIndexed
            val linked = t.linkedIndex ?: return@forEachIndexed
            txDao.setLink(ids[i], ids[linked])
        }

        ImportResult(
            insertedTx = ids.size,
            createdAccounts = createdAccounts,
            createdCategories = createdCategories,
            createdAssets = createdAssets,
            firstAccountId = accountIdByName[file.transactions.firstOrNull()?.accountName?.trim()],
            pendingRefundCount = pendingRefunds,
            unmappedCategoryDefaults = unmappedDefaults
        )
    }

    /** 导入时对 subtype 做类型一致性过滤；未知或错配一律降级为 null（按旧记录解析）。 */
    private fun parseImportSubtype(type: TxType, raw: String?): HuiWangSubtype? {
        val parsed = HuiWangSubtype.parse(raw) ?: return null
        return when {
            type == TxType.REFUND && parsed.isRefund -> parsed
            type == TxType.EXCHANGE && !parsed.isRefund -> parsed
            else -> null
        }
    }

    /** 导入文件的关联是否可落地；失效的关联保持 null，由 LegacyHuiWangResolver 标为待关联。 */
    private fun hasValidImportLink(index: Int, tx: TxJson, all: List<TxJson>): Boolean {
        val linked = tx.linkedIndex ?: return false
        if (linked !in all.indices || linked == index) return false
        val source = TxType.parse(tx.type) ?: return false
        val target = TxType.parse(all[linked].type) ?: return false
        if (target == TxType.REFUND) return false
        if (source == TxType.REFUND && target != TxType.EXPENSE) return false
        return true
    }

    /** 仅用于首次启动时的示例数据判断。 */
    suspend fun hasAnyTx(): Boolean = txDao.getAllRows().isNotEmpty()
}
