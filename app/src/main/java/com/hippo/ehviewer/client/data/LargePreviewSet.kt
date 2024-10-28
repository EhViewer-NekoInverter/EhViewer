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
package com.hippo.ehviewer.client.data

import com.hippo.ehviewer.client.EhUtils
import com.hippo.ehviewer.client.getLargePreviewKey
import com.hippo.widget.LoadImageView
import com.hippo.yorozuya.collect.IntList

class LargePreviewSet(
    private val mPositionList: IntList = IntList(),
    private val mImageUrlList: ArrayList<String> = arrayListOf(),
    private val mPageUrlList: ArrayList<String> = arrayListOf(),
) : PreviewSet() {
    fun addItem(index: Int, imageUrl: String, pageUrl: String) {
        mPositionList.add(index)
        mImageUrlList.add(imageUrl)
        mPageUrlList.add(pageUrl)
    }

    override fun size(): Int = mImageUrlList.size

    override fun getPosition(index: Int): Int = mPositionList[index]

    override fun getPageUrlAt(index: Int): String = mPageUrlList[index]

    override fun getGalleryPreview(gid: Long, index: Int): GalleryPreview {
        val galleryPreview = GalleryPreview()
        galleryPreview.position = mPositionList[index]
        galleryPreview.imageKey = getLargePreviewKey(gid, galleryPreview.position)
        galleryPreview.imageUrl = mImageUrlList[index]
        galleryPreview.pageUrl = mPageUrlList[index]
        return galleryPreview
    }

    override fun load(view: LoadImageView, gid: Long, index: Int) {
        view.resetClip()
        view.load(
            getLargePreviewKey(gid, mPositionList[index]),
            EhUtils.fixThumbUrl(mImageUrlList[index]),
        )
    }
}
