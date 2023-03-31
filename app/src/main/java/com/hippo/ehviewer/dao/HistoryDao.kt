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

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update

@Dao
interface HistoryDao : BasicDao<HistoryInfo> {
    @Query("SELECT * FROM HISTORY WHERE GID = :gid")
    fun load(gid: Long): HistoryInfo?

    @Query("SELECT * FROM HISTORY ORDER BY TIME DESC")
    override fun list(): List<HistoryInfo>

    @Query("SELECT * FROM HISTORY ORDER BY TIME DESC LIMIT :limit OFFSET :offset")
    fun list(offset: Int, limit: Int): List<HistoryInfo>

    @Query("SELECT * FROM HISTORY ORDER BY TIME DESC")
    fun listLazy(): PagingSource<Int, HistoryInfo>

    @Update
    fun update(historyInfo: HistoryInfo)

    @Insert
    override fun insert(t: HistoryInfo): Long

    @Delete
    fun delete(historyInfo: HistoryInfo)

    @Delete
    fun delete(historyInfo: List<HistoryInfo>)

    @Query("DELETE FROM HISTORY")
    fun deleteAll()
}
