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

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.widget.Toast
import androidx.browser.customtabs.CustomTabColorSchemeParams
import androidx.browser.customtabs.CustomTabsIntent
import com.hippo.ehviewer.client.EhUrlOpener.parseUrl
import com.hippo.ehviewer.client.data.GalleryDetail
import com.hippo.ehviewer.client.parser.GalleryPageUrlParser
import com.hippo.ehviewer.ui.GalleryActivity
import com.hippo.ehviewer.ui.MainActivity
import com.hippo.scene.StageActivity
import rikka.core.res.resolveColor

object UrlOpener {
    fun openUrl(
        context: Context,
        url: String?,
        ehUrl: Boolean,
        galleryDetail: GalleryDetail? = null,
    ) {
        if (url.isNullOrEmpty()) {
            return
        }
        var intent: Intent
        val uri = Uri.parse(url)
        if (ehUrl) {
            galleryDetail?.let {
                val result = GalleryPageUrlParser.parse(url)
                if (result != null) {
                    if (result.gid == it.gid) {
                        intent = Intent(context, GalleryActivity::class.java)
                        intent.action = GalleryActivity.ACTION_EH
                        intent.putExtra(GalleryActivity.KEY_GALLERY_INFO, it)
                        intent.putExtra(GalleryActivity.KEY_PAGE, result.page)
                        context.startActivity(intent)
                        return
                    }
                } else if (url.startsWith("#c")) {
                    try {
                        intent = Intent(context, GalleryActivity::class.java)
                        intent.action = GalleryActivity.ACTION_EH
                        intent.putExtra(GalleryActivity.KEY_GALLERY_INFO, it)
                        intent.putExtra(GalleryActivity.KEY_PAGE, url.replace("#c", "").toInt() - 1)
                        context.startActivity(intent)
                        return
                    } catch (_: NumberFormatException) {
                    }
                }
            }
            parseUrl(url)?.let {
                intent = Intent(context, MainActivity::class.java)
                intent.action = StageActivity.ACTION_START_SCENE
                intent.putExtra(StageActivity.KEY_SCENE_NAME, it.clazz.name)
                intent.putExtra(StageActivity.KEY_SCENE_ARGS, it.args)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                context.startActivity(intent)
                return
            }
        }
        val isNight = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_YES > 0
        val customTabsIntent = CustomTabsIntent.Builder()
        customTabsIntent.setShowTitle(true)
        val params = CustomTabColorSchemeParams.Builder()
            .setToolbarColor(context.theme.resolveColor(R.attr.toolbarColor))
            .build()
        customTabsIntent.setDefaultColorSchemeParams(params)
        customTabsIntent.setColorScheme(if (isNight) CustomTabsIntent.COLOR_SCHEME_DARK else CustomTabsIntent.COLOR_SCHEME_LIGHT)
        try {
            customTabsIntent.build().launchUrl(context, uri)
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(context, R.string.no_browser_installed, Toast.LENGTH_LONG).show()
        }
    }
}
