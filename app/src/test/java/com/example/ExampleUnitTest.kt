package com.example

import com.example.data.local.CustomTradingSession
import com.example.data.model.AnalysisPeriodType
import com.example.data.model.AnnouncementEntity
import com.example.data.model.AnnouncementPriority
import com.example.data.model.ConversationThread
import com.example.data.model.DirectMessageEntity
import com.example.data.model.ExitClosureType
import com.example.data.model.SmartAlertType
import com.example.data.model.StudentWarningEntity
import com.example.data.model.TimeOfDayInterval
import com.example.data.model.Timeframe
import com.example.data.model.TradeDirection
import com.example.data.model.TradeDurationBucket
import com.example.data.model.TradeEntity
import com.example.data.model.TradeResult
import com.example.data.model.TradingCurrency
import com.example.data.model.UserEntity
import com.example.data.model.UserRole
import com.example.data.model.WarningSeverity
import com.example.data.model.WeekdayFilter
import com.example.domain.AdvancedTradeAnalytics
import com.example.domain.AdvancedTradeFilter
import com.example.domain.CalendarDayStatus
import com.example.domain.SmartDisciplineEngine
import com.example.domain.TradeDateTimeHelper
import com.example.domain.TradingAnalytics
import com.example.export.ReportExporter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream

class ExampleUnitTest {
    @Test
    fun analyticsCalculatesMixedCurrencyMetricsAccuratelyWithHistoricalRates() {
        val user = UserEntity(
            id = "uid_1",
            userId = "uid_1",
            name = "Trader",
            email = "trader@test.com",
            role = UserRole.STUDENT.name,
            preferredCurrency = TradingCurrency.USD.name,
            dailyProfitGoal = 200.0,
            dailyMaxLossLimit = 100.0,
            maxTradesPerDay = 2,
            maxRiskPerTrade = 50.0
        )

        // Mixed INR + USD trades, each preserving its original currency and historical USD->INR FX rate:
        // t1: NIFTY 50 (INR) +8,350 INR @ 83.50 => +$100.00 USD, Risk 4,175 INR => $50.00 USD
        // t2: XAUUSD (USD) -$80.00 USD @ 84.00 => -₹6,720.00 INR, Risk $50.00 USD => ₹4,200.00 INR
        // t3: XAUUSD (USD) +$120.00 USD @ 85.00 => +₹10,200.00 INR, Risk $40.00 USD => ₹3,400.00 INR
        val trades = listOf(
            TradeEntity(
                id = "t1",
                tradeCode = "TWBB-0001",
                userId = "uid_1",
                userName = "Trader",
                tradeDateMillis = 1000L,
                tradeDateIso = "2026-11-20",
                instrument = "NIFTY 50",
                direction = TradeDirection.BUY.name,
                timeframe = Timeframe.M15.name,
                currency = TradingCurrency.INR.name,
                usdToInrRate = 83.50,
                entryPrice = 24000.0,
                exitPrice = 24100.0,
                stopLoss = 23950.0,
                takeProfit = 24150.0,
                lotSize = 1.0,
                riskAmount = 4175.0,
                actualPnl = 8350.0,
                result = TradeResult.WIN.name,
                strategy = "Breakout",
                entryReason = "Support bounce",
                exitReason = "Target hit",
                mistakes = "None",
                lessonsLearned = "Patience pays",
                screenshotData = null,
                notes = ""
            ),
            TradeEntity(
                id = "t2",
                tradeCode = "TWBB-0002",
                userId = "uid_1",
                userName = "Trader",
                tradeDateMillis = 2000L,
                tradeDateIso = "2026-11-21",
                instrument = "XAUUSD",
                direction = TradeDirection.SELL.name,
                timeframe = Timeframe.H1.name,
                currency = TradingCurrency.USD.name,
                usdToInrRate = 84.00,
                entryPrice = 2650.0,
                exitPrice = 2660.0,
                stopLoss = 2660.0,
                takeProfit = 2630.0,
                lotSize = 0.5,
                riskAmount = 50.0,
                actualPnl = -80.0,
                result = TradeResult.LOSS.name,
                strategy = "Price Action",
                entryReason = "Rejection",
                exitReason = "SL hit",
                mistakes = "FOMO",
                lessonsLearned = "Wait for candle close",
                screenshotData = null,
                notes = ""
            ),
            TradeEntity(
                id = "t3",
                tradeCode = "TWBB-0003",
                userId = "uid_1",
                userName = "Trader",
                tradeDateMillis = 3000L,
                tradeDateIso = "2026-11-21",
                instrument = "XAUUSD",
                direction = TradeDirection.BUY.name,
                timeframe = Timeframe.H1.name,
                currency = TradingCurrency.USD.name,
                usdToInrRate = 85.00,
                entryPrice = 2655.0,
                exitPrice = 2685.0,
                stopLoss = 2645.0,
                takeProfit = 2685.0,
                lotSize = 0.5,
                riskAmount = 40.0,
                actualPnl = 120.0,
                result = TradeResult.WIN.name,
                strategy = "SMC",
                entryReason = "OB tap",
                exitReason = "TP",
                mistakes = "None",
                lessonsLearned = "Clean execution",
                screenshotData = null,
                notes = ""
            )
        )

        // Verify smart currency suggestions
        assertEquals(TradingCurrency.INR, TradingAnalytics.suggestCurrencyForInstrument("NIFTY 50"))
        assertEquals(TradingCurrency.INR, TradingAnalytics.suggestCurrencyForInstrument("RELIANCE.NS"))
        assertEquals(TradingCurrency.USD, TradingAnalytics.suggestCurrencyForInstrument("XAUUSD"))
        assertEquals(TradingCurrency.USD, TradingAnalytics.suggestCurrencyForInstrument("EURUSD"))

        // Verify USD display currency calculations
        val usdMetrics = TradingAnalytics.calculateDashboardMetrics(trades, TradingCurrency.USD)
        assertEquals(TradingCurrency.USD, usdMetrics.displayCurrency)
        assertEquals(3, usdMetrics.totalTrades)
        assertEquals(1, usdMetrics.inrTradeCount)
        assertEquals(2, usdMetrics.usdTradeCount)
        assertEquals(2, usdMetrics.winningTrades)
        assertEquals(1, usdMetrics.losingTrades)
        assertEquals(66.666, usdMetrics.winRatePercent, 0.1)
        assertEquals(220.0, usdMetrics.totalProfit, 0.01)
        assertEquals(80.0, usdMetrics.totalLoss, 0.01)
        assertEquals(140.0, usdMetrics.netPnl, 0.01)
        assertEquals(2.75, usdMetrics.profitFactor, 0.01)
        assertEquals("2026-11-20", usdMetrics.bestTradingDay?.dateIso)
        assertEquals("2026-11-21", usdMetrics.worstTradingDay?.dateIso)
        assertEquals("1W Streak", usdMetrics.currentStreak.label)

        // Verify INR display currency calculations (using each trade's saved historical rate!)
        // +8350 - 6720 + 10200 = +11830 INR net; Total profit = 18550 INR; Total loss = 6720 INR
        val inrMetrics = TradingAnalytics.calculateDashboardMetrics(trades, TradingCurrency.INR)
        assertEquals(TradingCurrency.INR, inrMetrics.displayCurrency)
        assertEquals(18550.0, inrMetrics.totalProfit, 0.01)
        assertEquals(6720.0, inrMetrics.totalLoss, 0.01)
        assertEquals(11830.0, inrMetrics.netPnl, 0.01)
        assertEquals(2.7604, inrMetrics.profitFactor, 0.01)

        // Verify Discipline & CSV Multi-Currency export
        val disciplineInr = TradingAnalytics.calculateDisciplineMetrics(
            trades = trades,
            user = user,
            displayCurrency = TradingCurrency.INR,
            currentUsdToInrRate = 84.50
        )
        assertEquals(100.0, disciplineInr.stopLossDisciplinePercent, 0.01)
        assertEquals(100.0, disciplineInr.riskCompliancePercent, 0.01)
        assertEquals(16900.0, disciplineInr.convertedDailyGoal, 0.01)
        assertTrue(disciplineInr.mistakeCounts.any { it.first == "FOMO" && it.second == 1 })

        val csvOut = ByteArrayOutputStream()
        ReportExporter.writeCsvReport(csvOut, trades, TradingCurrency.INR)
        val csvText = csvOut.toString(Charsets.UTF_8.name())
        assertTrue(csvText.contains("Converted P&L (INR)"))
        assertTrue(csvText.contains("TWBB-0001"))
        assertTrue(csvText.contains("8350.00"))
        assertTrue(csvText.contains("10200.00"))
    }

    @Test
    fun communicationModelsMapRolesSeveritiesAndTimestampsCorrectly() {
        val thread = ConversationThread(
            id = "student_1",
            studentId = "student_1",
            studentName = "Alice",
            studentEmail = "alice@test.com",
            lastMessageText = "Please check my XAUUSD entry.",
            lastMessageSenderRole = UserRole.STUDENT.name,
            lastMessageAtMillis = 1700000123000L,
            unreadByAdminCount = 2,
            unreadByStudentCount = 0
        )
        assertEquals(UserRole.STUDENT, thread.lastSenderRoleEnum)
        assertEquals(2, thread.unreadByAdminCount)

        val msg = DirectMessageEntity(
            id = "m1",
            studentId = "student_1",
            senderId = "admin_1",
            senderName = "Bangali Babu",
            senderRole = UserRole.ADMIN.name,
            body = "Always place SL below the order block.",
            sentAtMillis = 1700000456000L
        )
        assertEquals(UserRole.ADMIN, msg.senderRoleEnum)
        assertEquals(1700000456000L, msg.effectiveTimestampMillis)

        val warning = StudentWarningEntity(
            id = "w1",
            studentId = "student_1",
            studentName = "Alice",
            senderAdminId = "admin_1",
            senderAdminName = "Bangali Babu",
            severity = WarningSeverity.CRITICAL.name,
            title = "Daily Loss Limit Alert",
            message = "Stop trading for today.",
            readByStudent = false,
            sentAtMillis = 1700000789000L
        )
        assertEquals(WarningSeverity.CRITICAL, warning.severityEnum)
        assertEquals(1700000789000L, warning.effectiveTimestampMillis)

        val ann = AnnouncementEntity(
            id = "a1",
            title = "Weekly Q&A Session",
            message = "Live tonight at 8 PM IST.",
            priority = AnnouncementPriority.IMPORTANT.name,
            authorId = "admin_1",
            authorName = "Bangali Babu",
            publishedAtMillis = 1700000999000L
        )
        assertEquals(AnnouncementPriority.IMPORTANT, ann.priorityEnum)
        assertEquals(1700000999000L, ann.effectiveTimestampMillis)
    }

    @Test
    fun smartDisciplineDetectsStopLossStreakStrategyLossesAndDeduplicatesAlerts() {
        val trader = UserEntity(
            id = "u_sl",
            userId = "u_sl",
            name = "Admin Trader",
            email = "admin@test.com",
            role = UserRole.ADMIN.name,
            preferredCurrency = TradingCurrency.USD.name,
            maxTradesPerDay = 3,
            maxRiskPerTrade = 50.0,
            dailyMaxLossLimit = 150.0,
            slAlertLookbackCount = 10,
            slAlertThreshold = 10,
            strategySlThreshold = 5
        )

        val baseMillis = 1763337600000L // 2025-11-17
        val slTrades = (1..10).map { idx ->
            TradeEntity(
                id = "sl_t_$idx",
                tradeCode = "TWBB-$idx",
                userId = "u_sl",
                userName = "Admin Trader",
                tradeDateMillis = baseMillis + idx * 3600_000L,
                tradeDateIso = "2026-11-18",
                instrument = "XAUUSD",
                direction = TradeDirection.BUY.name,
                timeframe = Timeframe.M15.name,
                currency = TradingCurrency.USD.name,
                usdToInrRate = 84.0,
                entryPrice = 2650.0,
                exitPrice = 2640.0,
                stopLoss = 2640.0,
                takeProfit = 2670.0,
                lotSize = 0.5,
                riskAmount = 50.0,
                actualPnl = -50.0,
                result = TradeResult.LOSS.name,
                strategy = if (idx <= 6) "Breakout" else "Scalping",
                entryReason = "Setup",
                exitReason = "SL Hit",
                mistakes = if (idx % 2 == 0) "FOMO" else "None",
                lessonsLearned = "",
                notes = ""
            )
        }

        // First evaluation triggers Weekly Loss Limit (>$200), 10 Consecutive SL Streak,
        // and Same-Strategy 3+ Consecutive SL Alerts for both Breakout (6 SLs) and Scalping (4 SLs).
        val firstEval = SmartDisciplineEngine.evaluateSmartAlerts(trader, slTrades, emptyList(), nowMillis = baseMillis + 99_000_000L)
        assertEquals(4, firstEval.newAlertsToCreate.size)
        val weeklyAlert = firstEval.newAlertsToCreate.first { it.alertTypeEnum == SmartAlertType.WEEKLY_LOSS_LIMIT }
        val streakAlert = firstEval.newAlertsToCreate.first { it.alertTypeEnum == SmartAlertType.STOP_LOSS_STREAK }
        val breakoutAlert = firstEval.newAlertsToCreate.first { it.alertTypeEnum == SmartAlertType.STRATEGY_LOSS_LIMIT && it.strategyName == "Breakout" }
        val scalpingAlert = firstEval.newAlertsToCreate.first { it.alertTypeEnum == SmartAlertType.STRATEGY_LOSS_LIMIT && it.strategyName == "Scalping" }
        assertTrue(weeklyAlert.lossAmountUsd > 200.0)
        assertEquals(10, streakAlert.stopLossCount)
        assertTrue(streakAlert.message.contains("10 consecutive Stop Loss trades"))
        assertEquals(6, breakoutAlert.stopLossCount)
        assertEquals(4, scalpingAlert.stopLossCount)

        // Re-evaluating with those existing unacknowledged alerts must NOT duplicate any alert
        val secondEval = SmartDisciplineEngine.evaluateSmartAlerts(trader, slTrades, firstEval.newAlertsToCreate, nowMillis = baseMillis + 99_000_000L)
        assertTrue(secondEval.newAlertsToCreate.isEmpty())
        assertTrue(secondEval.existingAlertsToUpdate.isEmpty())

        // Adding a 7th Breakout SL updates the existing unacknowledged alerts rather than creating duplicates
        val seventhBreakout = slTrades.last().copy(
            id = "sl_t_11",
            tradeDateMillis = baseMillis + 11 * 3600_000L,
            strategy = "Breakout"
        )
        val thirdEval = SmartDisciplineEngine.evaluateSmartAlerts(trader, slTrades + seventhBreakout, firstEval.newAlertsToCreate, nowMillis = baseMillis + 99_000_000L)
        assertEquals(0, thirdEval.newAlertsToCreate.size)
        assertTrue(thirdEval.existingAlertsToUpdate.any { it.newStopLossCount == 11 })
    }

    @Test
    fun weeklyPerformanceReportDetectsMistakesOvertradingRevengeAndRuleViolations() {
        val student = UserEntity(
            id = "st_1",
            userId = "st_1",
            name = "Alice",
            email = "alice@test.com",
            role = UserRole.STUDENT.name,
            preferredCurrency = TradingCurrency.USD.name,
            maxTradesPerDay = 2,
            maxRiskPerTrade = 50.0,
            dailyMaxLossLimit = 100.0
        )

        val mondayMillis = 1763337600000L // Monday in UTC/US calendar
        val week = SmartDisciplineEngine.weekWindowForMillis(mondayMillis)
        val day1 = week.startIso
        val trades = listOf(
            // Trade 1: SL hit with FOMO
            TradeEntity(
                id = "w1", tradeCode = "TWBB-101", userId = "st_1", userName = "Alice",
                tradeDateMillis = week.startMillis + 1000L, tradeDateIso = day1,
                instrument = "XAUUSD", direction = TradeDirection.BUY.name, timeframe = Timeframe.M15.name,
                currency = TradingCurrency.USD.name, usdToInrRate = 84.0,
                entryPrice = 2650.0, exitPrice = 2640.0, stopLoss = 2640.0, takeProfit = 2670.0,
                lotSize = 0.5, riskAmount = 50.0, actualPnl = -50.0, result = TradeResult.LOSS.name,
                strategy = "Breakout", entryReason = "Breakout", exitReason = "SL Hit", mistakes = "FOMO"
            ),
            // Trade 2: Rapid oversized risk after loss (revenge pattern) + No Stop Loss + Oversized risk
            TradeEntity(
                id = "w2", tradeCode = "TWBB-102", userId = "st_1", userName = "Alice",
                tradeDateMillis = week.startMillis + 2000L, tradeDateIso = day1,
                instrument = "XAUUSD", direction = TradeDirection.BUY.name, timeframe = Timeframe.M15.name,
                currency = TradingCurrency.USD.name, usdToInrRate = 84.0,
                entryPrice = 2642.0, exitPrice = 2620.0, stopLoss = null, takeProfit = null,
                lotSize = 1.0, riskAmount = 90.0, actualPnl = -90.0, result = TradeResult.LOSS.name,
                strategy = "", entryReason = "", exitReason = "Manual exit", mistakes = "FOMO, Revenge Trade, No Stop Loss"
            ),
            // Trade 3 on same day => overtrading (>2 maxTradesPerDay)
            TradeEntity(
                id = "w3", tradeCode = "TWBB-103", userId = "st_1", userName = "Alice",
                tradeDateMillis = week.startMillis + 3000L, tradeDateIso = day1,
                instrument = "XAUUSD", direction = TradeDirection.SELL.name, timeframe = Timeframe.M15.name,
                currency = TradingCurrency.USD.name, usdToInrRate = 84.0,
                entryPrice = 2625.0, exitPrice = 2610.0, stopLoss = 2635.0, takeProfit = 2610.0,
                lotSize = 0.5, riskAmount = 40.0, actualPnl = 60.0, result = TradeResult.WIN.name,
                strategy = "Breakout", entryReason = "Retest", exitReason = "TP Hit", mistakes = "None"
            )
        )

        val report = SmartDisciplineEngine.generateWeeklyReport(
            user = student,
            week = week,
            weekTrades = trades,
            allUserTrades = trades,
            displayCurrency = TradingCurrency.USD,
            generatedAtMillis = week.endMillis + 1000L
        )

        assertEquals(3, report.totalTrades)
        assertEquals(1, report.overtradingDays)
        assertTrue(report.revengeTradingCount >= 1)
        assertEquals(1, report.noStopLossCount)
        assertTrue(report.ruleViolationsCount >= 1)
        assertTrue(report.repeatedMistakes.any { it.contains("FOMO") && it.contains("Repeated") })
        assertTrue(report.poorStrategies.any { it.contains("General") || it.contains("Breakout") })
        assertTrue(report.improvementAreas.isNotEmpty())
        assertTrue(report.personalizedSuggestions.isNotEmpty())
        assertTrue(report.disciplineScore < 80)
    }

    @Test
    fun studentProfileCompletionAndAvatarStateBehavesCorrectly() {
        val newStudent = UserEntity(
            id = "uid_st_1",
            userId = "uid_st_1",
            name = "Aarav Roy",
            email = "aarav@example.com",
            role = UserRole.STUDENT.name
        )

        // On first login, profile is incomplete and no photo URL is set (never falls back to app logo)
        assertTrue(newStudent.hasIncompleteProfile)
        assertEquals("", newStudent.profilePhotoUrl)
        assertEquals("AR", newStudent.initials)
        assertEquals(25, newStudent.profileCompletionPercent)

        val completedStudent = newStudent.copy(
            mobileNumber = "+91 9876543210",
            whatsappNumber = "+91 9876543210",
            dateOfBirth = "2001-04-12",
            gender = "Male",
            fullAddress = "12 MG Road, Kolkata, West Bengal 700001",
            profilePhotoUrl = "https://firebasestorage.googleapis.com/v0/b/test/o/avatar.jpg",
            profilePhotoPath = "student_profiles/uid_st_1/avatar.jpg",
            profileCompleted = true
        )

        assertEquals(false, completedStudent.hasIncompleteProfile)
        assertEquals(100, completedStudent.profileCompletionPercent)

        // Deleting photo leaves personal details intact and clears photo URL/path
        val photoRemoved = completedStudent.copy(profilePhotoUrl = "", profilePhotoPath = "")
        assertEquals(false, photoRemoved.hasIncompleteProfile)
        assertEquals(87, photoRemoved.profileCompletionPercent)
    }

    @Test
    fun advancedTradeAnalyticsComputesTimeWeekdaySessionDurationFrequencyCalendarAndReports() {
        val ist = "Asia/Kolkata"
        val sessions = CustomTradingSession.DEFAULT_SESSIONS

        fun istTime(dateIso: String, hour: Int, min: Int): Long {
            val start = TradeDateTimeHelper.parseDateIsoStartMillis(dateIso, ist)!!
            return TradeDateTimeHelper.combineDateAndTimeMillis(start, hour, min, ist)
        }

        val trades = listOf(
            TradeEntity(
                id = "a1", tradeCode = "TWBB-A1", userId = "u1", userName = "Trader",
                tradeDateMillis = istTime("2026-11-16", 9, 30), tradeDateIso = "2026-11-16",
                entryTimeMillis = istTime("2026-11-16", 9, 30), exitDateIso = "2026-11-16",
                exitTimeMillis = istTime("2026-11-16", 9, 40),
                tradingSession = "India Morning", exitClosureType = ExitClosureType.AUTO_TP.name,
                timezoneId = ist, instrument = "NIFTY 50", direction = TradeDirection.BUY.name,
                timeframe = Timeframe.M5.name, currency = TradingCurrency.USD.name, usdToInrRate = 85.0,
                entryPrice = 24000.0, exitPrice = 24050.0, lotSize = 1.0, riskAmount = 50.0,
                actualPnl = 200.0, result = TradeResult.WIN.name, strategy = "SMC",
                exitReason = "TP Hit", mistakes = "None"
            ),
            TradeEntity(
                id = "a2", tradeCode = "TWBB-A2", userId = "u1", userName = "Trader",
                tradeDateMillis = istTime("2026-11-16", 10, 15), tradeDateIso = "2026-11-16",
                entryTimeMillis = istTime("2026-11-16", 10, 15), exitDateIso = "2026-11-16",
                exitTimeMillis = istTime("2026-11-16", 10, 25),
                tradingSession = "India Morning", exitClosureType = ExitClosureType.MANUAL.name,
                timezoneId = ist, instrument = "NIFTY 50", direction = TradeDirection.BUY.name,
                timeframe = Timeframe.M5.name, currency = TradingCurrency.USD.name, usdToInrRate = 85.0,
                entryPrice = 24060.0, exitPrice = 24100.0, lotSize = 1.0, riskAmount = 50.0,
                actualPnl = 150.0, result = TradeResult.WIN.name, strategy = "SMC",
                exitReason = "Manual Exit", mistakes = "None"
            ),
            TradeEntity(
                id = "a3", tradeCode = "TWBB-A3", userId = "u1", userName = "Trader",
                tradeDateMillis = istTime("2026-11-17", 19, 0), tradeDateIso = "2026-11-17",
                entryTimeMillis = istTime("2026-11-17", 19, 0), exitDateIso = "2026-11-17",
                exitTimeMillis = istTime("2026-11-17", 21, 30),
                tradingSession = "New York Session", exitClosureType = ExitClosureType.AUTO_SL.name,
                timezoneId = ist, instrument = "XAUUSD", direction = TradeDirection.SELL.name,
                timeframe = Timeframe.M1.name, currency = TradingCurrency.USD.name, usdToInrRate = 85.0,
                entryPrice = 2660.0, exitPrice = 2675.0, lotSize = 1.0, riskAmount = 90.0,
                actualPnl = -180.0, result = TradeResult.LOSS.name, strategy = "Breakout",
                exitReason = "SL Hit", mistakes = "FOMO"
            ),
            TradeEntity(
                id = "a4", tradeCode = "TWBB-A4", userId = "u1", userName = "Trader",
                tradeDateMillis = istTime("2026-11-17", 21, 40), tradeDateIso = "2026-11-17",
                entryTimeMillis = istTime("2026-11-17", 21, 40), exitDateIso = "2026-11-17",
                exitTimeMillis = istTime("2026-11-17", 22, 50),
                tradingSession = "New York Session", exitClosureType = ExitClosureType.AUTO_SL.name,
                timezoneId = ist, instrument = "XAUUSD", direction = TradeDirection.BUY.name,
                timeframe = Timeframe.M1.name, currency = TradingCurrency.USD.name, usdToInrRate = 85.0,
                entryPrice = 2670.0, exitPrice = 2658.0, lotSize = 1.0, riskAmount = 80.0,
                actualPnl = -120.0, result = TradeResult.LOSS.name, strategy = "Breakout",
                exitReason = "SL Hit", mistakes = "Revenge Trade"
            ),
            TradeEntity(
                id = "a5", tradeCode = "TWBB-A5", userId = "u1", userName = "Trader",
                tradeDateMillis = istTime("2026-11-17", 12, 0), tradeDateIso = "2026-11-17",
                entryTimeMillis = null, exitTimeMillis = null,
                instrument = "BANKNIFTY", direction = TradeDirection.BUY.name,
                timeframe = Timeframe.M15.name, currency = TradingCurrency.INR.name, usdToInrRate = 85.0,
                entryPrice = 51000.0, exitPrice = 51100.0, lotSize = 1.0, riskAmount = 1000.0,
                actualPnl = 4250.0, result = TradeResult.WIN.name, strategy = "SMC",
                exitReason = "Early Exit", mistakes = "Early Exit"
            )
        )

        val analysis = AdvancedTradeAnalytics.analyze(
            trades = trades,
            displayCurrency = TradingCurrency.USD,
            timezoneId = ist,
            customSessions = sessions,
            filter = AdvancedTradeFilter(),
            maxTradesPerDayThreshold = 2
        )

        assertEquals(4, analysis.exactTimestampCount)
        assertEquals(1, analysis.legacyTimestampCount)
        assertEquals(1, analysis.durationAnalysis.tradesMissingDurationCount)
        assertEquals(4, analysis.durationAnalysis.tradesWithDurationCount)

        assertEquals(TimeOfDayInterval.MORNING, analysis.mostProfitableTimeOfDay?.interval)
        assertEquals(350.0, analysis.mostProfitableTimeOfDay?.netPnl ?: 0.0, 0.01)

        assertEquals(WeekdayFilter.MONDAY, analysis.bestWeekday?.weekday)
        assertEquals(WeekdayFilter.TUESDAY, analysis.worstWeekday?.weekday)
        assertEquals(-250.0, analysis.worstWeekday?.netPnl ?: 0.0, 0.01)

        assertEquals("India Morning", analysis.bestSession?.sessionName)
        assertEquals("New York Session", analysis.worstSession?.sessionName)

        assertEquals(600_000L, analysis.durationAnalysis.avgWinningHoldMillis)
        assertEquals(6_600_000L, analysis.durationAnalysis.avgLosingHoldMillis)
        assertEquals(false, analysis.durationAnalysis.longerHoldsImproveProfitability)

        assertEquals(3, analysis.frequencyStats.maxTradesInSingleDay)
        assertEquals("2026-11-17", analysis.frequencyStats.maxTradesDateIso)
        assertEquals(1, analysis.frequencyStats.rapidReEntryAfterLossCount)
        assertTrue(analysis.frequencyStats.overtradingDetected)

        assertEquals(2, analysis.entryExitAnalysis.slHitCount)
        assertEquals(1, analysis.entryExitAnalysis.tpHitCount)
        assertEquals(2, analysis.entryExitAnalysis.manualCloseCount)
        assertEquals(3, analysis.entryExitAnalysis.autoCloseCount)
        assertEquals(1, analysis.entryExitAnalysis.earlyExitCount)

        assertTrue(analysis.smartInsights.any { it.id == "tod_best_MORNING" })
        assertTrue(analysis.smartInsights.any { it.id == "tf_cmp_M5_M1" })
        assertTrue(analysis.smartInsights.any { it.id == "strat_contrast" })
        assertTrue(analysis.smartInsights.any { it.id == "weekday_weak_TUESDAY" })
        assertTrue(analysis.smartInsights.any { it.id == "duration_loss_longer" })

        val sparse = AdvancedTradeAnalytics.analyze(listOf(trades.first()), TradingCurrency.USD, ist, sessions)
        assertTrue(sparse.smartInsights.isEmpty())

        val filteredOnlyTueLoss = AdvancedTradeAnalytics.analyze(
            trades = trades,
            displayCurrency = TradingCurrency.USD,
            timezoneId = ist,
            customSessions = sessions,
            filter = AdvancedTradeFilter(
                weekdays = setOf(WeekdayFilter.TUESDAY),
                results = setOf(TradeResult.LOSS),
                durationBuckets = setOf(TradeDurationBucket.LONG)
            )
        )
        assertEquals(1, filteredOnlyTueLoss.enrichedTrades.size)
        assertEquals("TWBB-A3", filteredOnlyTueLoss.enrichedTrades.first().trade.tradeCode)

        val calNov = AdvancedTradeAnalytics.buildCalendarMonth(analysis.enrichedTrades, 2026, 10, ist)
        val nov16 = calNov.days.first { it.dateIso == "2026-11-16" }
        val nov17 = calNov.days.first { it.dateIso == "2026-11-17" }
        assertEquals(CalendarDayStatus.PROFIT, nov16.status)
        assertEquals(2, nov16.tradeCount)
        assertEquals(CalendarDayStatus.LOSS, nov17.status)
        assertEquals(3, nov17.tradeCount)
        assertEquals(1, calNov.profitableDays)
        assertEquals(1, calNov.losingDays)

        val monthlyRep = AdvancedTradeAnalytics.buildPeriodReport(
            trades = trades,
            periodType = AnalysisPeriodType.MONTHLY,
            anchorMillis = istTime("2026-11-17", 12, 0),
            displayCurrency = TradingCurrency.USD,
            timezoneId = ist,
            customSessions = sessions
        )
        assertEquals(5, monthlyRep.totalTrades)
        assertEquals(400.0, monthlyRep.grossProfit, 0.01)
        assertEquals(300.0, monthlyRep.grossLoss, 0.01)
        assertEquals(100.0, monthlyRep.netPnl, 0.01)
        assertTrue(monthlyRep.bestInstrument.contains("NIFTY 50"))
        assertTrue(monthlyRep.bestStrategy.contains("SMC"))
        assertTrue(monthlyRep.bestTimeframe.contains("M5"))

        val periodCsv = ByteArrayOutputStream()
        ReportExporter.writePeriodAnalyticsCsvReport(periodCsv, monthlyRep, "Trader", ist)
        val periodCsvText = periodCsv.toString(Charsets.UTF_8.name())
        assertTrue(periodCsvText.contains("Best Trading Time"))
        assertTrue(periodCsvText.contains("Holding Duration"))
        assertTrue(periodCsvText.contains("Asia/Kolkata"))
        assertNotNull(TradeDateTimeHelper.shortTimezoneTag("Asia/Kolkata"))
        assertFalse(monthlyRep.trades.isEmpty())
    }
}
