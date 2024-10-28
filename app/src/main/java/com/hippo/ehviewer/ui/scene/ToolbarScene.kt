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

import android.os.Bundle
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.appcompat.widget.Toolbar
import com.hippo.ehviewer.R

abstract class ToolbarScene : BaseScene() {
    private var mToolbar: Toolbar? = null
    private var mTempTitle: CharSequence? = null

    open fun onCreateViewWithToolbar(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View? = null

    override var needWhiteStatusBar = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View? {
        val view = inflater.inflate(R.layout.scene_toolbar, container, false)
        val toolbar = view.findViewById<Toolbar>(R.id.toolbar)
        val contentPanel = view.findViewById<FrameLayout>(R.id.content_panel)
        val contentView = onCreateViewWithToolbar(inflater, contentPanel, savedInstanceState)
        return if (contentView == null) {
            null
        } else {
            mToolbar = toolbar
            contentPanel.addView(contentView, 0)
            view
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        mToolbar = null
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        mToolbar?.apply {
            mTempTitle?.let { title = it }
            val menuResId = getMenuResId()
            if (menuResId != 0) {
                inflateMenu(menuResId)
                setOnMenuItemClickListener { item: MenuItem -> onMenuItemClick(item) }
            }
            setNavigationOnClickListener { onNavigationClick() }
        }
    }

    open fun getMenuResId(): Int = 0

    open fun onMenuItemClick(item: MenuItem): Boolean = false

    open fun onNavigationClick() {}

    fun setNavigationIcon(@DrawableRes resId: Int) {
        mToolbar?.setNavigationIcon(resId)
    }

    fun setTitle(@StringRes resId: Int) {
        setTitle(getString(resId))
    }

    fun setTitle(title: CharSequence) {
        mToolbar?.title = title
        mTempTitle = title
    }
}
