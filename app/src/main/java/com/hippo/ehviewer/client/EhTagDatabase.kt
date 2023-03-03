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
import com.hippo.ehviewer.EhApplication.Companion.okHttpClient
import com.hippo.ehviewer.R
import com.hippo.util.ExceptionUtils
import com.hippo.util.HashCodeUtils
import com.hippo.util.launchIO
import com.hippo.yorozuya.FileUtils
import com.hippo.yorozuya.IOUtils
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import kotlinx.coroutines.DelicateCoroutinesApi
import okhttp3.OkHttpClient
import okhttp3.Request
import okio.BufferedSource
import okio.buffer
import okio.source
import org.json.JSONException
import org.json.JSONObject

private typealias TagGroup = Map<String, String>
private typealias TagGroups = Map<String, TagGroup>

object EhTagDatabase {
    private const val NAMESPACE_PREFIX = "n"
    private lateinit var tagGroups: TagGroups
    private lateinit var tagList: TagGroup

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
                    tmpTagList.put(if (prefix == NAMESPACE_PREFIX) "$tag:" else "$prefix:$tag", hint)
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
        try {
            file.source().buffer().use { return it.readString(StandardCharsets.UTF_8) }
        } catch (e: IOException) {
            return null
        }
    }

    private fun getFileSha1(file: File): String? {
        try {
            FileInputStream(file).use { stream ->
                val digest = MessageDigest.getInstance("SHA-1")
                var n: Int
                val buffer = ByteArray(4 * 1024)
                while (stream.read(buffer).also { n = it } != -1) {
                    digest.update(buffer, 0, n)
                }
                return HashCodeUtils.bytesToHexString(digest.digest())
            }
        } catch (e: IOException) {
            return null
        } catch (e: NoSuchAlgorithmException) {
            return null
        }
    }

    private fun checkData(sha1File: File, dataFile: File): Boolean {
        val s1 = getFileContent(sha1File) ?: return false
        val s2 = getFileSha1(dataFile) ?: return false
        return s1 == s2
    }

    private fun save(client: OkHttpClient, url: String, file: File): Boolean {
        val request: Request = Request.Builder().url(url).build()
        val call = client.newCall(request)
        try {
            call.execute().use { response ->
                if (!response.isSuccessful) {
                    return false
                }
                val body = response.body
                body.byteStream()
                    .use { ins -> FileOutputStream(file).use { os -> IOUtils.copy(ins, os) } }
                return true
            }
        } catch (t: Throwable) {
            FileUtils.delete(file)
            t.printStackTrace()
            ExceptionUtils.throwIfFatal(t)
            return false
        }
    }

    @OptIn(DelicateCoroutinesApi::class)
    @JvmStatic
    fun update() {
        launchIO {
            updateInternal()
        }
    }

    @Synchronized
    private fun updateInternal() {
        val urls = getMetadata(EhApplication.application)
        urls?.let {
            val sha1Name = urls[0]
            val sha1Url = urls[1]
            val dataName = urls[2]
            val dataUrl = urls[3]

            try {
                val dir = AppConfig.getFilesDir("tag-translations")
                checkNotNull(dir)

                // Check current sha1 and current data
                val sha1File = File(dir, sha1Name)
                val dataFile = File(dir, dataName)
                if (!checkData(sha1File, dataFile)) {
                    FileUtils.delete(sha1File)
                    FileUtils.delete(dataFile)
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
                val client = okHttpClient

                // Save new sha1
                val tempSha1File = File(dir, "$sha1Name.tmp")
                check(save(client, sha1Url, tempSha1File))

                // Check new sha1 and current data
                if (checkData(tempSha1File, dataFile)) {
                    // The data is the same
                    FileUtils.delete(tempSha1File)
                    return
                }

                // Save new data
                val tempDataFile = File(dir, "$dataName.tmp")
                check(save(client, dataUrl, tempDataFile))

                // Check new sha1 and new data
                if (!checkData(tempSha1File, tempDataFile)) {
                    FileUtils.delete(tempSha1File)
                    FileUtils.delete(tempDataFile)
                    return
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
            } catch (e: Throwable) {
                e.printStackTrace()
            }
        }
    }
}