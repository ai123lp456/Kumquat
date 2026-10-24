package com.kumquat.ledger.data

import com.kumquat.ledger.domain.TxType

/**
 * 「生成提示词」：给 AI 的导入格式说明。用户复制这段文字给任意 AI，
 * AI 按格式输出 JSON 后，粘贴回导入框即可校验导入。
 *
 * v1.1：
 * - 生成时自动把导入框里用户写的原文嵌进模板（省去手动粘贴）
 * - 修正 JSON 示例：categories 必须同时给出一级与二级（二级带 parentName）
 */
object PromptText {

    fun build(userText: String = ""): String {
        val categories = buildString {
            listOf(TxType.INCOME, TxType.EXPENSE).forEach { type ->
                appendLine("[${type.name} ${type.label}]")
                CategorySeed.groups[type]?.forEach { g ->
                    if (g.children.isEmpty()) {
                        appendLine("- L1: ${g.name}")
                    } else {
                        appendLine("- L1: ${g.name}（二级：${g.children.joinToString("、") { it.first }}）")
                    }
                }
                appendLine()
            }
        }

        val userBlock = userText.trim().let { raw ->
            if (raw.isEmpty()) {
                "（无：我还没写描述，请按下面的格式等我补充。）"
            } else {
                raw
            }
        }

        return """
请把下面【我的原始描述】里提到的账单，整理成这个 JSON 结构，并直接输出 JSON 本体（不要解释、不要代码块以外的文字）。

【我的原始描述】
$userBlock

【硬性要求】
1. 顶层字段：version（固定 1）、exportedAt（ISO 时间字符串，可留空）、accounts、assets、categories、transactions。
2. 金额单位一律是「整数分」，不要写小数、不要带货币符号。例如 15.5 元写成 1550。
3. 日期格式固定 yyyy-MM-dd，必须是真实存在的日期。
4. UI 只有四类：入账、出账、回往、转账。底层 type 仍是五个值之一：INCOME、EXPENSE、REFUND、EXCHANGE、TRANSFER；回往必须额外写 huiwangSubtype：REFUND_FULL、REFUND_PARTIAL、ADVANCE_PAY、LEND、COLLECT、REPAY。
5. 账本用 accountName 表示；不存在时会自动新建。
6. transactions 里每条的 linkedIndex 可为 null；退款必须填原出账在 transactions 数组中的下标（从 0 开始），REFUND 只能关联 EXPENSE。收回/还款可选关联原代付/借出。
7. 出账如果有多级分类：categoryL1 写一级（如 饮食），categoryL2 写二级（如 正餐）；入账的一级分类 categoryL2 留空；回往/转账不使用普通分类，categoryL2 留空字符串。
8. categories 只列出入账/出账分类，必须把**一级和二级都列出来**：一级只写 name，二级必须带 parentName；可选 defaultAssetName（默认资产账户名）。
9. 资产账户在 assets 里声明（name、initialBalanceCents、initialDate）；账单可用 assetName 关联资产账户。
10. 内部转账（TRANSFER）：categoryL1 固定写「内部转账」，assetName 写转出账户，toAssetName 写转入账户，两者不能相同；转账不计入收支统计。
11. 退款只冲减原出账，不记收入；代付/借出记回往负项，收回/还款记正项；不要编造我没说的账单。
12. 不确定的字段留空字符串或 null。

【默认分类（可自定义，但优先使用这些名字）】
$categories
【JSON 示例】
{
  "version": 1,
  "exportedAt": "2026-10-20T12:00:00",
  "accounts": [{"name": "日常", "coverType": "color", "coverValue": "#FFD1E8"}],
  "assets": [{"name": "微信零钱", "initialBalanceCents": 100000, "initialDate": "2026-10-01"}],
  "categories": [{"type": "EXPENSE", "level": 1, "name": "饮食", "defaultAssetName": "微信零钱"}, {"type": "EXPENSE", "level": 2, "name": "正餐", "parentName": "饮食"}],
  "transactions": [
    {
      "accountName": "日常",
      "type": "EXPENSE",
      "categoryL1": "饮食",
      "categoryL2": "正餐",
      "amountCents": 1500,
      "date": "2026-10-20",
      "note": "午饭",
      "linkedIndex": null,
      "assetName": "微信零钱",
      "toAssetName": null,
      "huiwangSubtype": null
    }
  ]
}
""".trimIndent()
    }
}
