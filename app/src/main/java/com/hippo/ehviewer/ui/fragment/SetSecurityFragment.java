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

package com.hippo.ehviewer.ui.fragment;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.biometric.BiometricManager;

import com.hippo.ehviewer.R;
import com.hippo.ehviewer.Settings;
import com.hippo.widget.lockpattern.LockPatternUtils;
import com.hippo.widget.lockpattern.LockPatternView;
import com.hippo.yorozuya.ViewUtils;

public class SetSecurityFragment extends BaseFragment implements View.OnClickListener {

    @Nullable
    private LockPatternView mPatternView;
    @Nullable
    private View mCancel;
    @Nullable
    private View mSet;
    @Nullable
    private CheckBox mFingerprint;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.activity_set_security, container, false);

        mPatternView = (LockPatternView) ViewUtils.$$(view, R.id.pattern_view);
        mCancel = ViewUtils.$$(view, R.id.cancel);
        mSet = ViewUtils.$$(view, R.id.set);
        mFingerprint = (CheckBox) ViewUtils.$$(view, R.id.fingerprint_checkbox);

        String pattern = Settings.getSecurity();
        if (!TextUtils.isEmpty(pattern)) {
            mPatternView.setPattern(LockPatternView.DisplayMode.Correct,
                    LockPatternUtils.stringToPattern(pattern));
        }

        if (BiometricManager.from(requireContext()).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK) == BiometricManager.BIOMETRIC_SUCCESS) {
            mFingerprint.setVisibility(View.VISIBLE);
            mFingerprint.setChecked(Settings.getEnableFingerprint());
        }

        mCancel.setOnClickListener(this);
        mSet.setOnClickListener(this);
        return view;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        mPatternView = null;
    }

    @Override
    public void onClick(View v) {
        if (v == mCancel) {
            requireActivity().onBackPressed();
        } else if (v == mSet) {
            if (null != mPatternView && null != mFingerprint) {
                String security;
                if (mPatternView.getCellSize() <= 1) {
                    security = "";
                } else {
                    security = mPatternView.getPatternString();
                }
                Settings.putSecurity(security);
                Settings.putEnableFingerprint(mFingerprint.getVisibility() == View.VISIBLE &&
                        mFingerprint.isChecked() && !security.isEmpty());
            }
            requireActivity().onBackPressed();
        }
    }

    @Override
    public int getFragmentTitle() {
        return R.string.set_pattern_protection;
    }
}
