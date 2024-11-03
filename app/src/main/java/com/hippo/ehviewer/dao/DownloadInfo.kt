/*
 * Copyright 2022 Moedog
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
package com.hippo.ehviewer.dao

import android.annotation.SuppressLint
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Ignore
import com.hippo.ehviewer.client.data.BaseGalleryInfo
import com.hippo.ehviewer.client.data.GalleryInfo

@SuppressLint("ParcelCreator")
@Entity(tableName = "DOWNLOADS")
class DownloadInfo() : BaseGalleryInfo() {
    @ColumnInfo(name = "STATE")
    var state = 0

    @ColumnInfo(name = "LEGACY")
    var legacy = 0

    @ColumnInfo(name = "TIME")
    var time: Long = 0

    @ColumnInfo(name = "LABEL")
    var label: String? = null

    @Ignore
    var speed: Long = 0

    @Ignore
    var remaining: Long = 0

    @Ignore
    var finished = 0

    @Ignore
    var downloaded = 0

    @Ignore
    var total = 0

    constructor(galleryInfo: GalleryInfo) : this() {
        gid = galleryInfo.gid
        token = galleryInfo.token
        title = galleryInfo.title
        titleJpn = galleryInfo.titleJpn
        thumb = galleryInfo.thumb
        this.category = galleryInfo.category
        posted = galleryInfo.posted
        uploader = galleryInfo.uploader
        rating = galleryInfo.rating
        simpleTags = galleryInfo.simpleTags
        simpleLanguage = galleryInfo.simpleLanguage
    }

    companion object {
        const val STATE_INVALID = -1
        const val STATE_NONE = 0
        const val STATE_WAIT = 1
        const val STATE_DOWNLOAD = 2
        const val STATE_FINISH = 3
        const val STATE_FAILED = 4
    }
}
