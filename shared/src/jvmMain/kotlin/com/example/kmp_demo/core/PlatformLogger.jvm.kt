package com.example.kmp_demo.core

actual object PlatformLogger {
    actual fun d(tag: String, message: String) {
        if (LOG_ENABLED) println("DEBUG [$tag] $message")
    }

    actual fun i(tag: String, message: String) {
        if (LOG_ENABLED) println("INFO  [$tag] $message")
    }

    actual fun w(tag: String, message: String) {
        if (LOG_ENABLED) println("WARN  [$tag] $message")
    }

    actual fun e(tag: String, message: String) {
        if (LOG_ENABLED) System.err.println("ERROR [$tag] $message")
    }

    actual fun e(tag: String, message: String, throwable: Throwable?) {
        if (LOG_ENABLED) {
            System.err.println("ERROR [$tag] $message")
            throwable?.printStackTrace()
        }
    }
}
