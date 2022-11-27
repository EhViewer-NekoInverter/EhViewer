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

package com.hippo.ehviewer.ui.fragment;

import android.app.Activity;
import android.content.res.Configuration;
import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.preference.Preference;

import com.hippo.ehviewer.R;
import com.hippo.ehviewer.Settings;
import com.hippo.ehviewer.client.EhTagDatabase;

import rikka.material.app.DayNightDelegate;

public class EhFragment extends BasePreferenceFragment {

    @Override
    public void onCreatePreferences(@Nullable Bundle savedInstanceState, String rootKey) {
        addPreferencesFromResource(R.xml.eh_settings);

        Preference theme = findPreference(Settings.KEY_THEME);
        Preference blackDarkTheme = findPreference(Settings.KEY_BLACK_DARK_THEME);
        Preference gallerySite = findPreference(Settings.KEY_GALLERY_SITE);
        Preference listMode = findPreference(Settings.KEY_LIST_MODE);
        Preference listThumbSize = findPreference(Settings.KEY_LIST_THUMB_SIZE);
        Preference detailSize = findPreference(Settings.KEY_DETAIL_SIZE);
        Preference thumbSize = findPreference(Settings.KEY_THUMB_SIZE);
        Preference showTagTranslations = findPreference(Settings.KEY_SHOW_TAG_TRANSLATIONS);
        Preference tagTranslationsSource = findPreference("tag_translations_source");

        theme.setOnPreferenceChangeListener(this);
        gallerySite.setOnPreferenceChangeListener(this);
        listMode.setOnPreferenceChangeListener(this);
        listThumbSize.setOnPreferenceChangeListener(this);
        detailSize.setOnPreferenceChangeListener(this);
        thumbSize.setOnPreferenceChangeListener(this);
        showTagTranslations.setOnPreferenceChangeListener(this);
        blackDarkTheme.setOnPreferenceChangeListener(this);

        if (!EhTagDatabase.isPossible(requireActivity())) {
            getPreferenceScreen().removePreference(showTagTranslations);
            getPreferenceScreen().removePreference(tagTranslationsSource);
        }
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        String key = preference.getKey();
        if (Settings.KEY_THEME.equals(key)) {
            DayNightDelegate.setDefaultNightMode(Integer.parseInt((String) newValue));
            requireActivity().recreate();
            return true;
        } else if (Settings.KEY_GALLERY_SITE.equals(key)) {
            requireActivity().setResult(Activity.RESULT_OK);
            return true;
        } else if (Settings.KEY_LIST_MODE.equals(key)) {
            requireActivity().setResult(Activity.RESULT_OK);
            return true;
        } else if (Settings.KEY_LIST_THUMB_SIZE.equals(key)) {
            Settings.LIST_THUMB_SIZE_INITED = false;
            requireActivity().setResult(Activity.RESULT_OK);
            return true;
        } else if (Settings.KEY_DETAIL_SIZE.equals(key)) {
            requireActivity().setResult(Activity.RESULT_OK);
        } else if (Settings.KEY_THUMB_SIZE.equals(key)) {
            requireActivity().setResult(Activity.RESULT_OK);
        } else if (Settings.KEY_SHOW_TAG_TRANSLATIONS.equals(key)) {
            if (Boolean.TRUE.equals(newValue)) {
                EhTagDatabase.update(requireActivity());
            }
        } else if (Settings.KEY_BLACK_DARK_THEME.equals(key)) {
            if ((requireActivity().getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_YES) > 0) {
                requireActivity().recreate();
            }
            return true;
        }
        return true;
    }

    @Override
    public int getFragmentTitle() {
        return R.string.settings_eh;
    }
}
