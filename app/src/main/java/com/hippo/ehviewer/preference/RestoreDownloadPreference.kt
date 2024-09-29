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
import com.hippo.ehviewer.EhDB
import com.hippo.ehviewer.GetText
import com.hippo.ehviewer.R
import com.hippo.ehviewer.Settings
import com.hippo.ehviewer.client.EhEngine.fillGalleryListByApi
import com.hippo.ehviewer.client.EhUrl
import com.hippo.ehviewer.client.data.BaseGalleryInfo
import com.hippo.ehviewer.client.parser.GalleryDetailUrlParser
import com.hippo.ehviewer.spider.SpiderQueen
import com.hippo.ehviewer.spider.readCompatFromUniFile
import com.hippo.unifile.UniFile
import com.hippo.unifile.openInputStream
import com.hippo.util.launchUI
import com.hippo.util.runSuspendCatching
import com.hippo.util.withUIContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import okio.buffer
import okio.source
import com.hippo.ehviewer.download.DownloadManager as downloadManager

class RestoreDownloadPreference(
    context: Context,
    attrs: AttributeSet? = null,
) : TaskPreference(context, attrs) {
    private var restoreDirCount = 0

    @SuppressLint("ParcelCreator")
    private class RestoreItem(val dirname: String, gid: Long, token: String) : BaseGalleryInfo(gid, token)

    private fun getRestoreItem(dir: UniFile): RestoreItem? {
        if (!dir.isDirectory) return null
        return runCatching {
            val result = dir.findFile(SpiderQueen.SPIDER_INFO_FILENAME)?.let {
                readCompatFromUniFile(it)?.run {
                    GalleryDetailUrlParser.Result(gid, token!!)
                }
            } ?: dir.findFile(COMIC_INFO_FILE)?.let { file ->
                file.openInputStream().source().buffer().use {
                    GalleryDetailUrlParser.parse(it.readUtf8())
                }
            } ?: return null
            val gid = result.gid
            val dirname = dir.name!!
            if (downloadManager.containDownloadInfo(gid)) {
                // Restore download dir to avoid re-download
                val dbDirName = EhDB.getDownloadDirname(gid)
                if (null == dbDirName || dirname != dbDirName) {
                    EhDB.putDownloadDirname(gid, dirname)
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

    override val jobTitle = GetText.getString(R.string.settings_download_restore_download_items)

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
                            var i = 0
                            val n = result.size
                            while (i < n) {
                                val item = result[i]
                                // Avoid failed gallery info
                                if (null != item.title) {
                                    // Put to download
                                    downloadManager.addDownload(item, null)
                                    // Put download dir to DB
                                    EhDB.putDownloadDirname(item.gid, item.dirname)
                                    count++
                                }
                                i++
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
        private val RESTORE_NOT_FOUND = GetText.getString(R.string.settings_download_restore_not_found)
        private val RESTORE_FAILED = GetText.getString(R.string.settings_download_restore_failed)
        private val RESTORE_COUNT_MSG =
            { cnt: Int -> if (cnt == 0) RESTORE_NOT_FOUND else GetText.getString(R.string.settings_download_restore_successfully, cnt) }
    }
}

private const val COMIC_INFO_FILE = "ComicInfo.xml"
private var singletonJob: Job? = null
