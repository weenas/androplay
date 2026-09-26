package com.weenas.castbay.util

import android.content.Context

/**
 * The installed version, e.g. "1.0.19", read from the package at run time. BuildConfig's
 * VERSION_NAME is a compile-time constant copied into each caller, and an incremental build
 * that only bumps the version left callers showing the old one.
 */
object AppVersion {
    fun name(context: Context): String = runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName
    }.getOrNull() ?: "?"
}
