package com.example.academicreportassistant.util

import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import java.io.File
import kotlin.math.ceil

object PdfUtil {
    fun writeTextPdf(
        text: String,
        outFile: File,
        pageWidth: Int = 595,
        pageHeight: Int = 842,
        margin: Int = 36,
        textSize: Float = 12f,
    ) {
        outFile.parentFile?.mkdirs()
        val paint = Paint().apply { this.textSize = textSize }
        val lineHeight = ceil(textSize * 1.4f).toInt()
        val maxWidth = pageWidth - margin * 2
        val maxHeight = pageHeight - margin * 2

        val doc = PdfDocument()
        var pageNumber = 1
        var page = doc.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create())
        var y = margin

        fun newPage() {
            doc.finishPage(page)
            pageNumber += 1
            page = doc.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create())
            y = margin
        }

        val lines = wrapText(text, paint, maxWidth)
        for (line in lines) {
            if (y + lineHeight > margin + maxHeight) newPage()
            page.canvas.drawText(line, margin.toFloat(), y.toFloat(), paint)
            y += lineHeight
        }

        doc.finishPage(page)
        outFile.outputStream().use { out -> doc.writeTo(out) }
        doc.close()
    }

    private fun wrapText(text: String, paint: Paint, maxWidth: Int): List<String> {
        val result = mutableListOf<String>()
        val paragraphs = text.replace("\r\n", "\n").split("\n")
        for (p in paragraphs) {
            if (p.isBlank()) {
                result.add("")
                continue
            }
            var start = 0
            while (start < p.length) {
                var end = p.length
                while (end > start) {
                    val sub = p.substring(start, end)
                    if (paint.measureText(sub) <= maxWidth) break
                    end -= 1
                }
                if (end == start) {
                    result.add(p.substring(start, start + 1))
                    start += 1
                } else {
                    result.add(p.substring(start, end))
                    start = end
                }
            }
        }
        return result
    }
}

