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
import com.example.data.model.UserRole
import com.example.domain.TradingAnalytics
import com.example.ui.theme.LocalTradingColors
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
    canEditOrDelete: Boolean,
    onDismiss: () -> Unit,
    onEdit: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
    onViewScreenshot: (String) -> Unit
) {
    val ext = LocalTradingColors.current
    val pnlColor = when {
        trade.actualPnl > 0 -> ext.profit
        trade.actualPnl < 0 -> ext.loss
        else -> ext.gold
    }
    val decodedBitmap = rememberDecodedScreenshot(trade.screenshotData)

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
                            text = "${trade.tradeDateIso} • ${trade.userName}",
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
                    ResultBadge(trade.resultEnum)
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
                    Row(
                        modifier = Modifier.padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Actual Profit / Loss", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                text = TradingAnalytics.formatCurrency(trade.actualPnl),
                                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                                color = pnlColor
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("Risk / Lot", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                text = "${TradingAnalytics.formatPlainCurrency(trade.riskAmount)} • ${trade.lotSize} lot",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }

                HorizontalDivider(color = ext.cardBorder)

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
