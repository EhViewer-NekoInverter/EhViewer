/*
 * Copyright 2019 Hippo Seven
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
package com.hippo.ehviewer.widget

import android.content.Context
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import androidx.core.content.withStyledAttributes
import com.hippo.ehviewer.R
import com.hippo.widget.LoadImageView

open class FixedThumb @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : LoadImageView(context, attrs, defStyleAttr) {
    private var minAspect = 0f
    private var maxAspect = 0f
    private var alwaysCutAndScale = false

    init {
        context.withStyledAttributes(attrs, R.styleable.FixedThumb, defStyleAttr, defStyleAttr) {
            minAspect = getFloat(R.styleable.FixedThumb_minAspect, 0f)
            maxAspect = getFloat(R.styleable.FixedThumb_maxAspect, 0f)
            alwaysCutAndScale = getBoolean(R.styleable.FixedThumb_alwaysCutAndScale, false)
        }
    }

    override fun onPreSetImageDrawable(drawable: Drawable?, isTarget: Boolean) {
        if (alwaysCutAndScale) {
            setScaleType(ScaleType.CENTER_CROP)
            return
        }
        if (isTarget && drawable != null) {
            val width = drawable.intrinsicWidth
            val height = drawable.intrinsicHeight
            if (width > 0 && height > 0) {
                val aspect = width.toFloat() / height.toFloat()
                if (aspect < maxAspect && aspect > minAspect) {
                    setScaleType(ScaleType.CENTER_CROP)
                    return
                }
            }
        }

        setScaleType(ScaleType.FIT_CENTER)
    }

    override fun onPreSetImageResource(resId: Int, isTarget: Boolean) {
        setScaleType(ScaleType.FIT_CENTER)
    }
}
