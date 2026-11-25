package com.example.ui.components

import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.ShowChart
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.R
import com.example.data.model.AccountStatus
import com.example.data.model.TradeDirection
import com.example.data.model.TradeEntity
import com.example.data.model.TradeResult
import com.example.data.model.TradingCurrency
import com.example.data.model.UserRole
import com.example.domain.TradeDateTimeHelper
import com.example.domain.TradingAnalytics
import com.example.ui.theme.LocalTradingColors
import androidx.compose.material.icons.rounded.CurrencyExchange
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.CircularProgressIndicator
import coil.compose.SubcomposeAsyncImage
import java.util.Locale

@Composable
fun rememberDecodedScreenshot(screenshotData: String?): ImageBitmap? {
    return remember(screenshotData) {
        if (screenshotData.isNullOrBlank()) null
        else runCatching {
            val bytes = Base64.decode(screenshotData, Base64.DEFAULT)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
        }.getOrNull()
    }
}

@Composable
fun BrandLogoBadge(
    size: Dp = 44.dp,
    modifier: Modifier = Modifier
) {
    val ext = LocalTradingColors.current
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(Color(0xFF0A1628))
            .border(1.5.dp, ext.gold, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(id = R.drawable.brand_logo),
            contentDescription = "Trading with Bangali Babu Logo",
            modifier = Modifier
                .fillMaxSize()
                .padding(2.dp)
                .clip(CircleShape),
            contentScale = ContentScale.Crop
        )
    }
}

@Composable
fun StudentAvatar(
    photoUrl: String,
    name: String,
    size: Dp = 48.dp,
    borderColor: Color? = null,
    isLoading: Boolean = false,
    showErrorIndicator: Boolean = false,
    onLoadError: ((String?) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val ext = LocalTradingColors.current
    val resolvedBorder = borderColor ?: ext.electricBlue
    val initials = remember(name) {
        val parts = name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        when {
            parts.size >= 2 -> "${parts[0].first().uppercaseChar()}${parts[1].first().uppercaseChar()}"
            parts.size == 1 -> parts[0].take(2).uppercase(Locale.US)
            else -> ""
        }
    }

    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(
                Brush.linearGradient(
                    listOf(
                        ext.electricBlue.copy(alpha = 0.22f),
                        ext.purple.copy(alpha = 0.22f)
                    )
                )
            )
            .border(1.5.dp, resolvedBorder, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        if (photoUrl.isNotBlank()) {
            ResilientFirebaseImage(
                model = photoUrl,
                contentDescription = "Profile photo of ${name.ifBlank { "Student" }}",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape),
                onLoadError = onLoadError,
                loadingContent = {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(
                            modifier = Modifier.size((size.value * 0.42f).dp),
                            strokeWidth = 2.dp,
                            color = resolvedBorder
                        )
                    }
                },
                errorContent = { _, _ ->
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        StudentAvatarFallback(initials = initials, size = size, tint = resolvedBorder)
                        if (showErrorIndicator && size >= 64.dp) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .fillMaxWidth()
                                    .background(ext.loss.copy(alpha = 0.85f))
                                    .padding(vertical = 2.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "Retry",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            )
        } else {
            StudentAvatarFallback(initials = initials, size = size, tint = resolvedBorder)
        }

        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.48f)),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size((size.value * 0.42f).dp),
                    strokeWidth = 2.5.dp,
                    color = Color.White
                )
            }
        }
    }
}

@Composable
private fun StudentAvatarFallback(
    initials: String,
    size: Dp,
    tint: Color
) {
    if (initials.isNotBlank()) {
        Text(
            text = initials,
            style = when {
                size >= 80.dp -> MaterialTheme.typography.headlineSmall
                size >= 48.dp -> MaterialTheme.typography.titleMedium
                else -> MaterialTheme.typography.labelMedium
            }.copy(fontWeight = FontWeight.Bold),
            color = tint,
            maxLines = 1
        )
    } else {
        Icon(
            imageVector = Icons.Rounded.Person,
            contentDescription = "Default student avatar",
            tint = tint,
            modifier = Modifier.size((size.value * 0.54f).dp)
        )
    }
}

@Composable
fun GradientKpiCard(
    title: String,
    value: String,
    subtitle: String,
    icon: ImageVector,
    gradientColors: List<Color>,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    val ext = LocalTradingColors.current
    Card(
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        border = BorderStroke(1.dp, accentColor.copy(alpha = if (ext.isDark) 0.35f else 0.25f)),
        elevation = CardDefaults.cardElevation(defaultElevation = if (ext.isDark) 4.dp else 2.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Brush.linearGradient(gradientColors))
                .padding(14.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Box(
                        modifier = Modifier
                            .size(30.dp)
                            .clip(RoundedCornerShape(9.dp))
                            .background(accentColor.copy(alpha = 0.16f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = title,
                            tint = accentColor,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                Text(
                    text = value,
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = accentColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun ResultBadge(result: TradeResult, modifier: Modifier = Modifier) {
    val ext = LocalTradingColors.current
    val (bg, fg) = when (result) {
        TradeResult.WIN -> ext.profitContainer to ext.profit
        TradeResult.LOSS -> ext.lossContainer to ext.loss
        TradeResult.BREAK_EVEN -> ext.goldContainer to ext.gold
    }
    Surface(
        modifier = modifier,
        color = bg,
        shape = RoundedCornerShape(999.dp),
        border = BorderStroke(1.dp, fg.copy(alpha = 0.4f))
    ) {
        Text(
            text = result.label.uppercase(Locale.US),
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
            color = fg,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}

@Composable
fun DirectionBadge(direction: TradeDirection, modifier: Modifier = Modifier) {
    val ext = LocalTradingColors.current
    val color = if (direction == TradeDirection.BUY) ext.profit else ext.loss
    Surface(
        modifier = modifier,
        color = color.copy(alpha = 0.14f),
        shape = RoundedCornerShape(6.dp)
    ) {
        Text(
            text = direction.label.uppercase(Locale.US),
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
            color = color,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
        )
    }
}

@Composable
fun RoleBadge(role: UserRole) {
    val ext = LocalTradingColors.current
    val color = if (role == UserRole.ADMIN) ext.gold else ext.electricBlue
    Surface(
        color = color.copy(alpha = 0.15f),
        shape = RoundedCornerShape(999.dp),
        border = BorderStroke(1.dp, color.copy(alpha = 0.5f))
    ) {
        Text(
            text = role.label.uppercase(Locale.US),
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
            color = color,
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp)
        )
    }
}

@Composable
fun StatusBadge(status: AccountStatus) {
    val ext = LocalTradingColors.current
    val color = if (status == AccountStatus.ACTIVE) ext.profit else ext.loss
    Surface(
        color = color.copy(alpha = 0.15f),
        shape = RoundedCornerShape(999.dp)
    ) {
        Text(
            text = status.label,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
            color = color,
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp)
        )
    }
}

@Composable
fun CurrencyBadge(currency: TradingCurrency, modifier: Modifier = Modifier) {
    val ext = LocalTradingColors.current
    val color = if (currency == TradingCurrency.INR) ext.gold else ext.teal
    Surface(
        modifier = modifier,
        color = color.copy(alpha = 0.15f),
        shape = RoundedCornerShape(6.dp),
        border = BorderStroke(1.dp, color.copy(alpha = 0.4f))
    ) {
        Text(
            text = "${currency.code} ${currency.symbol}",
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
            color = color,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

@Composable
fun DisplayCurrencySelectorBar(
    selectedCurrency: TradingCurrency,
    onSelectCurrency: (TradingCurrency) -> Unit,
    liveUsdToInrRate: Double,
    isRefreshingRate: Boolean = false,
    onRefreshRate: (() -> Unit)? = null,
    onHeroSurface: Boolean = false,
    modifier: Modifier = Modifier
) {
    val ext = LocalTradingColors.current
    val containerBg = if (onHeroSurface) Color.White.copy(alpha = 0.10f) else ext.cardElevated.copy(alpha = 0.7f)
    val borderCol = if (onHeroSurface) ext.gold.copy(alpha = 0.35f) else ext.cardBorder
    val primaryText = if (onHeroSurface) Color.White else MaterialTheme.colorScheme.onSurface
    val mutedText = if (onHeroSurface) Color.White.copy(alpha = 0.75f) else MaterialTheme.colorScheme.onSurfaceVariant

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = containerBg,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, borderCol)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = Icons.Rounded.CurrencyExchange,
                    contentDescription = "Display currency",
                    tint = ext.gold,
                    modifier = Modifier.size(16.dp)
                )
                Column {
                    Text(
                        text = "Display: ${selectedCurrency.label}",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = primaryText
                    )
                    Text(
                        text = "1 USD = ₹${String.format(Locale.US, "%.2f", liveUsdToInrRate)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = mutedText
                    )
                }
                if (onRefreshRate != null) {
                    IconButton(
                        onClick = onRefreshRate,
                        enabled = !isRefreshingRate,
                        modifier = Modifier.size(36.dp)
                    ) {
                        if (isRefreshingRate) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(14.dp),
                                strokeWidth = 2.dp,
                                color = ext.gold
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Rounded.Refresh,
                                contentDescription = "Refresh live USD/INR rate",
                                tint = ext.gold,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                TradingCurrency.entries.forEach { curr ->
                    val active = selectedCurrency == curr
                    Surface(
                        color = if (active) ext.gold else Color.Transparent,
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, if (active) ext.gold else borderCol),
                        modifier = Modifier.clickable { onSelectCurrency(curr) }
                    ) {
                        Text(
                            text = "${curr.symbol} ${curr.code}",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = if (active) Color(0xFF0A1628) else primaryText,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun GradientButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    colors: List<Color>? = null
) {
    val ext = LocalTradingColors.current
    val brushColors = colors ?: listOf(ext.electricBlue, ext.purple)
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(50.dp),
        contentPadding = PaddingValues(0.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.Transparent,
            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        shape = RoundedCornerShape(14.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    if (enabled) Brush.horizontalGradient(brushColors)
                    else Brush.horizontalGradient(
                        listOf(
                            MaterialTheme.colorScheme.surfaceVariant,
                            MaterialTheme.colorScheme.surfaceVariant
                        )
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.padding(horizontal = 16.dp)
            ) {
                if (icon != null) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Text(
                    text = text,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (enabled) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun EmptyTradingState(
    title: String,
    subtitle: String,
    icon: ImageVector = Icons.Rounded.ShowChart,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val ext = LocalTradingColors.current
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = ext.cardSurface),
        border = BorderStroke(1.dp, ext.cardBorder)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(ext.electricBlue.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = ext.electricBlue,
                    modifier = Modifier.size(28.dp)
                )
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            if (actionLabel != null && onAction != null) {
                Spacer(Modifier.height(4.dp))
                GradientButton(
                    text = actionLabel,
                    onClick = onAction,
                    modifier = Modifier.width(200.dp)
                )
            }
        }
    }
}

@Composable
fun TradeDetailDialog(
    trade: TradeEntity,
    displayCurrency: TradingCurrency = trade.currencyEnum,
    canEditOrDelete: Boolean,
    timezoneId: String = trade.effectiveTimezoneId,
    timeSincePrevTradeMillis: Long? = null,
    onDismiss: () -> Unit,
    onEdit: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
    onViewScreenshot: (String) -> Unit
) {
    val ext = LocalTradingColors.current
    val origCurr = trade.currencyEnum
    val displayedNetOrig = if (trade.isClosedTrade) trade.netRealizedPnl else trade.actualPnl
    val pnlColor = when {
        !trade.isClosedTrade -> ext.electricBlue
        displayedNetOrig > 0 -> ext.profit
        displayedNetOrig < 0 -> ext.loss
        else -> ext.gold
    }
    val convertedPnl = TradingAnalytics.convertedPnl(trade, displayCurrency)
    val convertedRisk = TradingAnalytics.convertedRisk(trade, displayCurrency)
    val decodedBitmap = rememberDecodedScreenshot(trade.screenshotData)
    val weekdayLabel = TradeDateTimeHelper.weekdayOf(
        entryTimeMillis = trade.entryTimeMillis,
        tradeDateMillis = trade.tradeDateMillis,
        tradeDateIso = trade.tradeDateIso,
        timezoneId = timezoneId
    )?.label ?: ""

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 24.dp),
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(containerColor = ext.cardSurface),
            border = BorderStroke(1.dp, ext.cardBorder)
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "${trade.tradeCode} • ${trade.instrument}",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "${trade.tradeDateIso}${if (weekdayLabel.isNotBlank()) " ($weekdayLabel)" else ""} • ${trade.userName}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Rounded.Close, contentDescription = "Close")
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    DirectionBadge(trade.directionEnum)
                    if (trade.isClosedTrade) {
                        ResultBadge(trade.resultEnum)
                    } else {
                        Surface(color = ext.goldContainer, shape = RoundedCornerShape(6.dp)) {
                            Text(
                                text = "OPEN",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = ext.gold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }
                    CurrencyBadge(origCurr)
                    Surface(
                        color = ext.purple.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = trade.timeframeEnum.label,
                            style = MaterialTheme.typography.labelSmall,
                            color = ext.purple,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }

                Surface(
                    color = pnlColor.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, pnlColor.copy(alpha = 0.3f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = if (trade.isClosedTrade) "Net Realized P&L (${origCurr.code})" else "Open Unrealized P&L (${origCurr.code})",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = TradingAnalytics.formatCurrency(displayedNetOrig, origCurr),
                                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                                    color = pnlColor
                                )
                                if (trade.fees > 0.0) {
                                    Text(
                                        text = "Gross ${TradingAnalytics.formatCurrency(trade.actualPnl, origCurr)} • Fees ${TradingAnalytics.formatPlainCurrency(trade.fees, origCurr)}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    "Risk / Lot (${origCurr.code})",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "${TradingAnalytics.formatPlainCurrency(trade.riskAmount, origCurr)} • ${trade.lotSize} lot",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }

                        HorizontalDivider(color = pnlColor.copy(alpha = 0.2f))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Saved Rate: 1 USD = ₹${String.format(Locale.US, "%.2f", trade.effectiveUsdToInrRate)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = ext.gold
                            )
                            if (origCurr != displayCurrency && trade.isClosedTrade) {
                                Text(
                                    text = "In ${displayCurrency.code}: ${TradingAnalytics.formatCurrency(convertedPnl, displayCurrency)} (Risk ${TradingAnalytics.formatPlainCurrency(convertedRisk, displayCurrency)})",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }

                HorizontalDivider(color = ext.cardBorder)

                val entryDisplay = trade.entryTimeMillis?.takeIf { it > 0L }?.let {
                    "${TradeDateTimeHelper.formatDateTimeDisplay(it, timezoneId)} (${TradeDateTimeHelper.shortTimezoneTag(timezoneId)})"
                } ?: "${trade.tradeDateIso} • Time not recorded"
                val exitDisplay = trade.exitTimeMillis?.takeIf { it > 0L }?.let {
                    "${TradeDateTimeHelper.formatDateTimeDisplay(it, timezoneId)} (${TradeDateTimeHelper.shortTimezoneTag(timezoneId)})"
                } ?: "Time not recorded"
                val durationDisplay = trade.holdingDurationMillis?.let {
                    TradeDateTimeHelper.formatDuration(it)
                } ?: "Not recorded"
                val gapDisplay = timeSincePrevTradeMillis?.let {
                    TradeDateTimeHelper.formatDuration(it)
                } ?: "N/A"

                DetailGridRow("Entry Date & Time", entryDisplay, "Exit Date & Time", exitDisplay)
                DetailGridRow("Holding Duration", durationDisplay, "Time Since Prev Trade", gapDisplay)
                DetailGridRow(
                    "Trading Session",
                    trade.tradingSession.ifBlank { "Unassigned" },
                    "Closure Mode",
                    trade.resolvedExitClosureType.label
                )
                DetailGridRow("Entry Price", trade.entryPrice.toString(), "Exit Price", trade.exitPrice.toString())
                DetailGridRow(
                    "Stop Loss",
                    trade.stopLoss?.toString() ?: "Not Set",
                    "Take Profit",
                    trade.takeProfit?.toString() ?: "Not Set"
                )
                DetailTextBlock("Trading Strategy", trade.strategy)
                DetailTextBlock("Entry Reason", trade.entryReason)
                DetailTextBlock("Exit Reason", trade.exitReason)
                DetailTextBlock("Mistakes", trade.mistakes)
                DetailTextBlock("Lessons Learned", trade.lessonsLearned)
                DetailTextBlock("Additional Notes", trade.notes)

                val shotData = trade.screenshotData
                if (!shotData.isNullOrBlank() && decodedBitmap != null) {
                    Text("Chart Screenshot", style = MaterialTheme.typography.labelMedium, color = ext.gold)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(170.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .border(1.dp, ext.cardBorder, RoundedCornerShape(12.dp))
                            .clickable { onViewScreenshot(shotData) }
                    ) {
                        Image(
                            bitmap = decodedBitmap,
                            contentDescription = "Chart screenshot for ${trade.tradeCode}",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                        Surface(
                            color = Color.Black.copy(alpha = 0.6f),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(Icons.Rounded.Image, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                                Text("Tap to zoom", style = MaterialTheme.typography.labelSmall, color = Color.White)
                            }
                        }
                    }
                }

                if (canEditOrDelete) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        if (onDelete != null) {
                            TextButton(onClick = onDelete) {
                                Icon(Icons.Rounded.Delete, contentDescription = "Delete", tint = ext.loss)
                                Spacer(Modifier.width(4.dp))
                                Text("Delete", color = ext.loss)
                            }
                        }
                        if (onEdit != null) {
                            Spacer(Modifier.width(8.dp))
                            Button(onClick = onEdit) {
                                Icon(Icons.Rounded.Edit, contentDescription = "Edit", modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Edit Trade")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailGridRow(label1: String, value1: String, label2: String, value2: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label1, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value1, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(label2, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value2, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

@Composable
private fun DetailTextBlock(label: String, content: String) {
    if (content.isNotBlank()) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            Text(content, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

@Composable
fun ScreenshotLightboxDialog(screenshotData: String, onDismiss: () -> Unit) {
    val decodedBitmap = rememberDecodedScreenshot(screenshotData)
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.92f))
                .clickable { onDismiss() },
            contentAlignment = Alignment.Center
        ) {
            if (decodedBitmap != null) {
                Image(
                    bitmap = decodedBitmap,
                    contentDescription = "Full chart screenshot",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                )
            }
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
                    .background(Color.Black.copy(alpha = 0.6f), CircleShape)
            ) {
                Icon(Icons.Rounded.Close, contentDescription = "Close preview", tint = Color.White)
            }
        }
    }
}
