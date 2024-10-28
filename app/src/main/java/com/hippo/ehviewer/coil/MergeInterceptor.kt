/*
 * Copyright 2023 Tarsin Norbin
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

import coil.decode.DataSource
import coil.intercept.Interceptor
import coil.request.ImageResult
import coil.request.SuccessResult
import com.hippo.ehviewer.client.isNormalPreviewKey

object MergeInterceptor : Interceptor {
    private val mutex = NamedMutex<String>()

    override suspend fun intercept(chain: Interceptor.Chain): ImageResult {
        val req = chain.request
        val key = req.memoryCacheKey?.key?.takeIf { it.isNormalPreviewKey }
        return if (key != null) {
            val (result, suspended) = mutex.withLockNeedSuspend(key) { chain.proceed(req) }
            when (result) {
                is SuccessResult if (suspended) -> result.copy(dataSource = DataSource.MEMORY)
                else -> result
            }
        } else {
            chain.proceed(req)
        }
    }
}
