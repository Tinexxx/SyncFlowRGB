package me.kavishdevar.openrgb.utils

import android.util.Log

// Update with your package name

fun logFullStackTrace(tag: String, throwable: Throwable) {
    Log.e(tag, throwable.message ?: "No error message")
    throwable.stackTrace.forEach { element ->
        Log.e(tag, "    at $element")
    }
    throwable.cause?.let { cause ->
        Log.e(tag, "Caused by:")
        logFullStackTrace(tag, cause)
    }
}