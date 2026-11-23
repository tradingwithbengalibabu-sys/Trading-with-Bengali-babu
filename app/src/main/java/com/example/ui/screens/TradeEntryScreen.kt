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
import androidx.compose.material.icons.rounded.CalendarToday
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Save
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
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
import com.example.data.model.Timeframe
import com.example.data.model.TradeDirection
import com.example.data.model.TradeEntity
import com.example.data.model.TradeResult
import com.example.data.model.UserEntity
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
    onEncodeScreenshot: suspend (Uri) -> String?,
    onSave: (
        existingTradeId: String?,
        tradeDateMillis: Long,
        tradeDateIso: String,
        instrument: String,
        direction: TradeDirection,
        timeframe: Timeframe,
        entryPrice: Double,
        exitPrice: Double,
        stopLoss: Double?,
        takeProfit: Double?,
        lotSize: Double,
        riskAmount: Double,
        actualPnl: Double,
        result: TradeResult,
        strategy: String,
        entryReason: String,
        exitReason: String,
        mistakes: String,
        lessonsLearned: String,
        screenshotData: String?,
        notes: String
    ) -> Unit,
    onCancel: () -> Unit
) {
    BackHandler(onBack = onCancel)

    val ext = LocalTradingColors.current
    val scope = rememberCoroutineScope()
    val isoFmt = remember { SimpleDateFormat("yyyy-MM-dd", Locale.US) }

    var tradeDateMillis by remember(editingTrade) {
        mutableLongStateOf(editingTrade?.tradeDateMillis ?: System.currentTimeMillis())
    }
    var showDatePicker by remember { mutableStateOf(false) }

    var instrument by remember(editingTrade) { mutableStateOf(editingTrade?.instrument ?: "") }
    var direction by remember(editingTrade) { mutableStateOf(editingTrade?.directionEnum ?: TradeDirection.BUY) }
    var timeframe by remember(editingTrade) { mutableStateOf(editingTrade?.timeframeEnum ?: Timeframe.M15) }
    var entryPrice by remember(editingTrade) { mutableStateOf(editingTrade?.entryPrice?.toString() ?: "") }
    var exitPrice by remember(editingTrade) { mutableStateOf(editingTrade?.exitPrice?.toString() ?: "") }
    var stopLoss by remember(editingTrade) { mutableStateOf(editingTrade?.stopLoss?.toString() ?: "") }
    var takeProfit by remember(editingTrade) { mutableStateOf(editingTrade?.takeProfit?.toString() ?: "") }
    var lotSize by remember(editingTrade) { mutableStateOf(editingTrade?.lotSize?.toString() ?: "1.0") }
    var riskAmount by remember(editingTrade) { mutableStateOf(editingTrade?.riskAmount?.toString() ?: "") }
    var actualPnl by remember(editingTrade) { mutableStateOf(editingTrade?.actualPnl?.toString() ?: "") }
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

                OutlinedButton(
                    onClick = { showDatePicker = true },
                    modifier = Modifier.fillMaxWidth().height(52.dp).testTag("trade_date_button"),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Rounded.CalendarToday, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Trade Date: ${isoFmt.format(Date(tradeDateMillis))}")
                }

                OutlinedTextField(
                    value = instrument,
                    onValueChange = { instrument = it },
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
                            onClick = { instrument = sym },
                            label = { Text(sym) }
                        )
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
                        label = { Text("Exit Price *") },
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
                        label = { Text("Risk Amount ($)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                OutlinedTextField(
                    value = actualPnl,
                    onValueChange = { raw ->
                        actualPnl = raw
                        val parsed = raw.toDoubleOrNull()
                        if (parsed != null) {
                            result = when {
                                parsed > 0.0 -> TradeResult.WIN
                                parsed < 0.0 -> TradeResult.LOSS
                                else -> TradeResult.BREAK_EVEN
                            }
                        }
                    },
                    label = { Text("Actual Profit / Loss ($) * (use - for loss)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("trade_pnl_input")
                )

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
                        val xp = exitPrice.toDoubleOrNull()
                        val ls = lotSize.toDoubleOrNull()
                        val pnl = actualPnl.toDoubleOrNull()
                        val risk = riskAmount.toDoubleOrNull()?.coerceAtLeast(0.0) ?: 0.0
                        val sl = stopLoss.takeIf { it.isNotBlank() }?.toDoubleOrNull()
                        val tp = takeProfit.takeIf { it.isNotBlank() }?.toDoubleOrNull()

                        when {
                            instrument.isBlank() -> validationError = "Please enter the Trading Instrument."
                            ep == null || ep <= 0.0 -> validationError = "Please enter a valid Entry Price (> 0)."
                            xp == null || xp <= 0.0 -> validationError = "Please enter a valid Exit Price (> 0)."
                            ls == null || ls <= 0.0 -> validationError = "Please enter a valid Lot Size (> 0)."
                            pnl == null -> validationError = "Please enter Actual Profit / Loss (e.g., 150 or -75)."
                            stopLoss.isNotBlank() && (sl == null || sl < 0.0) -> validationError = "Stop Loss must be >= 0."
                            takeProfit.isNotBlank() && (tp == null || tp < 0.0) -> validationError = "Take Profit must be >= 0."
                            else -> {
                                validationError = null
                                onSave(
                                    editingTrade?.id,
                                    tradeDateMillis,
                                    isoFmt.format(Date(tradeDateMillis)),
                                    instrument,
                                    direction,
                                    timeframe,
                                    ep,
                                    xp,
                                    sl,
                                    tp,
                                    ls,
                                    risk,
                                    pnl,
                                    result,
                                    strategy,
                                    entryReason,
                                    exitReason,
                                    mistakes,
                                    lessonsLearned,
                                    screenshotData,
                                    notes
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

    if (showDatePicker) {
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = tradeDateMillis)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { tradeDateMillis = it }
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
            }
        ) {
            DatePicker(state = pickerState)
        }
    }
}
