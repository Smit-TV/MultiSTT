package com.github.smittv.multistt.engines

open class ResultCallback {
    open fun onResult(result: String?) {}
    open fun onError(error: ListenCallback.Results.ResultError) {}
}