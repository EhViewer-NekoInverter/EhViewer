/*
 * Copyright 2019 Hippo Seven
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
package com.hippo.ehviewer.client

import android.content.Context
import com.hippo.ehviewer.AppConfig
import com.hippo.ehviewer.EhApplication
import com.hippo.ehviewer.EhApplication.Companion.nonCacheOkHttpClient
import com.hippo.ehviewer.R
import com.hippo.ehviewer.Settings
import com.hippo.unifile.UniFile
import com.hippo.unifile.sha1
import com.hippo.yorozuya.FileUtils
import com.hippo.yorozuya.copyToFile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.Request
import okhttp3.coroutines.executeAsync
import okio.BufferedSource
import okio.buffer
import okio.source
import org.json.JSONException
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.nio.charset.StandardCharsets

private typealias TagGroup = Map<String, String>
private typealias TagGroups = Map<String, TagGroup>

object EhTagDatabase {
    private const val NAMESPACE_PREFIX = "n"
    private const val UPDATE_INTERVAL = 3 * 24 * 3600 * 1000
    const val TYPE_EQUAL = 0
    const val TYPE_START = 1
    const val TYPE_CONTAIN = 2
    private lateinit var tagGroups: TagGroups
    private val dir = AppConfig.getFilesDir("tag-translations")
    private val urls = getMetadata(EhApplication.application)
    private val sha1Name = urls?.get(0)!!
    private val sha1Url = urls?.get(1)!!
    private val dataName = urls?.get(2)!!
    private val dataUrl = urls?.get(3)!!
    private val updateLock = Mutex()

    fun isInitialized(): Boolean = this::tagGroups.isInitialized

    private fun JSONObject.toTagGroups(): TagGroups = keys().asSequence().associateWith { getJSONObject(it).toTagGroup() }

    private fun JSONObject.toTagGroup(): TagGroup = keys().asSequence().associateWith { getString(it) }

    private fun updateData(source: BufferedSource) {
        try {
            tagGroups = JSONObject(source.readString(StandardCharsets.UTF_8)).toTagGroups()
        } catch (e: JSONException) {
            e.printStackTrace()
        }
    }

    fun getTranslation(prefix: String? = NAMESPACE_PREFIX, tag: String?): String? = tagGroups[prefix]?.get(tag)?.trim()?.takeIf { it.isNotEmpty() }

    private fun internalSuggestFlow(
        tags: Map<String, String>,
        keyword: String,
        translate: Boolean,
        type: Int,
    ): Flow<Pair<String?, String>> = flow {
        when (type) {
            TYPE_EQUAL -> {
                if (translate) {
                    tags.forEach { (tag, hint) ->
                        if (tag.equalsIgnoreSpace(keyword) || hint.equalsIgnoreSpace(keyword)) {
                            emit(Pair(hint, tag))
                        }
                    }
                } else {
                    tags[keyword]?.let {
                        emit(Pair(null, keyword))
                    }
                }
            }

            TYPE_START -> {
                if (translate) {
                    tags.forEach { (tag, hint) ->
                        if (!tag.equalsIgnoreSpace(keyword) &&
                            !hint.equalsIgnoreSpace(keyword) &&
                            (tag.startsWithIgnoreSpace(keyword) || hint.startsWithIgnoreSpace(keyword))
                        ) {
                            emit(Pair(hint, tag))
                        }
                    }
                } else {
                    tags.keys.forEach { tag ->
                        if (!tag.equalsIgnoreSpace(keyword) && tag.startsWithIgnoreSpace(keyword)) {
                            emit(Pair(null, tag))
                        }
                    }
                }
            }

            TYPE_CONTAIN -> {
                if (translate) {
                    tags.forEach { (tag, hint) ->
                        if (!tag.equalsIgnoreSpace(keyword) &&
                            !hint.equalsIgnoreSpace(keyword) &&
                            !tag.startsWithIgnoreSpace(keyword) &&
                            !hint.startsWithIgnoreSpace(keyword) &&
                            (tag.containsIgnoreSpace(keyword) || hint.containsIgnoreSpace(keyword))
                        ) {
                            emit(Pair(hint, tag))
                        }
                    }
                } else {
                    tags.keys.forEach { tag ->
                        if (!tag.equalsIgnoreSpace(keyword) &&
                            !tag.startsWithIgnoreSpace(keyword) &&
                            tag.containsIgnoreSpace(keyword)
                        ) {
                            emit(Pair(null, tag))
                        }
                    }
                }
            }
        }
    }

    /* Construct a cold flow for tag database suggestions */
    fun suggestFlow(
        keyword: String,
        translate: Boolean,
        type: Int,
    ): Flow<Pair<String?, String>> = flow {
        val keywordPrefix = keyword.substringBefore(':')
        val keywordTag = keyword.drop(keywordPrefix.length + 1)
        val prefix = namespaceToPrefix(keywordPrefix) ?: keywordPrefix
        val tags = tagGroups[prefix.takeIf { keywordTag.isNotEmpty() && it != NAMESPACE_PREFIX }]
        tags?.let {
            internalSuggestFlow(it, keywordTag, translate, type).collect { (hint, tag) ->
                emit(Pair(hint, "$prefix:$tag"))
            }
        } ?: tagGroups.forEach { (prefix, tags) ->
            internalSuggestFlow(tags, keyword, translate, type).collect { (hint, tag) ->
                emit(Pair(hint, if (prefix == NAMESPACE_PREFIX) "$tag:" else "$prefix:$tag"))
            }
        }
    }

    private fun String.removeSpace(): String = replace(" ", "")

    private fun String.containsIgnoreSpace(other: String, ignoreCase: Boolean = true): Boolean = removeSpace().contains(other.removeSpace(), ignoreCase)

    private fun String.equalsIgnoreSpace(other: String, ignoreCase: Boolean = true): Boolean = removeSpace().equals(other.removeSpace(), ignoreCase)

    private fun String.startsWithIgnoreSpace(other: String, ignoreCase: Boolean = true): Boolean = removeSpace().startsWith(other.removeSpace(), ignoreCase)

    private val NAMESPACE_TO_PREFIX = HashMap<String, String>().also {
        it["artist"] = "a"
        it["cosplayer"] = "cos"
        it["character"] = "c"
        it["female"] = "f"
        it["group"] = "g"
        it["language"] = "l"
        it["male"] = "m"
        it["mixed"] = "x"
        it["other"] = "o"
        it["parody"] = "p"
        it["reclass"] = "r"
    }

    @JvmStatic
    fun namespaceToPrefix(namespace: String): String? = NAMESPACE_TO_PREFIX[namespace]

    private fun getMetadata(context: Context): Array<String>? = context.resources.getStringArray(R.array.tag_translation_metadata)
        .takeIf { it.size == 4 }

    fun isTranslatable(context: Context): Boolean = context.resources.getBoolean(R.bool.tag_translatable)

    private fun getFileContent(file: File): String? = runCatching {
        file.source().buffer().use { it.readString(StandardCharsets.UTF_8) }
    }.getOrNull()

    private fun checkData(sha1: String?, data: File): Boolean = sha1 != null && sha1 == UniFile.fromFile(data)?.sha1()

    private suspend fun save(url: String, file: File): Boolean {
        val request: Request = Request.Builder().url(url).build()
        val call = nonCacheOkHttpClient.newCall(request)
        runCatching {
            call.executeAsync().use { response ->
                if (!response.isSuccessful) {
                    return false
                }
                response.body.use {
                    it.copyToFile(file)
                }
                return true
            }
        }.onFailure {
            file.delete()
            it.printStackTrace()
        }
        return false
    }

    suspend fun read() {
        if (urls != null && !isInitialized()) {
            runCatching {
                checkNotNull(dir)
                val sha1File = File(dir, sha1Name)
                val dataFile = File(dir, dataName)

                // Check current sha1 and current data
                val sha1 = getFileContent(sha1File)
                if (!checkData(sha1, dataFile)) {
                    FileUtils.delete(sha1File)
                    FileUtils.delete(dataFile)
                    Settings.putTranslationsLastUpdate(-1)
                }

                // Read current EhTagDatabase
                if (dataFile.exists()) {
                    try {
                        dataFile.source().buffer().use { updateData(it) }
                    } catch (_: IOException) {
                        FileUtils.delete(sha1File)
                        FileUtils.delete(dataFile)
                        Settings.putTranslationsLastUpdate(-1)
                    }
                }
            }.onFailure {
                it.printStackTrace()
            }
            update()
        }
    }

    suspend fun update(force: Boolean = false) {
        val time = System.currentTimeMillis()
        if (urls != null && (force || time - Settings.translationsLastUpdate > UPDATE_INTERVAL)) {
            updateLock.withLock {
                runCatching {
                    checkNotNull(dir)
                    val sha1File = File(dir, sha1Name)
                    val dataFile = File(dir, dataName)

                    // Save new sha1
                    val tempSha1File = File(dir, "$sha1Name.tmp")
                    check(save(sha1Url, tempSha1File))
                    val tempSha1 = getFileContent(tempSha1File)

                    // Check new sha1 and current sha1
                    if (tempSha1 == getFileContent(sha1File)) {
                        // The data is the same
                        FileUtils.delete(tempSha1File)
                        return@runCatching
                    }

                    // Save new data
                    val tempDataFile = File(dir, "$dataName.tmp")
                    check(save(dataUrl, tempDataFile))

                    // Check new sha1 and new data
                    if (!checkData(tempSha1, tempDataFile)) {
                        FileUtils.delete(tempSha1File)
                        FileUtils.delete(tempDataFile)
                        return@runCatching
                    }

                    // Replace current sha1 and current data with new sha1 and new data
                    FileUtils.delete(sha1File)
                    FileUtils.delete(dataFile)
                    tempSha1File.renameTo(sha1File)
                    tempDataFile.renameTo(dataFile)

                    // Read new EhTagDatabase
                    try {
                        dataFile.source().buffer().use { updateData(it) }
                        Settings.putTranslationsLastUpdate(time)
                    } catch (_: IOException) {
                    }
                }.onFailure {
                    it.printStackTrace()
                }
            }
        }
    }
}
