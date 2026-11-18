package com.aster.core.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.aster.core.model.DigestDetail
import com.aster.core.model.DigestSettings
import com.aster.core.model.NewsSource

/** 页面只提交配置，由 data 层管理偏好与凭据 */
@Composable
fun DigestSettingsContent(
    settings: DigestSettings,
    sources: List<NewsSource>,
    saving: Boolean,
    savedRevision: Int,
    onSave: (DigestSettings, String?) -> Unit
) {
    var selected by remember(settings, savedRevision) { mutableStateOf(settings.sourceIds) }
    var model by remember(settings, savedRevision) { mutableStateOf(settings.model) }
    var detail by remember(settings, savedRevision) { mutableStateOf(settings.detail) }
    // API Key 不进入保存实例状态，提交成功后清空输入框
    var apiKey by remember(savedRevision) { mutableStateOf("") }
    var expanded by rememberSaveable { mutableStateOf(false) }
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("DeepSeek", style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(apiKey, { apiKey = it },
            modifier = Modifier.fillMaxWidth().testTag("digest_api_key"), enabled = !saving, singleLine = true,
            label = { Text(stringResource(R.string.digest_api_key)) },
            supportingText = { Text(stringResource(if (settings.hasApiKey) R.string.digest_key_saved else R.string.digest_key_needed)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { focus.clearFocus(); keyboard?.hide() }),
            visualTransformation = PasswordVisualTransformation())
        if (settings.hasApiKey) TextButton(
            onClick = { onSave(settings, "") }, enabled = !saving,
            modifier = Modifier.testTag("digest_remove_key")
        ) { Text(stringResource(R.string.digest_remove_key)) }
        Text(stringResource(R.string.digest_model), style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("deepseek-flash", "deepseek-v4-pro").forEach { name ->
                FilterChip(model == name, { model = name }, enabled = !saving, label = { Text(name) })
            }
        }
        Text(stringResource(R.string.digest_detail), style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DigestDetail.entries.forEach { value ->
                FilterChip(detail == value, { detail = value }, enabled = !saving,
                    label = { Text(stringResource(when (value) {
                        DigestDetail.BRIEF -> R.string.digest_brief
                        DigestDetail.STANDARD -> R.string.digest_standard
                        DigestDetail.DETAILED -> R.string.digest_detailed
                    })) })
            }
        }
        TextButton(onClick = { expanded = !expanded }, modifier = Modifier.testTag("digest_source_picker")) {
            Text(stringResource(R.string.digest_selected_sources, selected.size))
        }
        if (expanded) {
            Row {
                TextButton(onClick = { selected = sources.map { it.id }.toSet() }, enabled = !saving) {
                    Text(stringResource(R.string.digest_select_all))
                }
                TextButton(onClick = { selected = emptySet() }, enabled = !saving) {
                    Text(stringResource(R.string.digest_select_none))
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                sources.forEach { source ->
                    FilterChip(source.id in selected, {
                        selected = if (source.id in selected) selected - source.id else selected + source.id
                    }, enabled = !saving, label = { Text(source.name) }, modifier = Modifier.testTag("digest_source_${source.id}"))
                }
            }
        }
        Text(stringResource(R.string.digest_settings_hint), style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Button(onClick = { onSave(settings.copy(sourceIds = selected, model = model, detail = detail), apiKey.takeIf { it.isNotBlank() }) },
            enabled = !saving, modifier = Modifier.testTag("digest_save_settings")) {
            Text(stringResource(if (saving) R.string.digest_saving else R.string.digest_save))
        }
    }
}
