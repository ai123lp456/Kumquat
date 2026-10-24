package com.kumquat.ledger.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 账本。封面编码约定（见合并决策 #7）：
 * - coverType = "color"    -> coverValue = "#RRGGBB"
 * - coverType = "gradient" -> coverValue = "#RRGGBB,#RRGGBB"
 */
@Entity(tableName = "account")
data class Account(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val coverType: String = "color",
    val coverValue: String = "#FFD1E8",
    val createdAt: Long,
    val updatedAt: Long
)

/**
 * 资产账户（v1.1 新增）：微信零钱、银行卡、现金等。
 * 余额不落库，统一由「初始余额 + 关联流水」实时算出，避免双写不一致。
 * 删除走软删除（isDeleted = true），已记账单的引用保持有效。
 */
@Entity(tableName = "asset")
data class Asset(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val initialBalanceCents: Long = 0,
    val initialDate: String,
    val isDeleted: Boolean = false
)

/**
 * 账单。金额一律整数分。
 * type: INCOME / EXPENSE / REFUND / EXCHANGE / TRANSFER
 * categoryL2 为空串时表示一级分类即叶子（入账/回冲/往来/转账为扁平分类）。
 *
 * v1.1 新增：
 * - [assetId]   关联的资产账户（转账时为「转出账户」）
 * - [toAssetId] 内部转账的「转入账户」，其他类型固定为 null
 *
 * v1.1.1 新增（Room v3）：
 * - [huiwangSubtype] 回往方向（全额退款/部分退款/代付/借出/收回/还款），非回往记录为 null；
 *   旧记录允许为空，由 LegacyHuiWangResolver 在读取时推断。
 * - [linkedTxId] 即 linkedBillId：退款关联的原出账账单；收回/还款可选关联原代付/借出。
 *   沿用现有列名，避免新增同义列、重复维护两套关联关系。
 */
@Entity(
    tableName = "tx",
    indices = [
        Index("accountId"),
        Index("date"),
        Index("type"),
        Index("linkedTxId"),
        Index("assetId"),
        Index("toAssetId")
    ]
)
data class Tx(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val accountId: Long,
    val type: String,
    val categoryL1: String,
    val categoryL2: String,
    val amountCents: Long,
    val date: String,
    val note: String = "",
    val linkedTxId: Long? = null,
    val assetId: Long? = null,
    val toAssetId: Long? = null,
    val huiwangSubtype: String? = null,
    val createdAt: Long,
    val updatedAt: Long
)

/**
 * 分类。内置分类不可删，只可隐藏（hidden=true）；自定义分类可增删改。
 * [hidden] 字段用于支持内置分类的隐藏而不删除。
 *
 * v1.1.1 新增 [defaultAssetId]：分类的默认资产账户，null 表示不绑定。
 * 不建硬外键；账户软删除时由仓库层统一置空。
 */
@Entity(
    tableName = "category",
    indices = [Index(value = ["type", "level"]), Index("parentId"), Index("defaultAssetId")]
)
data class Category(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: String,
    val level: Int,
    val parentId: Long? = null,
    val name: String,
    val icon: String = "",
    val sortOrder: Int = 0,
    val isBuiltin: Boolean = false,
    val hidden: Boolean = false,
    val defaultAssetId: Long? = null
)
