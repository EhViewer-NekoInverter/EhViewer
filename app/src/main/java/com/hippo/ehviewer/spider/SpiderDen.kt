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
import android.os.ParcelFileDescriptor
import android.os.ParcelFileDescriptor.MODE_READ_WRITE
import coil.disk.DiskCache
import com.hippo.ehviewer.EhApplication
import com.hippo.ehviewer.EhApplication.Companion.application
import com.hippo.ehviewer.EhDB
import com.hippo.ehviewer.Settings
import com.hippo.ehviewer.client.EhCacheKeyFactory
import com.hippo.ehviewer.client.EhUtils.getSuitableTitle
import com.hippo.ehviewer.client.data.GalleryInfo
import com.hippo.ehviewer.client.referer
import com.hippo.ehviewer.coil.edit
import com.hippo.ehviewer.coil.read
import com.hippo.ehviewer.gallery.GalleryProvider2
import com.hippo.image.Image
import com.hippo.image.Image.CloseableSource
import com.hippo.sendTo
import com.hippo.unifile.UniFile
import com.hippo.unifile.openOutputStream
import com.hippo.util.runSuspendCatching
import com.hippo.yorozuya.FileUtils
import io.ktor.client.plugins.onDownload
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.ContentType
import io.ktor.http.contentLength
import io.ktor.http.contentType
import io.ktor.utils.io.jvm.nio.copyTo
import java.io.File
import java.io.IOException
import java.util.Locale
import kotlin.io.path.readText

private val client = EhApplication.ktorClient

class SpiderDen(private val mGalleryInfo: GalleryInfo) {
    private val mGid: Long = mGalleryInfo.gid
    private var mDownloadDir: UniFile? = getGalleryDownloadDir(mGid)

    @Volatile
    private var mMode = SpiderQueen.MODE_READ

    fun setMode(@SpiderQueen.Mode mode: Int) {
        mMode = mode
        if (mode == SpiderQueen.MODE_DOWNLOAD) {
            ensureDownloadDir()
        }
    }

    private fun ensureDownloadDir(): Boolean {
        mDownloadDir?.let { return it.ensureDir() }
        val title = getSuitableTitle(mGalleryInfo)
        val dirname = FileUtils.sanitizeFilename("$mGid-$title")
        EhDB.putDownloadDirname(mGid, dirname)
        mDownloadDir = getGalleryDownloadDir(mGid)
        return mDownloadDir?.ensureDir() ?: false
    }

    val downloadDir: UniFile?
        get() {
            if (mDownloadDir == null) {
                mDownloadDir = getGalleryDownloadDir(mGid)
            }
            return mDownloadDir?.takeIf { it.isDirectory }
        }

    private fun containInCache(index: Int): Boolean {
        val key = EhCacheKeyFactory.getImageKey(mGid, index)
        return sCache[key]?.apply { close() } != null
    }

    private fun containInDownloadDir(index: Int): Boolean {
        val dir = downloadDir ?: return false
        return findImageFile(dir, index) != null
    }

    /**
     * @param extension with dot
     */
    private fun fixExtension(extension: String): String {
        return extension.takeIf { GalleryProvider2.SUPPORT_IMAGE_EXTENSIONS.contains(it) }
            ?: GalleryProvider2.SUPPORT_IMAGE_EXTENSIONS[0]
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
                if (skip && extension != GalleryProvider2.SUPPORT_IMAGE_EXTENSIONS[2]) {
                    return false
                }
                // Copy from cache to download dir
                val file = dir.createFile(generateImageFilename(index, extension)) ?: return false
                file.openFileDescriptor("w").use { outFd ->
                    ParcelFileDescriptor.open(data.toFile(), MODE_READ_WRITE).use {
                        it sendTo outFd
                    }
                }
            }
        }.getOrElse {
            it.printStackTrace()
            false
        }
    }

    operator fun contains(index: Int): Boolean {
        return when (mMode) {
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
        return downloadDir?.let { findImageFile(it, index)?.delete() } ?: false
    }

    fun remove(index: Int): Boolean {
        return removeFromCache(index) or removeFromDownloadDir(index)
    }

    private fun findDownloadFileForIndex(index: Int, extension: String): UniFile? {
        val dir = downloadDir ?: return null
        val ext = fixExtension(".$extension")
        return dir.createFile(generateImageFilename(index, ext))
    }

    @Throws(IOException::class)
    suspend fun makeHttpCallAndSaveImage(
        index: Int,
        url: String,
        referer: String?,
        notifyProgress: (Long, Long, Int) -> Unit
    ): Boolean {
        return client.prepareGet(url) {
            var state: Long = 0
            referer(referer)
            onDownload { bytesSentTotal, contentLength ->
                notifyProgress(contentLength, bytesSentTotal, (bytesSentTotal - state).toInt())
                state = bytesSentTotal
            }
        }.execute {
            if (it.status.value >= 400) return@execute false
            saveFromHttpResponse(index, it)
        }
    }

    private suspend fun saveFromHttpResponse(index: Int, body: HttpResponse): Boolean {
        val contentType = body.contentType()
        val extension = contentType?.contentSubtype ?: "jpg"
        val length = body.contentLength() ?: return false

        suspend fun doSave(outFile: UniFile): Long {
            val ret: Long
            outFile.openOutputStream().use {
                ret = body.bodyAsChannel().copyTo(it.channel)
            }
            if (contentType == ContentType.Image.GIF)
                outFile.openFileDescriptor("rw").use {
                    Image.rewriteGifSource2(it.fd)
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
        if (mMode == SpiderQueen.MODE_READ) {
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
        file.openFileDescriptor("w").use { toFd ->
            val key = EhCacheKeyFactory.getImageKey(mGid, index)

            // Read from diskCache first
            sCache[key]?.use { snapshot ->
                runCatching {
                    UniFile.fromFile(snapshot.data.toFile())!!.openFileDescriptor("r").use {
                        it sendTo toFd
                    }
                }.onSuccess {
                    return true
                }.onFailure {
                    it.printStackTrace()
                    return false
                }
            }

            // Read from download dir
            downloadDir?.let { uniFile ->
                runCatching {
                    findImageFile(uniFile, index)?.openFileDescriptor("r")?.use {
                        it sendTo toFd
                    }
                }.onFailure {
                    it.printStackTrace()
                    return false
                }.onSuccess {
                    return true
                }
            }
        }
        return false
    }

    fun getExtension(index: Int): String? {
        val key = EhCacheKeyFactory.getImageKey(mGid, index)
        return sCache[key]?.use { it.metadata.toNioPath().readText() }
            ?: downloadDir?.let { findImageFile(it, index) }
                ?.name.let { FileUtils.getExtensionFromFilename(it) }
    }

    fun getImageSource(index: Int): CloseableSource? {
        if (mMode == SpiderQueen.MODE_READ) {
            val key = EhCacheKeyFactory.getImageKey(mGid, index)
            val snapshot: DiskCache.Snapshot? = sCache[key]
            if (snapshot != null) {
                val source = ImageDecoder.createSource(snapshot.data.toFile())
                return object : CloseableSource {
                    override val source: ImageDecoder.Source
                        get() = source

                    override fun close() {
                        snapshot.close()
                    }
                }
            }
        }
        val dir = downloadDir ?: return null
        val file = findImageFile(dir, index) ?: return null
        file.openFileDescriptor("rw").use {
            Image.rewriteGifSource2(it.fd)
        }
        return object : CloseableSource {
            override val source: ImageDecoder.Source
                get() = file.imageSource

            override fun close() {}
        }
    }

    companion object {
        private val COMPAT_IMAGE_EXTENSIONS = GalleryProvider2.SUPPORT_IMAGE_EXTENSIONS + ".jpeg"

        private val sCache by lazy {
            DiskCache.Builder()
                .directory(File(application.cacheDir, "image"))
                .maxSizeBytes(Settings.readCacheSize.coerceIn(160, 5120).toLong() * 1024 * 1024)
                .build()
        }

        fun getGalleryDownloadDir(gid: Long): UniFile? {
            val dir = Settings.downloadLocation
            // Read from DB
            var dirname = EhDB.getDownloadDirname(gid)
            return if (dir != null && dirname != null) {
                // Some dirname may be invalid in some version
                dirname = FileUtils.sanitizeFilename(dirname)
                EhDB.putDownloadDirname(gid, dirname)
                dir.subFile(dirname)
            } else {
                null
            }
        }

        /**
         * @param extension with dot
         */
        fun generateImageFilename(index: Int, extension: String?): String {
            return String.format(Locale.US, "%08d%s", index + 1, extension)
        }

        private fun findImageFile(dir: UniFile, index: Int): UniFile? {
            for (extension in COMPAT_IMAGE_EXTENSIONS) {
                val filename = generateImageFilename(index, extension)
                val file = dir.findFile(filename)
                if (file != null) {
                    return file
                }
            }
            return null
        }
    }
}