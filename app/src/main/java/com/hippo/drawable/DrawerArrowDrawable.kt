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
package com.hippo.drawable

import android.animation.ObjectAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
import androidx.annotation.ColorInt
import androidx.annotation.Keep
import com.hippo.ehviewer.R
import com.hippo.yorozuya.MathUtils
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * A drawable that can draw a "Drawer hamburger" menu or an Arrow and animate between them.
 * @param context used to get the configuration for the drawable from
*/
class DrawerArrowDrawable(context: Context, color: Int) : Drawable() {
    private val mPaint = Paint()
    private val mBarThickness: Float

    // The length of top and bottom bars when they merge into an arrow
    private val mTopBottomArrowSize: Float

    // The length of middle bar
    private val mBarSize: Float

    // The length of the middle bar when arrow is shaped
    private val mMiddleArrowSize: Float

    // The space between bars when they are parallel
    private val mBarGap: Float

    // Whether bars should spin or not during progress
    private val mSpin: Boolean

    // Use Path instead of canvas operations so that if color has transparency, overlapping sections
    // wont look different
    private val mPath = Path()

    // The reported intrinsic size of the drawable.
    private val mSize: Int

    // the amount that overlaps w/ bar size when rotation is max
    private val mMaxCutForBarSize: Float

    // Whether we should mirror animation when animation is reversed.
    private var mVerticalMirror = false

    // The interpolated version of the original progress
    private var mProgress = 0f

    init {
        val resources = context.resources
        mPaint.isAntiAlias = true
        mPaint.setColor(color)
        mSize = resources.getDimensionPixelSize(R.dimen.dad_drawable_size)
        // round this because having this floating may cause bad measurements
        mBarSize = resources.getDimension(R.dimen.dad_bar_size).roundToInt().toFloat()
        // round this because having this floating may cause bad measurements
        mTopBottomArrowSize =
            resources.getDimension(R.dimen.dad_top_bottom_bar_arrow_size).roundToInt().toFloat()
        mBarThickness = resources.getDimension(R.dimen.dad_thickness)
        // round this because having this floating may cause bad measurements
        mBarGap = resources.getDimension(R.dimen.dad_gap_between_bars).roundToInt().toFloat()
        mSpin = resources.getBoolean(R.bool.dad_spin_bars)
        mMiddleArrowSize = resources.getDimension(R.dimen.dad_middle_bar_arrow_size)
        mPaint.style = Paint.Style.STROKE
        mPaint.strokeJoin = Paint.Join.MITER
        mPaint.strokeCap = Paint.Cap.BUTT
        mPaint.strokeWidth = mBarThickness
        mMaxCutForBarSize = (mBarThickness / 2 * cos(ARROW_HEAD_ANGLE.toDouble())).toFloat()
    }

    /**
     * If set, canvas is flipped when progress reached to end and going back to start.
     */
    private fun setVerticalMirror(verticalMirror: Boolean) {
        mVerticalMirror = verticalMirror
    }

    override fun draw(canvas: Canvas) {
        val bounds = getBounds()
        // Interpolated widths of arrow bars
        val arrowSize = MathUtils.lerp(mBarSize, mTopBottomArrowSize, mProgress)
        val middleBarSize = MathUtils.lerp(mBarSize, mMiddleArrowSize, mProgress)
        // Interpolated size of middle bar
        val middleBarCut = MathUtils.lerp(0f, mMaxCutForBarSize, mProgress).roundToInt().toFloat()
        // The rotation of the top and bottom bars (that make the arrow head)
        val rotation = MathUtils.lerp(0f, ARROW_HEAD_ANGLE, mProgress)

        // The whole canvas rotates as the transition happens
        val canvasRotate = MathUtils.lerp(-180, 0, mProgress).toFloat()
        val arrowWidth = (arrowSize * cos(rotation.toDouble())).roundToInt().toFloat()
        val arrowHeight = (arrowSize * sin(rotation.toDouble())).roundToInt().toFloat()
        mPath.rewind()
        val topBottomBarOffset = MathUtils.lerp(
            mBarGap + mBarThickness,
            -mMaxCutForBarSize,
            mProgress,
        )
        val arrowEdge = -middleBarSize / 2
        // draw middle bar
        mPath.moveTo(arrowEdge + middleBarCut, 0f)
        mPath.rLineTo(middleBarSize - middleBarCut * 2, 0f)

        // bottom bar
        mPath.moveTo(arrowEdge, topBottomBarOffset)
        mPath.rLineTo(arrowWidth, arrowHeight)

        // top bar
        mPath.moveTo(arrowEdge, -topBottomBarOffset)
        mPath.rLineTo(arrowWidth, -arrowHeight)
        mPath.close()
        canvas.save()
        // Rotate the whole canvas if spinning, if not, rotate it 180 to get
        // the arrow pointing the other way for RTL.
        canvas.translate(bounds.centerX().toFloat(), bounds.centerY().toFloat())
        if (mSpin) {
            canvas.rotate(canvasRotate * if (mVerticalMirror) -1 else 1)
        }
        canvas.drawPath(mPath, mPaint)
        canvas.restore()
    }

    fun setColor(@ColorInt color: Int) {
        mPaint.setColor(color)
        invalidateSelf()
    }

    override fun setAlpha(i: Int) {
        mPaint.setAlpha(i)
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        mPaint.setColorFilter(colorFilter)
    }

    override fun getIntrinsicHeight(): Int = mSize

    override fun getIntrinsicWidth(): Int = mSize

    @Deprecated(
        "Deprecated in Java",
        ReplaceWith("PixelFormat.TRANSLUCENT", "android.graphics.PixelFormat"),
    )
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT

    @get:Keep
    @set:Keep
    var progress: Float
        get() = mProgress
        set(progress) {
            if (progress == 1f) {
                setVerticalMirror(true)
            } else if (progress == 0f) {
                setVerticalMirror(false)
            }
            mProgress = progress
            invalidateSelf()
        }

    fun setMenu(duration: Long) {
        setShape(false, duration)
    }

    fun setArrow(duration: Long) {
        setShape(true, duration)
    }

    private fun setShape(arrow: Boolean, duration: Long) {
        if (!(!arrow && mProgress == 0f || arrow && mProgress == 1f)) {
            val endProgress = if (arrow) 1f else 0f
            if (duration <= 0) {
                progress = endProgress
            } else {
                val oa = ObjectAnimator.ofFloat(this, "progress", endProgress)
                oa.setDuration(duration)
                oa.setAutoCancel(true)
                oa.start()
            }
        }
    }

    companion object {
        // The angle in degrees that the arrow head is inclined at.
        private val ARROW_HEAD_ANGLE = Math.toRadians(45.0).toFloat()
    }
}
