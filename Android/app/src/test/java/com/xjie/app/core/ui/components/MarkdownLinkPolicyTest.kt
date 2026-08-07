package com.xjie.app.core.ui.components

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownLinkPolicyTest {
    @Test
    fun httpLinksRenderAsAnchorsWhileUnsafeSchemesRemainPlainText() {
        val safe = markdownToHtml("查看[医学来源](https://example.test/paper?id=7&lang=zh)。")
        val unsafe = markdownToHtml("不要打开[脚本](javascript:alert(1))。")

        assertTrue(safe.contains("<a href=\"https://example.test/paper?id=7&amp;lang=zh\">医学来源</a>"))
        assertFalse(unsafe.contains("<a href="))
        assertTrue(unsafe.contains("[脚本](javascript:alert(1))"))
    }

    @Test
    fun linkPolicyAllowsOnlyHttpAndHttps() {
        assertTrue(isSafeMarkdownLink("https://example.test"))
        assertTrue(isSafeMarkdownLink("HTTP://example.test"))
        assertFalse(isSafeMarkdownLink("javascript:alert(1)"))
        assertFalse(isSafeMarkdownLink("file:///tmp/private"))
        assertFalse(isSafeMarkdownLink("intent://unsafe"))
    }
}
