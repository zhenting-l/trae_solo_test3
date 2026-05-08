package com.example.academicreportassistant.util

import java.io.File
import java.io.FileInputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object ZipUtil {
    fun zipFiles(entries: List<Pair<File, String>>, outFile: File) {
        outFile.parentFile?.mkdirs()
        ZipOutputStream(outFile.outputStream()).use { zos ->
            for ((file, name) in entries) {
                FileInputStream(file).use { input ->
                    zos.putNextEntry(ZipEntry(name))
                    input.copyTo(zos)
                    zos.closeEntry()
                }
            }
        }
    }
}

