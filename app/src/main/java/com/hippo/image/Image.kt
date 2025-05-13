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

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.drawable.Animatable
import androidx.core.graphics.createBitmap
import coil3.BitmapImage
import coil3.DrawableImage
import coil3.asImage
import coil3.imageLoader
import coil3.request.CachePolicy
import coil3.request.ErrorResult
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.size.Dimension
import coil3.size.Precision
import com.hippo.ehviewer.EhApplication
import com.hippo.ehviewer.jni.isGif
import com.hippo.ehviewer.jni.mmap
import com.hippo.ehviewer.jni.munmap
import com.hippo.ehviewer.jni.nativeTexImage
import com.hippo.ehviewer.jni.rewriteGifSource
import com.hippo.unifile.UniFile
import com.hippo.util.isAtLeastU
import java.nio.ByteBuffer
import coil3.Image as CoilImage

class Image private constructor(
    private val image: CoilImage,
    private val src: ImageSource? = null,
) {
    private var mBitmap: Bitmap? = null
    private var mCanvas: Canvas? = null

    val animated get() = image is DrawableImage && image.drawable is Animatable
    val delay get() = if (animated) 40 else 0
    val isOpaque get() = false
    val width get() = image.width
    val height get() = image.height
    var frameUpdateAllowed = true
    var isRecycled = false
        private set
    var started = false
        private set

    @Synchronized
    fun recycle() {
        if (isRecycled) return
        when (image) {
            is DrawableImage -> {
                (image.drawable as? Animatable)?.stop()
                image.drawable.callback = null
                src?.close()
                mCanvas = null
                mBitmap?.recycle()
                mBitmap = null
            }
            is BitmapImage -> image.bitmap.recycle()
        }
        isRecycled = true
    }

    private fun prepareBitmap() {
        if (mBitmap != null) return
        mBitmap = createBitmap(width, height)
        mCanvas = Canvas(mBitmap!!)
    }

    private fun updateBitmap() {
        if (frameUpdateAllowed) {
            frameUpdateAllowed = false
            prepareBitmap()
            mCanvas!!.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
            image.draw(mCanvas!!)
        }
    }

    fun texImage(init: Boolean, offsetX: Int, offsetY: Int, width: Int, height: Int) {
        val bitmap = if (image is BitmapImage) {
            image.bitmap
        } else {
            updateBitmap()
            mBitmap!!
        }
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
            if (image is DrawableImage) (image.drawable as? Animatable)?.start()
        }
    }

    companion object {
        private val appCtx = EhApplication.application
        private val targetWidth = appCtx.resources.displayMetrics.widthPixels * 2

        private suspend fun decodeCoil(data: Any): CoilImage {
            val req = ImageRequest.Builder(appCtx).apply {
                data(data)
                size(Dimension(targetWidth), Dimension.Undefined)
                precision(Precision.INEXACT)
                allowHardware(false)
                memoryCachePolicy(CachePolicy.DISABLED)
            }.build()
            return when (val result = appCtx.imageLoader.execute(req)) {
                is SuccessResult -> result.image
                is ErrorResult -> throw result.throwable
            }
        }

        suspend fun decode(src: ImageSource): Image? {
            return runCatching {
                val image = when (src) {
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
                        decodeCoil(src.source.uri)
                    }

                    is ByteBufferSource -> {
                        if (!isAtLeastU) {
                            rewriteGifSource(src.source)
                        }
                        decodeCoil(src.source)
                    }
                }
                when (image) {
                    is DrawableImage -> image.drawable.apply {
                        setBounds(0, 0, intrinsicWidth, intrinsicHeight)
                    }
                    is BitmapImage -> src.close()
                }
                Image(image, src)
            }.onFailure {
                src.close()
                it.printStackTrace()
            }.getOrNull()
        }

        @JvmStatic
        fun create(bitmap: Bitmap): Image = Image(bitmap.asImage(), null)
    }
}

sealed interface ImageSource : AutoCloseable

interface UniFileSource : ImageSource {
    val source: UniFile
}

interface ByteBufferSource : ImageSource {
    val source: ByteBuffer
}
