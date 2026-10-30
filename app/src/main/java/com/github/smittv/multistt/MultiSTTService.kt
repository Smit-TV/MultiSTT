package com.github.smittv.multistt

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.inputmethodservice.InputMethodService
import android.os.Build
import android.os.Bundle
import android.preference.PreferenceManager
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.TextView
import android.widget.Toast
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.rememberTextMeasurer
import com.github.smittv.multistt.engines.Engine
import com.github.smittv.multistt.engines.EngineConfigParser
import com.github.smittv.multistt.engines.SpeechRecognizerUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MultiSTTService : InputMethodService() {
    companion object {
        const val TAG = "MultiSTTService"

        // Recognition is not working now
        const val MODE_NOT_RECOGNIZING = -1

        // Recognizes once
        const val MODE_ONCE = 0

        // Starts continuous recognition
        const val MODE_CONTINUOUS = 1

        var instance: MultiSTTService? = null
    }

    private lateinit var prefs: SharedPreferences
    private lateinit var editor: SharedPreferences.Editor
    private lateinit var imm: InputMethodManager
    private lateinit var keyboardView: LinearLayout
    private lateinit var speechRecognizerUtils: SpeechRecognizerUtils
    private val serviceScope = CoroutineScope(
        SupervisorJob() + Dispatchers.Main
    )
    private lateinit var speechRecognizer: SpeechRecognizer
    private var supportedLanguages = setOf<String>()
    private var currentLanguage = ""
    private var currentEngine = ""
    private var recognitionMode = MODE_NOT_RECOGNIZING

    override fun onCreate() {
        super.onCreate()

        Log.i(TAG, "onCreate")

        instance = this
        prefs = PreferenceManager.getDefaultSharedPreferences(this)
        editor = prefs.edit()
        imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this,
            ComponentName(this,
            MultiSTTRecognitionService::class.java
            )
        )

        speechRecognizer.setRecognitionListener( object : RecognitionListener {

            override fun onReadyForSpeech(params: Bundle?) {
                Log.i(TAG, "onReadyForSpeech: $params")
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
            }

            override fun onError(error: Int) {
                Log.e(TAG, "Stopping all recognition sessions. onError")
                recognitionMode = MODE_NOT_RECOGNIZING

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

                serviceScope.launch {
                    if (!isInputViewShown) {
                        return@launch
                    }

                    Toast.makeText(this@MultiSTTService,
                        getString(R.string.fail_to_recognize_voice_reason, errorName),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }

            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val confidences = results?.getFloatArray(SpeechRecognizer.CONFIDENCE_SCORES)

                Log.i(TAG, "onResults")
                Log.i(TAG, "  Matches: $matches")
                Log.i(TAG, "  Confidences: ${confidences?.contentToString()}")
                Log.i(TAG, "  Bundle: $results")

                if (recognitionMode == MODE_ONCE) {
                    recognitionMode = MODE_NOT_RECOGNIZING
                }



                if (matches == null ||
                    matches.joinToString(separator = "").isEmpty()) {
                    serviceScope.launch {
                        Toast.makeText(this@MultiSTTService, R.string.cant_hear, Toast.LENGTH_SHORT).show()
                    }

                    if (prefs.getBoolean(Prefs.AUTOMATICLY_STOP_CONTINUOUS_SPEECH_RECOGNITION, false) && recognitionMode == MODE_CONTINUOUS) {
                        Log.i(TAG, "automaticly stop continuous speech recognition when an empty result received")
                        recognitionMode = MODE_NOT_RECOGNIZING
                    }

                    return
                }

                var text = matches.joinToString("\n")

//                text = TextProcessor(this@MultiSTTService, text).result()

                val ic = currentInputConnection ?: run {
                    Log.e(TAG, "No input connection")
                    return
                }

                ic.commitText(text, 1)

                // starting recognition again if needed
                if (recognitionMode == MODE_CONTINUOUS) {
                    recognitionMode = MODE_NOT_RECOGNIZING
                    startContinuousRecognition()
                }
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

        initSupportedLanguages()

        Log.i(TAG, "onCreated")
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
            Activity.RESULT_OK,
            null,
            null
        )
    }

    fun initLanguageBar() {
        Log.i(TAG, "initLanguageBar")

        if (!::keyboardView.isInitialized || supportedLanguages.isEmpty()) {
            Log.i(TAG, "keyboard view is not initialized or speech recognizer doesn't support any languages")
            return
        }

        val language = keyboardView.findViewById<TextView>(R.id.current_recognition_language)

        if (language.text.isEmpty()) {
            language.text = supportedLanguages.first()
            currentLanguage = language.text.toString()
        }

        Log.i(TAG, "default language ${language.text}")

        val nextLanguage = keyboardView.findViewById<ImageButton>(R.id.next_recognition_language)

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

        val previousLanguage = keyboardView.findViewById<ImageButton>(R.id.previous_recognition_language)

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

    override fun onDestroy() {
        super.onDestroy()

        Log.i(TAG, "onDestroy")

        instance = null
        speechRecognizer.stopListening()
        speechRecognizer.destroy()

        Log.i(TAG, "onDestroyed")
    }

    fun updateSpeechRecognizer() {
        Log.i(TAG, "onUpdateSpeechRecognizer")
    }

    fun openSettings() {
        val intent = Intent(this, MainActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        startActivity(intent)
    }

    fun switchToKeyboard() {
        try {
            if (Build.VERSION.SDK_INT > 27) {
                switchToNextInputMethod(false)
                return
            }

            if (packageManager.hasSystemFeature(PackageManager.FEATURE_INPUT_METHODS)) {
                imm.showInputMethodPicker()
                return
            }

            val token = window?.window?.attributes?.token ?: run {
                Log.e(TAG, "fail to switch input method. fail to get token")
                return
            }

            imm.switchToNextInputMethod(token, false)
        } catch (e: Throwable) {
            e.printStackTrace()
        }
    }

    fun performIMeAction() {
        val ic = currentInputConnection ?: return
        val info = currentInputEditorInfo
        val action = info?.imeOptions?.and(EditorInfo.IME_MASK_ACTION)

        Log.i(TAG, "ime options ${info.imeOptions}")

        if (action != null &&
            action != EditorInfo.IME_ACTION_NONE &&
            action != EditorInfo.IME_ACTION_UNSPECIFIED &&
            action != EditorInfo.IME_ACTION_NEXT
        ) {
            if (!ic.performEditorAction(action)) {
                sendDownUpKeyEvents(KeyEvent.KEYCODE_ENTER)
            }
        } else {
            sendDownUpKeyEvents(KeyEvent.KEYCODE_ENTER)
        }
    }

    fun selectRecognitionEngine() {
        val currentRecoEngine = keyboardView.findViewById<TextView>(R.id.current_engine)

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

    fun initEnterButton() {
        val enter = keyboardView.findViewById<Button>(R.id.enter)
        val info = currentInputEditorInfo ?: return

        val actionRes = when (info.imeOptions.and(EditorInfo.IME_MASK_ACTION)) {
            EditorInfo.IME_ACTION_NEXT -> R.string.next
            EditorInfo.IME_ACTION_GO -> R.string.go
            EditorInfo.IME_ACTION_DONE -> R.string.done
            EditorInfo.IME_ACTION_SEARCH -> R.string.search
            EditorInfo.IME_ACTION_SEND -> R.string.search
            else -> R.string.enter
        }

        enter.setText(actionRes)
    }

    override fun onStartInput(attribute: EditorInfo?, restarting: Boolean) {
        super.onStartInput(attribute, restarting)

        initSupportedLanguages()

        if (::keyboardView.isInitialized) {
            initEnterButton()
            initLanguageBar()
        }
    }

    fun recognizeOnce() {
        Log.i(TAG, "recognizeOnce")

        if (recognitionMode != MODE_NOT_RECOGNIZING) {
            Log.w(TAG, "Already recognizing....stopping recognition")
            speechRecognizer.stopListening()
            return
        }


        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE, currentLanguage
            )

            putExtra(
                MultiSTTRecognitionService.RECOGNITION_ENGINE, currentEngine
            )
        }

        recognitionMode = MODE_ONCE

                speechRecognizer.startListening(intent)
    }

    fun startContinuousRecognition() {
        Log.i(TAG, "startContinuousRecognition")

        if (recognitionMode != MODE_NOT_RECOGNIZING) {
            Log.w(TAG, "Already recognizing.... passing")
            return
        }

        recognitionMode = MODE_CONTINUOUS

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE, currentLanguage
            )

            putExtra(
                MultiSTTRecognitionService.RECOGNITION_ENGINE, currentEngine
            )
        }

        speechRecognizer.startListening(intent)
    }

    override fun onFinishInput() {
        super.onFinishInput()

        Log.i(TAG, "onFinishInput")

        if (::speechRecognizer.isInitialized) {
            speechRecognizer.stopListening()
        }
    }

    override fun onWindowHidden() {
        super.onWindowHidden()

        if (::speechRecognizer.isInitialized) {
            speechRecognizer.stopListening()
        }
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

    override fun onCreateInputView(): View? {
        Log.i(TAG, "onCreateInputView")

        keyboardView = layoutInflater.inflate(R.layout.keyboard_view, null) as LinearLayout

        val openSettings = keyboardView.findViewById<ImageButton>(R.id.open_settings)

        openSettings.setOnClickListener {
            openSettings()
        }

        val switchToKeyboard = keyboardView.findViewById<ImageButton>(R.id.keyboard)

        switchToKeyboard.setOnClickListener {
            switchToKeyboard()
        }

        val enter = keyboardView.findViewById<Button>(R.id.enter)

        enter.setOnClickListener {
            performIMeAction()
        }

        val speak = keyboardView.findViewById<ImageButton>(R.id.speak)

        speak.setOnClickListener {
            recognizeOnce()
        }

        speak.setOnLongClickListener {
            startContinuousRecognition()
            true
        }

        val currentRecoEngine = keyboardView.findViewById<LinearLayout>(R.id.current_reco_engine)

        currentRecoEngine.setOnClickListener {
            selectRecognitionEngine()
        }

        val currentEngineName = keyboardView.findViewById<TextView>(R.id.current_engine)

        currentEngine = getCurrentRecognitionEngine()
        currentEngineName.text = currentEngine

        return keyboardView
    }
}