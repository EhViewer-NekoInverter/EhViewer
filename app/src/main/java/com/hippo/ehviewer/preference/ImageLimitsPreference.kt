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
import android.widget.Button
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import com.hippo.ehviewer.R
import com.hippo.ehviewer.client.EhCookieStore
import com.hippo.ehviewer.client.EhEngine
import com.hippo.ehviewer.client.EhUrl
import com.hippo.ehviewer.client.parser.HomeParser
import com.hippo.ehviewer.ui.SettingsActivity
import com.hippo.ehviewer.ui.scene.BaseScene
import com.hippo.preference.DialogPreference
import com.hippo.util.ReadableTime
import com.hippo.util.launchIO
import com.hippo.util.withUIContext
import okhttp3.HttpUrl.Companion.toHttpUrl

class ImageLimitsPreference(
    context: Context,
    attrs: AttributeSet? = null,
) : DialogPreference(context, attrs), View.OnClickListener {
    private val mActivity = context as SettingsActivity
    private val placeholder = context.getString(R.string.please_wait)
    private val coroutineScope = mActivity.lifecycleScope
    private lateinit var resetButton: Button
    private lateinit var mDialog: AlertDialog
    private lateinit var mLimits: HomeParser.Limits
    private lateinit var mFunds: HomeParser.Funds

    init {
        if (EhCookieStore.hasSignedIn()) {
            coroutineScope.launchIO {
                getImageLimits { updateSummary() }
            }
        }
    }

    override fun onPrepareDialogBuilder(builder: AlertDialog.Builder) {
        super.onPrepareDialogBuilder(builder)
        builder.setMessage(placeholder)
    }

    override fun onDialogCreated(dialog: AlertDialog) {
        super.onDialogCreated(dialog)
        mDialog = dialog
        resetButton = dialog.getButton(DialogInterface.BUTTON_POSITIVE)
        resetButton.setOnClickListener(this)
        resetButton.isEnabled = false
        if (this::mLimits.isInitialized) {
            bind()
        } else {
            coroutineScope.launchIO {
                getImageLimits(true) { bind() }
            }
        }
    }

    private fun formatCurrent(): String {
        val (current, maximum, _) = mLimits
        return when (maximum) {
            HomeParser.IP_NORMAL -> mActivity.getString(R.string.settings_eh_image_limits_summary_ip) +
                mActivity.getString(R.string.settings_eh_image_limits_summary_ip_ok)
            HomeParser.IP_RESTRICTED -> mActivity.getString(R.string.settings_eh_image_limits_summary_ip) +
                mActivity.getString(R.string.settings_eh_image_limits_summary_ip_restricted)
            else -> mActivity.getString(R.string.settings_eh_image_limits_summary_acc, current, maximum)
        }
    }

    private fun updateSummary() {
        summary = formatCurrent()
    }

    private suspend fun getImageLimits(showError: Boolean = false, onSuccess: () -> Unit) {
        runCatching {
            EhEngine.getImageLimits()
        }.onFailure {
            it.printStackTrace()
            if (showError) {
                withUIContext {
                    mDialog.setMessage(it.message)
                }
            }
        }.onSuccess {
            mLimits = it.limits
            mFunds = it.funds
            withUIContext {
                onSuccess()
            }
        }
    }

    private fun bind() {
        val (_, maximum, resetCost) = mLimits
        val (fundsGP, fundsC) = mFunds
        var quotaExpire = 0L
        EhCookieStore.getCookies(EhUrl.HOST_E.toHttpUrl()).forEach {
            if (it.name == EhCookieStore.KEY_QUOTA) {
                quotaExpire = it.expiresAt
            }
        }
        var message = formatCurrent()
        if (quotaExpire > 0) {
            message += "  (~${ReadableTime.getShortTime(quotaExpire)})"
        }
        message += "\n" + if (maximum < 0) {
            mActivity.getString(R.string.settings_eh_unlock_cost, resetCost)
        } else {
            mActivity.getString(R.string.settings_eh_reset_cost, resetCost)
        } +
            "\n" + mActivity.getString(R.string.current_funds, "$fundsGP+", fundsC)
        mDialog.setMessage(message)
        resetButton.text = if (maximum < 0) {
            mActivity.getString(R.string.settings_eh_unlock)
        } else {
            mActivity.getString(R.string.settings_eh_reset)
        }
        resetButton.isEnabled = resetCost != 0
        updateSummary()
    }

    override fun onClick(v: View) {
        resetButton.isEnabled = false
        mDialog.setMessage(placeholder)
        coroutineScope.launchIO {
            runCatching {
                EhEngine.resetImageLimits(mLimits.maximum < 0)
            }.onFailure {
                it.printStackTrace()
                withUIContext {
                    mDialog.setMessage(it.message)
                }
            }.onSuccess {
                EhCookieStore.copyCookie(EhUrl.DOMAIN_E, EhUrl.DOMAIN_EX, EhCookieStore.KEY_QUOTA)
                withUIContext {
                    mLimits = it
                    mActivity.showTip(
                        R.string.settings_eh_reset_limits_succeed,
                        BaseScene.LENGTH_SHORT,
                    )
                    bind()
                }
            }
        }
    }
}
