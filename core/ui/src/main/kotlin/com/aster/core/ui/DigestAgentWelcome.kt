package com.aster.core.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

@Composable
fun DigestAgentWelcome(enabled: Boolean, onPrompt: (String) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(stringResource(R.string.agent_welcome), style = MaterialTheme.typography.headlineMedium)
        Text(stringResource(R.string.agent_welcome_detail), style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        listOf(R.string.agent_prompt_briefing, R.string.agent_prompt_technology, R.string.agent_prompt_compare).forEach { resource ->
            val prompt = stringResource(resource)
            SuggestionChip(onClick = { onPrompt(prompt) }, enabled = enabled, label = { Text(prompt) })
        }
    }
}
