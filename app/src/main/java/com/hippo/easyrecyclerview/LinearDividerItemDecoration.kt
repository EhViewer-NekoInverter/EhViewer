/*
 * Copyright (C) 2015 Hippo Seven
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
package com.hippo.easyrecyclerview

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.view.View
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerView.ItemDecoration

/**
 * Only work for [androidx.recyclerview.widget.LinearLayoutManager].
 * Show divider between item, just like
 * [android.widget.ListView.setDivider]
 */
class LinearDividerItemDecoration(orientation: Int, color: Int, thickness: Int) : ItemDecoration() {
    private val mRect: Rect = Rect()
    private val mPaint: Paint = Paint()
    private var mShowFirstDivider = false
    private var mShowLastDivider = false
    private var mOrientation = 0
    private var mThickness = 0
    private var mPaddingStart = 0
    private var mPaddingEnd = 0
    private var mOverlap = false
    private var mShowDividerHelper: ShowDividerHelper? = null

    init {
        mPaint.style = Paint.Style.FILL
        setOrientation(orientation)
        setColor(color)
        setThickness(thickness)
    }

    fun setShowDividerHelper(showDividerHelper: ShowDividerHelper?) {
        mShowDividerHelper = showDividerHelper
    }

    fun setOrientation(orientation: Int) {
        require(!(orientation != HORIZONTAL && orientation != VERTICAL)) { "invalid orientation" }
        mOrientation = orientation
    }

    fun setColor(color: Int) {
        mPaint.setColor(color)
    }

    fun setThickness(thickness: Int) {
        mThickness = thickness
    }

    fun setShowFirstDivider(showFirstDivider: Boolean) {
        mShowFirstDivider = showFirstDivider
    }

    fun setShowLastDivider(showLastDivider: Boolean) {
        mShowLastDivider = showLastDivider
    }

    fun setPadding(padding: Int) {
        setPaddingStart(padding)
        setPaddingEnd(padding)
    }

    fun setPaddingStart(paddingStart: Int) {
        mPaddingStart = paddingStart
    }

    fun setPaddingEnd(paddingEnd: Int) {
        mPaddingEnd = paddingEnd
    }

    fun setOverlap(overlap: Boolean) {
        mOverlap = overlap
    }

    override fun getItemOffsets(
        outRect: Rect,
        view: View,
        parent: RecyclerView,
        state: RecyclerView.State,
    ) {
        if (parent.adapter == null) {
            outRect[0, 0, 0] = 0
            return
        }
        if (mOverlap) {
            outRect[0, 0, 0] = 0
            return
        }
        val position = parent.getChildLayoutPosition(view)
        val itemCount = parent.adapter!!.itemCount
        if (mShowDividerHelper != null) {
            if (mOrientation == VERTICAL) {
                if (position == 0 && mShowDividerHelper!!.showDivider(0)) {
                    outRect.top = mThickness
                }
                if (mShowDividerHelper!!.showDivider(position + 1)) {
                    outRect.bottom = mThickness
                }
            } else {
                if (position == 0 && mShowDividerHelper!!.showDivider(0)) {
                    outRect.left = mThickness
                }
                if (mShowDividerHelper!!.showDivider(position + 1)) {
                    outRect.right = mThickness
                }
            }
        } else {
            if (mOrientation == VERTICAL) {
                if (position == 0 && mShowFirstDivider) {
                    outRect.top = mThickness
                }
                outRect.bottom = mThickness
                if (position == itemCount - 1 && !mShowLastDivider) {
                    outRect.bottom = 0
                }
            } else {
                if (position == 0 && mShowFirstDivider) {
                    outRect.left = mThickness
                }
                outRect.right = mThickness
                if (position == itemCount - 1 && !mShowLastDivider) {
                    outRect.right = 0
                }
            }
        }
    }

    override fun onDrawOver(
        c: Canvas,
        parent: RecyclerView,
        state: RecyclerView.State,
    ) {
        val adapter = parent.adapter ?: return
        val itemCount = adapter.itemCount
        val overlap = mOverlap
        if (mOrientation == VERTICAL) {
            val isRtl = parent.layoutDirection == View.LAYOUT_DIRECTION_RTL
            val mPaddingLeft: Int
            val mPaddingRight: Int
            if (isRtl) {
                mPaddingLeft = mPaddingEnd
                mPaddingRight = mPaddingStart
            } else {
                mPaddingLeft = mPaddingStart
                mPaddingRight = mPaddingEnd
            }
            val left = parent.getPaddingLeft() + mPaddingLeft
            val right = parent.width - parent.getPaddingRight() - mPaddingRight
            val childCount = parent.childCount
            for (i in 0 until childCount) {
                val child = parent.getChildAt(i)
                val lp = child.layoutParams as RecyclerView.LayoutParams
                val position = parent.getChildLayoutPosition(child)
                var show: Boolean
                show = if (mShowDividerHelper != null) {
                    mShowDividerHelper!!.showDivider(position + 1)
                } else {
                    position != itemCount - 1 || mShowLastDivider
                }
                if (show) {
                    var top = child.bottom + lp.bottomMargin
                    if (overlap) {
                        top -= mThickness
                    }
                    val bottom = top + mThickness
                    mRect[left, top, right] = bottom
                    c.drawRect(mRect, mPaint)
                }
                if (position == 0) {
                    show = if (mShowDividerHelper != null) {
                        mShowDividerHelper!!.showDivider(0)
                    } else {
                        mShowFirstDivider
                    }
                    if (show) {
                        var bottom = child.top + lp.topMargin
                        if (overlap) {
                            bottom += mThickness
                        }
                        val top = bottom - mThickness
                        mRect[left, top, right] = bottom
                        c.drawRect(mRect, mPaint)
                    }
                }
            }
        } else {
            val top = parent.paddingTop + mPaddingStart
            val bottom = parent.height - parent.paddingBottom - mPaddingEnd
            val childCount = parent.childCount
            for (i in 0 until childCount) {
                val child = parent.getChildAt(i)
                val lp = child.layoutParams as RecyclerView.LayoutParams
                val position = parent.getChildLayoutPosition(child)
                var show: Boolean
                show = if (mShowDividerHelper != null) {
                    mShowDividerHelper!!.showDivider(position + 1)
                } else {
                    position != itemCount - 1 || mShowLastDivider
                }
                if (show) {
                    var left = child.right + lp.rightMargin
                    if (overlap) {
                        left -= mThickness
                    }
                    val right = left + mThickness
                    mRect[left, top, right] = bottom
                    c.drawRect(mRect, mPaint)
                }
                if (position == 0) {
                    show = if (mShowDividerHelper != null) {
                        mShowDividerHelper!!.showDivider(0)
                    } else {
                        mShowFirstDivider
                    }
                    if (show) {
                        var right = child.left + lp.leftMargin
                        if (overlap) {
                            right += mThickness
                        }
                        val left = right - mThickness
                        mRect[left, top, right] = bottom
                        c.drawRect(mRect, mPaint)
                    }
                }
            }
        }
    }

    interface ShowDividerHelper {
        fun showDivider(index: Int): Boolean
    }

    companion object {
        const val HORIZONTAL = LinearLayoutManager.HORIZONTAL
        const val VERTICAL = LinearLayoutManager.VERTICAL
    }
}
