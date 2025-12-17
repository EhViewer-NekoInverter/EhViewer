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
package com.hippo.yorozuya

import com.hippo.util.isAtLeastN
import java.io.File
import okhttp3.ResponseBody
import okio.buffer
import okio.sink

fun ResponseBody.copyToFile(file: File) {
    file.outputStream().use { os ->
        source().use {
            // Prior to the adoption of OpenJDK, transferFrom will call ByteBuffer.allocate((int) count)
            if (isAtLeastN) {
                os.channel.transferFrom(it, 0, Long.MAX_VALUE)
            } else {
                os.sink().buffer().use { buffer -> buffer.writeAll(source()) }
            }
        }
    }
}
