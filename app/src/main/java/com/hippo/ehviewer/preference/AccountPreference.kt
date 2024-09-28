/*
 * Copyright 2018 Hippo Seven
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
package com.hippo.ehviewer.preference

import android.content.Context
import android.content.DialogInterface
import android.util.AttributeSet
import android.view.View
import android.view.WindowManager
import android.widget.Button
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import com.hippo.ehviewer.R
import com.hippo.ehviewer.client.EhCookieStore
import com.hippo.ehviewer.client.EhEngine
import com.hippo.ehviewer.client.EhUrl
import com.hippo.ehviewer.client.EhUtils
import com.hippo.ehviewer.ui.SettingsActivity
import com.hippo.ehviewer.ui.scene.BaseScene
import com.hippo.preference.DialogPreference
import com.hippo.util.ReadableTime
import com.hippo.util.addTextToClipboard
import com.hippo.util.launchIO
import com.hippo.util.withUIContext
import okhttp3.HttpUrl.Companion.toHttpUrl

class AccountPreference(
    context: Context,
    attrs: AttributeSet? = null,
) : DialogPreference(context, attrs), View.OnClickListener {
    private val mActivity = context as SettingsActivity
    private var mCookie: String? = null
    private var mMessage: String? = context.getString(R.string.settings_eh_account_name_tourist)
    private lateinit var mDialog: AlertDialog
    private lateinit var refreshButton: Button

    private fun updateMessage() {
        if (EhCookieStore.hasSignedIn()) {
            var ipbMemberId: String? = null
            var ipbPassHash: String? = null
            var igneous: String? = null
            var igneousExpire = 0L

            EhCookieStore.getCookies(EhUrl.HOST_EX.toHttpUrl()).forEach {
                when (it.name) {
                    EhCookieStore.KEY_IPB_MEMBER_ID -> ipbMemberId = it.value
                    EhCookieStore.KEY_IPB_PASS_HASH -> ipbPassHash = it.value
                    EhCookieStore.KEY_IGNEOUS -> {
                        igneous = it.value
                        igneousExpire = it.expiresAt
                    }
                }
            }
            mCookie = EhCookieStore.KEY_IPB_MEMBER_ID + ": " + ipbMemberId +
                "\n" + EhCookieStore.KEY_IPB_PASS_HASH + ": " + ipbPassHash
            igneous?.let { mCookie += "\n" + EhCookieStore.KEY_IGNEOUS + ": " + it }
            mMessage = context.getString(R.string.settings_eh_account_identity_cookies, mCookie)
            if (igneousExpire > 0 && igneousExpire != ReadableTime.MAX_VALUE_MILLIS) {
                mMessage += "\n" + context.getString(R.string.settings_eh_account_igneous_expire) +
                    ReadableTime.getShortTime(igneousExpire)
            }
            mDialog.setMessage(mMessage)
        }
    }

    override fun onPrepareDialogBuilder(builder: AlertDialog.Builder) {
        super.onPrepareDialogBuilder(builder)
        if (EhCookieStore.hasSignedIn()) {
            builder.setNeutralButton(R.string.settings_eh_account_identity_cookies_copy) { dialog: DialogInterface, which: Int ->
                mActivity.addTextToClipboard(mCookie, true)
                this@AccountPreference.onClick(dialog, which)
            }
            builder.setNegativeButton(R.string.settings_eh_account_refresh_igneous, null)
        }
        builder.setPositiveButton(R.string.settings_eh_account_sign_out) { _: DialogInterface, _: Int ->
            EhUtils.signOut()
            mActivity.showTip(R.string.settings_eh_account_sign_out_tip, BaseScene.LENGTH_SHORT)
        }
        builder.setMessage(mMessage)
    }

    override fun onDialogCreated(dialog: AlertDialog) {
        super.onDialogCreated(dialog)
        mDialog = dialog
        refreshButton = dialog.getButton(DialogInterface.BUTTON_NEGATIVE)
        refreshButton.setOnClickListener(this)
        mDialog.window!!.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        updateMessage()
    }

    override fun onClick(v: View) {
        refreshButton.isEnabled = false
        mActivity.lifecycleScope.launchIO {
            EhCookieStore.deleteCookie(
                EhUrl.HOST_EX.toHttpUrl(),
                EhCookieStore.KEY_IGNEOUS,
            )
            runCatching { EhEngine.getUConfig(EhUrl.URL_UCONFIG_EX) }
            withUIContext {
                updateMessage()
                refreshButton.isEnabled = true
            }
        }
    }
}
