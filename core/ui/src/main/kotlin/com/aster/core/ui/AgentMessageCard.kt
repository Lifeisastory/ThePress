package com.aster.core.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.aster.core.design_system.component.AppLoadingIndicator
import com.aster.core.design_system.component.AppMarkdownText
import com.aster.core.model.*

/** 消息按发送方错位，气泡宽度随内容变化，工具记录使用两级折叠 */
@Composable
fun AgentMessageCard(
    message: AgentMessage,
    onOpenArticle: (String) -> Unit,
    modifier: Modifier = Modifier,
    failureText: String? = null
) {
    val user = message.role == AgentRole.USER
    BoxWithConstraints(modifier.fillMaxWidth()) {
        Surface(
            modifier = Modifier.align(if (user) Alignment.CenterEnd else Alignment.CenterStart)
                .widthIn(max = maxWidth * if (user) 0.78f else 0.9f).testTag("agent_message_${message.id}"),
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp,
                bottomStart = if (user) 24.dp else 6.dp, bottomEnd = if (user) 6.dp else 24.dp),
            color = if (user) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f) else MaterialTheme.colorScheme.surfaceContainer
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (message.steps.isNotEmpty()) AgentExecutionGroup(message.id, message.steps)
                if (message.text.isNotBlank()) AppMarkdownText(message.text, onOpenLink = onOpenArticle)
                if (message.status == AgentStatus.RUNNING) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AppLoadingIndicator()
                    Text(stringResource(R.string.agent_working), style = MaterialTheme.typography.bodySmall)
                }
                if (message.status == AgentStatus.CANCELLED) Text(stringResource(R.string.agent_cancelled),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (message.status == AgentStatus.FAILED) Text(failureText ?: stringResource(R.string.agent_failed),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                if (message.references.isNotEmpty()) AgentSources(message.references, onOpenArticle)
            }
        }
    }
}

@Composable
private fun AgentExecutionGroup(messageId: String, steps: List<AgentStep>) {
    var expanded by rememberSaveable(messageId) { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            Modifier.fillMaxWidth().testTag("agent_steps_toggle_${messageId}")
                .clickable(role = Role.Button) { expanded = !expanded }.padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(if (expanded) Icons.Default.KeyboardArrowDown else Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
            Text(stringResource(R.string.agent_steps_summary, steps.size), style = MaterialTheme.typography.labelLarge)
        }
        if (expanded) steps.forEach { step -> key(step.id) { AgentToolStep(step) } }
    }
}

@Composable
private fun AgentToolStep(step: AgentStep) {
    var expanded by rememberSaveable(step.id) { mutableStateOf(false) }
    val status = when (step.status) {
        AgentStatus.RUNNING -> R.string.agent_step_running
        AgentStatus.COMPLETED -> R.string.agent_step_completed
        AgentStatus.FAILED -> R.string.agent_step_failed
        AgentStatus.CANCELLED -> R.string.agent_step_cancelled
    }
    Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth().testTag("agent_step_${step.id}")
                .clickable(role = Role.Button) { expanded = !expanded }, verticalAlignment = Alignment.CenterVertically) {
                Icon(if (expanded) Icons.Default.KeyboardArrowDown else Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
                Column(Modifier.weight(1f)) {
                    Text(step.title, style = MaterialTheme.typography.labelLarge)
                    Text(stringResource(status), style = MaterialTheme.typography.labelSmall,
                        color = if (step.status == AgentStatus.FAILED) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
                }
            }
            if (expanded) Column(Modifier.testTag("agent_step_detail_${step.id}"), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(stringResource(R.string.agent_step_input), style = MaterialTheme.typography.labelSmall)
                SelectionContainer { Text(step.input, style = MaterialTheme.typography.bodySmall) }
                if (step.output.isNotBlank()) {
                    Text(stringResource(R.string.agent_step_output), style = MaterialTheme.typography.labelSmall)
                    if (step.kind == AgentStepKind.SKILL) AppMarkdownText(step.output)
                    else SelectionContainer { Text(step.output, style = MaterialTheme.typography.bodySmall) }
                }
            }
        }
    }
}

@Composable
private fun AgentSources(references: List<DigestReference>, onOpenArticle: (String) -> Unit) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    TextButton(onClick = { expanded = !expanded }) { Text(stringResource(R.string.agent_sources, references.size)) }
    if (expanded) references.forEach { reference ->
        TextButton(onClick = { onOpenArticle(reference.url) },
            enabled = reference.url.startsWith("https://") || reference.url.startsWith("http://"),
            modifier = Modifier.testTag("agent_source_${reference.id}")) {
            Text("${reference.sourceName} · ${reference.title}", style = MaterialTheme.typography.bodySmall)
        }
    }
}
