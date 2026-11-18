package com.aster.core.design_system.component.liquid_glass

import android.os.Build
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.compose.rememberAsyncImagePainter
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy

/** 玻璃底板只采样后方内容，Logo 内缩以露出折射边缘；全部/未知来源使用占位图 */
@Composable
fun AppIcon(
    sourceId: String?,
    contentDescription: String?,
    backdrop: Backdrop,
    modifier: Modifier = Modifier
) {
    val fallback = rememberAsyncImagePainter("file:///android_asset/source_icons/default.png")
    val platformId = sourceId?.substringBefore('-') ?: "default"
    val surface = MaterialTheme.colorScheme.surface.copy(
        alpha = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) 0.94f else 0.4f
    )
    Box(
        modifier = modifier.size(48.dp).semantics {
            if (contentDescription != null) this.contentDescription = contentDescription
        }.drawBackdrop(
            backdrop = backdrop,
            shape = { CircleShape },
            effects = {
                vibrancy()
                blur(8.dp.toPx())
                lens(16.dp.toPx(), 20.dp.toPx())
            },
            onDrawSurface = { drawRect(surface) }
        ),
        contentAlignment = Alignment.Center
    ) {
        // 由 Compose 驱动切换，命中图片内存缓存时也保留淡入淡出；玻璃底板不参与动画
        Crossfade(
            targetState = platformId,
            modifier = Modifier.size(32.dp).clip(CircleShape),
            animationSpec = tween(200),
            label = "Source icon crossfade"
        ) { id ->
            AsyncImage(
                model = "file:///android_asset/source_icons/$id.png",
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                placeholder = fallback,
                error = fallback
            )
        }
    }
}
