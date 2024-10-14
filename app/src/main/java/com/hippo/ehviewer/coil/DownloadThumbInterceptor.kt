/*
 * Copyright 2024 Moedog
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
package com.hippo.ehviewer.coil

import coil.intercept.Interceptor
import coil.request.ImageResult
import coil.request.SuccessResult
import com.hippo.ehviewer.EhApplication.Companion.thumbCache
import com.hippo.ehviewer.Settings.downloadLocation
import com.hippo.ehviewer.spider.DownloadInfoMagics.decodeMagicRequestOrUrl
import com.hippo.unifile.UniFile
import com.hippo.util.sendTo

object DownloadThumbInterceptor : Interceptor {
    private const val THUMB_FILE = ".thumb"
    override suspend fun intercept(chain: Interceptor.Chain): ImageResult {
        val magicOrUrl = chain.request.data as? String
        if (magicOrUrl != null) {
            val (url, location) = decodeMagicRequestOrUrl(magicOrUrl)
            if (location != null) {
                val thumb = downloadLocation?.subFile(location)?.subFile(THUMB_FILE)
                if (thumb?.isFile == true) {
                    val new = chain.request.newBuilder().data(thumb.uri).build()
                    val result = chain.withRequest(new).proceed(new)
                    if (result is SuccessResult) return result
                }
                val new = chain.request.newBuilder().data(url).build()
                val result = chain.withRequest(new).proceed(new)
                if (result is SuccessResult && thumb?.parentFile?.isDirectory == true) {
                    // Accessing the recreated file immediately after deleting it throws
                    // FileNotFoundException, so we just overwrite the existing file.
                    chain.request.memoryCacheKey?.let {
                        if (!thumb.exists()) thumb.ensureFile()
                        thumbCache.read(it.key) {
                            UniFile.fromFile(data.toFile())!! sendTo thumb
                        }
                    }
                    return result
                }
            }
        }
        return chain.proceed(chain.request)
    }
}
