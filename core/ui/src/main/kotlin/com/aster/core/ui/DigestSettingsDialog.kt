package com.aster.core.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.aster.core.design_system.component.AppLoadingIndicator
import com.aster.core.design_system.component.liquid_glass.AppDialog
import com.aster.core.design_system.modifier.blurFadeEdges
import com.aster.core.model.DigestSettings
import com.aster.core.model.NewsSource
import com.kyant.backdrop.Backdrop

@Composable
fun DigestSettingsDialog(
    settings: DigestSettings?, sources: List<NewsSource>, saving: Boolean, readFailed: Boolean,
    savedRevision: Int, message: String?, backdrop: Backdrop,
    onSave: (DigestSettings, String?) -> Unit, onDismiss: () -> Unit
) {
    val scroll = rememberLazyListState()
    AppDialog(stringResource(R.string.agent_settings_title), backdrop, onDismiss, dismissEnabled = !saving) { dismiss ->
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(stringResource(R.string.agent_settings_title), style = MaterialTheme.typography.titleLarge)
            TextButton(onClick = dismiss, enabled = !saving, modifier = Modifier.testTag("digest_close_settings")) {
                Text(stringResource(R.string.agent_close))
            }
        }
        LazyColumn(Modifier.weight(1f, fill = false).testTag("digest_settings_scroll")
            .blurFadeEdges(Orientation.Vertical, edgeSize = 24.dp,
                fadeStart = scroll.canScrollBackward, fadeEnd = scroll.canScrollForward),
            state = scroll,
            contentPadding = PaddingValues(vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                when {
                    readFailed -> Text(stringResource(R.string.agent_settings_read_failed), color = MaterialTheme.colorScheme.error)
                    settings == null -> AppLoadingIndicator()
                    else -> DigestSettingsContent(settings, sources, saving, savedRevision, onSave)
                }
            }
            if (message != null) item { Text(message, style = MaterialTheme.typography.bodySmall) }
        }
    }
}
