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

import android.graphics.Paint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import com.hippo.ehviewer.R
import com.hippo.ehviewer.Settings
import com.hippo.ehviewer.UrlOpener
import com.hippo.ehviewer.client.EhCookieStore
import com.hippo.ehviewer.client.EhEngine
import com.hippo.ehviewer.client.EhUrl
import com.hippo.scene.Announcer
import com.hippo.util.launchIO
import com.hippo.util.withUIContext
import com.hippo.yorozuya.ViewUtils
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class SignInScene : SolidScene(), View.OnClickListener {
    private var mProgress: View? = null
    private var mRegister: View? = null
    private var mSignInViaWebView: TextView? = null
    private var mSignInViaCookies: TextView? = null
    private var mSkipSigningIn: TextView? = null
    private var mSignInJob: Job? = null

    override fun needShowLeftDrawer(): Boolean {
        return false
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        val view = inflater.inflate(R.layout.scene_login, container, false)
        val loginForm = ViewUtils.`$$`(view, R.id.login_form)
        mProgress = ViewUtils.`$$`(view, R.id.progress)
        mRegister = ViewUtils.`$$`(loginForm, R.id.register)
        mSignInViaWebView = ViewUtils.`$$`(loginForm, R.id.sign_in_via_webview) as TextView
        mSignInViaCookies = ViewUtils.`$$`(loginForm, R.id.sign_in_via_cookies) as TextView
        mSkipSigningIn = ViewUtils.`$$`(loginForm, R.id.tourist_mode) as TextView
        mSignInViaWebView!!.run {
            paintFlags = paintFlags or Paint.UNDERLINE_TEXT_FLAG or Paint.ANTI_ALIAS_FLAG
        }
        mSignInViaCookies!!.run {
            paintFlags = paintFlags or Paint.UNDERLINE_TEXT_FLAG or Paint.ANTI_ALIAS_FLAG
        }
        mSkipSigningIn!!.run {
            paintFlags = paintFlags or Paint.UNDERLINE_TEXT_FLAG or Paint.ANTI_ALIAS_FLAG
        }
        mRegister!!.setOnClickListener(this)
        mSignInViaWebView!!.setOnClickListener(this)
        mSignInViaCookies!!.setOnClickListener(this)
        mSkipSigningIn!!.setOnClickListener(this)
        return view
    }

    override fun onDestroyView() {
        super.onDestroyView()
        mProgress = null
        mRegister = null
        mSignInViaWebView = null
        mSignInViaCookies = null
        mSkipSigningIn = null
        mSignInJob = null
    }

    private fun showProgress() {
        if (null != mProgress && View.VISIBLE != mProgress!!.visibility) {
            mProgress!!.run {
                alpha = 0.0f
                visibility = View.VISIBLE
                animate().alpha(1.0f).setDuration(500).start()
            }
        }
    }

    override fun onSceneResult(requestCode: Int, resultCode: Int, data: Bundle?) {
        when (requestCode) {
            REQUEST_CODE_WEBVIEW -> if (resultCode == RESULT_OK) {
                getProfile()
            }
            REQUEST_CODE_COOKIE -> if (resultCode == RESULT_OK) {
                finishSignIn()
            }
            else -> super.onSceneResult(requestCode, resultCode, data)
        }
    }

    override fun onClick(v: View) {
        val activity = mainActivity ?: return
        when (v) {
            mRegister ->
                UrlOpener.openUrl(activity, EhUrl.URL_REGISTER, false)
            mSignInViaCookies ->
                startScene(Announcer(CookieSignInScene::class.java).setRequestCode(this, REQUEST_CODE_COOKIE))
            mSignInViaWebView ->
                startScene(Announcer(WebViewSignInScene::class.java).setRequestCode(this, REQUEST_CODE_WEBVIEW))
            mSkipSigningIn -> {
                // Set gallery size SITE_E if skip sign in
                Settings.putGallerySite(EhUrl.SITE_E)
                Settings.putSelectSite(false)
                finishSignIn(false)
            }
        }
    }

    private fun getProfile() {
        lifecycleScope.launchIO {
            withUIContext {
                showProgress()
            }
            runCatching {
                EhEngine.getProfile().run {
                    Settings.putDisplayName(displayName)
                    Settings.putAvatar(avatar)
                }
            }
            finishSignIn()
        }
    }

    private fun finishSignIn(signedIn: Boolean = true) {
        lifecycleScope.launchIO {
            withUIContext {
                showProgress()
            }
            if (signedIn) {
                runCatching {
                    // For the `star` cookie, https://github.com/Ehviewer-Overhauled/Ehviewer/issues/873
                    EhEngine.getNews(false)
                    EhCookieStore.copyCookie(EhUrl.DOMAIN_E, EhUrl.DOMAIN_EX, EhCookieStore.KEY_STAR)

                    // Get cookies for image limits
                    launch { runCatching { EhEngine.getUConfig(EhUrl.URL_UCONFIG_E) } }

                    // Sad panda check
                    EhEngine.getUConfig(EhUrl.URL_UCONFIG_EX)
                }.onFailure {
                    Settings.putGallerySite(EhUrl.SITE_E)
                    Settings.putSelectSite(false)
                }
            }
            withUIContext {
                Settings.putNeedSignIn(false)
                updateAvatar()
                if (null != mainActivity) {
                    startSceneForCheckStep(CHECK_STEP_SIGN_IN, arguments)
                }
                finish()
            }
        }
    }

    companion object {
        private const val REQUEST_CODE_COOKIE = 0
        private const val REQUEST_CODE_WEBVIEW = 1
    }
}
