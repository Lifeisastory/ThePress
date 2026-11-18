package com.aster.core.design_system.component

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.aster.core.design_system.R
import kotlin.math.PI
import kotlin.math.cos

/** 自绘三点呼吸动画；由调用方控制显示时机和位置
 *  */
@Composable
fun AppLoadingIndicator(modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val description = stringResource(R.string.app_loading)
    val transition = rememberInfiniteTransition(label = "Loading dots")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(1200, easing = LinearEasing)),
        label = "Loading phase"
    )

    Box(
        modifier.semantics {
            contentDescription = description
            progressBarRangeInfo = ProgressBarRangeInfo.Indeterminate
        }
    ) {
        Canvas(Modifier.size(width = 48.dp, height = 24.dp)) {
            repeat(3) { index ->
                val pulse = (cos(phase - index * (2 * PI / 3).toFloat()) + 1f) / 2f
                drawCircle(
                    color = colors.primary.copy(alpha = 0.35f + 0.65f * pulse),
                    radius = (3f + pulse).dp.toPx(),
                    center = Offset(size.width * (index + 0.5f) / 3f, size.height / 2f)
                )
            }
        }
    }
}
