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
package com.hippo.scene

import android.app.assist.AssistContent
import android.os.Bundle
import android.view.View
import androidx.annotation.IntDef
import androidx.fragment.app.Fragment
import com.hippo.ehviewer.R
import com.hippo.yorozuya.collect.IntList
import rikka.core.res.resolveDrawable
import kotlin.math.min

open class SceneFragment : Fragment() {
    var result: Bundle? = null
    private var resultCode = RESULT_CANCELED
    private var mRequestSceneTagList: MutableList<String> = ArrayList(0)
    private var mRequestCodeList = IntList()

    open fun onNewArguments(args: Bundle) {}

    fun startScene(announcer: Announcer, horizontal: Boolean) {
        val activity = activity
        if (activity is StageActivity) {
            activity.startScene(announcer, horizontal)
        }
    }

    fun startScene(announcer: Announcer) {
        val activity = activity
        if (activity is StageActivity) {
            activity.startScene(announcer)
        }
    }

    fun finish(transitionHelper: TransitionHelper? = null) {
        val activity = activity
        if (activity is StageActivity) {
            activity.finishScene(this, transitionHelper)
        }
    }

    fun finishStage() {
        val activity = activity
        activity?.finish()
    }

    val stackIndex: Int
        /**
         * @return negative for error
         */
        get() {
            val activity = activity
            return if (activity is StageActivity) {
                activity.getSceneIndex(this)
            } else {
                -1
            }
        }

    open fun onBackPressed() {
        finish()
    }

    open fun onProvideAssistContent(outContent: AssistContent) {}

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        view.setTag(R.id.fragment_tag, tag)
        view.background =
            requireActivity().getTheme().resolveDrawable(android.R.attr.windowBackground)
        // Notify
        val activity = activity
        if (activity is StageActivity) {
            activity.onSceneViewCreated(this, savedInstanceState)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // Notify
        val activity = activity
        if (activity is StageActivity) {
            activity.onSceneViewDestroyed(this)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        val activity = activity
        if (activity is StageActivity) {
            activity.onSceneDestroyed(this)
        }
    }

    fun addRequest(requestSceneTag: String, requestCode: Int) {
        mRequestSceneTagList.add(requestSceneTag)
        mRequestCodeList.add(requestCode)
    }

    fun returnResult(stage: StageActivity) {
        for (i in 0 until min(mRequestSceneTagList.size.toDouble(), mRequestCodeList.size.toDouble()).toInt()) {
            val tag = mRequestSceneTagList[i]
            val code = mRequestCodeList[i]
            val scene = stage.findSceneByTag(tag)
            scene?.onSceneResult(code, resultCode, result)
        }
        mRequestSceneTagList.clear()
        mRequestCodeList.clear()
    }

    protected open fun onSceneResult(requestCode: Int, resultCode: Int, data: Bundle?) {}

    fun setResult(resultCode: Int, result: Bundle?) {
        this.resultCode = resultCode
        this.result = result
    }

    @IntDef(LAUNCH_MODE_STANDARD, LAUNCH_MODE_SINGLE_TOP, LAUNCH_MODE_SINGLE_TASK)
    @Retention(
        AnnotationRetention.SOURCE,
    )
    annotation class LaunchMode

    companion object {
        const val LAUNCH_MODE_STANDARD = 0
        const val LAUNCH_MODE_SINGLE_TOP = 1
        const val LAUNCH_MODE_SINGLE_TASK = 2

        /**
         * Standard scene result: operation canceled.
         */
        const val RESULT_CANCELED = 0

        /**
         * Standard scene result: operation succeeded.
         */
        const val RESULT_OK = -1
    }
}
