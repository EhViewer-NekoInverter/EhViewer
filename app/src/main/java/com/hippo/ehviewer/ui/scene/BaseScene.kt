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

import android.annotation.SuppressLint
import android.content.res.Configuration
import android.content.res.Resources
import android.content.res.Resources.Theme
import android.os.Bundle
import android.os.Parcelable
import android.util.SparseArray
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver.OnPreDrawListener
import androidx.annotation.IdRes
import androidx.annotation.StringRes
import androidx.core.view.GravityCompat
import androidx.core.view.SoftwareKeyboardControllerCompat
import androidx.core.view.WindowCompat
import androidx.drawerlayout.widget.DrawerLayout
import com.hippo.ehviewer.ui.MainActivity
import com.hippo.scene.SceneFragment
import com.hippo.util.getSparseParcelableArrayCompat
import com.hippo.util.isAtLeastR

abstract class BaseScene : SceneFragment() {
    private var drawerView: View? = null
    private var drawerViewState: SparseArray<Parcelable?>? = null
    open var needWhiteStatusBar = true

    fun updateAvatar() {
        val activity = activity
        if (activity is MainActivity) {
            activity.updateProfile()
        }
    }

    fun addAboveSnackView(view: View) {
        val activity = activity
        if (activity is MainActivity) {
            activity.addAboveSnackView(view)
        }
    }

    fun removeAboveSnackView(view: View) {
        val activity = activity
        if (activity is MainActivity) {
            activity.removeAboveSnackView(view)
        }
    }

    fun setDrawerLockMode(lockMode: Int, edgeGravity: Int) {
        val activity = activity
        if (activity is MainActivity) {
            activity.setDrawerLockMode(lockMode, edgeGravity)
        }
    }

    fun openDrawer(drawerGravity: Int) {
        val activity = activity
        if (activity is MainActivity) {
            activity.openDrawer(drawerGravity)
        }
    }

    fun closeDrawer(drawerGravity: Int) {
        val activity = activity
        if (activity is MainActivity) {
            activity.closeDrawer(drawerGravity)
        }
    }

    fun toggleDrawer(drawerGravity: Int) {
        val activity = activity
        if (activity is MainActivity) {
            activity.toggleDrawer(drawerGravity)
        }
    }

    fun showTip(message: CharSequence?, length: Int) {
        val activity = activity
        if (activity is MainActivity) {
            activity.showTip(message!!, length)
        }
    }

    fun showTip(@StringRes id: Int, length: Int) {
        val activity = activity
        if (activity is MainActivity) {
            activity.showTip(id, length)
        }
    }

    open fun needShowLeftDrawer(): Boolean = true

    open fun getNavCheckedItem(): Int = 0

    /**
     * @param resId 0 for clear
     */
    fun setNavCheckedItem(@IdRes resId: Int) {
        val activity = activity
        if (activity is MainActivity) {
            activity.setNavCheckedItem(resId)
        }
    }

    fun recreateDrawerView() {
        val activity = mainActivity
        activity?.createDrawerView(this)
    }

    fun createDrawerView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View? {
        drawerView = onCreateDrawerView(inflater, container, savedInstanceState)
        if (drawerView != null) {
            var saved = drawerViewState
            if (saved == null && savedInstanceState != null) {
                saved = savedInstanceState.getSparseParcelableArrayCompat(KEY_DRAWER_VIEW_STATE)
            }
            if (saved != null) {
                drawerView!!.restoreHierarchyState(saved)
            }
        }
        return drawerView
    }

    open fun onCreateDrawerView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View? = null

    fun destroyDrawerView() {
        if (drawerView != null) {
            drawerViewState = SparseArray()
            drawerView!!.saveHierarchyState(drawerViewState)
        }
        onDestroyDrawerView()
        drawerView = null
    }

    @Suppress("DEPRECATION")
    fun setLightStatusBar(set: Boolean) {
        val activity = requireActivity()
        val decorView = activity.window.decorView
        val isLight = set && (activity.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_YES) <= 0
        // https://github.com/EhViewer-NekoInverter/EhViewer/issues/55
        if (isAtLeastR) {
            WindowCompat.getInsetsController(activity.window, decorView).isAppearanceLightStatusBars = isLight
        } else {
            val flags = decorView.systemUiVisibility
            decorView.systemUiVisibility = if (isLight) {
                flags or View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
            } else {
                flags and View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR.inv()
            }
        }
        needWhiteStatusBar = set
    }

    open fun onDestroyDrawerView() {}

    @SuppressLint("RtlHardcoded")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        postponeEnterTransition()
        view.viewTreeObserver.addOnPreDrawListener(
            object : OnPreDrawListener {
                override fun onPreDraw(): Boolean {
                    view.viewTreeObserver.removeOnPreDrawListener(this)
                    startPostponedEnterTransition()
                    return true
                }
            },
        )

        // Update left drawer locked state
        if (needShowLeftDrawer()) {
            setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED, GravityCompat.START)
        } else {
            setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED, GravityCompat.START)
        }

        // Update nav checked item
        setNavCheckedItem(getNavCheckedItem())

        // Hide soft ime
        hideSoftInput()
        setLightStatusBar(needWhiteStatusBar)
    }

    val resourcesOrNull: Resources?
        get() {
            val context = context
            return context?.resources
        }

    val mainActivity: MainActivity?
        get() {
            val activity = activity
            return activity as? MainActivity
        }

    fun hideSoftInput() = activity?.window?.decorView?.run { SoftwareKeyboardControllerCompat(this) }?.hide()

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        if (drawerView != null) {
            drawerViewState = SparseArray()
            drawerView!!.saveHierarchyState(drawerViewState)
            outState.putSparseParcelableArray(KEY_DRAWER_VIEW_STATE, drawerViewState)
        }
    }

    val theme: Theme
        get() = requireActivity().theme

    companion object {
        const val LENGTH_SHORT = 0
        const val LENGTH_LONG = 1
        const val KEY_DRAWER_VIEW_STATE = "com.hippo.ehviewer.ui.scene.BaseScene:DRAWER_VIEW_STATE"
    }
}
