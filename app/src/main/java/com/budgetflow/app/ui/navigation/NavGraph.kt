package com.budgetflow.app.ui.navigation

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.budgetflow.app.R
import com.budgetflow.app.ui.bills.BillsGoalsScreen
import com.budgetflow.app.ui.budgets.BudgetsScreen
import com.budgetflow.app.ui.categories.CategoriesScreen
import com.budgetflow.app.ui.forecast.ForecastScreen
import com.budgetflow.app.ui.home.HomeScreen
import com.budgetflow.app.ui.insights.InsightsScreen
import com.budgetflow.app.ui.scanner.ReceiptScannerScreen
import com.budgetflow.app.ui.settings.SettingsScreen
import com.budgetflow.app.ui.shopping.ShoppingListScreen
import com.budgetflow.app.ui.spend.SpendScreen
import com.budgetflow.app.ui.spend.SpendViewModel
import com.budgetflow.app.ui.transactions.TransactionsScreen

sealed class Route(val path: String, @StringRes val labelRes: Int) {
    data object Home : Route("home", R.string.nav_home)
    data object Budgets : Route("budgets", R.string.nav_budgets)
    data object Insights : Route("insights", R.string.nav_insights)
    data object BillsGoals : Route("bills", R.string.nav_bills)
    data object Spend : Route("spend", R.string.nav_spend)
    data object Transactions : Route("transactions", R.string.nav_transactions)
    data object Scanner : Route("scanner", R.string.nav_scanner)
    data object ShoppingLists : Route("shopping_lists", R.string.nav_shopping_lists)
    data object Categories : Route("categories", R.string.nav_categories)
    data object Forecast : Route("forecast", R.string.nav_forecast)
    data object Settings : Route("settings", R.string.nav_settings)
}

private data class BottomNavItem(val route: Route, val icon: ImageVector)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BudgetFlowNavHost() {
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val currentDest = backStack?.destination
    val sharedSpendViewModel: SpendViewModel = hiltViewModel()

    val bottomItems = listOf(
        BottomNavItem(Route.Home, Icons.Default.Home),
        BottomNavItem(Route.Budgets, Icons.Default.AccountBalanceWallet),
        BottomNavItem(Route.Insights, Icons.Default.BarChart),
        BottomNavItem(Route.BillsGoals, Icons.Default.ReceiptLong)
    )
    val showBottomBar = bottomItems.any { item ->
        currentDest?.hierarchy?.any { it.route == item.route.path } == true
    }

    Scaffold(
        topBar = {
            if (showBottomBar) {
                TopAppBar(
                    title = {
                        val titleRes = bottomItems.firstOrNull { it.route.path == currentDest?.route }?.route?.labelRes
                            ?: R.string.app_name
                        Text(stringResource(titleRes))
                    },
                    actions = {
                        IconButton(onClick = { navController.navigate(Route.Settings.path) }) {
                            Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.nav_settings))
                        }
                    }
                )
            }
        },
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    bottomItems.forEach { item ->
                        val selected = currentDest?.hierarchy?.any { it.route == item.route.path } == true
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                navController.navigate(item.route.path) {
                                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(item.icon, contentDescription = stringResource(item.route.labelRes)) },
                            label = { Text(stringResource(item.route.labelRes)) }
                        )
                    }
                }
            }
        },
        floatingActionButton = {
            if (showBottomBar) {
                FloatingActionButton(onClick = {
                    sharedSpendViewModel.startNewTransaction()
                    navController.navigate(Route.Spend.path)
                }) {
                    Icon(Icons.Default.Add, contentDescription = stringResource(R.string.nav_spend))
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Route.Home.path,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Route.Home.path) {
                HomeScreen(
                    onNavigateToSpend = {
                        sharedSpendViewModel.startNewTransaction()
                        navController.navigate(Route.Spend.path)
                    },
                    onNavigateToScanner = { navController.navigate(Route.Scanner.path) },
                    onNavigateToGoals = { navController.navigate(Route.BillsGoals.path) },
                    onNavigateToShoppingLists = { navController.navigate(Route.ShoppingLists.path) },
                    onNavigateToTransactions = { navController.navigate(Route.Transactions.path) },
                    onEditTransaction = { txId ->
                        sharedSpendViewModel.loadTransaction(txId)
                        navController.navigate(Route.Spend.path)
                    }
                )
            }
            composable(Route.Transactions.path) {
                TransactionsScreen(
                    onEditTransaction = { txId ->
                        sharedSpendViewModel.loadTransaction(txId)
                        navController.navigate(Route.Spend.path)
                    }
                )
            }
            composable(Route.Budgets.path) {
                BudgetsScreen(
                    onNavigateToCategories = { navController.navigate(Route.Categories.path) },
                    onNavigateToForecast = { navController.navigate(Route.Forecast.path) }
                )
            }
            composable(Route.Categories.path) { CategoriesScreen() }
            composable(Route.Forecast.path) { ForecastScreen() }
            composable(Route.Insights.path) { InsightsScreen() }
            composable(Route.BillsGoals.path) { BillsGoalsScreen() }
            composable(Route.ShoppingLists.path) {
                ShoppingListScreen(
                    onCheckoutToSpend = { listName, categoryId, currency, items ->
                        sharedSpendViewModel.loadFromShoppingList(
                            listName = listName,
                            categoryId = categoryId,
                            currency = currency,
                            items = items
                        )
                        navController.navigate(Route.Spend.path)
                    }
                )
            }
            composable(Route.Spend.path) {
                SpendScreen(
                    onOpenScanner = { navController.navigate(Route.Scanner.path) },
                    onSaved = { navController.popBackStack() },
                    viewModel = sharedSpendViewModel
                )
            }
            composable(Route.Scanner.path) {
                ReceiptScannerScreen(
                    onReceiptParsed = { parsed ->
                        sharedSpendViewModel.applyParsedReceipt(parsed)
                        navController.navigate(Route.Spend.path) {
                            popUpTo(Route.Scanner.path) { inclusive = true }
                        }
                    },
                    onCancel = { navController.popBackStack() }
                )
            }
            composable(Route.Settings.path) { SettingsScreen() }
        }
    }
}
