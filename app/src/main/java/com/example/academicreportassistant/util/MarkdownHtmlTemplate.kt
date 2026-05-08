package com.lzt.summaryofslides.util

object MarkdownHtmlTemplate {
    fun wrap(bodyHtml: String): String {
        val dollar = "${'$'}"
        return """
<!doctype html>
<html>
  <head>
    <meta charset="utf-8" />
    <meta name="viewport" content="width=device-width, initial-scale=1" />
    <link rel="stylesheet" href="https://cdn.jsdelivr.net/npm/katex@0.16.11/dist/katex.min.css" />
    <script defer src="https://cdn.jsdelivr.net/npm/katex@0.16.11/dist/katex.min.js"></script>
    <script defer src="https://cdn.jsdelivr.net/npm/katex@0.16.11/dist/contrib/auto-render.min.js"></script>
    <style>
      body { font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Arial, "Noto Sans", "PingFang SC", "Hiragino Sans GB", "Microsoft YaHei", sans-serif; padding: 16px; line-height: 1.65; }
      pre { overflow-x: auto; background: #f6f8fa; padding: 12px; border-radius: 8px; }
      code { font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, "Liberation Mono", "Courier New", monospace; }
      blockquote { border-left: 4px solid #ddd; padding-left: 12px; color: #555; }
      table { border-collapse: collapse; width: 100%; }
      th, td { border: 1px solid #ddd; padding: 6px; }
      img { max-width: 100%; height: auto; }
    </style>
  </head>
  <body>
    <div id="content">$bodyHtml</div>
    <script>
      document.addEventListener("DOMContentLoaded", function () {
        if (typeof renderMathInElement === "function") {
          renderMathInElement(document.getElementById("content"), {
            delimiters: [
              { left: "${dollar}${dollar}", right: "${dollar}${dollar}", display: true },
              { left: "${dollar}", right: "${dollar}", display: false }
            ],
            throwOnError: false
          });
        }
      });
    </script>
  </body>
</html>
""".trimIndent()
    }
}

