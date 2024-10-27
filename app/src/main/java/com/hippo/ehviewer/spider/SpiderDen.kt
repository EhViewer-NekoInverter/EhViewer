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

import android.graphics.ImageDecoder
import com.hippo.ehviewer.EhApplication.Companion.nonCacheOkHttpClient
import com.hippo.ehviewer.Settings
import com.hippo.ehviewer.client.EhCacheKeyFactory
import com.hippo.ehviewer.client.EhRequestBuilder
import com.hippo.ehviewer.client.EhUtils.getSuitableTitle
import com.hippo.ehviewer.client.data.GalleryInfo
import com.hippo.ehviewer.coil.edit
import com.hippo.ehviewer.coil.read
import com.hippo.ehviewer.download.DownloadManager
import com.hippo.ehviewer.gallery.GalleryProvider2.Companion.SUPPORT_IMAGE_EXTENSIONS
import com.hippo.image.Image.CloseableSource
import com.hippo.image.rewriteGifSource2
import com.hippo.unifile.UniFile
import com.hippo.unifile.openOutputStream
import com.hippo.unifile.sha1
import com.hippo.util.runInterruptibleOkio
import com.hippo.util.runSuspendCatching
import com.hippo.util.sendTo
import com.hippo.yorozuya.FileUtils
import okhttp3.Response
import okhttp3.coroutines.executeAsync
import okio.buffer
import okio.sink
import java.io.IOException
import java.util.Locale
import kotlin.io.path.readText
import com.hippo.ehviewer.EhApplication.Companion.imageCache as sCache

class SpiderDen(private val mGalleryInfo: GalleryInfo) {
    private val fileHashRegex = Regex("/h/([0-9a-f]{40})")
    private val safeDirnameRegex = Regex("[^\\p{L}\\p{M}\\p{N}\\p{P}\\p{Z}\\p{Cf}\\p{Cs}\\s]")
    private val mGid = mGalleryInfo.gid
    var downloadDir: UniFile? = null

    @Volatile
    @SpiderQueen.Mode
    var mode = SpiderQueen.MODE_READ
        set(value) {
            field = value
            if (field == SpiderQueen.MODE_DOWNLOAD && downloadDir == null) {
                val title = getSuitableTitle(mGalleryInfo)
                val dirname = FileUtils.sanitizeFilename("$mGid-$title")
                val safeDirname = dirname.replace(safeDirnameRegex, "")
                downloadDir = perDownloadDir(dirname) ?: perDownloadDir(safeDirname)
            }
        }

    private fun perDownloadDir(dirname: String): UniFile? {
        DownloadManager.putDownloadDirname(mGid, dirname)
        return getGalleryDownloadDir(mGid)?.takeIf { it.ensureDir() }
    }

    private fun containInCache(index: Int): Boolean {
        val key = EhCacheKeyFactory.getImageKey(mGid, index)
        return sCache.openSnapshot(key)?.use { true } == true
    }

    private fun containInDownloadDir(index: Int): Boolean {
        val dir = downloadDir ?: return false
        return findImageFile(dir, index).first != null
    }

    private fun copyFromCacheToDownloadDir(index: Int, skip: Boolean): Boolean {
        val dir = downloadDir ?: return false
        // Find image file in cache
        val key = EhCacheKeyFactory.getImageKey(mGid, index)
        return runCatching {
            sCache.read(key) {
                // Get extension
                val extension = fixExtension("." + metadata.toFile().readText())
                // Don't copy from cache if `download original image` enabled, ignore gif
                if (skip && extension != GIF_IMAGE_EXTENSION) {
                    return false
                }
                // Copy from cache to download dir
                val file = dir.createFile(perFilename(index, extension)) ?: return false
                UniFile.fromFile(data.toFile())!! sendTo file
            }
        }.getOrElse {
            it.printStackTrace()
            false
        }
    }

    operator fun contains(index: Int): Boolean {
        return when (mode) {
            SpiderQueen.MODE_READ -> {
                containInCache(index) || containInDownloadDir(index)
            }

            SpiderQueen.MODE_DOWNLOAD -> {
                containInDownloadDir(index) || copyFromCacheToDownloadDir(index, Settings.skipCopyImage)
            }

            else -> {
                false
            }
        }
    }

    private fun removeFromCache(index: Int): Boolean {
        val key = EhCacheKeyFactory.getImageKey(mGid, index)
        return sCache.remove(key)
    }

    private fun removeFromDownloadDir(index: Int): Boolean {
        return downloadDir?.let { findImageFile(it, index).first?.delete() } == true
    }

    fun remove(index: Int): Boolean {
        return removeFromCache(index) or removeFromDownloadDir(index)
    }

    private fun findDownloadFileForIndex(index: Int, extension: String): UniFile? {
        val dir = downloadDir ?: return null
        val ext = fixExtension(".$extension")
        return dir.createFile(perFilename(index, ext))
    }

    @Throws(IOException::class)
    suspend fun saveImageFromUrl(
        url: String,
        referer: String?,
        dst: UniFile,
    ): Boolean {
        nonCacheOkHttpClient.newCall(EhRequestBuilder(url, referer).build()).executeAsync().use {
            if (it.code >= 400) return false
            return runSuspendCatching {
                var ret = 0L
                runInterruptibleOkio {
                    dst.openOutputStream().sink().buffer().use { sink ->
                        it.body.source().use { source ->
                            while (true) {
                                val bytesRead = source.read(sink.buffer, 8192)
                                if (bytesRead == -1L) break
                                ret += bytesRead
                                sink.emitCompleteSegments()
                            }
                        }
                    }
                }
                ret == it.body.contentLength()
            }.onFailure { e ->
                e.printStackTrace()
            }.getOrElse {
                false
            }
        }
    }

    @Throws(IOException::class)
    suspend fun makeHttpCallAndSaveImage(
        index: Int,
        url: String,
        referer: String?,
        notifyProgress: (Long, Long, Int) -> Unit,
    ): Boolean {
        // TODO: Use HttpEngine[https://developer.android.com/reference/android/net/http/HttpEngine] directly here if available
        // Since we don't want unnecessary copy between jvm heap & native heap
        nonCacheOkHttpClient.newCall(EhRequestBuilder(url, referer).build()).executeAsync().use {
            if (it.code >= 400) return false
            return saveFromHttpResponse(index, it, notifyProgress)
        }
    }

    private suspend fun saveFromHttpResponse(index: Int, response: Response, notifyProgress: (Long, Long, Int) -> Unit): Boolean {
        val url = response.request.url.toString()
        val extension = response.body.contentType()?.subtype ?: "jpg"
        val length = response.body.contentLength()

        suspend fun doSave(outFile: UniFile): Long {
            var ret = 0L
            runInterruptibleOkio {
                outFile.openOutputStream().sink().buffer().use { sink ->
                    response.body.source().use { source ->
                        while (true) {
                            val bytesRead = source.read(sink.buffer, 8192)
                            if (bytesRead == -1L) break
                            ret += bytesRead
                            sink.emitCompleteSegments()
                            notifyProgress(length, ret, bytesRead.toInt())
                        }
                    }
                }
                fileHashRegex.find(url)?.let {
                    val expected = it.groupValues[1]
                    val actual = outFile.sha1()
                    check(expected == actual) { "File hash mismatch: expected $expected, but got $actual\nURL: $url" }
                }
                if (extension.lowercase() == "gif") {
                    outFile.openFileDescriptor("rw").use {
                        rewriteGifSource2(it.fd)
                    }
                }
            }
            return ret
        }

        findDownloadFileForIndex(index, extension)?.runSuspendCatching {
            return doSave(this) == length
        }?.onFailure {
            it.printStackTrace()
            return false
        }

        // Read Mode, allow save to cache
        if (mode == SpiderQueen.MODE_READ) {
            val key = EhCacheKeyFactory.getImageKey(mGid, index)
            var received: Long = 0
            runSuspendCatching {
                sCache.edit(key) {
                    metadata.toFile().writeText(extension)
                    received = doSave(UniFile.fromFile(data.toFile())!!)
                }
            }.onFailure {
                it.printStackTrace()
            }.onSuccess {
                return received == length
            }
        }

        return false
    }

    fun saveToUniFile(index: Int, file: UniFile): Boolean {
        val key = EhCacheKeyFactory.getImageKey(mGid, index)

        // Read from diskCache first
        sCache.read(key) {
            runCatching {
                UniFile.fromFile(data.toFile())!! sendTo file
                return true
            }.onFailure {
                it.printStackTrace()
                return false
            }
        }

        // Read from download dir
        runCatching {
            findImageFile(downloadDir!!, index).first!! sendTo file
        }.onFailure {
            it.printStackTrace()
            return false
        }.onSuccess {
            return true
        }
        return false
    }

    fun getExtension(index: Int): String? {
        val key = EhCacheKeyFactory.getImageKey(mGid, index)
        return sCache.openSnapshot(key)?.use { it.metadata.toNioPath().readText() }
            ?: downloadDir?.let { findImageFile(it, index).first }
                ?.name.let { FileUtils.getExtensionFromFilename(it) }
    }

    fun getImageSource(index: Int): CloseableSource? {
        if (mode == SpiderQueen.MODE_READ) {
            val key = EhCacheKeyFactory.getImageKey(mGid, index)
            sCache.openSnapshot(key)?.let {
                val source = ImageDecoder.createSource(it.data.toFile())
                return object : CloseableSource, AutoCloseable by it {
                    override val source = source
                }
            }
        }
        val dir = downloadDir ?: return null
        val (file, isGif) = findImageFile(dir, index)
        file?.run {
            if (isGif) {
                openFileDescriptor("rw").use {
                    rewriteGifSource2(it.fd)
                }
            }
            return object : CloseableSource {
                override val source = imageSource

                override fun close() {}
            }
        } ?: return null
    }

    companion object {
        private val COMPAT_IMAGE_EXTENSIONS = SUPPORT_IMAGE_EXTENSIONS + ".jpeg"
        private val GIF_IMAGE_EXTENSION = SUPPORT_IMAGE_EXTENSIONS[2]

        /**
         * @param extension with dot
         */
        private fun fixExtension(extension: String): String {
            return extension.takeIf { SUPPORT_IMAGE_EXTENSIONS.contains(it) }
                ?: SUPPORT_IMAGE_EXTENSIONS[0]
        }

        fun findImageFile(dir: UniFile, index: Int): Pair<UniFile?, Boolean> {
            for (extension in COMPAT_IMAGE_EXTENSIONS) {
                val filename = perFilename(index, extension)
                val file = dir.findFile(filename)
                if (file != null) {
                    return file to (extension == GIF_IMAGE_EXTENSION)
                }
            }
            return null to false
        }

        /**
         * @param extension with dot
         */
        fun perFilename(index: Int, extension: String?): String {
            return String.format(Locale.US, "%08d%s", index + 1, extension)
        }

        fun getGalleryDownloadDir(gid: Long): UniFile? {
            val dir = Settings.downloadLocation ?: return null
            val dirname = DownloadManager.getDownloadDirname(gid) ?: return null
            return dir.subFile(dirname)
        }
    }
}
