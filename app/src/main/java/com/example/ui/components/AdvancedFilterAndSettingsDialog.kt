package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarToday
import androidx.compose.material.icons.rounded.ClearAll
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.FilterAlt
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.local.CustomTradingSession
import com.example.data.model.Timeframe
import com.example.data.model.TradeDurationBucket
import com.example.data.model.TradeResult
import com.example.data.model.WeekdayFilter
import com.example.domain.AdvancedTradeFilter
import com.example.domain.TradeDateTimeHelper
import com.example.ui.theme.LocalTradingColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class FilterDateTarget {
    SPECIFIC_DATE,
    RANGE_START,
    RANGE_END
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AdvancedFilterPanelCard(
    filter: AdvancedTradeFilter,
    availableInstruments: List<String>,
    availableStrategies: List<String>,
    availableSessions: List<String>,
    timezoneId: String,
    matchingCount: Int,
    totalCount: Int,
    onUpdateFilter: ((AdvancedTradeFilter) -> AdvancedTradeFilter) -> Unit,
    onClearFilter: () -> Unit,
    modifier: Modifier = Modifier
) {
    val ext = LocalTradingColors.current
    var expanded by remember { mutableStateOf(false) }
    var dateTarget by remember { mutableStateOf<FilterDateTarget?>(null) }

    Card(
        modifier = modifier.fillMaxWidth().testTag("advanced_filter_card"),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = ext.cardSurface),
        border = BorderStroke(1.dp, if (filter.isActive) ext.gold else ext.cardBorder)
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
                    Icon(Icons.Rounded.FilterAlt, contentDescription = null, tint = ext.gold, modifier = Modifier.size(20.dp))
                    Column {
                        Text(
                            text = if (filter.isActive) "Dashboard Filters (${filter.activeCount} active)" else "Dashboard Filters",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = "Showing $matchingCount of $totalCount trades",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                if (filter.isActive) {
                    TextButton(onClick = onClearFilter, modifier = Modifier.testTag("clear_advanced_filters")) {
                        Icon(Icons.Rounded.ClearAll, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Reset")
                    }
                }
                OutlinedButton(
                    onClick = { expanded = !expanded },
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.height(40.dp).testTag("toggle_advanced_filters")
                ) {
                    Text(if (expanded) "Hide" else "Filter")
                    Spacer(Modifier.width(4.dp))
                    Icon(
                        imageVector = if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            AnimatedVisibility(visible = expanded) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    HorizontalDivider(color = ext.cardBorder)

                    Text("Date & Date Range", style = MaterialTheme.typography.labelMedium, color = ext.gold)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedButton(
                            onClick = { dateTarget = FilterDateTarget.SPECIFIC_DATE },
                            modifier = Modifier.weight(1f).height(44.dp)
                        ) {
                            Icon(Icons.Rounded.CalendarToday, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(filter.specificDateIso ?: "Single Day", style = MaterialTheme.typography.labelSmall)
                        }
                        OutlinedButton(
                            onClick = { dateTarget = FilterDateTarget.RANGE_START },
                            modifier = Modifier.weight(1f).height(44.dp)
                        ) {
                            Text(filter.dateStartIso ?: "From Date", style = MaterialTheme.typography.labelSmall)
                        }
                        OutlinedButton(
                            onClick = { dateTarget = FilterDateTarget.RANGE_END },
                            modifier = Modifier.weight(1f).height(44.dp)
                        ) {
                            Text(filter.dateEndIso ?: "To Date", style = MaterialTheme.typography.labelSmall)
                        }
                    }

                    val startMin = (filter.timeStartMinutes ?: 0).toFloat()
                    val endMin = (filter.timeEndMinutes ?: 1439).toFloat()
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Exact Time Range (${TradeDateTimeHelper.shortTimezoneTag(timezoneId)})", style = MaterialTheme.typography.labelMedium, color = ext.gold)
                        Text(
                            text = "${CustomTradingSession.formatMinute(startMin.toInt())} – ${CustomTradingSession.formatMinute(endMin.toInt())}",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                    RangeSlider(
                        value = startMin..endMin,
                        onValueChange = { range ->
                            val s = range.start.toInt().coerceIn(0, 1439)
                            val e = range.endInclusive.toInt().coerceIn(s, 1439)
                            onUpdateFilter { f ->
                                if (s == 0 && e >= 1435) f.copy(timeStartMinutes = null, timeEndMinutes = null)
                                else f.copy(timeStartMinutes = s, timeEndMinutes = e)
                            }
                        },
                        valueRange = 0f..1439f
                    )

                    Text("Day of Week", style = MaterialTheme.typography.labelMedium)
                    Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        WeekdayFilter.entries.forEach { wd ->
                            val sel = wd in filter.weekdays
                            FilterChip(
                                selected = sel,
                                onClick = {
                                    onUpdateFilter { f ->
                                        f.copy(weekdays = if (sel) f.weekdays - wd else f.weekdays + wd)
                                    }
                                },
                                label = { Text(wd.shortLabel) }
                            )
                        }
                    }

                    if (availableSessions.isNotEmpty()) {
                        Text("Trading Session", style = MaterialTheme.typography.labelMedium)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            availableSessions.forEach { sess ->
                                val sel = sess in filter.sessions
                                FilterChip(
                                    selected = sel,
                                    onClick = {
                                        onUpdateFilter { f ->
                                            f.copy(sessions = if (sel) f.sessions - sess else f.sessions + sess)
                                        }
                                    },
                                    label = { Text(sess) }
                                )
                            }
                        }
                    }

                    if (availableInstruments.isNotEmpty()) {
                        Text("Index / Instrument", style = MaterialTheme.typography.labelMedium)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            availableInstruments.forEach { inst ->
                                val sel = inst in filter.instruments
                                FilterChip(
                                    selected = sel,
                                    onClick = {
                                        onUpdateFilter { f ->
                                            f.copy(instruments = if (sel) f.instruments - inst else f.instruments + inst)
                                        }
                                    },
                                    label = { Text(inst) }
                                )
                            }
                        }
                    }

                    Text("Timeframe", style = MaterialTheme.typography.labelMedium)
                    Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Timeframe.entries.forEach { tf ->
                            val sel = tf in filter.timeframes
                            FilterChip(
                                selected = sel,
                                onClick = {
                                    onUpdateFilter { f ->
                                        f.copy(timeframes = if (sel) f.timeframes - tf else f.timeframes + tf)
                                    }
                                },
                                label = { Text(tf.label) }
                            )
                        }
                    }

                    if (availableStrategies.isNotEmpty()) {
                        Text("Strategy", style = MaterialTheme.typography.labelMedium)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            availableStrategies.forEach { st ->
                                val sel = st in filter.strategies
                                FilterChip(
                                    selected = sel,
                                    onClick = {
                                        onUpdateFilter { f ->
                                            f.copy(strategies = if (sel) f.strategies - st else f.strategies + st)
                                        }
                                    },
                                    label = { Text(st) }
                                )
                            }
                        }
                    }

                    Text("Trade Result", style = MaterialTheme.typography.labelMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        TradeResult.entries.forEach { res ->
                            val sel = res in filter.results
                            FilterChip(
                                selected = sel,
                                onClick = {
                                    onUpdateFilter { f ->
                                        f.copy(results = if (sel) f.results - res else f.results + res)
                                    }
                                },
                                label = { Text(res.label) }
                            )
                        }
                    }

                    Text("Trade Duration", style = MaterialTheme.typography.labelMedium)
                    Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        TradeDurationBucket.entries.forEach { b ->
                            val sel = b in filter.durationBuckets
                            FilterChip(
                                selected = sel,
                                onClick = {
                                    onUpdateFilter { f ->
                                        f.copy(durationBuckets = if (sel) f.durationBuckets - b else f.durationBuckets + b)
                                    }
                                },
                                label = { Text(b.label) }
                            )
                        }
                    }
                }
            }
        }
    }

    dateTarget?.let { target ->
        val pickerState = rememberDatePickerState()
        DatePickerDialog(
            onDismissRequest = { dateTarget = null },
            confirmButton = {
                TextButton(onClick = {
                    val iso = pickerState.selectedDateMillis?.let { millis ->
                        SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
                            timeZone = java.util.TimeZone.getTimeZone("UTC")
                        }.format(Date(millis))
                    }
                    if (iso != null) {
                        onUpdateFilter { f ->
                            when (target) {
                                FilterDateTarget.SPECIFIC_DATE -> f.copy(specificDateIso = iso, dateStartIso = null, dateEndIso = null)
                                FilterDateTarget.RANGE_START -> f.copy(specificDateIso = null, dateStartIso = iso)
                                FilterDateTarget.RANGE_END -> f.copy(specificDateIso = null, dateEndIso = iso)
                            }
                        }
                    }
                    dateTarget = null
                }) { Text("Apply") }
            },
            dismissButton = {
                TextButton(onClick = { dateTarget = null }) { Text("Cancel") }
            }
        ) {
            DatePicker(state = pickerState)
        }
    }
}

@Composable
fun TimezoneAndSessionConfigDialog(
    selectedTimezoneId: String,
    customSessions: List<CustomTradingSession>,
    onSelectTimezone: (String) -> Unit,
    onSaveSessions: (List<CustomTradingSession>) -> Unit,
    onResetDefaultSessions: () -> Unit,
    onDismiss: () -> Unit
) {
    val editableSessions = remember(customSessions) {
        mutableStateListOf<CustomTradingSession>().apply { addAll(customSessions) }
    }
    var newName by remember { mutableStateOf("") }
    var newStartHour by remember { mutableStateOf("09") }
    var newStartMin by remember { mutableStateOf("15") }
    var newEndHour by remember { mutableStateOf("15") }
    var newEndMin by remember { mutableStateOf("30") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Rounded.Schedule, contentDescription = null)
                Text("Timezone & Session Times")
            }
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("Display & Analysis Timezone", style = MaterialTheme.typography.labelMedium)
                Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    CustomTradingSession.SUPPORTED_TIMEZONES.forEach { (tzId, label) ->
                        FilterChip(
                            selected = selectedTimezoneId == tzId,
                            onClick = { onSelectTimezone(tzId) },
                            label = { Text(label) }
                        )
                    }
                }
                HorizontalDivider()
                Text("Active Trading Sessions", style = MaterialTheme.typography.labelMedium)
                editableSessions.forEachIndexed { idx, s ->
                    Surface(shape = RoundedCornerShape(8.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(s.name, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold))
                                Text(s.windowLabel, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            if (editableSessions.size > 1) {
                                TextButton(onClick = { editableSessions.removeAt(idx) }) { Text("Remove") }
                            }
                        }
                    }
                }
                Text("Add Custom Session Window (24h)", style = MaterialTheme.typography.labelMedium)
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text("Session Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedTextField(value = newStartHour, onValueChange = { newStartHour = it }, label = { Text("From H") }, singleLine = true, modifier = Modifier.weight(1f))
                    OutlinedTextField(value = newStartMin, onValueChange = { newStartMin = it }, label = { Text("M") }, singleLine = true, modifier = Modifier.weight(1f))
                    OutlinedTextField(value = newEndHour, onValueChange = { newEndHour = it }, label = { Text("To H") }, singleLine = true, modifier = Modifier.weight(1f))
                    OutlinedTextField(value = newEndMin, onValueChange = { newEndMin = it }, label = { Text("M") }, singleLine = true, modifier = Modifier.weight(1f))
                }
                OutlinedButton(
                    onClick = {
                        val sh = newStartHour.toIntOrNull()?.coerceIn(0, 23) ?: 9
                        val sm = newStartMin.toIntOrNull()?.coerceIn(0, 59) ?: 0
                        val eh = newEndHour.toIntOrNull()?.coerceIn(0, 23) ?: 15
                        val em = newEndMin.toIntOrNull()?.coerceIn(0, 59) ?: 30
                        if (newName.isNotBlank()) {
                            editableSessions.add(
                                CustomTradingSession(
                                    id = "custom_${System.currentTimeMillis()}",
                                    name = newName.trim().take(40),
                                    startMinuteOfDay = sh * 60 + sm,
                                    endMinuteOfDay = eh * 60 + em
                                )
                            )
                            newName = ""
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Add Session")
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                onSaveSessions(editableSessions.toList())
                onDismiss()
            }) { Text("Save") }
        },
        dismissButton = {
            Row {
                TextButton(onClick = {
                    onResetDefaultSessions()
                    onDismiss()
                }) { Text("Defaults") }
                TextButton(onClick = onDismiss) { Text("Close") }
            }
        }
    )
}
