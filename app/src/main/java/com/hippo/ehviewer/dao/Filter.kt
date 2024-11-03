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
import androidx.room.PrimaryKey

@Entity(tableName = "FILTER")
data class Filter(
    @ColumnInfo(name = "MODE")
    var mode: Int = 0,

    @ColumnInfo(name = "TEXT")
    var text: String? = null,

    @ColumnInfo(name = "ENABLE")
    var enable: Boolean? = null,

    @PrimaryKey
    @ColumnInfo(name = "_id")
    var id: Long? = null,
)
