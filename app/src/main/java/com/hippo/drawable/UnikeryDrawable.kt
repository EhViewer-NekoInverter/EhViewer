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

import android.graphics.drawable.Animatable
import android.graphics.drawable.Drawable
import coil.imageLoader
import coil.request.Disposable
import coil.request.ImageRequest
import com.hippo.widget.ObservedTextView

class UnikeryDrawable(private val mTextView: ObservedTextView) :
    WrapDrawable(),
    ObservedTextView.OnWindowAttachListener {
    private var mUrl: String? = null
    private var task: Disposable? = null

    init {
        mTextView.setOnWindowAttachListener(this)
    }

    override fun onAttachedToWindow() {
        load(mUrl)
    }

    override fun onDetachedFromWindow() {
        if (task != null && !task!!.isDisposed) task!!.dispose()
        clearDrawable()
    }

    fun load(url: String?) {
        if (url != null) {
            mUrl = url
            val request =
                ImageRequest.Builder(mTextView.context).data(url)
                    .memoryCacheKey(url)
                    .diskCacheKey(url)
                    .target(
                        { },
                        { },
                        { drawable: Drawable ->
                            onGetValue(drawable)
                        },
                    ).build()
            task = mTextView.context.imageLoader.enqueue(request)
        }
    }

    private fun clearDrawable() {
        drawable = null
    }

    override var drawable: Drawable?
        get() = super.drawable
        set(newDrawable) {
            // Remove old callback
            val oldDrawable = drawable
            oldDrawable?.callback = null
            super.drawable = newDrawable
            newDrawable?.callback = mTextView
            updateBounds()
            if (newDrawable != null) {
                invalidateSelf()
            }
        }

    override fun invalidateSelf() {
        val cs = mTextView.getText()
        mTextView.text = cs
    }

    private fun onGetValue(newDrawable: Drawable) {
        clearDrawable()
        drawable = newDrawable
        (newDrawable as? Animatable)?.start()
    }
}
