package com.budgetflow.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.budgetflow.app.domain.model.AccountType
import com.budgetflow.app.domain.model.CurrencyCode
import com.budgetflow.app.domain.model.RolloverMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities = [
        AccountEntity::class,
        CategoryEntity::class,
        TransactionEntity::class,
        LineItemEntity::class,
        BudgetEntity::class,
        RecurringBillEntity::class,
        SavingsGoalEntity::class,
        ReceivableEntity::class,
        PayableEntity::class,
        ShoppingListEntity::class,
        ShoppingListItemEntity::class
    ],
    version = 9,
    exportSchema = false
)
abstract class BudgetDatabase : RoomDatabase() {
    abstract fun budgetDao(): BudgetDao

    companion object {
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `payables` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `personName` TEXT NOT NULL,
                        `description` TEXT NOT NULL,
                        `currency` TEXT NOT NULL,
                        `totalOweCents` INTEGER NOT NULL,
                        `paidCents` INTEGER NOT NULL,
                        `isSettled` INTEGER NOT NULL,
                        `createdAtMillis` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `accounts` ADD COLUMN `isArchived` INTEGER NOT NULL DEFAULT 0")
            }
        }

        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `accounts` ADD COLUMN `showOnHome` INTEGER NOT NULL DEFAULT 1")
            }
        }

        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `categories` ADD COLUMN `parentCategoryId` INTEGER DEFAULT NULL")
            }
        }

        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `shopping_lists` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `name` TEXT NOT NULL,
                        `currency` TEXT NOT NULL,
                        `categoryId` INTEGER,
                        `createdAtMillis` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `shopping_list_items` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `listId` INTEGER NOT NULL,
                        `name` TEXT NOT NULL,
                        `quantity` INTEGER NOT NULL,
                        `estimatedUnitCents` INTEGER NOT NULL,
                        `actualUnitCents` INTEGER,
                        `isChecked` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_shopping_list_items_listId` ON `shopping_list_items` (`listId`)")
            }
        }

        private val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE budgets ADD COLUMN createdAtMillis INTEGER NOT NULL DEFAULT 0")
            }
        }

        fun build(context: Context): BudgetDatabase {
            lateinit var instance: BudgetDatabase
            instance = Room.databaseBuilder(
                context.applicationContext,
                BudgetDatabase::class.java,
                "budgetflow.db"
            ).addMigrations(MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9)
                .addCallback(object : Callback() {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    super.onCreate(db)
                    CoroutineScope(Dispatchers.IO).launch {
                        seedInitialData(instance.budgetDao())
                    }
                }
            }).build()
            return instance
        }

        private suspend fun seedInitialData(dao: BudgetDao) {
            // Seed only starter categories; wallets and budgets are created by the user.
            dao.upsertCategory(CategoryEntity(name = "Groceries", iconName = "shopping_cart", colorHex = 0xFF059669))
            dao.upsertCategory(CategoryEntity(name = "Dining", iconName = "restaurant", colorHex = 0xFFF59E0B))
            dao.upsertCategory(CategoryEntity(name = "Transport", iconName = "directions_bus", colorHex = 0xFF3B82F6))
            dao.upsertCategory(CategoryEntity(name = "Utilities", iconName = "bolt", colorHex = 0xFF8B5CF6))
            dao.upsertCategory(CategoryEntity(name = "Entertainment", iconName = "movie", colorHex = 0xFFEC4899))
        }
    }
}
