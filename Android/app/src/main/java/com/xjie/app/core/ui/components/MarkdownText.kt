package com.xjie.app.core.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.text.HtmlCompat

/**
 * 轻量 Markdown → HTML 渲染。
 *
 * 当前实现把简单 Markdown 语法转成 HTML 后用 [HtmlCompat] 渲染。
 * 后续若需要表格 / 代码块，可替换为 Markwon。
 */
@Composable
fun MarkdownText(
    markdown: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurface,
) {
    val html = remember(markdown) { naiveMarkdownToHtml(markdown) }
    Box(modifier) {
        AndroidView(
            factory = { ctx ->
                android.widget.TextView(ctx).apply {
                    setTextColor(color.toArgbCompat())
                }
            },
            update = { tv ->
                tv.text = HtmlCompat.fromHtml(html, HtmlCompat.FROM_HTML_MODE_COMPACT)
            },
        )
    }
}

private fun naiveMarkdownToHtml(src: String): String {
    val lines = src.lines()
    val out = StringBuilder()
    for (line in lines) {
        val l = line.trimEnd()
        when {
            l.startsWith("### ") -> out.append("<h3>").append(escape(l.removePrefix("### "))).append("</h3>")
            l.startsWith("## ") -> out.append("<h2>").append(escape(l.removePrefix("## "))).append("</h2>")
            l.startsWith("# ") -> out.append("<h1>").append(escape(l.removePrefix("# "))).append("</h1>")
            l.startsWith("- ") || l.startsWith("* ") ->
                out.append("• ").append(escape(l.drop(2))).append("<br/>")
            l.isBlank() -> out.append("<br/>")
            else -> out.append(escape(l)).append("<br/>")
        }
    }
    return out.toString()
        .replace(Regex("\\*\\*(.+?)\\*\\*"), "<b>$1</b>")
        .replace(Regex("\\*(.+?)\\*"), "<i>$1</i>")
        .replace(Regex("`([^`]+)`"), "<code>$1</code>")
}

private fun escape(s: String): String =
    s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

private fun Color.toArgbCompat(): Int {
    val a = (alpha * 255).toInt() and 0xFF
    val r = (red * 255).toInt() and 0xFF
    val g = (green * 255).toInt() and 0xFF
    val b = (blue * 255).toInt() and 0xFF
    return (a shl 24) or (r shl 16) or (g shl 8) or b
}
