package com.aster.feature.for_you.impl

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.aster.core.design_system.component.liquid_glass.AppIcon
import com.aster.core.design_system.modifier.blurFadeEdges
import com.aster.core.model.NewsSource
import com.kyant.backdrop.Backdrop
import kotlin.math.abs

@Composable
internal fun SourceOrderList(
    sources: List<NewsSource>,
    backdrop: Backdrop,
    enabled: Boolean,
    onMove: (String, String) -> Unit,
    onDraggingChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    val currentOnMove by rememberUpdatedState(onMove)
    val currentOnDraggingChanged by rememberUpdatedState(onDraggingChanged)
    val moveSource = remember(listState) {
        { from: String, to: String ->
            // 重排首个可见项时固定视口位置，避免 LazyColumn 按原 key 跟随滚动
            listState.requestScrollToItem(listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset)
            currentOnMove(from, to)
        }
    }
    val drag = remember(listState, moveSource) { SourceOrderDragState(listState, moveSource) }
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current
    val edge = with(density) { 48.dp.toPx() }
    val maxSpeed = with(density) { 600.dp.toPx() }

    // 手指停在视口边缘时继续滚动，可将来源拖到屏幕以外的位置
    LaunchedEffect(drag.id, enabled) {
        if (drag.id == null || !enabled) return@LaunchedEffect
        var previousFrame = withFrameNanos { it }
        while (drag.id != null) {
            val frame = withFrameNanos { it }
            val seconds = ((frame - previousFrame) / 1_000_000_000f).coerceAtMost(0.032f)
            previousFrame = frame
            val info = listState.layoutInfo
            val speed = when {
                drag.pointerY < info.viewportStartOffset + edge ->
                    -((info.viewportStartOffset + edge - drag.pointerY) / edge).coerceIn(0f, 1f)
                drag.pointerY > info.viewportEndOffset - edge ->
                    ((drag.pointerY - info.viewportEndOffset + edge) / edge).coerceIn(0f, 1f)
                else -> 0f
            }
            if (speed != 0f) {
                listState.scrollBy(speed * maxSpeed * seconds)
                drag.moveIfNeeded()
            }
        }
    }

    val moveUp = stringResource(R.string.for_you_source_order_move_up)
    val moveDown = stringResource(R.string.for_you_source_order_move_down)
    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxWidth().testTag("for_you_source_order_list")
            .blurFadeEdges(
                orientation = Orientation.Vertical,
                fadeStart = listState.canScrollBackward,
                fadeEnd = listState.canScrollForward
            )
            .pointerInput(drag, enabled) {
                if (enabled) detectDragGesturesAfterLongPress(
                    onDragStart = { offset ->
                        drag.start(offset.y)
                        if (drag.id != null) {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            currentOnDraggingChanged(true)
                        }
                    },
                    onDragEnd = { drag.stop(); currentOnDraggingChanged(false) },
                    onDragCancel = { drag.stop(); currentOnDraggingChanged(false) },
                    onDrag = { change, amount ->
                        change.consume()
                        drag.dragBy(amount.y)
                    }
                )
            },
        userScrollEnabled = enabled && drag.id == null,
        contentPadding = PaddingValues(vertical = 4.dp, horizontal = 4.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        itemsIndexed(sources, key = { _, source -> source.id }) { index, source ->
            val isDragging = drag.id == source.id
            val releaseOffset by animateFloatAsState(
                targetValue = if (isDragging) drag.translationY else 0f,
                animationSpec = if (isDragging) snap() else spring(stiffness = 600f),
                label = "sourceOrderOffset"
            )
            val scale by animateFloatAsState(if (isDragging) 1.02f else 1f, label = "sourceOrderScale")
            val position = stringResource(R.string.for_you_source_order_position, index + 1, sources.size)
            Row(
                modifier = Modifier
                    .animateItem(placementSpec = if (isDragging) null else spring(stiffness = 600f))
                    .zIndex(if (isDragging || abs(releaseOffset) > 0.5f) 1f else 0f)
                    .graphicsLayer {
                        // 重排后布局位置会立即改变，补偿偏移必须在同一帧读取
                        // snap() 仍经过动画协程，拖动时使用它会短暂叠加上一帧的偏移
                        translationY = if (drag.id == source.id) drag.translationY else releaseOffset
                        scaleX = scale
                        scaleY = scale
                    }
                    .background(
                        if (isDragging) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surface.copy(alpha = 0.35f),
                        RoundedCornerShape(20.dp)
                    )
                    .fillMaxWidth().height(64.dp).padding(horizontal = 8.dp)
                    .testTag("for_you_source_order_${source.id}")
                    .semantics(mergeDescendants = true) {
                        stateDescription = position
                        customActions = if (!enabled || drag.id != null) emptyList() else buildList {
                            if (index > 0) add(CustomAccessibilityAction(moveUp) {
                                moveSource(source.id, sources[index - 1].id); true
                            })
                            if (index < sources.lastIndex) add(CustomAccessibilityAction(moveDown) {
                                moveSource(source.id, sources[index + 1].id); true
                            })
                        }
                    },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                AppIcon(source.id, contentDescription = null, backdrop = backdrop)
                Text(source.name, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Icon(Icons.Default.Menu, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Stable
private class SourceOrderDragState(
    private val listState: LazyListState,
    private val onMove: (String, String) -> Unit
) {
    var id: String? by mutableStateOf(null)
        private set
    var pointerY by mutableFloatStateOf(0f)
        private set
    private var top by mutableFloatStateOf(0f)
    private var height = 0
    private var pendingIndex: Int? = null

    val translationY: Float
        get() = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == id }
            ?.let { top - it.offset } ?: 0f

    fun start(y: Float) {
        val info = listState.layoutInfo
        val position = y + info.viewportStartOffset
        val item = info.visibleItemsInfo.firstOrNull { position >= it.offset && position < it.offset + it.size }
            ?: return
        id = item.key as String
        pointerY = position
        top = item.offset.toFloat()
        height = item.size
    }

    fun dragBy(delta: Float) {
        if (id == null) return
        pointerY += delta
        val info = listState.layoutInfo
        val minTop = info.viewportStartOffset.toFloat()
        val maxTop = (info.viewportEndOffset - height).toFloat().coerceAtLeast(minTop)
        top = (top + delta).coerceIn(minTop, maxTop)
        moveIfNeeded()
    }

    fun moveIfNeeded() {
        val items = listState.layoutInfo.visibleItemsInfo
        val dragged = items.firstOrNull { it.key == id } ?: return
        // 等待上次重排落到布局，避免同一帧依据旧坐标反复交换
        if (pendingIndex != null && dragged.index != pendingIndex) return
        pendingIndex = null
        val center = top + height / 2f
        val target = items.firstOrNull { it.key != id && center >= it.offset && center < it.offset + it.size }
            ?: return
        pendingIndex = target.index
        onMove(dragged.key as String, target.key as String)
    }

    fun stop() {
        id = null
        pendingIndex = null
    }
}
