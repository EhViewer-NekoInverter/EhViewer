/*
 * Copyright 2022 Tarsin Norbin
 *
 * This file is part of EhViewer
 *
 * EhViewer is free software: you can redistribute it and/or
 * modify it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * EhViewer is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with EhViewer.
 * If not, see <https://www.gnu.org/licenses/>.
 */
package com.hippo.image

import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ImageDecoder
import android.graphics.ImageDecoder.ALLOCATOR_SOFTWARE
import android.graphics.ImageDecoder.ImageInfo
import android.graphics.ImageDecoder.Source
import android.graphics.PorterDuff
import android.graphics.drawable.Animatable
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.core.graphics.drawable.toDrawable
import com.hippo.ehviewer.EhApplication
import java.nio.ByteBuffer
import kotlin.math.min

class Image private constructor(
    private val src: CloseableSource? = null,
    drawable: Drawable? = null,
) {
    private var mObtainedDrawable: Drawable?
    private var mBitmap: Bitmap? = null
    private var mCanvas: Canvas? = null

    init {
        mObtainedDrawable = null
        src?.let { src ->
            mObtainedDrawable =
                ImageDecoder.decodeDrawable(src.source) { decoder: ImageDecoder, info: ImageInfo, _: Source ->
                    decoder.allocator = ALLOCATOR_SOFTWARE
                    decoder.setTargetSampleSize(
                        calculateSampleSize(info, 2 * screenHeight, 2 * screenWidth),
                    )
                }.also {
                    if (it !is Animatable) src.close()
                }
        }
        if (mObtainedDrawable == null) {
            mObtainedDrawable = drawable!!
        }
    }

    val animated = mObtainedDrawable is Animatable
    val width = mObtainedDrawable!!.intrinsicWidth
    val height = mObtainedDrawable!!.intrinsicHeight
    val isRecycled = mObtainedDrawable == null
    var started = false

    @Synchronized
    fun recycle() {
        mObtainedDrawable ?: return
        (mObtainedDrawable as? Animatable)?.stop()
        (mObtainedDrawable as? BitmapDrawable)?.bitmap?.recycle()
        mObtainedDrawable?.callback = null
        if (mObtainedDrawable is Animatable) src?.close()
        mObtainedDrawable = null
        mCanvas = null
        mBitmap?.recycle()
        mBitmap = null
    }

    private fun prepareBitmap() {
        if (mBitmap != null) return
        mBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        mCanvas = Canvas(mBitmap!!)
    }

    private fun updateBitmap() {
        prepareBitmap()
        mCanvas!!.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
        mObtainedDrawable!!.draw(mCanvas!!)
    }

    fun texImage(init: Boolean, offsetX: Int, offsetY: Int, width: Int, height: Int) {
        val bitmap: Bitmap? = if (animated) {
            updateBitmap()
            mBitmap
        } else {
            (mObtainedDrawable as BitmapDrawable?)?.bitmap
        }
        bitmap ?: return
        nativeTexImage(
            bitmap,
            init,
            offsetX,
            offsetY,
            width,
            height,
        )
    }

    fun start() {
        if (!started) {
            started = true
            (mObtainedDrawable as Animatable?)?.start()
        }
    }

    val delay: Int
        get() {
            return if (animated) 10 else 0
        }

    val isOpaque: Boolean
        get() {
            return false
        }

    companion object {
        fun calculateSampleSize(info: ImageInfo, targetHeight: Int, targetWeight: Int): Int = min(
            info.size.width / targetWeight,
            info.size.height / targetHeight,
        ).coerceAtLeast(1)

        val screenWidth = EhApplication.application.resources.displayMetrics.widthPixels
        val screenHeight = EhApplication.application.resources.displayMetrics.heightPixels

        @JvmStatic
        fun decode(src: CloseableSource): Image? = runCatching {
            Image(src)
        }.onFailure {
            src.close()
            it.printStackTrace()
        }.getOrNull()

        @JvmStatic
        fun create(bitmap: Bitmap): Image = Image(drawable = bitmap.toDrawable(Resources.getSystem()))
    }

    interface CloseableSource : AutoCloseable {
        val source: Source
    }
}

private external fun nativeTexImage(
    bitmap: Bitmap,
    init: Boolean,
    offsetX: Int,
    offsetY: Int,
    width: Int,
    height: Int,
)
external fun rewriteGifSource(buffer: ByteBuffer)
external fun rewriteGifSource2(fd: Int)
