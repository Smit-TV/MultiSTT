package com.github.smittv.multistt.engines

/*
*  Each recognition engine must use this callback to notify recognition service about recognition progress
 */
open class ListenCallback {
    class Results(
        val isSuccessful: Boolean,
        val result: String?,
        val error: ResultError
    ) {
        enum class ResultError {
            NO_ERROR,
            ERROR_UNKNOWN_ERROR,
            ERROR_FAIL_TO_RECOGNIZE_TOO_MANY_REQUESTS,
            ERROR_RUNTIME_ERROR,
            ERROR_SERVICE_ERROR,
            ERROR_EMBEDDED_MODEL_ERROR,
            ERROR_SERVICE_TIMEOUT_ERROR,
            ERROR_AUTHENTICATION_ERROR,
            ERROR_BAD_REQUEST_ERROR,
            ERROR_CONNECTION_FAILURE_ERROR,
            ERROR_FORBIDDEN_ERROR,
            ERROR_SERVICE_REDIRECT_PERMANENT_ERROR,
            ERROR_SERVICE_REDIRECT_TEMPORARY_ERROR,
            ERROR_SERVICE_UNAVAILABLE_ERROR
        }
    }

    open fun onStartListening() {}
    open fun onStopListening(cancelled: Boolean, results: Results) {}
    open fun close() {}
}