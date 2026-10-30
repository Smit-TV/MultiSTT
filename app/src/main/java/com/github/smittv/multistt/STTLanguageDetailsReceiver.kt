package com.github.smittv.multistt

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.nfc.Tag
import android.os.Bundle
import android.speech.RecognizerIntent
import android.util.Log
import com.github.smittv.multistt.engines.ModelConfigParser

class STTLanguageDetailsReceiver : BroadcastReceiver() {
    companion object {
        const val TAG = "STTLanguageDetailsReceiver"
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        Log.i(TAG, "onReceive")
        context ?: run {
            Log.e(TAG, "No context received")
            return
        }

        val supportedLanguages = ModelConfigParser(context).getAvailableLanguages()

        Log.i(TAG, "supported languages $supportedLanguages")

        val extras = Bundle()

        extras.putStringArrayList(
            RecognizerIntent.EXTRA_SUPPORTED_LANGUAGES, ArrayList(supportedLanguages)
        )

        setResultExtras(extras)
    }
}