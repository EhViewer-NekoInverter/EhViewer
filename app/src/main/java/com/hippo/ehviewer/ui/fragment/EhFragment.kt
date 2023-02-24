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
import androidx.preference.Preference
import com.hippo.ehviewer.R
import com.hippo.ehviewer.Settings
import com.hippo.ehviewer.client.EhTagDatabase
import rikka.material.app.DayNightDelegate

class EhFragment : BasePreferenceFragment() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        addPreferencesFromResource(R.xml.eh_settings)
        val theme = findPreference<Preference>(Settings.KEY_THEME)
        val blackDarkTheme = findPreference<Preference>(Settings.KEY_BLACK_DARK_THEME)
        val gallerySite = findPreference<Preference>(Settings.KEY_GALLERY_SITE)
        val listMode = findPreference<Preference>(Settings.KEY_LIST_MODE)
        val detailSize = findPreference<Preference>(Settings.KEY_DETAIL_SIZE)
        val listThumbSize = findPreference<Preference>(Settings.KEY_LIST_THUMB_SIZE)
        val thumbSize = findPreference<Preference>(Settings.KEY_THUMB_SIZE)
        val thumbShowTitle = findPreference<Preference>(Settings.KEY_THUMB_SHOW_TITLE)
        val showTagTranslations = findPreference<Preference>(Settings.KEY_SHOW_TAG_TRANSLATIONS)
        val tagTranslationsSource = findPreference<Preference>("tag_translations_source")

        theme!!.onPreferenceChangeListener = this
        blackDarkTheme!!.onPreferenceChangeListener = this
        gallerySite!!.onPreferenceChangeListener = this
        listMode!!.onPreferenceChangeListener = this
        detailSize!!.onPreferenceChangeListener = this
        listThumbSize!!.onPreferenceChangeListener = this
        thumbSize!!.onPreferenceChangeListener = this
        thumbShowTitle!!.onPreferenceChangeListener = this
        showTagTranslations!!.onPreferenceChangeListener = this

        if (!EhTagDatabase.isTranslatable(requireActivity())) {
            if (!Settings.getShowTagTranslations()) {
                preferenceScreen.removePreference(showTagTranslations)
            }
            preferenceScreen.removePreference(tagTranslationsSource!!)
        }
        updateListPreference(Settings.getListMode())
    }

    override fun onPreferenceChange(preference: Preference, newValue: Any): Boolean {
        val key = preference.key
        if (Settings.KEY_THEME == key) {
            DayNightDelegate.setDefaultNightMode((newValue as String).toInt())
            requireActivity().recreate()
        } else if (Settings.KEY_BLACK_DARK_THEME == key) {
            if (requireActivity().resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_YES > 0) {
                requireActivity().recreate()
            }
        } else if (Settings.KEY_GALLERY_SITE == key) {
            requireActivity().setResult(Activity.RESULT_OK)
        } else if (Settings.KEY_LIST_MODE == key) {
            updateListPreference((newValue as String).toInt())
            requireActivity().setResult(Activity.RESULT_OK)
        } else if (Settings.KEY_DETAIL_SIZE == key) {
            requireActivity().setResult(Activity.RESULT_OK)
        } else if (Settings.KEY_LIST_THUMB_SIZE == key) {
            Settings.LIST_THUMB_SIZE_INITED = false
            requireActivity().setResult(Activity.RESULT_OK)
        } else if (Settings.KEY_THUMB_SIZE == key) {
            requireActivity().setResult(Activity.RESULT_OK)
        } else if (Settings.KEY_THUMB_SHOW_TITLE == key) {
            requireActivity().setResult(Activity.RESULT_OK)
        } else if (Settings.KEY_SHOW_TAG_TRANSLATIONS == key) {
            if (java.lang.Boolean.TRUE == newValue) {
                EhTagDatabase.update(requireActivity())
            }
        }
        return true
    }

    override fun getFragmentTitle(): Int {
        return R.string.settings_eh
    }

    fun updateListPreference(newValue: Int) {
        val isDetailMode = newValue == 0
        findPreference<Preference>(Settings.KEY_DETAIL_SIZE)!!.isVisible = isDetailMode
        findPreference<Preference>(Settings.KEY_LIST_THUMB_SIZE)!!.isVisible = isDetailMode
        findPreference<Preference>(Settings.KEY_THUMB_SIZE)!!.isVisible = !isDetailMode
        findPreference<Preference>(Settings.KEY_THUMB_SHOW_TITLE)!!.isVisible = !isDetailMode
    }
}