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

    private const val MAX_LOG_LENGTH = 2000

    private inline fun log(tag: String, message: String, logFn: (String, String) -> Unit) {
        var start = 0
        val len = message.length
        while (start < len) {
            val remaining = len - start
            if (remaining <= MAX_LOG_LENGTH) {
                val chunk = message.substring(start)
                logFn(tag, chunk)
                println("$tag: $chunk")
                break
            }
            val targetEnd = start + MAX_LOG_LENGTH
            val newline = message.lastIndexOf('\n', targetEnd)
            val end = if (newline > start) newline + 1 else targetEnd
            val chunk = message.substring(start, end)
            logFn(tag, chunk)
            println("$tag: $chunk")
            start = end
        }
    }
}

actual fun platformLogger(): SharedLogger = AndroidLogger
