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
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import androidx.biometric.BiometricManager
import com.hippo.ehviewer.R
import com.hippo.ehviewer.Settings
import com.hippo.widget.lockpattern.LockPatternUtils
import com.hippo.widget.lockpattern.LockPatternView
import com.hippo.yorozuya.ViewUtils

class SetSecurityFragment :
    BaseFragment(),
    View.OnClickListener {
    private var mPatternView: LockPatternView? = null
    private var mCancel: View? = null
    private var mSet: View? = null
    private var mFingerprint: CheckBox? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View? {
        val view = inflater.inflate(R.layout.activity_set_security, container, false)
        mPatternView = ViewUtils.`$$`(view, R.id.pattern_view) as LockPatternView
        mCancel = ViewUtils.`$$`(view, R.id.cancel)
        mSet = ViewUtils.`$$`(view, R.id.set)
        mFingerprint = ViewUtils.`$$`(view, R.id.fingerprint_checkbox) as CheckBox
        val pattern = Settings.security
        if (!TextUtils.isEmpty(pattern)) {
            mPatternView!!.setPattern(
                LockPatternView.DisplayMode.Correct,
                LockPatternUtils.stringToPattern(pattern),
            )
        }
        if (BiometricManager.from(requireContext()).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK) == BiometricManager.BIOMETRIC_SUCCESS) {
            mFingerprint!!.visibility = View.VISIBLE
            mFingerprint!!.isChecked = Settings.enableFingerprint
        }
        mCancel!!.setOnClickListener(this)
        mSet!!.setOnClickListener(this)
        return view
    }

    override fun onDestroyView() {
        super.onDestroyView()
        mPatternView = null
    }

    @Suppress("DEPRECATION")
    override fun onClick(v: View) {
        if (v == mCancel) {
            requireActivity().onBackPressed()
        } else if (v == mSet) {
            if (null != mPatternView && null != mFingerprint) {
                val security = if (mPatternView!!.cellSize <= 1) {
                    ""
                } else {
                    mPatternView!!.patternString
                }
                Settings.putSecurity(security)
                Settings.putEnableFingerprint(
                    mFingerprint!!.visibility == View.VISIBLE &&
                        mFingerprint!!.isChecked &&
                        security.isNotEmpty(),
                )
            }
            requireActivity().onBackPressed()
        }
    }

    override fun getFragmentTitle(): Int = R.string.set_pattern_protection
}
