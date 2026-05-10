package com.lzt.summaryofslides.util

import android.content.Context
import io.noties.markwon.Markwon
import io.noties.markwon.ext.latex.JLatexMathPlugin
import io.noties.markwon.ext.strikethrough.StrikethroughPlugin
import io.noties.markwon.ext.tables.TablePlugin
import io.noties.markwon.ext.tasklist.TaskListPlugin
import io.noties.markwon.image.ImagesPlugin
import io.noties.markwon.inlineparser.MarkwonInlineParserPlugin

object MarkdownRender {
    @Volatile
    private var cached: Markwon? = null

    fun get(context: Context): Markwon {
        val existing = cached
        if (existing != null) return existing
        val density = context.applicationContext.resources.displayMetrics.scaledDensity
        val latexSize = 16f * density
        val created =
            Markwon.builder(context.applicationContext)
                .usePlugin(ImagesPlugin.create())
                .usePlugin(MarkwonInlineParserPlugin.create())
                .usePlugin(TablePlugin.create(context))
                .usePlugin(StrikethroughPlugin.create())
                .usePlugin(TaskListPlugin.create(context))
                .usePlugin(
                    JLatexMathPlugin.create(
                        latexSize,
                        { builder ->
                            builder.blocksEnabled(true)
                            builder.inlinesEnabled(true)
                        },
                    ),
                )
                .build()
        cached = created
        return created
    }
}
