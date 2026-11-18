package com.aster.core.design_system.component.liquid_glass

import android.os.Build
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.CollectionInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.ScrollAxisRange
import androidx.compose.ui.semantics.collectionInfo
import androidx.compose.ui.semantics.horizontalScrollAxisRange
import androidx.compose.ui.semantics.scrollBy
import androidx.compose.ui.semantics.scrollToIndex
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.aster.core.design_system.modifier.blurFadeEdges
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.shapes.Capsule
import kotlin.math.roundToInt
import kotlin.math.sign

@Immutable
data class AppTab(val id: String, val label: String)

/** 选中胶囊固定居中；文字与 Pager 同步滑动，页面停稳后调整胶囊宽度 */
@Composable
fun AppTabRow(
    tabs: List<AppTab>,
    pagerState: PagerState,
    onTabSelected: (Int) -> Unit,
    backdrop: Backdrop,
    modifier: Modifier = Modifier
) {
    require(tabs.isNotEmpty())
    val colors = MaterialTheme.colorScheme
    val surface = colors.surface.copy(
        alpha = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) 0.94f else 0.4f
    )
    val density = LocalDensity.current
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val textMeasurer = rememberTextMeasurer()
    val textStyle = MaterialTheme.typography.labelLarge

    BoxWithConstraints(
        modifier.height(48.dp)
            .drawBackdrop(
                backdrop = backdrop,
                shape = { Capsule() },
                effects = {
                    vibrancy()
                    blur(8.dp.toPx())
                    lens(20.dp.toPx(), 24.dp.toPx())
                },
                onDrawSurface = { drawRect(surface) }
            )
            .padding(4.dp)
            .semantics(mergeDescendants = true) {}
    ) {
        val edgeWidth = minOf(24.dp, maxWidth / 4)
        val maxTabWidth = with(density) { minOf(144.dp, maxWidth - edgeWidth * 2).roundToPx() }
            .coerceAtLeast(1)
        val minTabWidth = with(density) { 56.dp.roundToPx() }.coerceAtMost(maxTabWidth)
        val textPadding = with(density) { 28.dp.roundToPx() }
        // 预留两种字重中较宽的一种，选中状态变化不会推动相邻文字
        val widths = remember(tabs, textMeasurer, textStyle, maxTabWidth, minTabWidth, textPadding) {
            tabs.map { tab ->
                val textWidth = maxOf(
                    textMeasurer.measure(tab.label, textStyle.copy(fontWeight = FontWeight.Normal)).size.width,
                    textMeasurer.measure(tab.label, textStyle.copy(fontWeight = FontWeight.SemiBold)).size.width
                )
                (textWidth + textPadding).coerceIn(minTabWidth, maxTabWidth)
            }
        }
        val gap = with(density) { 4.dp.toPx() }
        val flingThreshold = with(density) { 400.dp.toPx() }
        val scrollState = remember(pagerState, widths, gap, flingThreshold) {
            TabRowScrollState(pagerState, widths, gap, flingThreshold)
        }
        val flingBehavior = rememberSnapFlingBehavior(scrollState)
        val settledIndex = pagerState.settledPage.coerceIn(tabs.indices)
        val indicatorWidth = remember { Animatable(widths[settledIndex].toFloat()) }
        LaunchedEffect(pagerState.isScrollInProgress, widths[settledIndex]) {
            if (pagerState.isScrollInProgress) {
                indicatorWidth.stop()
            } else {
                indicatorWidth.animateTo(widths[settledIndex].toFloat(), tween(50))
            }
        }

        Box(
            Modifier
                .align(Alignment.Center)
                .width(with(density) { indicatorWidth.value.toDp() })
                .height(40.dp)
                .testTag("glass_tab_indicator")
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { Capsule() },
                    effects = {
                        blur(4.dp.toPx())
                        lens(8.dp.toPx(), 12.dp.toPx())
                    },
                    shadow = null,
                    onDrawSurface = {
                        drawRect(surface)
                        drawRect(colors.primary.copy(alpha = 0.12f))
                    }
                )
        )
        Layout(
            modifier = Modifier
                .fillMaxSize()
                .blurFadeEdges(orientation = Orientation.Horizontal, edgeSize = edgeWidth)
                .selectableGroup()
                .semantics {
                    collectionInfo = CollectionInfo(1, tabs.size)
                    horizontalScrollAxisRange = ScrollAxisRange(
                        value = { scrollState.offset },
                        maxValue = { scrollState.maxOffset },
                        reverseScrolling = isRtl
                    )
                    scrollToIndex { index ->
                        if (index in tabs.indices) {
                            onTabSelected(index)
                            true
                        } else false
                    }
                    // 无障碍滚动同样整页吸附，避免停在两个来源之间
                    scrollBy { x, _ ->
                        val direction = (if (isRtl) -x else x).sign.toInt()
                        val target = (pagerState.currentPage + direction).coerceIn(tabs.indices)
                        if (target != pagerState.currentPage) {
                            onTabSelected(target)
                            true
                        } else false
                    }
                }
                .scrollable(
                    state = scrollState,
                    orientation = Orientation.Horizontal,
                    reverseDirection = !isRtl,
                    flingBehavior = flingBehavior
                ),
            content = {
                tabs.forEachIndexed { index, tab ->
                    key(tab.id) {
                        val selected = index == pagerState.currentPage
                        Box(
                            Modifier
                                .selectable(
                                    selected = selected,
                                    interactionSource = null,
                                    indication = null,
                                    role = Role.Tab,
                                    onClick = { onTabSelected(index) }
                                )
                                .testTag("glass_tab_${tab.id}")
                                .padding(horizontal = 14.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = tab.label,
                                style = textStyle,
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                color = if (selected) colors.primary else colors.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        ) { measurables, constraints ->
            val placeables = measurables.mapIndexed { index, measurable ->
                measurable.measure(Constraints.fixed(widths[index], constraints.maxHeight))
            }
            layout(constraints.maxWidth, constraints.maxHeight) {
                // 在放置阶段读取进度；首尾项也以视口中心为锚点，无需额外滚动动画
                val offset = scrollState.offset
                placeables.forEachIndexed { index, placeable ->
                    val x = constraints.maxWidth / 2f + scrollState.centers[index] - offset - placeable.width / 2f
                    placeable.placeRelative(x.roundToInt(), 0)
                }
            }
        }
    }
}
