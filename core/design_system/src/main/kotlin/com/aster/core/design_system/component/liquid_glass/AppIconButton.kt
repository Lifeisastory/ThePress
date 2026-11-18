package com.aster.core.design_system.component.liquid_glass

import android.os.Build
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy

/** 通用圆形玻璃按钮，backdrop 不应包含按钮自身 */
@Composable
fun AppIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    backdrop: Backdrop,
    modifier: Modifier = Modifier
) {
    val surface = MaterialTheme.colorScheme.surface.copy(
        alpha = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) 0.94f else 0.4f
    )
    IconButton(
        onClick = onClick,
        modifier = modifier.size(48.dp).drawBackdrop(
            backdrop = backdrop,
            shape = { CircleShape },
            effects = {
                vibrancy()
                blur(8.dp.toPx())
                lens(16.dp.toPx(), 20.dp.toPx())
            },
            onDrawSurface = { drawRect(surface) }
        )
    ) {
        Icon(icon, contentDescription, tint = MaterialTheme.colorScheme.onSurface)
    }
}
