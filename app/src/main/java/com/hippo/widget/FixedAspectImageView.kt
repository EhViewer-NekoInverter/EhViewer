/*
 * Copyright 2015 Hippo Seven
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
package com.hippo.widget

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.TypedArray
import android.util.AttributeSet
import com.google.android.material.imageview.ShapeableImageView
import com.hippo.ehviewer.R
import com.hippo.yorozuya.MathUtils
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

open class FixedAspectImageView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0,
) : ShapeableImageView(
    context,
    attrs,
    defStyle,
) {
    private var mMinWidth = 0
    private var mMinHeight = 0
    private var mMaxWidth = Int.MAX_VALUE
    private var mMaxHeight = Int.MAX_VALUE
    private var mAdjustViewBounds = false

    // width / height
    private var mAspect = -1f

    @SuppressLint("ResourceType")
    private fun init(context: Context, attrs: AttributeSet?, defStyle: Int) {
        // Make sure we get value from xml
        var a: TypedArray = context.obtainStyledAttributes(attrs, MIN_ATTRS, defStyle, 0)
        setMinimumWidth(a.getDimensionPixelSize(0, 0))
        setMinimumHeight(a.getDimensionPixelSize(1, 0))
        a.recycle()
        a = context.obtainStyledAttributes(attrs, ATTRS, defStyle, 0)
        setAdjustViewBounds(a.getBoolean(0, false))
        setMaxWidth(a.getDimensionPixelSize(1, Int.MAX_VALUE))
        setMaxHeight(a.getDimensionPixelSize(2, Int.MAX_VALUE))
        a.recycle()
        a = context.obtainStyledAttributes(
            attrs,
            R.styleable.FixedAspectImageView,
            defStyle,
            0,
        )
        aspect = a.getFloat(R.styleable.FixedAspectImageView_aspect, -1f)
        a.recycle()
    }

    override fun setMinimumWidth(minWidth: Int) {
        super.setMinimumWidth(minWidth)
        mMinWidth = minWidth
    }

    override fun setMinimumHeight(minHeight: Int) {
        super.setMinimumHeight(minHeight)
        mMinHeight = minHeight
    }

    override fun setMaxWidth(maxWidth: Int) {
        super.setMaxWidth(maxWidth)
        mMaxWidth = maxWidth
    }

    override fun setMaxHeight(maxHeight: Int) {
        super.setMaxHeight(maxHeight)
        mMaxHeight = maxHeight
    }

    override fun setAdjustViewBounds(adjustViewBounds: Boolean) {
        super.setAdjustViewBounds(adjustViewBounds)
        mAdjustViewBounds = adjustViewBounds
    }

    var aspect: Float
        get() = mAspect

        /**
         * Enable aspect will set AdjustViewBounds true.
         * Any negative float to disable it,
         * disable Aspect will not disable AdjustViewBounds.
         *
         * @param aspect width/height
         */
        set(aspect) {
            mAspect = if (aspect > 0) {
                aspect
            } else {
                -1f
            }
            requestLayout()
        }

    private fun resolveAdjustedSize(
        desiredSize: Int,
        minSize: Int,
        maxSize: Int,
        measureSpec: Int,
    ): Int {
        var result = desiredSize
        val specMode = MeasureSpec.getMode(measureSpec)
        val specSize = MeasureSpec.getSize(measureSpec)
        when (specMode) {
            MeasureSpec.UNSPECIFIED ->
                // Parent says we can be as big as we want. Just don't be smaller
                // than min size, and don't be larger than max size.
                result = MathUtils.clamp(desiredSize, minSize, maxSize)

            MeasureSpec.AT_MOST ->
                // Parent says we can be as big as we want, up to specSize.
                // Don't be larger than specSize, and don't be smaller
                // than min size, and don't be larger than max size.
                result = min(
                    MathUtils.clamp(desiredSize, minSize, maxSize).toDouble(),
                    specSize.toDouble(),
                ).toInt()

            MeasureSpec.EXACTLY ->
                // No choice. Do what we are told.
                result = specSize
        }
        return result
    }

    private fun isSizeAcceptable(size: Int, minSize: Int, maxSize: Int, measureSpec: Int): Boolean {
        val specMode = MeasureSpec.getMode(measureSpec)
        val specSize = MeasureSpec.getSize(measureSpec)
        return when (specMode) {
            MeasureSpec.UNSPECIFIED ->
                // Parent says we can be as big as we want. Just don't be smaller
                // than min size, and don't be larger than max size.
                size in minSize..maxSize

            MeasureSpec.AT_MOST ->
                // Parent says we can be as big as we want, up to specSize.
                // Don't be larger than specSize, and don't be smaller
                // than min size, and don't be larger than max size.
                size in minSize..specSize && size <= maxSize

            MeasureSpec.EXACTLY ->
                // No choice.
                size == specSize

            else -> // WTF? Return true to make you happy. (´・ω・`)
                true
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        var w: Int
        var h: Int

        // Desired aspect ratio of the view's contents (not including padding)
        var desiredAspect = 0.0f

        // We are allowed to change the view's width
        var resizeWidth = false

        // We are allowed to change the view's height
        var resizeHeight = false
        val widthSpecMode = MeasureSpec.getMode(widthMeasureSpec)
        val heightSpecMode = MeasureSpec.getMode(heightMeasureSpec)
        val drawable = getDrawable()
        if (drawable == null) {
            // If no drawable, its intrinsic size is 0.
            h = 0
            w = 0

            // Aspect is forced set.
            if (mAspect > 0.0f) {
                resizeWidth = widthSpecMode != MeasureSpec.EXACTLY
                resizeHeight = heightSpecMode != MeasureSpec.EXACTLY
                desiredAspect = mAspect
            }
        } else {
            w = drawable.intrinsicWidth
            h = drawable.intrinsicHeight
            if (w <= 0) w = 1
            if (h <= 0) h = 1
            if (mAdjustViewBounds) {
                // We are supposed to adjust view bounds to match the aspect
                // ratio of our drawable. See if that is possible.
                resizeWidth = widthSpecMode != MeasureSpec.EXACTLY
                resizeHeight = heightSpecMode != MeasureSpec.EXACTLY
                desiredAspect = w.toFloat() / h.toFloat()
            } else if (mAspect > 0.0f) {
                // Aspect is forced set.
                resizeWidth = widthSpecMode != MeasureSpec.EXACTLY
                resizeHeight = heightSpecMode != MeasureSpec.EXACTLY
                desiredAspect = mAspect
            }
        }
        val pLeft = paddingLeft
        val pRight = paddingRight
        val pTop = paddingTop
        val pBottom = paddingBottom
        var widthSize: Int
        var heightSize: Int
        if (resizeWidth || resizeHeight) {
            // If we get here, it means we want to resize to match the
            // drawables aspect ratio, and we have the freedom to change at
            // least one dimension.

            // Get the max possible width given our constraints
            widthSize =
                resolveAdjustedSize(w + pLeft + pRight, mMinWidth, mMaxWidth, widthMeasureSpec)

            // Get the max possible height given our constraints
            heightSize =
                resolveAdjustedSize(h + pTop + pBottom, mMinHeight, mMaxHeight, heightMeasureSpec)
            if (desiredAspect != 0.0f) {
                // See what our actual aspect ratio is
                val actualAspect = (widthSize - pLeft - pRight).toFloat() /
                    (heightSize - pTop - pBottom)
                if (abs((actualAspect - desiredAspect).toDouble()) > 0.0000001) {
                    var done = false

                    // Try adjusting width to be proportional to height
                    if (resizeWidth) {
                        val newWidth = (desiredAspect * (heightSize - pTop - pBottom)).toInt() +
                            pLeft + pRight

                        // Allow the width to outgrow its original estimate if height is fixed.
                        // if (!resizeHeight) {
                        // widthSize = resolveAdjustedSize(newWidth, mMinWidth, mMaxWidth, widthMeasureSpec);
                        // }
                        if (isSizeAcceptable(newWidth, mMinWidth, mMaxWidth, widthMeasureSpec)) {
                            widthSize = newWidth
                            done = true
                        }
                    }

                    // Try adjusting height to be proportional to width
                    if (!done && resizeHeight) {
                        val newHeight = ((widthSize - pLeft - pRight) / desiredAspect).toInt() +
                            pTop + pBottom

                        // Allow the height to outgrow its original estimate if width is fixed.
                        if (!resizeWidth) {
                            heightSize = resolveAdjustedSize(
                                newHeight,
                                mMinHeight,
                                mMaxHeight,
                                heightMeasureSpec,
                            )
                        }
                        if (isSizeAcceptable(
                                newHeight,
                                mMinHeight,
                                mMaxHeight,
                                heightMeasureSpec,
                            )
                        ) {
                            heightSize = newHeight
                        }
                    }
                }
            }
        } else {
            // We are either don't want to preserve the drawables aspect ratio,
            // or we are not allowed to change view dimensions. Just measure in
            // the normal way.
            w += pLeft + pRight
            h += pTop + pBottom
            w = max(w.toDouble(), suggestedMinimumWidth.toDouble()).toInt()
            h = max(h.toDouble(), suggestedMinimumHeight.toDouble()).toInt()
            widthSize = resolveSizeAndState(w, widthMeasureSpec, 0)
            heightSize = resolveSizeAndState(h, heightMeasureSpec, 0)
        }
        setMeasuredDimension(widthSize, heightSize)
    }

    companion object {
        private val MIN_ATTRS = intArrayOf(
            android.R.attr.minWidth,
            android.R.attr.minHeight,
        )
        private val ATTRS = intArrayOf(
            android.R.attr.adjustViewBounds,
            android.R.attr.maxWidth,
            android.R.attr.maxHeight,
        )
    }

    init {
        init(context, attrs, defStyle)
    }
}
