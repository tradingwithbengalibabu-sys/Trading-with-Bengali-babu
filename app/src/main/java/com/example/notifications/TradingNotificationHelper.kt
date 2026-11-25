package com.example.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R

object TradingNotificationHelper {
    private const val CHANNEL_MESSAGES = "twbb_messages"
    private const val CHANNEL_WARNINGS = "twbb_warnings"
    private const val CHANNEL_ANNOUNCEMENTS = "twbb_announcements"
    private const val CHANNEL_WEEKLY_REPORTS = "twbb_weekly_reports"
    private const val CHANNEL_TRADE_SIGNALS = "twbb_trade_signals"
    private const val CHANNEL_LIVE_CLASS = "twbb_live_class"

    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val mgr = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return

        val msgChannel = NotificationChannel(
            CHANNEL_MESSAGES,
            "Direct Messages & Replies",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Direct conversations between students and Admin"
        }

        val warnChannel = NotificationChannel(
            CHANNEL_WARNINGS,
            "Admin Warnings & Alerts",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Important risk, Stop Loss streak, and discipline alerts"
        }

        val annChannel = NotificationChannel(
            CHANNEL_ANNOUNCEMENTS,
            "Trading Announcements",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Broadcast announcements from Trading with Bangali Babu"
        }

        val weeklyChannel = NotificationChannel(
            CHANNEL_WEEKLY_REPORTS,
            "Weekly Performance Reports",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Automated weekly trading mistake analysis and discipline summaries"
        }

        val signalsChannel = NotificationChannel(
            CHANNEL_TRADE_SIGNALS,
            "Admin Trade Signals",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Real-time BUY/SELL trade signals and status updates from Admin"
        }

        val liveClassChannel = NotificationChannel(
            CHANNEL_LIVE_CLASS,
            "Live Online Classroom",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Alerts when Live Trading Classroom starts"
        }

        mgr.createNotificationChannels(
            listOf(msgChannel, warnChannel, annChannel, weeklyChannel, signalsChannel, liveClassChannel)
        )
    }

    fun hasNotificationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    fun showMessageNotification(context: Context, title: String, body: String, notificationId: Int) {
        postNotification(context, CHANNEL_MESSAGES, title, body, notificationId, isUrgent = false)
    }

    fun showWarningNotification(context: Context, title: String, body: String, notificationId: Int) {
        postNotification(context, CHANNEL_WARNINGS, title, body, notificationId, isUrgent = true)
    }

    fun showAnnouncementNotification(context: Context, title: String, body: String, notificationId: Int) {
        postNotification(context, CHANNEL_ANNOUNCEMENTS, title, body, notificationId, isUrgent = false)
    }

    fun showSmartDisciplineAlertNotification(context: Context, title: String, body: String, notificationId: Int) {
        postNotification(context, CHANNEL_WARNINGS, title, body, notificationId, isUrgent = true)
    }

    fun showWeeklyReportNotification(context: Context, title: String, body: String, notificationId: Int) {
        postNotification(context, CHANNEL_WEEKLY_REPORTS, title, body, notificationId, isUrgent = false)
    }

    fun showTradeSignalNotification(context: Context, title: String, body: String, notificationId: Int) {
        postNotification(context, CHANNEL_TRADE_SIGNALS, title, body, notificationId, isUrgent = true)
    }

    fun showLiveClassNotification(context: Context, title: String, body: String, notificationId: Int) {
        postNotification(context, CHANNEL_LIVE_CLASS, title, body, notificationId, isUrgent = true)
    }

    private fun postNotification(
        context: Context,
        channelId: String,
        title: String,
        body: String,
        notificationId: Int,
        isUrgent: Boolean
    ) {
        if (!hasNotificationPermission(context)) return
        ensureChannels(context)

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(if (isUrgent) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        runCatching {
            NotificationManagerCompat.from(context).notify(notificationId, notification)
        }
    }
}
