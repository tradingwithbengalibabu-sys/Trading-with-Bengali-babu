package com.example

import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.model.AccountStatus
import com.example.data.model.Timeframe
import com.example.data.model.TradeDirection
import com.example.data.model.TradeEntity
import com.example.data.model.TradeResult
import com.example.data.model.TradingCurrency
import com.example.data.model.UserEntity
import com.example.data.model.UserRole
import com.example.export.ReportExporter
import com.example.notifications.TradingNotificationHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    private lateinit var context: Context

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
    }

    @Test
    fun appNameMatchesBranding() {
        assertEquals("Trading with Bangali Babu", context.getString(R.string.app_name))
    }

    @Test
    fun userRoleAndCsvReportExportWorkCleanly() {
        val admin = UserEntity(
            id = "admin_uid",
            userId = "admin_uid",
            name = "Bangali Babu",
            email = "tradingwithbengalibabu@gmail.com",
            role = UserRole.ADMIN.name,
            status = AccountStatus.ACTIVE.name,
            preferredCurrency = TradingCurrency.INR.name
        )
        val student = UserEntity(
            id = "student_uid",
            userId = "student_uid",
            name = "Alice",
            email = "alice@twbb.com",
            role = UserRole.STUDENT.name,
            status = AccountStatus.SUSPENDED.name,
            preferredCurrency = TradingCurrency.USD.name
        )

        assertTrue(admin.roleEnum == UserRole.ADMIN)
        assertFalse(admin.statusEnum == AccountStatus.SUSPENDED)
        assertEquals(TradingCurrency.INR, admin.preferredCurrencyEnum)
        assertFalse(student.roleEnum == UserRole.ADMIN)
        assertTrue(student.statusEnum == AccountStatus.SUSPENDED)
        assertEquals(TradingCurrency.USD, student.preferredCurrencyEnum)

        val trade = TradeEntity(
            id = "trade_1",
            tradeCode = "TWBB-0001",
            userId = "student_uid",
            userName = "Alice",
            tradeDateMillis = 1700000000000L,
            tradeDateIso = "2026-11-23",
            instrument = "NIFTY 50",
            direction = TradeDirection.BUY.name,
            timeframe = Timeframe.M15.name,
            currency = TradingCurrency.INR.name,
            usdToInrRate = 84.50,
            entryPrice = 24000.0,
            exitPrice = 24080.0,
            stopLoss = 23960.0,
            takeProfit = 24120.0,
            lotSize = 1.0,
            riskAmount = 4225.0,
            actualPnl = 8450.0,
            result = TradeResult.WIN.name,
            strategy = "Breakout",
            entryReason = "VWAP reclaim",
            exitReason = "TP hit",
            mistakes = "None",
            lessonsLearned = "Hold winners",
            screenshotData = null,
            notes = "Clean setup"
        )

        val csvOut = ByteArrayOutputStream()
        ReportExporter.writeCsvReport(csvOut, listOf(trade), TradingCurrency.USD)
        val csvText = csvOut.toString(Charsets.UTF_8.name())
        assertTrue(csvText.contains("TWBB-0001"))
        assertTrue(csvText.contains("NIFTY 50"))
        assertTrue(csvText.contains("INR"))
        assertTrue(csvText.contains("100.00"))
    }

    @Test
    fun notificationHelperRegistersCommunicationChannels() {
        TradingNotificationHelper.ensureChannels(context)
        val mgr = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelIds = mgr.notificationChannels.map { it.id }.toSet()
        assertTrue(channelIds.contains("twbb_messages"))
        assertTrue(channelIds.contains("twbb_warnings"))
        assertTrue(channelIds.contains("twbb_announcements"))
        assertTrue(channelIds.contains("twbb_weekly_reports"))
    }
}
