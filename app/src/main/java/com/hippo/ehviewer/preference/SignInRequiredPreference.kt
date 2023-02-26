/*
 * Copyright 2019 Hippo Seven
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
package com.hippo.ehviewer.preference;

import android.content.Context
import android.util.AttributeSet
import androidx.preference.Preference
import com.hippo.ehviewer.EhApplication
import com.hippo.ehviewer.R
import com.hippo.ehviewer.client.EhCookieStore
import com.hippo.ehviewer.client.EhUrl
import com.hippo.ehviewer.ui.SettingsActivity
import com.hippo.ehviewer.ui.scene.BaseScene
import okhttp3.HttpUrl.Companion.toHttpUrl

class SignInRequiredPreference @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : Preference(context, attrs), Preference.OnPreferenceClickListener {
    private val mActivity = context as SettingsActivity

    init {
        setOnPreferenceClickListener(this)
    }

    override fun onPreferenceClick(preference: Preference): Boolean {
        val store = EhApplication.ehCookieStore
        val e = EhUrl.HOST_E.toHttpUrl()
        val ex = EhUrl.HOST_EX.toHttpUrl()
        if (store.contains(e, EhCookieStore.KEY_IPB_MEMBER_ID) ||
                store.contains(e, EhCookieStore.KEY_IPB_PASS_HASH) ||
                store.contains(ex, EhCookieStore.KEY_IPB_MEMBER_ID) ||
                store.contains(ex, EhCookieStore.KEY_IPB_PASS_HASH)) {
            return false
        } else {
            mActivity.showTip(R.string.error_please_login_first, BaseScene.LENGTH_SHORT)
            return true
        }
    }
}