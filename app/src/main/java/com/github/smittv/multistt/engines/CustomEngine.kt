package com.github.smittv.multistt.engines

import android.content.Context

class CustomEngine(
    override val name: String
) : Engine {
    override fun recognizeOnce(context: Context?) {}
    override fun startContinuousRecognition(context: Context?) {}
    override fun stopRecognition() {}
    override fun getRecognizers(): List<SpeechRecognitionEngineModel> = emptyList()
    override fun selectDefaultRecognizer(recognizer: SpeechRecognitionEngineModel) {}
    override fun reinit() {}
    override fun close() {}
    override fun isRecognizing(): Boolean {
        return false
    }

    override fun setListenCallback(callback: ListenCallback) {}

    override fun writeAudioData(buffer: ByteArray) {}

    override fun endRecognition(resultCallback: ResultCallback) {
        resultCallback.onResult("This is a test result. You're using a test engine.")
    }
}