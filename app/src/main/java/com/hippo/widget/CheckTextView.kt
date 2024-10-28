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
package com.hippo.widget

import android.content.Context
import android.graphics.Canvas
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import androidx.appcompat.widget.AppCompatCheckedTextView
import com.hippo.ehviewer.R

open class CheckTextView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : AppCompatCheckedTextView(context, attrs, defStyleAttr),
    View.OnClickListener {
    private val mSelfBounds = Rect()
    private val mOverlayBounds = Rect()
    private var mForegroundInPadding = true
    private var mForegroundBoundsChanged = false
    private var mForeground: Drawable? = null
    private var mForegroundGravity = Gravity.FILL

    init {
        val a = context.obtainStyledAttributes(
            attrs,
            R.styleable.CheckTextView,
            defStyleAttr,
            0,
        )
        mForegroundGravity = a.getInt(
            R.styleable.CheckTextView_android_foregroundGravity,
            mForegroundGravity,
        )
        a.getDrawable(R.styleable.CheckTextView_android_foreground)?.let {
            foreground = it
        }
        mForegroundInPadding = a.getBoolean(
            R.styleable.CheckTextView_foregroundInsidePadding,
            true,
        )
        a.recycle()
        setOnClickListener(this)
    }

    /**
     * Describes how the foreground is positioned.
     *
     * @return foreground gravity.
     * @see .setForegroundGravity
     */
    override fun getForegroundGravity(): Int = mForegroundGravity

    /**
     * Describes how the foreground is positioned. Defaults to START and TOP.
     *
     * @param foregroundGravity See [android.view.Gravity]
     * @see .getForegroundGravity
     */
    override fun setForegroundGravity(foregroundGravity: Int) {
        var sForegroundGravity = foregroundGravity
        if (mForegroundGravity != sForegroundGravity) {
            if (sForegroundGravity and Gravity.RELATIVE_HORIZONTAL_GRAVITY_MASK == 0) {
                sForegroundGravity = sForegroundGravity or Gravity.START
            }
            if (sForegroundGravity and Gravity.VERTICAL_GRAVITY_MASK == 0) {
                sForegroundGravity = sForegroundGravity or Gravity.TOP
            }
            mForegroundGravity = sForegroundGravity
            if (mForegroundGravity == Gravity.FILL && mForeground != null) {
                val padding = Rect()
                mForeground!!.getPadding(padding)
            }
            requestLayout()
        }
    }

    override fun verifyDrawable(who: Drawable): Boolean = super.verifyDrawable(who) || who === mForeground

    override fun jumpDrawablesToCurrentState() {
        super.jumpDrawablesToCurrentState()
        if (mForeground != null) {
            mForeground!!.jumpToCurrentState()
        }
    }

    override fun drawableStateChanged() {
        super.drawableStateChanged()
        if (mForeground != null && mForeground!!.isStateful) {
            mForeground!!.state = drawableState
        }
    }

    /**
     * Returns the drawable used as the foreground of this FrameLayout. The
     * foreground drawable, if non-null, is always drawn on top of the children.
     *
     * @return A Drawable or null if no foreground was set.
     */
    override fun getForeground(): Drawable = mForeground!!

    /**
     * Supply a Drawable that is to be rendered on top of all of the child
     * views in the frame layout.  Any padding in the Drawable will be taken
     * into account by ensuring that the children are inset to be placed
     * inside of the padding area.
     *
     * @param drawable The Drawable to be drawn on top of the children.
     */
    override fun setForeground(drawable: Drawable?) {
        if (mForeground !== drawable) {
            mForeground?.let {
                it.callback = null
                unscheduleDrawable(it)
            }
            mForeground = drawable
            if (drawable != null) {
                setWillNotDraw(false)
                drawable.callback = this
                if (drawable.isStateful) {
                    drawable.state = drawableState
                }
                if (mForegroundGravity == Gravity.FILL) {
                    val padding = Rect()
                    drawable.getPadding(padding)
                }
            } else {
                setWillNotDraw(true)
            }
            requestLayout()
            invalidate()
        }
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        mForegroundBoundsChanged = mForegroundBoundsChanged or changed
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        mForegroundBoundsChanged = true
    }

    override fun draw(canvas: Canvas) {
        super.draw(canvas)
        if (mForeground != null) {
            val foreground: Drawable = mForeground!!
            if (mForegroundBoundsChanged) {
                mForegroundBoundsChanged = false
                val selfBounds = mSelfBounds
                val overlayBounds = mOverlayBounds
                val w = right - left
                val h = bottom - top
                if (mForegroundInPadding) {
                    selfBounds[0, 0, w] = h
                } else {
                    selfBounds[paddingLeft, paddingTop, w - paddingRight] = h - paddingBottom
                }
                Gravity.apply(
                    mForegroundGravity,
                    foreground.intrinsicWidth,
                    foreground.intrinsicHeight,
                    selfBounds,
                    overlayBounds,
                )
                foreground.bounds = overlayBounds
            }
            foreground.draw(canvas)
        }
    }

    override fun drawableHotspotChanged(x: Float, y: Float) {
        super.drawableHotspotChanged(x, y)
        if (mForeground != null) {
            mForeground!!.setHotspot(x, y)
        }
    }

    override fun onClick(v: View) {
        isChecked = !isChecked
    }
}
