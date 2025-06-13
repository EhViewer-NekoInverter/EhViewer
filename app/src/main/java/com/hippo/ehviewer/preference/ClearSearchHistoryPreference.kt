/*
 * Copyright 2025 Moedog
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
package com.hippo.ehviewer.preference

import android.content.Context
import android.util.AttributeSet
import com.hippo.ehviewer.EhApplication.Companion.searchDatabase
import com.hippo.ehviewer.GetText
import com.hippo.ehviewer.R
import com.hippo.util.launchUI
import kotlinx.coroutines.launch

class ClearSearchHistoryPreference(
    context: Context,
    attrs: AttributeSet? = null,
) : TaskPreference(context, attrs) {
    override val jobTitle = JOB_TITLE_CLEAR_SEARCH_HISTORY

    override fun launchJob() {
        launch {
            searchDatabase.clearQuery()
            launchUI {
                mDialog.dismiss()
                showTip(SEARCH_HISTORY_CLEARED)
            }
        }
    }

    companion object {
        private val JOB_TITLE_CLEAR_SEARCH_HISTORY = GetText.getString(R.string.settings_privacy_clear_search_history)
        private val SEARCH_HISTORY_CLEARED = GetText.getString(R.string.settings_privacy_clear_search_history_cleared)
    }
}
