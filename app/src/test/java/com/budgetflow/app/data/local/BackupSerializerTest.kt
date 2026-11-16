package com.budgetflow.app.data.local

import com.budgetflow.app.domain.DomainConstants
import com.budgetflow.app.domain.model.AccountType
import com.budgetflow.app.domain.model.CurrencyCode
import com.budgetflow.app.domain.model.RolloverMode
import com.budgetflow.app.domain.model.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

class BackupSerializerTest {

    @Test
    fun `toJson and fromJson round-trip full BackupSnapshot faithfully`() {
        val snapshot = BackupSnapshot(
            accounts = listOf(
                AccountEntity(1L, "Cash", AccountType.CASH.name, CurrencyCode.USD.name, 15000L, isArchived = false, showOnHome = true),
                AccountEntity(2L, "EcoCash", AccountType.MOBILE_MONEY.name, CurrencyCode.ZWG.name, 250000L, isArchived = true, showOnHome = false)
            ),
            categories = listOf(
                CategoryEntity(10L, "Food", "restaurant", 0xFF059669L, isArchived = false, parentCategoryId = null),
                CategoryEntity(11L, "Groceries", "shopping_cart", 0xFF3B82F6L, isArchived = true, parentCategoryId = 10L)
            ),
            transactions = listOf(
                TransactionEntity(
                    id = 100L,
                    accountId = 1L,
                    targetAccountId = null,
                    categoryId = 11L,
                    type = TransactionType.EXPENSE.name,
                    currency = CurrencyCode.USD.name,
                    amountCents = 2450L,
                    exchangeRateUsed = "1",
                    merchant = "Fresh Mart",
                    note = "Weekly groceries",
                    receiptUri = "content://receipts/100",
                    timestampMillis = 1_700_000_000_000L
                ),
                TransactionEntity(
                    id = 101L,
                    accountId = 1L,
                    targetAccountId = 2L,
                    categoryId = null,
                    type = TransactionType.TRANSFER.name,
                    currency = CurrencyCode.USD.name,
                    amountCents = 5000L,
                    exchangeRateUsed = "26.50",
                    merchant = "Cash -> EcoCash",
                    note = "",
                    receiptUri = null,
                    timestampMillis = 1_700_000_100_000L
                )
            ),
            lineItems = listOf(
                LineItemEntity(1000L, 100L, "Milk", 250L),
                LineItemEntity(1001L, 100L, "Bread", 150L)
            ),
            budgets = listOf(
                BudgetEntity(20L, 10L, CurrencyCode.USD.name, 50000L, RolloverMode.ROLLOVER_SURPLUS.name, 85, 1_699_000_000_000L)
            ),
            bills = listOf(
                RecurringBillEntity(30L, "Rent", 40000L, CurrencyCode.USD.name, 10L, 1, isPaidThisMonth = true, autoLog = true),
                RecurringBillEntity(31L, "Water", 1500L, CurrencyCode.ZWG.name, null, 15, isPaidThisMonth = false, autoLog = false)
            ),
            goals = listOf(
                SavingsGoalEntity(40L, "Emergency Fund", CurrencyCode.GBP.name, 100000L, 25000L)
            ),
            receivables = listOf(
                ReceivableEntity(50L, "Alice", "Lunch", CurrencyCode.ZAR.name, 20000L, 5000L, isSettled = false, createdAtMillis = 1_700_000_200_000L)
            ),
            payables = listOf(
                PayableEntity(60L, "Bob", "Tickets", CurrencyCode.EUR.name, 12000L, 12000L, isSettled = true, createdAtMillis = 1_700_000_300_000L)
            ),
            shoppingLists = listOf(
                ShoppingListEntity(70L, "Weekend", CurrencyCode.BWP.name, 11L, 1_700_000_400_000L)
            ),
            shoppingListItems = listOf(
                ShoppingListItemEntity(80L, 70L, "Apples", 3, 500L, 450L, isChecked = true),
                ShoppingListItemEntity(81L, 70L, "Juice", 1, 300L, null, isChecked = false)
            )
        )

        val json = BackupSerializer.toJson(snapshot, nowMillis = 1_700_000_500_000L)
        val restored = BackupSerializer.fromJson(json, nowMillis = 1_700_000_999_999L)

        assertEquals(snapshot, restored)
    }

    @Test
    fun `toJson and fromJson handle empty snapshot`() {
        val empty = BackupSnapshot(
            accounts = emptyList(),
            categories = emptyList(),
            transactions = emptyList(),
            lineItems = emptyList(),
            budgets = emptyList(),
            bills = emptyList(),
            goals = emptyList(),
            receivables = emptyList(),
            payables = emptyList()
        )
        val json = BackupSerializer.toJson(empty, nowMillis = 1234L)
        val restored = BackupSerializer.fromJson(json, nowMillis = 5678L)
        assertEquals(empty, restored)
    }

    @Test
    fun `fromJson applies defaults for missing optional fields and missing arrays`() {
        val now = 1_700_123_456_000L
        val minimalJson = """
            {
              "formatVersion": 1,
              "accounts": [
                { "id": 1, "name": "Wallet", "type": "CASH", "currency": "USD", "balanceCents": 100 }
              ],
              "budgets": [
                { "id": 2, "categoryId": 3, "currency": "USD", "limitCents": 5000 }
              ],
              "receivables": [
                { "id": 4, "personName": "Sam", "currency": "USD", "totalOwedCents": 1000 }
              ],
              "payables": [
                { "id": 5, "personName": "Pat", "currency": "USD", "totalOweCents": 2000 }
              ],
              "shoppingLists": [
                { "id": 6, "name": "Groceries", "currency": "USD" }
              ],
              "shoppingListItems": [
                { "id": 7, "listId": 6, "name": "Milk" }
              ]
            }
        """.trimIndent()

        val restored = BackupSerializer.fromJson(minimalJson, nowMillis = now)

        assertEquals(1, restored.accounts.size)
        assertEquals(false, restored.accounts.single().isArchived)
        assertEquals(true, restored.accounts.single().showOnHome)

        val budget = restored.budgets.single()
        assertEquals("RESET_MONTHLY", budget.rolloverMode)
        assertEquals(DomainConstants.DEFAULT_ALERT_THRESHOLD_PCT, budget.alertThresholdPct)
        assertEquals(0L, budget.createdAtMillis)

        val receivable = restored.receivables.single()
        assertEquals("", receivable.description)
        assertEquals(0L, receivable.paidBackCents)
        assertEquals(false, receivable.isSettled)
        assertEquals(now, receivable.createdAtMillis)

        val payable = restored.payables.single()
        assertEquals("", payable.description)
        assertEquals(0L, payable.paidCents)
        assertEquals(false, payable.isSettled)
        assertEquals(now, payable.createdAtMillis)

        val list = restored.shoppingLists.single()
        assertEquals(null, list.categoryId)
        assertEquals(now, list.createdAtMillis)

        val item = restored.shoppingListItems.single()
        assertEquals(1, item.quantity)
        assertEquals(0L, item.estimatedUnitCents)
        assertEquals(null, item.actualUnitCents)
        assertEquals(false, item.isChecked)
        assertTrue(restored.categories.isEmpty())
        assertTrue(restored.transactions.isEmpty())
    }

    @Test
    fun `fromJson rejects unsupported formatVersion`() {
        assertThrows(IllegalArgumentException::class.java) {
            BackupSerializer.fromJson("""{"formatVersion": 99}""")
        }
        assertThrows(IllegalArgumentException::class.java) {
            BackupSerializer.fromJson("""{}""")
        }
    }

    @Test
    fun `malformed enum strings in backup deserialize safely to domain defaults`() {
        val json = """
            {
              "formatVersion": 1,
              "accounts": [
                { "id": 1, "name": "Legacy", "type": "CRYPTO_VAULT", "currency": "BTC", "balanceCents": 500 }
              ],
              "transactions": [
                {
                  "id": 2,
                  "accountId": 1,
                  "type": "REFUND_UNKNOWN",
                  "currency": "XYZ",
                  "amountCents": 100,
                  "exchangeRateUsed": "-5.0",
                  "timestampMillis": 1000
                }
              ],
              "budgets": [
                { "id": 3, "categoryId": 1, "currency": "NOPE", "limitCents": 1000, "rolloverMode": "UNKNOWN_MODE" }
              ]
            }
        """.trimIndent()

        val restored = BackupSerializer.fromJson(json)

        val account = restored.accounts.single().toDomain()
        assertEquals(AccountType.CASH, account.type)
        assertEquals(CurrencyCode.USD, account.currency)

        val tx = TransactionWithItems(restored.transactions.single(), emptyList()).toDomain()
        assertEquals(TransactionType.EXPENSE, tx.type)
        assertEquals(CurrencyCode.USD, tx.currency)
        assertEquals(BigDecimal.ONE, tx.exchangeRateUsed)

        val budget = restored.budgets.single().toDomain()
        assertEquals(CurrencyCode.USD, budget.currency)
        assertEquals(RolloverMode.RESET_MONTHLY, budget.rolloverMode)
    }
}
