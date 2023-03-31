/*
 * Copyright 2016 Hippo Seven
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
package com.hippo.glgallery

import androidx.annotation.CallSuper
import androidx.annotation.IntDef
import androidx.collection.lruCache
import com.hippo.ehviewer.Settings
import com.hippo.glview.glrenderer.GLCanvas
import com.hippo.glview.image.ImageWrapper
import com.hippo.glview.view.GLRoot
import com.hippo.glview.view.GLRoot.OnGLIdleListener
import com.hippo.image.Image
import com.hippo.yorozuya.ConcurrentPool
import com.hippo.yorozuya.MathUtils
import com.hippo.yorozuya.OSUtils

abstract class GalleryProvider {
    private val mNotifyTaskPool = ConcurrentPool<NotifyTask>(5)
    private val mImageCache = lruCache<Int, ImageWrapper>(
        maxSize = (OSUtils.getTotalMemory() / 12).toInt().coerceIn(MIN_CACHE_SIZE, MAX_CACHE_SIZE),
        sizeOf = { _, v -> v.width * v.height * if (v.animated) 20 else 4 },
        onEntryRemoved = { _, _, o, _ -> o.release() },
    )
    private val mPreloads = MathUtils.clamp(Settings.preloadImage, 0, 100)

    @Volatile
    private var mListener: Listener? = null

    @Volatile
    private var mGLRoot: GLRoot? = null

    abstract suspend fun awaitReady(): Boolean

    abstract val isReady: Boolean

    abstract fun start()

    @CallSuper
    open fun stop() {
        mImageCache.evictAll()
    }

    fun setGLRoot(glRoot: GLRoot) {
        mGLRoot = glRoot
    }

    abstract val size: Int

    fun request(index: Int) {
        mImageCache[index]?.let {
            notifyPageSucceed(index, it)
        } ?: onRequest(index)
        val pagesAbsent =
            ((index - 5).coerceAtLeast(0) until (mPreloads + index).coerceAtMost(size)).mapNotNull { it.takeIf { mImageCache[it] == null } }
        preloadPages(
            pagesAbsent,
            (index - 10).coerceAtLeast(0) to (mPreloads + index + 10).coerceAtMost(
                size,
            ),
        )
    }

    fun forceRequest(index: Int) {
        onForceRequest(index)
    }

    fun removeCache(index: Int) {
        mImageCache.remove(index)
    }

    protected abstract fun preloadPages(pages: List<Int>, pair: Pair<Int, Int>)

    protected abstract fun onRequest(index: Int)

    protected abstract fun onForceRequest(index: Int)

    fun cancelRequest(index: Int) {
        onCancelRequest(index)
    }

    protected abstract fun onCancelRequest(index: Int)

    fun setListener(listener: Listener?) {
        mListener = listener
    }

    fun notifyDataChanged(index: Int) {
        notify(NotifyTask.TYPE_DATA_CHANGED, index, 0.0f, null, null)
    }

    fun notifyPageWait(index: Int) {
        notify(NotifyTask.TYPE_WAIT, index, 0.0f, null, null)
    }

    fun notifyPagePercent(index: Int, percent: Float) {
        notify(NotifyTask.TYPE_PERCENT, index, percent, null, null)
    }

    fun notifyPageSucceed(index: Int, image: Image) {
        val imageWrapper = ImageWrapper(image)
        if (imageWrapper.obtain()) mImageCache.put(index, imageWrapper)
        notifyPageSucceed(index, imageWrapper)
    }

    private fun notifyPageSucceed(index: Int, image: ImageWrapper) {
        notify(NotifyTask.TYPE_SUCCEED, index, 0.0f, image, null)
    }

    fun notifyPageFailed(index: Int, error: String?) {
        notify(NotifyTask.TYPE_FAILED, index, 0.0f, null, error)
    }

    private fun notify(
        @NotifyTask.Type type: Int,
        index: Int,
        percent: Float,
        image: ImageWrapper?,
        error: String?,
    ) {
        val listener = mListener ?: return
        val glRoot = mGLRoot ?: return
        val task = mNotifyTaskPool.pop() ?: NotifyTask(listener, mNotifyTaskPool)
        task.setData(type, index, percent, image, error)
        glRoot.addOnGLIdleListener(task)
    }

    interface Listener {
        fun onDataChanged()
        fun onPageWait(index: Int)
        fun onPagePercent(index: Int, percent: Float)
        fun onPageSucceed(index: Int, image: ImageWrapper?)
        fun onPageFailed(index: Int, error: String?)
        fun onDataChanged(index: Int)
    }

    private class NotifyTask(
        private val mListener: Listener,
        private val mPool: ConcurrentPool<NotifyTask>,
    ) : OnGLIdleListener {
        @Type
        private var mType = 0
        private var mIndex = 0
        private var mPercent = 0f
        private var mImage: ImageWrapper? = null
        private var mError: String? = null
        fun setData(
            @Type type: Int,
            index: Int,
            percent: Float,
            image: ImageWrapper?,
            error: String?,
        ) {
            mType = type
            mIndex = index
            mPercent = percent
            mImage = image
            mError = error
        }

        override fun onGLIdle(canvas: GLCanvas, renderRequested: Boolean): Boolean {
            when (mType) {
                TYPE_DATA_CHANGED -> if (mIndex < 0) {
                    mListener.onDataChanged()
                } else {
                    mListener.onDataChanged(mIndex)
                }

                TYPE_WAIT -> mListener.onPageWait(mIndex)
                TYPE_PERCENT -> mListener.onPagePercent(mIndex, mPercent)
                TYPE_SUCCEED -> mListener.onPageSucceed(mIndex, mImage)
                TYPE_FAILED -> mListener.onPageFailed(mIndex, mError)
            }

            // Clean data
            mImage = null
            mError = null
            // Push back
            mPool.push(this)
            return false
        }

        @IntDef(TYPE_DATA_CHANGED, TYPE_WAIT, TYPE_PERCENT, TYPE_SUCCEED, TYPE_FAILED)
        @Retention(
            AnnotationRetention.SOURCE,
        )
        annotation class Type
        companion object {
            const val TYPE_DATA_CHANGED = 0
            const val TYPE_WAIT = 1
            const val TYPE_PERCENT = 2
            const val TYPE_SUCCEED = 3
            const val TYPE_FAILED = 4
        }
    }

    companion object {
        private const val MAX_CACHE_SIZE = 512 * 1024 * 1024
        private const val MIN_CACHE_SIZE = 256 * 1024 * 1024
    }
}
