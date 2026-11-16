package com.aster.core.design_system.component

import kotlin.math.abs

/** 以 tab 宽度归一化手势，避免屏幕密度和导航栏宽度改变形变强度。 */
internal class LiquidGlassDragDynamics {
    private var velocity = 0f

    fun reset() {
        velocity = 0f
    }

    fun sample(deltaTabs: Float, elapsedMillis: Long): Float {
        if (elapsedMillis <= 0 || !deltaTabs.isFinite()) return 0f
        // 长时间停顿后的首个事件不应被当成突然加速。
        if (elapsedMillis > 120) {
            reset()
            return 0f
        }
        val seconds = elapsedMillis.coerceAtLeast(8) / 1000f
        val previousSpeed = abs(velocity)
        val smoothing = seconds / (0.04f + seconds)
        velocity += (deltaTabs / seconds - velocity) * smoothing
        val acceleration = (abs(velocity) - previousSpeed) / seconds
        return (acceleration / 100f * 0.16f).coerceIn(-0.12f, 0.18f)
    }
}

/** 两端越拉越难，最大位移渐近于 maxPullPx，且保留物理拖动方向。 */
internal fun dampedGlassEdgePull(overflowPx: Float, maxPullPx: Float): Float {
    if (maxPullPx <= 0f || !overflowPx.isFinite()) return 0f
    val resisted = overflowPx * 0.35f
    return maxPullPx * (resisted / (maxPullPx + abs(resisted)))
}
