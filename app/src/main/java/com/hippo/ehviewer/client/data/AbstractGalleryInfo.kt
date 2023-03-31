/*
 * Copyright 2023 Moedog
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
package com.hippo.ehviewer.client.data

interface AbstractGalleryInfo {
    var gid: Long
    var token: String?
    var title: String?
    var titleJpn: String?
    var thumb: String?
    var category: Int
    var posted: String?
    var uploader: String?
    var disowned: Boolean
    var rating: Float
    var rated: Boolean
    var simpleTags: Array<String>?
    var pages: Int
    var thumbWidth: Int
    var thumbHeight: Int
    var spanSize: Int
    var spanIndex: Int
    var spanGroupIndex: Int
    var simpleLanguage: String?
    var favoriteSlot: Int
    var favoriteName: String?
    var favoriteNote: String?
}
