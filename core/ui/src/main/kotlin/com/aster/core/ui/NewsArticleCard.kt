package com.aster.core.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.aster.core.model.NewsArticle
import com.aster.core.model.NewsSource

/** 共享资讯卡片；数据获取和打开原文由调用方负责 */
@Composable
fun NewsArticleCard(
    article: NewsArticle,
    source: NewsSource,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val summary = article.summary
    val imageUrl = article.imageUrl
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Row(
            modifier = Modifier.padding(18.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    source.name,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    article.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
                if (!summary.isNullOrBlank()) {
                    Text(
                        summary,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            if (!imageUrl.isNullOrBlank()) {
                val placeholder = ColorPainter(MaterialTheme.colorScheme.surfaceContainerHighest)
                AsyncImage(
                    model = imageUrl,
                    contentDescription = null,
                    modifier = Modifier.size(104.dp, 88.dp).clip(RoundedCornerShape(14.dp)),
                    contentScale = ContentScale.Crop,
                    placeholder = placeholder,
                    error = placeholder
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun NewsArticleCardPreview() {
    MaterialTheme {
        NewsArticleCard(
            article = NewsArticle(
                sourceId = "sspai",
                id = "preview",
                title = "今天，读点好内容。",
                url = "https://sspai.com/",
                summary = "从不同的视角发现值得关注的内容。"
            ),
            source = NewsSource("sspai", "少数派 · 热门文章"),
            onClick = {}
        )
    }
}
