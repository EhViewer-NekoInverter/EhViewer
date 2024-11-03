/*
 * Copyright 2015-2016 Hippo Seven
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
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.View
import androidx.annotation.DrawableRes
import androidx.annotation.IntDef
import androidx.core.content.ContextCompat
import coil.load
import coil.size.Size
import com.hippo.drawable.PreciselyClipDrawable
import com.hippo.ehviewer.R

open class LoadImageView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : FixedAspectImageView(context, attrs, defStyleAttr),
    View.OnClickListener,
    View.OnLongClickListener {
    private var mOffsetX = Int.MIN_VALUE
    private var mOffsetY = Int.MIN_VALUE
    private var mClipWidth = Int.MIN_VALUE
    private var mClipHeight = Int.MIN_VALUE
    private var mKey: String? = null
    private var mUrl: String? = null
    private var mCrossfade = true
    private var mHardware = true

    @RetryType
    private val mRetryType: Int =
        context.obtainStyledAttributes(attrs, R.styleable.LoadImageView, defStyleAttr, 0).run {
            getInt(R.styleable.LoadImageView_retryType, 0).also { recycle() }
        }

    private fun setRetry(canRetry: Boolean) {
        when (mRetryType) {
            RETRY_TYPE_CLICK -> {
                setOnClickListener(if (canRetry) this else null)
                isClickable = canRetry
            }

            RETRY_TYPE_LONG_CLICK -> {
                setOnLongClickListener(if (canRetry) this else null)
                isLongClickable = canRetry
            }

            RETRY_TYPE_NONE -> {}
        }
    }

    fun setClip(offsetX: Int, offsetY: Int, clipWidth: Int, clipHeight: Int) {
        mOffsetX = offsetX
        mOffsetY = offsetY
        mClipWidth = clipWidth
        mClipHeight = clipHeight
    }

    fun resetClip() {
        mOffsetX = Int.MIN_VALUE
        mOffsetY = Int.MIN_VALUE
        mClipWidth = Int.MIN_VALUE
        mClipHeight = Int.MIN_VALUE
    }

    fun load(
        key: String,
        url: String,
        crossfade: Boolean = true,
        hardware: Boolean = true,
    ) {
        mKey = key
        mUrl = url
        mCrossfade = crossfade
        mHardware = hardware
        load(url) {
            // https://coil-kt.github.io/coil/recipes/#shared-element-transitions
            allowHardware(hardware)
            data(url)
            memoryCacheKey(key)
            diskCacheKey(key)
            size(Size.ORIGINAL)
            if (!crossfade) crossfade(false)
            listener(
                { setRetry(false) },
                { setRetry(true) },
                { _, _ ->
                    val errorDrawable = ContextCompat.getDrawable(context, R.drawable.image_failed)
                    onPreSetImageDrawable(errorDrawable, true)
                    super.setImageDrawable(errorDrawable)
                    setRetry(true)
                },
                { _, _ -> setRetry(false) },
            )
        }
    }

    fun load(@DrawableRes id: Int) {
        onPreSetImageResource(id, true)
        setImageResource(id)
    }

    private fun reload() {
        mKey?.let { this.load(it, mUrl!!, mCrossfade, mHardware) }
    }

    override fun setImageDrawable(drawable: Drawable?) {
        var newDrawable = drawable
        if (newDrawable != null) {
            if (Int.MIN_VALUE != mOffsetX) {
                newDrawable =
                    PreciselyClipDrawable(newDrawable, mOffsetX, mOffsetY, mClipWidth, mClipHeight)
            }
            onPreSetImageDrawable(newDrawable, true)
        }
        super.setImageDrawable(newDrawable)
    }

    override fun getDrawable(): Drawable? {
        var newDrawable = super.getDrawable()
        if (newDrawable is PreciselyClipDrawable) {
            newDrawable = newDrawable.drawable
        }
        return newDrawable
    }

    override fun onClick(v: View) {
        reload()
    }

    override fun onLongClick(v: View): Boolean {
        reload()
        return true
    }

    open fun onPreSetImageDrawable(drawable: Drawable?, isTarget: Boolean) {}

    open fun onPreSetImageResource(resId: Int, isTarget: Boolean) {}

    @IntDef(RETRY_TYPE_NONE, RETRY_TYPE_CLICK, RETRY_TYPE_LONG_CLICK)
    @Retention(AnnotationRetention.SOURCE)
    private annotation class RetryType

    companion object {
        const val RETRY_TYPE_NONE = 0
        const val RETRY_TYPE_CLICK = 1
        const val RETRY_TYPE_LONG_CLICK = 2
    }
}
