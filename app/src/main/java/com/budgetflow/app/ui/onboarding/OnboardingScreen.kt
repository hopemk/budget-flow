package com.budgetflow.app.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.budgetflow.app.domain.DomainConstants
import com.budgetflow.app.domain.model.AccountType
import com.budgetflow.app.domain.model.CurrencyCode
import com.budgetflow.app.ui.components.AccountTypeChipRow
import com.budgetflow.app.ui.components.BudgetFlowCard
import com.budgetflow.app.ui.components.CurrencyChipRow

private data class TourStep(
    val icon: ImageVector,
    val title: String,
    val description: String
)

private val tourSteps = listOf(
    TourStep(
        icon = Icons.Default.AccountBalanceWallet,
        title = "USD & ZWG Together",
        description = "Track cash, bank, and mobile money wallets in both USD and ZWG with your own exchange rate."
    ),
    TourStep(
        icon = Icons.Default.CameraAlt,
        title = "Log Spend & Scan Receipts",
        description = "Record individual items you bought or scan receipts on-device to pre-fill merchant, items, and total."
    ),
    TourStep(
        icon = Icons.Default.BarChart,
        title = "Flexible Budgets & Insights",
        description = "Choose monthly reset or rollover per category, and monitor burn-rate projections and currency splits."
    ),
    TourStep(
        icon = Icons.Default.ReceiptLong,
        title = "Bills, Goals & What I'm Owed",
        description = "Keep recurring bills, savings goals, and money people owe you organized in one place."
    )
)

@Composable
fun OnboardingScreen(
    viewModel: OnboardingViewModel = hiltViewModel()
) {
    var stepIndex by remember { mutableIntStateOf(0) }
    val totalSteps = tourSteps.size + 1

    var walletName by remember { mutableStateOf("") }
    var walletType by remember { mutableStateOf(AccountType.CASH) }
    var walletCurrency by remember { mutableStateOf(CurrencyCode.USD) }
    var startingBalance by remember { mutableStateOf("0.00") }
    var exchangeRate by remember { mutableStateOf(DomainConstants.DEFAULT_ZWG_PER_USD) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "BudgetFlow",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            TextButton(onClick = viewModel::skipOnboarding) {
                Text("Skip")
            }
        }

        if (stepIndex < tourSteps.size) {
            val step = tourSteps[stepIndex]
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(88.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = step.icon,
                        contentDescription = null,
                        modifier = Modifier.size(42.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
                Text(
                    text = step.title,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = step.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        } else {
            BudgetFlowCard(
                cornerRadius = 20.dp,
                contentPadding = PaddingValues(18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Set Up Your First Wallet",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "No pre-filled fake balances—add your own wallet or leave blank to start empty.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = walletName,
                    onValueChange = { walletName = it },
                    label = { Text("Wallet Name (e.g. Cash, Bank)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                CurrencyChipRow(selected = walletCurrency, onSelect = { walletCurrency = it })
                AccountTypeChipRow(selected = walletType, onSelect = { walletType = it })
                OutlinedTextField(
                    value = startingBalance,
                    onValueChange = { startingBalance = it },
                    label = { Text("Starting Balance (${walletCurrency.name})") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = exchangeRate,
                    onValueChange = { exchangeRate = it },
                    label = { Text("Exchange Rate (1 USD = ? ZWG)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                repeat(totalSteps) { index ->
                    val active = index == stepIndex
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 4.dp)
                            .size(if (active) 10.dp else 7.dp)
                            .clip(CircleShape)
                            .background(
                                if (active) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.outline
                            )
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (stepIndex > 0) {
                    OutlinedButton(
                        onClick = { stepIndex-- },
                        modifier = Modifier.weight(1f)
                    ) { Text("Back") }
                } else {
                    Spacer(Modifier.weight(1f))
                }

                if (stepIndex < tourSteps.size) {
                    Button(
                        onClick = { stepIndex++ },
                        modifier = Modifier.weight(1f)
                    ) { Text("Next") }
                } else {
                    Button(
                        onClick = {
                            viewModel.completeOnboarding(
                                walletName = walletName,
                                walletType = walletType,
                                walletCurrency = walletCurrency,
                                startingBalanceInput = startingBalance,
                                exchangeRateInput = exchangeRate
                            )
                        },
                        modifier = Modifier.weight(1f)
                    ) { Text("Get Started") }
                }
            }
            Spacer(Modifier.height(4.dp))
        }
    }
}
