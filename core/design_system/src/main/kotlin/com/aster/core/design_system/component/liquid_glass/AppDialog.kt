package com.aster.core.design_system.component.liquid_glass

import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.dialog
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.aster.core.design_system.R
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.shapes.RoundedRectangle

/** 同窗口的模态玻璃弹层；应置于页面和底栏之后，backdrop 不包含弹层自身 */
@Composable
fun AppDialog(
    title: String,
    backdrop: Backdrop,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    dismissEnabled: Boolean = true,
    content: @Composable ColumnScope.(dismiss: () -> Unit) -> Unit
) {
    val visibility = remember { MutableTransitionState(false).apply { targetState = true } }
    val currentOnDismiss by rememberUpdatedState(onDismissRequest)
    val dismiss = { visibility.targetState = false }
    LaunchedEffect(visibility.isIdle, visibility.currentState) {
        if (visibility.isIdle && !visibility.currentState && !visibility.targetState) currentOnDismiss()
    }
    BackHandler { if (dismissEnabled) dismiss() }
    val surface = MaterialTheme.colorScheme.surface.copy(
        alpha = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) 0.97f else 0.65f
    )
    AnimatedVisibility(
        visibleState = visibility,
        modifier = modifier.fillMaxSize(),
        enter = fadeIn(tween(180)),
        exit = fadeOut(tween(160))
    ) {
        Box(Modifier.fillMaxSize()) {
            Box(
                Modifier.matchParentSize()
                    .background(Color.Black.copy(alpha = 0.4f))
                    .clickable(
                        interactionSource = null,
                        indication = null,
                        onClickLabel = stringResource(R.string.app_dismiss_dialog)
                    ) { if (dismissEnabled) dismiss() }
            )
            BoxWithConstraints(
                Modifier.fillMaxSize()
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Vertical))
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    Modifier.widthIn(max = 480.dp).fillMaxWidth().heightIn(max = maxHeight)
                        .animateEnterExit(
                            enter = scaleIn(tween(220), initialScale = 0.92f),
                            exit = scaleOut(tween(160), targetScale = 0.96f)
                        )
                        .semantics { dialog(); paneTitle = title }
                        .pointerInput(Unit) { detectTapGestures {} }
                        .drawBackdrop(
                            backdrop = backdrop,
                            shape = { RoundedRectangle(32.dp) },
                            effects = {
                                vibrancy()
                                blur(12.dp.toPx())
                                lens(24.dp.toPx(), 40.dp.toPx(), depthEffect = true)
                            },
                            highlight = { Highlight.Plain },
                            shadow = null,
                            onDrawSurface = { drawRect(surface) }
                        )
                        .padding(20.dp)
                ) {
                    content(dismiss)
                }
            }
        }
    }
}
