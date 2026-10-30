package com.github.smittv.multistt

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.util.Log

class SoundManager(
    private val context: Context
) {
    companion object {
        const val TAG = "SoundManager"
    }

    private val soundPool = SoundPool.Builder()
        .setMaxStreams(3)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        )
        .build()

    private val startListeningSoundId = soundPool.load(context, R.raw.start_recognition, 1)
    private val successfulRecognitionSoundId = soundPool.load(context, R.raw.successful_recognition, 1)
    private val failToRecognizeSoundId = soundPool.load(context, R.raw.fail_to_recognize, 1)

    private fun playSound(soundId: Int) {
        Log.i(TAG, "playSound $soundId")

        soundPool.play(
            soundId,
            1f,
            1f,
            1,
            0,
            1f
        )
    }

    fun playSuccessfulRecognitionSound() = playSound(successfulRecognitionSoundId)

    fun playFailToRecognizeSound() = playSound(failToRecognizeSoundId)

    fun playStartListeningSound() = playSound(startListeningSoundId)

    fun close() = soundPool.release()

}