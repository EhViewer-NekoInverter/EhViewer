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
package com.hippo.unifile

import android.os.ParcelFileDescriptor.AutoCloseInputStream
import android.os.ParcelFileDescriptor.AutoCloseOutputStream
import okio.HashingSink
import okio.blackholeSink
import okio.buffer
import okio.source
import java.io.FileInputStream
import java.io.FileOutputStream

/**
 * Use Native IO/NIO directly if possible, unless you need process file content on JVM!
 */
fun UniFile.openInputStream(): FileInputStream = AutoCloseInputStream(openFileDescriptor("r"))

/**
 * Use Native IO/NIO directly if possible, unless you need process file content on JVM!
 */
fun UniFile.openOutputStream(): FileOutputStream = AutoCloseOutputStream(openFileDescriptor("wt"))

fun UniFile.sha1() = runCatching {
    openInputStream().source().buffer().use { source ->
        HashingSink.sha1(blackholeSink()).use {
            source.readAll(it)
            it.hash.hex()
        }
    }
}.getOrElse {
    it.printStackTrace()
    null
}
