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
import android.text.Html
import android.util.AttributeSet
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import com.hippo.ehviewer.R
import com.hippo.ehviewer.client.EhCookieStore
import com.hippo.ehviewer.client.EhUrl
import com.hippo.ehviewer.client.EhUtils
import com.hippo.ehviewer.ui.SettingsActivity
import com.hippo.ehviewer.ui.scene.BaseScene
import com.hippo.preference.MessagePreference
import com.hippo.util.addTextToClipboard
import com.hippo.util.launchIO
import com.hippo.util.withUIContext
import okhttp3.Cookie
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.util.LinkedList

class AccountPreference @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : MessagePreference(context, attrs) {
    private val mActivity = context as SettingsActivity
    private var message: String? = null

    init {
        updateMessage()
    }

    private fun updateMessage() {
        val eCookies = EhCookieStore.getCookies(EhUrl.HOST_E.toHttpUrl())
        val exCookies = EhCookieStore.getCookies(EhUrl.HOST_EX.toHttpUrl())
        val cookies: MutableList<Cookie> = LinkedList(eCookies)
        cookies.addAll(exCookies)
        var ipbMemberId: String? = null
        var ipbPassHash: String? = null
        var igneous: String? = null
        cookies.forEach {
            when (it.name) {
                EhCookieStore.KEY_IPB_MEMBER_ID -> ipbMemberId = it.value
                EhCookieStore.KEY_IPB_PASS_HASH -> ipbPassHash = it.value
                EhCookieStore.KEY_IGNEOUS -> igneous = it.value
            }
        }
        if (ipbMemberId != null && ipbPassHash != null) {
            message = (
                EhCookieStore.KEY_IPB_MEMBER_ID + ": " + ipbMemberId + "<br>" +
                    EhCookieStore.KEY_IPB_PASS_HASH + ": " + ipbPassHash + "<br>" +
                    EhCookieStore.KEY_IGNEOUS + ": " + igneous
                )
            setDialogMessage(
                Html.fromHtml(
                    context.getString(
                        R.string.settings_eh_account_identity_cookies,
                        message,
                    ),
                    Html.FROM_HTML_MODE_LEGACY,
                ),
            )
            message = message!!.replace("<br>", "\n")
        } else {
            setDialogMessage(context.getString(R.string.settings_eh_account_name_tourist))
        }
    }

    override fun onPrepareDialogBuilder(builder: AlertDialog.Builder) {
        super.onPrepareDialogBuilder(builder)
        if (message != null) {
            builder.setNeutralButton(R.string.settings_eh_account_identity_cookies_copy) { dialog: DialogInterface?, which: Int ->
                mActivity.addTextToClipboard(message, true)
                this@AccountPreference.onClick(dialog, which)
            }
            builder.setNegativeButton(R.string.settings_eh_account_clear_igneous) { _: DialogInterface?, _: Int ->
                mActivity.lifecycleScope.launchIO {
                    EhCookieStore.deleteCookie(
                        EhUrl.HOST_EX.toHttpUrl(),
                        EhCookieStore.KEY_IGNEOUS,
                    )
                    withUIContext {
                        updateMessage()
                    }
                }
            }
        }
        builder.setPositiveButton(R.string.settings_eh_account_sign_out) { _: DialogInterface?, _: Int ->
            EhUtils.signOut()
            mActivity.showTip(R.string.settings_eh_account_sign_out_tip, BaseScene.LENGTH_SHORT)
        }
    }
}
