package com.github.smittv.multistt

import android.util.Log
import com.github.smittv.multistt.engines.ListenCallback

class ListenCallbackImpl(
    val recoService: MultiSTTRecognitionService
) : ListenCallback() {
    companion object {
        const val TAG = "ListenCallbackImpl"
    }

    override fun onStartListening() {
        super.onStartListening()

        Log.i(TAG, "onStartListening")
    }

    override fun onStopListening(cancelled: Boolean, results: Results) {
        super.onStopListening(cancelled, results)

        Log.i(TAG, "onStopListening cancelled: $cancelled is successful ${results.isSuccessful} error: ${results.error}")

        if (cancelled || results.result?.isNotEmpty() != true) {
            recoService.failToRecognize(results)
        }

        recoService.recognizedSpeechResult(results)
    }

}