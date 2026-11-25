package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.TradingCurrency
import com.example.domain.CategoryPerformance
import com.example.domain.DayPnlSummary
import com.example.domain.EquityPoint
import com.example.domain.MonthlyPnlSummary
import com.example.domain.TradingAnalytics
import com.example.ui.theme.LocalTradingColors
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max

@Composable
fun ChartCardContainer(
    title: String,
    subtitle: String,
    accentColor: Color,
    modifier: Modifier = Modifier,
    headerRight: @Composable (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    val ext = LocalTradingColors.current
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = ext.cardSurface),
        border = BorderStroke(1.dp, ext.cardBorder),
        elevation = CardDefaults.cardElevation(defaultElevation = if (ext.isDark) 3.dp else 1.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
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
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(accentColor)
                    )
                    Column {
                        Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                if (headerRight != null) {
                    headerRight()
                }
            }
            content()
        }
    }
}

@Composable
fun DailyPnlBarChart(
    series: List<DayPnlSummary>,
    displayCurrency: TradingCurrency = TradingCurrency.USD,
    modifier: Modifier = Modifier
) {
    val ext = LocalTradingColors.current
    val visible = series.takeLast(14)
    var selectedIdx by remember(visible) { mutableIntStateOf(visible.lastIndex) }

    ChartCardContainer(
        title = "Daily Profit & Loss (${displayCurrency.code})",
        subtitle = "Tap any bar to inspect daily net P&L",
        accentColor = ext.electricBlue,
        modifier = modifier,
        headerRight = {
            if (visible.isNotEmpty() && selectedIdx in visible.indices) {
                val item = visible[selectedIdx]
                val c = if (item.netPnl >= 0) ext.profit else ext.loss
                Surface(color = c.copy(alpha = 0.14f), shape = RoundedCornerShape(8.dp)) {
                    Text(
                        text = "${item.dateIso.takeLast(5)}: ${TradingAnalytics.formatCurrency(item.netPnl, displayCurrency)}",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = c,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
        }
    ) {
        if (visible.isEmpty()) {
            ChartEmptyNotice("Log trades to view daily Profit & Loss bars.")
        } else {
            val maxAbs = max(1.0, visible.maxOf { abs(it.netPnl) })
            val gridColor = ext.cardBorder
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(175.dp)
                    .pointerInput(visible) {
                        detectTapGestures { offset ->
                            val step = size.width.toFloat() / visible.size
                            val idx = (offset.x / step).toInt().coerceIn(0, visible.lastIndex)
                            selectedIdx = idx
                        }
                    }
            ) {
                val zeroY = size.height / 2f
                drawLine(
                    color = gridColor,
                    start = Offset(0f, zeroY),
                    end = Offset(size.width, zeroY),
                    strokeWidth = 2f
                )
                val slotWidth = size.width / visible.size
                val barWidth = (slotWidth * 0.58f).coerceAtMost(38f)

                visible.forEachIndexed { idx, item ->
                    val ratio = (abs(item.netPnl) / maxAbs).toFloat().coerceIn(0.05f, 1f)
                    val barHeight = ratio * (size.height * 0.44f)
                    val left = idx * slotWidth + (slotWidth - barWidth) / 2f
                    val top = if (item.netPnl >= 0) zeroY - barHeight else zeroY
                    val color = if (item.netPnl >= 0) ext.profit else ext.loss
                    val alpha = if (idx == selectedIdx) 1f else 0.72f

                    drawRoundRect(
                        color = color.copy(alpha = alpha),
                        topLeft = Offset(left, top),
                        size = Size(barWidth, barHeight),
                        cornerRadius = CornerRadius(6f, 6f)
                    )
                }
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(visible.first().dateIso.takeLast(5), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (selectedIdx in visible.indices) {
                    Text(
                        "${visible[selectedIdx].tradeCount} trades on ${visible[selectedIdx].dateIso}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(visible.last().dateIso.takeLast(5), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun EquityCurveChart(
    curve: List<EquityPoint>,
    displayCurrency: TradingCurrency = TradingCurrency.USD,
    modifier: Modifier = Modifier
) {
    val ext = LocalTradingColors.current
    var selectedIdx by remember(curve) { mutableIntStateOf(curve.lastIndex) }

    ChartCardContainer(
        title = "Trading History Equity Curve (${displayCurrency.code})",
        subtitle = "Cumulative account P&L progression",
        accentColor = ext.gold,
        modifier = modifier,
        headerRight = {
            if (curve.isNotEmpty() && selectedIdx in curve.indices) {
                val pt = curve[selectedIdx]
                val c = if (pt.cumulativePnl >= 0) ext.profit else ext.loss
                Surface(color = c.copy(alpha = 0.14f), shape = RoundedCornerShape(8.dp)) {
                    Text(
                        text = "${pt.tradeCode}: ${TradingAnalytics.formatCurrency(pt.cumulativePnl, displayCurrency)}",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = c,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
        }
    ) {
        if (curve.isEmpty()) {
            ChartEmptyNotice("Your equity growth curve will plot automatically from closed trades.")
        } else {
            val minVal = minOf(0.0, curve.minOf { it.cumulativePnl })
            val maxVal = maxOf(1.0, curve.maxOf { it.cumulativePnl })
            val span = max(1.0, maxVal - minVal)
            val lineColor = if (curve.last().cumulativePnl >= 0) ext.profit else ext.loss
            val gridColor = ext.cardBorder

            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(175.dp)
                    .pointerInput(curve) {
                        detectTapGestures { offset ->
                            if (curve.size == 1) {
                                selectedIdx = 0
                            } else {
                                val step = size.width / (curve.size - 1)
                                val idx = ((offset.x + step / 2f) / step).toInt().coerceIn(0, curve.lastIndex)
                                selectedIdx = idx
                            }
                        }
                    }
            ) {
                val topPad = 14f
                val bottomPad = 14f
                val plotH = size.height - topPad - bottomPad
                val zeroY = topPad + ((maxVal - 0.0) / span).toFloat() * plotH

                drawLine(
                    color = gridColor,
                    start = Offset(0f, zeroY),
                    end = Offset(size.width, zeroY),
                    strokeWidth = 1.5f
                )

                val points = curve.mapIndexed { idx, pt ->
                    val x = if (curve.size == 1) size.width / 2f else idx.toFloat() * size.width / (curve.size - 1)
                    val y = topPad + ((maxVal - pt.cumulativePnl) / span).toFloat() * plotH
                    Offset(x, y)
                }

                if (points.size >= 2) {
                    val linePath = Path().apply {
                        moveTo(points.first().x, points.first().y)
                        for (i in 1 until points.size) {
                            val prev = points[i - 1]
                            val curr = points[i]
                            val midX = (prev.x + curr.x) / 2f
                            cubicTo(midX, prev.y, midX, curr.y, curr.x, curr.y)
                        }
                    }
                    val fillPath = Path().apply {
                        addPath(linePath)
                        lineTo(points.last().x, zeroY)
                        lineTo(points.first().x, zeroY)
                        close()
                    }
                    drawPath(
                        path = fillPath,
                        brush = Brush.verticalGradient(
                            colors = listOf(lineColor.copy(alpha = 0.30f), Color.Transparent),
                            startY = topPad,
                            endY = size.height
                        )
                    )
                    drawPath(
                        path = linePath,
                        color = lineColor,
                        style = Stroke(width = 5f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                    )
                }

                points.forEachIndexed { idx, offset ->
                    val isSel = idx == selectedIdx
                    drawCircle(
                        color = if (isSel) ext.gold else lineColor,
                        radius = if (isSel) 9f else 4.5f,
                        center = offset
                    )
                }
            }
            if (selectedIdx in curve.indices) {
                val pt = curve[selectedIdx]
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Date: ${pt.dateIso}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        "Trade P&L: ${TradingAnalytics.formatCurrency(pt.tradePnl, displayCurrency)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (pt.tradePnl >= 0) ext.profit else ext.loss
                    )
                }
            }
        }
    }
}

@Composable
fun WinLossDonutChart(
    wins: Int,
    losses: Int,
    breakEvens: Int,
    winRatePercent: Double,
    modifier: Modifier = Modifier
) {
    val ext = LocalTradingColors.current
    val total = wins + losses + breakEvens

    ChartCardContainer(
        title = "Win vs Loss Breakdown",
        subtitle = "Outcome distribution across all saved trades",
        accentColor = ext.purple,
        modifier = modifier
    ) {
        if (total == 0) {
            ChartEmptyNotice("No trade outcomes recorded yet.")
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                Box(
                    modifier = Modifier.size(136.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(modifier = Modifier.size(126.dp)) {
                        val strokeW = 24f
                        val winSweep = (wins.toFloat() / total) * 360f
                        val lossSweep = (losses.toFloat() / total) * 360f
                        val beSweep = 360f - winSweep - lossSweep

                        var start = -90f
                        if (winSweep > 0f) {
                            drawArc(
                                color = ext.profit,
                                startAngle = start,
                                sweepAngle = winSweep,
                                useCenter = false,
                                style = Stroke(width = strokeW, cap = StrokeCap.Butt)
                            )
                            start += winSweep
                        }
                        if (lossSweep > 0f) {
                            drawArc(
                                color = ext.loss,
                                startAngle = start,
                                sweepAngle = lossSweep,
                                useCenter = false,
                                style = Stroke(width = strokeW, cap = StrokeCap.Butt)
                            )
                            start += lossSweep
                        }
                        if (beSweep > 0f) {
                            drawArc(
                                color = ext.gold,
                                startAngle = start,
                                sweepAngle = beSweep,
                                useCenter = false,
                                style = Stroke(width = strokeW, cap = StrokeCap.Butt)
                            )
                        }
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = String.format(Locale.US, "%.1f%%", winRatePercent),
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Win Rate",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    LegendStatRow("Winning Trades", wins, total, ext.profit)
                    LegendStatRow("Losing Trades", losses, total, ext.loss)
                    LegendStatRow("Break Even", breakEvens, total, ext.gold)
                }
            }
        }
    }
}

@Composable
private fun LegendStatRow(label: String, count: Int, total: Int, color: Color) {
    val pct = if (total > 0) (count.toDouble() / total) * 100.0 else 0.0
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(color))
            Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
        }
        Text(
            text = "$count (${String.format(Locale.US, "%.0f%%", pct)})",
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
            color = color
        )
    }
}

@Composable
fun MonthlyPerformanceChart(
    series: List<MonthlyPnlSummary>,
    displayCurrency: TradingCurrency = TradingCurrency.USD,
    modifier: Modifier = Modifier
) {
    val ext = LocalTradingColors.current
    val visible = series.takeLast(6)
    var selectedIdx by remember(visible) { mutableIntStateOf(visible.lastIndex) }

    ChartCardContainer(
        title = "Monthly Performance (${displayCurrency.code})",
        subtitle = "Gross profit vs gross loss by month",
        accentColor = ext.teal,
        modifier = modifier,
        headerRight = {
            if (visible.isNotEmpty() && selectedIdx in visible.indices) {
                val m = visible[selectedIdx]
                val c = if (m.netPnl >= 0) ext.profit else ext.loss
                Surface(color = c.copy(alpha = 0.14f), shape = RoundedCornerShape(8.dp)) {
                    Text(
                        text = "${m.label}: ${TradingAnalytics.formatCurrency(m.netPnl, displayCurrency)}",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = c,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
        }
    ) {
        if (visible.isEmpty()) {
            ChartEmptyNotice("Monthly profit & loss comparisons will appear here.")
        } else {
            val maxVal = max(1.0, visible.maxOf { max(it.grossProfit, it.grossLoss) })
            val gridColor = ext.cardBorder
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp)
                    .pointerInput(visible) {
                        detectTapGestures { offset ->
                            val step = size.width / visible.size
                            selectedIdx = (offset.x / step).toInt().coerceIn(0, visible.lastIndex)
                        }
                    }
            ) {
                val baseY = size.height - 8f
                drawLine(gridColor, Offset(0f, baseY), Offset(size.width, baseY), strokeWidth = 2f)
                val slotW = size.width / visible.size
                val barW = (slotW * 0.28f).coerceAtMost(28f)

                visible.forEachIndexed { idx, m ->
                    val centerX = idx * slotW + slotW / 2f
                    val profitH = ((m.grossProfit / maxVal).toFloat().coerceIn(0f, 1f)) * (size.height - 20f)
                    val lossH = ((m.grossLoss / maxVal).toFloat().coerceIn(0f, 1f)) * (size.height - 20f)
                    val alpha = if (idx == selectedIdx) 1f else 0.72f

                    if (profitH > 0f) {
                        drawRoundRect(
                            color = ext.profit.copy(alpha = alpha),
                            topLeft = Offset(centerX - barW - 3f, baseY - profitH),
                            size = Size(barW, profitH),
                            cornerRadius = CornerRadius(5f, 5f)
                        )
                    }
                    if (lossH > 0f) {
                        drawRoundRect(
                            color = ext.loss.copy(alpha = alpha),
                            topLeft = Offset(centerX + 3f, baseY - lossH),
                            size = Size(barW, lossH),
                            cornerRadius = CornerRadius(5f, 5f)
                        )
                    }
                }
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
                visible.forEachIndexed { idx, m ->
                    Text(
                        text = m.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (idx == selectedIdx) ext.gold else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
fun CategoryBreakdownChart(
    title: String,
    subtitle: String,
    accentColor: Color,
    items: List<CategoryPerformance>,
    displayCurrency: TradingCurrency = TradingCurrency.USD,
    modifier: Modifier = Modifier
) {
    val ext = LocalTradingColors.current
    val topItems = items.take(8)
    ChartCardContainer(
        title = title,
        subtitle = subtitle,
        accentColor = accentColor,
        modifier = modifier
    ) {
        if (topItems.isEmpty()) {
            ChartEmptyNotice("No performance breakdown available yet.")
        } else {
            val maxAbs = max(1.0, topItems.maxOf { abs(it.netPnl) })
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                topItems.forEach { item ->
                    val barColor = if (item.netPnl >= 0) ext.profit else ext.loss
                    val ratio = (abs(item.netPnl) / maxAbs).toFloat().coerceIn(0.06f, 1f)
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${item.category} (${item.tradeCount} • ${String.format(Locale.US, "%.0f%%", item.winRate)} WR)",
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = TradingAnalytics.formatCurrency(item.netPnl, displayCurrency),
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = barColor
                            )
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(999.dp))
                                .background(ext.cardElevated)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(ratio)
                                    .height(8.dp)
                                    .clip(RoundedCornerShape(999.dp))
                                    .background(
                                        Brush.horizontalGradient(
                                            listOf(barColor.copy(alpha = 0.75f), barColor)
                                        )
                                    )
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChartEmptyNotice(message: String) {
    val ext = LocalTradingColors.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(96.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(ext.cardElevated.copy(alpha = 0.55f)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
    }
}
