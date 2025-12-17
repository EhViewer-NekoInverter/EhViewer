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
import java.util.Locale
import kotlinx.coroutines.Job
import okhttp3.Cookie

class CookieSignInScene :
    SolidScene(),
    OnEditorActionListener,
    View.OnClickListener {
    private var mProgress: View? = null
    private var mIpbMemberIdLayout: TextInputLayout? = null
    private var mIpbPassHashLayout: TextInputLayout? = null
    private var mIpbMemberId: EditText? = null
    private var mIpbPassHash: EditText? = null
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
        mIpbMemberId = null
        mIpbPassHash = null
        mSignInJob = null
    }

    private fun showProgress() {
        if (mProgress?.visibility == View.VISIBLE) return
        mProgress?.apply {
            alpha = 0f
            visibility = View.VISIBLE
            animate().alpha(1f).setDuration(500).start()
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
        if (mSignInJob?.isActive == true) return
        val memberIdField = mIpbMemberId ?: return
        val passHashField = mIpbPassHash ?: return
        val memberIdLayout = mIpbMemberIdLayout ?: return
        val passHashLayout = mIpbPassHashLayout ?: return
        val memberId = memberIdField.text.toString().trim()
        val passHash = passHashField.text.toString().trim()

        if (memberId.isEmpty()) {
            memberIdLayout.error = getString(R.string.text_is_empty)
            return
        } else {
            memberIdLayout.error = null
        }
        if (passHash.isEmpty()) {
            passHashLayout.error = getString(R.string.text_is_empty)
            return
        } else {
            passHashLayout.error = null
        }
        hideSoftInput()
        showProgress()

        mSignInJob = viewLifecycleOwner.lifecycleScope.launchIO {
            EhUtils.signOut()
            val result = runCatching {
                storeCookie(memberId, passHash)
                EhEngine.getProfile().also {
                    Settings.putDisplayName(it.displayName)
                    Settings.putAvatar(it.avatar)
                }
            }
            withUIContext {
                hideProgress()
                result.onSuccess {
                    setResult(RESULT_OK, null)
                    finish()
                }.onFailure {
                    showResultErrorDialog(it)
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

    private suspend fun storeCookie(id: String, hash: String) {
        fun newCookie(name: String, value: String, domain: String): Cookie = Cookie.Builder().name(name).value(value)
            .domain(domain).expiresAt(Long.MAX_VALUE).build()
        EhCookieStore.addCookie(newCookie(EhCookieStore.KEY_IPB_MEMBER_ID, id, EhUrl.DOMAIN_E))
        EhCookieStore.addCookie(newCookie(EhCookieStore.KEY_IPB_MEMBER_ID, id, EhUrl.DOMAIN_EX))
        EhCookieStore.addCookie(newCookie(EhCookieStore.KEY_IPB_PASS_HASH, hash, EhUrl.DOMAIN_E))
        EhCookieStore.addCookie(newCookie(EhCookieStore.KEY_IPB_PASS_HASH, hash, EhUrl.DOMAIN_EX))
    }

    private fun fillCookiesFromClipboard() {
        val context = requireContext()
        fun showClipboardError() = showTip(R.string.from_clipboard_error, LENGTH_SHORT)
        hideSoftInput()

        val clipboardText = context.getClipboardManager().getTextFromClipboard(context)
        if (clipboardText.isNullOrBlank()) {
            showClipboardError()
            return
        }
        val kvs = when {
            clipboardText.contains(";") -> clipboardText.split(";")
            clipboardText.contains("\n") -> clipboardText.split("\n")
            else -> {
                showClipboardError()
                return
            }
        }.map { it.trim() }.filter { it.isNotEmpty() }
        val hasRequiredKeys = clipboardText.contains(EhCookieStore.KEY_IPB_MEMBER_ID) &&
            clipboardText.contains(EhCookieStore.KEY_IPB_PASS_HASH)
        if (!hasRequiredKeys || kvs.size < 2) {
            showClipboardError()
            return
        }

        try {
            kvs.forEach { entry ->
                val kv = when {
                    entry.contains("=") -> entry.split("=")
                    entry.contains(":") -> entry.split(":")
                    else -> return@forEach
                }
                if (kv.size != 2) return@forEach

                val key = kv[0].trim().lowercase(Locale.getDefault())
                val value = kv[1].trim().replace(Regex("[^a-zA-Z0-9\\-_.~]"), "")
                when (key) {
                    EhCookieStore.KEY_IPB_MEMBER_ID -> mIpbMemberId?.setText(value)
                    EhCookieStore.KEY_IPB_PASS_HASH -> mIpbPassHash?.setText(value)
                }
            }
            enter()
        } catch (e: Exception) {
            ExceptionUtils.throwIfFatal(e)
            e.printStackTrace()
            showClipboardError()
        }
    }
}
