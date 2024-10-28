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
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.hippo.easyrecyclerview.EasyRecyclerView
import com.hippo.easyrecyclerview.LinearDividerItemDecoration
import com.hippo.ehviewer.R
import com.hippo.ehviewer.Settings
import com.hippo.ehviewer.UrlOpener
import com.hippo.ehviewer.client.EhUrl
import com.hippo.ehviewer.client.EhUtils
import com.hippo.ehviewer.client.data.GalleryDetail
import com.hippo.ehviewer.client.thumbUrl
import com.hippo.util.addTextToClipboard
import com.hippo.util.getParcelableCompat
import com.hippo.yorozuya.LayoutUtils
import com.hippo.yorozuya.ViewUtils
import rikka.core.res.resolveColor

class GalleryInfoScene : ToolbarScene() {
    private var mKeys: ArrayList<String> = arrayListOf()
    private var mValues: ArrayList<String?> = arrayListOf()
    private var mRecyclerView: RecyclerView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) {
            onInit()
        } else {
            onRestore(savedInstanceState)
        }
    }

    private fun handlerArgs(args: Bundle?) {
        args?.getParcelableCompat<GalleryDetail>(KEY_GALLERY_DETAIL)?.let {
            mKeys.add(getString(R.string.header_key))
            mValues.add(getString(R.string.header_value))
            mKeys.add(getString(R.string.key_gid))
            mValues.add(it.gid.toString())
            mKeys.add(getString(R.string.key_token))
            mValues.add(it.token)
            mKeys.add(getString(R.string.key_url))
            mValues.add(EhUrl.getGalleryDetailUrl(it.gid, it.token))
            mKeys.add(getString(R.string.key_title))
            mValues.add(it.title)
            mKeys.add(getString(R.string.key_title_jpn))
            mValues.add(it.titleJpn)
            mKeys.add(getString(R.string.key_thumb))
            mValues.add(it.thumbUrl!!)
            mKeys.add(getString(R.string.key_category))
            mValues.add(EhUtils.getCategory(it.category))
            mKeys.add(getString(R.string.key_uploader))
            mValues.add(it.uploader)
            mKeys.add(getString(R.string.key_posted))
            mValues.add(it.posted)
            mKeys.add(getString(R.string.key_parent))
            mValues.add(it.parent)
            mKeys.add(getString(R.string.key_visible))
            mValues.add(it.visible)
            mKeys.add(getString(R.string.key_language))
            mValues.add(it.language)
            mKeys.add(getString(R.string.key_pages))
            mValues.add(it.pages.toString())
            mKeys.add(getString(R.string.key_size))
            mValues.add(it.size)
            mKeys.add(getString(R.string.key_favorite_count))
            mValues.add(it.favoriteCount.toString())
            mKeys.add(getString(R.string.key_favorited))
            mValues.add(java.lang.Boolean.toString(it.isFavorited))
            mKeys.add(getString(R.string.key_favorite_name))
            mValues.add(it.favoriteName)
            mKeys.add(getString(R.string.key_rating_count))
            mValues.add(it.ratingCount.toString())
            mKeys.add(getString(R.string.key_rating))
            mValues.add(it.rating.toString())
            mKeys.add(getString(R.string.key_torrents))
            mValues.add(it.torrentCount.toString())
            mKeys.add(getString(R.string.key_torrent_url))
            mValues.add(it.torrentUrl)
        }
    }

    private fun onInit() {
        handlerArgs(arguments)
    }

    private fun onRestore(savedInstanceState: Bundle) {
        mKeys = savedInstanceState.getStringArrayList(KEY_KEYS) as ArrayList<String>
        mValues = savedInstanceState.getStringArrayList(KEY_VALUES) as ArrayList<String?>
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putStringArrayList(KEY_KEYS, mKeys)
        outState.putStringArrayList(KEY_VALUES, mValues)
    }

    override fun onCreateViewWithToolbar(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        val view = inflater.inflate(R.layout.scene_gallery_info, container, false)
        val context = requireContext()
        mRecyclerView = ViewUtils.`$$`(view, R.id.recycler_view) as EasyRecyclerView
        val adapter = InfoAdapter()
        mRecyclerView!!.adapter = adapter
        mRecyclerView!!.layoutManager = LinearLayoutManager(context, RecyclerView.VERTICAL, false)
        val decoration = LinearDividerItemDecoration(
            LinearDividerItemDecoration.VERTICAL,
            theme.resolveColor(R.attr.dividerColor),
            LayoutUtils.dp2pix(context, 1f),
        )
        val keylineMargin = context.resources.getDimensionPixelOffset(R.dimen.keyline_margin)
        decoration.setPadding(keylineMargin)
        mRecyclerView!!.addItemDecoration(decoration)
        mRecyclerView!!.clipToPadding = false
        mRecyclerView!!.setHasFixedSize(true)
        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setTitle(R.string.gallery_info)
        setNavigationIcon(R.drawable.v_arrow_left_dark_x24)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        if (null != mRecyclerView) {
            mRecyclerView!!.stopScroll()
            mRecyclerView = null
        }
    }

    fun onItemClick(position: Int): Boolean {
        val context = context
        return if (null != context && 0 != position) {
            if (position == INDEX_PARENT) {
                UrlOpener.openUrl(context, mValues[position], true)
            } else {
                requireActivity().addTextToClipboard(mValues[position], false)
                if (position == INDEX_URL) {
                    // Save it to avoid detect the gallery
                    Settings.putClipboardTextHashCode(mValues[position].hashCode())
                }
            }
            true
        } else {
            false
        }
    }

    override fun onNavigationClick() {
        onBackPressed()
    }

    private class InfoHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val key: TextView = itemView.findViewById(R.id.key)
        val value: TextView = itemView.findViewById(R.id.value)
    }

    private inner class InfoAdapter : RecyclerView.Adapter<InfoHolder>() {
        private val mInflater: LayoutInflater = layoutInflater

        override fun getItemViewType(position: Int): Int = if (position == 0) {
            TYPE_HEADER
        } else {
            TYPE_DATA
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): InfoHolder = InfoHolder(
            mInflater.inflate(
                if (viewType == TYPE_HEADER) R.layout.item_gallery_info_header else R.layout.item_gallery_info_data,
                parent,
                false,
            ),
        )

        override fun onBindViewHolder(holder: InfoHolder, position: Int) {
            holder.key.text = mKeys[position]
            holder.value.text = mValues[position]
            holder.itemView.isEnabled = position != 0
            holder.itemView.setOnClickListener { onItemClick(position) }
        }

        override fun getItemCount(): Int = mKeys.size.coerceAtMost(mValues.size)
    }

    companion object {
        const val KEY_GALLERY_DETAIL = "gallery_detail"
        const val KEY_KEYS = "keys"
        const val KEY_VALUES = "values"
        private const val INDEX_URL = 3
        private const val INDEX_PARENT = 10
        private const val TYPE_HEADER = 0
        private const val TYPE_DATA = 1
    }
}
