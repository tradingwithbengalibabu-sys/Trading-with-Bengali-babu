package com.example.export

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import com.example.R
import com.example.data.model.TradeEntity
import com.example.data.model.TradingCurrency
import com.example.domain.DashboardMetrics
import com.example.domain.PeriodPerformanceReport
import com.example.domain.TradeDateTimeHelper
import com.example.domain.TradingAnalytics
import java.io.File
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object ReportExporter {

    fun writeCsvReport(
        outputStream: OutputStream,
        trades: List<TradeEntity>,
        displayCurrency: TradingCurrency = TradingCurrency.USD,
        timezoneId: String = TradeDateTimeHelper.DEFAULT_TIMEZONE_ID
    ) {
        val dispCode = displayCurrency.code
        val header = listOf(
            "Trade ID", "Trader Name", "Date", "Day of Week",
            "Entry Time", "Exit Date", "Exit Time", "Holding Duration",
            "Trading Session", "Closure Type", "Timezone",
            "Instrument", "Direction", "Timeframe",
            "Currency", "USD/INR Rate",
            "Entry Price", "Exit Price", "Stop Loss", "Take Profit", "Lot Size",
            "Original Risk", "Original P&L",
            "Converted Risk ($dispCode)", "Converted P&L ($dispCode)",
            "Result", "Strategy", "Entry Reason",
            "Exit Reason", "Mistakes", "Lessons Learned", "Notes"
        ).joinToString(",")

        val sb = StringBuilder()
        sb.append('\uFEFF')
        sb.appendLine(header)
        trades.forEach { t ->
            val convRisk = TradingAnalytics.convertedRisk(t, displayCurrency)
            val convPnl = TradingAnalytics.convertedPnl(t, displayCurrency)
            val weekdayLabel = TradeDateTimeHelper.weekdayOf(t.entryTimeMillis, t.tradeDateMillis, t.tradeDateIso, timezoneId)?.label ?: ""
            val entryTimeStr = t.entryTimeMillis?.takeIf { it > 0L }?.let { TradeDateTimeHelper.formatTime24h(it, timezoneId) } ?: "Not recorded"
            val exitDateStr = t.exitDateIso.ifBlank {
                t.exitTimeMillis?.takeIf { it > 0L }?.let { TradeDateTimeHelper.formatDateIso(it, timezoneId) } ?: ""
            }
            val exitTimeStr = t.exitTimeMillis?.takeIf { it > 0L }?.let { TradeDateTimeHelper.formatTime24h(it, timezoneId) } ?: "Not recorded"
            val durationStr = t.holdingDurationMillis?.let { TradeDateTimeHelper.formatDuration(it) } ?: "Not recorded"
            val row = listOf(
                t.tradeCode,
                t.userName,
                t.tradeDateIso,
                weekdayLabel,
                entryTimeStr,
                exitDateStr,
                exitTimeStr,
                durationStr,
                t.tradingSession.ifBlank { "Unassigned" },
                t.resolvedExitClosureType.label,
                t.effectiveTimezoneId,
                t.instrument,
                t.directionEnum.label,
                t.timeframeEnum.label,
                t.currencyEnum.code,
                String.format(Locale.US, "%.4f", t.effectiveUsdToInrRate),
                t.entryPrice.toString(),
                t.exitPrice.toString(),
                t.stopLoss?.toString() ?: "",
                t.takeProfit?.toString() ?: "",
                t.lotSize.toString(),
                String.format(Locale.US, "%.2f", t.riskAmount),
                String.format(Locale.US, "%.2f", t.actualPnl),
                String.format(Locale.US, "%.2f", convRisk),
                String.format(Locale.US, "%.2f", convPnl),
                t.resultEnum.label,
                t.strategy,
                t.entryReason,
                t.exitReason,
                t.mistakes,
                t.lessonsLearned,
                t.notes
            ).joinToString(",") { escapeCsv(it) }
            sb.appendLine(row)
        }
        outputStream.write(sb.toString().toByteArray(Charsets.UTF_8))
        outputStream.flush()
    }

    fun writePeriodAnalyticsCsvReport(
        outputStream: OutputStream,
        report: PeriodPerformanceReport,
        traderLabel: String,
        timezoneId: String = TradeDateTimeHelper.DEFAULT_TIMEZONE_ID
    ) {
        val dc = report.displayCurrency
        val sb = StringBuilder()
        sb.append('\uFEFF')
        sb.appendLine("Report,${escapeCsv(report.periodTitle)}")
        sb.appendLine("Trader,${escapeCsv(traderLabel)}")
        sb.appendLine("Period,${escapeCsv(report.dateRangeIso)}")
        sb.appendLine("Display Currency,${dc.code}")
        sb.appendLine("Timezone,${escapeCsv(timezoneId)}")
        sb.appendLine("Gross Profit,${String.format(Locale.US, "%.2f", report.grossProfit)}")
        sb.appendLine("Gross Loss,${String.format(Locale.US, "%.2f", report.grossLoss)}")
        sb.appendLine("Net P&L,${String.format(Locale.US, "%.2f", report.netPnl)}")
        sb.appendLine("Total Trades,${report.totalTrades}")
        sb.appendLine("Win Rate %,${String.format(Locale.US, "%.1f", report.winRatePercent)}")
        sb.appendLine("Average Win,${String.format(Locale.US, "%.2f", report.averageWin)}")
        sb.appendLine("Average Loss,${String.format(Locale.US, "%.2f", report.averageLoss)}")
        sb.appendLine("Best Trading Time,${escapeCsv(report.bestTradingTime)}")
        sb.appendLine("Worst Trading Time,${escapeCsv(report.worstTradingTime)}")
        sb.appendLine("Best Index/Instrument,${escapeCsv(report.bestInstrument)}")
        sb.appendLine("Worst Index/Instrument,${escapeCsv(report.worstInstrument)}")
        sb.appendLine("Best Strategy,${escapeCsv(report.bestStrategy)}")
        sb.appendLine("Worst Strategy,${escapeCsv(report.worstStrategy)}")
        sb.appendLine("Best Timeframe,${escapeCsv(report.bestTimeframe)}")
        sb.appendLine("Worst Timeframe,${escapeCsv(report.worstTimeframe)}")
        sb.appendLine()
        outputStream.write(sb.toString().toByteArray(Charsets.UTF_8))
        writeCsvReport(
            outputStream = outputStream,
            trades = report.trades.map { it.trade },
            displayCurrency = dc,
            timezoneId = timezoneId
        )
    }

    private fun escapeCsv(value: String): String {
        val cleaned = value.replace("\r", " ").replace("\n", " ")
        return if (cleaned.contains(",") || cleaned.contains("\"")) {
            "\"${cleaned.replace("\"", "\"\"")}\""
        } else {
            cleaned
        }
    }

    fun writePdfReport(
        context: Context,
        outputStream: OutputStream,
        reportTitle: String,
        traderLabel: String,
        dateRangeLabel: String,
        metrics: DashboardMetrics,
        trades: List<TradeEntity>,
        periodHighlights: PeriodPerformanceReport? = null,
        timezoneId: String = TradeDateTimeHelper.DEFAULT_TIMEZONE_ID
    ) {
        val displayCurrency = metrics.displayCurrency
        val pdf = PdfDocument()
        val pageWidth = 595
        val pageHeight = 842
        val margin = 32f

        val navyPaint = Paint().apply { color = Color.rgb(10, 22, 40) }
        val goldPaint = Paint().apply { color = Color.rgb(245, 158, 11) }
        val whiteTitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 18f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val subtitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(251, 191, 36)
            textSize = 10f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val sectionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(15, 23, 42)
            textSize = 13f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(51, 65, 85)
            textSize = 9f
        }
        val boldBodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(15, 23, 42)
            textSize = 9f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val greenPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(5, 150, 105)
            textSize = 9f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val redPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(220, 38, 38)
            textSize = 9f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val cardBgPaint = Paint().apply { color = Color.rgb(241, 245, 249) }
        val dividerPaint = Paint().apply {
            color = Color.rgb(203, 213, 225)
            strokeWidth = 1f
        }

        val logoBitmap: Bitmap? = runCatching {
            BitmapFactory.decodeResource(context.resources, R.drawable.brand_logo)
        }.getOrNull()

        var pageNumber = 1
        var page = pdf.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create())
        var canvas: Canvas = page.canvas

        canvas.drawRect(0f, 0f, pageWidth.toFloat(), 94f, navyPaint)
        canvas.drawRect(0f, 94f, pageWidth.toFloat(), 98f, goldPaint)
        if (logoBitmap != null) {
            canvas.drawBitmap(logoBitmap, null, Rect(24, 14, 90, 80), null)
        }
        canvas.drawText("TRADING WITH BANGALI BABU", 104f, 38f, whiteTitlePaint)
        canvas.drawText("${reportTitle.uppercase(Locale.US)} • ${displayCurrency.code}", 104f, 56f, subtitlePaint)
        val genTime = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date())
        canvas.drawText("Trader: $traderLabel | Period: $dateRangeLabel | Display: ${displayCurrency.label} | $genTime", 104f, 76f,
            Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(203, 213, 225); textSize = 8f })

        var y = 122f
        canvas.drawText("Performance Summary (${displayCurrency.label})", margin, y, sectionPaint)
        y += 12f

        val kpis = listOf(
            "Total Trades" to "${metrics.totalTrades} (${metrics.inrTradeCount} INR / ${metrics.usdTradeCount} USD)",
            "Win Rate" to String.format(Locale.US, "%.1f%%", metrics.winRatePercent),
            "Net P&L (${displayCurrency.code})" to TradingAnalytics.formatCurrency(metrics.netPnl, displayCurrency),
            "Profit Factor" to String.format(Locale.US, "%.2f", metrics.profitFactor),
            "Total Profit (${displayCurrency.code})" to TradingAnalytics.formatPlainCurrency(metrics.totalProfit, displayCurrency),
            "Total Loss (${displayCurrency.code})" to TradingAnalytics.formatPlainCurrency(metrics.totalLoss, displayCurrency),
            "Avg Win / Loss" to "${TradingAnalytics.formatPlainCurrency(metrics.averageWin, displayCurrency)} / ${TradingAnalytics.formatPlainCurrency(metrics.averageLoss, displayCurrency)}",
            "Avg R:R | Streak" to "${String.format(Locale.US, "%.2f", metrics.averageRiskReward)}R | ${metrics.currentStreak.label}"
        )

        val colWidth = (pageWidth - margin * 2 - 12f) / 2f
        for (i in kpis.indices step 2) {
            val rowTop = y
            val rowBottom = y + 32f
            for (c in 0..1) {
                val idx = i + c
                if (idx < kpis.size) {
                    val left = margin + c * (colWidth + 12f)
                    canvas.drawRoundRect(left, rowTop, left + colWidth, rowBottom, 6f, 6f, cardBgPaint)
                    canvas.drawText(kpis[idx].first, left + 10f, rowTop + 12f, bodyPaint)
                    val valPaint = when {
                        kpis[idx].first.startsWith("Net P&L") && metrics.netPnl >= 0 -> greenPaint
                        kpis[idx].first.startsWith("Net P&L") && metrics.netPnl < 0 -> redPaint
                        else -> boldBodyPaint
                    }
                    canvas.drawText(kpis[idx].second, left + 10f, rowTop + 25f, valPaint)
                }
            }
            y += 38f
        }

        if (periodHighlights != null) {
            y += 6f
            canvas.drawText("Time, Strategy & Instrument Breakdown ($timezoneId)", margin, y, sectionPaint)
            y += 12f
            val highlights = listOf(
                "Best Trading Time" to periodHighlights.bestTradingTime,
                "Worst Trading Time" to periodHighlights.worstTradingTime,
                "Best Index/Instrument" to periodHighlights.bestInstrument,
                "Worst Index/Instrument" to periodHighlights.worstInstrument,
                "Best Strategy" to periodHighlights.bestStrategy,
                "Worst Strategy" to periodHighlights.worstStrategy,
                "Best Timeframe" to periodHighlights.bestTimeframe,
                "Worst Timeframe" to periodHighlights.worstTimeframe
            )
            for (i in highlights.indices step 2) {
                val rowTop = y
                val rowBottom = y + 28f
                for (c in 0..1) {
                    val idx = i + c
                    if (idx < highlights.size) {
                        val left = margin + c * (colWidth + 12f)
                        canvas.drawRoundRect(left, rowTop, left + colWidth, rowBottom, 6f, 6f, cardBgPaint)
                        canvas.drawText(highlights[idx].first, left + 8f, rowTop + 11f, bodyPaint)
                        canvas.drawText(highlights[idx].second.take(36), left + 8f, rowTop + 22f, boldBodyPaint)
                    }
                }
                y += 32f
            }
        }

        y += 10f
        canvas.drawText("Trade Execution Ledger (${trades.size} trades • Converted to ${displayCurrency.code})", margin, y, sectionPaint)
        y += 14f

        fun drawTableHeader() {
            canvas.drawRect(margin, y, pageWidth - margin, y + 20f, navyPaint)
            val hp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                textSize = 7.5f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            canvas.drawText("ID", margin + 4f, y + 13f, hp)
            canvas.drawText("Date / Time", margin + 56f, y + 13f, hp)
            canvas.drawText("Dur.", margin + 138f, y + 13f, hp)
            canvas.drawText("Symbol", margin + 178f, y + 13f, hp)
            canvas.drawText("Side/TF", margin + 242f, y + 13f, hp)
            canvas.drawText("Orig P&L", margin + 292f, y + 13f, hp)
            canvas.drawText("P&L (${displayCurrency.code})", margin + 368f, y + 13f, hp)
            canvas.drawText("Closure", margin + 448f, y + 13f, hp)
            y += 24f
        }

        drawTableHeader()

        trades.forEach { t ->
            if (y > pageHeight - 48f) {
                pdf.finishPage(page)
                pageNumber++
                page = pdf.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create())
                canvas = page.canvas
                y = 36f
                drawTableHeader()
            }
            val convPnl = TradingAnalytics.convertedPnl(t, displayCurrency)
            val pnlPaint = if (convPnl >= 0) greenPaint else redPaint
            val timePart = t.entryTimeMillis?.takeIf { it > 0L }?.let {
                TradeDateTimeHelper.formatTime24h(it, timezoneId)
            } ?: "--:--"
            val durPart = t.holdingDurationMillis?.let {
                TradeDateTimeHelper.formatDuration(it)
            } ?: "N/A"
            canvas.drawText(t.tradeCode.take(9), margin + 4f, y + 11f, boldBodyPaint)
            canvas.drawText("${t.tradeDateIso} $timePart", margin + 56f, y + 11f, bodyPaint)
            canvas.drawText(durPart.take(7), margin + 138f, y + 11f, bodyPaint)
            canvas.drawText(t.instrument.take(10), margin + 178f, y + 11f, boldBodyPaint)
            canvas.drawText("${t.directionEnum.label}/${t.timeframeEnum.label}", margin + 242f, y + 11f, bodyPaint)
            canvas.drawText(TradingAnalytics.formatCurrency(t.actualPnl, t.currencyEnum), margin + 292f, y + 11f, bodyPaint)
            canvas.drawText(TradingAnalytics.formatCurrency(convPnl, displayCurrency), margin + 368f, y + 11f, pnlPaint)
            canvas.drawText(t.resolvedExitClosureType.label.take(12), margin + 448f, y + 11f, bodyPaint)
            y += 17f
            canvas.drawLine(margin, y, pageWidth - margin, y, dividerPaint)
            y += 4f
        }

        pdf.finishPage(page)
        pdf.writeTo(outputStream)
        outputStream.flush()
        pdf.close()
    }

    fun createShareableReportFile(
        context: Context,
        fileName: String,
        writer: (OutputStream) -> Unit
    ): Intent {
        val reportsDir = File(context.cacheDir, "reports").apply { mkdirs() }
        val file = File(reportsDir, fileName)
        file.outputStream().use { writer(it) }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val mime = when {
            fileName.endsWith(".pdf", true) -> "application/pdf"
            fileName.endsWith(".csv", true) -> "text/csv"
            else -> "application/json"
        }
        return Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "Trading with Bangali Babu - $fileName")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }
}
