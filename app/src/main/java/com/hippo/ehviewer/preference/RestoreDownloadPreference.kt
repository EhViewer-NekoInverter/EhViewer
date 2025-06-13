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
package com.hippo.ehviewer.preference

import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import com.hippo.ehviewer.GetText
import com.hippo.ehviewer.R
import com.hippo.ehviewer.Settings
import com.hippo.ehviewer.client.EhEngine.fillGalleryListByApi
import com.hippo.ehviewer.client.EhUrl
import com.hippo.ehviewer.client.data.BaseGalleryInfo
import com.hippo.ehviewer.client.parser.GalleryDetailUrlParser
import com.hippo.ehviewer.download.DownloadManager
import com.hippo.ehviewer.spider.SpiderDen.Companion.getGalleryDownloadDir
import com.hippo.ehviewer.spider.SpiderInfo
import com.hippo.ehviewer.spider.SpiderQueen.Companion.SPIDER_INFO_FILENAME
import com.hippo.ehviewer.spider.readFromUniFile
import com.hippo.ehviewer.spider.saveToUniFile
import com.hippo.unifile.UniFile
import com.hippo.unifile.openInputStream
import com.hippo.util.launchUI
import com.hippo.util.runSuspendCatching
import com.hippo.util.withUIContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import okio.buffer
import okio.source

class RestoreDownloadPreference(
    context: Context,
    attrs: AttributeSet? = null,
) : TaskPreference(context, attrs) {
    private var restoreDirCount = 0
    private val nonSpiderInfoItemList = mutableListOf<Long>()

    @SuppressLint("ParcelCreator")
    private class RestoreItem(val dirname: String, gid: Long, token: String) : BaseGalleryInfo(gid, token)

    private fun getRestoreItem(dir: UniFile): RestoreItem? {
        if (!dir.isDirectory) return null
        return runCatching {
            val result = dir.findFile(SPIDER_INFO_FILENAME)?.let {
                readFromUniFile(it)?.run {
                    GalleryDetailUrlParser.Result(gid, token!!)
                }
            } ?: dir.findFile(COMIC_INFO_FILE)?.let { file ->
                file.openInputStream().source().buffer().use {
                    GalleryDetailUrlParser.parse(it.readUtf8())
                }.also {
                    it?.apply { nonSpiderInfoItemList.add(gid) }
                }
            } ?: return null
            val gid = result.gid
            val dirname = dir.name!!
            if (DownloadManager.containDownloadInfo(gid)) {
                // Restore download dir to avoid re-download
                val dbDirname = DownloadManager.getDownloadDirname(gid)
                if (null == dbDirname || dirname != dbDirname) {
                    DownloadManager.putDownloadDirname(gid, dirname)
                    restoreDirCount++
                }
                return null
            }
            RestoreItem(dirname, gid, result.token)
        }.onFailure {
            it.printStackTrace()
        }.getOrNull()
    }

    private suspend fun doRealWork(): List<RestoreItem>? {
        val dir = Settings.downloadLocation ?: return null
        val files = dir.listFiles() ?: return null
        return runSuspendCatching {
            files.mapNotNull { getRestoreItem(it) }.also {
                fillGalleryListByApi(it, EhUrl.referer)
            }
        }.onFailure {
            it.printStackTrace()
        }.getOrNull()
    }

    override val jobTitle = JOB_TITLE_RESTORE_DOWNLOAD

    override fun launchJob() {
        if (singletonJob?.isActive == true) {
            singletonJob?.invokeOnCompletion {
                launchUI {
                    mDialog.dismiss()
                }
            }
        } else {
            singletonJob = launch {
                val result = doRealWork()
                withUIContext {
                    if (result == null) {
                        showTip(RESTORE_FAILED)
                    } else {
                        if (result.isEmpty()) {
                            showTip(RESTORE_COUNT_MSG(restoreDirCount))
                        } else {
                            var count = 0
                            result.forEach { item ->
                                if (null != item.title) {
                                    val gid = item.gid
                                    // Put to download
                                    DownloadManager.addDownload(item, null)
                                    // Put download dir to DB
                                    DownloadManager.putDownloadDirname(gid, item.dirname)
                                    // Create missing .ehviewer file
                                    if (gid in nonSpiderInfoItemList) {
                                        getGalleryDownloadDir(gid)?.run {
                                            createFile(SPIDER_INFO_FILENAME)?.also {
                                                SpiderInfo(gid, item.token, item.pages).saveToUniFile(it)
                                            }
                                        }
                                    }
                                    count++
                                }
                            }
                            showTip(RESTORE_COUNT_MSG(count + restoreDirCount))
                        }
                    }
                    mDialog.dismiss()
                }
            }
        }
    }

    companion object {
        private val JOB_TITLE_RESTORE_DOWNLOAD = GetText.getString(R.string.settings_download_restore_download_items)
        private val RESTORE_NOT_FOUND = GetText.getString(R.string.settings_download_restore_not_found)
        private val RESTORE_FAILED = GetText.getString(R.string.settings_download_restore_failed)
        private val RESTORE_COUNT_MSG =
            { cnt: Int -> if (cnt == 0) RESTORE_NOT_FOUND else GetText.getString(R.string.settings_download_restore_successfully, cnt) }
    }
}

private const val COMIC_INFO_FILE = "ComicInfo.xml"
private var singletonJob: Job? = null
