/*
 * Copyright (C) 2015 Hippo Seven
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
package com.hippo.ehviewer.widget

import android.content.Context
import android.os.Bundle
import android.os.Parcelable
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TableLayout
import com.hippo.ehviewer.R
import com.hippo.ehviewer.client.EhUtils
import com.hippo.util.getParcelableCompat
import com.hippo.widget.CheckTextView
import com.hippo.yorozuya.NumberUtils

class CategoryTable @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : TableLayout(context, attrs),
    View.OnLongClickListener {
    private var mDoujinshi: CheckTextView
    private var mManga: CheckTextView
    private var mArtistCG: CheckTextView
    private var mGameCG: CheckTextView
    private var mWestern: CheckTextView
    private var mNonH: CheckTextView
    private var mImageSets: CheckTextView
    private var mCosplay: CheckTextView
    private var mAsianPorn: CheckTextView
    private var mMisc: CheckTextView
    private var mOptions: Array<CheckTextView>

    init {
        LayoutInflater.from(context).inflate(R.layout.widget_category_table, this)
        val row0 = getChildAt(0) as ViewGroup
        mDoujinshi = row0.getChildAt(0) as CheckTextView
        mManga = row0.getChildAt(1) as CheckTextView
        val row1 = getChildAt(1) as ViewGroup
        mArtistCG = row1.getChildAt(0) as CheckTextView
        mGameCG = row1.getChildAt(1) as CheckTextView
        val row2 = getChildAt(2) as ViewGroup
        mWestern = row2.getChildAt(0) as CheckTextView
        mNonH = row2.getChildAt(1) as CheckTextView
        val row3 = getChildAt(3) as ViewGroup
        mImageSets = row3.getChildAt(0) as CheckTextView
        mCosplay = row3.getChildAt(1) as CheckTextView
        val row4 = getChildAt(4) as ViewGroup
        mAsianPorn = row4.getChildAt(0) as CheckTextView
        mMisc = row4.getChildAt(1) as CheckTextView
        mOptions = arrayOf(
            mDoujinshi, mManga, mArtistCG, mGameCG, mWestern, mNonH, mImageSets, mCosplay, mAsianPorn, mMisc,
        )
        for (option in mOptions) {
            option.setOnLongClickListener(this)
        }
    }

    override fun onLongClick(v: View): Boolean {
        if (v is CheckTextView) {
            val checked = v.isChecked
            for (option in mOptions) {
                if (option !== v) {
                    option.isChecked = !checked
                }
            }
        }
        return true
    }

    var category: Int
        get() {
            var category = 0
            if (!mDoujinshi.isChecked) category = category or EhUtils.DOUJINSHI
            if (!mManga.isChecked) category = category or EhUtils.MANGA
            if (!mArtistCG.isChecked) category = category or EhUtils.ARTIST_CG
            if (!mGameCG.isChecked) category = category or EhUtils.GAME_CG
            if (!mWestern.isChecked) category = category or EhUtils.WESTERN
            if (!mNonH.isChecked) category = category or EhUtils.NON_H
            if (!mImageSets.isChecked) category = category or EhUtils.IMAGE_SET
            if (!mCosplay.isChecked) category = category or EhUtils.COSPLAY
            if (!mAsianPorn.isChecked) category = category or EhUtils.ASIAN_PORN
            if (!mMisc.isChecked) category = category or EhUtils.MISC
            return category
        }
        set(category) {
            mDoujinshi.isChecked = !NumberUtils.int2boolean(category and EhUtils.DOUJINSHI)
            mManga.isChecked = !NumberUtils.int2boolean(category and EhUtils.MANGA)
            mArtistCG.isChecked = !NumberUtils.int2boolean(category and EhUtils.ARTIST_CG)
            mGameCG.isChecked = !NumberUtils.int2boolean(category and EhUtils.GAME_CG)
            mWestern.isChecked = !NumberUtils.int2boolean(category and EhUtils.WESTERN)
            mNonH.isChecked = !NumberUtils.int2boolean(category and EhUtils.NON_H)
            mImageSets.isChecked = !NumberUtils.int2boolean(category and EhUtils.IMAGE_SET)
            mCosplay.isChecked = !NumberUtils.int2boolean(category and EhUtils.COSPLAY)
            mAsianPorn.isChecked = !NumberUtils.int2boolean(category and EhUtils.ASIAN_PORN)
            mMisc.isChecked = !NumberUtils.int2boolean(category and EhUtils.MISC)
        }

    override fun onSaveInstanceState(): Parcelable {
        val bundle = Bundle()
        bundle.putParcelable(STATE_KEY_SUPER, super.onSaveInstanceState())
        bundle.putInt(STATE_KEY_CATEGORY, category)
        return bundle
    }

    override fun onRestoreInstanceState(state: Parcelable) {
        if (state is Bundle) {
            super.onRestoreInstanceState(state.getParcelableCompat(STATE_KEY_SUPER))
            category = state.getInt(STATE_KEY_CATEGORY)
        }
    }

    companion object {
        private const val STATE_KEY_SUPER = "super"
        private const val STATE_KEY_CATEGORY = "category"
    }
}
