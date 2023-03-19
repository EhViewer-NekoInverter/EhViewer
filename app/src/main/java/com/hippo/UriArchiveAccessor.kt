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
package com.hippo

import android.content.Context
import android.graphics.ImageDecoder
import android.net.Uri
import com.hippo.image.Image
import java.nio.ByteBuffer

class UriArchiveAccessor(ctx: Context, uri: Uri) {
    val pfd by lazy { ctx.contentResolver.openFileDescriptor(uri, "r")!! }
    fun open(): Int {
        return openArchive(pfd.fd, pfd.statSize)
    }

    private external fun openArchive(fd: Int, size: Long): Int
    private external fun extractToByteBuffer(index: Int): ByteBuffer?
    external fun extractToFd(index: Int, fd: Int)
    external fun getFilename(index: Int): String
    external fun needPassword(): Boolean
    external fun providePassword(str: String): Boolean
    private external fun closeArchive()
    fun close() {
        closeArchive()
        pfd.close()
    }

    fun getImageSource(index: Int): Image.CloseableSource? {
        val buffer = extractToByteBuffer(index)
        buffer ?: return null
        check(buffer.isDirect)
        Image.rewriteGifSource(buffer)
        val source = ImageDecoder.createSource(buffer)
        return object : Image.CloseableSource {
            override val source: ImageDecoder.Source
                get() = source

            override fun close() {
                releaseByteBuffer(buffer)
            }
        }
    }

    companion object {
        @JvmStatic
        private external fun releaseByteBuffer(buffer: ByteBuffer)
    }
}
