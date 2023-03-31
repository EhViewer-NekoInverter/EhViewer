/*
 * Copyright 2022 Moedog
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

import android.os.Bundle
import android.text.TextUtils
import androidx.annotation.StringRes
import androidx.preference.Preference
import com.hippo.ehviewer.R
import com.hippo.ehviewer.Settings

class PrivacyFragment : BasePreferenceFragment() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        addPreferencesFromResource(R.xml.privacy_settings)
    }

    override fun onStart() {
        super.onStart()
        val patternProtection = findPreference<Preference>(KEY_PATTERN_PROTECTION)
        patternProtection!!.summary = if (TextUtils.isEmpty(Settings.security)) {
            getString(R.string.settings_privacy_pattern_protection_not_set)
        } else {
            getString(R.string.settings_privacy_pattern_protection_set)
        }
    }

    @get:StringRes
    override val fragmentTitle: Int
        get() = R.string.settings_privacy

    companion object {
        private const val KEY_PATTERN_PROTECTION = "security"
    }
}
