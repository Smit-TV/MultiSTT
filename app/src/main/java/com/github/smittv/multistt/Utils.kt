package com.github.smittv.multistt

import android.content.Context
import android.content.Intent
import android.util.Base64
import androidx.core.content.FileProvider
import java.io.File


object Utils {
    fun sendFile(context: Context,file: File, text: String) {
        val intent = Intent(Intent.ACTION_SEND)
        val uri = FileProvider.getUriForFile(
            context, context.packageName + ".provider", file
        )
        intent.setType("text/plain")
        intent.putExtra(Intent.EXTRA_STREAM, uri)
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(Intent.createChooser(intent, text))
    }

    /*
    * Encodes strings
     */
    fun encodeString(str: String): String {
        try {
            return Base64.encodeToString(
                str.toByteArray(Charsets.UTF_8),
                Base64.NO_WRAP
            )
        } catch (e: Throwable) {
            return str
        }
    }

    /** Decodes string */
    fun decodeString(str: String): String {
        try {
            return String(
                Base64.decode(str, Base64.NO_WRAP),
                Charsets.UTF_8
            )
        } catch (e: Throwable) {
            return str
        }
    }


}