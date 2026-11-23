package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.Timeframe
import com.example.data.model.TradeDirection
import com.example.data.model.TradeEntity
import com.example.data.model.TradeResult
import com.example.domain.TradingAnalytics
import com.example.ui.components.DirectionBadge
import com.example.ui.components.EmptyTradingState
import com.example.ui.components.ResultBadge
import com.example.ui.components.ScreenshotLightboxDialog
import com.example.ui.components.TradeDetailDialog
import com.example.ui.theme.LocalTradingColors

@Composable
fun JournalScreen(
    trades: List<TradeEntity>,
    onAddTrade: () -> Unit,
    onEditTrade: (TradeEntity) -> Unit,
    onDeleteTrade: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val ext = LocalTradingColors.current
    var searchQuery by remember { mutableStateOf("") }
    var selectedResult by remember { mutableStateOf<TradeResult?>(null) }
    var selectedDirection by remember { mutableStateOf<TradeDirection?>(null) }
    var selectedTimeframe by remember { mutableStateOf<Timeframe?>(null) }
    var inspectedTrade by remember { mutableStateOf<TradeEntity?>(null) }
    var previewScreenshotData by remember { mutableStateOf<String?>(null) }

    val filtered = remember(trades, searchQuery, selectedResult, selectedDirection, selectedTimeframe) {
        trades.filter { t ->
            val q = searchQuery.trim()
            val matchesSearch = q.isEmpty() ||
                t.instrument.contains(q, ignoreCase = true) ||
                t.tradeCode.contains(q, ignoreCase = true) ||
                t.strategy.contains(q, ignoreCase = true) ||
                t.notes.contains(q, ignoreCase = true)
            val matchesResult = selectedResult == null || t.resultEnum == selectedResult
            val matchesDir = selectedDirection == null || t.directionEnum == selectedDirection
            val matchesTf = selectedTimeframe == null || t.timeframeEnum == selectedTimeframe
            matchesSearch && matchesResult && matchesDir && matchesTf
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = Color.Transparent,
        floatingActionButton = {
            FloatingActionButton(
                onClick = onAddTrade,
                containerColor = ext.gold,
                contentColor = Color(0xFF0A1628),
                modifier = Modifier.testTag("journal_fab_add_trade")
            ) {
                Icon(Icons.Rounded.Add, contentDescription = "Log New Trade")
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            item {
                Column(
                    modifier = Modifier.widthIn(max = 1000.dp).fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Search instrument, TWBB ID, strategy, notes...") },
                        leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(Icons.Rounded.Clear, contentDescription = "Clear search")
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth().testTag("journal_search_input")
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Rounded.FilterList,
                            contentDescription = "Filters",
                            tint = ext.electricBlue,
                            modifier = Modifier.size(18.dp)
                        )
                        TradeResult.entries.forEach { res ->
                            FilterChip(
                                selected = selectedResult == res,
                                onClick = { selectedResult = if (selectedResult == res) null else res },
                                label = { Text(res.label) }
                            )
                        }
                        TradeDirection.entries.forEach { dir ->
                            FilterChip(
                                selected = selectedDirection == dir,
                                onClick = { selectedDirection = if (selectedDirection == dir) null else dir },
                                label = { Text(dir.label) }
                            )
                        }
                        Timeframe.entries.forEach { tf ->
                            FilterChip(
                                selected = selectedTimeframe == tf,
                                onClick = { selectedTimeframe = if (selectedTimeframe == tf) null else tf },
                                label = { Text(tf.label) }
                            )
                        }
                    }

                    Text(
                        text = "Showing ${filtered.size} of ${trades.size} trades",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (filtered.isEmpty()) {
                item {
                    Box(modifier = Modifier.widthIn(max = 1000.dp).fillMaxWidth()) {
                        EmptyTradingState(
                            title = if (trades.isEmpty()) "Your Trading Journal is Empty" else "No Matching Trades",
                            subtitle = if (trades.isEmpty()) "Tap + to record your entry, exit, SL/TP, mistakes, lessons, and chart screenshot." else "Try adjusting your search or filter chips.",
                            actionLabel = if (trades.isEmpty()) "Add Trade" else null,
                            onAction = if (trades.isEmpty()) onAddTrade else null
                        )
                    }
                }
            } else {
                items(filtered, key = { it.id }) { trade ->
                    Box(modifier = Modifier.widthIn(max = 1000.dp).fillMaxWidth()) {
                        TradeCardItem(
                            trade = trade,
                            onClick = { inspectedTrade = trade }
                        )
                    }
                }
            }

            item { Spacer(Modifier.height(72.dp)) }
        }
    }

    inspectedTrade?.let { trade ->
        TradeDetailDialog(
            trade = trade,
            canEditOrDelete = true,
            onDismiss = { inspectedTrade = null },
            onEdit = {
                inspectedTrade = null
                onEditTrade(trade)
            },
            onDelete = {
                inspectedTrade = null
                onDeleteTrade(trade.id)
            },
            onViewScreenshot = { data -> previewScreenshotData = data }
        )
    }

    previewScreenshotData?.let { data ->
        ScreenshotLightboxDialog(screenshotData = data, onDismiss = { previewScreenshotData = null })
    }
}

@Composable
fun TradeCardItem(
    trade: TradeEntity,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val ext = LocalTradingColors.current
    val pnlColor = when {
        trade.actualPnl > 0 -> ext.profit
        trade.actualPnl < 0 -> ext.loss
        else -> ext.gold
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("trade_card_${trade.tradeCode}"),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = ext.cardSurface),
        border = BorderStroke(1.dp, ext.cardBorder),
        elevation = CardDefaults.cardElevation(defaultElevation = if (ext.isDark) 3.dp else 1.dp)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = trade.instrument,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    DirectionBadge(trade.directionEnum)
                    Surface(
                        color = ext.electricBlue.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = trade.timeframeEnum.label,
                            style = MaterialTheme.typography.labelSmall,
                            color = ext.electricBlue,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
                Text(
                    text = TradingAnalytics.formatCurrency(trade.actualPnl),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = pnlColor
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${trade.tradeCode} • ${trade.tradeDateIso} • Lot ${trade.lotSize}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                ResultBadge(trade.resultEnum)
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Entry ${trade.entryPrice} → Exit ${trade.exitPrice} | ${trade.strategy.ifEmpty { "Manual" }}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (trade.mistakes.isNotBlank() && !trade.mistakes.equals("None", ignoreCase = true)) {
                        Icon(
                            Icons.Rounded.Warning,
                            contentDescription = "Has mistake tag",
                            tint = ext.gold,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    if (!trade.screenshotData.isNullOrBlank()) {
                        Icon(
                            Icons.Rounded.Image,
                            contentDescription = "Has chart screenshot",
                            tint = ext.teal,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}
