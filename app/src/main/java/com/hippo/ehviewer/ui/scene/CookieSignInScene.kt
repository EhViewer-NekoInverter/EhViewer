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
import android.text.TextUtils
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import android.widget.TextView.OnEditorActionListener
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import com.google.android.material.textfield.TextInputLayout
import com.hippo.ehviewer.R
import com.hippo.ehviewer.Settings
import com.hippo.ehviewer.client.EhCookieStore
import com.hippo.ehviewer.client.EhEngine
import com.hippo.ehviewer.client.EhUrl
import com.hippo.ehviewer.client.EhUtils
import com.hippo.util.ExceptionUtils
import com.hippo.util.getClipboardManager
import com.hippo.util.getTextFromClipboard
import com.hippo.util.launchIO
import com.hippo.util.withUIContext
import com.hippo.yorozuya.ViewUtils
import kotlinx.coroutines.Job
import okhttp3.Cookie
import java.util.Locale

class CookieSignInScene :
    SolidScene(),
    OnEditorActionListener,
    View.OnClickListener {
    private var mProgress: View? = null
    private var mIpbMemberIdLayout: TextInputLayout? = null
    private var mIpbPassHashLayout: TextInputLayout? = null
    private var mIgneousLayout: TextInputLayout? = null
    private var mIpbMemberId: EditText? = null
    private var mIpbPassHash: EditText? = null
    private var mIgneous: EditText? = null
    private var mOk: View? = null
    private var mFromClipboard: TextView? = null
    private var mSignInJob: Job? = null

    override fun needShowLeftDrawer(): Boolean = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        val view = inflater.inflate(R.layout.scene_cookie_sign_in, container, false)
        val loginForm = ViewUtils.`$$`(view, R.id.cookie_signin_form)
        mProgress = ViewUtils.`$$`(view, R.id.progress)
        mIpbMemberIdLayout = ViewUtils.`$$`(loginForm, R.id.ipb_member_id_layout) as TextInputLayout
        mIpbMemberId = mIpbMemberIdLayout!!.editText!!
        mIpbPassHashLayout = ViewUtils.`$$`(loginForm, R.id.ipb_pass_hash_layout) as TextInputLayout
        mIpbPassHash = mIpbPassHashLayout!!.editText!!
        mIgneousLayout = ViewUtils.`$$`(loginForm, R.id.igneous_layout) as TextInputLayout
        mIgneous = mIgneousLayout!!.editText!!
        mOk = ViewUtils.`$$`(loginForm, R.id.ok)
        mFromClipboard = ViewUtils.`$$`(loginForm, R.id.from_clipboard) as TextView
        mFromClipboard!!.run {
            paintFlags = paintFlags or Paint.UNDERLINE_TEXT_FLAG or Paint.ANTI_ALIAS_FLAG
        }
        mIpbPassHash!!.setOnEditorActionListener(this)
        mOk!!.setOnClickListener(this)
        mFromClipboard!!.setOnClickListener(this)
        return view
    }

    override fun onDestroyView() {
        super.onDestroyView()
        mProgress = null
        mIpbMemberIdLayout = null
        mIpbPassHashLayout = null
        mIgneousLayout = null
        mIpbMemberId = null
        mIpbPassHash = null
        mIgneous = null
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

    override fun onClick(v: View) {
        when (v) {
            mOk -> enter()
            mFromClipboard -> fillCookiesFromClipboard()
        }
    }

    override fun onEditorAction(v: TextView, actionId: Int, event: KeyEvent?): Boolean {
        if (mIpbPassHash === v) {
            enter()
        }
        return true
    }

    fun enter() {
        if (mSignInJob?.isActive == true ||
            null == mIpbMemberIdLayout ||
            null == mIpbPassHashLayout ||
            null == mIgneousLayout ||
            null == mIpbMemberId ||
            null == mIpbPassHash ||
            null == mIgneous
        ) {
            return
        }
        val ipbMemberId = mIpbMemberId!!.text.toString().trim { it <= ' ' }
        val ipbPassHash = mIpbPassHash!!.text.toString().trim { it <= ' ' }
        val igneous = mIgneous!!.text.toString().trim { it <= ' ' }
        if (TextUtils.isEmpty(ipbMemberId)) {
            mIpbMemberIdLayout!!.error = getString(R.string.text_is_empty)
            return
        } else {
            mIpbMemberIdLayout!!.error = null
        }
        if (TextUtils.isEmpty(ipbPassHash)) {
            mIpbPassHashLayout!!.error = getString(R.string.text_is_empty)
            return
        } else {
            mIpbPassHashLayout!!.error = null
        }
        hideSoftInput()
        showProgress()
        mSignInJob = viewLifecycleOwner.lifecycleScope.launchIO {
            EhUtils.signOut()
            runCatching {
                storeCookie(ipbMemberId, ipbPassHash, igneous)
                EhEngine.getProfile().run {
                    Settings.putDisplayName(displayName)
                    Settings.putAvatar(avatar)
                }
            }.onFailure {
                withUIContext {
                    hideProgress()
                    showResultErrorDialog(it)
                }
            }.onSuccess {
                withUIContext {
                    setResult(RESULT_OK, null)
                    finish()
                }
            }
        }
    }

    private fun showResultErrorDialog(e: Throwable) {
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.sign_in_failed)
            .setMessage("${ExceptionUtils.getReadableString(e)}\n\n${getString(R.string.wrong_cookie_warning)}")
            .setPositiveButton(R.string.get_it, null)
            .show()
    }

    private suspend fun storeCookie(id: String, hash: String, igneous: String) {
        EhCookieStore.addCookie(newCookie(EhCookieStore.KEY_IPB_MEMBER_ID, id, EhUrl.DOMAIN_E))
        EhCookieStore.addCookie(newCookie(EhCookieStore.KEY_IPB_MEMBER_ID, id, EhUrl.DOMAIN_EX))
        EhCookieStore.addCookie(newCookie(EhCookieStore.KEY_IPB_PASS_HASH, hash, EhUrl.DOMAIN_E))
        EhCookieStore.addCookie(newCookie(EhCookieStore.KEY_IPB_PASS_HASH, hash, EhUrl.DOMAIN_EX))
        if (igneous.isNotEmpty()) {
            EhCookieStore.addCookie(newCookie(EhCookieStore.KEY_IGNEOUS, igneous, EhUrl.DOMAIN_EX))
        }
    }

    private fun fillCookiesFromClipboard() {
        val context = requireContext()
        hideSoftInput()
        val text = context.getClipboardManager().getTextFromClipboard(context)
        if (text == null) {
            showTip(R.string.from_clipboard_error, LENGTH_SHORT)
            return
        }
        try {
            val kvs: Array<String> = if (text.contains(";")) {
                text.split(";".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
            } else if (text.contains("\n")) {
                text.split("\n".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
            } else {
                showTip(R.string.from_clipboard_error, LENGTH_SHORT)
                return
            }
            if (kvs.size >= 2 &&
                text.contains(EhCookieStore.KEY_IPB_MEMBER_ID) &&
                text.contains(EhCookieStore.KEY_IPB_PASS_HASH)
            ) {
                for (s in kvs) {
                    val kv: Array<String> = if (s.contains("=")) {
                        s.split("=".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                    } else if (s.contains(":")) {
                        s.split(":".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                    } else {
                        continue
                    }
                    if (kv.size != 2) {
                        continue
                    }
                    when (kv[0].trim().lowercase(Locale.getDefault())) {
                        EhCookieStore.KEY_IPB_MEMBER_ID -> mIpbMemberId?.setText(kv[1].trim())
                        EhCookieStore.KEY_IPB_PASS_HASH -> mIpbPassHash?.setText(kv[1].trim())
                        EhCookieStore.KEY_IGNEOUS -> mIgneous?.setText(kv[1].trim())
                    }
                }
                enter()
            } else {
                showTip(R.string.from_clipboard_error, LENGTH_SHORT)
            }
        } catch (e: Exception) {
            ExceptionUtils.throwIfFatal(e)
            e.printStackTrace()
            showTip(R.string.from_clipboard_error, LENGTH_SHORT)
        }
    }

    companion object {
        private fun newCookie(name: String, value: String, domain: String): Cookie = Cookie.Builder().name(name).value(value)
            .domain(domain).expiresAt(Long.MAX_VALUE).build()
    }
}
