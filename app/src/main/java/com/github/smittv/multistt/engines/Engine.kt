package com.github.smittv.multistt.engines

import android.content.Context

interface Engine {
    val name: String

    fun recognizeOnce(context: Context? = null)
    fun startContinuousRecognition(context: Context? = null)
    fun stopRecognition()
    fun getRecognizers(): List<SpeechRecognitionEngineModel>
    fun selectDefaultRecognizer(recognizer: SpeechRecognitionEngineModel)
    fun reinit()
    fun close()
    fun isRecognizing(): Boolean
    fun setListenCallback(callback: ListenCallback)
    fun writeAudioData(buffer: ByteArray)
    fun endRecognition(resultCallback: ResultCallback)
}