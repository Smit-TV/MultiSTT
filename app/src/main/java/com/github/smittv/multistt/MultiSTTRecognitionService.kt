package com.github.smittv.multistt

import android.content.AttributionSource
import android.content.Context
import android.content.ContextParams
import android.content.Intent
import android.content.ServiceConnection
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.os.Bundle
import android.speech.ModelDownloadListener
import android.speech.RecognitionService
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import com.github.smittv.multistt.engines.Engine
import com.github.smittv.multistt.engines.ListenCallback
import com.github.smittv.multistt.engines.ResultCallback
import com.github.smittv.multistt.engines.SpeechRecognizerUtils
import java.util.concurrent.Executor

class MultiSTTRecognitionService : RecognitionService() {
    companion object {
        const val TAG = "MultiSTTRecognitionService"

        // MultiSTT supports a lot of engines so we need to know which one the user wants to use
        // at least in the future
        const val RECOGNITION_ENGINE = "com.github.smittv.multistt.RECOGNITION_ENGINE"
        var instance: MultiSTTRecognitionService? = null
    }

    private var speechRecognitionCallback: Callback? = null
    private var attributionContext: Context? = null
    private lateinit var soundManager: SoundManager
    private lateinit var speechRecognizerUtils: SpeechRecognizerUtils
    private lateinit var engines: Map<String, Engine>
    private var currentEngine: Engine? = null

    override fun onCreate() {
        super.onCreate()

        instance = this
        soundManager = SoundManager(this)
        speechRecognizerUtils = SpeechRecognizerUtils(this)
        engines = speechRecognizerUtils.getAllEngines()

        for ((_, engine) in engines) {
            engine.setListenCallback(ListenCallbackImpl(this))
        }

        Log.i(TAG, "onCreate")
    }

    override fun onDestroy() {
        super.onDestroy()

        for ((_, engine) in engines) {
            engine.close()
        }

        engines = emptyMap()
    }

    fun isListening(): Boolean = speechRecognitionCallback != null

    fun failToRecognize(results: ListenCallback.Results) {
        Log.i(TAG, "failToRecognize ${results.error}")

        onCancel(speechRecognitionCallback)
    }

    fun recognizedSpeechResult(results: ListenCallback.Results) {
        Log.i(TAG, "recognizedSpeechResult")

        if (speechRecognitionCallback == null) {
            Log.e(TAG, "No callback to give results")

            soundManager.playFailToRecognizeSound()
            return
        }

        val result = TextProcessor(this, results.result ?: "").toString()

        speechRecognitionCallback?.results(Bundle().apply {
            putStringArrayList(
                SpeechRecognizer.RESULTS_RECOGNITION,
                arrayListOf(result)
            )
        })

        speechRecognitionCallback?.endOfSpeech()

        if (result?.isNotEmpty() == true) {
            soundManager.playSuccessfulRecognitionSound()
        } else {
            soundManager.playFailToRecognizeSound()
        }

        speechRecognitionCallback = null
    }

    override fun onStartListening(recognizerIntent: Intent?, listener: Callback?) {
        Log.i(TAG, "onStartListening")

        if (listener == null || recognizerIntent == null
            || engines.isEmpty()) {
            Log.e(TAG, "onStartListening listener = $listener recognizer intent = $recognizerIntent")
            soundManager.playFailToRecognizeSound()
            return
        }

        Log.i(TAG,
            recognizerIntent.toString()
        + " "
            + recognizerIntent.`package`
            + " "
            + recognizerIntent.extras.toString()
        )

        speechRecognitionCallback = listener

        val extras = recognizerIntent.extras ?: Bundle()

        var language = extras.getString(
            RecognizerIntent.EXTRA_LANGUAGE, null
        )

        if (language == null) {
            Log.w(TAG, "Warn: recognition language is not set")
        }

        val engine = extras.getString(RECOGNITION_ENGINE, "")

        if (engine.isEmpty()) {
            Log.w(TAG, "No engine set. Using default")
        }

        if (Build.VERSION.SDK_INT >= 31) {
            Log.i(TAG, "Creating attribution context ")
            attributionContext = createContext(
                ContextParams.Builder()
                    .setNextAttributionSource(
                        listener.callingAttributionSource
                    )
                    .build()
            )
        }

        listener.readyForSpeech(Bundle())
        listener.beginningOfSpeech()
        soundManager.playStartListeningSound()

        var recognizer: Engine? = null

            recognizer = engines[engine] ?: engines[engines.keys.first()]

        recognizer?.let {
            Log.i(TAG, "recognition engine ${it.name}")

            currentEngine = it

            val reco = it.getRecognizers().filter {
                it.locale == language
            }?.firstOrNull()
            reco?.let { reco ->
                it.selectDefaultRecognizer(reco)
            }

            it.recognizeOnce(attributionContext)
        }
    }

    override fun onCancel(listener: Callback?) {
        Log.i(TAG, "onCancel")

        soundManager.playFailToRecognizeSound()
        attributionContext = null

        currentEngine?.stopRecognition()

        listener ?: return

        listener.endOfSpeech()
    }

    override fun onCheckRecognitionSupport(
        recognizerIntent: Intent,
        supportCallback: SupportCallback
    ) {
        Log.i(TAG, "onCheckRecognitionSupport")
        Log.i(TAG, "recognizer intent $recognizerIntent")
        super.onCheckRecognitionSupport(recognizerIntent, supportCallback)
    }

    override fun onCheckRecognitionSupport(
        recognizerIntent: Intent,
        attributionSource: AttributionSource,
        supportCallback: SupportCallback
    ) {
        onCheckRecognitionSupport(recognizerIntent, supportCallback)
    }

    override fun onTriggerModelDownload(recognizerIntent: Intent) {
        super.onTriggerModelDownload(recognizerIntent)
    }

    override fun onTriggerModelDownload(
        recognizerIntent: Intent,
        attributionSource: AttributionSource
    ) {
        super.onTriggerModelDownload(recognizerIntent, attributionSource)
    }

    override fun onTriggerModelDownload(
        recognizerIntent: Intent,
        attributionSource: AttributionSource,
        listener: ModelDownloadListener
    ) {
        listener.onSuccess()
    }

    override fun onStopListening(listener: Callback?) {
        Log.i(TAG, "onStopListening")

        attributionContext = null

        listener ?: return

        listener.endOfSpeech()

        currentEngine?.endRecognition(object : ResultCallback() {
            override fun onResult(result: String?) {
                Log.i(TAG, "ResultCallback@onResult")
                if (result?.isNotEmpty() == true) {
                    soundManager.playSuccessfulRecognitionSound()
                }

                listener.results(
                    Bundle().apply
                    {
                        putStringArrayList(
                            SpeechRecognizer.RESULTS_RECOGNITION,
                            arrayListOf(result ?: "")
                        )
                    })
            }

            override fun onError(error: ListenCallback.Results.ResultError) {
                Log.e(TAG, "ResultCallback@onError $error")
                    soundManager.playFailToRecognizeSound()

                listener?.results(Bundle())
            }

        })
    }
    }