package com.example.kmp_demo.core

import platform.UIKit.NSLog

actual object PlatformLogger {
    actual fun d(tag: String, message: String) {
        if (LOG_ENABLED) NSLog("[D][$tag] $message")
    }

    actual fun i(tag: String, message: String) {
        if (LOG_ENABLED) NSLog("[I][$tag] $message")
    }

    actual fun w(tag: String, message: String) {
        if (LOG_ENABLED) NSLog("[W][$tag] $message")
    }

    actual fun e(tag: String, message: String) {
        if (LOG_ENABLED) NSLog("[E][$tag] $message")
    }

    actual fun e(tag: String, message: String, throwable: Throwable?) {
        if (LOG_ENABLED) {
            NSLog("[E][$tag] $message")
            throwable?.let { NSLog("[E][$tag] ${it.stackTraceToString()}") }
        }
    }
}
