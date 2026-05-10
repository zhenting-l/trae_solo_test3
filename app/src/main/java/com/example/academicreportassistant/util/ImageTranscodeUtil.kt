package com.lzt.summaryofslides.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ImageDecoder
import android.os.Build
import java.io.ByteArrayOutputStream
import java.io.File

object ImageTranscodeUtil {
    fun loadAndCompressJpeg(
        file: File,
        maxSide: Int = 1280,
        quality: Int = 75,
    ): ByteArray {
        if (Build.VERSION.SDK_INT >= 28) {
            val source = ImageDecoder.createSource(file)
            val bitmap =
                ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                    val w = info.size.width
                    val h = info.size.height
                    val max = maxOf(w, h).coerceAtLeast(1)
                    if (max > maxSide) {
                        val ratio = maxSide.toFloat() / max.toFloat()
                        val nw = (w * ratio).toInt().coerceAtLeast(1)
                        val nh = (h * ratio).toInt().coerceAtLeast(1)
                        decoder.setTargetSize(nw, nh)
                    }
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                }
            val out = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
            bitmap.recycle()
            return out.toByteArray()
        }

        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, opts)

        val sampleSize = calculateInSampleSize(opts.outWidth, opts.outHeight, maxSide)
        val decodeOpts =
            BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.RGB_565
            }
        val bitmap = requireNotNull(BitmapFactory.decodeFile(file.absolutePath, decodeOpts))
        val scaled = scaleIfNeeded(bitmap, maxSide)
        val out = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, quality, out)
        if (scaled !== bitmap) bitmap.recycle()
        scaled.recycle()
        return out.toByteArray()
    }

    private fun scaleIfNeeded(src: Bitmap, maxSide: Int): Bitmap {
        val w = src.width
        val h = src.height
        val max = maxOf(w, h)
        if (max <= maxSide) return src
        val ratio = maxSide.toFloat() / max.toFloat()
        val nw = (w * ratio).toInt().coerceAtLeast(1)
        val nh = (h * ratio).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(src, nw, nh, true)
    }

    private fun calculateInSampleSize(w: Int, h: Int, maxSide: Int): Int {
        var sample = 1
        while (w / sample > maxSide || h / sample > maxSide) {
            sample *= 2
        }
        return sample.coerceAtLeast(1)
    }

    fun packJpegGrid(
        files: List<File>,
        columns: Int = 2,
        cellMaxSide: Int = 640,
        quality: Int = 75,
    ): ByteArray {
        val valid = files.filter { it.exists() }
        require(valid.isNotEmpty())
        val cols = columns.coerceAtLeast(1)
        val bitmaps = mutableListOf<Bitmap>()
        var cellW = 0
        var cellH = 0
        try {
            for (file in valid) {
                val bmp = decodeScaledBitmap(file, cellMaxSide)
                bitmaps += bmp
                cellW = maxOf(cellW, bmp.width)
                cellH = maxOf(cellH, bmp.height)
            }
            cellW = cellW.coerceAtLeast(1)
            cellH = cellH.coerceAtLeast(1)
            val rows = ((bitmaps.size + cols - 1) / cols).coerceAtLeast(1)
            val outW = (cols * cellW).coerceAtLeast(1)
            val outH = (rows * cellH).coerceAtLeast(1)
            val canvasBitmap = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(canvasBitmap)
            canvas.drawColor(Color.WHITE)
            for ((idx, bmp) in bitmaps.withIndex()) {
                val r = idx / cols
                val c = idx % cols
                val left = c * cellW + (cellW - bmp.width) / 2
                val top = r * cellH + (cellH - bmp.height) / 2
                canvas.drawBitmap(bmp, left.toFloat(), top.toFloat(), null)
            }
            val out = ByteArrayOutputStream()
            canvasBitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
            canvasBitmap.recycle()
            return out.toByteArray()
        } finally {
            for (bmp in bitmaps) runCatching { bmp.recycle() }
        }
    }

    private fun decodeScaledBitmap(file: File, maxSide: Int): Bitmap {
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, opts)
        val sampleSize = calculateInSampleSize(opts.outWidth, opts.outHeight, maxSide)
        val decodeOpts =
            BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
        val bitmap = requireNotNull(BitmapFactory.decodeFile(file.absolutePath, decodeOpts))
        val scaled = scaleIfNeeded(bitmap, maxSide)
        if (scaled !== bitmap) bitmap.recycle()
        return scaled
    }
}
