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
    <link rel="stylesheet" href="https://cdn.jsdelivr.net/npm/github-markdown-css@5.5.1/github-markdown.min.css" />
    <link rel="stylesheet" href="https://cdn.jsdelivr.net/npm/katex@0.16.11/dist/katex.min.css" />
    <link rel="stylesheet" href="https://cdn.jsdelivr.net/gh/highlightjs/cdn-release@11.9.0/build/styles/github.min.css" />
    <script defer src="https://cdn.jsdelivr.net/npm/katex@0.16.11/dist/katex.min.js"></script>
    <script defer src="https://cdn.jsdelivr.net/npm/katex@0.16.11/dist/contrib/auto-render.min.js"></script>
    <script defer src="https://cdn.jsdelivr.net/gh/highlightjs/cdn-release@11.9.0/build/highlight.min.js"></script>
    <style>
      body { margin: 0; background: #fff; }
      .container { padding: 16px; }
      .markdown-body { box-sizing: border-box; min-width: 200px; max-width: 980px; margin: 0 auto; padding: 16px; }
      .toc { max-width: 980px; margin: 0 auto; padding: 0 16px; }
      .toc details { border: 1px solid #e5e7eb; border-radius: 10px; padding: 10px 12px; background: #fafafa; }
      .toc summary { cursor: pointer; font-weight: 600; }
      .toc a { text-decoration: none; }
      .toc ul { margin: 10px 0 0 18px; padding: 0; }
      .toc li { margin: 6px 0; }
      img { max-width: 100%; height: auto; }
    </style>
  </head>
  <body>
    <div class="container">
      <div class="toc" id="toc"></div>
      <article id="content" class="markdown-body">$bodyHtml</article>
    </div>
    <script>
      document.addEventListener("DOMContentLoaded", function () {
        try {
          var root = document.getElementById("content");
          if (root) {
            var headings = root.querySelectorAll("h1, h2, h3");
            var ul = document.createElement("ul");
            var has = false;
            for (var i = 0; i < headings.length; i++) {
              var h = headings[i];
              if (!h.id) {
                var id = (h.textContent || "").trim().toLowerCase()
                  .replace(/[^a-z0-9\u4e00-\u9fa5\s-]/g, "")
                  .replace(/\s+/g, "-")
                  .substring(0, 64);
                if (!id) id = "h-" + i;
                h.id = id;
              }
              var li = document.createElement("li");
              if (h.tagName === "H2") li.style.marginLeft = "10px";
              if (h.tagName === "H3") li.style.marginLeft = "20px";
              var a = document.createElement("a");
              a.href = "#" + h.id;
              a.textContent = h.textContent;
              li.appendChild(a);
              ul.appendChild(li);
              has = true;
            }
            if (has) {
              var details = document.createElement("details");
              details.open = false;
              var summary = document.createElement("summary");
              summary.textContent = "目录";
              details.appendChild(summary);
              details.appendChild(ul);
              var toc = document.getElementById("toc");
              if (toc) toc.appendChild(details);
            }
          }
        } catch (e) {}

        try {
          if (typeof hljs !== "undefined" && hljs && typeof hljs.highlightAll === "function") {
            hljs.highlightAll();
          }
        } catch (e) {}

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
