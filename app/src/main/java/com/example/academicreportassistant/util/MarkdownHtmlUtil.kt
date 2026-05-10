package com.lzt.summaryofslides.util

import org.commonmark.Extension
import org.commonmark.ext.autolink.AutolinkExtension
import org.commonmark.ext.gfm.strikethrough.StrikethroughExtension
import org.commonmark.ext.gfm.tables.TablesExtension
import org.commonmark.parser.Parser
import org.commonmark.renderer.html.HtmlRenderer

object MarkdownHtmlUtil {
    private val extensions: List<Extension> =
        listOf(
            AutolinkExtension.create(),
            StrikethroughExtension.create(),
            TablesExtension.create(),
        )
    private val parser = Parser.builder().extensions(extensions).build()
    private val renderer = HtmlRenderer.builder().escapeHtml(true).build()

    fun toHtml(markdown: String): String {
        return renderer.render(parser.parse(markdown))
    }
}
