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
package com.hippo.ehviewer

import android.app.Activity
import android.content.Context
import android.os.StrictMode
import android.text.method.LinkMovementMethod
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatDelegate
import androidx.collection.LruCache
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.gif.AnimatedImageDecoder
import coil3.gif.GifDecoder
import coil3.network.ConnectivityChecker
import coil3.network.NetworkFetcher
import coil3.network.okhttp.asNetworkClient
import coil3.request.crossfade
import coil3.serviceLoaderEnabled
import coil3.util.DebugLogger
import com.hippo.ehviewer.client.EhCookieStore
import com.hippo.ehviewer.client.EhEngine
import com.hippo.ehviewer.client.EhTagDatabase
import com.hippo.ehviewer.client.data.GalleryDetail
import com.hippo.ehviewer.coil.DownloadThumbInterceptor
import com.hippo.ehviewer.coil.MergeInterceptor
import com.hippo.ehviewer.dao.buildMainDB
import com.hippo.ehviewer.download.DownloadManager
import com.hippo.ehviewer.ui.EhActivity
import com.hippo.ehviewer.ui.keepNoMediaFileStatus
import com.hippo.scene.SceneApplication
import com.hippo.util.ReadableTime
import com.hippo.util.isAtLeastP
import com.hippo.util.launchIO
import com.hippo.util.loadHtml
import com.hippo.yorozuya.FileUtils
import com.hippo.yorozuya.IntIdGenerator
import eu.kanade.tachiyomi.network.interceptor.CloudflareInterceptor
import kotlinx.coroutines.DelicateCoroutinesApi
import okhttp3.Cache
import okhttp3.OkHttpClient
import okio.FileSystem
import okio.Path.Companion.toOkioPath

class EhApplication :
    SceneApplication(),
    SingletonImageLoader.Factory {
    private val mIdGenerator = IntIdGenerator()
    private val mGlobalStuffMap = HashMap<Int, Any>()
    private val mActivityList = ArrayList<Activity>()
    val topActivity: EhActivity?
        get() = if (mActivityList.isNotEmpty()) {
            mActivityList[mActivityList.size - 1] as EhActivity
        } else {
            null
        }

    fun recreateAllActivity() {
        mActivityList.forEach { it.recreate() }
    }

    @OptIn(DelicateCoroutinesApi::class)
    override fun onCreate() {
        application = this
        val handler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            try {
                if (Settings.saveCrashLog) {
                    Crash.saveCrashLog(e)
                }
            } catch (_: Throwable) {
            }
            handler?.uncaughtException(t, e)
        }
        super.onCreate()
        System.loadLibrary("ehviewer")
        Settings.initialize()
        ReadableTime.initialize(this)
        AppConfig.initialize(this)
        AppCompatDelegate.setDefaultNightMode(Settings.theme)

        launchIO {
            launchIO {
                nonCacheOkHttpClient
            }
            launchIO {
                EhTagDatabase.read()
            }
            launchIO {
                ehDatabase
            }
            launchIO {
                DownloadManager.isIdle
            }
            launchIO {
                cleanupDownload()
            }
            launchIO {
                theDawnOfNewDay()
            }
        }
        mIdGenerator.setNextId(Settings.getInt(KEY_GLOBAL_STUFF_NEXT_ID, 0))
        if (BuildConfig.DEBUG) {
            StrictMode.enableDefaults()
        }
    }

    private suspend fun cleanupDownload() {
        runCatching {
            keepNoMediaFileStatus()
        }.onFailure {
            it.printStackTrace()
        }
        runCatching {
            clearTempDir()
        }.onFailure {
            it.printStackTrace()
        }
    }

    private suspend fun theDawnOfNewDay() {
        runCatching {
            if (Settings.requestNews && EhCookieStore.hasSignedIn()) {
                EhEngine.getNews(true)?.let { showEventPane(it) }
            }
        }.onFailure {
            it.printStackTrace()
        }
    }

    fun showEventPane(html: String) {
        if (Settings.hideHvEvents && html.contains("You have encountered a monster!")) {
            return
        }
        val activity = topActivity
        activity?.runOnUiThread {
            val dialog = AlertDialog.Builder(activity)
                .setMessage(loadHtml(html))
                .setPositiveButton(android.R.string.ok, null)
                .create()
            dialog.setOnShowListener {
                val messageView = dialog.findViewById<View>(android.R.id.message)
                if (messageView is TextView) {
                    messageView.movementMethod = LinkMovementMethod.getInstance()
                }
            }
            try {
                dialog.show()
            } catch (_: Throwable) {
                // ignore
            }
        }
    }

    private fun clearTempDir() {
        var dir = AppConfig.getTempDir()
        if (null != dir) {
            FileUtils.deleteContent(dir)
        }
        dir = AppConfig.getExternalTempDir()
        if (null != dir) {
            FileUtils.deleteContent(dir)
        }
    }

    fun putGlobalStuff(o: Any): Int {
        val id = mIdGenerator.nextId()
        mGlobalStuffMap[id] = o
        Settings.putInt(KEY_GLOBAL_STUFF_NEXT_ID, mIdGenerator.nextId())
        return id
    }

    fun containGlobalStuff(id: Int): Boolean = mGlobalStuffMap.containsKey(id)

    fun removeGlobalStuff(id: Int): Any? = mGlobalStuffMap.remove(id)

    fun removeGlobalStuff(o: Any) {
        mGlobalStuffMap.values.removeAll(setOf(o))
    }

    fun registerActivity(activity: Activity) {
        mActivityList.add(activity)
    }

    fun unregisterActivity(activity: Activity) {
        mActivityList.remove(activity)
    }

    override fun newImageLoader(context: Context) = ImageLoader.Builder(context).apply {
        serviceLoaderEnabled(false)
        components {
            if (isAtLeastP) {
                add(AnimatedImageDecoder.Factory(false))
            } else {
                add(GifDecoder.Factory())
            }
            add(
                NetworkFetcher.Factory(
                    networkClient = { nonCacheOkHttpClient.asNetworkClient() },
                    connectivityChecker = { ConnectivityChecker.ONLINE },
                ),
            )
            add(MergeInterceptor)
            add(DownloadThumbInterceptor)
        }
        crossfade(300)
        diskCache(thumbCache)
        if (BuildConfig.DEBUG) logger(DebugLogger())
    }.build()

    companion object {
        private const val KEY_GLOBAL_STUFF_NEXT_ID = "global_stuff_next_id"

        lateinit var application: EhApplication
            private set

        val cacheDir by lazy { application.cacheDir.toOkioPath() }

        val ehProxySelector by lazy { EhProxySelector() }

        val nonCacheOkHttpClient by lazy {
            OkHttpClient.Builder().apply {
                cookieJar(EhCookieStore)
                proxySelector(ehProxySelector)
                addInterceptor(CloudflareInterceptor(application))
            }.build()
        }

        val noRedirectOkHttpClient by lazy {
            nonCacheOkHttpClient.newBuilder()
                .followRedirects(false)
                .build()
        }

        // Never use this okhttp client to download large blobs!!!
        val okHttpClient by lazy {
            nonCacheOkHttpClient.newBuilder()
                .cache(Cache(FileSystem.SYSTEM, cacheDir / "http_cache", 20 * 1024 * 1024))
                .build()
        }

        val galleryDetailCache by lazy {
            LruCache<Long, GalleryDetail>(25).also {
                favouriteStatusRouter.addListener { gid, slot ->
                    it[gid]?.favoriteSlot = slot
                }
            }
        }

        val favouriteStatusRouter by lazy { FavouriteStatusRouter() }

        val ehDatabase by lazy { buildMainDB(application) }

        val thumbCache by lazy {
            DiskCache.Builder()
                .directory(cacheDir / "thumb")
                .maxSizeBytes((Settings.readCacheSize / 5).coerceIn(64, 1024).toLong() * 1024 * 1024)
                .build()
        }

        val imageCache by lazy {
            DiskCache.Builder()
                .directory(cacheDir / "image")
                .maxSizeBytes((Settings.readCacheSize / 5 * 4).coerceIn(256, 4096).toLong() * 1024 * 1024)
                .build()
        }
    }
}
