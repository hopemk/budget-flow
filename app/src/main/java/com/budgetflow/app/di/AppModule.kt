package com.budgetflow.app.di

import android.content.Context
import com.budgetflow.app.data.local.AndroidBackupStorage
import com.budgetflow.app.data.local.BudgetDao
import com.budgetflow.app.data.local.BudgetDatabase
import com.budgetflow.app.data.local.SettingsDataStore
import com.budgetflow.app.data.repository.OfflineBudgetRepository
import com.budgetflow.app.domain.repository.AccountRepository
import com.budgetflow.app.domain.repository.BackupRepository
import com.budgetflow.app.domain.repository.BackupStorage
import com.budgetflow.app.domain.repository.BillsGoalsRepository
import com.budgetflow.app.domain.repository.BudgetConfigRepository
import com.budgetflow.app.domain.repository.CategoryRepository
import com.budgetflow.app.domain.repository.DebtRepository
import com.budgetflow.app.domain.repository.PreferencesRepository
import com.budgetflow.app.domain.repository.ShoppingListRepository
import com.budgetflow.app.domain.repository.TransactionRepository
import com.budgetflow.app.domain.usecase.BudgetCalculator
import com.budgetflow.app.domain.usecase.BudgetForecastCalculator
import com.budgetflow.app.domain.usecase.CsvExporter
import com.budgetflow.app.domain.usecase.ReceiptTextParser
import com.budgetflow.app.domain.util.ReminderScheduler
import com.budgetflow.app.domain.util.SystemTimeProvider
import com.budgetflow.app.domain.util.TimeProvider
import com.budgetflow.app.notifications.BillReminderScheduler
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): BudgetDatabase =
        BudgetDatabase.build(context)

    @Provides
    fun provideBudgetDao(db: BudgetDatabase): BudgetDao = db.budgetDao()

    @Provides
    @Singleton
    fun provideSettingsDataStore(
        @ApplicationContext context: Context,
        timeProvider: TimeProvider
    ): SettingsDataStore = SettingsDataStore(context, timeProvider)

    @Provides
    @Singleton
    fun provideBudgetCalculator(): BudgetCalculator = BudgetCalculator()

    @Provides
    @Singleton
    fun provideBudgetForecastCalculator(
        budgetCalculator: BudgetCalculator
    ): BudgetForecastCalculator = BudgetForecastCalculator(budgetCalculator)

    @Provides
    @Singleton
    fun provideReceiptTextParser(): ReceiptTextParser = ReceiptTextParser()

    @Provides
    @Singleton
    fun provideCsvExporter(): CsvExporter = CsvExporter()
}

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindTimeProvider(impl: SystemTimeProvider): TimeProvider

    @Binds
    @Singleton
    abstract fun bindAccountRepository(impl: OfflineBudgetRepository): AccountRepository

    @Binds
    @Singleton
    abstract fun bindCategoryRepository(impl: OfflineBudgetRepository): CategoryRepository

    @Binds
    @Singleton
    abstract fun bindTransactionRepository(impl: OfflineBudgetRepository): TransactionRepository

    @Binds
    @Singleton
    abstract fun bindBudgetConfigRepository(impl: OfflineBudgetRepository): BudgetConfigRepository

    @Binds
    @Singleton
    abstract fun bindBillsGoalsRepository(impl: OfflineBudgetRepository): BillsGoalsRepository

    @Binds
    @Singleton
    abstract fun bindDebtRepository(impl: OfflineBudgetRepository): DebtRepository

    @Binds
    @Singleton
    abstract fun bindShoppingListRepository(impl: OfflineBudgetRepository): ShoppingListRepository

    @Binds
    @Singleton
    abstract fun bindPreferencesRepository(impl: OfflineBudgetRepository): PreferencesRepository

    @Binds
    @Singleton
    abstract fun bindBackupRepository(impl: OfflineBudgetRepository): BackupRepository

    @Binds
    @Singleton
    abstract fun bindBackupStorage(impl: AndroidBackupStorage): BackupStorage

    @Binds
    @Singleton
    abstract fun bindReminderScheduler(impl: BillReminderScheduler): ReminderScheduler
}
