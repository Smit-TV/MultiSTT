package com.github.smittv.multistt.engines

import android.content.Context

/*
* Utils
*/
class SpeechRecognizerUtils(
    val context: Context
) {
    fun getAllEngines(): Map<String, Engine> {
        val engineInstances = mutableMapOf<String, Engine>()
        val engines = EngineConfigParser(context).getAvailableEngines()

        for (engine in engines) {
            engineInstances[engine] = getEngineInstance(engine)
        }

        return engineInstances
    }

    fun getEngineInstance(engineName: String): Engine {
        return when (engineName) {
            "microsoft",
                 "Microsoft",
                     "MICROSOFT" -> MicrosoftOfflineSpeechRecognitionEngine(context)
            else -> CustomEngine(engineName)
        }
    }

}