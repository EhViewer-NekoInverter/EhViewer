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
package com.hippo.ehviewer.spider

import com.hippo.ehviewer.client.thumbUrl
import com.hippo.ehviewer.dao.DownloadInfo
import com.hippo.ehviewer.download.DownloadManager

object DownloadInfoMagics {
    private const val DOWNLOAD_INFO_DIRNAME_URL_MAGIC = "$"
    private const val DOWNLOAD_INFO_DIRNAME_URL_SEPARATOR = "|"

    fun encodeMagicRequest(info: DownloadInfo): String {
        val url = info.thumbUrl!!
        val location = DownloadManager.getDownloadDirname(info.gid)
        return if (location.isNullOrBlank()) {
            url
        } else {
            DOWNLOAD_INFO_DIRNAME_URL_MAGIC + url + DOWNLOAD_INFO_DIRNAME_URL_SEPARATOR + location
        }
    }

    fun decodeMagicRequestOrUrl(encoded: String): Pair<String, String?> = if (encoded.startsWith(DOWNLOAD_INFO_DIRNAME_URL_MAGIC)) {
        val (a, b) = encoded.removePrefix(DOWNLOAD_INFO_DIRNAME_URL_MAGIC).split(DOWNLOAD_INFO_DIRNAME_URL_SEPARATOR)
        a to b
    } else {
        encoded to null
    }
}
