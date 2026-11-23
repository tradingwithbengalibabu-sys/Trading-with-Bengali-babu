package com.example.data.model

enum class UserRole(val label: String) {
    ADMIN("Admin"),
    STUDENT("Student")
}

enum class AccountStatus(val label: String) {
    ACTIVE("Active"),
    SUSPENDED("Suspended")
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
