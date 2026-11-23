package com.example

import com.example.data.model.Timeframe
import com.example.data.model.TradeDirection
import com.example.data.model.TradeEntity
import com.example.data.model.TradeResult
import com.example.data.model.UserEntity
import com.example.data.model.UserRole
import com.example.domain.TradingAnalytics
import com.example.export.ReportExporter
import com.example.security.PasswordHasher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream

class ExampleUnitTest {
    @Test
    fun analyticsCalculatesAllMetricsAccuratelyFromSavedTrades() {
        val user = UserEntity(
            id = 1L,
            name = "Trader",
            email = "trader@test.com",
            passwordHash = "h",
            passwordSalt = "s",
            role = UserRole.STUDENT,
            dailyProfitGoal = 200.0,
            dailyMaxLossLimit = 100.0,
            maxTradesPerDay = 2,
            maxRiskPerTrade = 50.0
        )

        val trades = listOf(
            TradeEntity(
                id = 1L,
                tradeCode = "TWBB-0001",
                userId = 1L,
                userName = "Trader",
                tradeDateMillis = 1000L,
                tradeDateIso = "2026-11-20",
                instrument = "NIFTY 50",
                direction = TradeDirection.BUY,
                timeframe = Timeframe.M15,
                entryPrice = 24000.0,
                exitPrice = 24100.0,
                stopLoss = 23950.0,
                takeProfit = 24150.0,
                lotSize = 1.0,
                riskAmount = 50.0,
                actualPnl = 200.0,
                result = TradeResult.WIN,
                strategy = "Breakout",
                entryReason = "Support bounce",
                exitReason = "Target hit",
                mistakes = "None",
                lessonsLearned = "Patience pays",
                screenshotPath = null,
                notes = ""
            ),
            TradeEntity(
                id = 2L,
                tradeCode = "TWBB-0002",
                userId = 1L,
                userName = "Trader",
                tradeDateMillis = 2000L,
                tradeDateIso = "2026-11-21",
                instrument = "XAUUSD",
                direction = TradeDirection.SELL,
                timeframe = Timeframe.H1,
                entryPrice = 2650.0,
                exitPrice = 2660.0,
                stopLoss = 2660.0,
                takeProfit = 2630.0,
                lotSize = 0.5,
                riskAmount = 50.0,
                actualPnl = -80.0,
                result = TradeResult.LOSS,
                strategy = "Price Action",
                entryReason = "Rejection",
                exitReason = "SL hit",
                mistakes = "FOMO",
                lessonsLearned = "Wait for candle close",
                screenshotPath = null,
                notes = ""
            ),
            TradeEntity(
                id = 3L,
                tradeCode = "TWBB-0003",
                userId = 1L,
                userName = "Trader",
                tradeDateMillis = 3000L,
                tradeDateIso = "2026-11-21",
                instrument = "XAUUSD",
                direction = TradeDirection.BUY,
                timeframe = Timeframe.H1,
                entryPrice = 2655.0,
                exitPrice = 2685.0,
                stopLoss = 2645.0,
                takeProfit = 2685.0,
                lotSize = 0.5,
                riskAmount = 40.0,
                actualPnl = 120.0,
                result = TradeResult.WIN,
                strategy = "SMC",
                entryReason = "OB tap",
                exitReason = "TP",
                mistakes = "None",
                lessonsLearned = "Clean execution",
                screenshotPath = null,
                notes = ""
            )
        )

        val metrics = TradingAnalytics.calculateDashboardMetrics(trades)
        assertEquals(3, metrics.totalTrades)
        assertEquals(2, metrics.winningTrades)
        assertEquals(1, metrics.losingTrades)
        assertEquals(0, metrics.breakEvenTrades)
        assertEquals(66.666, metrics.winRatePercent, 0.1)
        assertEquals(33.333, metrics.lossRatePercent, 0.1)
        assertEquals(320.0, metrics.totalProfit, 0.01)
        assertEquals(80.0, metrics.totalLoss, 0.01)
        assertEquals(240.0, metrics.netPnl, 0.01)
        assertEquals(160.0, metrics.averageWin, 0.01)
        assertEquals(80.0, metrics.averageLoss, 0.01)
        assertEquals(4.0, metrics.profitFactor, 0.01)
        assertEquals("2026-11-20", metrics.bestTradingDay?.dateIso)
        assertEquals("2026-11-21", metrics.worstTradingDay?.dateIso)
        assertEquals("1W Streak", metrics.currentStreak.label)
        assertEquals(2, metrics.instrumentPerformance.size)
        assertEquals(2, metrics.timeframePerformance.size)

        val discipline = TradingAnalytics.calculateDisciplineMetrics(trades, user)
        assertEquals(100.0, discipline.stopLossDisciplinePercent, 0.01)
        assertEquals(100.0, discipline.riskCompliancePercent, 0.01)
        assertTrue(discipline.mistakeCounts.any { it.first == "FOMO" && it.second == 1 })

        // Verify CSV export formatting
        val csvOut = ByteArrayOutputStream()
        ReportExporter.writeCsvReport(csvOut, trades)
        val csvText = csvOut.toString(Charsets.UTF_8.name())
        assertTrue(csvText.contains("TWBB-0001"))
        assertTrue(csvText.contains("NIFTY 50"))
    }

    @Test
    fun passwordHasherVerifiesValidAndRejectsInvalidPasswords() {
        val salt = PasswordHasher.generateSalt()
        val hash = PasswordHasher.hashPassword("BangaliSecret99", salt)
        assertTrue(PasswordHasher.verifyPassword("BangaliSecret99", salt, hash))
        assertFalse(PasswordHasher.verifyPassword("WrongPass", salt, hash))
    }
}
