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

import android.content.Context
import android.view.View
import androidx.core.view.ViewCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentTransaction
import androidx.transition.TransitionInflater
import com.hippo.ehviewer.R
import com.hippo.scene.TransitionHelper

class EnterGalleryDetailTransaction(
    private val mThumb: View?,
) : TransitionHelper {
    override fun onTransition(
        context: Context,
        transaction: FragmentTransaction,
        exit: Fragment,
        enter: Fragment,
    ): Boolean {
        if (mThumb == null || enter !is GalleryDetailScene) {
            return false
        }
        ViewCompat.getTransitionName(mThumb)?.let {
            exit.sharedElementReturnTransition =
                TransitionInflater.from(context).inflateTransition(R.transition.trans_move)
            exit.exitTransition =
                TransitionInflater.from(context).inflateTransition(R.transition.trans_fade)
            enter.sharedElementEnterTransition =
                TransitionInflater.from(context).inflateTransition(R.transition.trans_move)
            enter.enterTransition =
                TransitionInflater.from(context).inflateTransition(R.transition.trans_fade)
            transaction.addSharedElement(mThumb, it)
        }
        return true
    }
}
