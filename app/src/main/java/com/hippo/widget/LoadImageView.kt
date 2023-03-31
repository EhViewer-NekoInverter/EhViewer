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
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.TransitionDrawable
import android.util.AttributeSet
import android.view.View
import androidx.annotation.DrawableRes
import androidx.annotation.IntDef
import androidx.core.content.ContextCompat
import coil.imageLoader
import coil.request.Disposable
import coil.request.ImageRequest
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
    private var mRequest: ImageRequest? = null
    private var mFailed = false
    private var mStarted = false
    private var mRetryType = 0
    private var mLoadFromDrawable = false
    private var task: Disposable? = null
    private val imageDrawable: Drawable?
        get() {
            var drawable = drawable
            if (drawable is TransitionDrawable) {
                if (drawable.numberOfLayers == 2) {
                    drawable = drawable.getDrawable(1)
                }
            }
            if (drawable is PreciselyClipDrawable) {
                drawable = drawable.drawable
            }
            return drawable
        }

    init {
        val a = context.obtainStyledAttributes(attrs, R.styleable.LoadImageView, defStyleAttr, 0)
        setRetryType(a.getInt(R.styleable.LoadImageView_retryType, 0))
        a.recycle()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (!mLoadFromDrawable) {
            if (mFailed) {
                onFailure()
            } else if (!mStarted) {
                task?.run { if (isDisposed) reload() }
            }
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        if (!mLoadFromDrawable) {
            mStarted = false
            task?.dispose()
            clearDrawable()
        }
    }

    private fun clearDrawable() {
        // Set drawable null
        setImageDrawable(null)
    }

    private fun clearRetry() {
        if (mRetryType == RETRY_TYPE_CLICK) {
            setOnClickListener(null)
            isClickable = false
        } else if (mRetryType == RETRY_TYPE_LONG_CLICK) {
            setOnLongClickListener(null)
            isLongClickable = false
        }
    }

    private fun setRetryType(@RetryType retryType: Int) {
        if (mRetryType != retryType) {
            val oldRetryType = mRetryType
            mRetryType = retryType
            if (mFailed) {
                if (oldRetryType == RETRY_TYPE_CLICK) {
                    setOnClickListener(null)
                    isClickable = false
                } else if (oldRetryType == RETRY_TYPE_LONG_CLICK) {
                    setOnLongClickListener(null)
                    isLongClickable = false
                }
                if (retryType == RETRY_TYPE_CLICK) {
                    setOnClickListener(this)
                } else if (retryType == RETRY_TYPE_LONG_CLICK) {
                    setOnLongClickListener(this)
                }
            }
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

    fun load(key: String, url: String, crossfade: Boolean = true) {
        if ((mRequest?.data as? String?) == url) return
        clearRetry()
        task?.dispose()
        mRequest =
            ImageRequest.Builder(context).data(url)
                .memoryCacheKey(key)
                .diskCacheKey(key)
                .size(Size.ORIGINAL)
                .target(
                    { onWait() },
                    { onFailure() },
                    { onGetValue(it, crossfade) },
                ).build().also { load(it) }
    }

    fun load(request: ImageRequest) {
        mFailed = false
        mStarted = true
        task = context.imageLoader.enqueue(request)
    }

    private fun reload() {
        mRequest?.let { load(it) }
    }

    fun load(@DrawableRes id: Int) {
        task?.dispose()
        clearDrawable()
        mLoadFromDrawable = true
        onPreSetImageResource(id, true)
        setImageResource(id)
    }

    private fun onWait() {
        clearDrawable()
    }

    private fun onGetValue(drawable: Drawable, crossfade: Boolean) {
        var mDrawable = drawable
        clearDrawable()
        if (Int.MIN_VALUE != mOffsetX) {
            mDrawable = PreciselyClipDrawable(mDrawable, mOffsetX, mOffsetY, mClipWidth, mClipHeight)
        }
        onPreSetImageDrawable(mDrawable, true)
        if (isShown && crossfade) {
            val layers = arrayOfNulls<Drawable>(2)
            layers[0] = ColorDrawable(Color.TRANSPARENT)
            layers[1] = mDrawable
            val transitionDrawable = TransitionDrawable(layers)
            setImageDrawable(transitionDrawable)
            transitionDrawable.startTransition(300)
        } else {
            setImageDrawable(mDrawable)
        }
    }

    fun onFailure() {
        mFailed = true
        mStarted = false
        clearDrawable()
        val drawable = ContextCompat.getDrawable(context, R.drawable.image_failed)
        onPreSetImageDrawable(drawable, true)
        setImageDrawable(drawable)
        when (mRetryType) {
            RETRY_TYPE_CLICK -> {
                setOnClickListener(this)
            }
            RETRY_TYPE_LONG_CLICK -> {
                setOnLongClickListener(this)
            }
            else -> {
                mRequest = null
            }
        }
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
