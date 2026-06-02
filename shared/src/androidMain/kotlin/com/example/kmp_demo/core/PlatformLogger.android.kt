package com.example.kmp_demo.core

import android.util.Log

actual object PlatformLogger {
    actual fun d(tag: String, message: String) {
        if (LOG_ENABLED) Log.d(tag, message)
    }

    actual fun i(tag: String, message: String) {
        if (LOG_ENABLED) Log.i(tag, message)
    }

    actual fun w(tag: String, message: String) {
        if (LOG_ENABLED) Log.w(tag, message)
    }

    actual fun e(tag: String, message: String) {
        if (LOG_ENABLED) Log.e(tag, message)
    }

    actual fun e(tag: String, message: String, throwable: Throwable?) {
        if (LOG_ENABLED) Log.e(tag, message, throwable)
    }
}
