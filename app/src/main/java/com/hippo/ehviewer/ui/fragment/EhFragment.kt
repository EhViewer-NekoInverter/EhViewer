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
package com.hippo.ehviewer.ui.fragment

import android.app.Activity
import android.content.res.Configuration
import android.os.Bundle
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatDelegate
import androidx.lifecycle.lifecycleScope
import androidx.preference.ListPreference
import androidx.preference.Preference
import com.hippo.ehviewer.EhApplication
import com.hippo.ehviewer.R
import com.hippo.ehviewer.Settings
import com.hippo.ehviewer.client.EhCookieStore
import com.hippo.ehviewer.client.EhEngine
import com.hippo.ehviewer.client.EhTagDatabase
import com.hippo.util.launchNonCancellable

class EhFragment : BasePreferenceFragment() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        addPreferencesFromResource(R.xml.eh_settings)
        val account = findPreference<Preference>(Settings.KEY_ACCOUNT)
        val theme = findPreference<Preference>(Settings.KEY_THEME)
        val blackDarkTheme = findPreference<Preference>(Settings.KEY_BLACK_DARK_THEME)
        val gallerySite = findPreference<Preference>(Settings.KEY_GALLERY_SITE)
        val listMode = findPreference<Preference>(Settings.KEY_LIST_MODE)
        val detailSize = findPreference<Preference>(Settings.KEY_DETAIL_SIZE)
        val listThumbSize = findPreference<Preference>(Settings.KEY_LIST_THUMB_SIZE)
        val thumbSize = findPreference<Preference>(Settings.KEY_THUMB_SIZE)
        val thumbShowTitle = findPreference<Preference>(Settings.KEY_THUMB_SHOW_TITLE)
        val thumbResolution = findPreference<Preference>(Settings.KEY_THUMB_RESOLUTION)
        val showTagTranslations = findPreference<Preference>(Settings.KEY_SHOW_TAG_TRANSLATIONS)
        val tagTranslationsSource = findPreference<Preference>(Settings.KEY_TAG_TRANSLATIONS_SOURCE)

        theme!!.onPreferenceChangeListener = this
        blackDarkTheme!!.onPreferenceChangeListener = this
        gallerySite!!.onPreferenceChangeListener = this
        listMode!!.onPreferenceChangeListener = this
        detailSize!!.onPreferenceChangeListener = this
        listThumbSize!!.onPreferenceChangeListener = this
        thumbSize!!.onPreferenceChangeListener = this
        thumbShowTitle!!.onPreferenceChangeListener = this
        showTagTranslations!!.onPreferenceChangeListener = this
        Settings.displayName?.let { account?.summary = it }
        thumbResolution!!.setSummaryProvider {
            getString(R.string.settings_eh_thumb_resolution_summary, (it as ListPreference).entry)
        }
        if (!EhTagDatabase.isTranslatable(requireActivity())) {
            if (!Settings.showTagTranslations) {
                preferenceScreen.removePreference(showTagTranslations)
            }
            preferenceScreen.removePreference(tagTranslationsSource!!)
        }
        if (!EhCookieStore.hasSignedIn()) {
            Settings.SIGN_IN_REQUIRED.forEach {
                val preference = findPreference<Preference>(it)
                preferenceScreen.removePreference(preference!!)
            }
        }
        updateListPreference(Settings.listMode)
    }

    override fun onPreferenceChange(preference: Preference, newValue: Any): Boolean {
        val key = preference.key
        if (Settings.KEY_THEME == key) {
            AppCompatDelegate.setDefaultNightMode((newValue as String).toInt())
            requireActivity().recreate()
        } else if (Settings.KEY_BLACK_DARK_THEME == key) {
            if (requireActivity().resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_YES > 0) {
                EhApplication.application.recreateAllActivity()
            }
        } else if (Settings.KEY_GALLERY_SITE == key) {
            requireActivity().setResult(Activity.RESULT_OK)
            lifecycleScope.launchNonCancellable {
                runCatching {
                    EhEngine.getUConfig()
                }.onFailure {
                    it.printStackTrace()
                }
            }
        } else if (Settings.KEY_LIST_MODE == key) {
            updateListPreference((newValue as String).toInt())
            requireActivity().setResult(Activity.RESULT_OK)
        } else if (Settings.KEY_DETAIL_SIZE == key) {
            requireActivity().setResult(Activity.RESULT_OK)
        } else if (Settings.KEY_LIST_THUMB_SIZE == key) {
            Settings.listThumbSizeInited = false
            requireActivity().setResult(Activity.RESULT_OK)
        } else if (Settings.KEY_THUMB_SIZE == key) {
            requireActivity().setResult(Activity.RESULT_OK)
        } else if (Settings.KEY_THUMB_SHOW_TITLE == key) {
            requireActivity().setResult(Activity.RESULT_OK)
        } else if (Settings.KEY_SHOW_TAG_TRANSLATIONS == key) {
            if (java.lang.Boolean.TRUE == newValue) {
                lifecycleScope.launchNonCancellable { EhTagDatabase.update(true) }
            }
        }
        return true
    }

    @get:StringRes
    override val fragmentTitle: Int
        get() = R.string.settings_eh

    private fun updateListPreference(newValue: Int) {
        val isDetailMode = newValue == 0
        findPreference<Preference>(Settings.KEY_DETAIL_SIZE)!!.isVisible = isDetailMode
        findPreference<Preference>(Settings.KEY_LIST_THUMB_SIZE)!!.isVisible = isDetailMode
        findPreference<Preference>(Settings.KEY_THUMB_SIZE)!!.isVisible = !isDetailMode
        findPreference<Preference>(Settings.KEY_THUMB_SHOW_TITLE)!!.isVisible = !isDetailMode
    }
}
