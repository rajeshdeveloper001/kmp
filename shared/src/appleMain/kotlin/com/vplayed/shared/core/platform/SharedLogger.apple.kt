package com.vplayed.shared.core.platform

import platform.Foundation.NSLog

private object AppleLogger : SharedLogger {
    override fun debug(tag: String, message: String) = log("D", tag, message)

    override fun info(tag: String, message: String) = log("I", tag, message)

    override fun error(tag: String, message: String, throwable: Throwable?) =
        log("E", tag, if (throwable == null) message else "$message: ${throwable::class.simpleName}")

    // The message goes in as an argument, never as the format string.
    private fun log(level: String, tag: String, message: String) = NSLog("%@", "[$level/$tag] $message")
}

actual fun platformLogger(): SharedLogger = AppleLogger
