package com.github.smittv.multistt

import android.content.Context
import android.net.Uri
import android.widget.Toast
import java.io.File

object UriUtils {
    fun getZipFileFromUri(context: Context, uri: Uri): File {
        val tempFile = File(context.cacheDir, "import.zip")
        if (tempFile.exists()) {
            tempFile.delete()
        }
        if (!tempFile.exists()) {
            tempFile.createNewFile()
        }

        context.contentResolver.openInputStream(uri)?.use { input ->
            tempFile.outputStream().use { output ->
                input.copyTo(output)
            }
        } ?: throw IllegalArgumentException("Cannot open URI")

        return  tempFile
    }
}