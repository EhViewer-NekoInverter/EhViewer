/*
 * Copyright 2024 Moedog
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
@file:Suppress("unused")

package com.hippo.ehviewer.jni

import java.nio.ByteBuffer

external fun releaseByteBuffer(buffer: ByteBuffer)
external fun openArchive(fd: Int, size: Long, sortEntries: Boolean): Int
external fun extractToByteBuffer(index: Int): ByteBuffer?
external fun extractToFd(index: Int, fd: Int): Boolean
external fun getFilename(index: Int): String
external fun needPassword(): Boolean
external fun providePassword(str: String): Boolean
external fun closeArchive()
