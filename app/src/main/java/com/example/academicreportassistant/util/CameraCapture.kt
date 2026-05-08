package com.example.academicreportassistant.util

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.util.UUID

fun createTempCameraFile(context: Context): File {
    val dir = File(context.cacheDir, "camera")
    dir.mkdirs()
    return File(dir, "${UUID.randomUUID()}.jpg")
}

fun fileToContentUri(context: Context, file: File): Uri {
    return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
}

