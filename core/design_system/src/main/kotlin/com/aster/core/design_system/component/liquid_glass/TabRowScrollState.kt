package com.aster.core.design_system.component.liquid_glass

import androidx.compose.foundation.MutatePriority
import androidx.compose.foundation.gestures.ScrollScope
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.gestures.snapping.SnapLayoutInfoProvider
import androidx.compose.foundation.pager.PagerState
import androidx.compose.ui.util.lerp
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

/** 将文字间距换算成页面距离，所有拖动和吸附仍使用 Pager 的滚动互斥与状态 */
internal class TabRowScrollState(
    private val pagerState: PagerState,
    widths: List<Int>,
    gap: Float,
    private val flingThreshold: Float
) : ScrollableState by pagerState, SnapLayoutInfoProvider {
    val centers = FloatArray(widths.size).apply {
        for (index in 1 until size) {
            this[index] = this[index - 1] + (widths[index - 1] + widths[index]) / 2f + gap
        }
    }
    private val pagePosition: Float
        get() = (pagerState.currentPage + pagerState.currentPageOffsetFraction)
            .coerceIn(0f, centers.lastIndex.toFloat())
    val offset: Float get() = offsetAt(pagePosition)
    val maxOffset: Float get() = centers.last()

    override suspend fun scroll(scrollPriority: MutatePriority, block: suspend ScrollScope.() -> Unit) {
        pagerState.scroll(scrollPriority) {
            val pagerScope = this
            block(object : ScrollScope {
                override fun scrollBy(pixels: Float): Float = scrollTabsBy(pixels, pagerScope::scrollBy)
            })
        }
    }

    override fun dispatchRawDelta(delta: Float): Float = scrollTabsBy(delta, pagerState::dispatchRawDelta)

    private fun scrollTabsBy(delta: Float, consume: (Float) -> Float): Float {
        val pageSize = pagerState.layoutInfo.pageSize + pagerState.layoutInfo.pageSpacing
        if (pageSize == 0 || centers.size == 1 || delta == 0f) return 0f
        val startPage = pagePosition
        val startOffset = offsetAt(startPage)
        val targetOffset = (startOffset + delta).coerceIn(0f, maxOffset)
        val lower = centers.indexOfLast { it <= targetOffset }.coerceAtMost(centers.lastIndex - 1)
        val targetPage = lower + (targetOffset - centers[lower]) / (centers[lower + 1] - centers[lower])
        val consumed = consume((targetPage - startPage) * pageSize)
        return offsetAt(startPage + consumed / pageSize) - startOffset
    }

    private fun offsetAt(page: Float): Float {
        val bounded = page.coerceIn(0f, centers.lastIndex.toFloat())
        val lower = bounded.toInt()
        val upper = (lower + 1).coerceAtMost(centers.lastIndex)
        return lerp(centers[lower], centers[upper], bounded - lower)
    }

    override fun calculateApproachOffset(velocity: Float, decayOffset: Float): Float = 0f

    override fun calculateSnapOffset(velocity: Float): Float {
        val position = pagePosition
        val target = when {
            velocity > flingThreshold -> floor(position).toInt() + 1
            velocity < -flingThreshold -> ceil(position).toInt() - 1
            else -> position.roundToInt()
        }.coerceIn(centers.indices)
        return centers[target] - offset
    }
}
