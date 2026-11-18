package com.aster.core.design_system.component.liquid_glass

import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.shapes.Capsule

/** 与首页 Tab 栏使用相同高度和玻璃效果的静态标题胶囊 */
@Composable
fun AppTitleBar(
    title: String,
    subtitle: String,
    backdrop: Backdrop,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    val surface = colors.surface.copy(alpha = if (Build.VERSION.SDK_INT < 31) 0.94f else 0.4f)
    Column(
        modifier.height(48.dp).drawBackdrop(
            backdrop = backdrop,
            shape = { Capsule() },
            effects = {
                vibrancy()
                blur(8.dp.toPx())
                lens(20.dp.toPx(), 24.dp.toPx())
            },
            onDrawSurface = { drawRect(surface) }
        ).padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(title, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (subtitle.isNotBlank()) {
            Text(
                subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = colors.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
