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
@file:Suppress("unused")

package com.hippo.image

import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ImageDecoder
import android.graphics.ImageDecoder.ImageInfo
import android.graphics.ImageDecoder.Source
import android.graphics.PorterDuff
import android.graphics.drawable.Animatable
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.core.graphics.drawable.toDrawable
import com.hippo.ehviewer.EhApplication
import com.hippo.unifile.UniFile
import com.hippo.util.isAtLeastU
import java.nio.ByteBuffer
import kotlin.math.min

class Image private constructor(
    drawable: Drawable,
    private val src: AutoCloseable? = null,
) {
    private var mObtainedDrawable: Drawable? = drawable
    private var mBitmap: Bitmap? = null
    private var mCanvas: Canvas? = null

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
            (mObtainedDrawable as? BitmapDrawable)?.bitmap
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
            (mObtainedDrawable as? Animatable)?.start()
        }
    }

    val delay: Int
        get() {
            return if (animated) 20 else 0
        }

    val isOpaque: Boolean
        get() {
            return false
        }

    companion object {
        private val appCtx = EhApplication.application
        private val targetWidth = appCtx.resources.displayMetrics.widthPixels * 2
        private val targetHeight = appCtx.resources.displayMetrics.heightPixels * 2

        @Suppress("SameParameterValue")
        private fun calculateSampleSize(info: ImageInfo, targetHeight: Int, targetWeight: Int): Int = min(
            info.size.width / targetWeight,
            info.size.height / targetHeight,
        ).coerceAtLeast(1)

        private fun decodeDrawable(src: Source) = ImageDecoder.decodeDrawable(src) { decoder, info, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            decoder.setTargetSampleSize(
                calculateSampleSize(info, targetHeight, targetWidth),
            )
        }

        fun decode(src: AutoCloseable): Image? {
            return runCatching {
                when (src) {
                    is UniFileSource -> {
                        if (!isAtLeastU) {
                            src.source.openFileDescriptor("rw").use {
                                val fd = it.fd
                                if (isGif(fd)) {
                                    val buffer = mmap(fd)!!
                                    val source = object : ByteBufferSource {
                                        override val source = buffer
                                        override fun close() {
                                            munmap(buffer)
                                            src.close()
                                        }
                                    }
                                    return decode(source)
                                }
                            }
                        }
                        val drawable = decodeDrawable(src.source.imageSource)
                        if (drawable !is Animatable) src.close()
                        Image(drawable, src)
                    }

                    is ByteBufferSource -> {
                        if (!isAtLeastU) {
                            rewriteGifSource(src.source)
                        }
                        val source = ImageDecoder.createSource(src.source)
                        val drawable = decodeDrawable(source)
                        if (drawable !is Animatable) src.close()
                        Image(drawable, src)
                    }

                    else -> null
                }
            }.onFailure {
                src.close()
                it.printStackTrace()
            }.getOrNull()
        }

        @JvmStatic
        fun create(bitmap: Bitmap): Image = Image(bitmap.toDrawable(Resources.getSystem()), null)
    }

    interface UniFileSource : AutoCloseable {
        val source: UniFile
    }

    interface ByteBufferSource : AutoCloseable {
        val source: ByteBuffer
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
external fun isGif(fd: Int): Boolean
external fun rewriteGifSource(buffer: ByteBuffer)
external fun mmap(fd: Int): ByteBuffer?
external fun munmap(buffer: ByteBuffer)
