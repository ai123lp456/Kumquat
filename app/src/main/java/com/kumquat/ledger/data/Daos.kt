package com.kumquat.ledger.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/** 按类型聚合的小计行。 */
data class TypeSumRow(val type: String, val total: Long)

/** 按分类聚合的行（分类排行）。 */
data class CatSumRow(val categoryL1: String, val categoryL2: String, val total: Long)

/** 日历圆点：某天某类型有几笔。 */
data class DayTypeRow(val date: String, val type: String, val count: Int)

/** 可退款的出账候选：带原金额、已退金额，UI 无需再逐笔查退款。 */
data class RefundCandidate(
    val id: Long,
    val type: String,
    val categoryL1: String,
    val categoryL2: String,
    val amountCents: Long,
    val date: String,
    val note: String,
    val assetId: Long?,
    val refundedCents: Long
) {
    val remainingCents: Long get() = (amountCents - refundedCents).coerceAtLeast(0L)
}

/** 某分类下的一笔候选账单（用于关联原账单）。 */
data class TxBrief(
    val id: Long,
    val type: String,
    val categoryL1: String,
    val categoryL2: String,
    val amountCents: Long,
    val date: String,
    val note: String
)

@Dao
interface AccountDao {

    @Query("SELECT * FROM account ORDER BY createdAt ASC, id ASC")
    fun observeAll(): Flow<List<Account>>

    @Query("SELECT * FROM account ORDER BY createdAt ASC, id ASC")
    suspend fun getAll(): List<Account>

    @Query("SELECT * FROM account WHERE id = :id")
    suspend fun byId(id: Long): Account?

    @Query("SELECT * FROM account WHERE name = :name LIMIT 1")
    suspend fun byName(name: String): Account?

    @Query("SELECT COUNT(*) FROM account")
    suspend fun count(): Int

    @Insert
    suspend fun insert(account: Account): Long

    @Update
    suspend fun update(account: Account)

    @Query("DELETE FROM account WHERE id = :id")
    suspend fun deleteById(id: Long)
}

@Dao
interface TxDao {

    @Insert
    suspend fun insert(tx: Tx): Long

    @Insert
    suspend fun insertAll(list: List<Tx>): List<Long>

    @Update
    suspend fun update(tx: Tx)

    @Delete
    suspend fun delete(tx: Tx)

    @Query("SELECT * FROM tx WHERE id = :id")
    suspend fun byId(id: Long): Tx?

    @Query("SELECT * FROM tx WHERE id = :id")
    fun observeById(id: Long): Flow<Tx?>

    @Query("SELECT * FROM tx WHERE accountId = :accountId AND date BETWEEN :start AND :end ORDER BY date DESC, id DESC")
    fun observeRange(accountId: Long, start: String, end: String): Flow<List<Tx>>

    @Query("SELECT * FROM tx WHERE accountId = :accountId ORDER BY date DESC, id DESC")
    fun observeAll(accountId: Long): Flow<List<Tx>>

    @Query("SELECT * FROM tx WHERE accountId = :accountId AND date = :date ORDER BY id DESC")
    fun observeByDate(accountId: Long, date: String): Flow<List<Tx>>

    @Query("SELECT * FROM tx WHERE accountId = :accountId ORDER BY date DESC, id DESC")
    suspend fun getAll(accountId: Long): List<Tx>

    /** 带筛选的区间查询：type/cat 传空串表示不过滤。 */
    @Query(
        "SELECT * FROM tx WHERE accountId = :accountId AND date BETWEEN :start AND :end " +
            "AND (:type = '' OR type = :type) AND (:cat = '' OR categoryL1 = :cat) " +
            "ORDER BY date DESC, id DESC"
    )
    fun observeFiltered(
        accountId: Long,
        start: String,
        end: String,
        type: String,
        cat: String
    ): Flow<List<Tx>>

    @Query("SELECT * FROM tx ORDER BY accountId ASC, date ASC, id ASC")
    suspend fun getAllRows(): List<Tx>

    /** 资产余额重算用：全部账本流水（余额是全局账户，不按账本拆分）。 */
    @Query("SELECT * FROM tx ORDER BY id ASC")
    fun observeAllRowsFlow(): Flow<List<Tx>>

    @Query(
        "SELECT id, type, categoryL1, categoryL2, amountCents, date, note FROM tx " +
            "WHERE accountId = :accountId AND type = :type AND id <> :excludeId AND date BETWEEN :start AND :end " +
            "ORDER BY date DESC, id DESC"
    )
    suspend fun candidates(
        accountId: Long,
        type: String,
        excludeId: Long,
        start: String,
        end: String
    ): List<TxBrief>

    /**
     * 可退款的原出账：同一账本、非自身、日期范围内、剩余可退 > 0。
     * 已退金额通过子查询实时求和，不依赖缓存字段。
     */
    @Query(
        "SELECT t.id AS id, t.type AS type, t.categoryL1 AS categoryL1, t.categoryL2 AS categoryL2, " +
            "t.amountCents AS amountCents, t.date AS date, t.note AS note, t.assetId AS assetId, " +
            "COALESCE(r.refunded, 0) AS refundedCents " +
            "FROM tx t LEFT JOIN (" +
            "SELECT linkedTxId AS linkedTxId, SUM(ABS(amountCents)) AS refunded FROM tx " +
            "WHERE type = 'REFUND' AND linkedTxId IS NOT NULL GROUP BY linkedTxId" +
            ") r ON r.linkedTxId = t.id " +
            "WHERE t.accountId = :accountId AND t.type = 'EXPENSE' AND t.id <> :excludeId " +
            "AND t.date BETWEEN :start AND :end " +
            "AND (t.amountCents - COALESCE(r.refunded, 0)) > 0 " +
            "ORDER BY t.date DESC, t.id DESC"
    )
    suspend fun refundCandidates(
        accountId: Long,
        excludeId: Long,
        start: String,
        end: String
    ): List<RefundCandidate>

    @Query("SELECT * FROM tx WHERE type = 'REFUND' AND linkedTxId = :expenseId ORDER BY date ASC, id ASC")
    suspend fun refundsLinkedTo(expenseId: Long): List<Tx>

    @Query(
        "SELECT type AS type, SUM(amountCents) AS total FROM tx " +
            "WHERE accountId = :accountId AND date BETWEEN :start AND :end GROUP BY type"
    )
    fun observeTypeTotals(accountId: Long, start: String, end: String): Flow<List<TypeSumRow>>

    @Query(
        "SELECT categoryL1 AS categoryL1, categoryL2 AS categoryL2, SUM(amountCents) AS total FROM tx " +
            "WHERE accountId = :accountId AND type = :type AND date BETWEEN :start AND :end " +
            "GROUP BY categoryL1, categoryL2 ORDER BY total DESC"
    )
    fun observeCatTotals(accountId: Long, type: String, start: String, end: String): Flow<List<CatSumRow>>

    /** 已关联原账单的回冲：按"被冲减的原账单"的分类聚合（用于出账排行抵扣）。 */
    @Query(
        "SELECT t.categoryL1 AS categoryL1, t.categoryL2 AS categoryL2, SUM(r.amountCents) AS total FROM tx r " +
            "JOIN tx t ON r.linkedTxId = t.id " +
            "WHERE r.accountId = :accountId AND r.type = 'REFUND' AND r.date BETWEEN :start AND :end " +
            "GROUP BY t.categoryL1, t.categoryL2"
    )
    fun observeRefundByLinkedCategory(
        accountId: Long,
        start: String,
        end: String
    ): Flow<List<CatSumRow>>

    @Query(
        "SELECT date AS date, type AS type, COUNT(*) AS count FROM tx " +
            "WHERE accountId = :accountId AND date BETWEEN :start AND :end GROUP BY date, type"
    )
    fun observeDayTypes(accountId: Long, start: String, end: String): Flow<List<DayTypeRow>>

    @Query("SELECT MAX(date) FROM tx WHERE accountId = :accountId")
    suspend fun latestDate(accountId: Long): String?

    @Query("SELECT COUNT(*) FROM tx WHERE accountId = :accountId")
    suspend fun countByAccount(accountId: Long): Int

    /** 合并账本：把来源账本的明细整体改挂到目标账本。 */
    @Query("UPDATE tx SET accountId = :targetId, updatedAt = :now WHERE accountId IN (:sourceIds)")
    suspend fun moveAccounts(targetId: Long, sourceIds: List<Long>, now: Long)

    /** 拆分账本：按日期区间把明细改挂到新账本。 */
    @Query(
        "UPDATE tx SET accountId = :targetId, updatedAt = :now " +
            "WHERE accountId = :sourceId AND date BETWEEN :start AND :end"
    )
    suspend fun moveByDate(targetId: Long, sourceId: Long, start: String, end: String, now: Long)

    /** 拆分账本：按一级（可带二级）分类把明细改挂到新账本。 */
    @Query(
        "UPDATE tx SET accountId = :targetId, updatedAt = :now " +
            "WHERE accountId = :sourceId AND categoryL1 = :l1 AND (:l2 = '' OR categoryL2 = :l2)"
    )
    suspend fun moveByCategory(targetId: Long, sourceId: Long, l1: String, l2: String, now: Long)

    @Query("UPDATE tx SET linkedTxId = :linkId WHERE id = :id")
    suspend fun setLink(id: Long, linkId: Long?)

    @Query("UPDATE tx SET linkedTxId = NULL WHERE linkedTxId = :id")
    suspend fun clearLinksTo(id: Long)

    // ---------------- 分类改名 / 删除后的账单迁移（v1.1.1） ----------------

    /** 分类重命名：账单存的是分类名，必须同步改历史账单，统计与展示才一致。 */
    @Query("UPDATE tx SET categoryL1 = :newName, updatedAt = :now WHERE type = :type AND categoryL1 = :oldName")
    suspend fun renameBillsL1(type: String, oldName: String, newName: String, now: Long)

    @Query(
        "UPDATE tx SET categoryL2 = :newName, updatedAt = :now " +
            "WHERE type = :type AND categoryL1 = :parentName AND categoryL2 = :oldName"
    )
    suspend fun renameBillsL2(type: String, parentName: String, oldName: String, newName: String, now: Long)

    /** 删除一级分类：该分类下所有账单（含子分类）移到未分类。 */
    @Query(
        "UPDATE tx SET categoryL1 = :targetName, categoryL2 = '', updatedAt = :now " +
            "WHERE type = :type AND categoryL1 = :oldName"
    )
    suspend fun moveBillsFromL1(type: String, oldName: String, targetName: String, now: Long)

    @Query(
        "UPDATE tx SET categoryL1 = :targetName, categoryL2 = '', updatedAt = :now " +
            "WHERE type = :type AND categoryL1 = :parentName AND categoryL2 = :oldName"
    )
    suspend fun moveBillsFromL2(
        type: String,
        parentName: String,
        oldName: String,
        targetName: String,
        now: Long
    )

    @Query("SELECT COUNT(*) FROM tx WHERE type = :type AND categoryL1 = :l1")
    suspend fun countBillsByL1(type: String, l1: String): Int

    @Query("SELECT COUNT(*) FROM tx WHERE type = :type AND categoryL1 = :l1 AND categoryL2 = :l2")
    suspend fun countBillsByL2(type: String, l1: String, l2: String): Int

    @Query("DELETE FROM tx WHERE accountId = :accountId")
    suspend fun deleteByAccount(accountId: Long)

    @Query("SELECT COUNT(*) FROM tx WHERE accountId = :accountId AND date BETWEEN :start AND :end")
    suspend fun countRange(accountId: Long, start: String, end: String): Int

    @Query(
        "SELECT COUNT(*) FROM tx WHERE accountId = :accountId AND categoryL1 = :l1 AND (:l2 = '' OR categoryL2 = :l2)"
    )
    suspend fun countCategory(accountId: Long, l1: String, l2: String): Int

    // ---------------- 资产余额（v1.1） ----------------
    // 余额不再用 SQL 分类型 CASE 计算：回往方向需要 LegacyHuiWangResolver 统一解析，
    // 由 AssetLedger 在仓库层基于全部流水计算，避免两套方向规则。

    @Query("SELECT COUNT(*) FROM tx WHERE assetId = :assetId OR toAssetId = :assetId")
    suspend fun countByAsset(assetId: Long): Int
}

@Dao
interface AssetDao {

    @Query("SELECT * FROM asset WHERE isDeleted = 0 ORDER BY id ASC")
    fun observeActive(): Flow<List<Asset>>

    @Query("SELECT * FROM asset WHERE isDeleted = 0 ORDER BY id ASC")
    suspend fun active(): List<Asset>

    /** 导出用：包含软删除的资产，保证 JSON 可以完整回导。 */
    @Query("SELECT * FROM asset ORDER BY id ASC")
    suspend fun all(): List<Asset>

    @Query("SELECT * FROM asset WHERE id = :id")
    suspend fun byId(id: Long): Asset?

    @Query("SELECT * FROM asset WHERE name = :name AND isDeleted = 0 LIMIT 1")
    suspend fun byName(name: String): Asset?

    @Query("SELECT COUNT(*) FROM asset WHERE isDeleted = 0")
    suspend fun countActive(): Int

    @Insert
    suspend fun insert(asset: Asset): Long

    @Update
    suspend fun update(asset: Asset)

    @Query("UPDATE asset SET isDeleted = 1 WHERE id = :id")
    suspend fun softDelete(id: Long)

    @Query("UPDATE asset SET name = :name, initialBalanceCents = :initialBalanceCents, initialDate = :initialDate WHERE id = :id")
    suspend fun updateMeta(id: Long, name: String, initialBalanceCents: Long, initialDate: String)
}

@Dao
interface CategoryDao {

    @Query("SELECT * FROM category ORDER BY type ASC, level ASC, sortOrder ASC, id ASC")
    fun observeAll(): Flow<List<Category>>

    @Query("SELECT * FROM category ORDER BY type ASC, level ASC, sortOrder ASC, id ASC")
    suspend fun getAll(): List<Category>

    @Query("SELECT * FROM category WHERE type = :type AND level = 1 ORDER BY sortOrder ASC, id ASC")
    suspend fun level1(type: String): List<Category>

    @Query("SELECT * FROM category WHERE type = :type AND level = 2 AND parentId = :parentId ORDER BY sortOrder ASC, id ASC")
    suspend fun level2(type: String, parentId: Long): List<Category>

    @Query("SELECT * FROM category WHERE type = :type AND level = 2 ORDER BY sortOrder ASC, id ASC")
    suspend fun allLevel2(type: String): List<Category>

    @Query("SELECT * FROM category WHERE id = :id")
    suspend fun byId(id: Long): Category?

    @Query("SELECT COUNT(*) FROM category WHERE parentId = :parentId")
    suspend fun childCount(parentId: Long): Int

    @Query("SELECT * FROM category WHERE type = :type AND level = :level AND name = :name COLLATE NOCASE AND parentId IS :parentId LIMIT 1")
    suspend fun findByName(type: String, level: Int, name: String, parentId: Long?): Category?

    @Query("SELECT COUNT(*) FROM category")
    suspend fun count(): Int

    @Query("SELECT MAX(sortOrder) FROM category WHERE type = :type AND level = :level AND parentId IS :parentId")
    suspend fun maxSortOrder(type: String, level: Int, parentId: Long?): Int?

    @Insert
    suspend fun insert(category: Category): Long

    @Insert
    suspend fun insertAll(list: List<Category>): List<Long>

    @Update
    suspend fun update(category: Category)

    @Delete
    suspend fun delete(category: Category)

    @Query("UPDATE category SET parentId = :newParentId WHERE parentId = :oldParentId")
    suspend fun reparent(oldParentId: Long, newParentId: Long?)

    /** 资产账户归档/删除时，清空所有分类对它的默认绑定。 */
    @Query("UPDATE category SET defaultAssetId = NULL WHERE defaultAssetId = :assetId")
    suspend fun clearDefaultAsset(assetId: Long)

    @Query("SELECT COUNT(*) FROM category WHERE defaultAssetId = :assetId")
    suspend fun countDefaultAsset(assetId: Long): Int
}
