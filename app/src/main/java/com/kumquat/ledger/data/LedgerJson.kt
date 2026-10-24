package com.kumquat.ledger.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * 导入导出的 JSON 结构定义。
 *
 * 兼容性原则：只**新增**可缺省字段，version 仍为 1。
 * - v1.1 新增 assets / transactions[].assetName / toAssetName；
 * - v1.1.1 新增 categories[].defaultAssetName / transactions[].huiwangSubtype。
 * 老版本 App 导入新文件时忽略未知字段（ignoreUnknownKeys = true），
 * 新版本 App 也能导入老文件（缺失字段按 null 处理）。
 *
 * 说明：JSON 里的 linkedIndex 就是 linkedBillId，使用“当前文件内下标”
 * 而不是数据库自增 id，导入时才能安全重映射。
 */
@Serializable
data class LedgerFile(
    val version: Int = 1,
    val exportedAt: String = "",
    val accounts: List<AccountJson> = emptyList(),
    val categories: List<CategoryJson> = emptyList(),
    val assets: List<AssetJson> = emptyList(),
    val transactions: List<TxJson> = emptyList()
)

@Serializable
data class AccountJson(
    val name: String,
    val coverType: String = "color",
    val coverValue: String = "#FFD1E8"
)

/** 资产账户。initialDate 为空时按导出日期/今天处理。 */
@Serializable
data class AssetJson(
    val name: String,
    val initialBalanceCents: Long = 0,
    val initialDate: String = "",
    val deleted: Boolean = false
)

@Serializable
data class CategoryJson(
    val type: String,
    val level: Int,
    val name: String,
    val parentName: String? = null,
    val icon: String? = null,
    /** 分类默认资产账户名；为空表示不绑定。导入时按名称重映射，找不到则置空。 */
    val defaultAssetName: String? = null
)

@Serializable
data class TxJson(
    val accountName: String = "",
    val type: String = "",
    val categoryL1: String = "",
    val categoryL2: String = "",
    val amountCents: Long = 0,
    val date: String = "",
    val note: String = "",
    val linkedIndex: Int? = null,
    /** 关联资产账户名（转账时为转出账户）。 */
    val assetName: String? = null,
    /** 内部转账的转入账户名，其他类型为 null。 */
    val toAssetName: String? = null,
    /** 回往方向；旧文件/旧版本缺失时为 null，由 LegacyHuiWangResolver 解析。 */
    val huiwangSubtype: String? = null
)

object LedgerCodec {

    val json: Json = Json {
        prettyPrint = true
        encodeDefaults = true
        ignoreUnknownKeys = true
        isLenient = false
    }

    fun encode(file: LedgerFile): String = json.encodeToString(file)

    fun decode(text: String): Result<LedgerFile> = runCatching { json.decodeFromString<LedgerFile>(text.trim()) }
}
