package com.aster.core.design_system.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/** 左侧半屏与原宽内容并排放置，展开时同步右移，共用边界且不互相覆盖 */
@Composable
fun AppSideBySidePane(
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    gesturesEnabled: Boolean = true,
    onInteraction: () -> Unit = {},
    sideContent: @Composable () -> Unit,
    content: @Composable () -> Unit
) {
    var widthPx by remember { mutableIntStateOf(0) }
    var dragging by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableFloatStateOf(0f) }
    val progress by animateFloatAsState(
        targetValue = if (dragging) dragFraction else if (expanded) 1f else 0f,
        animationSpec = if (dragging) snap() else tween(280), label = "sideBySideReveal"
    )
    val threshold = with(LocalDensity.current) { 600.dp.toPx() }
    val currentExpandedChange by rememberUpdatedState(onExpandedChange)
    val currentInteraction by rememberUpdatedState(onInteraction)
    LaunchedEffect(gesturesEnabled) { if (!gesturesEnabled) dragging = false }
    Layout(
        modifier = modifier.fillMaxSize().clipToBounds().onSizeChanged { widthPx = it.width }
            .draggable(
                state = rememberDraggableState { delta ->
                    dragFraction = (dragFraction + delta / (widthPx / 2f).coerceAtLeast(1f)).coerceIn(0f, 1f)
                },
                orientation = Orientation.Horizontal,
                enabled = gesturesEnabled,
                onDragStarted = { dragFraction = progress; dragging = true; currentInteraction() },
                onDragStopped = { velocity ->
                    currentExpandedChange(when {
                        velocity > threshold -> true
                        velocity < -threshold -> false
                        else -> dragFraction >= 0.5f
                    })
                    dragging = false
                }
            ),
        content = {
            Box(Modifier.fillMaxSize()) { if (progress > 0f || expanded) sideContent() }
            Box(Modifier.fillMaxSize()) { content() }
        }
    ) { measurables, constraints ->
        val width = constraints.maxWidth
        val height = constraints.maxHeight
        val sideWidth = width / 2
        val side = measurables[0].measure(Constraints.fixed(sideWidth, height))
        val main = measurables[1].measure(Constraints.fixed(width, height))
        layout(width, height) {
            val reveal = (sideWidth * progress).roundToInt()
            side.place(reveal - sideWidth, 0)
            main.place(reveal, 0)
        }
    }
}
