/*
 * Copyright 2022 Tarsin Norbin
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
package com.hippo.ehviewer.widget

import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import android.view.View
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.coordinatorlayout.widget.CoordinatorLayout.AttachedBehavior
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import com.google.android.material.snackbar.Snackbar.SnackbarLayout
import com.hippo.scene.StageLayout
import com.hippo.yorozuya.LayoutUtils
import kotlin.math.min

class EhStageLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0,
) : StageLayout(
    context,
    attrs,
    defStyle,
),
    AttachedBehavior {
    private var mAboveSnackViewList: MutableList<View>? = null

    fun addAboveSnackView(view: View) {
        if (null == mAboveSnackViewList) {
            mAboveSnackViewList = ArrayList()
        }
        mAboveSnackViewList!!.add(view)
    }

    fun removeAboveSnackView(view: View) {
        if (null == mAboveSnackViewList) {
            return
        }
        mAboveSnackViewList!!.remove(view)
    }

    val aboveSnackViewCount: Int
        get() = if (null == mAboveSnackViewList) 0 else mAboveSnackViewList!!.size

    fun getAboveSnackViewAt(index: Int): View? = if (null == mAboveSnackViewList || index < 0 || index >= mAboveSnackViewList!!.size) {
        null
    } else {
        mAboveSnackViewList!![index]
    }

    override fun getBehavior(): Behavior = Behavior()

    class Behavior : CoordinatorLayout.Behavior<EhStageLayout?>() {
        @SuppressLint("RestrictedApi")
        override fun layoutDependsOn(
            parent: CoordinatorLayout,
            child: EhStageLayout,
            dependency: View,
        ): Boolean = dependency is SnackbarLayout

        override fun onDependentViewChanged(
            parent: CoordinatorLayout,
            child: EhStageLayout,
            dependency: View,
        ): Boolean {
            for (i in 0 until child.aboveSnackViewCount) {
                val view = child.getAboveSnackViewAt(i)
                if (view != null) {
                    val translationY = min(
                        0.0,
                        (
                            dependency.translationY - dependency.height - LayoutUtils.dp2pix(
                                view.context,
                                8f,
                            )
                            ).toDouble(),
                    ).toFloat()
                    view.animate().setInterpolator(FastOutSlowInInterpolator())
                        .translationY(translationY).setDuration(150).start()
                }
            }
            return false
        }

        override fun onDependentViewRemoved(
            parent: CoordinatorLayout,
            child: EhStageLayout,
            dependency: View,
        ) {
            for (i in 0 until child.aboveSnackViewCount) {
                child.getAboveSnackViewAt(i)?.animate()?.setInterpolator(FastOutSlowInInterpolator())?.translationY(0f)
                    ?.setDuration(75)?.start()
            }
        }
    }
}
