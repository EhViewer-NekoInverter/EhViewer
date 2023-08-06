package com.hippo.ehviewer.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.webkit.WebView
import android.webkit.WebViewClient
import com.hippo.ehviewer.client.EhCookieStore
import com.hippo.ehviewer.util.setDefaultSettings

class WebViewActivity : EhActivity() {
    private var webView: WebView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val url = intent.extras?.getString(KEY_URL) ?: return
        webView = WebView(applicationContext).apply {
            setDefaultSettings()
            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView, url: String) {
                    val cloudflareBypassed = EhCookieStore.saveFromWebView(url) {
                        it.name == EhCookieStore.KEY_CLOUDFLARE
                    }
                    if (cloudflareBypassed) {
                        finish()
                    }
                }
            }
        }
        setContentView(webView)
        EhCookieStore.loadForWebView(url) {
            it.name != EhCookieStore.KEY_CLOUDFLARE
        }
        webView!!.loadUrl(url)
    }

    override fun onDestroy() {
        super.onDestroy()
        webView?.destroy()
        webView = null
    }

    companion object {
        const val KEY_URL = "url"

        fun newIntent(context: Context, url: String): Intent {
            return Intent(context, WebViewActivity::class.java).apply {
                putExtra(KEY_URL, url)
            }
        }
    }
}
