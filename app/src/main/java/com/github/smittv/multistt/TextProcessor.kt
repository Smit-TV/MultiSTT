package com.github.smittv.multistt

import android.content.Context
import androidx.preference.PreferenceManager

class TextProcessor(
    val context: Context,
    val text: String
) {
    private val prefs = PreferenceManager.getDefaultSharedPreferences(context)

    override fun toString(): String {
        var str = text

        // Detailed output format includes characters
        if (!prefs.getBoolean(Prefs.USE_DETAILED_OUTPUT_FORMAT, true)) {
            // replace all characters with ""
            str = replaceAllCharacters(str)
        }

        return str
    }

    fun replaceAllCharacters(str: String): String {
        return str.filter {
            it.isLetterOrDigit() || it.isWhitespace()
        }.toString()
    }
}