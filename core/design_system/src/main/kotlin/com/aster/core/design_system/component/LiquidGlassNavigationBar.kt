package com.aster.core.design_system.component

import android.os.Build
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy

@Immutable
data class LiquidGlassNavigationItem(
    val label: String,
    val icon: ImageVector
)

/**
 * 悬浮玻璃导航栏。调用方持有选中状态，并负责外边距和系统栏 Insets。
 *
 * 将页面内容通过 Modifier.layerBackdrop 记录到同一个 backdrop；导航栏应作为内容的
 * 兄弟节点绘制在其上方，避免把导航栏自身录入背景。参见 LiquidGlassNavigationDemo。
 */
@Composable
fun LiquidGlassNavigationBar(
    items: List<LiquidGlassNavigationItem>,
    selectedIndex: Int,
    onItemSelected: (Int) -> Unit,
    backdrop: Backdrop,
    modifier: Modifier = Modifier
) {
    require(items.isNotEmpty()) { "Navigation items must not be empty" }
    require(selectedIndex in items.indices) { "Selected index must refer to a navigation item" }

    val colors = MaterialTheme.colorScheme
    // Android 12 以下没有背景模糊，提高底色不透明度以保证文字可读性。
    val surfaceColor = colors.surface.copy(
        alpha = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) 0.58f else 0.94f
    )

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .drawBackdrop(
                backdrop = backdrop,
                shape = { CircleShape },
                effects = {
                    vibrancy()
                    blur(8.dp.toPx())
                    lens(20.dp.toPx(), 28.dp.toPx())
                },
                onDrawSurface = { drawRect(surfaceColor) }
            )
            .padding(6.dp)
    ) {
        val itemWidth = maxWidth / items.size
        val indicatorOffset by animateDpAsState(
            targetValue = itemWidth * selectedIndex,
            animationSpec = spring(dampingRatio = 0.8f, stiffness = 400f),
            label = "Navigation indicator"
        )

        Box(Modifier.matchParentSize()) {
            Box(
                Modifier
                    .offset { IntOffset(indicatorOffset.roundToPx(), 0) }
                    .width(itemWidth)
                    .fillMaxHeight()
                    .background(colors.primary.copy(alpha = 0.14f), CircleShape)
            )
        }

        Row(Modifier.fillMaxWidth().selectableGroup()) {
            items.forEachIndexed { index, item ->
                val selected = selectedIndex == index
                val contentColor by animateColorAsState(
                    targetValue = if (selected) colors.primary else colors.onSurfaceVariant,
                    label = "Navigation item color"
                )

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 60.dp)
                        .clip(CircleShape)
                        .selectable(
                            selected = selected,
                            role = Role.Tab,
                            onClick = { onItemSelected(index) }
                        )
                        .padding(horizontal = 4.dp, vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically)
                ) {
                    Icon(
                        imageVector = item.icon,
                        contentDescription = null,
                        modifier = Modifier.size(22.dp),
                        tint = contentColor
                    )
                    Text(
                        text = item.label,
                        color = contentColor,
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}
