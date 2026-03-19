package com.hippo.ehviewer.util

import android.annotation.SuppressLint
import android.webkit.WebSettings
import android.webkit.WebView
import androidx.webkit.WebViewCompat
import com.hippo.ehviewer.EhApplication
import com.hippo.ehviewer.Settings

private const val MINIMUM_WEBVIEW_VERSION = 118
val WebViewVersion = run {
    val context = EhApplication.application
    val uaVersion = runCatching {
        Regex("Chrome/(\\d+)")
            .find(WebSettings.getDefaultUserAgent(context))
            ?.groupValues
            ?.get(1)
            ?.toIntOrNull()
    }.getOrNull()
    val pkgVersion = WebViewCompat
        .getCurrentWebViewPackage(context)
        ?.versionName
        ?.substringBefore('.')
        ?.toIntOrNull()
    uaVersion ?: pkgVersion ?: MINIMUM_WEBVIEW_VERSION
}
val isWebViewOutdated = WebViewVersion < MINIMUM_WEBVIEW_VERSION

@SuppressLint("SetJavaScriptEnabled")
fun WebView.setDefaultSettings() = settings.run {
    builtInZoomControls = true
    displayZoomControls = false
    javaScriptEnabled = true
    domStorageEnabled = true
    userAgentString = Settings.userAgent!!
}
