package com.kumquat.ledger.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * 显式迁移链。SQL 独立于 [AppDatabase] 放在纯 JVM 对象里，
 * 迁移测试可以直接用 sqlite-jdbc 执行同一份 SQL，避免测试与线上迁移各写一套。
 *
 * 规则：只新增可空列 / 索引，不重建表、不删除、不改写历史数据。
 */
object KumquatMigrations {

    /** 2 -> 3 的完整 SQL。顺序固定，测试会逐条执行。 */
    val V2_TO_V3_STATEMENTS: List<String> = listOf(
        "ALTER TABLE `category` ADD COLUMN `defaultAssetId` INTEGER",
        "ALTER TABLE `tx` ADD COLUMN `huiwangSubtype` TEXT",
        "CREATE INDEX IF NOT EXISTS `index_category_defaultAssetId` ON `category` (`defaultAssetId`)"
    )

    val MIGRATION_2_3: Migration = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            V2_TO_V3_STATEMENTS.forEach(db::execSQL)
        }
    }
}

/**
 * 数据库。v1 -> v2 加表 + 加列；v2 -> v3 加回往子类型与分类默认账户。
 * 严禁 fallbackToDestructiveMigration：老用户升级后账单必须原样保留。
 */
@Database(
    entities = [Account::class, Tx::class, Category::class, Asset::class],
    version = 3,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun accountDao(): AccountDao
    abstract fun txDao(): TxDao
    abstract fun categoryDao(): CategoryDao
    abstract fun assetDao(): AssetDao

    companion object {
        /**
         * 1 -> 2：
         * - 新建 asset 表（资产账户）
         * - tx 增加 assetId / toAssetId（可空，老账单自动为 NULL）
         * 索引与 Room 生成的 v2 schema 保持一致（tx 上的 4 个新索引 + asset 无索引）。
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `asset` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`name` TEXT NOT NULL, " +
                        "`initialBalanceCents` INTEGER NOT NULL, " +
                        "`initialDate` TEXT NOT NULL, " +
                        "`isDeleted` INTEGER NOT NULL)"
                )
                db.execSQL("ALTER TABLE `tx` ADD COLUMN `assetId` INTEGER")
                db.execSQL("ALTER TABLE `tx` ADD COLUMN `toAssetId` INTEGER")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_tx_assetId` ON `tx` (`assetId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_tx_toAssetId` ON `tx` (`toAssetId`)")
            }
        }

        @Volatile
        private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "kumquat.db"
                )
                    .addMigrations(MIGRATION_1_2, KumquatMigrations.MIGRATION_2_3)
                    .build()
                    .also { instance = it }
            }
    }
}
