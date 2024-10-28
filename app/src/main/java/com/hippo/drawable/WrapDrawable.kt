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
package com.hippo.drawable

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.Drawable

open class WrapDrawable : Drawable() {
    open var drawable: Drawable? = null

    fun updateBounds() {
        setBounds(0, 0, intrinsicWidth, intrinsicHeight)
    }

    override fun draw(canvas: Canvas) {
        drawable?.draw(canvas)
    }

    override fun setBounds(left: Int, top: Int, right: Int, bottom: Int) {
        super.setBounds(left, top, right, bottom)
        drawable?.setBounds(left, top, right, bottom)
    }

    override fun setBounds(bounds: Rect) {
        super.bounds = bounds
        drawable?.bounds = bounds
    }

    override fun getChangingConfigurations(): Int = drawable?.changingConfigurations ?: super.changingConfigurations

    override fun setChangingConfigurations(configs: Int) {
        super.changingConfigurations = configs
        drawable?.changingConfigurations = configs
    }

    override fun setFilterBitmap(filter: Boolean) {
        super.setFilterBitmap(filter)
        drawable?.setFilterBitmap(filter)
    }

    override fun setAlpha(alpha: Int) {
        drawable?.alpha = alpha
    }

    override fun setColorFilter(cf: ColorFilter?) {
        drawable?.colorFilter = cf
    }

    @Deprecated(
        "Deprecated in Java",
        ReplaceWith("PixelFormat.TRANSLUCENT", "android.graphics.PixelFormat"),
    )
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT

    override fun getIntrinsicWidth(): Int = drawable?.intrinsicWidth ?: super.intrinsicWidth

    override fun getIntrinsicHeight(): Int = drawable?.intrinsicHeight ?: super.intrinsicHeight
}
