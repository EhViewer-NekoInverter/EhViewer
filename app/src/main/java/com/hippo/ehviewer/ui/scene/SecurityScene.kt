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
package com.hippo.ehviewer.ui.scene

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import com.hippo.ehviewer.R
import com.hippo.ehviewer.Settings
import com.hippo.widget.lockpattern.LockPatternUtils
import com.hippo.widget.lockpattern.LockPatternView
import com.hippo.widget.lockpattern.LockPatternView.OnPatternListener
import com.hippo.yorozuya.ObjectUtils
import com.hippo.yorozuya.ViewUtils
import java.util.concurrent.Executors

class SecurityScene :
    SolidScene(),
    OnPatternListener {
    private var mPatternView: LockPatternView? = null
    private var mSensorManager: SensorManager? = null
    private var mAccelerometer: Sensor? = null
    private var promptInfo: BiometricPrompt.PromptInfo? = null
    private var biometricPrompt: BiometricPrompt? = null
    private var canAuthenticate = false
    private var mRetryTimes = 0

    override fun needShowLeftDrawer(): Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val context = requireContext()
        mSensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        mAccelerometer = mSensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        mRetryTimes = savedInstanceState?.getInt(KEY_RETRY_TIMES) ?: MAX_RETRY_TIMES

        canAuthenticate = Settings.enableFingerprint &&
            BiometricManager.from(context).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK) == BiometricManager.BIOMETRIC_SUCCESS
        biometricPrompt = BiometricPrompt(
            this,
            Executors.newSingleThreadExecutor(),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    startSceneForCheckStep(CHECK_STEP_SECURITY, arguments)
                    finish()
                }
            },
        )
        promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(getString(R.string.app_name))
            .setNegativeButtonText(getString(android.R.string.cancel))
            .setConfirmationRequired(false)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        mSensorManager = null
        mAccelerometer = null
    }

    override fun onResume() {
        super.onResume()
        if (canAuthenticate) {
            startBiometricPrompt()
        }
    }

    private fun startBiometricPrompt() {
        biometricPrompt!!.authenticate(promptInfo!!)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(KEY_RETRY_TIMES, mRetryTimes)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.scene_security, container, false)
        if (canAuthenticate) {
            view.setOnClickListener { startBiometricPrompt() }
        }
        mPatternView = ViewUtils.`$$`(view, R.id.pattern_view) as LockPatternView
        mPatternView!!.setOnPatternListener(this)
        return view
    }

    override fun onDestroyView() {
        super.onDestroyView()
        mPatternView = null
    }

    override fun onPatternStart() {}
    override fun onPatternCleared() {}
    override fun onPatternCellAdded(pattern: List<LockPatternView.Cell>) {}

    override fun onPatternDetected(pattern: List<LockPatternView.Cell>) {
        val activity = mainActivity
        if (null == activity || null == mPatternView) {
            return
        }
        val enteredPatter = LockPatternUtils.patternToString(pattern)
        val targetPatter = Settings.security
        if (ObjectUtils.equal(enteredPatter, targetPatter)) {
            startSceneForCheckStep(CHECK_STEP_SECURITY, arguments)
            finish()
        } else {
            mPatternView!!.setDisplayMode(LockPatternView.DisplayMode.Wrong)
            mRetryTimes--
            if (mRetryTimes <= 0) {
                finish()
            }
        }
    }

    companion object {
        private const val KEY_RETRY_TIMES = "retry_times"
        private const val MAX_RETRY_TIMES = 5
    }
}
