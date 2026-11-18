package com.aster.feature.webview.impl

import android.content.Context
import android.annotation.SuppressLint
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.webkit.WebView
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollDispatcher
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import kotlin.math.abs

/** 仅由 AndroidView factory 创建；将顶部单指下拉交给 AppRefreshLayout，其他手势仍由网页处理 */
@SuppressLint("ViewConstructor")
internal class RefreshWebView(
    context: Context,
    private val dispatcher: NestedScrollDispatcher
) : WebView(context) {
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private var startX = 0f
    private var startY = 0f
    private var lastY = 0f
    private var verticalDrag: Boolean? = null
    private var parentDragging = false
    private var multiTouch = false

    // 点击和无障碍操作交给 WebView 原有实现，刷新接管时只取消网页手势
    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                startX = event.x
                startY = event.y
                lastY = event.y
                verticalDrag = null
                parentDragging = false
                multiTouch = false
            }
            MotionEvent.ACTION_POINTER_DOWN -> multiTouch = true
            MotionEvent.ACTION_MOVE -> {
                val delta = event.y - lastY
                lastY = event.y
                if (!multiTouch) {
                    val dx = abs(event.x - startX)
                    val dy = abs(event.y - startY)
                    if (verticalDrag == null && maxOf(dx, dy) > touchSlop) verticalDrag = dy > dx
                    if (verticalDrag == true) {
                        val pre = dispatcher.dispatchPreScroll(Offset(0f, delta), NestedScrollSource.UserInput).y
                        val remaining = delta - pre
                        val post = if (remaining > 0f && !canScrollVertically(-1)) {
                            dispatcher.dispatchPostScroll(
                                Offset.Zero, Offset(0f, remaining), NestedScrollSource.UserInput
                            ).y
                        } else 0f
                        if (!parentDragging && pre + post != 0f) {
                            // 接管后取消网页触摸，避免松手时误点链接或触发网页惯性滚动
                            val cancel = MotionEvent.obtain(event)
                            cancel.action = MotionEvent.ACTION_CANCEL
                            super.onTouchEvent(cancel)
                            cancel.recycle()
                            parentDragging = true
                        }
                    }
                }
            }
        }
        if (parentDragging) return true
        return super.onTouchEvent(event)
    }
}
