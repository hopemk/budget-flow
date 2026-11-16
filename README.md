# BudgetFlow

An offline-first personal budgeting and multi-currency expense tracking Android application built with **Kotlin**, **Jetpack Compose**, **Room**, and **Hilt**.

## Features

- **Multi-Currency Wallets & Transfers:** Manage Cash, Bank, Mobile Money, Savings, and Credit Card accounts across **USD**, **ZWG**, **GBP**, **ZAR**, **EUR**, and **BWP** with configurable exchange rates.
- **Customizable Home Dashboard:** Choose which accounts appear on the opening screen, view recent transactions, and jump to quick actions (**Scan**, **Spend**, **Lists**, **Goals**).
- **Flexible Categories & Subcategories:** Organize spending into top-level categories and nested subcategories (e.g., `Transport → Fuel`, `Groceries → Tomatoes`) with full CRUD, archive/restore, and safe delete.
- **Monthly Budgets & Rollover Rules:** Set per-category or per-subcategory monthly limits with **Reset Monthly**, **Rollover Surplus**, or **Rollover Surplus & Deficit** policies.
- **Budget Forecast:** Non-destructive what-if cash-flow simulator combining expected salary/income, unpaid bills, planned shopping lists, one-off expenses, and 1M/3M/6M projections before applying budget updates.
- **Shopping Lists:** Maintain multiple lists with optional planning prices, in-shop quantity & shelf-price confirmation, and one-tap checkout into Spend logging.
- **Receipt Scanner:** On-device receipt text recognition using CameraX and ML Kit to pre-fill merchant, line items, and totals.
- **Bills, Savings Goals & Debt Tracking:** Track recurring bills (with local reminder notifications), savings goals, receivables, and payables.
- **Insights, Export & Backup:** Monthly spending breakdowns, multi-month trends, CSV transaction export, JSON backup/restore, and optional biometric app lock.

## Tech Stack

- **Language:** Kotlin
- **UI:** Jetpack Compose + Material 3 + Navigation Compose
- **DI:** Hilt
- **Persistence:** Room (SQLite) + Jetpack DataStore
- **Scanning & Security:** CameraX, ML Kit Text Recognition, AndroidX Biometric

## Project Structure

```text
app/src/main/java/com/budgetflow/app/
├── data/           # Room database, DAO, entities, DataStore, JSON backup, repository impl
├── di/             # Hilt dependency injection modules
├── domain/         # Domain models, narrow repository interfaces, pure calculators & use cases
├── notifications/  # Bill reminder receiver & scheduler
└── ui/             # Compose screens, ViewModels, navigation, and shared UI components
```

## Build & Test

Run unit tests and assemble the debug APK:

```bash
gradle testDebugUnitTest assembleDebug
```
