package com.example.academicreportassistant.util

import org.commonmark.parser.Parser
import org.commonmark.renderer.html.HtmlRenderer

object MarkdownHtmlUtil {
    private val parser = Parser.builder().build()
    private val renderer = HtmlRenderer.builder().escapeHtml(true).build()

    fun toHtml(markdown: String): String {
        return renderer.render(parser.parse(markdown))
    }
}

