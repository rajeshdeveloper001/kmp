package com.vplayed.shared.core.platform

/**
 * Logging used by shared code. Messages never contain tokens, codes, emails, request bodies or
 * other personal data; callers are responsible for that.
 */
interface SharedLogger {
    fun debug(tag: String, message: String)

    fun info(tag: String, message: String)

    fun error(tag: String, message: String, throwable: Throwable? = null)
}

/** Discards everything; the default when an app passes no logger. */
object NoOpLogger : SharedLogger {
    override fun debug(tag: String, message: String) = Unit

    override fun info(tag: String, message: String) = Unit

    override fun error(tag: String, message: String, throwable: Throwable?) = Unit
}

/** The platform's system log (Logcat on Android, the unified log via NSLog on Apple platforms). */
expect fun platformLogger(): SharedLogger
