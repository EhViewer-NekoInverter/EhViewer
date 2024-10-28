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
package com.hippo.ehviewer.widget

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Parcel
import android.os.Parcelable
import android.util.AttributeSet
import android.view.AbsSavedState
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.core.content.ContextCompat
import com.hippo.ehviewer.R
import com.hippo.ehviewer.client.data.ListUrlBuilder
import com.hippo.ehviewer.client.exception.EhException
import com.hippo.unifile.UniFile
import com.hippo.unifile.openInputStream
import com.hippo.unifile.sha1
import com.hippo.yorozuya.ViewUtils
import java.io.FileInputStream

class ImageSearchLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : LinearLayout(
    context,
    attrs,
    defStyleAttr,
),
    View.OnClickListener {
    private var mPreview: ImageView? = null
    private var mSelectImage: View? = null
    private var mHelper: Helper? = null
    private var mImageUri: Uri? = null

    init {
        orientation = VERTICAL
        setDividerDrawable(ContextCompat.getDrawable(context, R.drawable.spacer_keyline))
        setShowDividers(SHOW_DIVIDER_MIDDLE)
        setClipChildren(false)
        clipToPadding = false
        LayoutInflater.from(context).inflate(R.layout.widget_image_search, this)
        mPreview = ViewUtils.`$$`(this, R.id.preview) as ImageView
        mSelectImage = ViewUtils.`$$`(this, R.id.select_image)
        mSelectImage!!.setOnClickListener(this)
    }

    fun setHelper(helper: Helper?) {
        mHelper = helper
    }

    override fun onClick(v: View) {
        if (v === mSelectImage) {
            if (null != mHelper) {
                mHelper!!.onSelectImage()
            }
        }
    }

    fun setImageUri(imageUri: Uri?) {
        if (null == imageUri) {
            return
        }
        val context = context
        UniFile.fromUri(context, imageUri)?.openInputStream().use {
            val bitmap = BitmapFactory.decodeStream(it) ?: return
            mImageUri = imageUri
            mPreview!!.setImageBitmap(bitmap)
            mPreview!!.setVisibility(VISIBLE)
        }
    }

    private fun setImagePath(imagePath: String?) {
        if (null == imagePath) {
            return
        }
        FileInputStream(imagePath).use {
            val bitmap = BitmapFactory.decodeStream(it) ?: return
            mImageUri = Uri.parse(imagePath)
            mPreview!!.setImageBitmap(bitmap)
            mPreview!!.setVisibility(VISIBLE)
        }
    }

    fun formatListUrlBuilder(builder: ListUrlBuilder) {
        if (null == mImageUri) {
            throw EhException(context.getString(R.string.select_image_first))
        }
        UniFile.fromUri(context, mImageUri!!)?.sha1()?.let {
            builder.hash = it
        }
    }

    override fun onSaveInstanceState(): Parcelable {
        val superState = super.onSaveInstanceState()
        val ss = SavedState(superState)
        ss.imagePath = mImageUri.toString()
        return ss
    }

    override fun onRestoreInstanceState(state: Parcelable) {
        val ss = state as SavedState
        super.onRestoreInstanceState(ss.superState)
        setImagePath(ss.imagePath)
    }

    interface Helper {
        fun onSelectImage()
    }

    private class SavedState : AbsSavedState {
        var imagePath: String? = null

        /**
         * Constructor called from [ImageSearchLayout.onSaveInstanceState]
         */
        constructor(superState: Parcelable?) : super(superState)

        /**
         * Constructor called from [.CREATOR]
         */
        private constructor(source: Parcel) : super(source) {
            imagePath = source.readString()
        }

        override fun writeToParcel(out: Parcel, flags: Int) {
            super.writeToParcel(out, flags)
            out.writeString(imagePath)
        }

        override fun describeContents(): Int = 0

        companion object CREATOR : Parcelable.Creator<SavedState> {
            override fun createFromParcel(`in`: Parcel): SavedState = SavedState(`in`)

            override fun newArray(size: Int): Array<SavedState?> = arrayOfNulls(size)
        }
    }
}
