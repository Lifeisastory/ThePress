package com.aster.feature.digest.impl

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.aster.core.design_system.component.AppLoadingIndicator
import com.aster.core.model.AgentStatus
import com.aster.core.ui.AgentMessageCard
import com.aster.core.ui.DigestAgentWelcome

@Composable
internal fun DigestChatContent(
    state: DigestAgentState, configured: Boolean, settingsFailed: Boolean,
    contentPadding: PaddingValues, list: LazyListState, onOpenArticle: (String) -> Unit,
    onSend: (String) -> Boolean, onRetry: () -> Unit,
    onReload: () -> Unit, onSaveAgain: () -> Unit,
    active: Boolean
) {
    val ready = configured && !state.loading && !state.readFailed
    LaunchedEffect(state.conversationId, state.messages.size, active) {
        if (active && state.messages.isNotEmpty()) list.animateScrollToItem(state.messages.size)
    }
    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            state = list, modifier = Modifier.fillMaxSize().testTag("digest_page"),
            contentPadding = contentPadding,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item(key = "status") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (state.loading) AppLoadingIndicator()
                    if (!configured) Text(stringResource(R.string.digest_setup_hint), Modifier.padding(horizontal = 8.dp),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (settingsFailed) Text(stringResource(R.string.digest_storage_error), color = MaterialTheme.colorScheme.error)
                    if (state.readFailed) TextButton(onClick = onReload) { Text(stringResource(R.string.agent_reload)) }
                    if (state.saveFailed) TextButton(onClick = onSaveAgain, enabled = !state.running && !state.loading) {
                        Text(stringResource(R.string.agent_save_again), color = MaterialTheme.colorScheme.error)
                    }
                    if (!state.loading && !state.readFailed && state.messages.isEmpty()) {
                        DigestAgentWelcome(ready, { onSend(it) })
                    }
                }
            }
            items(state.messages, key = { it.id }) { message ->
                AgentMessageCard(message, onOpenArticle, failureText = message.failure?.let { stringResource(it.message()) })
            }
            if (!state.running && state.messages.lastOrNull()?.status in listOf(AgentStatus.CANCELLED, AgentStatus.FAILED)) {
                item(key = "retry") {
                    TextButton(onClick = onRetry, enabled = ready, modifier = Modifier.testTag("agent_retry")) {
                        Text(stringResource(R.string.agent_retry))
                    }
                }
            }
        }
    }
}
