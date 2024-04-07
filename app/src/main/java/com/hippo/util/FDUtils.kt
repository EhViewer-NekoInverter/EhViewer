/*
 * Copyright 2022 Tarsin Norbin
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
package com.hippo.util

import android.os.ParcelFileDescriptor
import android.system.Int64Ref
import android.system.Os
import android.util.Log
import com.hippo.unifile.UniFile
import java.io.FileDescriptor
import java.io.FileInputStream
import java.io.FileOutputStream

private fun sendFileTotally(from: FileDescriptor, to: FileDescriptor): Long {
    // sendFile may fail on some devices
    try {
        return Os.sendfile(to, from, Int64Ref(0), Long.MAX_VALUE)
    } catch (e: Exception) {
        Log.e("sendFile", "failed", e)
    }
    return FileInputStream(from).use { src ->
        FileOutputStream(to).use { dst ->
            src.channel.transferTo(0, Long.MAX_VALUE, dst.channel)
        }
    }
}

infix fun ParcelFileDescriptor.sendTo(fd: ParcelFileDescriptor) {
    sendFileTotally(fileDescriptor, fd.fileDescriptor)
}

infix fun UniFile.sendTo(file: UniFile) = openFileDescriptor("r").use { src ->
    file.openFileDescriptor("wt").use { dst -> src sendTo dst }
}
