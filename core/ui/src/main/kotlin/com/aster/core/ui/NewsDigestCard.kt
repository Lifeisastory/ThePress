package com.aster.core.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.aster.core.model.DigestItem

@Composable
fun NewsDigestCard(item: DigestItem, onOpenArticle: (String) -> Unit, modifier: Modifier = Modifier) {
    Card(modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (item.isOriginal) Text(stringResource(R.string.digest_original),
                style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            Text(item.title, style = MaterialTheme.typography.titleMedium)
            if (item.body.isNotBlank()) Text(item.body, style = MaterialTheme.typography.bodyMedium)
            Text(stringResource(R.string.digest_sources), style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            item.references.forEach { reference ->
                TextButton(onClick = { onOpenArticle(reference.url) },
                    modifier = Modifier.testTag("digest_link_${reference.id}")) {
                    Text("${reference.sourceName} · ${reference.title}")
                }
            }
        }
    }
}
