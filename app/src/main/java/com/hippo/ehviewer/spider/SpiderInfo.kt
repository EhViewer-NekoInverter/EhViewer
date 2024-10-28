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
import com.hippo.util.runSuspendCatching
import com.hippo.yorozuya.NumberUtils
import kotlinx.serialization.Serializable
import kotlinx.serialization.cbor.Cbor
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.encodeToByteArray
import okio.buffer
import okio.source
import java.io.File
import java.io.InputStream

@Serializable
class SpiderInfo(
    val gid: Long,
    var token: String? = null,
    val pages: Int,
    val pTokenMap: MutableMap<Int, String> = hashMapOf(),
    var startPage: Int = 0,
    var previewPages: Int = -1,
    var previewPerPage: Int = -1,
)

private val cbor = Cbor {
    ignoreUnknownKeys = true
}

fun SpiderInfo.write(file: UniFile) {
    file.openOutputStream().use {
        it.write(cbor.encodeToByteArray(this))
    }
}

fun SpiderInfo.saveToCache() {
    runSuspendCatching {
        spiderInfoCache.edit(gid.toString()) {
            data.toFile().writeBytes(cbor.encodeToByteArray(this@saveToCache))
        }
    }.onFailure {
        it.printStackTrace()
    }
}

private val spiderInfoCache by lazy {
    DiskCache.Builder()
        .directory(File(EhApplication.application.cacheDir, "spider_info_v2"))
        .maxSizeBytes(20 * 1024 * 1024)
        .build()
}

fun readFromCache(gid: Long): SpiderInfo? {
    val snapshot = spiderInfoCache.openSnapshot(gid.toString()) ?: return null
    return runCatching {
        snapshot.use { snapShot ->
            return cbor.decodeFromByteArray<SpiderInfo>(snapShot.data.toFile().readBytes())
        }
    }.onFailure {
        it.printStackTrace()
    }.getOrNull()
}

fun readCompatFromUniFile(file: UniFile): SpiderInfo? = runCatching {
    file.openInputStream().use {
        cbor.decodeFromByteArray<SpiderInfo>(it.readBytes())
    }
}.getOrNull() ?: runCatching {
    file.openInputStream().use { readLegacySpiderInfo(it) }
}.getOrNull()

private fun readLegacySpiderInfo(inputStream: InputStream): SpiderInfo? {
    val source = inputStream.source().buffer()
    fun read(): String = source.readUtf8LineStrict()
    fun readInt(): Int = read().toInt()
    fun readLong(): Long = read().toLong()
    fun getVersion(str: String): Int = if (str.startsWith(VERSION_STR)) {
        NumberUtils.parseIntSafely(
            str.substring(VERSION_STR.length),
            -1,
        )
    } else {
        1
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
    return SpiderInfo(gid, token, pages, pTokenMap, startPage, previewPages, previewPerPage)
}

const val TOKEN_FAILED = "failed"
private const val VERSION_STR = "VERSION"
private const val VERSION = 2
