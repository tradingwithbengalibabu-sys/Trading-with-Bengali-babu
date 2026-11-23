package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AppDatabase
import com.example.data.local.SessionPreferences
import com.example.data.model.Timeframe
import com.example.data.model.TradeDirection
import com.example.data.model.TradeResult
import com.example.data.repository.TradingRepository
import com.example.export.ReportExporter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
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
    private lateinit var db: AppDatabase
    private lateinit var repo: TradingRepository

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repo = TradingRepository(context, db, SessionPreferences(context))
    }

    @After
    fun teardown() {
        db.close()
    }

    @Test
    fun appNameMatchesBranding() {
        assertEquals("Trading with Bangali Babu", context.getString(R.string.app_name))
    }

    @Test
    fun rbacPreventsStudentCrossUserAccessAndAllowsAdminMonitoring() = runTest {
        val admin = repo.registerInitialAdmin("Bangali Babu", "admin@twbb.com", "AdminPass123").getOrThrow()
        val alice = repo.registerStudent("Alice", "alice@twbb.com", "AlicePass123").getOrThrow()
        val bob = repo.registerStudent("Bob", "bob@twbb.com", "BobPass123").getOrThrow()

        // Alice logs a trade
        val savedTrade = repo.saveTrade(
            actor = alice,
            existingTradeId = null,
            targetUserId = alice.id,
            targetUserName = alice.name,
            tradeDateMillis = System.currentTimeMillis(),
            tradeDateIso = "2026-11-23",
            instrument = "NIFTY 50",
            direction = TradeDirection.BUY,
            timeframe = Timeframe.M15,
            entryPrice = 24000.0,
            exitPrice = 24080.0,
            stopLoss = 23960.0,
            takeProfit = 24120.0,
            lotSize = 1.0,
            riskAmount = 40.0,
            actualPnl = 160.0,
            result = TradeResult.WIN,
            strategy = "Breakout",
            entryReason = "VWAP reclaim",
            exitReason = "TP hit",
            mistakes = "None",
            lessonsLearned = "Hold winners",
            screenshotPath = null,
            notes = "Clean setup"
        ).getOrThrow()

        // Bob MUST NOT be able to view, edit, or delete Alice's trade
        assertTrue(repo.observeTradesForUser(bob, alice.id).first().isEmpty())
        assertTrue(repo.deleteTrade(bob, savedTrade.id).isFailure)
        assertTrue(repo.createStudentByAdmin(bob, "Hacker", "h@x.com", "123456", 100.0, 50.0, 3, 20.0).isFailure)

        // Admin CAN monitor Alice's trades and suspend Bob
        val adminObservedAlice = repo.observeTradesForUser(admin, alice.id).first()
        assertEquals(1, adminObservedAlice.size)
        assertTrue(repo.setStudentSuspended(admin, bob, true).isSuccess)
        assertTrue(repo.login("bob@twbb.com", "BobPass123").isFailure)

        // CSV Export produces valid output with trade code
        val csvOut = ByteArrayOutputStream()
        ReportExporter.writeCsvReport(csvOut, adminObservedAlice)
        val csvText = csvOut.toString(Charsets.UTF_8.name())
        assertTrue(csvText.contains(savedTrade.tradeCode))
        assertTrue(csvText.contains("NIFTY 50"))
    }
}
