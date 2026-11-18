package com.aster.core.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.aster.core.model.StockInfo
import java.math.BigDecimal

@Composable
internal fun StockInfoContent(stock: StockInfo) {
    val darkSurface = MaterialTheme.colorScheme.surfaceContainer.luminance() < 0.5f
    val color = when {
        stock.change > 0 -> if (darkSurface) Color(0xFFFF8A80) else Color(0xFFB3261E)
        stock.change < 0 -> if (darkSurface) Color(0xFF81C784) else Color(0xFF1B6B35)
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            stock.code,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                formatStockNumber(stock.price),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = color
            )
            Text(
                formatStockNumber(stock.change, signed = true),
                style = MaterialTheme.typography.bodyMedium,
                color = color
            )
            Text(
                "${formatStockNumber(stock.changePercent, signed = true)}%",
                style = MaterialTheme.typography.bodyMedium,
                color = color
            )
        }
    }
}

/** 至少保留两位小数，同时保留来源的小数精度；避免科学计数法和负零 */
internal fun formatStockNumber(value: Double, signed: Boolean = false): String {
    val decimal = BigDecimal.valueOf(value).stripTrailingZeros()
    val text = decimal.setScale(maxOf(2, decimal.scale())).toPlainString()
    return if (signed && value > 0) "+$text" else text
}
