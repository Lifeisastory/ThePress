package com.aster.core.design_system.component.liquid_glass

import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.shapes.Capsule

@Immutable
data class AppTab(val id: String, val label: String)

/** 胶囊形玻璃 Tab 栏；长列表横向滚动，选中项变化时自动滚入可见区域 */
@Composable
fun AppTabRow(
    tabs: List<AppTab>,
    selectedIndex: Int,
    onTabSelected: (Int) -> Unit,
    backdrop: Backdrop,
    modifier: Modifier = Modifier
) {
    require(tabs.isNotEmpty() && selectedIndex in tabs.indices)
    val colors = MaterialTheme.colorScheme
    val surface = colors.surface.copy(
        alpha = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) 0.94f else 0.4f
    )
    val listState = rememberLazyListState()
    LaunchedEffect(selectedIndex, tabs.size) {
        val layout = listState.layoutInfo
        val item = layout.visibleItemsInfo.firstOrNull { it.index == selectedIndex }
        if (item == null || item.offset < layout.viewportStartOffset ||
            item.offset + item.size > layout.viewportEndOffset
        ) {
            listState.animateScrollToItem(selectedIndex)
        }
    }
    LazyRow(
        state = listState,
        modifier = modifier.height(48.dp)
            .drawBackdrop(
                backdrop = backdrop,
                shape = { Capsule() },
                effects = {
                    vibrancy()
                    blur(8.dp.toPx())
                    lens(20.dp.toPx(), 24.dp.toPx())
                },
                onDrawSurface = { drawRect(surface) }
            )
            .padding(4.dp)
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        itemsIndexed(tabs, key = { _, tab -> tab.id }) { index, tab ->
            val selected = index == selectedIndex
            Box(
                Modifier
                    .height(40.dp)
                    .widthIn(min = 56.dp, max = 144.dp)
                    .then(
                        if (selected) Modifier.drawBackdrop(
                            backdrop = backdrop,
                            shape = { Capsule() },
                            effects = {
                                blur(4.dp.toPx())
                                lens(8.dp.toPx(), 12.dp.toPx())
                            },
                            shadow = null,
                            onDrawSurface = {
                                drawRect(surface)
                                drawRect(colors.primary.copy(alpha = 0.12f))
                            }
                        ) else Modifier
                    )
                    .clip(Capsule())
                    .selectable(
                        selected = selected,
                        interactionSource = null,
                        indication = null,
                        role = Role.Tab,
                        onClick = { onTabSelected(index) }
                    )
                    .testTag("glass_tab_${tab.id}")
                    .padding(horizontal = 14.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = tab.label,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (selected) colors.primary else colors.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
