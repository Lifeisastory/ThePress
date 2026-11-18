package com.aster.core.design_system.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun AppRankBadge(
    rank: Int,
    modifier: Modifier = Modifier
) {
    require(rank > 0) { "序号必须从 1 开始" }
    val backgroundColor = when (rank) {
        1 -> Color(0xFFD32F2F)
        2 -> Color(0xFFFF9800)
        3 -> Color(0xFFFBC02D)
        else -> Color(0xFF757575)
    }
    val contentColor = if (rank == 2 || rank == 3) Color(0xFF212121) else Color.White

    Box(
        modifier = modifier
            .defaultMinSize(minWidth = 28.dp, minHeight = 24.dp)
            .background(backgroundColor, RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = rank.toString(),
            color = contentColor,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            maxLines = 1
        )
    }
}
