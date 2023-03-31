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

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query

@Dao
interface LocalFavoritesDao : BasicDao<LocalFavoriteInfo> {
    @Query("SELECT * FROM LOCAL_FAVORITES ORDER BY TIME DESC")
    override fun list(): List<LocalFavoriteInfo>

    @Query("SELECT * FROM LOCAL_FAVORITES WHERE TITLE LIKE :title ORDER BY TIME DESC")
    fun list(title: String): List<LocalFavoriteInfo>

    @Query("SELECT * FROM LOCAL_FAVORITES WHERE GID = :gid")
    fun load(gid: Long): LocalFavoriteInfo?

    @Query("SELECT EXISTS(SELECT * FROM LOCAL_FAVORITES WHERE GID = :gid)")
    fun contains(gid: Long): Boolean

    @Insert
    override fun insert(t: LocalFavoriteInfo): Long

    @Delete
    fun delete(localFavoriteInfo: LocalFavoriteInfo)

    @Query("DELETE FROM LOCAL_FAVORITES WHERE GID = :gid")
    fun deleteByKey(gid: Long)
}
