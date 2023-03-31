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
package com.hippo.ehviewer.ui.scene

import android.os.Bundle
import android.util.Log
import com.hippo.ehviewer.Settings
import com.hippo.ehviewer.client.EhUtils
import com.hippo.scene.Announcer

/**
 * Scene for safety, can't be covered
 */
open class SolidScene : BaseScene() {
    fun startSceneForCheckStep(checkStep: Int, args: Bundle?) {
        when (checkStep) {
            CHECK_STEP_SECURITY -> {
                if (EhUtils.needSignedIn()) {
                    startScene(Announcer(SignInScene::class.java).setArgs(args), true)
                } else {
                    startSceneForCheckStep(CHECK_STEP_SIGN_IN, args)
                }
            }
            CHECK_STEP_SIGN_IN -> {
                if (Settings.selectSite) {
                    startScene(Announcer(SelectSiteScene::class.java).setArgs(args), true)
                } else {
                    startSceneForCheckStep(CHECK_STEP_SELECT_SITE, args)
                }
            }
            CHECK_STEP_SELECT_SITE -> {
                var targetScene: String? = null
                var targetArgs: Bundle? = null
                if (null != args) {
                    targetScene = args.getString(KEY_TARGET_SCENE)
                    targetArgs = args.getBundle(KEY_TARGET_ARGS)
                }
                var clazz: Class<*>? = null
                if (targetScene != null) {
                    try {
                        clazz = Class.forName(targetScene)
                    } catch (e: ClassNotFoundException) {
                        Log.e(TAG, "Can't find class with name: $targetScene")
                    }
                }
                if (clazz != null) {
                    startScene(Announcer(clazz).setArgs(targetArgs))
                } else {
                    val newArgs = Bundle()
                    newArgs.putString(GalleryListScene.KEY_ACTION, Settings.launchPageGalleryListSceneAction)
                    startScene(Announcer(GalleryListScene::class.java).setArgs(newArgs))
                }
            }
        }
    }

    companion object {
        const val CHECK_STEP_SECURITY = 0
        const val CHECK_STEP_SIGN_IN = 1
        const val CHECK_STEP_SELECT_SITE = 2
        const val KEY_TARGET_SCENE = "target_scene"
        const val KEY_TARGET_ARGS = "target_args"
        private val TAG = SolidScene::class.java.simpleName
    }
}
