package com.aster.core.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import com.aster.core.model.NewsArticle
import com.aster.core.model.NewsSource
import com.aster.core.model.StockInfo
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class NewsArticleStockTest {
    @get:Rule val compose = createComposeRule()

    private fun show(stock: StockInfo?, dark: Boolean = false) {
        compose.setContent {
            MaterialTheme(colorScheme = if (dark) darkColorScheme() else lightColorScheme()) {
                NewsArticleCard(
                    NewsArticle("stocks", "00700", "腾讯控股", "https://example.com", stockInfo = stock),
                    NewsSource("stocks", "热门股票"), onClick = {}
                )
            }
        }
    }

    private fun assertTextColor(text: String, expected: Color) {
        val layouts = mutableListOf<TextLayoutResult>()
        compose.onNodeWithText(text, useUnmergedTree = true).assertIsDisplayed()
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        assertEquals(expected, layouts.single().layoutInput.style.color)
    }

    private fun checkQuote(change: Double, percent: Double, dark: Boolean, color: Color) {
        show(StockInfo("00700", 372.8, change, percent), dark)
        compose.onNodeWithText("00700", useUnmergedTree = true).assertIsDisplayed()
        assertTextColor("372.80", color)
        assertTextColor(formatStockNumber(change, signed = true), color)
        assertTextColor("${formatStockNumber(percent, signed = true)}%", color)
    }

    @Test fun risingQuoteIsRed() = checkQuote(3.2, 0.85, false, Color(0xFFB3261E))
    @Test fun fallingQuoteIsGreen() = checkQuote(-3.2, -0.85, false, Color(0xFF1B6B35))
    @Test fun darkRisingQuoteIsRed() = checkQuote(3.2, 0.85, true, Color(0xFFFF8A80))
    @Test fun darkFallingQuoteIsGreen() = checkQuote(-3.2, -0.85, true, Color(0xFF81C784))
    @Test fun unchangedQuoteIsNeutral() = checkQuote(0.0, 0.0, false, lightColorScheme().onSurfaceVariant)

    @Test fun ordinaryNewsDoesNotDisplayStockFields() {
        show(null)
        compose.onNodeWithText("腾讯控股", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("00700", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithText("372.80", useUnmergedTree = true).assertDoesNotExist()
    }
}
