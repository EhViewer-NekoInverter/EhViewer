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

package com.hippo.ehviewer.dao;

import androidx.room.Database;
import androidx.room.RoomDatabase;

@Database(entities = {BookmarkInfo.class, DownloadInfo.class, DownloadLabel.class, DownloadDirname.class, Filter.class, HistoryInfo.class, LocalFavoriteInfo.class, QuickSearch.class}, version = 4, exportSchema = false)
public abstract class EhDatabase extends RoomDatabase {
    public abstract BookmarksDao bookmarksBao();

    public abstract DownloadDirnameDao downloadDirnameDao();

    public abstract DownloadLabelDao downloadLabelDao();

    public abstract DownloadsDao downloadsDao();

    public abstract FilterDao filterDao();

    public abstract HistoryDao historyDao();

    public abstract LocalFavoritesDao localFavoritesDao();

    public abstract QuickSearchDao quickSearchDao();
}
