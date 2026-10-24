package com.kumquat.ledger.data

import com.kumquat.ledger.domain.HuiWangSubtype
import com.kumquat.ledger.domain.Money
import com.kumquat.ledger.domain.TxType
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.ResolverStyle

/**
 * 导入校验：纯 JVM 逻辑，不依赖 Android，可单元测试。
 * 解析/校验失败一律不写入任何数据（写入在 Repository 的事务里）。
 */
data class ImportError(
    val index: Int,       // -1 表示文件级错误；>=0 表示 transactions 下标
    val field: String,
    val message: String
) {
    fun display(): String =
        if (index >= 0) "第 ${index + 1} 条 · $field：$message" else "文件 · $field：$message"
}

data class ImportPreview(
    val file: LedgerFile?,
    val txCount: Int = 0,
    val accountCount: Int = 0,
    val categoryCount: Int = 0,
    val assetCount: Int = 0,
    val totals: Map<TxType, Long> = emptyMap(),
    val errors: List<ImportError> = emptyList(),
    /** 可继续导入但需要提示的问题（关联失效、默认账户找不到等）。 */
    val warnings: List<ImportError> = emptyList()
) {
    val ok: Boolean get() = file != null && errors.isEmpty()
    val totalCents: Long get() = totals.values.sum()
}

object ImportValidator {

    private val dateFormatter: DateTimeFormatter =
        DateTimeFormatter.ofPattern("uuuu-MM-dd").withResolverStyle(ResolverStyle.STRICT)

    private val colorRegex = Regex("^#[0-9A-Fa-f]{6}$")
    private val gradientRegex = Regex("^#[0-9A-Fa-f]{6},#[0-9A-Fa-f]{6}$")

    private const val TYPE_HINT = "INCOME/EXPENSE/REFUND/EXCHANGE/TRANSFER"

    fun isValidDate(raw: String): Boolean = try {
        LocalDate.parse(raw, dateFormatter)
        true
    } catch (_: Exception) {
        false
    }

    fun validate(file: LedgerFile?): ImportPreview {
        if (file == null) {
            return ImportPreview(
                file = null,
                errors = listOf(ImportError(-1, "JSON", "内容为空或不是合法的 JSON"))
            )
        }
        val errors = ArrayList<ImportError>()
        val warnings = ArrayList<ImportError>()

        // 1. 版本
        if (file.version != 1) {
            errors.add(ImportError(-1, "version", "不支持的版本 ${file.version}，当前仅支持 1"))
        }

        // 2. 账本
        if (file.accounts.isEmpty()) {
            errors.add(ImportError(-1, "accounts", "至少需要一个账本"))
        }
        val accountNames = HashSet<String>()
        file.accounts.forEachIndexed { i, acc ->
            val name = acc.name.trim()
            if (name.isEmpty()) {
                errors.add(ImportError(-1, "accounts[$i].name", "账本名不能为空"))
            } else if (!accountNames.add(name)) {
                errors.add(ImportError(-1, "accounts[$i].name", "账本名重复：$name"))
            }
            when (acc.coverType) {
                "color" -> if (!colorRegex.matches(acc.coverValue)) {
                    errors.add(ImportError(-1, "accounts[$i].coverValue", "纯色封面需形如 #RRGGBB"))
                }
                "gradient" -> if (!gradientRegex.matches(acc.coverValue)) {
                    errors.add(ImportError(-1, "accounts[$i].coverValue", "渐变封面需形如 #RRGGBB,#RRGGBB"))
                }
                else -> errors.add(
                    ImportError(-1, "accounts[$i].coverType", "coverType 只能是 color 或 gradient")
                )
            }
        }

        // 2.5 资产账户（v1.1，可缺省）
        val assetNames = HashSet<String>()
        file.assets.forEachIndexed { i, a ->
            val name = a.name.trim()
            if (name.isEmpty()) {
                errors.add(ImportError(-1, "assets[$i].name", "资产账户名不能为空"))
            } else if (!assetNames.add(name)) {
                errors.add(ImportError(-1, "assets[$i].name", "资产账户名重复：$name"))
            }
            if (a.initialBalanceCents < -99_999_999_999L || a.initialBalanceCents > 99_999_999_999L) {
                errors.add(ImportError(-1, "assets[$i].initialBalanceCents", "初始余额超出范围"))
            }
            if (a.initialDate.isNotBlank() && !isValidDate(a.initialDate)) {
                errors.add(ImportError(-1, "assets[$i].initialDate", "初始日期需为 yyyy-MM-dd"))
            }
        }

        // 3. 分类
        val l1Keys = HashSet<String>()   // type|name
        val l2Keys = HashSet<String>()   // type|parent|name
        file.categories.forEachIndexed { i, c ->
            val type = TxType.parse(c.type)
            if (type == null) {
                errors.add(ImportError(-1, "categories[$i].type", "类型只能是 $TYPE_HINT"))
                return@forEachIndexed
            }
            if (c.level != 1 && c.level != 2) {
                errors.add(ImportError(-1, "categories[$i].level", "level 只能是 1 或 2"))
                return@forEachIndexed
            }
            val name = c.name.trim()
            if (name.isEmpty()) {
                errors.add(ImportError(-1, "categories[$i].name", "分类名不能为空"))
                return@forEachIndexed
            }
            val defaultAssetName = c.defaultAssetName?.trim().orEmpty()
            if (defaultAssetName.isNotEmpty() && defaultAssetName !in assetNames) {
                warnings.add(
                    ImportError(
                        -1,
                        "categories[$i].defaultAssetName",
                        "找不到资产账户 $defaultAssetName，将按未绑定导入"
                    )
                )
            }
            if (c.level == 1) {
                if (!l1Keys.add("${type.name}|$name")) {
                    errors.add(ImportError(-1, "categories[$i].name", "一级分类重复：$name"))
                }
            } else {
                val parent = c.parentName?.trim().orEmpty()
                if (parent.isEmpty()) {
                    errors.add(ImportError(-1, "categories[$i].parentName", "二级分类必须提供 parentName"))
                } else if (!l1Keys.contains("${type.name}|$parent")) {
                    // 允许引用本批次稍后声明的一级分类：先记下，最后统一补查
                    if (file.categories.none { it.level == 1 && it.name.trim() == parent && TxType.parse(it.type) == type }) {
                        errors.add(ImportError(-1, "categories[$i].parentName", "找不到一级分类：$parent"))
                    }
                }
                if (!l2Keys.add("${type.name}|$parent|$name")) {
                    errors.add(ImportError(-1, "categories[$i].name", "二级分类重复：$parent / $name"))
                }
            }
        }

        // 4. 账单
        val totals = LinkedHashMap<TxType, Long>()
        file.transactions.forEachIndexed { i, t ->
            val type = TxType.parse(t.type)
            if (type == null) {
                errors.add(ImportError(i, "type", "类型只能是 $TYPE_HINT"))
            } else {
                totals[type] = (totals[type] ?: 0L) + t.amountCents
            }
            if (t.accountName.trim().isEmpty()) {
                errors.add(ImportError(i, "accountName", "账本名不能为空"))
            }
            if (t.amountCents <= 0) {
                errors.add(ImportError(i, "amountCents", "金额必须是大于 0 的整数分"))
            } else if (t.amountCents > 99_999_999_999L) {
                errors.add(ImportError(i, "amountCents", "金额超出上限"))
            }
            if (!isValidDate(t.date)) {
                errors.add(ImportError(i, "date", "日期需为 yyyy-MM-dd，且是真实存在的日期"))
            }
            if (t.categoryL1.trim().isEmpty()) {
                errors.add(ImportError(i, "categoryL1", "一级分类不能为空"))
            }
            if (t.type.trim().uppercase() == TxType.TRANSFER.name) {
                val from = t.assetName?.trim().orEmpty()
                val to = t.toAssetName?.trim().orEmpty()
                if (from.isEmpty()) {
                    errors.add(ImportError(i, "assetName", "内部转账必须指定转出账户（assetName）"))
                }
                if (to.isEmpty()) {
                    errors.add(ImportError(i, "toAssetName", "内部转账必须指定转入账户（toAssetName）"))
                }
                if (from.isNotEmpty() && from == to) {
                    errors.add(ImportError(i, "toAssetName", "转出与转入账户不能相同"))
                }
            }
            val linked = t.linkedIndex
            if (linked != null) {
                when {
                    linked !in file.transactions.indices -> warnings.add(
                        ImportError(i, "linkedIndex", "找不到下标为 $linked 的账单，将按待关联导入")
                    )
                    linked == i -> warnings.add(
                        ImportError(i, "linkedIndex", "不能关联自己，将按待关联导入")
                    )
                    else -> {
                        val target = TxType.parse(file.transactions[linked].type)
                        if (type == TxType.REFUND && target != TxType.EXPENSE) {
                            warnings.add(
                                ImportError(i, "linkedIndex", "退款只能关联出账，将按待关联导入")
                            )
                        }
                        if (target == TxType.REFUND) {
                            warnings.add(
                                ImportError(i, "linkedIndex", "不能关联到退款账单，将按待关联导入")
                            )
                        }
                    }
                }
            }
            val subtype = HuiWangSubtype.parse(t.huiwangSubtype)
            if (subtype != null) {
                val mismatched = (type == TxType.REFUND && !subtype.isRefund) ||
                    (type == TxType.EXCHANGE && subtype.isRefund) ||
                    (type != TxType.REFUND && type != TxType.EXCHANGE)
                if (mismatched) {
                    warnings.add(
                        ImportError(i, "huiwangSubtype", "回往方向与类型不匹配，将按旧记录解析")
                    )
                }
            }
        }
        if (file.transactions.isEmpty()) {
            errors.add(ImportError(-1, "transactions", "没有可导入的账单"))
        }

        return ImportPreview(
            file = file,
            txCount = file.transactions.size,
            accountCount = file.accounts.size,
            categoryCount = file.categories.size,
            assetCount = file.assets.size,
            totals = totals,
            errors = errors,
            warnings = warnings
        )
    }

    fun validateText(text: String): ImportPreview {
        val decoded = LedgerCodec.decode(text)
        return decoded.fold(
            onSuccess = { validate(it) },
            onFailure = { e ->
                ImportPreview(
                    file = null,
                    errors = listOf(ImportError(-1, "JSON", "解析失败：${e.message ?: "格式错误"}"))
                )
            }
        )
    }

    fun totalsText(totals: Map<TxType, Long>): String = buildList {
        totals[TxType.INCOME]?.takeIf { it > 0 }?.let { add("入账 ${Money.format(it)}") }
        totals[TxType.EXPENSE]?.takeIf { it > 0 }?.let { add("出账 ${Money.format(it)}") }
        val huiWang = (totals[TxType.REFUND] ?: 0L) + (totals[TxType.EXCHANGE] ?: 0L)
        if (huiWang > 0L) add("回往 ${Money.format(huiWang)}")
        totals[TxType.TRANSFER]?.takeIf { it > 0 }?.let { add("转账 ${Money.format(it)}") }
    }.joinToString(" · ")
}
