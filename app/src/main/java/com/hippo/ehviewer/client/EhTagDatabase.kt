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
import com.hippo.yorozuya.FileUtils
import com.hippo.yorozuya.copyToFile
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.executeAsync
import okio.BufferedSource
import okio.HashingSink.Companion.sha1
import okio.blackholeSink
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
    private lateinit var tagGroups: TagGroups
    private lateinit var tagList: TagGroup
    private val updateLock = Mutex()

    fun isInitialized(): Boolean {
        return this::tagGroups.isInitialized && this::tagList.isInitialized
    }

    private fun JSONObject.toTagGroups(): TagGroups =
        keys().asSequence().associateWith { getJSONObject(it).toTagGroup() }

    private fun JSONObject.toTagGroup(): TagGroup =
        keys().asSequence().associateWith { getString(it) }

    private fun updateData(source: BufferedSource) {
        try {
            tagGroups = JSONObject(source.readString(StandardCharsets.UTF_8)).toTagGroups()
            val tmpTagList = mutableMapOf<String, String>()
            tagGroups.forEach { (prefix, tags) ->
                tags.forEach { (tag, hint) ->
                    tmpTagList[if (prefix == NAMESPACE_PREFIX) "$tag:" else "$prefix:$tag"] = hint
                }
            }
            tagList = tmpTagList
        } catch (e: JSONException) {
            e.printStackTrace()
        }
    }

    fun getTranslation(prefix: String? = NAMESPACE_PREFIX, tag: String?): String? {
        return tagGroups[prefix]?.get(tag)?.trim()?.takeIf { it.isNotEmpty() }
    }

    private fun internalSuggest(
        tags: Map<String, String>, prefix: String?,
        keyword: String, translate: Boolean
    ): ArrayList<Pair<String?, String>> {
        val equalsTags = ArrayList<Pair<String?, String>>()
        val startsTags = ArrayList<Pair<String?, String>>()
        val containsTags = ArrayList<Pair<String?, String>>()
        tags.forEach { (tag, hint) ->
            val pair = Pair(if (translate) hint else null, if (prefix == null) tag else "$prefix:$tag")
            val tagStr = if (prefix == null && (keyword.endsWith(':') || !tag.endsWith(':')))
                tag.substring(tag.indexOf(':') + 1) else tag
            if (tagStr.equalsIgnoreSpace(keyword) || hint.equalsIgnoreSpace(keyword)) {
                equalsTags.add(pair)
            } else if (tagStr.startsWithIgnoreSpace(keyword) || hint.startsWithIgnoreSpace(keyword)) {
                startsTags.add(pair)
            } else if (tagStr.containsIgnoreSpace(keyword) || hint.containsIgnoreSpace(keyword)) {
                containsTags.add(pair)
            }
        }
        equalsTags.addAll(startsTags)
        equalsTags.addAll(containsTags)
        return if (equalsTags.size > 100) ArrayList(equalsTags.subList(0, 100)) else equalsTags
    }

    fun suggest(keyword: String, translate: Boolean): ArrayList<Pair<String?, String>> {
        val keywordPrefix = keyword.substringBefore(':')
        val keywordTag = keyword.drop(keywordPrefix.length + 1)
        val prefix = namespaceToPrefix(keywordPrefix) ?: keywordPrefix
        val tags = tagGroups[prefix.takeIf { keywordTag.isNotEmpty() && it != NAMESPACE_PREFIX }]
        tags?.let {
            return internalSuggest(it, prefix, keywordTag, translate)
        } ?:
            return internalSuggest(tagList, null, keyword, translate)
    }

    private fun String.removeSpace(): String = replace(" ", "")

    private fun String.containsIgnoreSpace(other: String, ignoreCase: Boolean = true): Boolean =
        removeSpace().contains(other.removeSpace(), ignoreCase)

    private fun String.equalsIgnoreSpace(other: String, ignoreCase: Boolean = true): Boolean =
        removeSpace().equals(other.removeSpace(), ignoreCase)

    private fun String.startsWithIgnoreSpace(other: String, ignoreCase: Boolean = true): Boolean =
        removeSpace().startsWith(other.removeSpace(), ignoreCase)

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
    fun namespaceToPrefix(namespace: String): String? {
        return NAMESPACE_TO_PREFIX[namespace]
    }

    private fun getMetadata(context: Context): Array<String>? {
        return context.resources.getStringArray(R.array.tag_translation_metadata)
            .takeIf { it.size == 4 }
    }

    fun isTranslatable(context: Context): Boolean {
        return context.resources.getBoolean(R.bool.tag_translatable)
    }

    private fun getFileContent(file: File): String? {
        return runCatching {
            file.source().buffer().use { it.readString(StandardCharsets.UTF_8) }
        }.getOrNull()
    }

    private fun getFileSha1(file: File): String? {
        return runCatching {
            file.source().buffer().use { source ->
                sha1(blackholeSink()).use {
                    source.readAll(it)
                    it.hash.hex()
                }
            }
        }.getOrNull()
    }

    private fun checkData(sha1: String?, data: File): Boolean {
        return sha1 != null && sha1 == getFileSha1(data)
    }

    private suspend fun save(client: OkHttpClient, url: String, file: File): Boolean {
        val request: Request = Request.Builder().url(url).build()
        val call = client.newCall(request)
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

    suspend fun update() {
        updateLock.withLock {
            updateInternal()
        }
    }

    private suspend fun updateInternal() {
        val urls = getMetadata(EhApplication.application)
        urls?.let {
            val sha1Name = urls[0]
            val sha1Url = urls[1]
            val dataName = urls[2]
            val dataUrl = urls[3]

            val dir = AppConfig.getFilesDir("tag-translations")
            checkNotNull(dir)
            val sha1File = File(dir, sha1Name)
            val dataFile = File(dir, dataName)

            runCatching {
                // Check current sha1 and current data
                val sha1 = getFileContent(sha1File)
                if (!checkData(sha1, dataFile)) {
                    FileUtils.delete(sha1File)
                    FileUtils.delete(dataFile)
                }

                val client = nonCacheOkHttpClient

                // Save new sha1
                val tempSha1File = File(dir, "$sha1Name.tmp")
                check(save(client, sha1Url, tempSha1File))
                val tempSha1 = getFileContent(tempSha1File)

                // Check new sha1 and current sha1
                if (tempSha1 == sha1) {
                    // The data is the same
                    FileUtils.delete(tempSha1File)
                    return@runCatching
                }

                // Save new data
                val tempDataFile = File(dir, "$dataName.tmp")
                check(save(client, dataUrl, tempDataFile))

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
                } catch (_: IOException) {
                }
            }.onFailure {
                it.printStackTrace()
            }

            // Read current EhTagDatabase
            if (!isInitialized() && dataFile.exists()) {
                try {
                    dataFile.source().buffer().use { updateData(it) }
                } catch (e: IOException) {
                    FileUtils.delete(sha1File)
                    FileUtils.delete(dataFile)
                }
            }
        }
    }
}