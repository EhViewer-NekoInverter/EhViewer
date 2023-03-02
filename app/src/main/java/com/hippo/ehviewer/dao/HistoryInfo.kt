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

import androidx.room.ColumnInfo
import androidx.room.Entity
import com.hippo.ehviewer.client.data.GalleryInfo
import com.hippo.ehviewer.client.data.BaseGalleryInfo

@Entity(tableName = "HISTORY")
class HistoryInfo() : BaseGalleryInfo() {
    @JvmField
    @ColumnInfo(name = "TIME")
    var time: Long = 0

    // Trick: Use MODE for favoriteSlot
    // Shadow its accessors
    @ColumnInfo(name = "MODE")
    private val mode: Int = 0

    fun getMode(): Int {
        return favoriteSlot + 2
    }

    fun setMode(mode: Int) {
        favoriteSlot = mode - 2
    }
    // Trick end

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
        favoriteSlot = galleryInfo.favoriteSlot
    }
}