package com.github.smittv.multistt.engines

import android.util.Log

data class SpeechRecognitionEngineModel(
    val name: String,
    val locale: String,
    val pathToModel: String?,
    val version: String?,
    val vendor: String,
) {
    val isOffline = pathToModel != null

    init {
        Log.i("SpeechRecognitionEngineModel",
            "Created speech recognition engine model: $vendor: name: ${name} locale: ${locale} path to model: ${pathToModel} version: $version")
    }
}