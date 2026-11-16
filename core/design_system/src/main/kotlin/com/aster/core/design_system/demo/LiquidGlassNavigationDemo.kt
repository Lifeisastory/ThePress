package com.aster.core.design_system.demo

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.aster.core.design_system.R
import com.aster.core.design_system.component.LiquidGlassNavigationBar
import com.aster.core.design_system.component.LiquidGlassNavigationItem
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop

/** 可直接运行的示例：滚动内容从导航栏下方经过，供玻璃实时取样。 */
@Composable
fun LiquidGlassNavigationDemo(modifier: Modifier = Modifier) {
    var selectedIndex by rememberSaveable { mutableIntStateOf(0) }
    val items = listOf(
        LiquidGlassNavigationItem(stringResource(R.string.glass_demo_home), Icons.Default.Home),
        LiquidGlassNavigationItem(stringResource(R.string.glass_demo_discover), Icons.Default.Search),
        LiquidGlassNavigationItem(stringResource(R.string.glass_demo_saved), Icons.Default.Favorite)
    )
    val titles = stringArrayResource(R.array.glass_demo_titles)
    val headlines = stringArrayResource(
        when (selectedIndex) {
            1 -> R.array.glass_demo_discover_articles
            2 -> R.array.glass_demo_saved_articles
            else -> R.array.glass_demo_home_articles
        }
    )
    val accent = when (selectedIndex) {
        1 -> Color(0xFF477CA8)
        2 -> Color(0xFF9C6681)
        else -> Color(0xFF537D69)
    }
    val backgroundColor = MaterialTheme.colorScheme.background
    val backdrop = rememberLayerBackdrop {
        drawRect(backgroundColor)
        drawContent()
    }
    val systemPadding = WindowInsets.safeDrawing.asPaddingValues()
    val density = LocalDensity.current
    var navigationBarHeight by remember { mutableStateOf(104.dp) }

    Box(modifier.fillMaxSize().background(backgroundColor)) {
        // 只记录页面；玻璃栏必须在这个 layerBackdrop 节点之外。
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .layerBackdrop(backdrop)
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
                .testTag("glass_demo_feed"),
            contentPadding = PaddingValues(
                start = 24.dp,
                end = 24.dp,
                top = systemPadding.calculateTopPadding() + 24.dp,
                bottom = navigationBarHeight + 16.dp
            ),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "THE PRESS",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = titles[selectedIndex],
                        modifier = Modifier.testTag("glass_demo_title"),
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = stringResource(R.string.glass_demo_description),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(4.dp))
                }
            }
            itemsIndexed(headlines) { index, headline ->
                DemoArticleCard(
                    headline = headline,
                    index = index,
                    category = items[selectedIndex].label,
                    accent = accent
                )
            }
        }

        LiquidGlassNavigationBar(
            items = items,
            selectedIndex = selectedIndex,
            onItemSelected = { selectedIndex = it },
            backdrop = backdrop,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .onSizeChanged { navigationBarHeight = with(density) { it.height.toDp() } }
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 16.dp)
                .widthIn(max = 420.dp)
        )
    }
}

@Composable
private fun DemoArticleCard(
    headline: String,
    index: Int,
    category: String,
    accent: Color
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(176.dp)
                .background(
                    Brush.linearGradient(
                        listOf(accent, accent.copy(alpha = 0.45f), Color(0xFFEBDCC8))
                    )
                )
        ) {
            Box(
                Modifier
                    .align(if (index % 2 == 0) Alignment.TopEnd else Alignment.BottomStart)
                    .padding(20.dp)
                    .size(120.dp)
                    .background(Color.White.copy(alpha = 0.23f), CircleShape)
            )
            Text(
                text = "0${index + 1}",
                modifier = Modifier.align(Alignment.BottomStart).padding(20.dp),
                style = MaterialTheme.typography.displayMedium,
                color = Color.White,
                fontWeight = FontWeight.Light
            )
        }
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = category,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = stringResource(R.string.glass_demo_reading_time),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = headline,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
