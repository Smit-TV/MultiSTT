package com.github.smittv.multistt

import android.Manifest
import android.app.Activity
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.github.smittv.multistt.engines.EngineConfigParser
import kotlinx.coroutines.launch

class ActionRecognizeSpeechActivity : AppCompatActivity() {
    companion object {
        const val TAG = "ActionRecognizeSpeechActivity"
    }

    private lateinit var speechRecognizer: SpeechRecognizer
    private lateinit var recognizerView: LinearLayout
    private var supportedLanguages = setOf<String>()
    private var currentEngine = ""
    private  var currentLanguage = ""
    private var isListening = false

    private val launcher =
        registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { result ->
            val micGranted = result[Manifest.permission.RECORD_AUDIO] == true || PermissionUtils.hasMicrophonePermission(this)

            if (!micGranted) {
                showMicDialog()
            } else {
                showRecognizer()
            }
        }

    override fun onDestroy() {
        super.onDestroy()

        speechRecognizer.cancel()
        speechRecognizer.destroy()
    }

    override fun onStop() {
        super.onStop()

        finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.i(TAG, "onCreate")

        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this,
            ComponentName(this,
                MultiSTTRecognitionService::class.java
            )
        )

        speechRecognizer.setRecognitionListener( object : RecognitionListener {

            override fun onReadyForSpeech(params: Bundle?) {
                Log.i(TAG, "onReadyForSpeech: $params")

                isListening = true
            }

            override fun onBeginningOfSpeech() {
                Log.i(TAG, "onBeginningOfSpeech")
            }

            override fun onRmsChanged(rmsdB: Float) {
                Log.i(TAG, "onRmsChanged: $rmsdB")
            }

            override fun onBufferReceived(buffer: ByteArray?) {
                Log.i(TAG, "onBufferReceived: ${buffer?.size ?: 0} bytes")
            }

            override fun onEndOfSpeech() {
                Log.i(TAG, "onEndOfSpeech")
                isListening = false
            }

            override fun onError(error: Int) {

                val errorName = when (error) {
                    SpeechRecognizer.ERROR_AUDIO -> "ERROR_AUDIO"
                    SpeechRecognizer.ERROR_CLIENT -> "ERROR_CLIENT"
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "ERROR_INSUFFICIENT_PERMISSIONS"
                    SpeechRecognizer.ERROR_NETWORK -> "ERROR_NETWORK"
                    SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "ERROR_NETWORK_TIMEOUT"
                    SpeechRecognizer.ERROR_NO_MATCH -> "ERROR_NO_MATCH"
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "ERROR_RECOGNIZER_BUSY"
                    SpeechRecognizer.ERROR_SERVER -> "ERROR_SERVER"
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "ERROR_SPEECH_TIMEOUT"
                    SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED -> "ERROR_LANGUAGE_NOT_SUPPORTED"
                    SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE -> "ERROR_LANGUAGE_UNAVAILABLE"
                    SpeechRecognizer.ERROR_CANNOT_CHECK_SUPPORT -> "ERROR_CANNOT_CHECK_SUPPORT"
                    SpeechRecognizer.ERROR_CANNOT_LISTEN_TO_DOWNLOAD_EVENTS -> "ERROR_CANNOT_LISTEN_TO_DOWNLOAD_EVENTS"
                    else -> "UNKNOWN_ERROR"
                }

                Log.e(TAG, "onError: $error ($errorName)")

                isListening = false

                    Toast.makeText(this@ActionRecognizeSpeechActivity,
                        getString(R.string.fail_to_recognize_voice_reason, errorName),
                        Toast.LENGTH_SHORT
                    ).show()
            }

            override fun onResults(results: Bundle?) {

                isListening = false

                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val confidences = results?.getFloatArray(SpeechRecognizer.CONFIDENCE_SCORES)

                Log.i(TAG, "onResults")
                Log.i(TAG, "  Matches: $matches")
                Log.i(TAG, "  Confidences: ${confidences?.contentToString()}")
                Log.i(TAG, "  Bundle: $results")

                if (matches == null) {
                        Toast.makeText(this@ActionRecognizeSpeechActivity, R.string.cant_hear, Toast.LENGTH_SHORT).show()

                    return
                    }




                val text = matches.joinToString("\n")

            setRecognitionResult(text)
            }

            override fun onPartialResults(partialResults: Bundle?) {
                val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)

                Log.i(TAG, "onPartialResults")
                Log.i(TAG, "  Matches: $matches")
                Log.i(TAG, "  Bundle: $partialResults")
            }

            override fun onEvent(eventType: Int, params: Bundle?) {
                Log.i(TAG, "onEvent: type=$eventType params=$params")
            }

//            override fun onSegmentResults(segmentResults: Bundle?) {
//                Log.i(TAG, "onSegmentResults: $segmentResults")
//            }

            override fun onEndOfSegmentedSession() {
                Log.i(TAG, "onEndOfSegmentedSession")
            }

//            override fun onLanguageDetection(results: Bundle?) {
//                Log.i(TAG, "onLanguageDetection: $results")
        }
//        }
        )

        if (!PermissionUtils.hasMicrophonePermission(this)) {
            launcher.launch(arrayOf(
                Manifest.permission.RECORD_AUDIO
            ))
        } else {
            showRecognizer()
        }
}

    fun showMicDialog() {
        val dialog = AlertDialog.Builder(this, R.style.dialog)
            .setTitle(R.string.microphone)
            .setMessage(R.string.needed_for_voice_recording)
            .setNegativeButton(android.R.string.cancel) { _, _ ->
                finish()
            }
            .setPositiveButton(android.R.string.ok) { _, _ ->
                if (PermissionUtils.canShowMicrophonePermissionRequest(this) && !PermissionUtils.hasMicrophonePermission(this)) {
                    launcher.launch(
                        arrayOf(
                            Manifest.permission.RECORD_AUDIO
                        )
                    )
                } else if (!PermissionUtils.hasMicrophonePermission(this)) {
                    PermissionUtils.openAppSettings(this)
                    finish()
                }
            }
            .create()
        dialog.show()
    }

    override fun onBackPressed() {
        finish()
    }

    fun openSettings() {
        startActivity(
            Intent(this, SettingsActivity::class.java)
        )
    }

    fun initLanguageBar() {
        Log.i(TAG, "initLanguageBar")

        if (!::recognizerView.isInitialized || supportedLanguages.isEmpty()) {
            Log.i(TAG, "keyboard view is not initialized or speech recognizer doesn't support any languages")
            return
        }

        val language = recognizerView.findViewById<TextView>(R.id.current_recognition_language)

        if (language.text.isEmpty()) {
            language.text = supportedLanguages.first()
            currentLanguage = language.text.toString()
        }

        Log.i(TAG, "default language ${language.text}")

        val nextLanguage = recognizerView.findViewById<ImageButton>(R.id.next_recognition_language)

        nextLanguage.setOnClickListener {
            var isFound = false
            for (l in supportedLanguages) {
                if (isFound) {
                    Log.i(TAG, "default language ${l}")
                    language.text = l
                    currentLanguage = l
                    break
                }

                isFound = l == language.text
            }
        }

        val previousLanguage = recognizerView.findViewById<ImageButton>(R.id.previous_recognition_language)

        previousLanguage.setOnClickListener {
            var isFound = false
            for (l in supportedLanguages.reversed()) {
                if (isFound) {
                    language.text = l
                    currentLanguage = l
                    Log.i(TAG, "default language ${language.text}")
                    break
                }

                isFound = l == language.text
            }
        }
    }

    fun initSupportedLanguages() {
        Log.i(TAG, "initSupportedLanguages")

        val intent = Intent(RecognizerIntent.ACTION_GET_LANGUAGE_DETAILS)

        intent.setClass(this, STTLanguageDetailsReceiver::class.java)

        sendOrderedBroadcast(
            intent,
            null,
            object  : BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) {
                    val extras = getResultExtras(false) ?: run {
                        Log.e(TAG, "No languages received")
                        return
                    }

                    val supported = extras.getStringArrayList(
                        RecognizerIntent.EXTRA_SUPPORTED_LANGUAGES
                    ) ?: ArrayList<String>()

                    supportedLanguages = supported.toSet()

                    Log.i(TAG, "supported languages $supported")

                    initLanguageBar()
                }
            },
            null,
            RESULT_OK,
            null,
            null
        )
    }

    fun getCurrentRecognitionEngine(): String {
        val availableEngines = EngineConfigParser(this).getAvailableEngines()

        if (availableEngines.isEmpty()) {
            return currentEngine
        }

        if (availableEngines.contains(currentEngine)) {
            return currentEngine
        }

        return availableEngines.first()
    }

    fun selectRecognitionEngine() {
        val currentRecoEngine = recognizerView.findViewById<TextView>(R.id.current_engine)

        val availableEngines = EngineConfigParser(this).getAvailableEngines()

        if (availableEngines.size < 2) {
            return
        }

        val popupMenu = PopupMenu(this, currentRecoEngine)

        for (e in availableEngines) {
            popupMenu.menu.add(e)
        }

        popupMenu.setOnMenuItemClickListener {
            currentRecoEngine.text = it.title
            currentEngine = it.title.toString()
            true
        }
        popupMenu.show()
    }

    fun recognizeOnce() {
        if (isListening) {
            speechRecognizer.cancel()
            return
        }

        speechRecognizer.startListening(
            Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE, currentLanguage
                )
                putExtra(
                    MultiSTTRecognitionService.RECOGNITION_ENGINE, currentEngine
                )
            }
        )
    }

    fun showRecognizer() {
        Log.i(TAG, "showRecognizer")

        recognizerView = layoutInflater.inflate(R.layout.keyboard_view, null) as LinearLayout

        val openSettings = recognizerView.findViewById<ImageButton>(R.id.open_settings)

        openSettings.setOnClickListener {
            openSettings()
        }

        val switchToKeyboard = recognizerView.findViewById<ImageButton>(R.id.keyboard)
        switchToKeyboard.visibility = View.INVISIBLE

        val enter = recognizerView.findViewById<Button>(R.id.enter)
        enter.visibility = View.INVISIBLE

        val speak = recognizerView.findViewById<ImageButton>(R.id.speak)
        speak.contentDescription = getString(R.string.speak_once)

        speak.setOnClickListener {
            recognizeOnce()
        }

        val currentRecoEngine = recognizerView.findViewById<LinearLayout>(R.id.current_reco_engine)

        currentRecoEngine.setOnClickListener {
            selectRecognitionEngine()
        }

        val currentEngineName = recognizerView.findViewById<TextView>(R.id.current_engine)

        currentEngine = getCurrentRecognitionEngine()
        currentEngineName.text = currentEngine

        initSupportedLanguages()

        setContentView(recognizerView)
    }

fun setRecognitionResult(text: String) {
        val request = intent ?: Intent()

        val logs = StringBuilder()
            .append("intent action: ")
            .append(request.action)
            .append(" extras ")
            .append(request.extras ?: Bundle())
            .toString()

        Log.i(TAG, logs)

        val data = Intent().apply {
            putStringArrayListExtra(
                RecognizerIntent.EXTRA_RESULTS, arrayListOf(text)
            )
        }

        setResult(RESULT_OK, data)
        finish()
    }
}