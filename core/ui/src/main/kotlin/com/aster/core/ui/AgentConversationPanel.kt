package com.aster.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aster.core.model.AgentConversationSummary

@Composable
fun AgentConversationPanel(
    conversations: List<AgentConversationSummary>, selectedId: Long?, enabled: Boolean,
    readFailed: Boolean, contentPadding: PaddingValues,
    onOpenConversation: (Long) -> Unit, onNewConversation: () -> Unit
) {
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainerLow)
        .testTag("agent_history_panel")) {
        Row(Modifier.fillMaxWidth().padding(top = contentPadding.calculateTopPadding(), start = 12.dp, end = 4.dp)
            .heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.agent_history_title), Modifier.weight(1f),
                style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            TextButton(onClick = onNewConversation, enabled = enabled) { Text(stringResource(R.string.agent_history_new)) }
        }
        if (readFailed) Text(stringResource(R.string.agent_history_read_failed), Modifier.padding(12.dp),
            color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        AgentConversationList(conversations, selectedId, enabled,
            PaddingValues(start = 8.dp, end = 8.dp, top = 8.dp, bottom = contentPadding.calculateBottomPadding()),
            onOpenConversation, Modifier.weight(1f))
    }
}
