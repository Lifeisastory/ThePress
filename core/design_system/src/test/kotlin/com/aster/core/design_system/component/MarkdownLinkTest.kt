package com.aster.core.design_system.component

import org.junit.Assert.*
import org.junit.Test

class MarkdownLinkTest {
    @Test fun onlyAbsoluteWebLinksCanEnterTheAppNavigator() {
        assertTrue(isMarkdownWebLink("https://example.com/article?q=1"))
        assertTrue(isMarkdownWebLink("HTTP://example.com/"))
        listOf("javascript:alert(1)", "file:///data/private", "intent://app", "relative/path", "https://", "https://exa mple.com").forEach {
            assertFalse(it, isMarkdownWebLink(it))
        }
    }
}
