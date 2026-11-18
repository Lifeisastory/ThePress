package com.aster.core.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.aster.core.model.NewsArticle
import com.aster.core.model.NewsSource

/** 仅用于编辑器预览的模拟内容，不进入正式应用文案资源。 */
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
