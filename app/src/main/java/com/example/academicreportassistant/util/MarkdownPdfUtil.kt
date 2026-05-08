package com.example.academicreportassistant.util

import android.content.Context
import android.graphics.pdf.PdfDocument
import android.util.TypedValue
import android.view.View
import android.widget.TextView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.ceil

object MarkdownPdfUtil {
    suspend fun writeMarkdownPdf(
        context: Context,
        markdown: String,
        outFile: File,
    ) {
        withContext(Dispatchers.Main) {
            val pageWidth = 595
            val pageHeight = 842
            val margin = 36
            val contentWidth = pageWidth - margin * 2
            val contentHeight = pageHeight - margin * 2

            val tv =
                TextView(context).apply {
                    setTextIsSelectable(false)
                    setLineSpacing(0f, 1.25f)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                    ellipsize = null
                    maxLines = Integer.MAX_VALUE
                    isSingleLine = false
                    setText(markdown)
                }

            MarkdownRender.get(context).setMarkdown(tv, markdown)
            delay(800)

            val widthSpec = View.MeasureSpec.makeMeasureSpec(contentWidth, View.MeasureSpec.EXACTLY)
            val heightSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
            tv.measure(widthSpec, heightSpec)
            tv.layout(0, 0, contentWidth, tv.measuredHeight)

            outFile.parentFile?.mkdirs()
            val doc = PdfDocument()
            val totalPages = ceil(tv.measuredHeight.toFloat() / contentHeight.toFloat()).toInt().coerceAtLeast(1)
            for (pageIndex in 0 until totalPages) {
                val pageNumber = pageIndex + 1
                val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
                val page = doc.startPage(pageInfo)
                val canvas = page.canvas
                val yOffset = pageIndex * contentHeight
                canvas.save()
                canvas.translate(margin.toFloat(), (margin - yOffset).toFloat())
                tv.draw(canvas)
                canvas.restore()
                doc.finishPage(page)
            }
            outFile.outputStream().use { out -> doc.writeTo(out) }
            doc.close()
        }
    }
}
