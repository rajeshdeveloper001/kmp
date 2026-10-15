package com.vplayed.shared.core.platform

import platform.Foundation.NSLog

private object AppleLogger : SharedLogger {
    override fun debug(tag: String, message: String) = log("D", tag, message)

    override fun info(tag: String, message: String) = log("I", tag, message)

    override fun error(tag: String, message: String, throwable: Throwable?) =
        log("E", tag, if (throwable == null) message else "$message: ${throwable::class.simpleName}")

    private const val MAX_LOG_LENGTH = 1000

    // The message goes in as an argument, never as the format string.
    private fun log(level: String, tag: String, message: String) {
        if (message.length <= MAX_LOG_LENGTH) {
            NSLog("%@", "[$level/$tag] $message")
            return
        }
        var start = 0
        while (start < message.length) {
            var end = (start + MAX_LOG_LENGTH).coerceAtMost(message.length)
            val newline = message.lastIndexOf('\n', end)
            if (newline > start && end < message.length) {
                end = newline + 1
            }
            NSLog("%@", "[$level/$tag] ${message.substring(start, end)}")
            start = end
        }
    }
}

actual fun platformLogger(): SharedLogger = AppleLogger
