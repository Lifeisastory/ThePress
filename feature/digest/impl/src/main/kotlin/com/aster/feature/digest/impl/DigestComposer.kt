package com.aster.feature.digest.impl

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.aster.core.design_system.component.liquid_glass.AppExpandingInputBar
import com.aster.core.design_system.component.liquid_glass.AppIconButton
import com.kyant.backdrop.Backdrop
import kotlinx.coroutines.launch

/** 玻璃输入区是列表采样层的兄弟节点，避免录入自身造成反馈 */
@Composable
internal fun BoxScope.DigestComposer(
    state: DigestAgentState, ready: Boolean, list: LazyListState, backdrop: Backdrop, bottom: Dp,
    draft: String, onDraftChange: (String) -> Unit, expanded: Boolean, onExpandedChange: (Boolean) -> Unit,
    onSend: (String) -> Boolean, onCancel: () -> Unit, onHeightChanged: (Int) -> Unit,
    onInputBoundsChanged: (Rect) -> Unit, onActionBoundsChanged: (Rect) -> Unit
) {
    val scope = rememberCoroutineScope()
    Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().onSizeChanged { onHeightChanged(it.height) }
        .padding(start = 8.dp, end = 8.dp, bottom = bottom + 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (list.canScrollForward && state.messages.isNotEmpty()) AppIconButton(
            Icons.Default.KeyboardArrowDown, stringResource(R.string.agent_latest),
            { onExpandedChange(false); scope.launch { list.animateScrollToItem(state.messages.size, Int.MAX_VALUE) } }, backdrop,
            Modifier.align(Alignment.End).testTag("agent_latest")
        )
        AppExpandingInputBar(draft, onDraftChange, expanded, onExpandedChange, backdrop,
            icon = if (state.running) Icons.Default.Close else Icons.AutoMirrored.Filled.Send,
            actionDescription = stringResource(when { state.running -> R.string.agent_stop
                expanded -> R.string.agent_send; else -> R.string.agent_composer_expand }),
            onAction = { when { state.running -> onCancel(); !expanded -> onExpandedChange(true); else -> onSend(draft) } },
            placeholder = stringResource(R.string.agent_input_hint),
            enabled = state.running || (ready && (!expanded || draft.isNotBlank())), inputEnabled = ready && !state.running,
            inputModifier = Modifier.testTag("agent_input").onGloballyPositioned { onInputBoundsChanged(it.boundsInWindow()) },
            actionModifier = Modifier.testTag(if (state.running) "agent_stop" else "agent_send")
                .onGloballyPositioned { onActionBoundsChanged(it.boundsInWindow()) })
    }
}
