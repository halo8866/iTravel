package com.itravel.app.data

import android.content.Context
import java.io.File
import java.util.UUID

object PhotoStorage {

    fun imagesDir(context: Context): File =
        File(context.filesDir, "images").apply { if (!exists()) mkdirs() }

    fun newImageFile(context: Context): File =
        File(imagesDir(context), "${UUID.randomUUID()}.jpg")

    /** Copy content behind [uri] into app-private storage, returns absolute path or null. */
    fun copyUri(context: Context, uri: android.net.Uri): String? = runCatching {
        val dst = newImageFile(context)
        context.contentResolver.openInputStream(uri).use { input ->
            requireNotNull(input) { "cannot open input stream" }
            dst.outputStream().use { output -> input.copyTo(output) }
        }
        dst.absolutePath
    }.getOrNull()

    fun delete(path: String) {
        runCatching { File(path).takeIf { it.exists() }?.delete() }
    }
}
