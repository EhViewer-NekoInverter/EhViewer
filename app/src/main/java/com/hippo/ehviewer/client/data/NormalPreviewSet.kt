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

import com.hippo.widget.LoadImageView
import com.hippo.yorozuya.collect.IntList

class NormalPreviewSet(
    private var mPositionList: IntList = IntList(),
    private var mImageKeyList: ArrayList<String> = arrayListOf(),
    private var mImageUrlList: ArrayList<String> = arrayListOf(),
    private var mOffsetXList: IntList = IntList(),
    private var mOffsetYList: IntList = IntList(),
    private var mClipWidthList: IntList = IntList(),
    private var mClipHeightList: IntList = IntList(),
    private var mPageUrlList: ArrayList<String> = arrayListOf(),
) : PreviewSet() {
    private fun getImageKey(imageUrl: String): String {
        val index = imageUrl.indexOf('/')
        return if (index >= 0) {
            imageUrl.substring(index + 1)
        } else {
            imageUrl
        }
    }

    fun addItem(
        position: Int,
        imageUrl: String,
        xOffset: Int,
        yOffset: Int,
        width: Int,
        height: Int,
        pageUrl: String,
    ) {
        mPositionList.add(position)
        mImageKeyList.add(getImageKey(imageUrl))
        mImageUrlList.add(imageUrl)
        mOffsetXList.add(xOffset)
        mOffsetYList.add(yOffset)
        mClipWidthList.add(width)
        mClipHeightList.add(height)
        mPageUrlList.add(pageUrl)
    }

    override fun size(): Int = mPositionList.size

    override fun getPosition(index: Int): Int = mPositionList[index]

    override fun getPageUrlAt(index: Int): String = mPageUrlList[index]

    override fun getGalleryPreview(gid: Long, index: Int): GalleryPreview {
        val galleryPreview = GalleryPreview()
        galleryPreview.position = mPositionList[index]
        galleryPreview.imageKey = mImageKeyList[index]
        galleryPreview.imageUrl = mImageUrlList[index]
        galleryPreview.pageUrl = mPageUrlList[index]
        galleryPreview.offsetX = mOffsetXList[index]
        galleryPreview.offsetY = mOffsetYList[index]
        galleryPreview.clipWidth = mClipWidthList[index]
        galleryPreview.clipHeight = mClipHeightList[index]
        return galleryPreview
    }

    override fun load(view: LoadImageView, gid: Long, index: Int) {
        view.setClip(
            mOffsetXList[index],
            mOffsetYList[index],
            mClipWidthList[index],
            mClipHeightList[index],
        )
        view.load(mImageKeyList[index], mImageUrlList[index])
    }
}
