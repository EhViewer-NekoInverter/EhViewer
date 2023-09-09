/*
 * Copyright 2023 Tarsin Norbin
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

import coil.disk.DiskCache

inline fun DiskCache.edit(key: String, block: DiskCache.Editor.() -> Unit): Boolean {
    val editor = openEditor(key) ?: return false
    editor.runCatching {
        block(this)
    }.onFailure {
        editor.abort()
        throw it
    }.onSuccess {
        editor.commit()
    }
    return true
}

inline fun DiskCache.read(key: String, block: DiskCache.Snapshot.() -> Unit): Boolean {
    (openSnapshot(key) ?: return false).use { block(it) }
    return true
}
