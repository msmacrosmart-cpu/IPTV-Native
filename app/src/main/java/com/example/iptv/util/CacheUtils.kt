package com.example.iptv.util

import android.content.Context

/** Small storage helpers for the settings screen. */
object CacheUtils {

    fun clear(context: Context): Boolean = try {
        context.cacheDir?.listFiles()?.forEach { it.deleteRecursively() }
        context.externalCacheDir?.listFiles()?.forEach { it.deleteRecursively() }
        true
    } catch (_: Exception) {
        false
    }

    fun approximateSizeKb(context: Context): Long = try {
        fun sizeOf(directory: java.io.File?): Long {
            if (directory == null || !directory.exists()) return 0L
            return directory.walkTopDown().filter { it.isFile }.sumOf { it.length() }
        }
        (sizeOf(context.cacheDir) + sizeOf(context.externalCacheDir)) / 1024
    } catch (_: Exception) {
        0L
    }
}
