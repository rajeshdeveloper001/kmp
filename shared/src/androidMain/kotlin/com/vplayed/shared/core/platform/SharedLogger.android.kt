package com.vplayed.shared.core.platform

import android.util.Log

private object AndroidLogger : SharedLogger {
    override fun debug(tag: String, message: String) = log(tag, message) { t, m -> Log.d(t, m) }

    override fun info(tag: String, message: String) = log(tag, message) { t, m -> Log.i(t, m) }

    override fun error(tag: String, message: String, throwable: Throwable?) {
        if (throwable != null) {
            Log.e(tag, message, throwable)
        } else {
            log(tag, message) { t, m -> Log.e(t, m) }
        }
    }

    private const val MAX_LOG_LENGTH = 4000

    private inline fun log(tag: String, message: String, logFn: (String, String) -> Unit) {
        if (message.length <= MAX_LOG_LENGTH) {
            logFn(tag, message)
            return
        }
        var start = 0
        while (start < message.length) {
            var end = (start + MAX_LOG_LENGTH).coerceAtMost(message.length)
            val newline = message.lastIndexOf('\n', end)
            if (newline > start && end < message.length) {
                end = newline + 1
            }
            logFn(tag, message.substring(start, end))
            start = end
        }
    }
}

actual fun platformLogger(): SharedLogger = AndroidLogger
