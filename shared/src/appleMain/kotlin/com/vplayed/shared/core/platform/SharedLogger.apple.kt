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
        var start = 0
        val len = message.length
        while (start < len) {
            val remaining = len - start
            if (remaining <= MAX_LOG_LENGTH) {
                val chunk = message.substring(start)
                println("[$level/$tag] $chunk")
                NSLog("%@", "[$level/$tag] $chunk")
                break
            }
            val targetEnd = start + MAX_LOG_LENGTH
            val newline = message.lastIndexOf('\n', targetEnd)
            val end = if (newline > start) newline + 1 else targetEnd
            val chunk = message.substring(start, end)
            println("[$level/$tag] $chunk")
            NSLog("%@", "[$level/$tag] $chunk")
            start = end
        }
    }
}

actual fun platformLogger(): SharedLogger = AppleLogger
