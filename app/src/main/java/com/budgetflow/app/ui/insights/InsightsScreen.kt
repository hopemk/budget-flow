package com.budgetflow.app.ui.insights

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.budgetflow.app.domain.model.BurnRateProjection
import com.budgetflow.app.domain.model.CurrencyCode
import com.budgetflow.app.domain.model.CurrencySpendSplit
import com.budgetflow.app.domain.model.DisplayCurrencyMode
import com.budgetflow.app.domain.model.MonthOverMonthComparison
import com.budgetflow.app.domain.model.TopSpendEntry
import com.budgetflow.app.domain.usecase.CurrencyConverter
import com.budgetflow.app.ui.components.DisplayCurrencySelector
import com.budgetflow.app.ui.components.EmptyStateCard
import com.budgetflow.app.ui.theme.StatusError
import com.budgetflow.app.ui.theme.StatusWarning

@Composable
fun InsightsScreen(viewModel: InsightsViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val summary = state.summary
    val insights = state.insights
    val currency = state.displayCurrency

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            DisplayCurrencySelector(
                selected = state.displayMode,
                onSelect = viewModel::onDisplayModeChanged
            )
        }

        state.netPosition?.let { net ->
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("NET POSITION", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            text = CurrencyConverter.format(net.netPositionCents, net.displayCurrency),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (net.netPositionCents < 0L) StatusError else MaterialTheme.colorScheme.onSurface
                        )
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column {
                                Text("Operating", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(CurrencyConverter.format(net.operatingTotalCents, net.displayCurrency), fontWeight = FontWeight.SemiBold)
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("Savings", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(CurrencyConverter.format(net.savingsTotalCents, net.displayCurrency), fontWeight = FontWeight.SemiBold)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text("Owed", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(CurrencyConverter.format(net.receivablesTotalCents, net.displayCurrency), fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                            }
                            if (net.payablesTotalCents > 0L) {
                                Column(horizontalAlignment = Alignment.End) {
                                    Text("I Owe", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(CurrencyConverter.format(net.payablesTotalCents, net.displayCurrency), fontWeight = FontWeight.SemiBold, color = StatusError)
                                }
                            }
                        }
                        if (state.displayMode.includeAllCurrencies && net.walletsByCurrency.isNotEmpty()) {
                            val walletSummary = net.walletsByCurrency.entries
                                .filter { it.value != 0L }
                                .joinToString("  •  ") { (cur, cents) ->
                                    "${cur.name}: ${CurrencyConverter.format(cents, cur)}"
                                }
                            if (walletSummary.isNotEmpty()) {
                                Text(
                                    text = walletSummary,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }

        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Card(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text("Income", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            text = CurrencyConverter.format(summary?.totalIncomeCents ?: 0L, currency),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                Card(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        val net = (summary?.totalIncomeCents ?: 0L) - (summary?.totalSpentCents ?: 0L)
                        Text("Net Saved", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            text = CurrencyConverter.format(net, currency),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (net < 0L) StatusError else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }

        if (insights != null && summary != null) {
            item {
                BurnRateProjectionCard(
                    projection = insights.projection,
                    totalBudgetCents = summary.totalBudgetCents,
                    currency = currency
                )
            }

            item {
                MonthOverMonthCard(
                    comparison = insights.monthOverMonth,
                    currency = currency
                )
            }
        }

        item {
            if (state.shares.isEmpty()) {
                EmptyStateCard(
                    title = "No spending data yet",
                    subtitle = "Log expenses this month to see your category breakdown and pace."
                )
            } else {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        SpendingDonutChart(shares = state.shares, modifier = Modifier.size(110.dp))
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("Category Share", fontWeight = FontWeight.SemiBold)
                            state.shares.take(5).forEach { share ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(share.name, style = MaterialTheme.typography.labelMedium)
                                    Text(
                                        "${share.percent}% (${CurrencyConverter.format(share.amountCents, currency)})",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        if (insights != null && insights.currencySplit.entries.isNotEmpty()) {
            item {
                CurrencySplitCard(split = insights.currencySplit)
            }
        }

        if (insights != null && insights.topMerchants.isNotEmpty()) {
            item {
                TopSpendListCard(
                    title = "Top Merchants",
                    entries = insights.topMerchants,
                    currency = currency
                )
            }
        }

        if (insights != null && insights.topItems.isNotEmpty()) {
            item {
                TopSpendListCard(
                    title = "Top Purchased Items",
                    entries = insights.topItems,
                    currency = currency
                )
            }
        }

        if (summary != null) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Spending Pace & Budget Health", fontWeight = FontWeight.SemiBold)
                        Text(
                            text = "${summary.monthProgressPct}% through the month with ${summary.budgetUsedPct}% of your budget used.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (insights != null && (insights.onTrackBudgets + insights.warningBudgets + insights.overBudgets) > 0) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                HealthPill(
                                    label = "${insights.onTrackBudgets} On Track",
                                    color = MaterialTheme.colorScheme.primary
                                )
                                HealthPill(
                                    label = "${insights.warningBudgets} Warning",
                                    color = StatusWarning
                                )
                                HealthPill(
                                    label = "${insights.overBudgets} Over",
                                    color = StatusError
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BurnRateProjectionCard(
    projection: BurnRateProjection,
    totalBudgetCents: Long,
    currency: CurrencyCode
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Projected End-of-Month", fontWeight = FontWeight.SemiBold)
                Text(
                    text = "Day ${projection.elapsedDays}/${projection.daysInMonth}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text("Daily Burn", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        text = "${CurrencyConverter.format(projection.dailyBurnCents, currency)}/day",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Projected Spend", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        text = CurrencyConverter.format(projection.projectedMonthSpendCents, currency),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
                if (totalBudgetCents > 0L) {
                    val isSurplus = projection.projectedRemainingCents >= 0L
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = if (isSurplus) "Proj. Surplus" else "Proj. Deficit",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = CurrencyConverter.format(projection.projectedRemainingCents, currency, showPlus = true),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (isSurplus) MaterialTheme.colorScheme.primary else StatusError
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MonthOverMonthCard(
    comparison: MonthOverMonthComparison,
    currency: CurrencyCode
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Month-over-Month Spend", fontWeight = FontWeight.SemiBold)
                val pct = comparison.deltaPercent
                val badgeText = when {
                    pct == null && comparison.currentMonthSpentCents > 0L -> "No prior month"
                    pct == null -> "No spend"
                    pct > 0 -> "+$pct%"
                    else -> "$pct%"
                }
                val isIncrease = comparison.deltaCents > 0L
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Text(
                        text = badgeText,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = when {
                            pct == null -> MaterialTheme.colorScheme.onSurfaceVariant
                            isIncrease -> StatusError
                            else -> MaterialTheme.colorScheme.primary
                        }
                    )
                }
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text("This Month", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        text = CurrencyConverter.format(comparison.currentMonthSpentCents, currency),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
                Column {
                    Text("Last Month", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        text = CurrencyConverter.format(comparison.previousMonthSpentCents, currency),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Medium
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("Difference", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        text = CurrencyConverter.format(comparison.deltaCents, currency, showPlus = true),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = if (comparison.deltaCents > 0L) StatusError else MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

@Composable
private fun CurrencySplitCard(split: CurrencySpendSplit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("USD vs. ZWG Spend Split", fontWeight = FontWeight.SemiBold)
            val barColors = listOf(
                MaterialTheme.colorScheme.primary,
                StatusWarning,
                MaterialTheme.colorScheme.tertiary,
                MaterialTheme.colorScheme.secondary,
                StatusError,
                MaterialTheme.colorScheme.outline
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(10.dp)
                    .clip(RoundedCornerShape(5.dp))
            ) {
                split.entries.forEachIndexed { index, entry ->
                    if (entry.percent > 0) {
                        Box(
                            modifier = Modifier
                                .weight(entry.percent.toFloat())
                                .fillMaxHeight()
                                .background(barColors[index % barColors.size])
                        )
                    }
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                split.entries.forEachIndexed { index, entry ->
                    Text(
                        text = "${entry.currency.name} ${entry.percent}% (${CurrencyConverter.format(entry.spentCents, entry.currency)})",
                        style = MaterialTheme.typography.labelMedium,
                        color = barColors[index % barColors.size],
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

@Composable
private fun TopSpendListCard(
    title: String,
    entries: List<TopSpendEntry>,
    currency: CurrencyCode
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            entries.forEachIndexed { index, entry ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${index + 1}. ${entry.name} (${entry.count}x)",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = CurrencyConverter.format(entry.amountCents, currency),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

@Composable
private fun HealthPill(label: String, color: Color) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = color.copy(alpha = 0.14f)
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = color
        )
    }
}

@Composable
private fun SpendingDonutChart(
    shares: List<CategoryShare>,
    modifier: Modifier = Modifier
) {
    val total = shares.sumOf { it.amountCents }.coerceAtLeast(1L).toFloat()
    Canvas(modifier = modifier) {
        var startAngle = -90f
        shares.forEach { share ->
            val sweep = (share.amountCents / total) * 360f
            drawArc(
                color = Color(share.colorHex),
                startAngle = startAngle,
                sweepAngle = (sweep - 4f).coerceAtLeast(2f),
                useCenter = false,
                style = Stroke(width = 24f, cap = StrokeCap.Round)
            )
            startAngle += sweep
        }
    }
}
