/*
 * Copyright 2022 Tarsin Norbin
 *
 * This file is part of EhViewer
 *
 * EhViewer is free software: you can redistribute it and/or
 * modify it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * EhViewer is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with EhViewer.
 * If not, see <https://www.gnu.org/licenses/>.
 */
package com.hippo.ehviewer.ui.fragment

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.core.view.MenuProvider
import androidx.lifecycle.Lifecycle
import com.google.android.material.progressindicator.CircularProgressIndicator
import com.hippo.ehviewer.R
import com.hippo.ehviewer.client.EhCookieStore
import com.hippo.ehviewer.client.EhUrl
import com.hippo.ehviewer.ui.scene.BaseScene
import com.hippo.ehviewer.widget.DialogWebChromeClient
import com.hippo.util.launchIO
import kotlinx.coroutines.DelicateCoroutinesApi
import okhttp3.Cookie
import okhttp3.HttpUrl.Companion.toHttpUrl
import rikka.core.res.resolveColor

class UConfigFragment : BaseFragment() {
    private val url = EhUrl.uConfigUrl
    private var webView: WebView? = null
    private var progress: CircularProgressIndicator? = null
    private var loaded = false

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        val view = inflater.inflate(R.layout.activity_webview, container, false)
        webView = view.findViewById(R.id.webview)
        webView!!.run {
            setBackgroundColor(requireActivity().theme.resolveColor(android.R.attr.colorBackground))
            settings.builtInZoomControls = true
            settings.displayZoomControls = false
            settings.javaScriptEnabled = true
            webViewClient = UConfigWebViewClient()
            webChromeClient = DialogWebChromeClient(requireContext())
        }
        progress = view.findViewById(R.id.progress)
        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        progress!!.visibility = View.VISIBLE
        webView!!.loadUrl(url)
        showTip(R.string.apply_tip, BaseScene.LENGTH_LONG)
        requireActivity().addMenuProvider(
            object : MenuProvider {
                override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
                    menuInflater.inflate(R.menu.activity_u_config, menu)
                }

                override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
                    if (menuItem.itemId == R.id.action_apply) {
                        if (loaded) apply()
                        return true
                    }
                    return false
                }
            },
            viewLifecycleOwner,
            Lifecycle.State.RESUMED,
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // http://stackoverflow.com/questions/32284642/how-to-handle-an-uncatched-exception
        val cookieManager = CookieManager.getInstance()
        cookieManager.flush()
        cookieManager.removeAllCookies(null)
        cookieManager.removeSessionCookies(null)

        // Copy cookies from okhttp cookie store to CookieManager
        for (cookie in EhCookieStore.getCookies(url.toHttpUrl())) {
            cookieManager.setCookie(url, cookie.toString())
        }
    }

    private fun apply() {
        webView?.loadUrl("javascript: document.getElementById('apply').children[0].click();")
    }

    private fun longLive(cookie: Cookie): Cookie = Cookie.Builder()
        .name(cookie.name)
        .value(cookie.value)
        .domain(cookie.domain)
        .path(cookie.path)
        .expiresAt(Long.MAX_VALUE)
        .build()

    @OptIn(DelicateCoroutinesApi::class)
    override fun onDestroyView() {
        super.onDestroyView()
        webView?.destroy()
        webView = null

        val cookiesString = CookieManager.getInstance().getCookie(url)
        if (!cookiesString.isNullOrEmpty()) {
            val hostUrl = EhUrl.host.toHttpUrl()
            launchIO {
                EhCookieStore.deleteCookie(hostUrl, EhCookieStore.KEY_SETTINGS_PROFILE)
                for (header in cookiesString.split(";".toRegex()).dropLastWhile { it.isEmpty() }) {
                    Cookie.parse(hostUrl, header)?.let {
                        if (it.name == EhCookieStore.KEY_CLOUDFLARE || it.name == EhCookieStore.KEY_SETTINGS_PROFILE) {
                            EhCookieStore.addCookie(longLive(it))
                        }
                    }
                }
            }
        }
    }

    override fun getFragmentTitle(): Int = R.string.u_config

    private inner class UConfigWebViewClient : WebViewClient() {
        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
            // Never load other urls
            return true
        }

        override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
            progress!!.visibility = View.VISIBLE
            loaded = false
        }

        override fun onPageFinished(view: WebView, url: String) {
            progress!!.visibility = View.GONE
            loaded = true
        }
    }
}
