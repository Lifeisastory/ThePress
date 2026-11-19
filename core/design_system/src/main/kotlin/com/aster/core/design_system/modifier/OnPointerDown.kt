package com.aster.core.design_system.modifier

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput

/** 观察按下位置而不消费事件，子组件继续处理点击、选择和滚动 */
@Composable
fun Modifier.onPointerDown(onDown: (Offset) -> Unit): Modifier {
    val currentOnDown by rememberUpdatedState(onDown)
    return pointerInput(Unit) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            currentOnDown(down.position)
        }
    }
}
