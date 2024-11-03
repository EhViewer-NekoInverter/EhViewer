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
import com.hippo.ehviewer.client.data.BaseGalleryInfo
import com.hippo.ehviewer.client.data.GalleryInfo

@SuppressLint("ParcelCreator")
@Entity(tableName = "HISTORY")
class HistoryInfo() : BaseGalleryInfo() {
    @ColumnInfo(name = "TIME")
    var time: Long = 0

    // Trick: Use MODE for favoriteSlot
    @ColumnInfo(name = "MODE")
    var favoriteSlotBackingField: Int = 0

    override var favoriteSlot: Int
        get() = favoriteSlotBackingField - 2
        set(value) {
            favoriteSlotBackingField = value + 2
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
