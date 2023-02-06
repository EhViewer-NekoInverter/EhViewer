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

import android.content.Context
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ImageDecoder
import android.graphics.ImageDecoder.ALLOCATOR_DEFAULT
import android.graphics.ImageDecoder.ALLOCATOR_SOFTWARE
import android.graphics.ImageDecoder.DecodeException
import android.graphics.ImageDecoder.ImageInfo
import android.graphics.ImageDecoder.Source
import android.graphics.PixelFormat
import android.graphics.drawable.AnimatedImageDrawable
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.core.graphics.drawable.toDrawable
import coil.decode.FrameDelayRewritingSource
import com.hippo.Native
import okio.Buffer
import okio.BufferedSource
import okio.buffer
import okio.source
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import kotlin.math.min

class Image private constructor(
    source: Source?, drawable: Drawable? = null,
    val hardware: Boolean = true,
    val release: () -> Unit? = {}
) {
    internal var mObtainedDrawable: Drawable?
    private var mBitmap: Bitmap? = null
    private var mCanvas: Canvas? = null

    init {
        mObtainedDrawable = null
        source?.let {
            mObtainedDrawable =
                ImageDecoder.decodeDrawable(source) { decoder: ImageDecoder, info: ImageInfo, _: Source ->
                    decoder.allocator = if (hardware) ALLOCATOR_DEFAULT else ALLOCATOR_SOFTWARE
                    // Sadly we must use software memory since we need copy it to tile buffer, fuck glgallery
                    // Idk it will cause how much performance regression

                    decoder.setTargetSampleSize(
                        min(
                            info.size.width / (2 * screenWidth),
                            info.size.height / (2 * screenHeight)
                        ).coerceAtLeast(1)
                    )
                }
        }
        if (mObtainedDrawable == null) {
            mObtainedDrawable = drawable!!
        }
    }

    val animated = mObtainedDrawable is AnimatedImageDrawable
    val width = mObtainedDrawable!!.intrinsicWidth
    val height = mObtainedDrawable!!.intrinsicHeight
    val isRecycled = mObtainedDrawable == null

    var started = false

    @Synchronized
    fun recycle() {
        mObtainedDrawable ?: return
        (mObtainedDrawable as? AnimatedImageDrawable)?.stop()
        (mObtainedDrawable as? BitmapDrawable)?.bitmap?.recycle()
        mObtainedDrawable?.callback = null
        mObtainedDrawable = null
        mCanvas = null
        mBitmap?.recycle()
        mBitmap = null
        release()
    }

    private fun prepareBitmap() {
        if (mBitmap != null) return
        mBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        mCanvas = Canvas(mBitmap!!)
    }

    private fun updateBitmap() {
        prepareBitmap()
        mObtainedDrawable!!.draw(mCanvas!!)
    }

    fun render(
        srcX: Int, srcY: Int, dst: Bitmap, dstX: Int, dstY: Int,
        width: Int, height: Int
    ) {
        check(!hardware) { "Hardware buffer cannot be used in glgallery" }
        val bitmap: Bitmap = if (animated) {
            updateBitmap()
            mBitmap!!
        } else {
            (mObtainedDrawable as BitmapDrawable).bitmap
        }
        nativeRender(
            bitmap,
            srcX,
            srcY,
            dst,
            dstX,
            dstY,
            width,
            height
        )
    }

    fun texImage(init: Boolean, offsetX: Int, offsetY: Int, width: Int, height: Int) {
        check(!hardware) { "Hardware buffer cannot be used in glgallery" }
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
            height
        )
    }

    fun start() {
        if (!started) {
            (mObtainedDrawable as AnimatedImageDrawable?)?.start()
        }
    }

    val delay: Int
        get() {
            if (animated)
                return 10
            return 0
        }

    val isOpaque: Boolean
        get() {
            return mObtainedDrawable?.opacity == PixelFormat.OPAQUE
        }

    companion object {
        var screenWidth: Int = 0
        var screenHeight: Int = 0

        @JvmStatic
        fun initialize(context: Context) {
            screenWidth = context.resources.displayMetrics.widthPixels
            screenHeight = context.resources.displayMetrics.heightPixels
        }

        @Throws(DecodeException::class)
        @JvmStatic
        fun decode(stream: FileInputStream, hardware: Boolean): Image {
            val buffer = stream.channel.map(
                FileChannel.MapMode.READ_ONLY, 0,
                stream.available().toLong()
            )
            val source = if (checkIsGif(buffer)) {
                rewriteSource(stream.source().buffer())
            } else {
                buffer
            }
            return Image(ImageDecoder.createSource(source), hardware = hardware)
        }

        @Throws(DecodeException::class)
        @JvmStatic
        fun decode(buffer: ByteBuffer, hardware: Boolean, release: () -> Unit? = {}): Image {
            return if (checkIsGif(buffer)) {
                val rewritten = rewriteSource(Buffer().apply {
                    write(buffer)
                    release()
                })
                Image(ImageDecoder.createSource(rewritten), hardware = hardware)
            } else {
                Image(ImageDecoder.createSource(buffer), hardware = hardware) {
                    release()
                }
            }
        }

        @JvmStatic
        fun create(bitmap: Bitmap): Image {
            return Image(null, bitmap.toDrawable(Resources.getSystem()), false)
        }

        private fun rewriteSource(source: BufferedSource): ByteBuffer {
            val bufferedSource = FrameDelayRewritingSource(source).buffer()
            return ByteBuffer.wrap(bufferedSource.use { it.readByteArray() })
        }

        private fun checkIsGif(buffer: ByteBuffer): Boolean {
            check(buffer.isDirect)
            return Native.isGif(buffer)
        }

        @JvmStatic
        private external fun nativeRender(
            bitmap: Bitmap,
            srcX: Int, srcY: Int, dst: Bitmap, dstX: Int, dstY: Int,
            width: Int, height: Int
        )

        @JvmStatic
        private external fun nativeTexImage(
            bitmap: Bitmap,
            init: Boolean,
            offsetX: Int,
            offsetY: Int,
            width: Int,
            height: Int
        )
    }
}