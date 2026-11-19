package com.aster.feature.for_you.impl

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.aster.core.design_system.component.liquid_glass.AppDialog
import com.aster.core.model.NewsSource
import com.kyant.backdrop.Backdrop
import kotlinx.coroutines.launch

/** Main 只负责承载弹层；来源快照与保存行为由推荐页提供 */
class SourceOrderEditor(
    val sources: List<NewsSource>,
    val saveOrder: suspend (List<String>) -> Boolean
)

@Composable
fun SourceOrderDialog(
    editor: SourceOrderEditor,
    backdrop: Backdrop,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val initialIds = remember(editor) { editor.sources.map { it.id } }
    var draft by rememberSaveable(initialIds) { mutableStateOf(ArrayList(initialIds)) }
    val sourcesById = remember(editor) { editor.sources.associateBy { it.id } }
    var isDragging by remember { mutableStateOf(false) }
    var isSaving by remember { mutableStateOf(false) }
    var saveFailed by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val title = stringResource(R.string.for_you_source_order_title)

    AppDialog(
        title = title,
        backdrop = backdrop,
        onDismissRequest = onDismiss,
        dismissEnabled = !isSaving && !isDragging,
        modifier = modifier.testTag("for_you_source_order_dialog")
    ) { dismiss ->
        Text(title, style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))
        SourceOrderList(
            sources = draft.mapNotNull(sourcesById::get),
            backdrop = backdrop,
            enabled = !isSaving,
            onDraggingChanged = { isDragging = it },
            onMove = { fromId, toId ->
                val from = draft.indexOf(fromId)
                val to = draft.indexOf(toId)
                if (from >= 0 && to >= 0 && from != to) {
                    draft = ArrayList(draft).apply { add(to, removeAt(from)) }
                    saveFailed = false
                }
            },
            modifier = Modifier.weight(1f, fill = false)
        )
        if (saveFailed) {
            Spacer(Modifier.height(12.dp))
            Text(
                stringResource(R.string.for_you_source_order_save_failed),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall
            )
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(
                onClick = dismiss,
                enabled = !isSaving && !isDragging,
                modifier = Modifier.testTag("for_you_source_order_cancel")
            ) { Text(stringResource(R.string.for_you_source_order_cancel)) }
            Button(
                enabled = !isSaving && !isDragging,
                modifier = Modifier.testTag("for_you_source_order_save"),
                onClick = {
                    isSaving = true
                    saveFailed = false
                    scope.launch {
                        if (editor.saveOrder(draft.toList())) {
                            // 退出动画结束前继续禁用按钮，避免重复保存
                            dismiss()
                        } else {
                            isSaving = false
                            saveFailed = true
                        }
                    }
                }
            ) {
                Text(stringResource(if (isSaving) R.string.for_you_source_order_saving else R.string.for_you_source_order_done))
            }
        }
    }
}
