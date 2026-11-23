package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Immutable
data class TradingExtendedColors(
    val profit: Color,
    val profitContainer: Color,
    val loss: Color,
    val lossContainer: Color,
    val gold: Color,
    val goldContainer: Color,
    val electricBlue: Color,
    val purple: Color,
    val teal: Color,
    val cardSurface: Color,
    val cardElevated: Color,
    val cardBorder: Color,
    val heroStart: Color,
    val heroMid: Color,
    val heroEnd: Color,
    val isDark: Boolean
)

val LocalTradingColors = staticCompositionLocalOf {
    TradingExtendedColors(
        profit = EmeraldGreen,
        profitContainer = EmeraldSoftBg,
        loss = CoralRed,
        lossContainer = CoralSoftBg,
        gold = BrightGold,
        goldContainer = Color(0xFF452A04),
        electricBlue = ElectricBlue,
        purple = VibrantPurple,
        teal = TealCyan,
        cardSurface = DeepNavy800,
        cardElevated = DeepNavy700,
        cardBorder = DeepNavy600,
        heroStart = DeepNavy900,
        heroMid = Color(0xFF132C57),
        heroEnd = Color(0xFF281E5D),
        isDark = true
    )
}

private val DarkExtendedColors = TradingExtendedColors(
    profit = EmeraldGreen,
    profitContainer = Color(0xFF0A362B),
    loss = CoralRed,
    lossContainer = Color(0xFF4B1422),
    gold = BrightGold,
    goldContainer = Color(0xFF3E2705),
    electricBlue = ElectricBlue,
    purple = VibrantPurple,
    teal = TealCyan,
    cardSurface = DeepNavy800,
    cardElevated = DeepNavy700,
    cardBorder = Color(0xFF233E6B),
    heroStart = DeepNavy900,
    heroMid = Color(0xFF142E5C),
    heroEnd = Color(0xFF2B1F63),
    isDark = true
)

private val LightExtendedColors = TradingExtendedColors(
    profit = EmeraldDark,
    profitContainer = Color(0xFFD1FAE5),
    loss = CoralDark,
    lossContainer = Color(0xFFFFE4E6),
    gold = DeepGold,
    goldContainer = Color(0xFFFEF3C7),
    electricBlue = RoyalBlue,
    purple = DeepPurple,
    teal = DeepTeal,
    cardSurface = PureWhite,
    cardElevated = SoftSlate100,
    cardBorder = SoftSlate200,
    heroStart = DeepNavy900,
    heroMid = RoyalBlueDark,
    heroEnd = DeepPurple,
    isDark = false
)

private val DarkColorScheme = darkColorScheme(
    primary = ElectricBlue,
    onPrimary = DeepNavy950,
    primaryContainer = DeepNavy700,
    onPrimaryContainer = TextLightPrimary,
    secondary = BrightGold,
    onSecondary = DeepNavy950,
    secondaryContainer = Color(0xFF422C08),
    onSecondaryContainer = BrightGold,
    tertiary = VibrantPurple,
    onTertiary = PureWhite,
    tertiaryContainer = Color(0xFF2E1E5C),
    onTertiaryContainer = Color(0xFFE9D5FF),
    background = DeepNavy950,
    onBackground = TextLightPrimary,
    surface = DeepNavy900,
    onSurface = TextLightPrimary,
    surfaceVariant = DeepNavy800,
    onSurfaceVariant = TextLightSecondary,
    outline = Color(0xFF2C4875),
    error = CoralRed,
    onError = PureWhite
)

private val LightColorScheme = lightColorScheme(
    primary = RoyalBlue,
    onPrimary = PureWhite,
    primaryContainer = Color(0xFFDBEAFE),
    onPrimaryContainer = DeepNavy900,
    secondary = DeepGold,
    onSecondary = PureWhite,
    secondaryContainer = Color(0xFFFEF3C7),
    onSecondaryContainer = Color(0xFF78350F),
    tertiary = DeepPurple,
    onTertiary = PureWhite,
    tertiaryContainer = Color(0xFFEDE9FE),
    onTertiaryContainer = Color(0xFF3B0764),
    background = SoftOffWhite,
    onBackground = TextDarkPrimary,
    surface = PureWhite,
    onSurface = TextDarkPrimary,
    surfaceVariant = SoftSlate100,
    onSurfaceVariant = TextDarkSecondary,
    outline = SoftSlate200,
    error = CoralDark,
    onError = PureWhite
)

val AppShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun BangaliBabuTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val extendedColors = if (darkTheme) DarkExtendedColors else LightExtendedColors

    CompositionLocalProvider(LocalTradingColors provides extendedColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            shapes = AppShapes,
            content = content
        )
    }
}
