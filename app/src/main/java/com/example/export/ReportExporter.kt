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
import com.example.domain.DashboardMetrics
import com.example.domain.TradingAnalytics
import java.io.File
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object ReportExporter {

    fun writeCsvReport(outputStream: OutputStream, trades: List<TradeEntity>) {
        val header = listOf(
            "Trade ID", "Trader Name", "Date", "Instrument", "Direction", "Timeframe",
            "Entry Price", "Exit Price", "Stop Loss", "Take Profit", "Lot Size",
            "Risk Amount", "Actual P&L", "Result", "Strategy", "Entry Reason",
            "Exit Reason", "Mistakes", "Lessons Learned", "Notes"
        ).joinToString(",")

        val sb = StringBuilder()
        // UTF-8 BOM so Microsoft Excel opens currency & symbols cleanly
        sb.append('\uFEFF')
        sb.appendLine(header)
        trades.forEach { t ->
            val row = listOf(
                t.tradeCode,
                t.userName,
                t.tradeDateIso,
                t.instrument,
                t.direction.label,
                t.timeframe.label,
                t.entryPrice.toString(),
                t.exitPrice.toString(),
                t.stopLoss?.toString() ?: "",
                t.takeProfit?.toString() ?: "",
                t.lotSize.toString(),
                t.riskAmount.toString(),
                t.actualPnl.toString(),
                t.result.label,
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
        trades: List<TradeEntity>
    ) {
        val pdf = PdfDocument()
        val pageWidth = 595 // A4 width in pt
        val pageHeight = 842 // A4 height in pt
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
            textSize = 9.5f
        }
        val boldBodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(15, 23, 42)
            textSize = 9.5f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val greenPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(5, 150, 105)
            textSize = 9.5f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val redPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(220, 38, 38)
            textSize = 9.5f
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

        // Header banner
        canvas.drawRect(0f, 0f, pageWidth.toFloat(), 94f, navyPaint)
        canvas.drawRect(0f, 94f, pageWidth.toFloat(), 98f, goldPaint)
        if (logoBitmap != null) {
            canvas.drawBitmap(logoBitmap, null, Rect(24, 14, 90, 80), null)
        }
        canvas.drawText("TRADING WITH BANGALI BABU", 104f, 38f, whiteTitlePaint)
        canvas.drawText(reportTitle.uppercase(Locale.US), 104f, 56f, subtitlePaint)
        val genTime = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date())
        canvas.drawText("Trader: $traderLabel  |  Period: $dateRangeLabel  |  Generated: $genTime", 104f, 76f,
            Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(203, 213, 225); textSize = 8.5f })

        var y = 122f
        canvas.drawText("Performance Summary", margin, y, sectionPaint)
        y += 12f

        val kpis = listOf(
            "Total Trades" to metrics.totalTrades.toString(),
            "Win Rate" to String.format(Locale.US, "%.1f%%", metrics.winRatePercent),
            "Net P&L" to TradingAnalytics.formatCurrency(metrics.netPnl),
            "Profit Factor" to String.format(Locale.US, "%.2f", metrics.profitFactor),
            "Total Profit" to TradingAnalytics.formatPlainCurrency(metrics.totalProfit),
            "Total Loss" to TradingAnalytics.formatPlainCurrency(metrics.totalLoss),
            "Avg Win / Loss" to "${TradingAnalytics.formatPlainCurrency(metrics.averageWin)} / ${TradingAnalytics.formatPlainCurrency(metrics.averageLoss)}",
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
                        kpis[idx].first == "Net P&L" && metrics.netPnl >= 0 -> greenPaint
                        kpis[idx].first == "Net P&L" && metrics.netPnl < 0 -> redPaint
                        else -> boldBodyPaint
                    }
                    canvas.drawText(kpis[idx].second, left + 10f, rowTop + 25f, valPaint)
                }
            }
            y += 38f
        }

        y += 12f
        canvas.drawText("Trade Execution Ledger (${trades.size} trades)", margin, y, sectionPaint)
        y += 14f

        fun drawTableHeader() {
            canvas.drawRect(margin, y, pageWidth - margin, y + 20f, navyPaint)
            val hp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                textSize = 8.5f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            canvas.drawText("ID", margin + 6f, y + 13f, hp)
            canvas.drawText("Date", margin + 66f, y + 13f, hp)
            canvas.drawText("Symbol", margin + 128f, y + 13f, hp)
            canvas.drawText("Side/TF", margin + 204f, y + 13f, hp)
            canvas.drawText("Lot", margin + 266f, y + 13f, hp)
            canvas.drawText("P&L", margin + 312f, y + 13f, hp)
            canvas.drawText("Result", margin + 382f, y + 13f, hp)
            canvas.drawText("Strategy", margin + 440f, y + 13f, hp)
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
            canvas.drawText(t.tradeCode.take(10), margin + 6f, y + 11f, boldBodyPaint)
            canvas.drawText(t.tradeDateIso, margin + 66f, y + 11f, bodyPaint)
            canvas.drawText(t.instrument.take(12), margin + 128f, y + 11f, boldBodyPaint)
            canvas.drawText("${t.direction.label}/${t.timeframe.label}", margin + 204f, y + 11f, bodyPaint)
            canvas.drawText(String.format(Locale.US, "%.2f", t.lotSize), margin + 266f, y + 11f, bodyPaint)
            val pnlPaint = if (t.actualPnl >= 0) greenPaint else redPaint
            canvas.drawText(TradingAnalytics.formatCurrency(t.actualPnl), margin + 312f, y + 11f, pnlPaint)
            canvas.drawText(t.result.label, margin + 382f, y + 11f, bodyPaint)
            canvas.drawText(t.strategy.take(16).ifEmpty { "-" }, margin + 440f, y + 11f, bodyPaint)
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
