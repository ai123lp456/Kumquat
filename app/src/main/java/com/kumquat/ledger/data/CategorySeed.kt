package com.kumquat.ledger.data

import com.kumquat.ledger.domain.TxType

/**
 * 内置分类。
 * 入账 / 回冲 / 往来 / 转账为扁平分类（无二级）；出账带二级分类。
 */
object CategorySeed {

    /** 系统兜底分类：删除分类时账单移到这里，不可删除/重命名。 */
    const val UNCATEGORIZED = "未分类"

    data class Group(
        val name: String,
        val icon: String,
        val children: List<Pair<String, String>> = emptyList()
    )

    val groups: Map<TxType, List<Group>> = mapOf(
        TxType.INCOME to listOf(
            Group("常规", "income_regular"),
            Group("任务", "income_task"),
            Group("红包", "income_redpacket"),
            Group("补贴", "income_subsidy"),
            Group("奖励", "income_reward"),
            Group("生活费", "income_living"),
            Group("其他", "income_other")
        ),
        TxType.EXPENSE to listOf(
            Group(
                "饮食", "food", listOf(
                    "正餐" to "food_meal",
                    "垃圾零食" to "food_snack",
                    "健康零食" to "food_snack_healthy",
                    "垃圾饮料" to "food_drink",
                    "健康饮料" to "food_drink_healthy",
                    "水果" to "food_fruit",
                    "其他饮食" to "food_other"
                )
            ),
            Group(
                "购物", "shopping", listOf(
                    "服饰鞋帽" to "shop_clothes",
                    "数码电器" to "shop_device",
                    "学习用品" to "shop_study",
                    "生活用品" to "shop_daily",
                    "卫生" to "shop_hygiene"
                )
            ),
            Group("其他", "expense_other")
        ),
        TxType.REFUND to listOf(
            Group("全额退款", "refund_full"),
            Group("部分退款", "refund_part"),
            Group("其他回冲", "refund_other")
        ),
        TxType.EXCHANGE to listOf(
            Group("代付", "ex_pay_for"),
            Group("借出", "ex_lend"),
            Group("借入", "ex_borrow"),
            Group("还款", "ex_repay"),
            Group("收回", "ex_collect"),
            Group("其他往来", "ex_other")
        ),
        // v1.1 新增：内部转账只有一个"分类"，真正的信息在转出/转入资产账户上
        TxType.TRANSFER to listOf(
            Group("内部转账", "transfer")
        )
    )

    /** 把内置分类写入空库。返回写入条数；库里已有分类则直接返回 0。 */
    suspend fun seed(dao: CategoryDao): Int {
        if (dao.count() > 0) return 0
        return insertGroups(dao, groups)
    }

    /**
     * 老用户升级（v1.0 -> v1.1）：v1.0 的分类表非空，[seed] 会直接跳过，
     * 导致「转账」没有可选分类、无法记账。这里只补 TRANSFER 这一组，幂等。
     */
    suspend fun ensureTransferSeeded(dao: CategoryDao): Int {
        val transferGroups = groups[TxType.TRANSFER].orEmpty()
        if (transferGroups.isEmpty()) return 0
        if (dao.level1(TxType.TRANSFER.name).isNotEmpty()) return 0
        return insertGroups(dao, mapOf(TxType.TRANSFER to transferGroups))
    }

    /**
     * 补「未分类」兜底分类（入账/出账各一条），幂等。
     * v1.1.0 升级和新建库都会调用；删除分类时账单统一移到这里。
     */
    suspend fun ensureUncategorizedSeeded(dao: CategoryDao): Int {
        var inserted = 0
        listOf(TxType.INCOME, TxType.EXPENSE).forEach { type ->
            if (dao.findByName(type.name, 1, UNCATEGORIZED, null) == null) {
                dao.insert(
                    Category(
                        type = type.name,
                        level = 1,
                        parentId = null,
                        name = UNCATEGORIZED,
                        icon = "category",
                        sortOrder = 999,
                        isBuiltin = true
                    )
                )
                inserted++
            }
        }
        return inserted
    }

    private suspend fun insertGroups(dao: CategoryDao, source: Map<TxType, List<Group>>): Int {
        var inserted = 0
        source.forEach { (type, list) ->
            list.forEachIndexed { index, g ->
                val parentId = dao.insert(
                    Category(
                        type = type.name,
                        level = 1,
                        parentId = null,
                        name = g.name,
                        icon = g.icon,
                        sortOrder = index,
                        isBuiltin = true
                    )
                )
                inserted++
                g.children.forEachIndexed { childIndex, child ->
                    dao.insert(
                        Category(
                            type = type.name,
                            level = 2,
                            parentId = parentId,
                            name = child.first,
                            icon = child.second,
                            sortOrder = childIndex,
                            isBuiltin = true
                        )
                    )
                    inserted++
                }
            }
        }
        return inserted
    }
}
