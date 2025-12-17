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
package com.hippo.ehviewer.gallery

import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.Log
import com.hippo.ehviewer.GetText
import com.hippo.ehviewer.R
import com.hippo.ehviewer.Settings
import com.hippo.ehviewer.jni.closeArchive
import com.hippo.ehviewer.jni.extractToByteBuffer
import com.hippo.ehviewer.jni.extractToFd
import com.hippo.ehviewer.jni.getFilename
import com.hippo.ehviewer.jni.needPassword
import com.hippo.ehviewer.jni.openArchive
import com.hippo.ehviewer.jni.providePassword
import com.hippo.ehviewer.jni.releaseByteBuffer
import com.hippo.image.ByteBufferSource
import com.hippo.image.Image
import com.hippo.unifile.UniFile
import com.hippo.yorozuya.FileUtils
import java.nio.ByteBuffer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit

class ArchiveGalleryProvider(context: Context, private val uri: Uri, passwdFlow: Flow<String>) :
    GalleryProvider2(),
    CoroutineScope {
    override val coroutineContext = Dispatchers.IO + Job()
    private lateinit var pfd: ParcelFileDescriptor
    private val hostJob = launch(start = CoroutineStart.LAZY) {
        Log.d(DEBUG_TAG, "Open archive $uri")
        pfd = context.contentResolver.openFileDescriptor(uri, "r")!!
        size = openArchive(pfd.fd, pfd.statSize, true)
        if (size == 0) {
            return@launch
        }
        if (needPassword()) {
            Settings.archivePasswds?.forEach {
                if (providePassword(it)) return@launch
            }
            passwdFlow.collect {
                if (providePassword(it)) {
                    Settings.putPasswdToArchivePasswds(it)
                    currentCoroutineContext().cancel()
                }
            }
        }
    }

    override var size = -1

    override fun start() {
        hostJob.start()
    }

    override fun stop() {
        cancel()
        closeArchive()
        pfd.close()
        Log.d(DEBUG_TAG, "Close archive $uri successfully!")
        super.stop()
    }

    private val mJobMap = hashMapOf<Int, Job>()
    private val mWorkerMutex by lazy { (0 until size).map { Mutex() } }
    private val mSemaphore = Semaphore(4)

    override fun onRequest(index: Int) {
        notifyPageWait(index)
        synchronized(mJobMap) {
            val current = mJobMap[index]
            if (current?.isActive != true) {
                mJobMap[index] = launch {
                    mWorkerMutex[index].withLock {
                        mSemaphore.withPermit {
                            doRealWork(index)
                        }
                    }
                }
            }
        }
    }

    private suspend fun doRealWork(index: Int) {
        val buffer = extractToByteBuffer(index)
        buffer ?: return
        check(buffer.isDirect)
        val src = object : ByteBufferSource {
            override val source: ByteBuffer = buffer
            override fun close() {
                releaseByteBuffer(buffer)
            }
        }
        runCatching {
            currentCoroutineContext().ensureActive()
        }.onFailure {
            src.close()
            throw it
        }
        val image = Image.decode(src) ?: return notifyPageFailed(index, GetText.getString(R.string.error_decoding_failed))
        runCatching {
            currentCoroutineContext().ensureActive()
        }.onFailure {
            image.recycle()
            throw it
        }
        notifyPageSucceed(index, image)
    }

    override fun onForceRequest(index: Int) {
        onRequest(index)
    }

    override suspend fun awaitReady(): Boolean {
        hostJob.join()
        return size != -1
    }

    override val isReady: Boolean
        get() = size != -1

    override fun onCancelRequest(index: Int) {
        mJobMap[index]?.cancel()
    }

    override fun getImageFilename(index: Int): String = FileUtils.getNameFromFilename(getImageFilenameWithExtension(index))

    override fun getImageFilenameWithExtension(index: Int): String = FileUtils.sanitizeFilename(getFilename(index))

    override fun save(index: Int, file: UniFile) = runCatching {
        file.openFileDescriptor("w").use {
            extractToFd(index, it.fd)
        }
    }.getOrElse {
        it.printStackTrace()
        false
    }

    override fun save(index: Int, dir: UniFile, filename: String): UniFile {
        val extension = FileUtils.getExtensionFromFilename(getImageFilenameWithExtension(index))
        val dst = dir.subFile(if (null != extension) "$filename.$extension" else filename)
        save(index, dst!!)
        return dst
    }

    override suspend fun downloadOriginal(index: Int, dir: UniFile, filename: String): UniFile? = null

    override fun preloadPages(pages: List<Int>, pair: Pair<Int, Int>) {}
}

private const val DEBUG_TAG = "ArchiveGalleryProvider"
