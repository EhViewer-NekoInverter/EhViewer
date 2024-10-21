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
import com.hippo.ehviewer.client.EhUrl.URL_PREFIX_THUMB_EX
import com.hippo.ehviewer.client.EhUrl.URL_SIGNATURE_THUMB_NORMAL
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

object LimitConcurrencyInterceptor : Interceptor {
    val semaphores = NamedSemaphore<String>(permits = 16)
    override suspend fun intercept(chain: Interceptor.Chain): ImageResult {
        val url = chain.request.data as? String
        return if (url != null) {
            when {
                // Ex thumb server may not have h2 multiplexing support
                URL_PREFIX_THUMB_EX in url -> semaphores.withPermit(URL_PREFIX_THUMB_EX) {
                    withContext(NonCancellable) { chain.proceed(chain.request) }
                }
                // H@H server may not have h2 multiplexing support
                URL_SIGNATURE_THUMB_NORMAL in url -> semaphores.withPermit(url.substringBefore(URL_SIGNATURE_THUMB_NORMAL)) {
                    withContext(NonCancellable) { chain.proceed(chain.request) }
                }
                // H2 multiplexing enabled
                else -> chain.proceed(chain.request)
            }
        } else {
            chain.proceed(chain.request)
        }
    }
}
