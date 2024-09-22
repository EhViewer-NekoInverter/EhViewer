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
package com.hippo.scene

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import com.hippo.ehviewer.R
import java.lang.reflect.Field

open class StageLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : FrameLayout(
    context,
    attrs,
    defStyleAttr,
) {
    private var mDisappearingChildrenField: Field? = null
    private var mSuperDisappearingChildren: ArrayList<View>? = null
    private var mSortedScenes: ArrayList<View>? = null
    private var mDumpView: View? = null
    private var mDoTrick = false

    @SuppressLint("DiscouragedPrivateApi")
    private fun init(context: Context) {
        try {
            mDisappearingChildrenField =
                ViewGroup::class.java.getDeclaredField("mDisappearingChildren")
            mDisappearingChildrenField!!.isAccessible = true
        } catch (e: NoSuchFieldException) {
            e.printStackTrace()
        }
        if (mDisappearingChildrenField != null) {
            mDumpView = View(context)
            addView(mDumpView, LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        }
    }

    private val superDisappearingChildren: Unit
        get() {
            if (mDisappearingChildrenField == null || mSuperDisappearingChildren != null) {
                return
            }
            try {
                @Suppress("UNCHECKED_CAST")
                mSuperDisappearingChildren = mDisappearingChildrenField!![this] as ArrayList<View>?
            } catch (e: IllegalAccessException) {
                e.printStackTrace()
            }
        }

    private fun beforeDispatchDraw(): Boolean {
        superDisappearingChildren
        if (mSuperDisappearingChildren == null || mSuperDisappearingChildren!!.isEmpty() || childCount <= 1) { // only dump view
            return false
        }

        // Get stage
        val stage: StageActivity?
        val context = context
        if (context is StageActivity) {
            stage = context
        } else {
            return false
        }
        if (null == mSortedScenes) {
            mSortedScenes = ArrayList()
        }

        // Add all scene view to mSortedScenes
        val disappearingChildren: ArrayList<View> = mSuperDisappearingChildren!!
        val sortedScenes = mSortedScenes!!
        run {
            for (i in 1 until childCount) {
                // Skip dump view
                val view = getChildAt(i)
                if (null != view.getTag(R.id.fragment_tag)) {
                    sortedScenes.add(view)
                }
            }
        }
        for (i in 0 until disappearingChildren.size) {
            val view = disappearingChildren[i]
            if (null != view.getTag(R.id.fragment_tag)) {
                sortedScenes.add(view)
            }
        }
        stage.sortSceneViews(sortedScenes)
        return true
    }

    private fun afterDispatchDraw() {
        mSortedScenes?.clear()
    }

    override fun dispatchDraw(canvas: Canvas) {
        mDoTrick = beforeDispatchDraw()
        super.dispatchDraw(canvas)
        afterDispatchDraw()
        mDoTrick = false
    }

    override fun drawChild(canvas: Canvas, child: View, drawingTime: Long): Boolean {
        if (mDoTrick) {
            val sortedScenes = mSortedScenes
            if (child === mDumpView) {
                var more = false
                for (i in 0 until sortedScenes!!.size) {
                    more = more or super.drawChild(canvas, sortedScenes[i], drawingTime)
                }
                return more
            } else if (sortedScenes!!.contains(child)) {
                // Skip
                return false
            }
        }
        return super.drawChild(canvas, child, drawingTime)
    }

    init {
        init(context)
    }
}
