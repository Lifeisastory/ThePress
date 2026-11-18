package com.aster.core.design_system.component.liquid_glass

import android.os.Build
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import androidx.compose.ui.zIndex
import com.aster.core.design_system.R
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.shapes.Capsule
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sign

@Immutable
data class AppNavigationItem(
    val label: String,
    val icon: ImageVector
)

/**
 * 使用 LiquidBottomTabs 示例的玻璃底板、着色采样层和可拖动透镜
 * 页面通过 Modifier.layerBackdrop 记录背景，导航栏作为兄弟节点绘制在其上方
 * 回到顶部的可见性及滚动行为由调用方决定，组件只负责呈现和转发点击
 */
@Composable
fun AppNavigationBar(
    items: List<AppNavigationItem>,
    selectedIndex: Int,
    onItemSelected: (Int) -> Unit,
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
    showScrollToTop: Boolean = false,
    onScrollToTop: () -> Unit = {}
) {
    require(items.isNotEmpty()) { "Navigation items must not be empty" }
    require(selectedIndex in items.indices) { "Selected index must refer to a navigation item" }

    val progress by animateFloatAsState(
        targetValue = if (showScrollToTop) 1f else 0f,
        animationSpec = tween(550),
        label = "navigationGlassSeparation"
    )
    BoxWithConstraints(modifier.fillMaxWidth().height(AppNavigationHeight)) {
        val navigationWidth = (maxWidth - (AppNavigationHeight + AppNavigationGap) * progress)
            .coerceAtLeast(AppNavigationHeight)
        AppNavigationSurface(
            backdrop = backdrop,
            progress = progress,
            modifier = Modifier.matchParentSize().testTag("liquid_glass_navigation_surface")
        )
        NavigationTabs(
            items = items,
            selectedIndex = selectedIndex,
            onItemSelected = onItemSelected,
            backdrop = backdrop,
            // 提升整个导航栏分支，确保放大后越界的透镜覆盖右侧按钮
            modifier = Modifier.align(Alignment.CenterStart).width(navigationWidth).zIndex(1f)
        )
        // 图标在水滴形成后显现；玻璃本身始终由共享底板绘制
        val iconProgress = ((progress - 0.45f) / 0.45f).coerceIn(0f, 1f)
        if (iconProgress > 0f) {
            IconButton(
                onClick = onScrollToTop,
                enabled = showScrollToTop && progress > 0.95f,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .offset {
                        val travel = AppNavigationTravel.toPx() * (1f - progress)
                        // offset 使用逻辑方向，RTL 下由 Compose 自动镜像
                        IntOffset(-travel.roundToInt(), 0)
                    }
                    .size(AppNavigationHeight)
                    .graphicsLayer { alpha = iconProgress }
                    .testTag("liquid_glass_scroll_to_top")
            ) {
                Icon(
                    imageVector = Icons.Default.KeyboardArrowUp,
                    contentDescription = stringResource(R.string.app_scroll_to_top),
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@Composable
private fun NavigationTabs(
    items: List<AppNavigationItem>,
    selectedIndex: Int,
    onItemSelected: (Int) -> Unit,
    backdrop: Backdrop,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    val isLightSurface = colors.surface.luminance() > 0.5f
    val containerColor = colors.surface.copy(
        alpha = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) 0.94f else 0.4f
    )
    val tabsBackdrop = rememberLayerBackdrop()
    val currentSelectedIndex by rememberUpdatedState(selectedIndex)
    val currentOnItemSelected by rememberUpdatedState(onItemSelected)

    BoxWithConstraints(
        modifier.fillMaxWidth(),
        contentAlignment = Alignment.CenterStart
    ) {
        val density = LocalDensity.current
        val tabWidth = with(density) { (maxWidth - 8.dp).toPx() / items.size }
        val currentTabWidth by rememberUpdatedState(tabWidth)
        val panelWidth by rememberUpdatedState(constraints.maxWidth.toFloat())
        val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr
        val animationScope = rememberCoroutineScope()
        val offsetAnimation = remember { Animatable(0f) }
        val panelOffset by remember(density, offsetAnimation) {
            derivedStateOf {
                val fraction = (offsetAnimation.value / panelWidth.coerceAtLeast(1f)).coerceIn(-1f, 1f)
                with(density) { 4.dp.toPx() * fraction.sign * EaseOut.transform(abs(fraction)) }
            }
        }
        val resetPanelOffset: () -> Unit = {
            animationScope.launch { offsetAnimation.animateTo(0f, spring(1f, 300f, 0.5f)) }
        }
        // 右侧按钮动画会连续改变 tabWidth，保持拖动状态并读取最新宽度
        val dragAnimation = remember(animationScope, items.size, isLtr) {
            DampedDragAnimation(
                animationScope = animationScope,
                initialValue = currentSelectedIndex.toFloat(),
                valueRange = 0f..items.lastIndex.toFloat(),
                visibilityThreshold = 0.001f,
                initialScale = 1f,
                pressedScale = 78f / 56f,
                onDragStarted = {},
                onDragStopped = {
                    val targetIndex = targetValue.roundToInt().coerceIn(items.indices)
                    animateToValue(targetIndex.toFloat())
                    resetPanelOffset()
                    if (targetIndex != currentSelectedIndex) currentOnItemSelected(targetIndex)
                },
                onDragCancelled = {
                    animateToValue(currentSelectedIndex.toFloat())
                    resetPanelOffset()
                },
                onDrag = { _, dragAmount ->
                    if (currentTabWidth > 0f) {
                        updateValue(targetValue + dragAmount.x / currentTabWidth * if (isLtr) 1f else -1f)
                        animationScope.launch {
                            offsetAnimation.snapTo(offsetAnimation.value + dragAmount.x)
                        }
                    }
                }
            )
        }
        // 外部导航（包括返回键）只同步动画，不再次发出导航事件
        LaunchedEffect(selectedIndex, dragAnimation) {
            if (dragAnimation.targetValue != selectedIndex.toFloat()) {
                dragAnimation.animateToValue(selectedIndex.toFloat())
            }
        }
        val interactiveHighlight = remember(animationScope, dragAnimation, isLtr) {
            InteractiveHighlight(animationScope) { size, _ ->
                Offset(
                    if (isLtr) (dragAnimation.value + 0.5f) * currentTabWidth + panelOffset
                    else size.width - (dragAnimation.value + 0.5f) * currentTabWidth + panelOffset,
                    size.height / 2f
                )
            }
        }

        Box(
            Modifier
                // 高光与共享底板保持同一轮廓，避免按压时整圈溢出
                .clip(Capsule())
                .then(interactiveHighlight.modifier)
                .height(64.dp)
                .fillMaxWidth()
        ) {
            Row(
                Modifier.fillMaxSize().padding(4.dp)
                    .selectableGroup()
                    .testTag("liquid_glass_navigation_tabs"),
                verticalAlignment = Alignment.CenterVertically
            ) {
                items.forEachIndexed { index, item ->
                    NavigationTab(
                        item = item,
                        selected = selectedIndex == index,
                        onClick = { if (index != currentSelectedIndex) currentOnItemSelected(index) }
                    )
                }
            }
        }

        // 隐藏层只给透镜采样：包含玻璃底色和主题色内容，不参与点击及无障碍遍历
        Row(
            Modifier
                .clearAndSetSemantics {}
                .alpha(0f)
                .layerBackdrop(tabsBackdrop)
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { Capsule() },
                    effects = {
                        val progress = dragAnimation.pressProgress
                        vibrancy()
                        blur(8.dp.toPx())
                        lens(24.dp.toPx() * progress, 24.dp.toPx() * progress)
                    },
                    highlight = { Highlight.Default.copy(alpha = dragAnimation.pressProgress) },
                    // 采样层不记录整条导航栏的阴影，避免被放大的透镜再次采样
                    shadow = null,
                    onDrawSurface = { drawRect(containerColor) }
                )
                .then(interactiveHighlight.modifier)
                .height(56.dp)
                .fillMaxWidth()
                .padding(horizontal = 4.dp)
                .graphicsLayer(colorFilter = ColorFilter.tint(colors.primary)),
            verticalAlignment = Alignment.CenterVertically
        ) {
            items.forEach { item ->
                NavigationTab(item = item, scale = { lerp(1f, 1.2f, dragAnimation.pressProgress) })
            }
        }

        Box(
            Modifier
                .padding(horizontal = 4.dp)
                .graphicsLayer {
                    translationX = dragAnimation.value * tabWidth * (if (isLtr) 1f else -1f) + panelOffset
                }
                .then(interactiveHighlight.gestureModifier)
                .then(dragAnimation.modifier)
                .drawBackdrop(
                    backdrop = rememberCombinedBackdrop(backdrop, tabsBackdrop),
                    shape = { Capsule() },
                    effects = {
                        val progress = dragAnimation.pressProgress
                        lens(10.dp.toPx() * progress, 14.dp.toPx() * progress, chromaticAberration = true)
                    },
                    highlight = { Highlight.Default.copy(alpha = dragAnimation.pressProgress) },
                    shadow = null,
                    innerShadow = null,
                    layerBlock = {
                        scaleX = dragAnimation.scaleX
                        scaleY = dragAnimation.scaleY
                        val velocity = dragAnimation.velocity / 10f
                        scaleX /= 1f - (velocity * 0.75f).coerceIn(-0.2f, 0.2f)
                        scaleY *= 1f - (velocity * 0.25f).coerceIn(-0.2f, 0.2f)
                    },
                    onDrawSurface = {
                        val progress = dragAnimation.pressProgress
                        drawRect(
                            if (isLightSurface) Color.Black.copy(alpha = 0.1f) else Color.White.copy(alpha = 0.1f),
                            alpha = 1f - progress
                        )
                        drawRect(Color.Black.copy(alpha = 0.03f * progress))
                    }
                )
                .testTag("liquid_glass_navigation_indicator")
                .height(56.dp)
                .width(with(density) { tabWidth.coerceAtLeast(0f).toDp() })
        )
    }
}

@Composable
private fun RowScope.NavigationTab(
    item: AppNavigationItem,
    selected: Boolean = false,
    onClick: (() -> Unit)? = null,
    scale: () -> Float = { 1f }
) {
    Column(
        Modifier
            .clip(Capsule())
            .then(
                if (onClick != null) {
                    Modifier.selectable(
                        selected = selected,
                        interactionSource = null,
                        indication = null,
                        role = Role.Tab,
                        onClick = onClick
                    )
                } else Modifier
            )
            .fillMaxHeight()
            .weight(1f)
            .graphicsLayer {
                val contentScale = scale()
                scaleX = contentScale
                scaleY = contentScale
            },
        verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(item.icon, contentDescription = null, modifier = Modifier.size(22.dp), tint = MaterialTheme.colorScheme.onSurface)
        Text(
            text = item.label,
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
