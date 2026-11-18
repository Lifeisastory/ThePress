package com.aster.feature.saved.impl

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aster.core.data.repository.DigestSettingsRepository
import com.aster.core.data.repository.NewsDigestRepository
import com.aster.core.design_system.component.AppLoadingIndicator
import com.aster.core.model.DigestFailure
import com.aster.core.ui.NewsDigestCard
import java.text.DateFormat
import java.util.Date

@Composable
fun DigestPage(
    contentPadding: PaddingValues,
    repository: NewsDigestRepository,
    settingsRepository: DigestSettingsRepository,
    onOpenArticle: (String) -> Unit,
    onOpenSettings: () -> Unit
) {
    val factory = remember(repository, settingsRepository) { DigestViewModel.factory(repository, settingsRepository) }
    val viewModel: DigestViewModel = viewModel(factory = factory)
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LazyColumn(Modifier.fillMaxSize().testTag("saved_page"), contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(com.aster.feature.saved.api.R.string.feature_saved_title),
                    style = MaterialTheme.typography.headlineLarge, modifier = Modifier.testTag("page_title"))
                Text(stringResource(R.string.digest_subtitle), color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(onClick = onOpenSettings, modifier = Modifier.testTag("digest_open_settings")) {
                    Text(stringResource(R.string.digest_settings))
                }
                val configured = state.settings?.let { it.hasApiKey && it.sourceIds.isNotEmpty() } == true
                if (!configured && !state.settingsFailed && state.settings != null) Text(stringResource(R.string.digest_setup_hint))
                if (state.generating) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AppLoadingIndicator()
                        Text(stringResource(R.string.digest_generating))
                    }
                    TextButton(onClick = viewModel::cancel, modifier = Modifier.testTag("digest_cancel")) {
                        Text(stringResource(R.string.digest_cancel))
                    }
                } else {
                    Button(onClick = viewModel::generate,
                        enabled = configured && !state.settingsFailed && !state.readFailed && !state.loading,
                        modifier = Modifier.testTag("digest_generate")) {
                        Text(stringResource(if (state.digest == null) R.string.digest_generate else R.string.digest_regenerate))
                    }
                }
                if (state.readFailed || state.settingsFailed) Text(stringResource(R.string.digest_storage_error), color = MaterialTheme.colorScheme.error)
                state.failure?.let { Text(stringResource(it.message()), color = MaterialTheme.colorScheme.error) }
                if (state.loading) AppLoadingIndicator()
                if (!state.loading && state.digest == null) Text(stringResource(R.string.digest_empty))
                state.digest?.let { digest ->
                    val originalCount = digest.items.count { it.isOriginal }
                    if (originalCount > 0) Text(stringResource(R.string.digest_partial, originalCount),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                    Text(stringResource(R.string.digest_metadata, DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
                        .format(Date(digest.generatedAt)), digest.inputCount, digest.items.size, digest.model),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (state.settings?.let { it.sourceIds != digest.sources.map { source -> source.id }.toSet() ||
                                it.model != digest.model || it.detail != digest.detail } == true) {
                        Text(stringResource(R.string.digest_settings_changed), style = MaterialTheme.typography.bodySmall)
                    }
                    digest.sources.filter { it.refreshFailed || it.articleCount == 0 }.forEach { source ->
                        Text(stringResource(if (source.articleCount == 0) R.string.digest_source_empty else R.string.digest_source_stale, source.name),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        items(state.digest?.items.orEmpty()) { item ->
            NewsDigestCard(item, onOpenArticle, Modifier.padding(horizontal = 8.dp))
        }
    }
}

private fun DigestFailure.message() = when (this) {
    DigestFailure.NOT_CONFIGURED, DigestFailure.NO_SOURCES -> R.string.digest_setup_hint
    DigestFailure.NO_NEWS -> R.string.digest_no_news
    DigestFailure.INPUT_TOO_LARGE -> R.string.digest_too_large
    DigestFailure.BUSY -> R.string.digest_busy
    DigestFailure.CONNECTION -> R.string.digest_connection_error
    DigestFailure.TIMEOUT -> R.string.digest_timeout
    DigestFailure.AUTHENTICATION -> R.string.digest_auth_error
    DigestFailure.BALANCE -> R.string.digest_balance_error
    DigestFailure.RATE_LIMIT -> R.string.digest_rate_limit
    DigestFailure.SERVER -> R.string.digest_server_error
    DigestFailure.INVALID_RESPONSE -> R.string.digest_invalid_response
    DigestFailure.STORAGE -> R.string.digest_storage_error
}
