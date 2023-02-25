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
package com.hippo.ehviewer.preference

import android.content.Context
import android.util.AttributeSet
import androidx.appcompat.app.AlertDialog
import com.hippo.ehviewer.R
import com.hippo.ehviewer.client.EhUtils
import com.hippo.ehviewer.ui.SettingsActivity
import com.hippo.ehviewer.ui.scene.BaseScene
import com.hippo.preference.MessagePreference

class SignOutPreference @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : MessagePreference(context, attrs) {
    private val mActivity = context as SettingsActivity

    init {
        setDialogMessage(context.getString(R.string.settings_eh_sign_out_warning))
    }

    override fun onPrepareDialogBuilder(builder: AlertDialog.Builder) {
        super.onPrepareDialogBuilder(builder)
        builder.setPositiveButton(R.string.settings_eh_sign_out_yes, this)
        builder.setNegativeButton(android.R.string.cancel, null)
    }

    override fun onDialogClosed(positiveResult: Boolean) {
        super.onDialogClosed(positiveResult)
        if (positiveResult) {
            EhUtils.signOut(context)
            mActivity.showTip(R.string.settings_eh_sign_out_tip, BaseScene.LENGTH_SHORT)
        }
    }
}