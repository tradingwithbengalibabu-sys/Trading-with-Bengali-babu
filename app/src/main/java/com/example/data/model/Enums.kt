package com.example.data.model

enum class UserRole(val label: String) {
    ADMIN("Admin"),
    STUDENT("Student")
}

enum class AccountStatus(val label: String) {
    ACTIVE("Active"),
    SUSPENDED("Suspended")
}

enum class TradingCurrency(
    val code: String,
    val symbol: String,
    val label: String
) {
    INR("INR", "₹", "INR (₹)"),
    USD("USD", "$", "USD ($)")
}

enum class TradeDirection(val label: String) {
    BUY("Buy"),
    SELL("Sell")
}

enum class Timeframe(val label: String) {
    M1("M1"),
    M5("M5"),
    M15("M15"),
    M30("M30"),
    H1("H1"),
    H4("H4"),
    D1("D1")
}

enum class TradeStatus(val label: String) {
    CLOSED("Closed"),
    OPEN("Open")
}

enum class TradeResult(val label: String) {
    WIN("Win"),
    LOSS("Loss"),
    BREAK_EVEN("Break Even")
}

enum class ReviewPeriod(val label: String) {
    DAILY("Daily"),
    WEEKLY("Weekly"),
    MONTHLY("Monthly")
}

enum class EmotionState(val label: String) {
    CALM("Calm & Focused"),
    CONFIDENT("Confident"),
    ANXIOUS("Anxious"),
    FOMO("FOMO"),
    REVENGE("Revenge Trading")
}

enum class ReportRangeType(val label: String) {
    DAILY("Daily"),
    WEEKLY("Weekly"),
    MONTHLY("Monthly"),
    CUSTOM("Custom Range")
}

enum class WarningSeverity(val label: String) {
    ADVISORY("Advisory"),
    IMPORTANT("Important"),
    CRITICAL("Critical Warning")
}

enum class AnnouncementPriority(val label: String) {
    NORMAL("General Update"),
    IMPORTANT("Important Notice")
}

enum class SmartAlertType(val label: String) {
    WEEKLY_LOSS_LIMIT("Weekly Loss Limit ($200 Eq.)"),
    STOP_LOSS_STREAK("10 Consecutive Stop Losses"),
    STRATEGY_LOSS_LIMIT("3 Consecutive Strategy SLs")
}

enum class TimeOfDayInterval(val label: String, val rangeLabel: String) {
    EARLY_MORNING("Early Morning", "05:00–08:59"),
    MORNING("Morning", "09:00–11:59"),
    AFTERNOON("Afternoon", "12:00–16:59"),
    EVENING("Evening", "17:00–20:59"),
    NIGHT("Night", "21:00–04:59")
}

enum class WeekdayFilter(
    val calendarDayOfWeek: Int,
    val label: String,
    val shortLabel: String,
    val mondayIndex: Int
) {
    MONDAY(java.util.Calendar.MONDAY, "Monday", "Mon", 0),
    TUESDAY(java.util.Calendar.TUESDAY, "Tuesday", "Tue", 1),
    WEDNESDAY(java.util.Calendar.WEDNESDAY, "Wednesday", "Wed", 2),
    THURSDAY(java.util.Calendar.THURSDAY, "Thursday", "Thu", 3),
    FRIDAY(java.util.Calendar.FRIDAY, "Friday", "Fri", 4),
    SATURDAY(java.util.Calendar.SATURDAY, "Saturday", "Sat", 5),
    SUNDAY(java.util.Calendar.SUNDAY, "Sunday", "Sun", 6);

    companion object {
        fun fromCalendarDayOfWeek(dayOfWeek: Int): WeekdayFilter? {
            return entries.firstOrNull { it.calendarDayOfWeek == dayOfWeek }
        }
    }
}

enum class ExitClosureType(val label: String, val isAutomatic: Boolean) {
    AUTO_SL("Auto Stop Loss", true),
    AUTO_TP("Auto Take Profit", true),
    TRAILING_SL("Trailing SL", true),
    MANUAL("Manual Close", false),
    BREAKEVEN("Break-Even Exit", false),
    UNKNOWN("Not Specified", false)
}

enum class TradeDurationBucket(val label: String, val rangeLabel: String) {
    SHORT("Short (< 15m)", "< 15 min"),
    MEDIUM("Medium (15m–2h)", "15m – 2h"),
    LONG("Long (> 2h)", "> 2 hrs")
}

enum class AnalysisPeriodType(val label: String) {
    DAILY("Daily"),
    WEEKLY("Weekly"),
    MONTHLY("Monthly")
}

enum class SignalAccess(val label: String) {
    ALLOWED("Allowed"),
    BLOCKED("Blocked")
}

enum class TradeSignalStatus(val label: String) {
    ACTIVE("Active"),
    TARGET_HIT("Target Hit"),
    SL_HIT("Stop Loss Hit"),
    CANCELLED("Cancelled")
}

enum class SignalAudienceType(val label: String) {
    ALL_STUDENTS("All Students"),
    SELECTED_STUDENTS("Selected Students")
}

