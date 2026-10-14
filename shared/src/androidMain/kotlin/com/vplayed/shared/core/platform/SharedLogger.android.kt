package com.vplayed.shared.core.platform

import android.util.Log

private object AndroidLogger : SharedLogger {
    override fun debug(tag: String, message: String) {
        Log.d(tag, message)
    }

    override fun info(tag: String, message: String) {
        Log.i(tag, message)
    }

    override fun error(tag: String, message: String, throwable: Throwable?) {
        Log.e(tag, message, throwable)
    }
}

actual fun platformLogger(): SharedLogger = AndroidLogger
