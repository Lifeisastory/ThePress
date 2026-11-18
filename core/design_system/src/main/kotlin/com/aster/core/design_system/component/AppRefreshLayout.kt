package com.aster.core.design_system.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToDownIgnoreConsumed
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import com.aster.core.design_system.component.liquid_glass.AppRefreshIndicator
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import kotlin.math.abs

/**
 * 包裹标准纵向滚动内容，在顶部下拉刷新、底部上拉加载；阻尼后的距离达到 [threshold] 后松手触发
 *
 * [scrollState] 必须与内部列表共用，可传入 LazyListState、LazyGridState 或 ScrollState
 * 回调只通知业务层，调用方应立即更新 [isRefreshing] / [isLoadingMore]，并在成功或失败后复位
 * 两种请求互斥；请求进行中仍允许正常滚动，但不会再次触发拉动操作
 * [hasMore] 为 false 时，上拉仅显示“没有更多了”。空列表仍可下拉刷新
 *
 * 容器不会给列表添加安全区或内容 padding。[indicatorPadding] 仅定位覆盖层指示器，
 * 可传入页面顶部控件、底部导航栏的避让距离，保持列表的沉浸视口
 */
@Composable
fun AppRefreshLayout(
    scrollState: ScrollableState,
    isRefreshing: Boolean,
    isLoadingMore: Boolean,
    hasMore: Boolean,
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    indicatorPadding: PaddingValues = PaddingValues(0.dp),
    threshold: Dp = 64.dp,
    content: @Composable BoxScope.() -> Unit
) {
    val backgroundColor = MaterialTheme.colorScheme.background
    val indicatorBackdrop = rememberLayerBackdrop {
        drawRect(backgroundColor)
        drawContent()
    }
    val thresholdPx = with(LocalDensity.current) { threshold.toPx() }
    val gesture = remember(scrollState, thresholdPx) { RefreshGestureState(thresholdPx) }
    val currentEnabled by rememberUpdatedState(enabled)
    val currentBusy by rememberUpdatedState(isRefreshing || isLoadingMore)
    val currentHasMore by rememberUpdatedState(hasMore)
    val currentOnRefresh by rememberUpdatedState(onRefresh)
    val currentOnLoadMore by rememberUpdatedState(onLoadMore)

    LaunchedEffect(gesture, enabled, isRefreshing, isLoadingMore) {
        if (!enabled || isRefreshing || isLoadingMore) gesture.block()
    }

    val connection = remember(scrollState, gesture) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset = Offset(
                x = 0f,
                y = gesture.consumePreScroll(
                    available.y, source == NestedScrollSource.UserInput, currentEnabled, currentBusy
                )
            )

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource
            ): Offset = Offset(
                x = 0f,
                y = gesture.consumePostScroll(
                    available.y,
                    scrollState.canScrollBackward,
                    scrollState.canScrollForward,
                    source == NestedScrollSource.UserInput,
                    currentEnabled,
                    currentBusy
                )
            )

            override suspend fun onPreFling(available: Velocity): Velocity =
                Velocity(0f, gesture.consumeFling(available.y))
        }
    }

    val fraction by animateFloatAsState(
        targetValue = when {
            isRefreshing -> 1f
            isLoadingMore -> -1f
            else -> gesture.fraction
        },
        animationSpec = if (gesture.isTouching) snap() else tween(180),
        label = "Refresh indicator return"
    )

    Box(
        modifier = modifier
            .nestedScroll(connection)
            .pointerInput(gesture) {
                // 观察触摸生命周期，不消费指针，纵向拖动仍由列表的 nestedScroll 分发
                try {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            if (!gesture.isTouching && event.changes.any { it.changedToDownIgnoreConsumed() }) {
                                gesture.begin(currentEnabled, currentBusy)
                            }
                            if (gesture.isTouching && event.changes.none { it.pressed }) {
                                // Compose 的取消事件会将抬起变化预先消费；先记录，再等子组件处理完本帧。
                                val releasedNormally = event.changes.any { it.changedToUp() }
                                awaitPointerEvent(PointerEventPass.Final)
                                if (releasedNormally) {
                                    when (gesture.release(currentEnabled, currentBusy, currentHasMore)) {
                                        RefreshAction.Refresh -> currentOnRefresh()
                                        RefreshAction.LoadMore -> currentOnLoadMore()
                                        null -> Unit
                                    }
                                } else {
                                    gesture.cancel()
                                }
                            }
                        }
                    }
                } finally {
                    gesture.cancel()
                }
            }
    ) {
        // 指示器只采样列表；独立于覆盖层记录，避免将玻璃自身重复录入背景。
        Box(Modifier.fillMaxSize().layerBackdrop(indicatorBackdrop), content = content)
        // 只裁剪指示器的动画区域，不裁剪或缩小列表视口
        Box(Modifier.matchParentSize().padding(indicatorPadding).clipToBounds()) {
            if (fraction != 0f) {
                val atTop = fraction > 0f
                val progress = abs(fraction)
                AppRefreshIndicator(
                    atTop = atTop,
                    busy = if (atTop) isRefreshing else isLoadingMore,
                    hasMore = hasMore,
                    progress = progress,
                    backdrop = indicatorBackdrop,
                    modifier = Modifier
                        .align(if (atTop) Alignment.TopCenter else Alignment.BottomCenter)
                        .graphicsLayer {
                            alpha = progress.coerceIn(0f, 1f)
                            translationY = (1f - alpha) * size.height * if (atTop) -1f else 1f
                        }
                        .testTag(if (atTop) "refresh_indicator" else "load_more_indicator")
                )
            }
        }
    }
}

private enum class RefreshAction { Refresh, LoadMore }

/** 仅保存当前手势，不保存请求或列表数据。距离使用物理像素，正值为下拉 */
private class RefreshGestureState(private val thresholdPx: Float) {
    init {
        require(thresholdPx > 0f)
    }

    private var distance by mutableFloatStateOf(0f)
    var isTouching by mutableStateOf(false)
        private set
    private var blocked = false
    private var consumeNextFling = false
    private var flingHandled = false

    // 以半速累积拉动进度，超出阈值后限制最大显示进度
    val fraction: Float get() = (distance * 0.5f / thresholdPx).coerceIn(-1.5f, 1.5f)

    fun begin(enabled: Boolean, busy: Boolean) {
        distance = 0f
        isTouching = true
        blocked = !enabled || busy
        consumeNextFling = false
        flingHandled = false
    }

    fun block() {
        blocked = true
        distance = 0f
    }

    private fun canPull(userInput: Boolean, enabled: Boolean, busy: Boolean): Boolean {
        if (!enabled || busy) block()
        return isTouching && !blocked && userInput
    }

    /** 已拉出的距离先收回，剩余位移再交给列表。返回值始终是手指的实际位移 */
    fun consumePreScroll(delta: Float, userInput: Boolean, enabled: Boolean, busy: Boolean): Float {
        if (!canPull(userInput, enabled, busy)) return 0f
        val previous = distance
        distance = when {
            previous > 0f && delta < 0f -> (previous + delta).coerceAtLeast(0f)
            previous < 0f && delta > 0f -> (previous + delta).coerceAtMost(0f)
            else -> previous
        }
        return distance - previous
    }

    /** 只接收子列表到达边界后剩余的纵向拖动 */
    fun consumePostScroll(
        delta: Float,
        canScrollBackward: Boolean,
        canScrollForward: Boolean,
        userInput: Boolean,
        enabled: Boolean,
        busy: Boolean
    ): Float {
        if (!canPull(userInput, enabled, busy)) return 0f
        val atEdge = when {
            delta > 0f -> !canScrollBackward && distance >= 0f
            delta < 0f -> !canScrollForward && distance <= 0f
            else -> false
        }
        if (!atEdge) return 0f
        distance += delta
        return delta
    }

    fun release(enabled: Boolean, busy: Boolean, hasMore: Boolean): RefreshAction? {
        if (!isTouching) return null
        val action = if (!blocked && enabled && !busy && abs(fraction) >= 1f) {
            when {
                distance > 0f -> RefreshAction.Refresh
                hasMore -> RefreshAction.LoadMore
                else -> null
            }
        } else null
        consumeNextFling = distance != 0f && !flingHandled
        distance = 0f
        isTouching = false
        return action
    }

    /** Fling 回调可能在末次触摸事件的 Main 或 Final 阶段前后到达，只消费速度 */
    fun consumeFling(velocity: Float): Float {
        if (distance == 0f && !consumeNextFling) return 0f
        consumeNextFling = false
        flingHandled = true
        return velocity
    }

    fun cancel() {
        distance = 0f
        isTouching = false
        blocked = false
        consumeNextFling = false
        flingHandled = false
    }
}
