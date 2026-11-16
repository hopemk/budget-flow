package com.budgetflow.app.data.local

import com.budgetflow.app.domain.DomainConstants
import org.json.JSONArray
import org.json.JSONObject

data class BackupSnapshot(
    val accounts: List<AccountEntity>,
    val categories: List<CategoryEntity>,
    val transactions: List<TransactionEntity>,
    val lineItems: List<LineItemEntity>,
    val budgets: List<BudgetEntity>,
    val bills: List<RecurringBillEntity>,
    val goals: List<SavingsGoalEntity>,
    val receivables: List<ReceivableEntity>,
    val payables: List<PayableEntity>,
    val shoppingLists: List<ShoppingListEntity> = emptyList(),
    val shoppingListItems: List<ShoppingListItemEntity> = emptyList()
)

object BackupSerializer {
    private const val BACKUP_FORMAT_VERSION = 1

    fun toJson(snapshot: BackupSnapshot, nowMillis: Long = System.currentTimeMillis()): String {
        val root = JSONObject()
        root.put("formatVersion", BACKUP_FORMAT_VERSION)
        root.put("exportedAtMillis", nowMillis)

        root.put("accounts", JSONArray().apply {
            snapshot.accounts.forEach { a ->
                put(JSONObject().apply {
                    put("id", a.id)
                    put("name", a.name)
                    put("type", a.type)
                    put("currency", a.currency)
                    put("balanceCents", a.balanceCents)
                    put("isArchived", a.isArchived)
                    put("showOnHome", a.showOnHome)
                })
            }
        })

        root.put("categories", JSONArray().apply {
            snapshot.categories.forEach { c ->
                put(JSONObject().apply {
                    put("id", c.id)
                    put("name", c.name)
                    put("iconName", c.iconName)
                    put("colorHex", c.colorHex)
                    put("isArchived", c.isArchived)
                    put("parentCategoryId", c.parentCategoryId ?: JSONObject.NULL)
                })
            }
        })

        root.put("transactions", JSONArray().apply {
            snapshot.transactions.forEach { t ->
                put(JSONObject().apply {
                    put("id", t.id)
                    put("accountId", t.accountId)
                    put("targetAccountId", t.targetAccountId ?: JSONObject.NULL)
                    put("categoryId", t.categoryId ?: JSONObject.NULL)
                    put("type", t.type)
                    put("currency", t.currency)
                    put("amountCents", t.amountCents)
                    put("exchangeRateUsed", t.exchangeRateUsed)
                    put("merchant", t.merchant)
                    put("note", t.note)
                    put("receiptUri", t.receiptUri ?: JSONObject.NULL)
                    put("timestampMillis", t.timestampMillis)
                })
            }
        })

        root.put("lineItems", JSONArray().apply {
            snapshot.lineItems.forEach { li ->
                put(JSONObject().apply {
                    put("id", li.id)
                    put("transactionId", li.transactionId)
                    put("name", li.name)
                    put("amountCents", li.amountCents)
                })
            }
        })

        root.put("budgets", JSONArray().apply {
            snapshot.budgets.forEach { b ->
                put(JSONObject().apply {
                    put("id", b.id)
                    put("categoryId", b.categoryId)
                    put("currency", b.currency)
                    put("limitCents", b.limitCents)
                    put("rolloverMode", b.rolloverMode)
                    put("alertThresholdPct", b.alertThresholdPct)
                    put("createdAtMillis", b.createdAtMillis)
                })
            }
        })

        root.put("bills", JSONArray().apply {
            snapshot.bills.forEach { bill ->
                put(JSONObject().apply {
                    put("id", bill.id)
                    put("name", bill.name)
                    put("amountCents", bill.amountCents)
                    put("currency", bill.currency)
                    put("categoryId", bill.categoryId ?: JSONObject.NULL)
                    put("dueDayOfMonth", bill.dueDayOfMonth)
                    put("isPaidThisMonth", bill.isPaidThisMonth)
                    put("autoLog", bill.autoLog)
                })
            }
        })

        root.put("goals", JSONArray().apply {
            snapshot.goals.forEach { g ->
                put(JSONObject().apply {
                    put("id", g.id)
                    put("name", g.name)
                    put("currency", g.currency)
                    put("targetCents", g.targetCents)
                    put("currentCents", g.currentCents)
                })
            }
        })

        root.put("receivables", JSONArray().apply {
            snapshot.receivables.forEach { r ->
                put(JSONObject().apply {
                    put("id", r.id)
                    put("personName", r.personName)
                    put("description", r.description)
                    put("currency", r.currency)
                    put("totalOwedCents", r.totalOwedCents)
                    put("paidBackCents", r.paidBackCents)
                    put("isSettled", r.isSettled)
                    put("createdAtMillis", r.createdAtMillis)
                })
            }
        })

        root.put("payables", JSONArray().apply {
            snapshot.payables.forEach { p ->
                put(JSONObject().apply {
                    put("id", p.id)
                    put("personName", p.personName)
                    put("description", p.description)
                    put("currency", p.currency)
                    put("totalOweCents", p.totalOweCents)
                    put("paidCents", p.paidCents)
                    put("isSettled", p.isSettled)
                    put("createdAtMillis", p.createdAtMillis)
                })
            }
        })

        root.put("shoppingLists", JSONArray().apply {
            snapshot.shoppingLists.forEach { l ->
                put(JSONObject().apply {
                    put("id", l.id)
                    put("name", l.name)
                    put("currency", l.currency)
                    put("categoryId", l.categoryId ?: JSONObject.NULL)
                    put("createdAtMillis", l.createdAtMillis)
                })
            }
        })

        root.put("shoppingListItems", JSONArray().apply {
            snapshot.shoppingListItems.forEach { item ->
                put(JSONObject().apply {
                    put("id", item.id)
                    put("listId", item.listId)
                    put("name", item.name)
                    put("quantity", item.quantity)
                    put("estimatedUnitCents", item.estimatedUnitCents)
                    put("actualUnitCents", item.actualUnitCents ?: JSONObject.NULL)
                    put("isChecked", item.isChecked)
                })
            }
        })

        return root.toString(2)
    }

    fun fromJson(json: String, nowMillis: Long = System.currentTimeMillis()): BackupSnapshot {
        val root = JSONObject(json)
        val version = root.optInt("formatVersion", -1)
        require(version in 1..BACKUP_FORMAT_VERSION) { "Unsupported backup format version: $version" }

        val accounts = root.optJSONArray("accounts").mapObjects { o ->
            AccountEntity(
                id = o.getLong("id"),
                name = o.getString("name"),
                type = o.getString("type"),
                currency = o.getString("currency"),
                balanceCents = o.getLong("balanceCents"),
                isArchived = o.optBoolean("isArchived", false),
                showOnHome = o.optBoolean("showOnHome", true)
            )
        }

        val categories = root.optJSONArray("categories").mapObjects { o ->
            CategoryEntity(
                id = o.getLong("id"),
                name = o.getString("name"),
                iconName = o.getString("iconName"),
                colorHex = o.getLong("colorHex"),
                isArchived = o.optBoolean("isArchived", false),
                parentCategoryId = o.optNullableLong("parentCategoryId")
            )
        }

        val transactions = root.optJSONArray("transactions").mapObjects { o ->
            TransactionEntity(
                id = o.getLong("id"),
                accountId = o.getLong("accountId"),
                targetAccountId = o.optNullableLong("targetAccountId"),
                categoryId = o.optNullableLong("categoryId"),
                type = o.getString("type"),
                currency = o.getString("currency"),
                amountCents = o.getLong("amountCents"),
                exchangeRateUsed = o.getString("exchangeRateUsed"),
                merchant = o.optString("merchant", ""),
                note = o.optString("note", ""),
                receiptUri = o.optNullableString("receiptUri"),
                timestampMillis = o.getLong("timestampMillis")
            )
        }

        val lineItems = root.optJSONArray("lineItems").mapObjects { o ->
            LineItemEntity(
                id = o.getLong("id"),
                transactionId = o.getLong("transactionId"),
                name = o.getString("name"),
                amountCents = o.getLong("amountCents")
            )
        }

        val budgets = root.optJSONArray("budgets").mapObjects { o ->
            BudgetEntity(
                id = o.getLong("id"),
                categoryId = o.getLong("categoryId"),
                currency = o.getString("currency"),
                limitCents = o.getLong("limitCents"),
                rolloverMode = o.optString("rolloverMode", "RESET_MONTHLY"),
                alertThresholdPct = o.optInt("alertThresholdPct", DomainConstants.DEFAULT_ALERT_THRESHOLD_PCT),
                createdAtMillis = o.optLong("createdAtMillis", 0L)
            )
        }

        val bills = root.optJSONArray("bills").mapObjects { o ->
            RecurringBillEntity(
                id = o.getLong("id"),
                name = o.getString("name"),
                amountCents = o.getLong("amountCents"),
                currency = o.getString("currency"),
                categoryId = o.optNullableLong("categoryId"),
                dueDayOfMonth = o.getInt("dueDayOfMonth"),
                isPaidThisMonth = o.optBoolean("isPaidThisMonth", false),
                autoLog = o.optBoolean("autoLog", false)
            )
        }

        val goals = root.optJSONArray("goals").mapObjects { o ->
            SavingsGoalEntity(
                id = o.getLong("id"),
                name = o.getString("name"),
                currency = o.getString("currency"),
                targetCents = o.getLong("targetCents"),
                currentCents = o.getLong("currentCents")
            )
        }

        val receivables = root.optJSONArray("receivables").mapObjects { o ->
            ReceivableEntity(
                id = o.getLong("id"),
                personName = o.getString("personName"),
                description = o.optString("description", ""),
                currency = o.getString("currency"),
                totalOwedCents = o.getLong("totalOwedCents"),
                paidBackCents = o.optLong("paidBackCents", 0L),
                isSettled = o.optBoolean("isSettled", false),
                createdAtMillis = o.optLong("createdAtMillis", nowMillis)
            )
        }

        val payables = root.optJSONArray("payables").mapObjects { o ->
            PayableEntity(
                id = o.getLong("id"),
                personName = o.getString("personName"),
                description = o.optString("description", ""),
                currency = o.getString("currency"),
                totalOweCents = o.getLong("totalOweCents"),
                paidCents = o.optLong("paidCents", 0L),
                isSettled = o.optBoolean("isSettled", false),
                createdAtMillis = o.optLong("createdAtMillis", nowMillis)
            )
        }

        val shoppingLists = root.optJSONArray("shoppingLists").mapObjects { o ->
            ShoppingListEntity(
                id = o.getLong("id"),
                name = o.getString("name"),
                currency = o.getString("currency"),
                categoryId = o.optNullableLong("categoryId"),
                createdAtMillis = o.optLong("createdAtMillis", nowMillis)
            )
        }

        val shoppingListItems = root.optJSONArray("shoppingListItems").mapObjects { o ->
            ShoppingListItemEntity(
                id = o.getLong("id"),
                listId = o.getLong("listId"),
                name = o.getString("name"),
                quantity = o.optInt("quantity", 1),
                estimatedUnitCents = o.optLong("estimatedUnitCents", 0L),
                actualUnitCents = o.optNullableLong("actualUnitCents"),
                isChecked = o.optBoolean("isChecked", false)
            )
        }

        return BackupSnapshot(
            accounts = accounts,
            categories = categories,
            transactions = transactions,
            lineItems = lineItems,
            budgets = budgets,
            bills = bills,
            goals = goals,
            receivables = receivables,
            payables = payables,
            shoppingLists = shoppingLists,
            shoppingListItems = shoppingListItems
        )
    }

    private inline fun <T> JSONArray?.mapObjects(transform: (JSONObject) -> T): List<T> {
        if (this == null) return emptyList()
        return buildList(length()) {
            for (i in 0 until length()) {
                add(transform(getJSONObject(i)))
            }
        }
    }

    private fun JSONObject.optNullableLong(key: String): Long? =
        if (!has(key) || isNull(key)) null else getLong(key)

    private fun JSONObject.optNullableString(key: String): String? =
        if (!has(key) || isNull(key)) null else getString(key)
}
