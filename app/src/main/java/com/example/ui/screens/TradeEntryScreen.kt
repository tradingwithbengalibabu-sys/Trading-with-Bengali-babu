package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AccessTime
import androidx.compose.material.icons.rounded.CalendarToday
import androidx.compose.material.icons.rounded.CurrencyExchange
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.data.local.CustomTradingSession
import com.example.data.model.ExitClosureType
import com.example.data.model.Timeframe
import com.example.data.model.TradeDirection
import com.example.data.model.TradeEntity
import com.example.data.model.TradeResult
import com.example.data.model.TradeStatus
import com.example.data.model.TradingCurrency
import com.example.data.model.UserEntity
import com.example.domain.TradeDateTimeHelper
import com.example.domain.TradingAnalytics
import com.example.ui.components.GradientButton
import com.example.ui.components.rememberDecodedScreenshot
import com.example.ui.theme.LocalTradingColors
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val QUICK_INSTRUMENTS = listOf("NIFTY 50", "BANKNIFTY", "XAUUSD", "EURUSD", "BTCUSD", "US30", "RELIANCE")
private val QUICK_STRATEGIES = listOf("Price Action", "SMC", "Breakout", "Scalping", "Trend Pullback", "Swing")
private val QUICK_MISTAKES = listOf("None", "FOMO", "Early Exit", "No Stop Loss", "Overtrading", "Revenge Trade", "Oversized Lot", "Moved SL")

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TradeEntryScreen(
    user: UserEntity,
    editingTrade: TradeEntity?,
    liveUsdToInrRate: Double,
    isRefreshingRate: Boolean,
    selectedTimezoneId: String = TradeDateTimeHelper.DEFAULT_TIMEZONE_ID,
    customSessions: List<CustomTradingSession> = CustomTradingSession.DEFAULT_SESSIONS,
    onRefreshRate: () -> Unit,
    onEncodeScreenshot: suspend (Uri) -> String?,
    onSave: (
        existingTradeId: String?,
        tradeDateMillis: Long,
        tradeDateIso: String,
        instrument: String,
        direction: TradeDirection,
        timeframe: Timeframe,
        currency: TradingCurrency,
        usdToInrRate: Double,
        entryPrice: Double,
        exitPrice: Double,
        stopLoss: Double?,
        takeProfit: Double?,
        lotSize: Double,
        riskAmount: Double,
        actualPnl: Double,
        fees: Double,
        tradeStatus: TradeStatus,
        result: TradeResult,
        strategy: String,
        entryReason: String,
        exitReason: String,
        mistakes: String,
        lessonsLearned: String,
        screenshotData: String?,
        notes: String,
        entryTimeMillis: Long?,
        exitDateIso: String,
        exitTimeMillis: Long?,
        tradingSession: String,
        exitClosureType: ExitClosureType,
        timezoneId: String
    ) -> Unit,
    onCancel: () -> Unit
) {
    BackHandler(onBack = onCancel)

    val ext = LocalTradingColors.current
    val scope = rememberCoroutineScope()
    val effectiveTimezoneId = remember(editingTrade, selectedTimezoneId) {
        editingTrade?.timezoneId?.takeIf { it.isNotBlank() } ?: selectedTimezoneId
    }

    var tradeDateMillis by remember(editingTrade) {
        mutableLongStateOf(editingTrade?.tradeDateMillis ?: System.currentTimeMillis())
    }
    var recordExactTimes by remember(editingTrade) {
        mutableStateOf(editingTrade == null || editingTrade.hasExactEntryTime || editingTrade.hasExactExitTime)
    }
    var entryTimeMillis by remember(editingTrade) {
        mutableStateOf<Long?>(
            if (editingTrade != null) editingTrade.entryTimeMillis
            else System.currentTimeMillis()
        )
    }
    var exitTimeMillis by remember(editingTrade) {
        mutableStateOf<Long?>(
            if (editingTrade != null) editingTrade.exitTimeMillis
            else System.currentTimeMillis()
        )
    }
    var tradingSession by remember(editingTrade) {
        mutableStateOf(
            editingTrade?.tradingSession?.takeIf { it.isNotBlank() }
                ?: entryTimeMillis?.let {
                    TradeDateTimeHelper.suggestSessionForTimestamp(it, customSessions, effectiveTimezoneId)
                }
                ?: ""
        )
    }
    var sessionManuallyEdited by remember(editingTrade) {
        mutableStateOf(editingTrade != null && editingTrade.tradingSession.isNotBlank())
    }
    var exitClosureType by remember(editingTrade) {
        mutableStateOf(editingTrade?.resolvedExitClosureType ?: ExitClosureType.UNKNOWN)
    }

    var showEntryDatePicker by remember { mutableStateOf(false) }
    var showExitDatePicker by remember { mutableStateOf(false) }
    var showEntryTimePicker by remember { mutableStateOf(false) }
    var showExitTimePicker by remember { mutableStateOf(false) }

    var instrument by remember(editingTrade) { mutableStateOf(editingTrade?.instrument ?: "") }
    var currency by remember(editingTrade) {
        mutableStateOf(editingTrade?.currencyEnum ?: TradingCurrency.USD)
    }
    var currencyManuallyChosen by remember(editingTrade) {
        mutableStateOf(editingTrade != null)
    }
    var usdToInrRateText by remember(editingTrade) {
        mutableStateOf(
            String.format(
                Locale.US,
                "%.2f",
                editingTrade?.effectiveUsdToInrRate ?: liveUsdToInrRate
            )
        )
    }
    var rateManuallyEdited by remember(editingTrade) {
        mutableStateOf(editingTrade != null)
    }

    LaunchedEffect(liveUsdToInrRate) {
        if (!rateManuallyEdited && editingTrade == null) {
            usdToInrRateText = String.format(Locale.US, "%.2f", liveUsdToInrRate)
        }
    }

    var direction by remember(editingTrade) { mutableStateOf(editingTrade?.directionEnum ?: TradeDirection.BUY) }
    var timeframe by remember(editingTrade) { mutableStateOf(editingTrade?.timeframeEnum ?: Timeframe.M15) }
    var entryPrice by remember(editingTrade) { mutableStateOf(editingTrade?.entryPrice?.toString() ?: "") }
    var exitPrice by remember(editingTrade) { mutableStateOf(editingTrade?.exitPrice?.toString() ?: "") }
    var stopLoss by remember(editingTrade) { mutableStateOf(editingTrade?.stopLoss?.toString() ?: "") }
    var takeProfit by remember(editingTrade) { mutableStateOf(editingTrade?.takeProfit?.toString() ?: "") }
    var lotSize by remember(editingTrade) { mutableStateOf(editingTrade?.lotSize?.toString() ?: "1.0") }
    var riskAmount by remember(editingTrade) { mutableStateOf(editingTrade?.riskAmount?.toString() ?: "") }
    var actualPnl by remember(editingTrade) { mutableStateOf(editingTrade?.actualPnl?.toString() ?: "") }
    var feesText by remember(editingTrade) {
        mutableStateOf(
            if (editingTrade != null && editingTrade.fees > 0.0) editingTrade.fees.toString() else ""
        )
    }
    var tradeStatus by remember(editingTrade) {
        mutableStateOf(editingTrade?.tradeStatusEnum ?: TradeStatus.CLOSED)
    }
    var result by remember(editingTrade) { mutableStateOf(editingTrade?.resultEnum ?: TradeResult.WIN) }
    var strategy by remember(editingTrade) { mutableStateOf(editingTrade?.strategy ?: "Price Action") }
    var entryReason by remember(editingTrade) { mutableStateOf(editingTrade?.entryReason ?: "") }
    var exitReason by remember(editingTrade) { mutableStateOf(editingTrade?.exitReason ?: "") }
    var mistakes by remember(editingTrade) { mutableStateOf(editingTrade?.mistakes ?: "None") }
    var lessonsLearned by remember(editingTrade) { mutableStateOf(editingTrade?.lessonsLearned ?: "") }
    var screenshotData by remember(editingTrade) { mutableStateOf(editingTrade?.screenshotData) }
    var notes by remember(editingTrade) { mutableStateOf(editingTrade?.notes ?: "") }
    var validationError by remember { mutableStateOf<String?>(null) }

    val decodedPreview = rememberDecodedScreenshot(screenshotData)

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = { uri ->
            if (uri != null) {
                scope.launch {
                    val encoded = onEncodeScreenshot(uri)
                    if (encoded != null) screenshotData = encoded
                }
            }
        }
    )

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = if (editingTrade == null) "Log New Trade" else "Edit ${editingTrade.tradeCode}",
                            style = MaterialTheme.typography.titleLarge
                        )
                        Text(
                            text = "Trader: ${user.name} • ${editingTrade?.tradeCode ?: "Auto ID"}",
                            style = MaterialTheme.typography.bodySmall,
                            color = ext.gold
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onCancel, modifier = Modifier.testTag("trade_entry_back")) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(
                modifier = Modifier.widthIn(max = 760.dp).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (validationError != null) {
                    Surface(
                        color = ext.lossContainer,
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, ext.loss),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = validationError.orEmpty(),
                            color = ext.loss,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }

                val entryDateIso = TradeDateTimeHelper.formatDateIso(tradeDateMillis, effectiveTimezoneId)
                val resolvedWeekday = TradeDateTimeHelper.weekdayOf(
                    entryTimeMillis = if (recordExactTimes) entryTimeMillis else null,
                    tradeDateMillis = tradeDateMillis,
                    tradeDateIso = entryDateIso,
                    timezoneId = effectiveTimezoneId
                )
                val previewDurationMillis = if (recordExactTimes && entryTimeMillis != null && exitTimeMillis != null && exitTimeMillis!! >= entryTimeMillis!!) {
                    exitTimeMillis!! - entryTimeMillis!!
                } else null

                Surface(
                    color = ext.cardSurface,
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, ext.cardBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Complete Date & Time Tracking",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                                )
                                Text(
                                    text = "${resolvedWeekday?.label ?: "Day"} • TZ: $effectiveTimezoneId (${TradeDateTimeHelper.shortTimezoneTag(effectiveTimezoneId)})",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = ext.gold
                                )
                            }
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("Exact Time", style = MaterialTheme.typography.labelSmall)
                                Switch(
                                    checked = recordExactTimes,
                                    onCheckedChange = { enabled ->
                                        recordExactTimes = enabled
                                        if (enabled) {
                                            if (entryTimeMillis == null) entryTimeMillis = tradeDateMillis
                                            if (exitTimeMillis == null) exitTimeMillis = entryTimeMillis
                                            if (!sessionManuallyEdited && entryTimeMillis != null) {
                                                tradingSession = TradeDateTimeHelper.suggestSessionForTimestamp(
                                                    entryTimeMillis!!,
                                                    customSessions,
                                                    effectiveTimezoneId
                                                )
                                            }
                                        }
                                    },
                                    modifier = Modifier.testTag("trade_exact_time_switch")
                                )
                            }
                        }

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = { showEntryDatePicker = true },
                                modifier = Modifier.weight(1f).height(48.dp).testTag("trade_date_button"),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Rounded.CalendarToday, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Entry: $entryDateIso", style = MaterialTheme.typography.labelMedium)
                            }
                            if (recordExactTimes) {
                                OutlinedButton(
                                    onClick = { showEntryTimePicker = true },
                                    modifier = Modifier.weight(1f).height(48.dp).testTag("trade_entry_time_button"),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Icon(Icons.Rounded.AccessTime, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    val label = entryTimeMillis?.let { TradeDateTimeHelper.formatTime12h(it, effectiveTimezoneId) } ?: "Set Time"
                                    Text(label, style = MaterialTheme.typography.labelMedium)
                                }
                            }
                        }

                        if (recordExactTimes) {
                            val currentExitMillis = exitTimeMillis ?: entryTimeMillis ?: tradeDateMillis
                            val exitDateIsoPreview = TradeDateTimeHelper.formatDateIso(currentExitMillis, effectiveTimezoneId)
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(
                                    onClick = { showExitDatePicker = true },
                                    modifier = Modifier.weight(1f).height(48.dp).testTag("trade_exit_date_button"),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Icon(Icons.Rounded.CalendarToday, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Exit: $exitDateIsoPreview", style = MaterialTheme.typography.labelMedium)
                                }
                                OutlinedButton(
                                    onClick = { showExitTimePicker = true },
                                    modifier = Modifier.weight(1f).height(48.dp).testTag("trade_exit_time_button"),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Icon(Icons.Rounded.Schedule, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    val label = exitTimeMillis?.let { TradeDateTimeHelper.formatTime12h(it, effectiveTimezoneId) } ?: "Set Exit"
                                    Text(label, style = MaterialTheme.typography.labelMedium)
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val durText = if (!recordExactTimes) {
                                "Clock time omitted (date only)"
                            } else if (previewDurationMillis != null) {
                                "Holding Duration: ${TradeDateTimeHelper.formatDuration(previewDurationMillis)}"
                            } else {
                                "Duration: Set exit >= entry time"
                            }
                            Text(
                                text = durText,
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = if (previewDurationMillis != null) ext.teal else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (recordExactTimes && entryTimeMillis != null) {
                                TextButton(
                                    onClick = {
                                        sessionManuallyEdited = false
                                        tradingSession = TradeDateTimeHelper.suggestSessionForTimestamp(
                                            entryTimeMillis!!,
                                            customSessions,
                                            effectiveTimezoneId
                                        )
                                    }
                                ) {
                                    Text("Auto Session", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }

                        Text("Trading Session", style = MaterialTheme.typography.labelMedium)
                        Row(
                            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            customSessions.forEach { sess ->
                                FilterChip(
                                    selected = tradingSession.equals(sess.name, ignoreCase = true),
                                    onClick = {
                                        tradingSession = if (tradingSession.equals(sess.name, ignoreCase = true)) "" else sess.name
                                        sessionManuallyEdited = true
                                    },
                                    label = { Text("${sess.name} (${sess.windowLabel})") }
                                )
                            }
                        }
                    }
                }

                OutlinedTextField(
                    value = instrument,
                    onValueChange = { typed ->
                        instrument = typed
                        if (!currencyManuallyChosen && typed.isNotBlank()) {
                            currency = TradingAnalytics.suggestCurrencyForInstrument(typed)
                        }
                    },
                    label = { Text("Trading Instrument * (e.g., NIFTY 50, XAUUSD)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("trade_instrument_input")
                )
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    QUICK_INSTRUMENTS.forEach { sym ->
                        FilterChip(
                            selected = instrument.equals(sym, ignoreCase = true),
                            onClick = {
                                instrument = sym
                                if (!currencyManuallyChosen) {
                                    currency = TradingAnalytics.suggestCurrencyForInstrument(sym)
                                }
                            },
                            label = { Text(sym) }
                        )
                    }
                }

                Text("Trade Currency (Original Amount)", style = MaterialTheme.typography.labelLarge)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    TradingCurrency.entries.forEach { curr ->
                        FilterChip(
                            selected = currency == curr,
                            onClick = {
                                currency = curr
                                currencyManuallyChosen = true
                            },
                            label = {
                                Text(
                                    text = if (curr == TradingCurrency.INR) "INR (₹) • Indian Stocks/Index" else "USD ($) • Forex / Global",
                                    fontWeight = FontWeight.SemiBold
                                )
                            },
                            modifier = Modifier.weight(1f).testTag("trade_currency_${curr.code}")
                        )
                    }
                }

                // Historical Exchange Rate card saved per trade
                Surface(
                    color = ext.cardSurface,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, ext.cardBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp).fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(Icons.Rounded.CurrencyExchange, contentDescription = null, tint = ext.gold)
                        OutlinedTextField(
                            value = usdToInrRateText,
                            onValueChange = {
                                usdToInrRateText = it
                                rateManuallyEdited = true
                            },
                            label = { Text("Trade USD → INR Rate (1 USD = ₹)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            modifier = Modifier.weight(1f).testTag("trade_fx_rate_input")
                        )
                        OutlinedButton(
                            onClick = {
                                rateManuallyEdited = false
                                usdToInrRateText = String.format(Locale.US, "%.2f", liveUsdToInrRate)
                                onRefreshRate()
                            },
                            enabled = !isRefreshingRate,
                            modifier = Modifier.height(48.dp)
                        ) {
                            Icon(Icons.Rounded.Refresh, contentDescription = "Apply live FX rate", modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Live")
                        }
                    }
                }

                Text("Direction", style = MaterialTheme.typography.labelLarge)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    TradeDirection.entries.forEach { dir ->
                        val selected = direction == dir
                        FilterChip(
                            selected = selected,
                            onClick = { direction = dir },
                            label = { Text(dir.label, fontWeight = FontWeight.Bold) },
                            modifier = Modifier.weight(1f).testTag("direction_${dir.name}")
                        )
                    }
                }

                Text("Timeframe", style = MaterialTheme.typography.labelLarge)
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Timeframe.entries.forEach { tf ->
                        FilterChip(
                            selected = timeframe == tf,
                            onClick = { timeframe = tf },
                            label = { Text(tf.label) }
                        )
                    }
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = entryPrice,
                        onValueChange = { entryPrice = it },
                        label = { Text("Entry Price *") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f).testTag("trade_entry_price_input")
                    )
                    OutlinedTextField(
                        value = exitPrice,
                        onValueChange = { exitPrice = it },
                        label = {
                            Text(
                                if (tradeStatus == TradeStatus.CLOSED) "Exit Price *"
                                else "Target / Current Price"
                            )
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f).testTag("trade_exit_price_input")
                    )
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = stopLoss,
                        onValueChange = { stopLoss = it },
                        label = { Text("Stop Loss") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = takeProfit,
                        onValueChange = { takeProfit = it },
                        label = { Text("Take Profit") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = lotSize,
                        onValueChange = { lotSize = it },
                        label = { Text("Lot Size *") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = riskAmount,
                        onValueChange = { riskAmount = it },
                        label = { Text("Risk Amount (${currency.symbol})") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                Text("Trade Status", style = MaterialTheme.typography.labelLarge)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    TradeStatus.entries.forEach { st ->
                        FilterChip(
                            selected = tradeStatus == st,
                            onClick = { tradeStatus = st },
                            label = {
                                Text(
                                    text = if (st == TradeStatus.CLOSED) "Closed (Realized)" else "Open (Unrealized)",
                                    fontWeight = FontWeight.SemiBold
                                )
                            },
                            modifier = Modifier.weight(1f).testTag("trade_status_${st.name.lowercase()}")
                        )
                    }
                }
                if (tradeStatus == TradeStatus.OPEN) {
                    Text(
                        text = "Open trades are excluded from weekly realized loss and consecutive Stop Loss risk warnings until closed.",
                        style = MaterialTheme.typography.labelSmall,
                        color = ext.gold
                    )
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = actualPnl,
                        onValueChange = { raw ->
                            actualPnl = raw
                            val parsed = raw.toDoubleOrNull()
                            if (parsed != null) {
                                val fee = feesText.toDoubleOrNull()?.coerceAtLeast(0.0) ?: 0.0
                                val net = parsed - fee
                                result = when {
                                    net > 0.0 -> TradeResult.WIN
                                    net < 0.0 -> TradeResult.LOSS
                                    else -> TradeResult.BREAK_EVEN
                                }
                            }
                        },
                        label = {
                            Text(
                                if (tradeStatus == TradeStatus.CLOSED) "Gross P&L (${currency.symbol}) *"
                                else "Current P&L (${currency.symbol})"
                            )
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f).testTag("trade_pnl_input")
                    )
                    OutlinedTextField(
                        value = feesText,
                        onValueChange = { raw ->
                            feesText = raw
                            val parsedPnl = actualPnl.toDoubleOrNull()
                            if (parsedPnl != null) {
                                val fee = raw.toDoubleOrNull()?.coerceAtLeast(0.0) ?: 0.0
                                val net = parsedPnl - fee
                                result = when {
                                    net > 0.0 -> TradeResult.WIN
                                    net < 0.0 -> TradeResult.LOSS
                                    else -> TradeResult.BREAK_EVEN
                                }
                            }
                        },
                        label = { Text("Fees / Comm (${currency.symbol})") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f).testTag("trade_fees_input")
                    )
                }

                val parsedPnlPreview = actualPnl.toDoubleOrNull()
                val parsedFeesPreview = feesText.toDoubleOrNull()?.coerceAtLeast(0.0) ?: 0.0
                val netPnlPreview = parsedPnlPreview?.let { it - parsedFeesPreview }
                val parsedRatePreview = usdToInrRateText.toDoubleOrNull()
                if (netPnlPreview != null && parsedRatePreview != null && parsedRatePreview > 0.0) {
                    val otherCurr = if (currency == TradingCurrency.INR) TradingCurrency.USD else TradingCurrency.INR
                    val otherVal = TradingAnalytics.convertAmount(netPnlPreview, currency, otherCurr, parsedRatePreview)
                    val prefix = if (tradeStatus == TradeStatus.OPEN) "Unrealized Net" else "Net Realized"
                    Text(
                        text = "$prefix: ${TradingAnalytics.formatCurrency(netPnlPreview, currency)}  •  Equivalent: ${TradingAnalytics.formatCurrency(otherVal, otherCurr)} @ ₹${String.format(Locale.US, "%.2f", parsedRatePreview)}/$",
                        style = MaterialTheme.typography.bodySmall,
                        color = ext.gold
                    )
                }

                Text("Result", style = MaterialTheme.typography.labelLarge)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TradeResult.entries.forEach { res ->
                        FilterChip(
                            selected = result == res,
                            onClick = { result = res },
                            label = { Text(res.label) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                OutlinedTextField(
                    value = strategy,
                    onValueChange = { strategy = it },
                    label = { Text("Trading Strategy") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    QUICK_STRATEGIES.forEach { st ->
                        FilterChip(
                            selected = strategy.equals(st, ignoreCase = true),
                            onClick = { strategy = st },
                            label = { Text(st) }
                        )
                    }
                }

                OutlinedTextField(
                    value = entryReason,
                    onValueChange = { entryReason = it },
                    label = { Text("Entry Reason") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = exitReason,
                    onValueChange = { exitReason = it },
                    label = { Text("Exit Reason") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(
                        "SL Hit" to ExitClosureType.AUTO_SL,
                        "TP Hit" to ExitClosureType.AUTO_TP,
                        "Trailing SL" to ExitClosureType.TRAILING_SL,
                        "Manual Exit" to ExitClosureType.MANUAL,
                        "Break-Even Exit" to ExitClosureType.BREAKEVEN
                    ).forEach { (reason, closure) ->
                        FilterChip(
                            selected = exitReason.equals(reason, ignoreCase = true),
                            onClick = {
                                exitReason = reason
                                exitClosureType = closure
                            },
                            label = { Text(reason) },
                            modifier = Modifier.testTag("exit_reason_${reason.lowercase().replace(" ", "_")}")
                        )
                    }
                }

                Text("Closure Execution Mode", style = MaterialTheme.typography.labelLarge)
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ExitClosureType.entries.filter { it != ExitClosureType.UNKNOWN }.forEach { ct ->
                        FilterChip(
                            selected = exitClosureType == ct,
                            onClick = {
                                exitClosureType = if (exitClosureType == ct) ExitClosureType.UNKNOWN else ct
                            },
                            label = { Text(ct.label) },
                            modifier = Modifier.testTag("closure_type_${ct.name.lowercase()}")
                        )
                    }
                }

                Text("Mistakes (Tap to tag)", style = MaterialTheme.typography.labelLarge)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val currentTags = mistakes.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                    QUICK_MISTAKES.forEach { tag ->
                        val selected = currentTags.any { it.equals(tag, ignoreCase = true) }
                        FilterChip(
                            selected = selected,
                            onClick = {
                                mistakes = if (tag == "None") {
                                    "None"
                                } else {
                                    val mutable = currentTags.filterNot { it.equals("None", ignoreCase = true) }.toMutableList()
                                    if (selected) mutable.removeAll { it.equals(tag, ignoreCase = true) }
                                    else mutable.add(tag)
                                    if (mutable.isEmpty()) "None" else mutable.joinToString(", ")
                                }
                            },
                            label = { Text(tag) }
                        )
                    }
                }
                OutlinedTextField(
                    value = mistakes,
                    onValueChange = { mistakes = it },
                    label = { Text("Mistakes Details") },
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = lessonsLearned,
                    onValueChange = { lessonsLearned = it },
                    label = { Text("Lessons Learned") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Additional Notes") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )

                Text("Chart Screenshot", style = MaterialTheme.typography.labelLarge)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = {
                            photoPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        modifier = Modifier.weight(1f).height(48.dp).testTag("upload_screenshot_button")
                    ) {
                        Icon(Icons.Rounded.Image, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(if (screenshotData == null) "Upload Chart Screenshot" else "Replace Screenshot")
                    }
                    if (screenshotData != null) {
                        IconButton(onClick = { screenshotData = null }) {
                            Icon(Icons.Rounded.Delete, contentDescription = "Remove screenshot", tint = ext.loss)
                        }
                    }
                }
                if (decodedPreview != null) {
                    Image(
                        bitmap = decodedPreview,
                        contentDescription = "Selected chart screenshot",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(170.dp)
                            .clip(RoundedCornerShape(14.dp))
                    )
                }

                Spacer(Modifier.height(6.dp))
                GradientButton(
                    text = if (editingTrade == null) "Save Trade Entry" else "Update Trade Entry",
                    icon = Icons.Rounded.Save,
                    colors = listOf(ext.profit, ext.teal),
                    onClick = {
                        val ep = entryPrice.toDoubleOrNull()
                        val rawXp = exitPrice.toDoubleOrNull()
                        val xp = if (tradeStatus == TradeStatus.OPEN && exitPrice.isBlank() && ep != null) ep else rawXp
                        val ls = lotSize.toDoubleOrNull()
                        val rawPnl = actualPnl.toDoubleOrNull()
                        val pnl = if (tradeStatus == TradeStatus.OPEN && actualPnl.isBlank()) 0.0 else rawPnl
                        val fees = feesText.takeIf { it.isNotBlank() }?.toDoubleOrNull()
                        val fxRate = usdToInrRateText.toDoubleOrNull()
                        val risk = riskAmount.toDoubleOrNull()?.coerceAtLeast(0.0) ?: 0.0
                        val sl = stopLoss.takeIf { it.isNotBlank() }?.toDoubleOrNull()
                        val tp = takeProfit.takeIf { it.isNotBlank() }?.toDoubleOrNull()

                        val resolvedEntryTime = if (recordExactTimes) entryTimeMillis else null
                        val resolvedExitTime = if (recordExactTimes) exitTimeMillis else null
                        val resolvedTradeDateIso = TradeDateTimeHelper.formatDateIso(
                            resolvedEntryTime ?: tradeDateMillis,
                            effectiveTimezoneId
                        )
                        val resolvedExitDateIso = resolvedExitTime?.let {
                            TradeDateTimeHelper.formatDateIso(it, effectiveTimezoneId)
                        } ?: ""

                        when {
                            instrument.isBlank() -> validationError = "Please enter the Trading Instrument."
                            fxRate == null || fxRate < 1.0 || fxRate > 1000.0 -> validationError = "Please enter a valid USD → INR exchange rate (1 - 1000)."
                            ep == null || ep <= 0.0 -> validationError = "Please enter a valid Entry Price (> 0)."
                            tradeStatus == TradeStatus.CLOSED && (xp == null || xp <= 0.0) -> validationError = "Please enter a valid Exit Price (> 0)."
                            tradeStatus == TradeStatus.OPEN && xp == null -> validationError = "Please enter a valid Price (>= 0)."
                            ls == null || ls <= 0.0 -> validationError = "Please enter a valid Lot Size (> 0)."
                            tradeStatus == TradeStatus.CLOSED && pnl == null -> validationError = "Please enter Actual Profit / Loss (e.g., 150 or -75)."
                            feesText.isNotBlank() && (fees == null || fees < 0.0) -> validationError = "Fees / Commissions must be >= 0."
                            stopLoss.isNotBlank() && (sl == null || sl < 0.0) -> validationError = "Stop Loss must be >= 0."
                            takeProfit.isNotBlank() && (tp == null || tp < 0.0) -> validationError = "Take Profit must be >= 0."
                            resolvedEntryTime != null && resolvedExitTime != null && resolvedExitTime < resolvedEntryTime ->
                                validationError = "Exit date & time must be on or after Entry date & time."
                            else -> {
                                validationError = null
                                onSave(
                                    editingTrade?.id,
                                    resolvedEntryTime ?: tradeDateMillis,
                                    resolvedTradeDateIso,
                                    instrument,
                                    direction,
                                    timeframe,
                                    currency,
                                    fxRate,
                                    ep,
                                    xp ?: ep,
                                    sl,
                                    tp,
                                    ls,
                                    risk,
                                    pnl ?: 0.0,
                                    fees ?: 0.0,
                                    tradeStatus,
                                    result,
                                    strategy,
                                    entryReason,
                                    exitReason,
                                    mistakes,
                                    lessonsLearned,
                                    screenshotData,
                                    notes,
                                    resolvedEntryTime,
                                    resolvedExitDateIso,
                                    resolvedExitTime,
                                    tradingSession,
                                    exitClosureType,
                                    effectiveTimezoneId
                                )
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth().testTag("save_trade_button")
                )
                Spacer(Modifier.height(24.dp))
            }
        }
    }

    if (showEntryDatePicker) {
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = tradeDateMillis)
        DatePickerDialog(
            onDismissRequest = { showEntryDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { pickedDate ->
                        val (h, m) = (entryTimeMillis ?: tradeDateMillis).let {
                            TradeDateTimeHelper.hourAndMinuteOf(it, effectiveTimezoneId)
                        }
                        val utcFmt = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
                            timeZone = java.util.TimeZone.getTimeZone("UTC")
                        }
                        val utcDateMillis = TradeDateTimeHelper.parseDateIsoStartMillis(
                            utcFmt.format(Date(pickedDate)),
                            effectiveTimezoneId
                        ) ?: pickedDate
                        val combined = TradeDateTimeHelper.combineDateAndTimeMillis(utcDateMillis, h, m, effectiveTimezoneId)
                        tradeDateMillis = combined
                        if (recordExactTimes) {
                            entryTimeMillis = combined
                            if (exitTimeMillis != null && exitTimeMillis!! < combined) {
                                exitTimeMillis = combined
                            }
                        }
                    }
                    showEntryDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showEntryDatePicker = false }) { Text("Cancel") }
            }
        ) {
            DatePicker(state = pickerState)
        }
    }

    if (showExitDatePicker) {
        val baseExit = exitTimeMillis ?: entryTimeMillis ?: tradeDateMillis
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = baseExit)
        DatePickerDialog(
            onDismissRequest = { showExitDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { pickedDate ->
                        val (h, m) = TradeDateTimeHelper.hourAndMinuteOf(baseExit, effectiveTimezoneId)
                        val utcFmt = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
                            timeZone = java.util.TimeZone.getTimeZone("UTC")
                        }
                        val utcDateMillis = TradeDateTimeHelper.parseDateIsoStartMillis(
                            utcFmt.format(Date(pickedDate)),
                            effectiveTimezoneId
                        ) ?: pickedDate
                        exitTimeMillis = TradeDateTimeHelper.combineDateAndTimeMillis(utcDateMillis, h, m, effectiveTimezoneId)
                    }
                    showExitDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showExitDatePicker = false }) { Text("Cancel") }
            }
        ) {
            DatePicker(state = pickerState)
        }
    }

    if (showEntryTimePicker) {
        val base = entryTimeMillis ?: tradeDateMillis
        val (initHour, initMin) = TradeDateTimeHelper.hourAndMinuteOf(base, effectiveTimezoneId)
        val timeState = rememberTimePickerState(initialHour = initHour, initialMinute = initMin)
        AlertDialog(
            onDismissRequest = { showEntryTimePicker = false },
            title = { Text("Entry Time ($effectiveTimezoneId)") },
            text = { TimePicker(state = timeState) },
            confirmButton = {
                TextButton(onClick = {
                    val updated = TradeDateTimeHelper.combineDateAndTimeMillis(
                        base, timeState.hour, timeState.minute, effectiveTimezoneId
                    )
                    entryTimeMillis = updated
                    tradeDateMillis = updated
                    if (!sessionManuallyEdited) {
                        tradingSession = TradeDateTimeHelper.suggestSessionForTimestamp(
                            updated, customSessions, effectiveTimezoneId
                        )
                    }
                    if (exitTimeMillis != null && exitTimeMillis!! < updated) {
                        exitTimeMillis = updated
                    }
                    showEntryTimePicker = false
                }) { Text("Set") }
            },
            dismissButton = {
                TextButton(onClick = { showEntryTimePicker = false }) { Text("Cancel") }
            }
        )
    }

    if (showExitTimePicker) {
        val base = exitTimeMillis ?: entryTimeMillis ?: tradeDateMillis
        val (initHour, initMin) = TradeDateTimeHelper.hourAndMinuteOf(base, effectiveTimezoneId)
        val timeState = rememberTimePickerState(initialHour = initHour, initialMinute = initMin)
        AlertDialog(
            onDismissRequest = { showExitTimePicker = false },
            title = { Text("Exit Time ($effectiveTimezoneId)") },
            text = { TimePicker(state = timeState) },
            confirmButton = {
                TextButton(onClick = {
                    exitTimeMillis = TradeDateTimeHelper.combineDateAndTimeMillis(
                        base, timeState.hour, timeState.minute, effectiveTimezoneId
                    )
                    showExitTimePicker = false
                }) { Text("Set") }
            },
            dismissButton = {
                TextButton(onClick = { showExitTimePicker = false }) { Text("Cancel") }
            }
        )
    }
}
