/*
 * Copyright 2016 Hippo Seven
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.hippo.ehviewer.ui.scene

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.lifecycle.lifecycleScope
import com.hippo.ehviewer.client.EhCookieStore
import com.hippo.ehviewer.client.EhUrl
import com.hippo.ehviewer.client.EhUtils
import com.hippo.ehviewer.widget.DialogWebChromeClient
import com.hippo.util.launchIO
import com.hippo.util.withUIContext
import okhttp3.Cookie
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import rikka.core.res.resolveColor

class WebViewSignInScene : SolidScene() {
    private var mWebView: WebView? = null

    override fun needShowLeftDrawer(): Boolean {
        return false
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        EhUtils.signOut()

        // http://stackoverflow.com/questions/32284642/how-to-handle-an-uncatched-exception
        CookieManager.getInstance().apply {
            flush()
            removeAllCookies(null)
            removeSessionCookies(null)
        }
        return WebView(requireContext()).apply {
            setBackgroundColor(theme.resolveColor(android.R.attr.colorBackground))
            settings.run {
                builtInZoomControls = true
                displayZoomControls = true
                javaScriptEnabled = true
            }
            webViewClient = LoginWebViewClient()
            webChromeClient = DialogWebChromeClient(context)
            loadUrl(EhUrl.URL_SIGN_IN)
            mWebView = this
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        mWebView?.destroy()
        mWebView = null
    }

    private inner class LoginWebViewClient : WebViewClient() {
        fun parseCookies(url: HttpUrl?, cookieStrings: String?): List<Cookie> {
            if (cookieStrings == null) {
                return emptyList()
            }
            var cookies: MutableList<Cookie>? = null
            val pieces =
                cookieStrings.split(";".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
            for (piece in pieces) {
                val cookie = Cookie.parse(url!!, piece) ?: continue
                if (cookies == null) {
                    cookies = ArrayList()
                }
                cookies.add(cookie)
            }
            return cookies ?: emptyList()
        }

        private fun addCookie(domain: String, cookie: Cookie) {
            EhCookieStore.addCookie(
                EhCookieStore.newCookie(
                    cookie,
                    domain,
                    forcePersistent = true,
                    forceLongLive = true,
                    forceNotHostOnly = true,
                ),
            )
        }

        override fun onPageFinished(view: WebView, url: String) {
            val httpUrl = url.toHttpUrlOrNull() ?: return
            val cookieString = CookieManager.getInstance().getCookie(EhUrl.HOST_E)
            val cookies = parseCookies(httpUrl, cookieString)
            var getId = false
            var getHash = false
            for (cookie in cookies) {
                if (EhCookieStore.KEY_IPB_MEMBER_ID == cookie.name) {
                    getId = true
                } else if (EhCookieStore.KEY_IPB_PASS_HASH == cookie.name) {
                    getHash = true
                }
            }
            if (getId && getHash) {
                viewLifecycleOwner.lifecycleScope.launchIO {
                    cookies.forEach {
                        addCookie(EhUrl.DOMAIN_EX, it)
                        addCookie(EhUrl.DOMAIN_E, it)
                    }
                    withUIContext {
                        setResult(RESULT_OK, null)
                        finish()
                    }
                }
            }
        }
    }
}
