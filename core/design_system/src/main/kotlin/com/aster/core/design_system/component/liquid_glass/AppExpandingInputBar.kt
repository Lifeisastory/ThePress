package com.aster.core.design_system.component.liquid_glass

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop

/** 复用底部导航的融合轮廓，输入区域从右侧操作按钮分离并回收 */
@Composable
fun AppExpandingInputBar(
    value: String,
    onValueChange: (String) -> Unit,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    backdrop: Backdrop,
    icon: ImageVector,
    actionDescription: String,
    onAction: () -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    inputEnabled: Boolean = true,
    inputModifier: Modifier = Modifier,
    actionModifier: Modifier = Modifier
) {
    val progress by animateFloatAsState(if (expanded) 1f else 0f, tween(550), label = "inputGlassSeparation")
    val focus = remember { FocusRequester() }
    val bringIntoView = remember { BringIntoViewRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val inputVisible = expanded && progress > 0.8f
    val imeBottom = WindowInsets.ime.getBottom(LocalDensity.current)
    LaunchedEffect(inputVisible) {
        if (inputVisible) { focus.requestFocus(); keyboard?.show() }
    }
    LaunchedEffect(inputVisible, imeBottom) {
        if (inputVisible && imeBottom > 0) {
            // IME 改变布局后重新上报输入区，清除系统依据旧焦点位置产生的窗口平移
            withFrameNanos { }
            bringIntoView.bringIntoView()
        }
    }
    LaunchedEffect(expanded) {
        if (!expanded) { focusManager.clearFocus(); keyboard?.hide() }
    }
    BackHandler(enabled = expanded) { onExpandedChange(false) }
    BoxWithConstraints(modifier.fillMaxWidth().height(AppNavigationHeight), contentAlignment = Alignment.CenterEnd) {
        val width = AppNavigationHeight + (maxWidth - AppNavigationHeight) * progress
        Box(Modifier.width(width).fillMaxHeight()) {
            AppNavigationSurface(backdrop, progress, Modifier.matchParentSize())
            if (inputVisible) AppInputBar(
                value, onValueChange, backdrop,
                inputModifier.align(Alignment.CenterStart).width((width - AppNavigationHeight - AppNavigationGap).coerceAtLeast(0.dp))
                    .focusRequester(focus).bringIntoViewRequester(bringIntoView)
                    .graphicsLayer { alpha = ((progress - 0.8f) / 0.2f).coerceIn(0f, 1f) },
                placeholder = { Text(placeholder) }, enabled = inputEnabled, singleLine = true,
                onSubmit = onAction, drawSurface = false
            )
            IconButton(onClick = onAction, enabled = enabled,
                modifier = actionModifier.align(Alignment.CenterEnd).size(AppNavigationHeight)) {
                Icon(icon, actionDescription, tint = MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 1f else 0.38f))
            }
        }
    }
}
