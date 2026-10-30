package com.github.smittv.multistt.engines

import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import com.microsoft.cognitiveservices.speech.CancellationErrorCode
import com.microsoft.cognitiveservices.speech.EmbeddedSpeechConfig
import com.microsoft.cognitiveservices.speech.OutputFormat
import com.microsoft.cognitiveservices.speech.PropertyId
import com.microsoft.cognitiveservices.speech.SpeechRecognizer
import com.microsoft.cognitiveservices.speech.audio.AudioConfig
import com.microsoft.cognitiveservices.speech.audio.AudioStreamFormat
import com.microsoft.cognitiveservices.speech.audio.PushAudioInputStream
import java.io.File

    class MicrosoftOfflineSpeechRecognitionEngine(
    val context: Context
) : Engine {
    companion object {
        const val TAG = "MicrosoftOfflineSpeechRecognitionEngine"
        const val VENDOR = "microsoft"
    }

    private var isListening = false
    private var recognizer: SpeechRecognizer? = null
    private val recognizers = mutableListOf<SpeechRecognizer>()
    private var callback: ListenCallback? = null
        private lateinit var pushStream: PushAudioInputStream
        private lateinit var audioConfig: AudioConfig
        private var audioRecord: AudioRecord? = null
        private var lastResult: String? = null


        private var audioThread: Thread? = null

        @Volatile
        private var isRecording = false

        val modelsDir: String
        get() = (context.getExternalFilesDir(null)?.absolutePath ?: "") + "/$VENDOR/"

    init {
        Log.i(TAG, "first init of microsoft offline speech recognizer")
        init()
    }

    override val name = "microsoft"

    override fun reinit() {
        Log.i(TAG, "reinit")
        close()
        init()
    }

    override fun close() {
        Log.i(TAG, "close")
        isListening = false
        recognizer = null

        for (r in recognizers) {
            r.close()
        }

        recognizers.clear()

        callback?.close()
        callback = null

        try {
            audioRecord?.stop()
            audioRecord?.release()
            audioRecord = null
        } catch (e: Throwable) {
            Log.e(TAG, "$e", e)
        }
    }

    fun init() {
        Log.i(TAG, "init")

        val modelConfigParser = ModelConfigParser(context)
        val models = modelConfigParser.getSpeechModels(VENDOR)

        for (model in models) {
            val rec = createRecognizer(model) ?: continue

//            rec.speechRecognitionLanguage = model.locale

            recognizers.add(rec)
        }

        if (recognizer == null && recognizers.isNotEmpty()) {
            Log.i(TAG, "Default recognizer set")
            recognizer = recognizers.first()
        }
    }

    fun createRecognizer(model: ModelConfigParser.Model): SpeechRecognizer? {
        Log.i(TAG, "createRecognizer $model")

        try {
            val modelPath = modelsDir + "/" + model.folder
            if (!File(modelPath).exists()) {
                throw Exception("No model data found at $modelPath $model")
            }

            val config = EmbeddedSpeechConfig.fromPath(modelPath)

            config.speechRecognitionOutputFormat = OutputFormat.Detailed

            // Parameters
            // First line is a model name
            // second line is a model license or key (sometimes not required)
            // format
            /*
            * Model name
            * license or key
             */
            val parameters = model.parameters.split("\n")
            val modelName = parameters.firstOrNull() ?: throw Exception("No model parameters set $model")
            val modelLicense = parameters.getOrNull(1) ?: ""

            config.setSpeechRecognitionModel(modelName, modelLicense)

            config.setProperty(PropertyId.SpeechServiceConnection_RecoLanguage, model.locale)

            val stt = SpeechRecognizer(config, createPushAudioInputStream())

            stt.canceled.addEventListener { any, args ->
                Log.e(TAG, "recognition cancelled")

                lastResult = null

                try {
                    audioRecord?.stop()
                    audioRecord?.release()
                } catch (e: Throwable) {
                    Log.e(TAG, "fail to release audio record $e", e)
                } finally {
                    audioRecord = null
                }

                val errorCode = args.errorCode

                val error = when (errorCode) {
                    CancellationErrorCode.RuntimeError -> ListenCallback.Results.ResultError.ERROR_RUNTIME_ERROR
                    CancellationErrorCode.ServiceError -> ListenCallback.Results.ResultError.ERROR_SERVICE_ERROR
                    CancellationErrorCode.EmbeddedModelError -> ListenCallback.Results.ResultError.ERROR_EMBEDDED_MODEL_ERROR
                    CancellationErrorCode.ServiceTimeout -> ListenCallback.Results.ResultError.ERROR_SERVICE_TIMEOUT_ERROR
                    CancellationErrorCode.AuthenticationFailure -> ListenCallback.Results.ResultError.ERROR_AUTHENTICATION_ERROR
                    CancellationErrorCode.BadRequest -> ListenCallback.Results.ResultError.ERROR_BAD_REQUEST_ERROR
                    CancellationErrorCode.ConnectionFailure -> ListenCallback.Results.ResultError.ERROR_CONNECTION_FAILURE_ERROR
                    CancellationErrorCode.Forbidden -> ListenCallback.Results.ResultError.ERROR_FORBIDDEN_ERROR
                    CancellationErrorCode.TooManyRequests -> ListenCallback.Results.ResultError.ERROR_FAIL_TO_RECOGNIZE_TOO_MANY_REQUESTS
                    CancellationErrorCode.NoError -> ListenCallback.Results.ResultError.NO_ERROR
                    CancellationErrorCode.ServiceRedirectPermanent -> ListenCallback.Results.ResultError.ERROR_SERVICE_REDIRECT_PERMANENT_ERROR
                    CancellationErrorCode.ServiceRedirectTemporary -> ListenCallback.Results.ResultError.ERROR_SERVICE_REDIRECT_TEMPORARY_ERROR
                    CancellationErrorCode.ServiceUnavailable -> ListenCallback.Results.ResultError.ERROR_SERVICE_UNAVAILABLE_ERROR
                    else -> ListenCallback.Results.ResultError.ERROR_UNKNOWN_ERROR
                }

                isListening = false
                callback?.onStopListening(
                    true,
                    ListenCallback.Results(
                        false,
                        null,
                        error
                    ))
            }

            stt.recognized.addEventListener { any, args ->
                isListening = false

                try {
                    audioRecord?.stop()
                    audioRecord?.release()
                } catch (e: Throwable) {
                    Log.e(TAG, "fail to release audio record $e", e)
                } finally {
                    audioRecord = null
                }

                lastResult = args.result.text

                callback?.onStopListening(
                    false,
                    ListenCallback.Results(
                        true,
                        args.result.text,
                        ListenCallback.Results.ResultError.NO_ERROR
                    )
                )
            }

            stt.sessionStarted.addEventListener { any, args ->
                isListening = true
                lastResult = null
                callback?.onStartListening()
            }

            return stt

        } catch (e: Throwable) {
            Log.e(TAG, "createRecognizer fail $e", e)
        }

        return null
    }

    override fun isRecognizing(): Boolean {
        Log.i(TAG, "isRecognizing ${isListening}")
        return isListening
    }

    override fun recognizeOnce(context: Context?) {
        Log.i(
            TAG,
            "recognizeOnce"
        )
        try {
            initAudioRecord(context)
            recognizer?.recognizeOnceAsync()
            startRecording()
        } catch (e: Throwable) {
            Log.e(
                TAG,
                "Fail to recognize once"
            )
            e.printStackTrace()
        }
    }

        private fun startRecording() {
            val recorder = audioRecord
                ?: throw IllegalStateException("AudioRecord is not initialized")

            if (isRecording) return

            isRecording = true

            audioThread = Thread {
                val buffer = ByteArray(3200)

                try {
                    recorder.startRecording()

                    while (isRecording) {
                        val bytesRead = recorder.read(
                            buffer,
                            0,
                            buffer.size
                        )

                        if (bytesRead > 0) {

                            writeAudioData(buffer.copyOf(bytesRead))
                        } else if (bytesRead < 0) {
                            Log.e(
                                "AudioRecord",
                                "Fail to read audio: $bytesRead"
                            )
                            break
                        }
                    }
                } catch (e: Exception) {
                    Log.e(
                        TAG,
                        "Recording error",
                        e
                    )
                } finally {
                    isRecording = false

                    try {
                        if (
                            recorder.recordingState ==
                            AudioRecord.RECORDSTATE_RECORDING
                        ) {
                            recorder.stop()
                        }
                    } catch (e: IllegalStateException) {
                        Log.e(
                            TAG,
                            "Fail to stop audio recording",
                            e
                        )
                    }
                }
            }.apply {
                name = "AudioRecordThread"
                start()
            }
        }

        fun initAudioRecord(context: Context?) {
            Log.i(TAG, "initAudioRecord")

            val bufferSize = AudioRecord.getMinBufferSize(
                16000,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )

            audioRecord = AudioRecord.Builder()
                .setContext(context ?: this.context)
                .setAudioSource(MediaRecorder.AudioSource.MIC)
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setSampleRate(16000)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                        .build()
                )
                .setBufferSizeInBytes(bufferSize * 2)
                .build()

            require(audioRecord?.state == AudioRecord.STATE_INITIALIZED) {
                "fail to initialize audio record"
            }
        }

    override fun startContinuousRecognition(context: Context?) {
        TODO("Not yet implemented")
    }

    override fun stopRecognition() {
        Log.i(TAG, "stopRecognition")

        isRecording = false

        audioRecord?.let {
            try {
                audioRecord?.stop()
                audioRecord?.release()
            } catch (e: Throwable) {
                Log.e(TAG, "fail to stop recognition audio record $e", e)
            } finally {
                audioRecord = null
            }
        }

        recognizer?.let {
            it.stopContinuousRecognitionAsync()
        }

    }

    override fun getRecognizers(): List<SpeechRecognitionEngineModel> {
        Log.i(TAG, "getRecognizers")

        val models = mutableListOf<SpeechRecognitionEngineModel>()

        for (r in recognizers) {
            models.add(
                SpeechRecognitionEngineModel(
                    r.properties.getProperty(PropertyId.SpeechServiceConnection_RecoModelName),
                    r.properties.getProperty(PropertyId.SpeechServiceConnection_RecoLanguage),
                    r.properties.getProperty(PropertyId.SpeechServiceConnection_SynthOfflineDataPath),
                    null,
                    VENDOR
                )
            )
        }

        return models
    }

    override fun selectDefaultRecognizer(recognizer: SpeechRecognitionEngineModel) {
        Log.i(TAG, "selectDefaultRecognizer $recognizer")

        for (r in recognizers) {
            if (r.properties.getProperty(PropertyId.SpeechServiceConnection_RecoModelName) != recognizer.name) {
                continue
            }
                Log.i(TAG, "default recognizer set")
                this.recognizer = r
        }
    }

    override fun setListenCallback(listenCallback: ListenCallback) {
        callback = listenCallback
    }

        fun createPushAudioInputStream(): AudioConfig {
            if (::audioConfig.isInitialized) {
                return audioConfig
            }

            val format = AudioStreamFormat.getWaveFormatPCM(
                16000,
                16,
                1
            )


            pushStream = PushAudioInputStream.create(format)

            audioConfig = AudioConfig.fromStreamInput(pushStream)

            return audioConfig
        }

        override fun writeAudioData(buffer: ByteArray) {
            Log.i(TAG, "buffer received")
            pushStream?.write(buffer)
        }

        override fun endRecognition(resultCallback: ResultCallback) {
            stopRecognition()
            resultCallback.onResult(lastResult)
        }
}