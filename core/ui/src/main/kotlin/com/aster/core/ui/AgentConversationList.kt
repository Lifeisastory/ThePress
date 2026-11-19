package com.aster.core.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aster.core.model.AgentConversationSummary
import java.text.DateFormat
import java.util.Date

@Composable
fun AgentConversationList(
    conversations: List<AgentConversationSummary>, selectedId: Long?, enabled: Boolean,
    contentPadding: PaddingValues, onOpenConversation: (Long) -> Unit, modifier: Modifier = Modifier
) {
    LazyColumn(modifier.fillMaxSize().testTag("agent_history_list"), contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (conversations.isEmpty()) item { Text(stringResource(R.string.agent_history_empty), Modifier.padding(16.dp)) }
        items(conversations, key = { it.id }) { item ->
            Surface(shape = RoundedCornerShape(20.dp),
                color = if (selectedId == item.id) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                    else MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier.fillMaxWidth().testTag("agent_conversation_${item.id}")
                    .clickable(enabled = enabled, role = Role.Button) { onOpenConversation(item.id) }) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(item.title.ifBlank { stringResource(R.string.agent_history_legacy) },
                        style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (item.updatedAt > 0) Text(DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
                            .format(Date(item.updatedAt)), style = MaterialTheme.typography.bodySmall)
                        if (item.messageCount > 0) Text(stringResource(R.string.agent_history_count, item.messageCount),
                            style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}
