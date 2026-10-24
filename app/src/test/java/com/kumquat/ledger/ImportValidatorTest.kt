package com.kumquat.ledger

import com.kumquat.ledger.data.AccountJson
import com.kumquat.ledger.data.CategoryJson
import com.kumquat.ledger.data.ImportValidator
import com.kumquat.ledger.data.LedgerCodec
import com.kumquat.ledger.data.LedgerFile
import com.kumquat.ledger.data.TxJson
import com.kumquat.ledger.domain.TxType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ImportValidatorTest {

    private fun validFile(): LedgerFile = LedgerFile(
        version = 1,
        exportedAt = "2026-10-20T12:00:00",
        accounts = listOf(AccountJson("日常", "color", "#FFD1E8")),
        transactions = listOf(
            TxJson("日常", "EXPENSE", "饮食", "正餐", 1500, "2026-10-20", "午饭", null),
            TxJson("日常", "REFUND", "部分退款", "", 500, "2026-10-20", "退款", 0)
        )
    )

    @Test
    fun valid_file_passes() {
        val preview = ImportValidator.validate(validFile())
        assertTrue(preview.ok)
        assertEquals(2, preview.txCount)
        assertEquals(2000L, preview.totalCents)
    }

    @Test
    fun wrong_version_rejected() {
        val preview = ImportValidator.validate(validFile().copy(version = 2))
        assertFalse(preview.ok)
    }

    @Test
    fun bad_date_rejected() {
        val file = validFile().copy(
            transactions = listOf(
                TxJson("日常", "EXPENSE", "饮食", "正餐", 1500, "2026-13-01", "", null)
            )
        )
        val preview = ImportValidator.validate(file)
        assertFalse(preview.ok)
        assertTrue(preview.errors.any { it.field == "date" })
    }

    @Test
    fun bad_type_rejected() {
        val file = validFile().copy(
            transactions = listOf(TxJson("日常", "BORROW", "饮食", "正餐", 1500, "2026-10-20", "", null))
        )
        val preview = ImportValidator.validate(file)
        assertFalse(preview.ok)
        assertTrue(preview.errors.any { it.field == "type" })
    }

    @Test
    fun transfer_requires_two_different_assets() {
        val missing = validFile().copy(
            transactions = listOf(
                TxJson("日常", "TRANSFER", "内部转账", "", 1500, "2026-10-20", "", null)
            )
        )
        val preview = ImportValidator.validate(missing)
        assertFalse(preview.ok)
        assertTrue(preview.errors.any { it.field == "assetName" })
        assertTrue(preview.errors.any { it.field == "toAssetName" })

        val same = validFile().copy(
            transactions = listOf(
                TxJson("日常", "TRANSFER", "内部转账", "", 1500, "2026-10-20", "", null, "微信", "微信")
            )
        )
        assertFalse(ImportValidator.validate(same).ok)

        val ok = validFile().copy(
            transactions = listOf(
                TxJson("日常", "TRANSFER", "内部转账", "", 1500, "2026-10-20", "", null, "微信", "银行卡")
            )
        )
        assertTrue(ImportValidator.validate(ok).ok)
        assertEquals(1500L, ImportValidator.validate(ok).totals[TxType.TRANSFER])
    }

    @Test
    fun non_positive_amount_rejected() {
        val file = validFile().copy(
            transactions = listOf(TxJson("日常", "EXPENSE", "饮食", "正餐", 0, "2026-10-20", "", null))
        )
        assertFalse(ImportValidator.validate(file).ok)
    }

    @Test
    fun refund_linked_to_income_is_imported_as_pending_with_warning() {
        val file = validFile().copy(
            transactions = listOf(
                TxJson("日常", "INCOME", "红包", "", 5000, "2026-10-20", "", null),
                TxJson("日常", "REFUND", "全额退款", "", 5000, "2026-10-20", "", 0)
            )
        )
        val preview = ImportValidator.validate(file)
        assertTrue(preview.ok)
        assertTrue(preview.warnings.any { it.field == "linkedIndex" })
    }

    @Test
    fun linked_index_out_of_range_becomes_pending_with_warning() {
        val file = validFile().copy(
            transactions = listOf(TxJson("日常", "EXPENSE", "饮食", "正餐", 1500, "2026-10-20", "", 5))
        )
        val preview = ImportValidator.validate(file)
        assertTrue(preview.ok)
        assertTrue(preview.warnings.any { it.field == "linkedIndex" })
    }

    @Test
    fun unknown_account_name_is_allowed_auto_created() {
        val file = validFile().copy(
            transactions = listOf(TxJson("新账本", "EXPENSE", "饮食", "正餐", 1500, "2026-10-20", "", null))
        )
        assertTrue(ImportValidator.validate(file).ok)
    }

    @Test
    fun json_roundtrip_is_strict() {
        val file = validFile()
        val text = LedgerCodec.encode(file)
        val decoded = LedgerCodec.decode(text).getOrNull()
        assertNotNull(decoded)
        assertEquals(file.accounts, decoded!!.accounts)
        assertEquals(file.transactions, decoded.transactions)
        assertEquals(TxType.EXPENSE.name, decoded.transactions[0].type)
    }

    @Test
    fun broken_json_reports_file_error() {
        val preview = ImportValidator.validateText("{ not json")
        assertFalse(preview.ok)
        assertEquals(-1, preview.errors.first().index)
    }

    @Test
    fun v110_json_without_new_fields_is_accepted() {
        val legacy = """
            {"version":1,"accounts":[{"name":"日常"}],
             "transactions":[{"accountName":"日常","type":"EXPENSE",
             "categoryL1":"饮食","amountCents":1500,"date":"2026-10-20"}]}
        """.trimIndent()
        val decoded = LedgerCodec.decode(legacy).getOrNull()
        assertNotNull(decoded)
        assertNull(decoded!!.transactions[0].huiwangSubtype)
        val preview = ImportValidator.validate(decoded)
        assertTrue(preview.ok)
    }

    @Test
    fun v111_optional_fields_roundtrip() {
        val file = validFile().copy(
            categories = listOf(CategoryJson("EXPENSE", 1, "饮食", null, "restaurant", "微信")),
            transactions = listOf(
                TxJson("日常", "EXPENSE", "饮食", "", 1500, "2026-10-20", "", null, null, null, null),
                TxJson(
                    "日常", "REFUND", "部分退款", "", 500, "2026-10-21", "", 0,
                    null, null, "REFUND_PARTIAL"
                )
            )
        )
        val decoded = LedgerCodec.decode(LedgerCodec.encode(file)).getOrNull()
        assertNotNull(decoded)
        assertEquals("微信", decoded!!.categories.single().defaultAssetName)
        assertEquals("REFUND_PARTIAL", decoded.transactions[1].huiwangSubtype)
        assertEquals(file.transactions, decoded.transactions)
    }

    @Test
    fun missing_default_asset_name_only_warns() {
        val file = validFile().copy(
            accounts = listOf(AccountJson("日常", "color", "#FFD1E8")),
            categories = listOf(CategoryJson("EXPENSE", 1, "饮食", null, "restaurant", "不存在账户"))
        )
        val preview = ImportValidator.validate(file)
        assertTrue(preview.ok)
        assertTrue(preview.warnings.any { it.field.endsWith("defaultAssetName") })
    }

    @Test
    fun mismatched_subtype_only_warns() {
        val file = validFile().copy(
            transactions = listOf(
                TxJson(
                    "日常", "REFUND", "部分退款", "", 500, "2026-10-20", "", null,
                    null, null, "ADVANCE_PAY"
                )
            )
        )
        val preview = ImportValidator.validate(file)
        assertTrue(preview.ok)
        assertTrue(preview.warnings.any { it.field == "huiwangSubtype" })
    }
}
