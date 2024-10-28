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

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import androidx.preference.ListPreference
import androidx.preference.Preference
import com.hippo.ehviewer.AppConfig
import com.hippo.ehviewer.R
import com.hippo.ehviewer.Settings
import com.hippo.ehviewer.ui.keepNoMediaFileStatus
import com.hippo.ehviewer.ui.scene.BaseScene
import com.hippo.unifile.UniFile
import com.hippo.util.ExceptionUtils
import com.hippo.util.launchNonCancellable

class DownloadFragment : BasePreferenceFragment() {
    private var mDownloadLocation: Preference? = null
    private var pickImageDirLauncher = registerForActivityResult<Uri?, Uri>(
        ActivityResultContracts.OpenDocumentTree(),
    ) { treeUri: Uri? ->
        if (treeUri != null) {
            requireActivity().contentResolver.takePersistableUriPermission(
                treeUri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
            val uniFile = UniFile.fromTreeUri(requireActivity(), treeUri)
            if (uniFile != null) {
                Settings.putDownloadLocation(uniFile)
                lifecycleScope.launchNonCancellable {
                    keepNoMediaFileStatus()
                }
                onUpdateDownloadLocation()
            } else {
                showTip(
                    R.string.settings_download_cant_get_download_location,
                    BaseScene.LENGTH_SHORT,
                )
            }
        }
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        addPreferencesFromResource(R.xml.download_settings)
        mDownloadLocation = findPreference(Settings.KEY_DOWNLOAD_LOCATION)
        val mediaScan = findPreference<Preference>(Settings.KEY_MEDIA_SCAN)
        val multiThreadDownload = findPreference<Preference>(Settings.KEY_MULTI_THREAD_DOWNLOAD)
        val downloadDelay = findPreference<Preference>(Settings.KEY_DOWNLOAD_DELAY)
        val preloadImage = findPreference<Preference>(Settings.KEY_PRELOAD_IMAGE)
        val downloadOriginImage = findPreference<Preference>(Settings.KEY_DOWNLOAD_ORIGIN_IMAGE)
        mDownloadLocation?.onPreferenceClickListener = this
        mediaScan!!.onPreferenceChangeListener = this
        multiThreadDownload!!.setSummaryProvider {
            getString(R.string.settings_download_concurrency_summary, (it as ListPreference).entry)
        }
        downloadDelay!!.setSummaryProvider {
            getString(R.string.settings_download_download_delay_summary, (it as ListPreference).entry)
        }
        preloadImage!!.setSummaryProvider {
            getString(R.string.settings_download_preload_image_summary, (it as ListPreference).entry)
        }
        downloadOriginImage!!.setSummaryProvider {
            getString(R.string.settings_download_download_origin_image_summary, (it as ListPreference).entry)
        }
        onUpdateDownloadLocation()
    }

    override fun onDestroy() {
        super.onDestroy()
        mDownloadLocation = null
    }

    private fun onUpdateDownloadLocation() {
        val file = Settings.downloadLocation
        if (mDownloadLocation != null) {
            if (file != null) {
                mDownloadLocation!!.summary = file.uri.toString()
            } else {
                mDownloadLocation!!.setSummary(R.string.settings_download_invalid_download_location)
            }
        }
    }

    override fun onPreferenceClick(preference: Preference): Boolean {
        val key = preference.key
        if (Settings.KEY_DOWNLOAD_LOCATION == key) {
            val file = Settings.downloadLocation
            if (file != null &&
                !UniFile.isFileUri(Settings.downloadLocation!!.uri)
            ) {
                AlertDialog.Builder(requireContext())
                    .setTitle(R.string.settings_download_download_location)
                    .setMessage(file.uri.toString())
                    .setPositiveButton(R.string.settings_download_pick_new_location) { _, _ -> openDirPickerL() }
                    .setNeutralButton(R.string.settings_download_reset_location) { _, _ ->
                        val uniFile = UniFile.fromFile(AppConfig.getDefaultDownloadDir())
                        if (uniFile != null) {
                            Settings.putDownloadLocation(uniFile)
                            lifecycleScope.launchNonCancellable {
                                keepNoMediaFileStatus()
                            }
                            onUpdateDownloadLocation()
                        } else {
                            showTip(
                                R.string.settings_download_cant_get_download_location,
                                BaseScene.LENGTH_SHORT,
                            )
                        }
                    }
                    .show()
            } else {
                openDirPickerL()
            }
            return true
        }
        return false
    }

    private fun openDirPickerL() {
        try {
            pickImageDirLauncher.launch(null)
        } catch (e: Throwable) {
            ExceptionUtils.throwIfFatal(e)
            showTip(R.string.error_cant_find_activity, BaseScene.LENGTH_SHORT)
        }
    }

    override fun onPreferenceChange(preference: Preference, newValue: Any): Boolean {
        val key = preference.key
        if (Settings.KEY_MEDIA_SCAN == key) {
            if (newValue is Boolean) {
                lifecycleScope.launchNonCancellable {
                    keepNoMediaFileStatus()
                }
            }
            return true
        }
        return false
    }

    override val fragmentTitle: Int
        get() = R.string.settings_download
}
