package com.hippo.util;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.util.Log;

import androidx.webkit.WebViewCompat;

public class WebViewUtil {
    private static final String TAG = "WebViewUtil";

    public static boolean available(Context context) {
        PackageInfo webViewPackageInfo = WebViewCompat.getCurrentWebViewPackage(context);
        if (webViewPackageInfo == null) {
            Log.d(TAG, "WebView not available");
            return false;
        }
        Log.d(TAG, "WebView version: " + webViewPackageInfo.versionName);
        return true;
    }
}
