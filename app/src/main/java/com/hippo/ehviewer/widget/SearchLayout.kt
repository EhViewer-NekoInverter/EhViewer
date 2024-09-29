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

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.os.Parcelable
import android.util.AttributeSet
import android.util.SparseArray
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CompoundButton
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.Switch
import android.widget.TextView
import androidx.annotation.IntDef
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayout.OnTabSelectedListener
import com.hippo.easyrecyclerview.EasyRecyclerView
import com.hippo.easyrecyclerview.MarginItemDecoration
import com.hippo.easyrecyclerview.SimpleHolder
import com.hippo.ehviewer.GetText.getString
import com.hippo.ehviewer.R
import com.hippo.ehviewer.client.data.ListUrlBuilder
import com.hippo.ehviewer.client.exception.EhException
import com.hippo.util.getParcelableCompat
import com.hippo.widget.RadioGridGroup
import com.hippo.yorozuya.ViewUtils

@SuppressLint("InflateParams")
class SearchLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0,
) : EasyRecyclerView(
    context,
    attrs,
    defStyle,
),
    CompoundButton.OnCheckedChangeListener,
    View.OnClickListener,
    ImageSearchLayout.Helper,
    OnTabSelectedListener {
    private var mInflater: LayoutInflater? = null

    @SearchMode
    private var mSearchMode = SEARCH_MODE_NORMAL
    private var mEnableAdvance = false
    private var mNormalView: View? = null
    private var mCategoryTable: CategoryTable? = null
    private var mNormalSearchMode: RadioGridGroup? = null
    private var mNormalSearchModeHelp: ImageView? = null

    @SuppressLint("UseSwitchCompatOrMaterialCode")
    private var mEnableAdvanceSwitch: Switch? = null
    private var mAdvanceView: View? = null
    private var mTableAdvanceSearch: AdvanceSearchTable? = null
    private var mImageView: ImageSearchLayout? = null
    private var mActionView: View? = null
    private var mAction: TabLayout? = null
    private var mLayoutManager: LinearLayoutManager? = null
    private var mAdapter: SearchAdapter? = null
    private var mHelper: Helper? = null

    init {
        val resources = context.resources
        mInflater = LayoutInflater.from(context)
        mLayoutManager = SearchLayoutManager(context)
        mAdapter = SearchAdapter()
        mAdapter!!.setHasStableIds(true)
        setLayoutManager(mLayoutManager)
        setAdapter(mAdapter)
        setHasFixedSize(true)
        setClipToPadding(false)
        (itemAnimator as DefaultItemAnimator?)!!.supportsChangeAnimations = false
        val interval = resources.getDimensionPixelOffset(R.dimen.search_layout_interval)
        val paddingH = resources.getDimensionPixelOffset(R.dimen.search_layout_margin_h)
        val paddingV = resources.getDimensionPixelOffset(R.dimen.search_layout_margin_v)
        val decoration = MarginItemDecoration(
            interval,
            paddingH,
            paddingV,
            paddingH,
            paddingV,
        )
        addItemDecoration(decoration)
        decoration.applyPaddings(this)

        // Create normal view
        val normalView = mInflater!!.inflate(R.layout.search_normal, null)
        mNormalView = normalView
        mCategoryTable = normalView.findViewById(R.id.search_category_table)
        mNormalSearchMode = normalView.findViewById(R.id.normal_search_mode)
        mNormalSearchModeHelp = normalView.findViewById(R.id.normal_search_mode_help)
        mEnableAdvanceSwitch = normalView.findViewById(R.id.search_enable_advance)
        mNormalSearchModeHelp!!.setOnClickListener(this)
        mEnableAdvanceSwitch!!.setOnCheckedChangeListener(this)
        mEnableAdvanceSwitch!!.setSwitchPadding(resources.getDimensionPixelSize(R.dimen.switch_padding))

        // Create advance view
        mAdvanceView = mInflater!!.inflate(R.layout.search_advance, null)
        mTableAdvanceSearch = mAdvanceView!!.findViewById(R.id.search_advance_search_table)

        // Create image view
        mImageView = mInflater!!.inflate(R.layout.search_image, null) as ImageSearchLayout
        mImageView!!.setHelper(this)

        // Create action view
        mActionView = mInflater!!.inflate(R.layout.search_action, null)
        mActionView!!.setLayoutParams(
            LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.WRAP_CONTENT,
            ),
        )
        mAction = mActionView!!.findViewById(R.id.action)
        mAction!!.addOnTabSelectedListener(this)
    }

    fun setHelper(helper: Helper?) {
        mHelper = helper
    }

    fun scrollSearchContainerToTop() {
        mLayoutManager!!.scrollToPositionWithOffset(0, 0)
    }

    fun setImageUri(imageUri: Uri?) {
        mImageView!!.setImageUri(imageUri)
    }

    fun setNormalSearchMode(id: Int) {
        mNormalSearchMode!!.check(id)
    }

    override fun onSelectImage() {
        if (mHelper != null) {
            mHelper!!.onSelectImage()
        }
    }

    override fun dispatchSaveInstanceState(container: SparseArray<Parcelable>) {
        super.dispatchSaveInstanceState(container)
        mNormalView!!.saveHierarchyState(container)
        mAdvanceView!!.saveHierarchyState(container)
        mImageView!!.saveHierarchyState(container)
        mActionView!!.saveHierarchyState(container)
    }

    override fun dispatchRestoreInstanceState(container: SparseArray<Parcelable>) {
        super.dispatchRestoreInstanceState(container)
        mNormalView!!.restoreHierarchyState(container)
        mAdvanceView!!.restoreHierarchyState(container)
        mImageView!!.restoreHierarchyState(container)
        mActionView!!.restoreHierarchyState(container)
    }

    override fun onSaveInstanceState(): Parcelable {
        val state = Bundle()
        state.putParcelable(STATE_KEY_SUPER, super.onSaveInstanceState())
        state.putInt(STATE_KEY_SEARCH_MODE, mSearchMode)
        state.putBoolean(STATE_KEY_ENABLE_ADVANCE, mEnableAdvance)
        return state
    }

    override fun onRestoreInstanceState(state: Parcelable) {
        if (state is Bundle) {
            super.onRestoreInstanceState(state.getParcelableCompat(STATE_KEY_SUPER)!!)
            mSearchMode = state.getInt(STATE_KEY_SEARCH_MODE)
            mEnableAdvance = state.getBoolean(STATE_KEY_ENABLE_ADVANCE)
        }
    }

    override fun onCheckedChanged(buttonView: CompoundButton, isChecked: Boolean) {
        if (buttonView === mEnableAdvanceSwitch) {
            post {
                mEnableAdvance = isChecked
                if (mSearchMode == SEARCH_MODE_NORMAL) {
                    if (mEnableAdvance) {
                        mAdapter!!.notifyItemInserted(1)
                    } else {
                        mAdapter!!.notifyItemRemoved(1)
                    }
                    if (mHelper != null) {
                        mHelper!!.onChangeSearchMode()
                    }
                }
            }
        }
    }

    fun formatListUrlBuilder(urlBuilder: ListUrlBuilder, query: String?) {
        urlBuilder.reset()
        when (mSearchMode) {
            SEARCH_MODE_NORMAL -> {
                val nsMode = mNormalSearchMode!!.checkedRadioButtonId
                when (nsMode) {
                    R.id.search_subscription_search -> {
                        urlBuilder.mode = ListUrlBuilder.MODE_SUBSCRIPTION
                    }
                    R.id.search_specify_uploader -> {
                        urlBuilder.mode = ListUrlBuilder.MODE_UPLOADER
                    }
                    R.id.search_specify_tag -> {
                        urlBuilder.mode = ListUrlBuilder.MODE_TAG
                    }
                    else -> {
                        urlBuilder.mode = ListUrlBuilder.MODE_NORMAL
                    }
                }
                urlBuilder.keyword = query
                urlBuilder.category = mCategoryTable!!.category
                if (mEnableAdvance) {
                    urlBuilder.advanceSearch = mTableAdvanceSearch!!.advanceSearch
                    urlBuilder.minRating = mTableAdvanceSearch!!.minRating
                    val pageFrom = mTableAdvanceSearch!!.pageFrom
                    val pageTo = mTableAdvanceSearch!!.pageTo
                    if (pageFrom != -1 && pageFrom > 1000) {
                        throw EhException(getString(R.string.search_sp_err0))
                    } else if (pageTo != -1 && pageTo < 10) {
                        throw EhException(getString(R.string.search_sp_err1))
                    } else if (pageFrom != -1 && pageTo != -1 && pageTo - pageFrom < 20) {
                        throw EhException(getString(R.string.search_sp_err2))
                    } else if (pageFrom != -1 && pageTo != -1 && pageFrom.toFloat() / pageTo > 0.8) {
                        throw EhException(getString(R.string.search_sp_err3))
                    }
                    urlBuilder.pageFrom = pageFrom
                    urlBuilder.pageTo = pageTo
                }
            }

            SEARCH_MODE_IMAGE -> {
                urlBuilder.mode = ListUrlBuilder.MODE_IMAGE_SEARCH
                mImageView!!.formatListUrlBuilder(urlBuilder)
            }
        }
    }

    override fun onClick(v: View) {
        if (mNormalSearchModeHelp === v) {
            AlertDialog.Builder(context)
                .setMessage(R.string.search_tip)
                .show()
        }
    }

    override fun onTabSelected(tab: TabLayout.Tab) {
        post { setSearchMode(tab.position) }
    }

    fun setSearchMode(@SearchMode mode: Int) {
        val oldItemCount = mAdapter!!.getItemCount()
        mSearchMode = mode
        val newItemCount = mAdapter!!.getItemCount()
        mAdapter!!.notifyItemRangeRemoved(0, oldItemCount - 1)
        mAdapter!!.notifyItemRangeInserted(0, newItemCount - 1)
        if (mHelper != null) {
            mHelper!!.onChangeSearchMode()
        }
    }

    override fun onTabUnselected(tab: TabLayout.Tab) {}
    override fun onTabReselected(tab: TabLayout.Tab) {}

    @IntDef(SEARCH_MODE_NORMAL, SEARCH_MODE_IMAGE)
    @Retention(AnnotationRetention.SOURCE)
    private annotation class SearchMode
    interface Helper {
        fun onChangeSearchMode()
        fun onSelectImage()
    }

    internal class SearchLayoutManager(context: Context?) : LinearLayoutManager(context) {
        override fun onLayoutChildren(recycler: Recycler, state: State) {
            try {
                super.onLayoutChildren(recycler, state)
            } catch (e: IndexOutOfBoundsException) {
                e.printStackTrace()
            }
        }
    }

    private inner class SearchAdapter : Adapter<SimpleHolder>() {
        override fun getItemCount(): Int {
            var count = SEARCH_ITEM_COUNT_ARRAY[mSearchMode]
            if (mSearchMode == SEARCH_MODE_NORMAL && !mEnableAdvance) {
                count--
            }
            return count
        }

        override fun getItemViewType(position: Int): Int {
            var type = SEARCH_ITEM_TYPE[mSearchMode][position]
            if (mSearchMode == SEARCH_MODE_NORMAL && position == 1 && !mEnableAdvance) {
                type = ITEM_TYPE_ACTION
            }
            return type
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SimpleHolder {
            val view: View?
            if (viewType == ITEM_TYPE_ACTION) {
                ViewUtils.removeFromParent(mActionView)
                view = mActionView
            } else {
                view = mInflater!!.inflate(R.layout.search_category, parent, false)
                val title = view.findViewById<TextView>(R.id.category_title)
                val content = view.findViewById<FrameLayout>(R.id.category_content)
                when (viewType) {
                    ITEM_TYPE_NORMAL -> {
                        title.setText(R.string.search_normal)
                        ViewUtils.removeFromParent(mNormalView)
                        content.addView(mNormalView)
                    }

                    ITEM_TYPE_NORMAL_ADVANCE -> {
                        title.setText(R.string.search_advance)
                        ViewUtils.removeFromParent(mAdvanceView)
                        content.addView(mAdvanceView)
                    }

                    ITEM_TYPE_IMAGE -> {
                        title.setText(R.string.search_image)
                        ViewUtils.removeFromParent(mImageView)
                        content.addView(mImageView)
                    }
                }
            }
            return SimpleHolder(view!!)
        }

        override fun onBindViewHolder(holder: SimpleHolder, position: Int) {
            if (holder.itemViewType == ITEM_TYPE_ACTION) {
                mAction!!.selectTab(mAction!!.getTabAt(mSearchMode))
            }
        }

        override fun getItemId(position: Int): Long {
            var type = SEARCH_ITEM_TYPE[mSearchMode][position]
            if (mSearchMode == SEARCH_MODE_NORMAL && position == 1 && !mEnableAdvance) {
                type = ITEM_TYPE_ACTION
            }
            return type.toLong()
        }
    }

    companion object {
        const val SEARCH_MODE_NORMAL = 0
        const val SEARCH_MODE_IMAGE = 1
        private const val STATE_KEY_SUPER = "super"
        private const val STATE_KEY_SEARCH_MODE = "search_mode"
        private const val STATE_KEY_ENABLE_ADVANCE = "enable_advance"
        private const val ITEM_TYPE_NORMAL = 0
        private const val ITEM_TYPE_NORMAL_ADVANCE = 1
        private const val ITEM_TYPE_IMAGE = 2
        private const val ITEM_TYPE_ACTION = 3
        private val SEARCH_ITEM_COUNT_ARRAY = intArrayOf(
            3,
            2,
        )
        private val SEARCH_ITEM_TYPE = arrayOf(
            intArrayOf(ITEM_TYPE_NORMAL, ITEM_TYPE_NORMAL_ADVANCE, ITEM_TYPE_ACTION),
            intArrayOf(
                ITEM_TYPE_IMAGE,
                ITEM_TYPE_ACTION,
            ),
        )
    }
}
