/*
 * Copyright (C) 2014 Hippo Seven
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

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.Drawable
import com.hippo.yorozuya.MathUtils
import kotlin.math.sqrt

class BatteryDrawable : Drawable() {
    private val mPaint: Paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG)
    private val mTopRect: Rect
    private val mBottomRect: Rect
    private val mRightRect: Rect
    private val mHeadRect: Rect
    private val mElectRect: Rect
    private var mColor = Color.WHITE
    private var mWarningColor = Color.RED
    private var mElect = -1
    private var mStart = 0
    private var mStop = 0

    init {
        mPaint.style = Paint.Style.FILL
        mTopRect = Rect()
        mBottomRect = Rect()
        mRightRect = Rect()
        mHeadRect = Rect()
        mElectRect = Rect()
        updatePaint()
    }

    override fun onBoundsChange(bounds: Rect) {
        val width = bounds.width()
        val height = bounds.height()
        val strokeWidth = (sqrt((width * width + height * height).toDouble()) * 0.06f).toInt()
        val turn1 = width * 6 / 7
        val turn2 = height / 3
        val secBottom = height - strokeWidth
        mStart = strokeWidth
        mStop = turn1 - strokeWidth
        mTopRect[0, 0, turn1] = strokeWidth
        mBottomRect[0, secBottom, turn1] = height
        mRightRect[turn1 - strokeWidth, strokeWidth, turn1] = secBottom
        mHeadRect[turn1, turn2, width] = height - turn2
        mElectRect[0, strokeWidth, mStop] = secBottom
    }

    /**
     * How to draw:
     * |------------------------------|
     * |\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\|
     * |------------------------------|---|
     * |/////////////////|         |//|\\\|
     * |/////////////////|         |//|\\\|
     * |------------------------------|---|
     * |\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\|
     * |------------------------------|
     */
    override fun draw(canvas: Canvas) {
        if (mElect == -1) {
            return
        }
        mElectRect.right = MathUtils.lerp(mStart, mStop, mElect / 100.0f)
        canvas.drawRect(mTopRect, mPaint)
        canvas.drawRect(mBottomRect, mPaint)
        canvas.drawRect(mRightRect, mPaint)
        canvas.drawRect(mHeadRect, mPaint)
        canvas.drawRect(mElectRect, mPaint)
    }

    private val isWarn: Boolean
        get() = mElect <= WARN_LIMIT

    fun setColor(color: Int) {
        if (mColor == color) {
            return
        }
        mColor = color
        if (!isWarn) {
            mPaint.setColor(mColor)
            invalidateSelf()
        }
    }

    fun setWarningColor(color: Int) {
        if (mWarningColor == color) {
            return
        }
        mWarningColor = color
        if (isWarn) {
            mPaint.setColor(mWarningColor)
            invalidateSelf()
        }
    }

    fun setElect(elect: Int) {
        if (mElect == elect) {
            return
        }
        mElect = elect
        updatePaint()
    }

    fun setElect(elect: Int, warn: Boolean) {
        if (mElect == elect) {
            return
        }
        mElect = elect
        updatePaint(warn)
    }

    private fun updatePaint(warn: Boolean = isWarn) {
        if (warn) {
            mPaint.setColor(mWarningColor)
        } else {
            mPaint.setColor(mColor)
        }
        invalidateSelf()
    }

    override fun setAlpha(alpha: Int) {
        mPaint.setAlpha(alpha)
    }

    override fun setColorFilter(cf: ColorFilter?) {
        mPaint.setColorFilter(cf)
    }

    @Deprecated(
        "Deprecated in Java",
        ReplaceWith("PixelFormat.TRANSLUCENT", "android.graphics.PixelFormat"),
    )
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT

    companion object {
        const val WARN_LIMIT = 15
    }
}
