package com.lzt.summaryofslides.util

object MarkdownTidyUtil {
    fun tidy(raw: String): String {
        var md = raw.replace("\r\n", "\n").trim()
        md = stripMathCodeFences(md)
        md = ensureDoubleDollarStandalone(md)
        md = md.replace("\\\\(", "\$")
            .replace("\\\\)", "\$")
            .replace("\\\\[", "\$\$")
            .replace("\\\\]", "\$\$")
        md = md.replace(Regex("\\$\\\\\\s*\\n")) { "${'$'}\n" }
        md = normalizeBlockMathDelimiters(md)
        md = fixInlineMathNewlines(md)
        md = fixLatexInMathSegments(md)
        md = removeMarkdownLineEndBackslashes(md)
        md = convertTablesToLists(md)
        md = md.replace(Regex("\n{3,}"), "\n\n").trim()
        return md + "\n"
    }

    private fun stripMathCodeFences(md: String): String {
        return md.replace(Regex("```(?:latex|math)\\s*([\\s\\S]*?)\\s*```", RegexOption.IGNORE_CASE)) { m ->
            m.groupValues.getOrNull(1).orEmpty().trim()
        }
    }

    private fun ensureDoubleDollarStandalone(md: String): String {
        val lines = md.replace("\r\n", "\n").split('\n')
        val out = StringBuilder(md.length + 32)
        var inCodeFence = false
        for (line0 in lines) {
            val line = line0
            val trimmed = line.trimStart()
            if (trimmed.startsWith("```")) {
                inCodeFence = !inCodeFence
                out.append(line).append('\n')
                continue
            }
            if (!inCodeFence && line.contains("$$") && line.trim() != "$$") {
                out.append(line.replace("$$", "\n$$\n")).append('\n')
                continue
            }
            out.append(line).append('\n')
        }
        return out.toString().trimEnd()
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
        return runCatching {
            var s = raw
            s = s.replace('，', ',').replace('。', '.').replace('：', ':').replace('；', ';')
            s = s.replace('（', '(').replace('）', ')').replace('【', '[').replace('】', ']')
            s = s.replace('−', '-')
            s = s.replace(Regex("""\\\s*abla\b"""), "\\\\nabla")
            s = s.replace(Regex("""\babla\b"""), "\\\\nabla")
            s = s.replace(Regex("""\beq\s+0\b"""), "\\\\neq 0")
            s = s.replace("\\arg\\min{", "\\arg\\min_{")
            s = s.replace(Regex("""\\(bar|hat|tilde|vec|overline)\{([a-zA-Z])\}([a-zA-Z0-9])\b""")) { m ->
                val cmd = m.groupValues[1]
                val sym = m.groupValues[2]
                val sub = m.groupValues[3]
                "\\\\$cmd{$sym}_{$sub}"
            }
            s = s.replace(Regex("""([a-zA-Z])\{([0-9a-zA-Z,+\-]{1,10})\}""")) { m ->
                val v = m.groupValues[1]
                val sub = m.groupValues[2]
                "${v}_{$sub}"
            }
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
            s = s.replace(Regex("""\Q\}{\E([a-zA-Z]+\s*=\s*[^}]+)\}""")) { m ->
                "\\\\}_{" + m.groupValues[1] + "}"
            }
            s = fixMathbbE(s)
            val leftCount = Regex("""\\left\b""").findAll(s).count()
            val rightCount = Regex("""\\right\b""").findAll(s).count()
            if (leftCount != rightCount) {
                s = s.replace("\\left", "").replace("\\right", "")
            }
            s
        }.getOrDefault(raw)
    }

    private fun removeMarkdownLineEndBackslashes(md: String): String {
        val lines = md.replace("\r\n", "\n").split('\n')
        val out = StringBuilder(md.length)
        var inCodeFence = false
        var inMathBlock = false
        for (line0 in lines) {
            val line = line0
            val trimmed = line.trimStart()
            if (trimmed.startsWith("```")) {
                inCodeFence = !inCodeFence
                out.append(line).append('\n')
                continue
            }
            if (!inCodeFence) {
                val t = line.trim()
                if (t.startsWith("$$")) {
                    inMathBlock = !inMathBlock
                    out.append(line).append('\n')
                    continue
                }
            }
            if (!inCodeFence && !inMathBlock) {
                val t = line.trim()
                if (t == "\\") {
                    out.append('\n')
                    continue
                }
                if (line.endsWith("\\") && !line.endsWith("\\\\")) {
                    out.append(line.dropLast(1)).append('\n')
                    continue
                }
            }
            out.append(line).append('\n')
        }
        return out.toString().trimEnd()
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
