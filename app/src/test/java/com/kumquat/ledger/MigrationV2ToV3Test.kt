package com.kumquat.ledger

import com.kumquat.ledger.data.KumquatMigrations
import java.sql.Connection
import java.sql.DriverManager
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Room Migration(2,3) 的 JVM 迁移测试。
 *
 * 不依赖模拟器：用真实 SQLite（sqlite-jdbc）建出 v2 结构、灌入旧数据，
 * 再执行与 [KumquatMigrations.MIGRATION_2_3] **完全相同**的 SQL，
 * 验证“列新增、数据不丢、关联不变”。Room 的 schema 校验由构建期导出的 3.json 兜底。
 */
class MigrationV2ToV3Test {

    private lateinit var db: Connection

    @Before
    fun setUp() {
        Class.forName("org.sqlite.JDBC")
        db = DriverManager.getConnection("jdbc:sqlite::memory:")
        createV2Schema()
        insertV2Data()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun createV2Schema() {
        val statements = listOf(
            "CREATE TABLE IF NOT EXISTS `account` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, " +
                "`coverType` TEXT NOT NULL, `coverValue` TEXT NOT NULL, " +
                "`createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL)",
            "CREATE TABLE IF NOT EXISTS `tx` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `accountId` INTEGER NOT NULL, " +
                "`type` TEXT NOT NULL, `categoryL1` TEXT NOT NULL, `categoryL2` TEXT NOT NULL, " +
                "`amountCents` INTEGER NOT NULL, `date` TEXT NOT NULL, `note` TEXT NOT NULL, " +
                "`linkedTxId` INTEGER, `assetId` INTEGER, `toAssetId` INTEGER, " +
                "`createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL)",
            "CREATE TABLE IF NOT EXISTS `category` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `type` TEXT NOT NULL, " +
                "`level` INTEGER NOT NULL, `parentId` INTEGER, `name` TEXT NOT NULL, " +
                "`icon` TEXT NOT NULL, `sortOrder` INTEGER NOT NULL, `isBuiltin` INTEGER NOT NULL, " +
                "`hidden` INTEGER NOT NULL)",
            "CREATE TABLE IF NOT EXISTS `asset` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, " +
                "`initialBalanceCents` INTEGER NOT NULL, `initialDate` TEXT NOT NULL, " +
                "`isDeleted` INTEGER NOT NULL)",
            "CREATE INDEX IF NOT EXISTS `index_tx_accountId` ON `tx` (`accountId`)",
            "CREATE INDEX IF NOT EXISTS `index_tx_date` ON `tx` (`date`)",
            "CREATE INDEX IF NOT EXISTS `index_tx_type` ON `tx` (`type`)",
            "CREATE INDEX IF NOT EXISTS `index_tx_linkedTxId` ON `tx` (`linkedTxId`)",
            "CREATE INDEX IF NOT EXISTS `index_tx_assetId` ON `tx` (`assetId`)",
            "CREATE INDEX IF NOT EXISTS `index_tx_toAssetId` ON `tx` (`toAssetId`)",
            "CREATE INDEX IF NOT EXISTS `index_category_type_level` ON `category` (`type`, `level`)",
            "CREATE INDEX IF NOT EXISTS `index_category_parentId` ON `category` (`parentId`)"
        )
        statements.forEach { execute(it) }
    }

    private fun insertV2Data() {
        execute(
            "INSERT INTO account (id, name, coverType, coverValue, createdAt, updatedAt) " +
                "VALUES (1, '日常', 'color', '#FFD1E8', 1000, 1000)"
        )
        execute(
            "INSERT INTO asset (id, name, initialBalanceCents, initialDate, isDeleted) VALUES " +
                "(1, '微信', 0, '2026-09-01', 0), (2, '银行卡', 20000, '2026-09-01', 0)"
        )
        execute(
            "INSERT INTO category (id, type, level, parentId, name, icon, sortOrder, isBuiltin, hidden) VALUES " +
                "(1, 'EXPENSE', 1, NULL, '饮食', 'food', 0, 1, 0), " +
                "(2, 'REFUND', 1, NULL, '部分退款', 'refund_part', 0, 1, 0), " +
                "(3, 'EXCHANGE', 1, NULL, '代付', 'ex_pay_for', 0, 1, 0), " +
                "(4, 'INCOME', 1, NULL, '红包', 'income_redpacket', 0, 1, 0)"
        )
        execute(
            "INSERT INTO tx (id, accountId, type, categoryL1, categoryL2, amountCents, date, note, " +
                "linkedTxId, assetId, toAssetId, createdAt, updatedAt) VALUES " +
                "(1, 1, 'EXPENSE', '饮食', '', 15000, '2026-09-12', '购物', NULL, 1, NULL, 1000, 1000), " +
                "(2, 1, 'REFUND', '部分退款', '', 10000, '2026-10-03', '退款', 1, 1, NULL, 1000, 1000), " +
                "(3, 1, 'EXCHANGE', '代付', '', 560, '2026-09-20', '帮带饭', NULL, 1, NULL, 1000, 1000), " +
                "(4, 1, 'EXCHANGE', '收回', '', 300, '2026-09-21', '收回', NULL, 1, NULL, 1000, 1000), " +
                "(5, 1, 'TRANSFER', '内部转账', '', 5000, '2026-09-22', '转账', NULL, 1, 2, 1000, 1000), " +
                "(6, 1, 'INCOME', '红包', '', 3000, '2026-09-23', '红包', NULL, 1, NULL, 1000, 1000)"
        )
        execute("PRAGMA user_version = 2")
    }

    private fun execute(sql: String) {
        db.createStatement().use { it.execute(sql) }
    }

    private fun queryLong(sql: String): Long =
        db.createStatement().use { st ->
            st.executeQuery(sql).use { rs ->
                assertTrue("查询无结果：$sql", rs.next())
                rs.getLong(1)
            }
        }

    private fun columnNames(table: String): Set<String> {
        val names = LinkedHashSet<String>()
        db.createStatement().use { st ->
            st.executeQuery("PRAGMA table_info(`$table`)").use { rs ->
                while (rs.next()) names.add(rs.getString("name"))
            }
        }
        return names
    }

    @Test
    fun migration_adds_columns_and_keeps_every_row() {
        val countsBefore = listOf("account", "asset", "category", "tx").associateWith {
            queryLong("SELECT COUNT(*) FROM `$it`")
        }
        val sumBefore = queryLong("SELECT SUM(amountCents) FROM tx")

        KumquatMigrations.V2_TO_V3_STATEMENTS.forEach { execute(it) }

        countsBefore.forEach { (table, count) ->
            assertEquals("表 $table 行数变化", count, queryLong("SELECT COUNT(*) FROM `$table`"))
        }
        assertEquals(33_860L, sumBefore)
        assertEquals("金额合计变化", sumBefore, queryLong("SELECT SUM(amountCents) FROM tx"))

        assertTrue("tx 缺少 huiwangSubtype", "huiwangSubtype" in columnNames("tx"))
        assertTrue("category 缺少 defaultAssetId", "defaultAssetId" in columnNames("category"))
        assertEquals(0L, queryLong("SELECT COUNT(*) FROM tx WHERE huiwangSubtype IS NOT NULL"))
        assertEquals(0L, queryLong("SELECT COUNT(*) FROM category WHERE defaultAssetId IS NOT NULL"))

        // 退款关联关系不变（linkedTxId 就是 linkedBillId）
        assertEquals(1L, queryLong("SELECT linkedTxId FROM tx WHERE id = 2"))
        assertEquals(15000L, queryLong("SELECT amountCents FROM tx WHERE id = 1"))
        assertEquals("2026-10-03", db.createStatement().use { st ->
            st.executeQuery("SELECT date FROM tx WHERE id = 2").use { rs ->
                rs.next()
                rs.getString(1)
            }
        })
    }

    private fun columnInfo(table: String, column: String): Pair<String, Int> {
        db.createStatement().use { st ->
            st.executeQuery("PRAGMA table_info(`$table`)").use { rs ->
                while (rs.next()) {
                    if (rs.getString("name") == column) {
                        return rs.getString("type") to rs.getInt("notnull")
                    }
                }
            }
        }
        error("列不存在：$table.$column")
    }

    @Test
    fun new_columns_are_nullable_with_expected_types() {
        KumquatMigrations.V2_TO_V3_STATEMENTS.forEach { execute(it) }

        assertEquals("TEXT" to 0, columnInfo("tx", "huiwangSubtype"))
        assertEquals("INTEGER" to 0, columnInfo("category", "defaultAssetId"))
    }

    @Test
    fun migration_creates_index_declared_by_entity() {
        KumquatMigrations.V2_TO_V3_STATEMENTS.forEach { execute(it) }

        val names = HashSet<String>()
        db.createStatement().use { st ->
            st.executeQuery("PRAGMA index_list(`category`)").use { rs ->
                while (rs.next()) names.add(rs.getString("name"))
            }
        }
        assertTrue("缺少 index_category_defaultAssetId：$names", "index_category_defaultAssetId" in names)
    }

    @Test
    fun new_columns_accept_new_values_after_migration() {
        KumquatMigrations.V2_TO_V3_STATEMENTS.forEach { execute(it) }

        execute("UPDATE category SET defaultAssetId = 2 WHERE id = 1")
        execute("UPDATE tx SET huiwangSubtype = 'REFUND_PARTIAL' WHERE id = 2")
        execute("UPDATE tx SET huiwangSubtype = 'ADVANCE_PAY' WHERE id = 3")

        assertEquals(2L, queryLong("SELECT defaultAssetId FROM category WHERE id = 1"))
        assertEquals(
            "REFUND_PARTIAL",
            db.createStatement().use { st ->
                st.executeQuery("SELECT huiwangSubtype FROM tx WHERE id = 2").use { rs ->
                    rs.next()
                    rs.getString(1)
                }
            }
        )
        assertEquals(1L, queryLong("SELECT COUNT(*) FROM tx WHERE huiwangSubtype = 'ADVANCE_PAY'"))
    }

    @Test
    fun migration_sql_is_non_destructive() {
        assertTrue(KumquatMigrations.V2_TO_V3_STATEMENTS.isNotEmpty())
        KumquatMigrations.V2_TO_V3_STATEMENTS.forEach { sql ->
            val normalized = sql.trim().uppercase()
            assertTrue("迁移只允许新增列/索引：$sql", normalized.startsWith("ALTER TABLE") || normalized.startsWith("CREATE INDEX"))
            listOf("DROP ", "DELETE ", "TRUNCATE", "INSERT ", "REPLACE ", "UPDATE ").forEach { forbidden ->
                assertFalse("迁移 SQL 含破坏性语句：$sql", normalized.contains(forbidden))
            }
        }
    }

    @Test
    fun v2_refund_remains_readable_after_migration() {
        KumquatMigrations.V2_TO_V3_STATEMENTS.forEach { execute(it) }

        // 旧退款仍能通过 linkedTxId 找到原出账，且旧分类名仍可用于展示“历史退款”
        val sql = "SELECT t.amountCents, r.amountCents, r.categoryL1 FROM tx r " +
            "JOIN tx t ON r.linkedTxId = t.id WHERE r.type = 'REFUND'"
        db.createStatement().use { st ->
            st.executeQuery(sql).use { rs ->
                assertTrue(rs.next())
                assertEquals(15_000L, rs.getLong(1))
                assertEquals(10_000L, rs.getLong(2))
                assertEquals("部分退款", rs.getString(3))
                assertNotNull(rs.getString(3))
            }
        }
    }
}
