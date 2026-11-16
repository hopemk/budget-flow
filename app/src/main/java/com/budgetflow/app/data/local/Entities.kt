package com.budgetflow.app.data.local

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation
import com.budgetflow.app.domain.model.Account
import com.budgetflow.app.domain.model.AccountType
import com.budgetflow.app.domain.model.Budget
import com.budgetflow.app.domain.model.Category
import com.budgetflow.app.domain.model.CurrencyCode
import com.budgetflow.app.domain.model.LineItem
import com.budgetflow.app.domain.model.Payable
import com.budgetflow.app.domain.model.Receivable
import com.budgetflow.app.domain.model.RecurringBill
import com.budgetflow.app.domain.model.RolloverMode
import com.budgetflow.app.domain.model.SavingsGoal
import com.budgetflow.app.domain.model.ShoppingList
import com.budgetflow.app.domain.model.ShoppingListItem
import com.budgetflow.app.domain.model.Transaction
import com.budgetflow.app.domain.model.TransactionType
import java.math.BigDecimal

@Entity(tableName = "accounts")
data class AccountEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    val type: String,
    val currency: String,
    val balanceCents: Long,
    val isArchived: Boolean = false,
    val showOnHome: Boolean = true
)

@Entity(tableName = "categories")
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    val iconName: String,
    val colorHex: Long,
    val isArchived: Boolean = false,
    val parentCategoryId: Long? = null
)

@Entity(
    tableName = "transactions",
    indices = [Index("accountId"), Index("categoryId"), Index("timestampMillis")]
)
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val accountId: Long,
    val targetAccountId: Long?,
    val categoryId: Long?,
    val type: String,
    val currency: String,
    val amountCents: Long,
    val exchangeRateUsed: String,
    val merchant: String,
    val note: String,
    val receiptUri: String?,
    val timestampMillis: Long
)

@Entity(
    tableName = "line_items",
    foreignKeys = [
        ForeignKey(
            entity = TransactionEntity::class,
            parentColumns = ["id"],
            childColumns = ["transactionId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("transactionId")]
)
data class LineItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val transactionId: Long,
    val name: String,
    val amountCents: Long
)

data class TransactionWithItems(
    @Embedded val transaction: TransactionEntity,
    @Relation(parentColumn = "id", entityColumn = "transactionId")
    val items: List<LineItemEntity>
)

@Entity(tableName = "budgets", indices = [Index(value = ["categoryId"], unique = true)])
data class BudgetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val categoryId: Long,
    val currency: String,
    val limitCents: Long,
    val rolloverMode: String,
    val alertThresholdPct: Int,
    val createdAtMillis: Long = 0L
)

@Entity(tableName = "recurring_bills")
data class RecurringBillEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    val amountCents: Long,
    val currency: String,
    val categoryId: Long?,
    val dueDayOfMonth: Int,
    val isPaidThisMonth: Boolean,
    val autoLog: Boolean
)

@Entity(tableName = "savings_goals")
data class SavingsGoalEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    val currency: String,
    val targetCents: Long,
    val currentCents: Long
)

@Entity(tableName = "receivables")
data class ReceivableEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val personName: String,
    val description: String,
    val currency: String,
    val totalOwedCents: Long,
    val paidBackCents: Long,
    val isSettled: Boolean,
    val createdAtMillis: Long
)

@Entity(tableName = "shopping_lists")
data class ShoppingListEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    val currency: String,
    val categoryId: Long?,
    val createdAtMillis: Long
)

@Entity(
    tableName = "shopping_list_items",
    indices = [Index("listId")]
)
data class ShoppingListItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val listId: Long,
    val name: String,
    val quantity: Int,
    val estimatedUnitCents: Long,
    val actualUnitCents: Long?,
    val isChecked: Boolean
)

data class ShoppingListWithItems(
    @Embedded val list: ShoppingListEntity,
    @Relation(parentColumn = "id", entityColumn = "listId")
    val items: List<ShoppingListItemEntity>
)

@Entity(tableName = "payables")
data class PayableEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val personName: String,
    val description: String,
    val currency: String,
    val totalOweCents: Long,
    val paidCents: Long,
    val isSettled: Boolean,
    val createdAtMillis: Long
)

private inline fun <reified T : Enum<T>> safeEnumValueOf(value: String, default: T): T =
    runCatching { enumValueOf<T>(value) }.getOrDefault(default)

private fun safePositiveBigDecimal(raw: String, default: BigDecimal = BigDecimal.ONE): BigDecimal =
    runCatching { BigDecimal(raw) }.getOrNull()?.takeIf { it > BigDecimal.ZERO } ?: default

fun AccountEntity.toDomain() = Account(
    id = id,
    name = name,
    type = safeEnumValueOf(type, AccountType.CASH),
    currency = safeEnumValueOf(currency, CurrencyCode.USD),
    balanceCents = balanceCents,
    isArchived = isArchived,
    showOnHome = showOnHome
)

fun Account.toEntity() = AccountEntity(
    id = id,
    name = name,
    type = type.name,
    currency = currency.name,
    balanceCents = balanceCents,
    isArchived = isArchived,
    showOnHome = showOnHome
)

fun CategoryEntity.toDomain() = Category(
    id = id,
    name = name,
    iconName = iconName,
    colorHex = colorHex,
    isArchived = isArchived,
    parentCategoryId = parentCategoryId
)

fun Category.toEntity() = CategoryEntity(
    id = id,
    name = name,
    iconName = iconName,
    colorHex = colorHex,
    isArchived = isArchived,
    parentCategoryId = parentCategoryId
)

fun TransactionWithItems.toDomain() = Transaction(
    id = transaction.id,
    accountId = transaction.accountId,
    targetAccountId = transaction.targetAccountId,
    categoryId = transaction.categoryId,
    type = safeEnumValueOf(transaction.type, TransactionType.EXPENSE),
    currency = safeEnumValueOf(transaction.currency, CurrencyCode.USD),
    amountCents = transaction.amountCents,
    exchangeRateUsed = safePositiveBigDecimal(transaction.exchangeRateUsed),
    merchant = transaction.merchant,
    note = transaction.note,
    receiptUri = transaction.receiptUri,
    timestampMillis = transaction.timestampMillis,
    items = items.map { LineItem(it.id, it.transactionId, it.name, it.amountCents) }
)

fun Transaction.toEntity() = TransactionEntity(
    id = id,
    accountId = accountId,
    targetAccountId = targetAccountId,
    categoryId = categoryId,
    type = type.name,
    currency = currency.name,
    amountCents = amountCents,
    exchangeRateUsed = exchangeRateUsed.toPlainString(),
    merchant = merchant,
    note = note,
    receiptUri = receiptUri,
    timestampMillis = timestampMillis
)

fun BudgetEntity.toDomain() = Budget(
    id = id,
    categoryId = categoryId,
    currency = safeEnumValueOf(currency, CurrencyCode.USD),
    limitCents = limitCents,
    rolloverMode = safeEnumValueOf(rolloverMode, RolloverMode.RESET_MONTHLY),
    alertThresholdPct = alertThresholdPct,
    createdAtMillis = createdAtMillis
)

fun Budget.toEntity() = BudgetEntity(
    id = id,
    categoryId = categoryId,
    currency = currency.name,
    limitCents = limitCents,
    rolloverMode = rolloverMode.name,
    alertThresholdPct = alertThresholdPct,
    createdAtMillis = createdAtMillis
)

fun RecurringBillEntity.toDomain() = RecurringBill(
    id = id,
    name = name,
    amountCents = amountCents,
    currency = safeEnumValueOf(currency, CurrencyCode.USD),
    categoryId = categoryId,
    dueDayOfMonth = dueDayOfMonth,
    isPaidThisMonth = isPaidThisMonth,
    autoLog = autoLog
)

fun RecurringBill.toEntity() = RecurringBillEntity(
    id = id,
    name = name,
    amountCents = amountCents,
    currency = currency.name,
    categoryId = categoryId,
    dueDayOfMonth = dueDayOfMonth,
    isPaidThisMonth = isPaidThisMonth,
    autoLog = autoLog
)

fun SavingsGoalEntity.toDomain() = SavingsGoal(
    id = id,
    name = name,
    currency = safeEnumValueOf(currency, CurrencyCode.USD),
    targetCents = targetCents,
    currentCents = currentCents
)

fun SavingsGoal.toEntity() = SavingsGoalEntity(
    id = id,
    name = name,
    currency = currency.name,
    targetCents = targetCents,
    currentCents = currentCents
)

fun ReceivableEntity.toDomain() = Receivable(
    id = id,
    personName = personName,
    description = description,
    currency = safeEnumValueOf(currency, CurrencyCode.USD),
    totalOwedCents = totalOwedCents,
    paidBackCents = paidBackCents,
    isSettled = isSettled,
    createdAtMillis = createdAtMillis
)

fun Receivable.toEntity() = ReceivableEntity(
    id = id,
    personName = personName,
    description = description,
    currency = currency.name,
    totalOwedCents = totalOwedCents,
    paidBackCents = paidBackCents,
    isSettled = isSettled,
    createdAtMillis = createdAtMillis
)

fun PayableEntity.toDomain() = Payable(
    id = id,
    personName = personName,
    description = description,
    currency = safeEnumValueOf(currency, CurrencyCode.USD),
    totalOweCents = totalOweCents,
    paidCents = paidCents,
    isSettled = isSettled,
    createdAtMillis = createdAtMillis
)

fun Payable.toEntity() = PayableEntity(
    id = id,
    personName = personName,
    description = description,
    currency = currency.name,
    totalOweCents = totalOweCents,
    paidCents = paidCents,
    isSettled = isSettled,
    createdAtMillis = createdAtMillis
)

fun ShoppingListItemEntity.toDomain() = ShoppingListItem(
    id = id,
    listId = listId,
    name = name,
    quantity = quantity,
    estimatedUnitCents = estimatedUnitCents,
    actualUnitCents = actualUnitCents,
    isChecked = isChecked
)

fun ShoppingListItem.toEntity() = ShoppingListItemEntity(
    id = id,
    listId = listId,
    name = name,
    quantity = quantity,
    estimatedUnitCents = estimatedUnitCents,
    actualUnitCents = actualUnitCents,
    isChecked = isChecked
)

fun ShoppingListWithItems.toDomain() = ShoppingList(
    id = list.id,
    name = list.name,
    currency = safeEnumValueOf(list.currency, CurrencyCode.USD),
    categoryId = list.categoryId,
    createdAtMillis = list.createdAtMillis,
    items = items.map { it.toDomain() }
)

fun ShoppingList.toEntity() = ShoppingListEntity(
    id = id,
    name = name,
    currency = currency.name,
    categoryId = categoryId,
    createdAtMillis = createdAtMillis
)
