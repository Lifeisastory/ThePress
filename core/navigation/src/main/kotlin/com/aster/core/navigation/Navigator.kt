
package com.aster.core.navigation

import androidx.navigation3.runtime.NavKey

/** 通过应用返回栈处理独立路由页的打开与返回 */
class Navigator(val state: NavigationState) {
    /** 已在栈中的页面直接回到原条目，避免同一个路由键产生重复实例 */
    fun navigate(key: NavKey) {
        val stack = state.backStack
        val existingIndex = stack.indexOf(key)
        if (existingIndex >= 0) {
            stack.subList(existingIndex + 1, stack.size).clear()
        } else {
            stack.add(key)
        }
    }

    fun goBack() {
        // 栈底始终保留；根路由页的退出交给 Activity
        state.backStack.run {
            if (size > 1) removeLastOrNull()
        }
    }
}
