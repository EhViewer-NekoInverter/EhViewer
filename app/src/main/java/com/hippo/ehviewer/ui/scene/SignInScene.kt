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
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.TextView
import android.widget.TextView.OnEditorActionListener
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import com.google.android.material.textfield.TextInputLayout
import com.hippo.ehviewer.R
import com.hippo.ehviewer.Settings
import com.hippo.ehviewer.UrlOpener
import com.hippo.ehviewer.client.EhCookieStore
import com.hippo.ehviewer.client.EhEngine
import com.hippo.ehviewer.client.EhUrl
import com.hippo.ehviewer.client.EhUtils
import com.hippo.scene.Announcer
import com.hippo.util.ExceptionUtils
import com.hippo.util.launchIO
import com.hippo.util.withUIContext
import com.hippo.yorozuya.ViewUtils
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class SignInScene :
    SolidScene(),
    OnEditorActionListener,
    View.OnClickListener {
    private var mProgress: View? = null
    private var mUsernameLayout: TextInputLayout? = null
    private var mPasswordLayout: TextInputLayout? = null
    private var mUsername: EditText? = null
    private var mPassword: EditText? = null
    private var mRegister: View? = null
    private var mSignIn: View? = null
    private var mSignInViaWebView: TextView? = null
    private var mSignInViaCookies: TextView? = null
    private var mSkipSigningIn: TextView? = null
    private var mSignInJob: Job? = null

    override fun needShowLeftDrawer(): Boolean = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        val view = inflater.inflate(R.layout.scene_login, container, false)
        val loginForm = ViewUtils.`$$`(view, R.id.login_form)
        mProgress = ViewUtils.`$$`(view, R.id.progress)
        mUsernameLayout = ViewUtils.`$$`(loginForm, R.id.username_layout) as TextInputLayout
        mUsername = mUsernameLayout!!.editText!!
        mPasswordLayout = ViewUtils.`$$`(loginForm, R.id.password_layout) as TextInputLayout
        mPassword = mPasswordLayout!!.editText!!
        mRegister = ViewUtils.`$$`(loginForm, R.id.register)
        mSignIn = ViewUtils.`$$`(loginForm, R.id.sign_in)
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
        mPassword!!.setOnEditorActionListener(this)
        mRegister!!.setOnClickListener(this)
        mSignIn!!.setOnClickListener(this)
        mSignInViaWebView!!.setOnClickListener(this)
        mSignInViaCookies!!.setOnClickListener(this)
        mSkipSigningIn!!.setOnClickListener(this)
        return view
    }

    override fun onDestroyView() {
        super.onDestroyView()
        mProgress = null
        mUsernameLayout = null
        mPasswordLayout = null
        mUsername = null
        mPassword = null
        mRegister = null
        mSignIn = null
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

    private fun hideProgress() {
        mProgress?.visibility = View.GONE
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
            mSignIn ->
                signIn()
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

    override fun onEditorAction(v: TextView, actionId: Int, event: KeyEvent?): Boolean {
        if (v == mPassword) {
            if (actionId == EditorInfo.IME_ACTION_DONE || actionId == EditorInfo.IME_NULL) {
                signIn()
                return true
            }
        }
        return false
    }

    private fun signIn() {
        if (mSignInJob?.isActive == true ||
            mUsername == null ||
            mPassword == null ||
            mUsernameLayout == null ||
            mPasswordLayout == null
        ) {
            return
        }
        val username = mUsername!!.text.toString()
        val password = mPassword!!.text.toString()
        if (username.isEmpty()) {
            mUsernameLayout!!.error = getString(R.string.error_username_cannot_empty)
            return
        } else {
            mUsernameLayout!!.error = null
        }
        if (password.isEmpty()) {
            mPasswordLayout!!.error = getString(R.string.error_password_cannot_empty)
            return
        } else {
            mPasswordLayout!!.error = null
        }
        hideSoftInput()
        showProgress()
        mSignInJob = viewLifecycleOwner.lifecycleScope.launchIO {
            EhUtils.signOut()
            runCatching {
                EhEngine.signIn(username, password)
            }.onFailure {
                it.printStackTrace()
                withUIContext {
                    hideProgress()
                    showResultErrorDialog(it)
                }
            }.onSuccess {
                getProfile()
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

    private fun showResultErrorDialog(e: Throwable) {
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.sign_in_failed)
            .setMessage("${ExceptionUtils.getReadableString(e)}\n\n${getString(R.string.sign_in_failed_tip)}")
            .setPositiveButton(R.string.get_it, null)
            .show()
    }

    companion object {
        private const val REQUEST_CODE_COOKIE = 0
        private const val REQUEST_CODE_WEBVIEW = 1
    }
}
