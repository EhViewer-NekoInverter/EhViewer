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
package com.hippo.util

import android.os.Build
import java.net.URLEncoder
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets

fun encode(s: String, charset: Charset): String = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
    URLEncoder.encode(s, charset)
} else {
    URLEncoder.encode(s, charset.name())
}

fun encodeUTF8(s: String): String = encode(s, StandardCharsets.UTF_8)
