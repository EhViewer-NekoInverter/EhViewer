/*
 * Copyright 2016 Hippo Seven
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.hippo.ehviewer.spider

import coil.disk.DiskCache
import com.hippo.ehviewer.EhApplication
import com.hippo.ehviewer.coil.edit
import com.hippo.unifile.UniFile
import com.hippo.unifile.openInputStream
import com.hippo.unifile.openOutputStream
import com.hippo.yorozuya.IOUtils
import com.hippo.yorozuya.NumberUtils
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.io.OutputStreamWriter

class SpiderInfo @JvmOverloads constructor(
    val gid: Long,
    val pages: Int,
    val pTokenMap: MutableMap<Int, String> = hashMapOf(),
    var startPage: Int = 0,
    var token: String? = null,
    var previewPages: Int = -1,
    var previewPerPage: Int = -1,
) {
    private fun write(outputStream: OutputStream) {
        OutputStreamWriter(outputStream).use {
            it.write("$VERSION_STR$VERSION\n")
            it.write("${String.format("%08x", startPage.coerceAtLeast(0))}\n")
            it.write("$gid\n")
            it.write("$token\n")
            it.write("1\n")
            it.write("$previewPages\n")
            it.write("$previewPerPage\n")
            it.write("$pages\n")
            for ((key, value) in pTokenMap) {
                if (TOKEN_FAILED == value || value.isEmpty()) {
                    continue
                }
                it.write("$key $value\n")
            }
            it.flush()
        }
    }

    fun write(file: UniFile) {
        file.openOutputStream().use {
            write(it)
        }
    }

    fun saveToCache() {
        runCatching {
            spiderInfoCache.edit(gid.toString()) {
                data.toFile().outputStream().use {
                    write(it)
                }
            }
        }.onFailure {
            it.printStackTrace()
        }
    }

    companion object {
        const val TOKEN_FAILED = "failed"
        private const val VERSION_STR = "VERSION"
        private const val VERSION = 2

        private val spiderInfoCache by lazy {
            DiskCache.Builder()
                .directory(File(EhApplication.application.cacheDir, "spider_info"))
                .maxSizeBytes(20 * 1024 * 1024)
                .build()
        }

        private fun read(inputStream: InputStream): SpiderInfo? {
            fun read(): String {
                return IOUtils.readAsciiLine(inputStream)
            }
            fun readInt(): Int {
                return read().toInt()
            }
            fun readLong(): Long {
                return read().toLong()
            }
            fun getVersion(str: String): Int {
                return if (str.startsWith(VERSION_STR)) {
                    NumberUtils.parseIntSafely(
                        str.substring(VERSION_STR.length),
                        -1,
                    )
                } else {
                    1
                }
            }
            val version = getVersion(read())
            var startPage = 0
            when (version) {
                VERSION -> {
                    // Read next line
                    startPage = read().toInt(16).coerceAtLeast(0)
                }
                1 -> {
                    // pass
                }
                else -> {
                    // Invalid version
                    return null
                }
            }
            val gid = readLong()
            val token = read()
            read() // Deprecated, mode, skip it
            val previewPages = readInt()
            val previewPerPage = if (version == 1) 0 else readInt()
            val pages = readInt()
            if (gid == -1L || pages <= 0) {
                return null
            }
            val pTokenMap = hashMapOf<Int, String>()
            runCatching {
                while (true) {
                    val line = read()
                    val pos = line.indexOf(" ")
                    if (pos > 0) {
                        val index = line.substring(0, pos).toInt()
                        val pToken = line.substring(pos + 1)
                        if (pToken.isNotEmpty()) {
                            pTokenMap[index] = pToken
                        }
                    }
                }
            }
            return SpiderInfo(gid, pages, pTokenMap, startPage, token, previewPages, previewPerPage)
        }

        @JvmStatic
        fun readCompatFromUniFile(file: UniFile): SpiderInfo? {
            return runCatching {
                file.openInputStream().use {
                    read(it)
                }
            }.getOrNull()
        }

        @JvmStatic
        fun readFromCache(gid: Long): SpiderInfo? {
            val snapshot = spiderInfoCache.openSnapshot(gid.toString()) ?: return null
            return runCatching {
                snapshot.use { snapShot ->
                    return snapShot.data.toFile().inputStream().use { inputStream ->
                        read(inputStream)
                    }
                }
            }.onFailure {
                it.printStackTrace()
            }.getOrNull()
        }
    }
}
