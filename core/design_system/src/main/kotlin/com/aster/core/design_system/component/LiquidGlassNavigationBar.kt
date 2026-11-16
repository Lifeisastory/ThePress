package com.aster.core.design_system.component

import android.os.Build
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filterIsInstance
import kotlin.math.roundToInt

@Immutable
data class LiquidGlassNavigationItem(
    val label: String,
    val icon: ImageVector
)

/**
 * 悬浮玻璃导航栏。调用方持有选中状态，并负责外边距和系统栏 Insets。
 *
 * 将页面内容通过 Modifier.layerBackdrop 记录到同一个 backdrop；导航栏应作为内容的
 * 兄弟节点绘制在其上方，避免把导航栏自身录入背景。参见 LiquidGlassNavigationDemo。
 * 横向拖动时胶囊跟随手指，松手后才提交选中项；取消手势则回到原选中项。
 */
@Composable
fun LiquidGlassNavigationBar(
    items: List<LiquidGlassNavigationItem>,
    selectedIndex: Int,
    onItemSelected: (Int) -> Unit,
    backdrop: Backdrop,
    modifier: Modifier = Modifier
) {
    require(items.isNotEmpty()) { "Navigation items must not be empty" }
    require(selectedIndex in items.indices) { "Selected index must refer to a navigation item" }

    val colors = MaterialTheme.colorScheme
    val isLightSurface = colors.surface.luminance() > 0.5f
    // Android 12 以下没有背景模糊，提高底色不透明度以保证文字可读性。
    val surfaceColor = colors.surface.copy(
        alpha = when {
            Build.VERSION.SDK_INT < Build.VERSION_CODES.S -> 0.94f
            isLightSurface -> 0.18f
            else -> 0.30f
        }
    )
    // 记录底板最终外观（含高光、阴影），胶囊作为兄弟节点，不进入这份采样。
    val glassBackdrop = rememberLayerBackdrop()
    val contentTint = remember(colors.primary) {
        Paint().apply { colorFilter = ColorFilter.tint(colors.primary) }
    }
    // layerBackdrop 正常绘制原色内容，只将主题色版本记录给胶囊采样。
    // 记录范围仅包含图标、文字，不包含玻璃底板或胶囊，避免递归采样。
    val contentBackdrop = rememberLayerBackdrop {
        drawContext.canvas.saveLayer(Rect(0f, 0f, size.width, size.height), contentTint)
        try {
            drawContent()
        } finally {
            drawContext.canvas.restore()
        }
    }
    val currentSelectedIndex by rememberUpdatedState(selectedIndex)
    val currentOnItemSelected by rememberUpdatedState(onItemSelected)
    val interactionSource = remember { MutableInteractionSource() }
    val maxEdgePullPx = with(LocalDensity.current) { 6.dp.toPx() }
    var edgePullTarget by remember { mutableFloatStateOf(0f) }
    val edgePull by animateFloatAsState(
        targetValue = edgePullTarget,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = 400f),
        label = "Navigation surface edge pull"
    )
    var isPressed by remember { mutableStateOf(false) }
    var tapReleased by remember { mutableStateOf(false) }
    LaunchedEffect(interactionSource) {
        interactionSource.interactions.filterIsInstance<PressInteraction>().collect { interaction ->
            when (interaction) {
                is PressInteraction.Press -> {
                    isPressed = true
                    tapReleased = false
                }
                is PressInteraction.Release -> {
                    isPressed = false
                    tapReleased = true
                }
                is PressInteraction.Cancel -> {
                    isPressed = false
                    tapReleased = false
                }
            }
        }
    }

    // drawBackdrop 会裁剪其子节点；只把它用于底板，浮动胶囊作为兄弟节点绘制。
    Box(modifier = modifier.fillMaxWidth()) {
        Box(
            Modifier
                .matchParentSize()
                // 必须放在 drawBackdrop 外侧，才能录入它绘制的玻璃表面与装饰。
                .layerBackdrop(glassBackdrop)
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { CircleShape },
                    effects = {
                        vibrancy()
                        blur(4.dp.toPx())
                        lens(24.dp.toPx(), 32.dp.toPx(), depthEffect = true)
                    },
                    highlight = { Highlight.Default.copy(width = 1.dp) },
                    shadow = { Shadow(radius = 20.dp, color = Color.Black.copy(alpha = 0.16f)) },
                    innerShadow = { InnerShadow(radius = 8.dp, alpha = 0.45f) },
                    layerBlock = {
                        // 边缘拖动只让底板轻微平移，保持原有宽高。
                        translationX = edgePull.coerceIn(-maxEdgePullPx, maxEdgePullPx)
                    },
                    onDrawSurface = { drawRect(surfaceColor) }
                )
                .testTag("liquid_glass_navigation_surface")
        )
        BoxWithConstraints(Modifier.fillMaxWidth().padding(6.dp)) {
            val itemWidth = maxWidth / items.size
            val itemWidthPx = with(LocalDensity.current) { itemWidth.toPx() }
            val layoutDirection = LocalLayoutDirection.current
            var dragPosition by remember(items.size, itemWidthPx, layoutDirection) {
                mutableStateOf<Float?>(null)
            }
            val isDragging = dragPosition != null
            val dragDynamics = remember(items.size, itemWidthPx, layoutDirection) { LiquidGlassDragDynamics() }
            var stretchTarget by remember { mutableFloatStateOf(0f) }
            var dragSample by remember { mutableIntStateOf(0) }
            val capsulePosition = dragPosition ?: selectedIndex.toFloat()
            val distanceToEdge = minOf(capsulePosition, items.lastIndex - capsulePosition)
            val isAtEdge = distanceToEdge <= 0f
            val dragStretch by animateFloatAsState(
                targetValue = if (isAtEdge) 0f else stretchTarget,
                animationSpec = if (isAtEdge) snap() else spring(dampingRatio = 0.65f, stiffness = 650f),
                label = "Navigation capsule inertia"
            )
            LaunchedEffect(dragSample) {
                // 手指停住后也恢复形状，不依赖下一次移动事件。
                delay(80)
                stretchTarget = 0f
            }
            val indicatorPosition by animateFloatAsState(
                targetValue = dragPosition ?: selectedIndex.toFloat(),
                animationSpec = if (isDragging) snap() else spring(dampingRatio = 0.8f, stiffness = 400f),
                label = "Navigation indicator"
            )
            val liftProgress = remember { Animatable(0f) }
            val isInteracting = isPressed || isDragging
            LaunchedEffect(isInteracting, tapReleased) {
                if (isInteracting) {
                    liftProgress.animateTo(1f, tween(120))
                } else {
                    // 快速轻点也完成一次抬起反馈；拖动结束或取消则直接回弹。
                    if (tapReleased) liftProgress.animateTo(1f, tween(80))
                    liftProgress.animateTo(0f, spring(dampingRatio = 0.8f, stiffness = 500f))
                }
            }
            // Press 被拖动取消时，isInteracting 仍为 true，同一个动画保持当前进度。
            val interactionProgress = liftProgress.value.coerceIn(0f, 1f)
            // 接近两端时逐渐收起惯性形变，到达边界后保持按压尺寸。
            val stretchWeight = (distanceToEdge / 0.25f).coerceIn(0f, 1f)
            val stretch = 1f + dragStretch.coerceIn(-0.12f, 0.18f) * stretchWeight
            // 反向调整高度，保持按压后的面积，避免形变被感知为整体忽然缩小。
            val indicatorScaleX = (1f + 0.15f * interactionProgress) * stretch
            val indicatorScaleY = (1f + 0.3f * interactionProgress) / stretch
            // 按视觉层级叠加页面、完整底板、导航内容，再整体进行胶囊折射。
            // 页面层也保证胶囊放大到超出底板时仍有背景可采样。
            val indicatorBackdrop = rememberCombinedBackdrop(backdrop, glassBackdrop, contentBackdrop)

            Row(
                Modifier
                    .fillMaxWidth()
                    .selectableGroup()
                    .testTag("liquid_glass_navigation_tabs")
                    .layerBackdrop(contentBackdrop)
                    .pointerInput(items.size, itemWidthPx, layoutDirection, maxEdgePullPx) {
                        if (itemWidthPx <= 0f) return@pointerInput
                        fun updateDragPosition(x: Float): Boolean {
                            val logicalX = if (layoutDirection == LayoutDirection.Ltr) x else size.width - x
                            val rawPosition = logicalX / itemWidthPx - 0.5f
                            val clampedPosition = rawPosition.coerceIn(0f, (items.size - 1).toFloat())
                            dragPosition = clampedPosition
                            val direction = if (layoutDirection == LayoutDirection.Ltr) 1f else -1f
                            edgePullTarget = dampedGlassEdgePull(
                                (rawPosition - clampedPosition) * itemWidthPx * direction,
                                maxEdgePullPx
                            )
                            return clampedPosition > 0f && clampedPosition < items.lastIndex
                        }
                        fun resetDrag() {
                            dragPosition = null
                            stretchTarget = 0f
                            edgePullTarget = 0f
                            dragDynamics.reset()
                        }
                        detectHorizontalDragGestures(
                            onDragStart = {
                                dragDynamics.reset()
                                updateDragPosition(it.x)
                            },
                            onHorizontalDrag = { change, _ ->
                                change.consume()
                                val previousPosition = dragPosition ?: 0f
                                val wasInside = previousPosition > 0f && previousPosition < items.lastIndex
                                val isInside = updateDragPosition(change.position.x)
                                stretchTarget = if (isInside && wasInside) {
                                    dragDynamics.sample(
                                        (change.position.x - change.previousPosition.x) / itemWidthPx,
                                        change.uptimeMillis - change.previousUptimeMillis
                                    )
                                } else {
                                    // 边缘移动不积累速度，返回中间时也不带入越界位移。
                                    dragDynamics.reset()
                                    0f
                                }
                                dragSample++
                            },
                            onDragEnd = {
                                val targetIndex = dragPosition?.roundToInt()
                                resetDrag()
                                if (targetIndex != null && targetIndex != currentSelectedIndex) {
                                    currentOnItemSelected(targetIndex)
                                }
                            },
                            onDragCancel = { resetDrag() }
                        )
                    }
            ) {
                items.forEachIndexed { index, item ->
                    val selected = selectedIndex == index

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 60.dp)
                            .clip(CircleShape)
                            .selectable(
                                selected = selected,
                                interactionSource = interactionSource,
                                indication = null,
                                role = Role.Tab,
                                onClick = { onItemSelected(index) }
                            )
                            .padding(horizontal = 4.dp, vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically)
                    ) {
                        Icon(
                            imageVector = item.icon,
                            contentDescription = null,
                            modifier = Modifier.size(22.dp),
                            tint = colors.onSurface
                        )
                        Text(
                            text = item.label,
                            color = colors.onSurface,
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            // 先记录导航内容，再绘制折射胶囊，避免原色内容覆盖折射结果。
            Box(Modifier.matchParentSize()) {
                Box(
                    Modifier
                        .offset { IntOffset((indicatorPosition * itemWidthPx).roundToInt(), 0) }
                        .width(itemWidth)
                        .fillMaxHeight()
                        .drawBackdrop(
                            backdrop = indicatorBackdrop,
                            shape = { CircleShape },
                            effects = {
                                lens(
                                    (10.dp + 12.dp * interactionProgress).toPx(),
                                    (16.dp + 24.dp * interactionProgress).toPx(),
                                    chromaticAberration = true
                                )
                            },
                            highlight = { Highlight.Default.copy(width = 1.dp) },
                            shadow = { Shadow(radius = 8.dp, alpha = 0.5f + 0.5f * interactionProgress) },
                            innerShadow = { InnerShadow(radius = 6.dp, alpha = 0.5f) },
                            layerBlock = {
                                scaleX = indicatorScaleX
                                scaleY = indicatorScaleY
                                translationX = edgePull * 0.65f
                            },
                            onDrawSurface = {
                                drawRect(colors.primary.copy(alpha = 0.08f))
                            }
                        )
                        .testTag("liquid_glass_navigation_indicator")
                )
            }
        }
    }
}
