package com.aster.core.design_system.component.liquid_glass

import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.aster.core.design_system.R
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.shapes.Capsule

/** 胶囊形液态玻璃刷新指示器；backdrop 只记录后方列表，不应包含指示器自身。 */
@Composable
fun AppRefreshIndicator(
    atTop: Boolean,
    busy: Boolean,
    hasMore: Boolean,
    progress: Float,
    backdrop: Backdrop,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    val surface = colors.surface.copy(
        alpha = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) 0.94f else 0.4f
    )
    val message = when {
        busy -> if (atTop) R.string.app_refresh_refreshing else R.string.app_refresh_loading_more
        !atTop && !hasMore -> R.string.app_refresh_no_more
        progress >= 1f -> if (atTop) R.string.app_refresh_release else R.string.app_refresh_release_load
        else -> if (atTop) R.string.app_refresh_pull_down else R.string.app_refresh_pull_up
    }

    Row(
        modifier = modifier
            .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite }
            .heightIn(min = 40.dp)
            .drawBackdrop(
                backdrop = backdrop,
                shape = { Capsule() },
                effects = {
                    vibrancy()
                    blur(8.dp.toPx())
                    lens(16.dp.toPx(), 20.dp.toPx())
                },
                onDrawSurface = { drawRect(surface) }
            )
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (busy) {
            CircularProgressIndicator(Modifier.size(18.dp), color = colors.primary, strokeWidth = 2.dp)
        } else if (atTop || hasMore) {
            Icon(
                imageVector = if (atTop) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowUp,
                contentDescription = null,
                tint = colors.onSurfaceVariant,
                modifier = Modifier.size(20.dp).graphicsLayer {
                    rotationZ = if (progress >= 1f) 180f else 0f
                }
            )
        }
        Text(stringResource(message), color = colors.onSurface, style = MaterialTheme.typography.labelLarge)
    }
}
