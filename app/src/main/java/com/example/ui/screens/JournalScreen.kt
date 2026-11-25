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
import androidx.compose.material.icons.rounded.CalendarMonth
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
import androidx.compose.material3.OutlinedButton
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
import com.example.data.local.CustomTradingSession
import com.example.data.model.Timeframe
import com.example.data.model.TradeDirection
import com.example.data.model.TradeEntity
import com.example.data.model.TradeResult
import com.example.data.model.TradingCurrency
import com.example.domain.AdvancedTradeAnalytics
import com.example.domain.TradeDateTimeHelper
import com.example.domain.TradingAnalytics
import com.example.ui.components.CurrencyBadge
import com.example.ui.components.DirectionBadge
import com.example.ui.components.EmptyTradingState
import com.example.ui.components.ResultBadge
import com.example.ui.components.ScreenshotLightboxDialog
import com.example.ui.components.TradeDetailDialog
import com.example.ui.theme.LocalTradingColors
import java.util.Locale

@Composable
fun JournalScreen(
    trades: List<TradeEntity>,
    displayCurrency: TradingCurrency,
    timezoneId: String = TradeDateTimeHelper.DEFAULT_TIMEZONE_ID,
    customSessions: List<CustomTradingSession> = CustomTradingSession.DEFAULT_SESSIONS,
    onAddTrade: () -> Unit,
    onEditTrade: (TradeEntity) -> Unit,
    onDeleteTrade: (String) -> Unit,
    onOpenCalendarAnalytics: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val ext = LocalTradingColors.current
    var searchQuery by remember { mutableStateOf("") }
    var selectedCurrencyFilter by remember { mutableStateOf<TradingCurrency?>(null) }
    var selectedResult by remember { mutableStateOf<TradeResult?>(null) }
    var selectedDirection by remember { mutableStateOf<TradeDirection?>(null) }
    var selectedTimeframe by remember { mutableStateOf<Timeframe?>(null) }
    var inspectedTrade by remember { mutableStateOf<TradeEntity?>(null) }
    var previewScreenshotData by remember { mutableStateOf<String?>(null) }

    val gapByTradeId = remember(trades, displayCurrency, timezoneId, customSessions) {
        AdvancedTradeAnalytics.enrichTrades(trades, displayCurrency, timezoneId, customSessions)
            .associate { it.trade.id to it.timeSincePrevTradeMillis }
    }

    val filtered = remember(
        trades,
        searchQuery,
        selectedCurrencyFilter,
        selectedResult,
        selectedDirection,
        selectedTimeframe
    ) {
        trades.filter { t ->
            val q = searchQuery.trim()
            val matchesSearch = q.isEmpty() ||
                t.instrument.contains(q, ignoreCase = true) ||
                t.tradeCode.contains(q, ignoreCase = true) ||
                t.strategy.contains(q, ignoreCase = true) ||
                t.notes.contains(q, ignoreCase = true) ||
                t.currencyEnum.code.contains(q, ignoreCase = true)
            val matchesCurr = selectedCurrencyFilter == null || t.currencyEnum == selectedCurrencyFilter
            val matchesResult = selectedResult == null || t.resultEnum == selectedResult
            val matchesDir = selectedDirection == null || t.directionEnum == selectedDirection
            val matchesTf = selectedTimeframe == null || t.timeframeEnum == selectedTimeframe
            matchesSearch && matchesCurr && matchesResult && matchesDir && matchesTf
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
                        placeholder = { Text("Search instrument, TWBB ID, currency (INR/USD), strategy...") },
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
                        TradingCurrency.entries.forEach { curr ->
                            FilterChip(
                                selected = selectedCurrencyFilter == curr,
                                onClick = { selectedCurrencyFilter = if (selectedCurrencyFilter == curr) null else curr },
                                label = { Text(curr.label) }
                            )
                        }
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

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Showing ${filtered.size} of ${trades.size} trades • ${displayCurrency.code}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f)
                        )
                        if (onOpenCalendarAnalytics != null) {
                            OutlinedButton(
                                onClick = onOpenCalendarAnalytics,
                                border = BorderStroke(1.dp, ext.gold.copy(alpha = 0.55f)),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.height(40.dp).testTag("journal_open_calendar_analytics")
                            ) {
                                Icon(Icons.Rounded.CalendarMonth, contentDescription = null, tint = ext.gold, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.size(6.dp))
                                Text("Calendar & Insights", style = MaterialTheme.typography.labelMedium, color = ext.gold)
                            }
                        }
                    }
                }
            }

            if (filtered.isEmpty()) {
                item {
                    Box(modifier = Modifier.widthIn(max = 1000.dp).fillMaxWidth()) {
                        EmptyTradingState(
                            title = if (trades.isEmpty()) "Your Trading Journal is Empty" else "No Matching Trades",
                            subtitle = if (trades.isEmpty()) "Tap + to record your entry, exit, currency (INR/USD), SL/TP, mistakes, and chart screenshot." else "Try adjusting your search or filter chips.",
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
                            displayCurrency = displayCurrency,
                            timezoneId = timezoneId,
                            timeSincePrevTradeMillis = gapByTradeId[trade.id],
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
            displayCurrency = displayCurrency,
            canEditOrDelete = true,
            timezoneId = timezoneId,
            timeSincePrevTradeMillis = gapByTradeId[trade.id],
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
    displayCurrency: TradingCurrency = trade.currencyEnum,
    timezoneId: String = TradeDateTimeHelper.DEFAULT_TIMEZONE_ID,
    timeSincePrevTradeMillis: Long? = null,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val ext = LocalTradingColors.current
    val origCurr = trade.currencyEnum
    val displayedOrigPnl = if (trade.isClosedTrade) trade.netRealizedPnl else trade.actualPnl
    val pnlColor = when {
        !trade.isClosedTrade -> ext.electricBlue
        displayedOrigPnl > 0 -> ext.profit
        displayedOrigPnl < 0 -> ext.loss
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
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = trade.instrument,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    DirectionBadge(trade.directionEnum)
                    CurrencyBadge(origCurr)
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
                    if (!trade.isClosedTrade) {
                        Surface(
                            color = ext.goldContainer,
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = "OPEN",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = ext.gold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = TradingAnalytics.formatCurrency(displayedOrigPnl, origCurr),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = pnlColor
                    )
                    if (trade.fees > 0.0) {
                        Text(
                            text = "Fees ${TradingAnalytics.formatPlainCurrency(trade.fees, origCurr)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (origCurr != displayCurrency && trade.isClosedTrade) {
                        val conv = TradingAnalytics.convertedPnl(trade, displayCurrency)
                        Text(
                            text = "≈ ${TradingAnalytics.formatCurrency(conv, displayCurrency)} @ ₹${String.format(Locale.US, "%.2f", trade.effectiveUsdToInrRate)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            val weekdayShort = TradeDateTimeHelper.weekdayOf(
                trade.entryTimeMillis, trade.tradeDateMillis, trade.tradeDateIso, timezoneId
            )?.shortLabel
            val timeRangeLabel = when {
                trade.hasExactEntryTime && trade.hasExactExitTime ->
                    "${TradeDateTimeHelper.formatTime12h(trade.entryTimeMillis!!, timezoneId)} → ${TradeDateTimeHelper.formatTime12h(trade.exitTimeMillis!!, timezoneId)}"
                trade.hasExactEntryTime ->
                    "In ${TradeDateTimeHelper.formatTime12h(trade.entryTimeMillis!!, timezoneId)}"
                else -> null
            }
            val durationStr = trade.holdingDurationMillis?.let { TradeDateTimeHelper.formatDuration(it) }
            val sessionStr = trade.tradingSession.takeIf { it.isNotBlank() }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${trade.tradeCode} • ${trade.tradeDateIso}${if (weekdayShort != null) " ($weekdayShort)" else ""} • Lot ${trade.lotSize}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (trade.isClosedTrade) {
                    ResultBadge(trade.resultEnum)
                } else {
                    Surface(color = ext.goldContainer, shape = RoundedCornerShape(999.dp)) {
                        Text(
                            text = "Open Position",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = ext.gold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            if (timeRangeLabel != null || durationStr != null || sessionStr != null || timeSincePrevTradeMillis != null) {
                val timingParts = buildList {
                    if (timeRangeLabel != null) add(timeRangeLabel)
                    if (durationStr != null) add("Hold $durationStr")
                    if (sessionStr != null) add(sessionStr)
                    if (timeSincePrevTradeMillis != null) add("Gap ${TradeDateTimeHelper.formatDuration(timeSincePrevTradeMillis)}")
                }.joinToString(" • ")
                Text(
                    text = timingParts,
                    style = MaterialTheme.typography.labelSmall,
                    color = ext.electricBlue
                )
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
