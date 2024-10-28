/*
 * Copyright 2019 Hippo Seven
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
package com.hippo.ehviewer.ui.dialog

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.TextView
import androidx.appcompat.content.res.AppCompatResources
import com.hippo.ehviewer.R

class SelectItemWithIconAdapter(
    private val context: Context,
    private val texts: Array<CharSequence>,
    private val icons: IntArray,
) : BaseAdapter() {
    private val inflater: LayoutInflater

    init {
        require(texts.size == icons.size) { "Length conflict" }
        inflater = LayoutInflater.from(context)
    }

    override fun getCount(): Int = texts.size

    override fun getItem(position: Int): Any = texts[position]

    override fun getItemId(position: Int): Long = position.toLong()

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val mConvertView = convertView ?: inflater.inflate(R.layout.dialog_item_select_with_icon, parent, false)
        val view = mConvertView as TextView
        view.text = texts[position]
        val icon = AppCompatResources.getDrawable(context, icons[position])
        icon!!.setBounds(0, 0, icon.intrinsicWidth, icon.intrinsicHeight)
        view.setCompoundDrawables(icon, null, null, null)
        return view
    }
}
