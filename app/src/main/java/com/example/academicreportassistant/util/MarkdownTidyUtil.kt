package com.lzt.summaryofslides.util

object MarkdownTidyUtil {
    fun tidy(raw: String): String {
        var md = raw.replace("\r\n", "\n").trim()
        md = md.replace("\\\\(", "\$")
            .replace("\\\\)", "\$")
            .replace("\\\\[", "\$\$")
            .replace("\\\\]", "\$\$")
        md = md.replace(Regex("\\$\\\\\\s*\\n")) { "${'$'}\n" }
        md = normalizeBlockMathDelimiters(md)
        md = fixInlineMathNewlines(md)
        md = fixLatexInMathSegments(md)
        md = convertTablesToLists(md)
        md = md.replace(Regex("\n{3,}"), "\n\n").trim()
        return md + "\n"
    }

    private fun normalizeBlockMathDelimiters(md: String): String {
        return md.replace(Regex("\\$\\$([^\\n]*?)\\$\\$")) { m ->
            val inner = m.groupValues.getOrNull(1).orEmpty().trim()
            if (inner.isBlank()) m.value
            else "$$\n$inner\n$$"
        }
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

    private fun fixLatexInMathSegments(md: String): String {
        val out = StringBuilder(md.length)
        var i = 0
        var inInline = false
        var inBlock = false
        var segmentStart = -1
        while (i < md.length) {
            val c = md[i]
            if (c == '$') {
                val isDouble = i + 1 < md.length && md[i + 1] == '$'
                if (isDouble) {
                    if (!inInline && !inBlock) {
                        inBlock = true
                        segmentStart = i + 2
                        out.append("$$")
                        i += 2
                        continue
                    }
                    if (inBlock) {
                        val rawSeg = md.substring(segmentStart.coerceAtLeast(0), i)
                        out.append(fixLatexSegment(rawSeg))
                        out.append("$$")
                        inBlock = false
                        segmentStart = -1
                        i += 2
                        continue
                    }
                    out.append("$$")
                    i += 2
                    continue
                }
                if (inBlock) {
                    i += 1
                    continue
                }
                if (!inBlock && !inInline) {
                    inInline = true
                    segmentStart = i + 1
                    out.append('$')
                    i += 1
                    continue
                }
                if (inInline) {
                    val rawSeg = md.substring(segmentStart.coerceAtLeast(0), i)
                    out.append(fixLatexSegment(rawSeg))
                    out.append('$')
                    inInline = false
                    segmentStart = -1
                    i += 1
                    continue
                }
                out.append('$')
                i += 1
                continue
            }
            if (!inInline && !inBlock) out.append(c)
            i += 1
        }
        if (inInline || inBlock) {
            val rawSeg = md.substring(segmentStart.coerceAtLeast(0))
            out.append(fixLatexSegment(rawSeg))
        }
        return out.toString()
    }

    private fun fixLatexSegment(raw: String): String {
        var s = raw
        s = s.replace('，', ',').replace('。', '.').replace('：', ':').replace('；', ';')
        s = s.replace('（', '(').replace('）', ')').replace('【', '[').replace('】', ']')
        s = s.replace('−', '-')
        s = s.replace(Regex("""\\hat\{([^}]+)\}\{([^}]+)\}""")) { m ->
            val a = m.groupValues[1]
            val idx = m.groupValues[2]
            "\\hat{$a}_{$idx}"
        }
        s = s.replace(Regex("""D\{\\text\{KL\}\}"""), "D_{\\\\text{KL}}")
        s = s.replace(Regex("""D\{\\mathrm\{KL\}\}"""), "D_{\\\\mathrm{KL}}")
        s = s.replace(Regex("""\\pi\{(\\theta[^}]*)\}""")) { m ->
            "\\\\pi_{" + m.groupValues[1] + "}"
        }
        s = s.replace(Regex("""\\hat\{A\}\{i,t\}"""), "\\\\hat{A}_{i,t}")
        s = s.replace(Regex("""\\hat\{A\}\{i, t\}"""), "\\\\hat{A}_{i,t}")
        s = s.replace(Regex("""\\text\{clip\}"""), "\\\\text{clip}")
        s = s.replace(Regex("""\\}\{([a-zA-Z]+\s*=\s*[^}]+)\}""")) { m ->
            "\\\\}_{" + m.groupValues[1] + "}"
        }
        s = fixMathbbE(s)
        return s
    }

    private fun fixMathbbE(latex: String): String {
        val needle = "\\mathbb{E}{"
        val out = StringBuilder(latex.length)
        var i = 0
        while (i < latex.length) {
            val idx = latex.indexOf(needle, i)
            if (idx < 0) {
                out.append(latex.substring(i))
                break
            }
            out.append(latex.substring(i, idx))
            var j = idx + needle.length
            var depth = 1
            while (j < latex.length && depth > 0) {
                val ch = latex[j]
                if (ch == '{' && (j == 0 || latex[j - 1] != '\\')) depth += 1
                if (ch == '}' && (j == 0 || latex[j - 1] != '\\')) depth -= 1
                j += 1
            }
            if (depth == 0) {
                val inner = latex.substring(idx + needle.length, j - 1)
                out.append("\\mathbb{E}_{").append(inner).append('}')
                i = j
            } else {
                out.append(latex.substring(idx))
                break
            }
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
