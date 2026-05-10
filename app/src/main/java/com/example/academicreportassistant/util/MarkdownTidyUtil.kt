package com.lzt.summaryofslides.util

object MarkdownTidyUtil {
    fun tidy(raw: String): String {
        var md = raw.replace("\r\n", "\n").trim()
        md = md.replace("\\\\(", "\$")
            .replace("\\\\)", "\$")
            .replace("\\\\[", "\$\$")
            .replace("\\\\]", "\$\$")
        md = md.replace(Regex("\\$\\\\\\s*\\n")) { "${'$'}\n" }
        md = fixInlineMathNewlines(md)
        md = convertTablesToLists(md)
        md = md.replace(Regex("\n{3,}"), "\n\n").trim()
        return md + "\n"
    }

    private fun fixInlineMathNewlines(md: String): String {
        val out = StringBuilder(md.length)
        var i = 0
        var inInline = false
        var inBlock = false
        while (i < md.length) {
            val c = md[i]
            if (c == '$') {
                val isDouble = i + 1 < md.length && md[i + 1] == '$'
                if (isDouble) {
                    inBlock = !inBlock
                    out.append("$$")
                    i += 2
                    continue
                }
                if (!inBlock) inInline = !inInline
                out.append('$')
                i += 1
                continue
            }
            if (c == '\n' && inInline && !inBlock) {
                out.append(' ')
                i += 1
                continue
            }
            out.append(c)
            i += 1
        }
        return out.toString()
    }

    private fun convertTablesToLists(md: String): String {
        val lines = md.split('\n')
        val out = StringBuilder()
        var i = 0
        while (i < lines.size) {
            val line = lines[i]
            if (i + 1 < lines.size && isTableRow(line) && isTableSeparator(lines[i + 1])) {
                val headers = splitTableRow(line)
                i += 2
                while (i < lines.size && isTableRow(lines[i]) && lines[i].isNotBlank()) {
                    val cells = splitTableRow(lines[i])
                    val parts =
                        headers.mapIndexed { idx, h ->
                            val c = cells.getOrNull(idx).orEmpty()
                            "${h.trim()}：${c.trim()}"
                        }.filter { it.isNotBlank() }
                    if (parts.isNotEmpty()) {
                        out.append("- ").append(parts.joinToString("；")).append('\n')
                    }
                    i += 1
                }
                out.append('\n')
                continue
            }
            out.append(line).append('\n')
            i += 1
        }
        return out.toString()
    }

    private fun isTableRow(line: String): Boolean {
        val t = line.trim()
        if (t.length < 3) return false
        val pipeCount = t.count { it == '|' }
        if (pipeCount < 2) return false
        val looksLikeRow = t.startsWith('|') || t.endsWith('|')
        return looksLikeRow
    }

    private fun isTableSeparator(line: String): Boolean {
        val t = line.trim()
        if (!t.contains('-')) return false
        return Regex("""^[\s\|\-:]+$""").matches(t)
    }

    private fun splitTableRow(line: String): List<String> {
        val t = line.trim().trim('|')
        return t.split('|').map { it.trim() }
    }
}
