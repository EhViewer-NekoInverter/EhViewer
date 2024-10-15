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

import android.annotation.SuppressLint
import android.app.Activity
import android.content.DialogInterface
import android.content.Intent
import android.os.Bundle
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.PopupMenu
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import androidx.core.view.GravityCompat
import androidx.core.view.ViewCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.SimpleItemAnimator
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.hippo.app.CheckBoxDialogBuilder
import com.hippo.app.EditTextDialogBuilder
import com.hippo.easyrecyclerview.EasyRecyclerView
import com.hippo.easyrecyclerview.EasyRecyclerView.CustomChoiceListener
import com.hippo.easyrecyclerview.FastScroller
import com.hippo.easyrecyclerview.FastScroller.OnDragHandlerListener
import com.hippo.easyrecyclerview.HandlerDrawable
import com.hippo.easyrecyclerview.LinearDividerItemDecoration
import com.hippo.easyrecyclerview.MarginItemDecoration
import com.hippo.ehviewer.EhDB
import com.hippo.ehviewer.R
import com.hippo.ehviewer.Settings
import com.hippo.ehviewer.client.EhCacheKeyFactory
import com.hippo.ehviewer.client.EhUtils
import com.hippo.ehviewer.dao.DownloadInfo
import com.hippo.ehviewer.download.DownloadManager
import com.hippo.ehviewer.download.DownloadManager.DownloadInfoListener
import com.hippo.ehviewer.download.DownloadService
import com.hippo.ehviewer.download.DownloadService.Companion.clear
import com.hippo.ehviewer.spider.DownloadInfoMagics.encodeMagicRequest
import com.hippo.ehviewer.spider.SpiderDen
import com.hippo.ehviewer.ui.GalleryActivity
import com.hippo.ehviewer.widget.SimpleRatingView
import com.hippo.scene.Announcer
import com.hippo.unifile.UniFile
import com.hippo.util.launchIO
import com.hippo.util.launchNonCancellable
import com.hippo.util.launchUI
import com.hippo.view.ViewTransition
import com.hippo.widget.FabLayout
import com.hippo.widget.FabLayout.OnClickFabListener
import com.hippo.widget.LoadImageView
import com.hippo.widget.recyclerview.AutoStaggeredGridLayoutManager
import com.hippo.yorozuya.FileUtils
import com.hippo.yorozuya.LayoutUtils
import com.hippo.yorozuya.ObjectUtils
import com.hippo.yorozuya.ViewUtils
import com.hippo.yorozuya.collect.LongList
import rikka.core.res.resolveColor
import java.util.LinkedList

@SuppressLint("RtlHardcoded")
class DownloadsScene :
    ToolbarScene(),
    DownloadInfoListener,
    OnClickFabListener,
    OnDragHandlerListener {
    private lateinit var mLabels: MutableList<String>
    private var mLabel: String? = null
    private var mList: MutableList<DownloadInfo>? = null
    private var mTip: TextView? = null
    private var mFastScroller: FastScroller? = null
    private var mRecyclerView: EasyRecyclerView? = null
    private var mViewTransition: ViewTransition? = null
    private var mFabLayout: FabLayout? = null
    private var mAdapter: DownloadAdapter? = null
    private var mItemTouchHelper: ItemTouchHelper? = null
    private var mLayoutManager: AutoStaggeredGridLayoutManager? = null
    private var mLabelAdapter: DownloadLabelAdapter? = null
    private var mLabelItemTouchHelper: ItemTouchHelper? = null
    private var mKeyword: String? = null
    private var mSort = Settings.defaultSortingMethod
    private var mType = -1
    private var mInitPosition = -1

    override fun getNavCheckedItem(): Int {
        return R.id.nav_downloads
    }

    private fun initLabels() {
        val listLabel = DownloadManager.labelList
        mLabels = ArrayList(listLabel.size + LABEL_OFFSET)
        // Add "All" and "Default" label names
        mLabels.add(getString(R.string.download_all))
        mLabels.add(getString(R.string.default_download_label_name))
        listLabel.forEach {
            mLabels.add(it.label!!)
        }
    }

    private fun handleArguments(args: Bundle?): Boolean {
        if (null == args) {
            return false
        }
        if (ACTION_CLEAR_DOWNLOAD_SERVICE == args.getString(KEY_ACTION)) {
            clear()
        }
        val gid = args.getLong(KEY_GID, -1L)
        if (-1L != gid) {
            DownloadManager.getDownloadInfo(gid)?.let {
                mLabel = it.label
                updateForLabel()
                updateView()

                // Get position
                if (null != mList) {
                    val position = mList!!.indexOf(it)
                    if (position >= 0 && null != mRecyclerView) {
                        mRecyclerView!!.scrollToPosition(position)
                    } else {
                        mInitPosition = position
                    }
                }
                return true
            }
        }
        return false
    }

    override fun onNewArguments(args: Bundle) {
        handleArguments(args)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        DownloadManager.addDownloadInfoListener(this)
        if (savedInstanceState == null) {
            onInit()
        } else {
            onRestore(savedInstanceState)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        mList = null
        DownloadManager.removeDownloadInfoListener(this)
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun updateForLabel() {
        var list: MutableList<DownloadInfo>?
        if (mLabel == null) {
            list = DownloadManager.allDownloadInfoList
        } else if (mLabel == getString(R.string.default_download_label_name)) {
            list = DownloadManager.defaultDownloadInfoList
        } else {
            list = DownloadManager.getLabelDownloadInfoList(mLabel)
            if (list == null) {
                mLabel = null
                list = DownloadManager.allDownloadInfoList
            }
        }

        if (mType != -1) {
            mList = ArrayList()
            list.forEach {
                if (mKeyword != null && EhUtils.getSuitableTitle(it).contains(mKeyword!!, true) || it.state == mType) {
                    mList!!.add(it)
                }
            }
        } else {
            mList = list
        }

        if (mSort == 10) {
            mList = ArrayList(mList!!.shuffled())
        } else {
            mList!!.sortWith { o1, o2 ->
                val title1 = EhUtils.getSuitableTitle(o1)
                val title2 = EhUtils.getSuitableTitle(o2)
                when (mSort) {
                    0 -> o2.time.compareTo(o1.time)
                    1 -> o1.time.compareTo(o2.time)
                    2 -> title1.compareTo(title2, true)
                    3 -> title2.compareTo(title1, true)
                    4 -> getAuthor(title1).compareTo(getAuthor(title2), true)
                    5 -> getAuthor(title2).compareTo(getAuthor(title1), true)
                    6 -> getName(title1).compareTo(getName(title2), true)
                    7 -> getName(title2).compareTo(getName(title1), true)
                    8 -> o1.category.compareTo(o2.category)
                    9 -> o2.category.compareTo(o1.category)
                    else -> 0
                }
            }
        }

        mAdapter?.notifyDataSetChanged()

        updateTitle()
        Settings.putRecentDownloadLabel(mLabel)
    }

    private fun updateTitle() {
        setTitle(
            getString(
                R.string.scene_download_title,
                if (mLabel != null) mLabel else getString(R.string.download_all),
            ),
        )
    }

    private fun onInit() {
        if (!handleArguments(arguments)) {
            mLabel = Settings.recentDownloadLabel
            updateForLabel()
        }
    }

    private fun onRestore(savedInstanceState: Bundle) {
        mLabel = savedInstanceState.getString(KEY_LABEL)
        updateForLabel()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(KEY_LABEL, mLabel)
    }

    override fun onCreateViewWithToolbar(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        val view = inflater.inflate(R.layout.scene_download, container, false)
        val content = ViewUtils.`$$`(view, R.id.content)
        mRecyclerView = ViewUtils.`$$`(content, R.id.recycler_view) as EasyRecyclerView
        mFastScroller = ViewUtils.`$$`(content, R.id.fast_scroller) as FastScroller
        mFabLayout = ViewUtils.`$$`(view, R.id.fab_layout) as FabLayout
        mTip = ViewUtils.`$$`(view, R.id.tip) as TextView
        mViewTransition = ViewTransition(content, mTip)
        val context = context
        val resources = context!!.resources
        val drawable = ContextCompat.getDrawable(requireContext(), R.drawable.big_download)
        drawable!!.setBounds(0, 0, drawable.intrinsicWidth, drawable.intrinsicHeight)
        mTip!!.setCompoundDrawables(null, drawable, null, null)
        mAdapter = DownloadAdapter()
        mAdapter!!.setHasStableIds(true)
        mRecyclerView!!.adapter = mAdapter
        mLayoutManager = AutoStaggeredGridLayoutManager(0, StaggeredGridLayoutManager.VERTICAL)
        mLayoutManager!!.setColumnSize(Settings.detailSize)
        mLayoutManager!!.setStrategy(AutoStaggeredGridLayoutManager.STRATEGY_MIN_SIZE)
        mRecyclerView!!.layoutManager = mLayoutManager
        mRecyclerView!!.clipToPadding = false
        mRecyclerView!!.clipChildren = false
        mRecyclerView!!.setChoiceMode(EasyRecyclerView.CHOICE_MODE_MULTIPLE_CUSTOM)
        mRecyclerView!!.setCustomCheckedListener(DownloadChoiceListener())
        // Cancel change animation
        val itemAnimator = mRecyclerView!!.itemAnimator
        if (itemAnimator is SimpleItemAnimator) {
            itemAnimator.supportsChangeAnimations = false
        }
        val interval = resources.getDimensionPixelOffset(R.dimen.gallery_list_interval)
        val paddingH = resources.getDimensionPixelOffset(R.dimen.gallery_list_margin_h)
        val paddingV = resources.getDimensionPixelOffset(R.dimen.gallery_list_margin_v)
        val decoration = MarginItemDecoration(interval, paddingH, paddingV, paddingH, paddingV)
        mRecyclerView!!.addItemDecoration(decoration)
        if (mInitPosition >= 0) {
            mRecyclerView!!.scrollToPosition(mInitPosition)
            mInitPosition = -1
        }
        mItemTouchHelper = ItemTouchHelper(DownloadItemTouchHelperCallback())
        mItemTouchHelper!!.attachToRecyclerView(mRecyclerView)
        mFastScroller!!.attachToRecyclerView(mRecyclerView)
        val handlerDrawable = HandlerDrawable()
        handlerDrawable.setColor(theme.resolveColor(R.attr.widgetColorThemeAccent))
        mFastScroller!!.setHandlerDrawable(handlerDrawable)
        mFastScroller!!.setOnDragHandlerListener(this)
        mFabLayout!!.setExpanded(expanded = false, animation = false)
        mFabLayout!!.setHidePrimaryFab(true)
        mFabLayout!!.setAutoCancel(false)
        mFabLayout!!.setOnClickFabListener(this)
        addAboveSnackView(mFabLayout!!)
        updateView()
        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        updateTitle()
        setNavigationIcon(R.drawable.ic_baseline_menu_24)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        if (null != mRecyclerView) {
            mRecyclerView!!.stopScroll()
            mRecyclerView = null
        }
        if (null != mFabLayout) {
            removeAboveSnackView(mFabLayout!!)
            mFabLayout = null
        }
        mRecyclerView = null
        mViewTransition = null
        mAdapter = null
        mLayoutManager = null
    }

    override fun onNavigationClick() {
        toggleDrawer(GravityCompat.START)
    }

    override fun getMenuResId(): Int {
        return R.menu.scene_download
    }

    override fun onMenuItemClick(item: MenuItem): Boolean {
        // Skip when in choice mode
        val activity: Activity? = mainActivity
        if (null == activity || null == mRecyclerView || mRecyclerView!!.isInCustomChoice) {
            return false
        }
        when (item.itemId) {
            R.id.action_filter -> {
                AlertDialog.Builder(requireActivity())
                    .setSingleChoiceItems(
                        R.array.download_state,
                        mType + 1,
                    ) { dialog: DialogInterface, which: Int ->
                        dialog.dismiss()
                        if (which == 6) {
                            showFilterTitleDialog()
                        } else {
                            mType = which - 1
                            mKeyword = null
                            updateForLabel()
                            updateView()
                        }
                    }
                    .show()
                return true
            }

            R.id.action_start_all -> {
                val intent = Intent(activity, DownloadService::class.java)
                intent.action = DownloadService.ACTION_START_ALL
                ContextCompat.startForegroundService(activity, intent)
                return true
            }

            R.id.action_stop_all -> {
                // DownloadManager Actions
                DownloadManager.stopAllDownload()
                return true
            }

            R.id.action_open_download_labels -> {
                openDrawer(GravityCompat.END)
                return true
            }

            R.id.action_reset_reading_progress -> {
                AlertDialog.Builder(requireContext())
                    .setMessage(R.string.reset_reading_progress_message)
                    .setNegativeButton(android.R.string.cancel, null)
                    .setPositiveButton(android.R.string.ok) { _: DialogInterface?, _: Int ->
                        lifecycleScope.launchNonCancellable {
                            // DownloadManager Actions
                            DownloadManager.resetAllReadingProgress()
                        }
                    }.show()
                return true
            }

            R.id.action_start_all_reversed -> {
                val list = mList ?: return true
                val gidList = LongList()
                for (i in list.size - 1 downTo 0) {
                    val info = list[i]
                    if (info.state != DownloadInfo.STATE_FINISH) {
                        gidList.add(info.gid)
                    }
                }
                val intent = Intent(activity, DownloadService::class.java)
                intent.action = DownloadService.ACTION_START_RANGE
                intent.putExtra(DownloadService.KEY_GID_LIST, gidList)
                ContextCompat.startForegroundService(activity, intent)
                return true
            }

            R.id.action_sort -> {
                AlertDialog.Builder(requireActivity())
                    .setSingleChoiceItems(
                        R.array.download_sort,
                        mSort,
                    ) { dialog: DialogInterface, which: Int ->
                        mSort = which
                        Settings.putDefaultSortingMethod(which)
                        dialog.dismiss()
                        updateForLabel()
                        updateView()
                    }
                    .show()
                return true
            }

            else -> return false
        }
    }

    private fun showFilterTitleDialog() {
        val builder = EditTextDialogBuilder(
            requireActivity(),
            null,
            getString(R.string.download_filter_title),
        )
        builder.setTitle(R.string.search)
        builder.setPositiveButton(android.R.string.ok, null)
        val dialog = builder.show()
        val button: View? = dialog.getButton(DialogInterface.BUTTON_POSITIVE)
        button?.setOnClickListener {
            val text = builder.text.trim { it <= ' ' }
            if (TextUtils.isEmpty(text)) {
                builder.setError(getString(R.string.text_is_empty))
            } else {
                builder.setError(null)
                dialog.dismiss()
                mType = 5
                mKeyword = text.lowercase()
                updateForLabel()
                updateView()
            }
        }
    }

    fun updateView() {
        if (mViewTransition != null) {
            if (mList.isNullOrEmpty()) {
                mViewTransition!!.showView(1)
            } else {
                mViewTransition!!.showView(0)
            }
        }
    }

    override fun onCreateDrawerView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        val view = inflater.inflate(R.layout.drawer_list_rv, container, false)
        val toolbar = view.findViewById<Toolbar>(R.id.toolbar)
        toolbar.setTitle(R.string.download_labels)
        toolbar.inflateMenu(R.menu.drawer_download)
        toolbar.setOnMenuItemClickListener { item: MenuItem ->
            val id = item.itemId
            if (id == R.id.action_add) {
                val builder =
                    EditTextDialogBuilder(requireContext(), null, getString(R.string.download_labels))
                builder.setTitle(R.string.new_label_title)
                builder.setPositiveButton(android.R.string.ok, null)
                val dialog = builder.show()
                NewLabelDialogHelper(builder, dialog)
                return@setOnMenuItemClickListener true
            } else if (id == R.id.action_default_download_label) {
                val list = DownloadManager.labelList
                val items = arrayOfNulls<String>(list.size + 2)
                items[0] = getString(R.string.let_me_select)
                items[1] = getString(R.string.default_download_label_name)
                var i = 0
                val n = list.size
                while (i < n) {
                    items[i + 2] = list[i].label
                    i++
                }
                AlertDialog.Builder(requireContext())
                    .setTitle(R.string.default_download_label)
                    .setItems(items) { _: DialogInterface?, which: Int ->
                        if (which == 0) {
                            Settings.putHasDefaultDownloadLabel(false)
                        } else {
                            Settings.putHasDefaultDownloadLabel(true)
                            val label: String? = if (which == 1) {
                                null
                            } else {
                                items[which]
                            }
                            Settings.putDefaultDownloadLabel(label)
                        }
                    }.show()
                return@setOnMenuItemClickListener true
            }
            false
        }
        initLabels()
        mLabelAdapter = DownloadLabelAdapter(inflater)
        val recyclerView = view.findViewById<EasyRecyclerView>(R.id.recycler_view_drawer)
        recyclerView.layoutManager = LinearLayoutManager(context)
        val decoration = LinearDividerItemDecoration(
            LinearDividerItemDecoration.VERTICAL,
            theme.resolveColor(R.attr.dividerColor),
            LayoutUtils.dp2pix(context, 1f),
        )
        decoration.setShowLastDivider(true)
        mLabelAdapter!!.setHasStableIds(true)
        mLabelItemTouchHelper = ItemTouchHelper(DownloadLabelItemTouchHelperCallback())
        mLabelItemTouchHelper!!.attachToRecyclerView(recyclerView)
        recyclerView.adapter = mLabelAdapter
        return view
    }

    override fun onBackPressed() {
        if (mRecyclerView != null && mRecyclerView!!.isInCustomChoice) {
            mRecyclerView!!.outOfCustomChoiceMode()
        } else {
            super.onBackPressed()
        }
    }

    override fun onStartDragHandler() {
        // Lock right drawer
        setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED, GravityCompat.END)
    }

    override fun onEndDragHandler() {
        // Restore right drawer
        if (null != mRecyclerView && !mRecyclerView!!.isInCustomChoice) {
            setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED, GravityCompat.END)
        }
    }

    override fun onClickPrimaryFab(view: FabLayout, fab: FloatingActionButton) {
        if (mRecyclerView != null && mRecyclerView!!.isInCustomChoice) {
            mRecyclerView!!.outOfCustomChoiceMode()
        }
    }

    override fun onClickSecondaryFab(view: FabLayout, fab: FloatingActionButton, position: Int) {
        val context = context
        val activity: Activity? = mainActivity
        val recyclerView = mRecyclerView
        if (null == context || null == activity || null == recyclerView) {
            return
        }
        if (0 == position) {
            recyclerView.checkAll()
        } else {
            val list = mList ?: return
            var gidList: LongList? = null
            var downloadInfoList: MutableList<DownloadInfo>? = null
            val collectGid = position == 2 || position == 3 || position == 4 // Start, Stop, Delete
            val collectDownloadInfo = position == 1 || position == 4 || position == 5 // Pin, Delete, Move
            if (collectGid) {
                gidList = LongList()
            }
            if (collectDownloadInfo) {
                downloadInfoList = LinkedList()
            }
            recyclerView.checkedItemPositions?.let {
                for (i in 0 until it.size()) {
                    if (it.valueAt(i)) {
                        val info = list[it.keyAt(i)]
                        if (collectDownloadInfo) {
                            downloadInfoList!!.add(info)
                        }
                        if (collectGid) {
                            gidList!!.add(info.gid)
                        }
                    }
                }
            }
            when (position) {
                // Pin to top
                1 -> {
                    val pinList = downloadInfoList!!.reversed()
                    val nowTimeStamp = System.currentTimeMillis()
                    for (i in pinList.indices) {
                        pinList[i].time = nowTimeStamp + i
                        // DB Actions
                        EhDB.putDownloadInfo(pinList[i])
                    }
                    recyclerView.outOfCustomChoiceMode()
                    updateForLabel()
                }
                // Start
                2 -> {
                    val intent = Intent(activity, DownloadService::class.java)
                    intent.action = DownloadService.ACTION_START_RANGE
                    intent.putExtra(DownloadService.KEY_GID_LIST, gidList)
                    ContextCompat.startForegroundService(activity, intent)
                    // Cancel check mode
                    recyclerView.outOfCustomChoiceMode()
                }
                // Stop
                3 -> {
                    // DownloadManager Actions
                    DownloadManager.stopRangeDownload(gidList!!)
                    // Cancel check mode
                    recyclerView.outOfCustomChoiceMode()
                }
                // Delete
                4 -> {
                    val builder = CheckBoxDialogBuilder(
                        context,
                        getString(R.string.download_remove_dialog_message_2, gidList!!.size),
                        getString(R.string.download_remove_dialog_check_text),
                        Settings.removeImageFiles,
                    )
                    val helper = DeleteRangeDialogHelper(
                        downloadInfoList!!,
                        gidList,
                        builder,
                    )
                    builder.setTitle(R.string.download_remove_dialog_title)
                        .setPositiveButton(android.R.string.ok, helper)
                        .show()
                }
                // Move
                5 -> {
                    val labelRawList = DownloadManager.labelList
                    val labelList: MutableList<String> = ArrayList(labelRawList.size + 1)
                    labelList.add(getString(R.string.default_download_label_name))
                    labelRawList.forEach {
                        labelList.add(it.label!!)
                    }
                    val labels = labelList.toTypedArray()
                    val helper = MoveDialogHelper(labels, downloadInfoList!!)
                    AlertDialog.Builder(context)
                        .setTitle(R.string.download_move_dialog_title)
                        .setItems(labels, helper)
                        .show()
                }
            }
        }
    }

    override fun onAdd(info: DownloadInfo, list: List<DownloadInfo>, position: Int) {
        if (mList !== list) {
            return
        }
        mAdapter?.notifyItemInserted(position)
        updateView()
    }

    override fun onUpdate(info: DownloadInfo, list: List<DownloadInfo>) {
        if (null == mList) {
            return
        }
        val index = mList!!.indexOf(info)
        if (index >= 0) {
            mAdapter?.notifyItemChanged(index, PAYLOAD_STATE)
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    override fun onUpdateAll() {
        mAdapter?.notifyDataSetChanged()
    }

    @SuppressLint("NotifyDataSetChanged")
    override fun onReload() {
        mAdapter?.notifyDataSetChanged()
        updateView()
    }

    override fun onChange() {
        lifecycleScope.launchUI {
            mLabel = null
            updateForLabel()
            updateView()
        }
    }

    override fun onRenameLabel(from: String, to: String) {
        if (!ObjectUtils.equal(mLabel, from)) {
            return
        }
        mLabel = to
        updateForLabel()
        updateView()
    }

    override fun onRemove(info: DownloadInfo, list: List<DownloadInfo>, position: Int) {
        if (mList !== list) {
            return
        }
        mAdapter?.notifyItemRemoved(position)
        updateView()
    }

    override fun onUpdateLabels() {
        // TODO
    }

    private fun bindForState(holder: DownloadHolder, info: DownloadInfo) {
        val context = context ?: return
        when (info.state) {
            DownloadInfo.STATE_NONE -> bindState(
                holder,
                info,
                context.getString(R.string.download_state_none),
            )

            DownloadInfo.STATE_WAIT -> bindState(
                holder,
                info,
                context.getString(R.string.download_state_wait),
            )

            DownloadInfo.STATE_DOWNLOAD -> bindProgress(holder, info)
            DownloadInfo.STATE_FAILED -> {
                val text: String = if (info.legacy <= 0) {
                    context.getString(R.string.download_state_failed)
                } else {
                    context.getString(R.string.download_state_failed_2, info.legacy)
                }
                bindState(holder, info, text)
            }

            DownloadInfo.STATE_FINISH -> bindState(
                holder,
                info,
                context.getString(R.string.download_state_finish),
            )
        }
    }

    private fun bindState(holder: DownloadHolder, info: DownloadInfo, state: String) {
        holder.uploader.visibility = View.VISIBLE
        holder.rating.visibility = View.VISIBLE
        holder.category.visibility = View.VISIBLE
        holder.state.visibility = View.VISIBLE
        holder.progressBar.visibility = View.GONE
        holder.percent.visibility = View.GONE
        holder.speed.visibility = View.GONE
        if (info.state == DownloadInfo.STATE_WAIT || info.state == DownloadInfo.STATE_DOWNLOAD) {
            holder.start.visibility = View.GONE
            holder.stop.visibility = View.VISIBLE
        } else {
            holder.start.visibility = View.VISIBLE
            holder.stop.visibility = View.GONE
        }
        holder.state.text = state
        if (mSort == 0 && mType == -1) {
            holder.move.visibility = View.VISIBLE
        } else {
            holder.move.visibility = View.GONE
        }
    }

    @SuppressLint("SetTextI18n")
    private fun bindProgress(holder: DownloadHolder, info: DownloadInfo) {
        holder.uploader.visibility = View.GONE
        holder.rating.visibility = View.GONE
        holder.category.visibility = View.GONE
        holder.state.visibility = View.GONE
        holder.progressBar.visibility = View.VISIBLE
        holder.percent.visibility = View.VISIBLE
        holder.speed.visibility = View.VISIBLE
        if (info.state == DownloadInfo.STATE_WAIT || info.state == DownloadInfo.STATE_DOWNLOAD) {
            holder.start.visibility = View.GONE
            holder.stop.visibility = View.VISIBLE
        } else {
            holder.start.visibility = View.VISIBLE
            holder.stop.visibility = View.GONE
        }
        if (info.total <= 0 || info.finished < 0) {
            holder.percent.text = null
            holder.progressBar.isIndeterminate = true
        } else {
            holder.percent.text = info.finished.toString() + "/" + info.total
            holder.progressBar.isIndeterminate = false
            holder.progressBar.max = info.total
            holder.progressBar.progress = info.finished
        }
        var speed = info.speed
        if (speed < 0) {
            speed = 0
        }
        holder.speed.text = FileUtils.humanReadableByteCount(speed, false) + "/S"
    }

    @SuppressLint("ClickableViewAccessibility")
    private inner class DownloadLabelHolder(
        itemView: View,
    ) : RecyclerView.ViewHolder(itemView), View.OnTouchListener {
        val label: TextView = ViewUtils.`$$`(itemView, R.id.tv_key) as TextView
        val option: ImageView = ViewUtils.`$$`(itemView, R.id.iv_option) as ImageView

        init {
            option.setOnTouchListener(this)
        }

        override fun onTouch(v: View, event: MotionEvent): Boolean {
            if (mLabelItemTouchHelper != null && event.action == MotionEvent.ACTION_DOWN) {
                mLabelItemTouchHelper!!.startDrag(this)
            }
            return false
        }
    }

    private inner class DownloadLabelAdapter(private val mInflater: LayoutInflater) :
        RecyclerView.Adapter<DownloadLabelHolder>() {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DownloadLabelHolder {
            val holder = DownloadLabelHolder(mInflater.inflate(R.layout.item_drawer_list, parent, false))
            holder.itemView.setOnClickListener {
                val index = holder.bindingAdapterPosition
                val label1: String? = if (index == 0) {
                    null
                } else {
                    mLabels[index]
                }
                if (!ObjectUtils.equal(label1, mLabel)) {
                    mLabel = label1
                    updateForLabel()
                    updateView()
                    closeDrawer(GravityCompat.END)
                }
            }
            holder.itemView.setOnLongClickListener {
                val index = holder.bindingAdapterPosition
                if (index >= LABEL_OFFSET) {
                    val popupMenu = PopupMenu(requireContext(), holder.option)
                    popupMenu.inflate(R.menu.download_label_option)
                    popupMenu.show()
                    popupMenu.setOnMenuItemClickListener(
                        object : PopupMenu.OnMenuItemClickListener {
                            override fun onMenuItemClick(item: MenuItem): Boolean {
                                val label = mLabels[index]
                                when (item.itemId) {
                                    R.id.menu_label_rename -> {
                                        val builder = EditTextDialogBuilder(
                                            requireContext(),
                                            label,
                                            getString(R.string.download_labels),
                                        )
                                        builder.setTitle(R.string.rename_label_title)
                                        builder.setPositiveButton(android.R.string.ok, null)
                                        val dialog = builder.show()
                                        RenameLabelDialogHelper(builder, dialog, label)
                                        return true
                                    }
                                    R.id.menu_label_remove -> {
                                        AlertDialog.Builder(requireContext())
                                            .setTitle(R.string.delete_label_title)
                                            .setMessage(getString(R.string.delete_label_message, label))
                                            .setPositiveButton(R.string.delete) { _: DialogInterface?, _: Int ->
                                                // DownloadManager Actions
                                                DownloadManager.deleteLabel(label)
                                                mLabels.removeAt(index)
                                                notifyItemRemoved(index)
                                            }
                                            .setNegativeButton(android.R.string.cancel, null)
                                            .show()
                                        return true
                                    }
                                }
                                return false
                            }
                        },
                    )
                }
                return@setOnLongClickListener true
            }
            return holder
        }

        @SuppressLint("SetTextI18n")
        override fun onBindViewHolder(holder: DownloadLabelHolder, position: Int) {
            val index = holder.bindingAdapterPosition
            val label = mLabels[index]
            val list = when (position) {
                0 -> {
                    DownloadManager.allDownloadInfoList
                }
                1 -> {
                    DownloadManager.defaultDownloadInfoList
                }
                else -> {
                    DownloadManager.getLabelDownloadInfoList(label)
                }
            }
            if (list != null) {
                holder.label.text = label + " [" + list.size + "]"
            } else {
                holder.label.text = label
            }
            if (position < LABEL_OFFSET) {
                holder.option.visibility = View.GONE
            } else {
                holder.option.visibility = View.VISIBLE
            }
        }

        override fun getItemId(position: Int): Long {
            return (if (position < LABEL_OFFSET) position else mLabels[position].hashCode()).toLong()
        }

        override fun getItemCount(): Int {
            return mLabels.size
        }
    }

    private inner class DeleteRangeDialogHelper(
        private val mDownloadInfoList: List<DownloadInfo>,
        private val mGidList: LongList,
        private val mBuilder: CheckBoxDialogBuilder,
    ) : DialogInterface.OnClickListener {
        override fun onClick(dialog: DialogInterface, which: Int) {
            if (which != DialogInterface.BUTTON_POSITIVE) {
                return
            }

            // Cancel check mode
            if (mRecyclerView != null) {
                mRecyclerView!!.outOfCustomChoiceMode()
            }

            // Delete
            // DownloadManager Actions
            DownloadManager.deleteRangeDownload(mGidList)

            // Delete image files
            val checked = mBuilder.isChecked
            Settings.putRemoveImageFiles(checked)
            if (checked) {
                val files = arrayOfNulls<UniFile>(mDownloadInfoList.size)
                for ((i, info) in mDownloadInfoList.withIndex()) {
                    // Put file
                    files[i] = SpiderDen.getGalleryDownloadDir(info.gid)
                    // DB Actions
                    DownloadManager.removeDownloadDirname(info.gid)
                }
                // Other Actions
                lifecycleScope.launchIO {
                    runCatching {
                        files.forEach { it?.delete() }
                    }
                }
            }
        }
    }

    private inner class MoveDialogHelper(
        private val mLabels: Array<String>,
        private val mDownloadInfoList: List<DownloadInfo>,
    ) : DialogInterface.OnClickListener {
        @SuppressLint("NotifyDataSetChanged")
        override fun onClick(dialog: DialogInterface, which: Int) {
            // Cancel check mode
            context ?: return
            if (null != mRecyclerView) {
                mRecyclerView!!.outOfCustomChoiceMode()
            }
            val label: String? = if (which == 0) {
                null
            } else {
                mLabels[which]
            }
            // DownloadManager Actions
            DownloadManager.changeLabel(mDownloadInfoList, label)
            mLabelAdapter?.notifyDataSetChanged()
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private inner class DownloadHolder(itemView: View) :
        RecyclerView.ViewHolder(itemView),
        View.OnClickListener,
        View.OnTouchListener {
        val thumb: LoadImageView = itemView.findViewById(R.id.thumb)
        val title: TextView = itemView.findViewById(R.id.title)
        val uploader: TextView = itemView.findViewById(R.id.uploader)
        val rating: SimpleRatingView = itemView.findViewById(R.id.rating)
        val category: TextView = itemView.findViewById(R.id.category)
        val start: View = itemView.findViewById(R.id.start)
        val stop: View = itemView.findViewById(R.id.stop)
        val move: View = itemView.findViewById(R.id.move)
        val state: TextView = itemView.findViewById(R.id.state)
        val progressBar: ProgressBar = itemView.findViewById(R.id.progress_bar)
        val percent: TextView = itemView.findViewById(R.id.percent)
        val speed: TextView = itemView.findViewById(R.id.speed)

        init {
            // TODO cancel on click listener when select items
            thumb.setOnClickListener(this)
            start.setOnClickListener(this)
            stop.setOnClickListener(this)
            move.setOnTouchListener(this)
        }

        override fun onClick(v: View) {
            val context = context
            val activity: Activity? = mainActivity
            val recyclerView = mRecyclerView
            if (null == context || null == activity || null == recyclerView || recyclerView.isInCustomChoice) {
                return
            }
            val list = mList ?: return
            val size = list.size
            val index = recyclerView.getChildAdapterPosition(itemView)
            if (index < 0 || index >= size) {
                return
            }
            if (thumb === v) {
                val args = Bundle()
                args.putString(
                    GalleryDetailScene.KEY_ACTION,
                    GalleryDetailScene.ACTION_GALLERY_INFO,
                )
                args.putParcelable(GalleryDetailScene.KEY_GALLERY_INFO, list[index])
                val announcer = Announcer(GalleryDetailScene::class.java).setArgs(args)
                announcer.setTranHelper(EnterGalleryDetailTransaction(thumb))
                startScene(announcer)
            } else if (start === v) {
                val intent = Intent(activity, DownloadService::class.java)
                intent.action = DownloadService.ACTION_START
                intent.putExtra(DownloadService.KEY_GALLERY_INFO, list[index])
                ContextCompat.startForegroundService(activity, intent)
            } else if (stop === v) {
                // DownloadManager Actions
                DownloadManager.stopDownload(list[index].gid)
            }
        }

        override fun onTouch(v: View, event: MotionEvent): Boolean {
            if (mItemTouchHelper != null && event.action == MotionEvent.ACTION_DOWN) {
                mItemTouchHelper!!.startDrag(this)
            }
            return false
        }
    }

    private inner class DownloadAdapter : RecyclerView.Adapter<DownloadHolder>() {
        private val mInflater: LayoutInflater = layoutInflater
        private val mListThumbWidth: Int
        private val mListThumbHeight: Int

        init {
            @SuppressLint("InflateParams")
            val calculator =
                mInflater.inflate(R.layout.item_gallery_list_thumb_height, null)
            ViewUtils.measureView(calculator, 1024, ViewGroup.LayoutParams.WRAP_CONTENT)
            mListThumbHeight = calculator.measuredHeight
            mListThumbWidth = mListThumbHeight * 2 / 3
        }

        override fun getItemId(position: Int): Long {
            return if (mList == null || position < 0 || position >= mList!!.size) {
                0
            } else {
                mList!![position].gid
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DownloadHolder {
            val holder = DownloadHolder(mInflater.inflate(R.layout.item_download, parent, false))
            val lp = holder.thumb.layoutParams
            lp.width = mListThumbWidth
            lp.height = mListThumbHeight
            holder.thumb.layoutParams = lp
            return holder
        }

        override fun onBindViewHolder(holder: DownloadHolder, position: Int) {
            if (mList == null) {
                return
            }
            val info = mList!![holder.bindingAdapterPosition]
            info.thumb?.let {
                holder.thumb.load(
                    EhCacheKeyFactory.getThumbKey(info.gid),
                    encodeMagicRequest(info),
                    hardware = false,
                )
            }
            holder.title.text = EhUtils.getSuitableTitle(info)
            holder.uploader.text = info.uploader
            holder.rating.rating = info.rating
            val category = holder.category
            val newCategoryText = EhUtils.getCategory(info.category)
            if (!newCategoryText.contentEquals(category.text)) {
                category.text = newCategoryText
                category.setBackgroundColor(EhUtils.getCategoryColor(info.category))
            }
            bindForState(holder, info)

            // Update transition name
            ViewCompat.setTransitionName(
                holder.thumb,
                TransitionNameFactory.getThumbTransitionName(info.gid),
            )
            holder.itemView.setOnClickListener {
                if (mainActivity != null && mRecyclerView != null && mList != null) {
                    val index = holder.bindingAdapterPosition
                    if (mRecyclerView!!.isInCustomChoice) {
                        mRecyclerView!!.toggleItemChecked(index)
                    } else {
                        if (index in 0 until mList!!.size) {
                            val intent = Intent(mainActivity!!, GalleryActivity::class.java)
                            intent.action = GalleryActivity.ACTION_EH
                            intent.putExtra(GalleryActivity.KEY_GALLERY_INFO, mList!![index])
                            startActivity(intent)
                        }
                    }
                }
            }
            holder.itemView.setOnLongClickListener {
                if (mRecyclerView != null) {
                    if (!mRecyclerView!!.isInCustomChoice) {
                        mRecyclerView!!.intoCustomChoiceMode()
                    }
                    mRecyclerView!!.toggleItemChecked(holder.bindingAdapterPosition)
                    return@setOnLongClickListener true
                }
                return@setOnLongClickListener false
            }
        }

        override fun onBindViewHolder(
            holder: DownloadHolder,
            position: Int,
            payloads: MutableList<Any>,
        ) {
            if (payloads.any { it == PAYLOAD_STATE }) {
                mList?.let { bindForState(holder, it[position]) }
            } else {
                super.onBindViewHolder(holder, position, payloads)
            }
        }

        override fun getItemCount(): Int {
            return if (mList == null) 0 else mList!!.size
        }
    }

    private inner class DownloadChoiceListener : CustomChoiceListener {
        override fun onIntoCustomChoice(view: EasyRecyclerView) {
            if (mFabLayout != null) {
                mFabLayout!!.isExpanded = true
            }
            // Lock drawer
            setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED, GravityCompat.START)
            setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED, GravityCompat.END)
        }

        override fun onOutOfCustomChoice(view: EasyRecyclerView) {
            if (mFabLayout != null) {
                mFabLayout!!.isExpanded = false
            }
            // Unlock drawer
            setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED, GravityCompat.START)
            setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED, GravityCompat.END)
        }

        override fun onItemCheckedStateChanged(
            view: EasyRecyclerView,
            position: Int,
            id: Long,
            checked: Boolean,
        ) {
            if (view.checkedItemCount == 0) {
                view.outOfCustomChoiceMode()
            }
        }
    }

    private inner class RenameLabelDialogHelper(
        private val mBuilder: EditTextDialogBuilder,
        private val mDialog: AlertDialog,
        private val mOriginalLabel: String?,
    ) : View.OnClickListener {
        init {
            val button: Button = mDialog.getButton(DialogInterface.BUTTON_POSITIVE)
            button.setOnClickListener(this)
        }

        @SuppressLint("NotifyDataSetChanged")
        override fun onClick(v: View) {
            context ?: return
            val text = mBuilder.text
            if (TextUtils.isEmpty(text)) {
                mBuilder.setError(getString(R.string.label_text_is_empty))
            } else if (getString(R.string.download_all) == text || getString(R.string.default_download_label_name) == text) {
                mBuilder.setError(getString(R.string.label_text_is_invalid))
            } else if (DownloadManager.containLabel(text)) {
                mBuilder.setError(getString(R.string.label_text_exist))
            } else {
                mBuilder.setError(null)
                mDialog.dismiss()
                // DownloadManager Actions
                DownloadManager.renameLabel(mOriginalLabel!!, text)
                if (mLabelAdapter != null) {
                    initLabels()
                    mLabelAdapter!!.notifyDataSetChanged()
                }
            }
        }
    }

    private inner class NewLabelDialogHelper(
        private val mBuilder: EditTextDialogBuilder,
        private val mDialog: AlertDialog,
    ) : View.OnClickListener {
        init {
            val button: Button = mDialog.getButton(DialogInterface.BUTTON_POSITIVE)
            button.setOnClickListener(this)
        }

        override fun onClick(v: View) {
            context ?: return
            val text = mBuilder.text
            if (TextUtils.isEmpty(text)) {
                mBuilder.setError(getString(R.string.label_text_is_empty))
            } else if (getString(R.string.download_all) == text || getString(R.string.default_download_label_name) == text) {
                mBuilder.setError(getString(R.string.label_text_is_invalid))
            } else if (DownloadManager.containLabel(text)) {
                mBuilder.setError(getString(R.string.label_text_exist))
            } else {
                mBuilder.setError(null)
                mDialog.dismiss()
                // DownloadManager Actions
                DownloadManager.addLabel(text)
                initLabels()
                mLabelAdapter?.notifyItemInserted(mLabels.size - 1)
            }
        }
    }

    private inner class DownloadLabelItemTouchHelperCallback : ItemTouchHelper.Callback() {
        override fun getMovementFlags(
            recyclerView: RecyclerView,
            viewHolder: RecyclerView.ViewHolder,
        ): Int {
            val position = viewHolder.bindingAdapterPosition
            return if (position < LABEL_OFFSET) {
                makeMovementFlags(0, 0)
            } else {
                makeMovementFlags(
                    ItemTouchHelper.UP or ItemTouchHelper.DOWN,
                    0,
                )
            }
        }

        override fun isLongPressDragEnabled(): Boolean {
            return false
        }

        override fun onMove(
            recyclerView: RecyclerView,
            viewHolder: RecyclerView.ViewHolder,
            target: RecyclerView.ViewHolder,
        ): Boolean {
            val fromPosition = viewHolder.bindingAdapterPosition
            val toPosition = target.bindingAdapterPosition
            if (fromPosition == toPosition || toPosition < LABEL_OFFSET) {
                return false
            }
            // DownloadManager Actions
            DownloadManager.moveLabel(fromPosition - LABEL_OFFSET, toPosition - LABEL_OFFSET)
            val item = mLabels.removeAt(fromPosition)
            mLabels.add(toPosition, item)
            mLabelAdapter?.notifyItemMoved(fromPosition, toPosition)
            return true
        }

        override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {}
    }

    private inner class DownloadItemTouchHelperCallback : ItemTouchHelper.Callback() {
        override fun getMovementFlags(
            recyclerView: RecyclerView,
            viewHolder: RecyclerView.ViewHolder,
        ): Int {
            return makeMovementFlags(
                ItemTouchHelper.UP or ItemTouchHelper.DOWN,
                0,
            )
        }

        override fun isLongPressDragEnabled(): Boolean {
            return false
        }

        override fun onMove(
            recyclerView: RecyclerView,
            viewHolder: RecyclerView.ViewHolder,
            target: RecyclerView.ViewHolder,
        ): Boolean {
            val fromPosition = viewHolder.bindingAdapterPosition
            val toPosition = target.bindingAdapterPosition
            if (fromPosition == toPosition) {
                return false
            }
            // DownloadManager Actions
            when (mLabel) {
                null -> {
                    DownloadManager.moveDownload(fromPosition, toPosition)
                }

                getString(R.string.default_download_label_name) -> {
                    DownloadManager.moveDownload(null, fromPosition, toPosition)
                }

                else -> {
                    DownloadManager.moveDownload(mLabel, fromPosition, toPosition)
                }
            }
            mAdapter!!.notifyItemMoved(fromPosition, toPosition)
            return true
        }

        override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {}
    }

    companion object {
        const val KEY_GID = "gid"
        const val KEY_ACTION = "action"
        const val ACTION_CLEAR_DOWNLOAD_SERVICE = "clear_download_service"
        private val PATTERN_AUTHOR = Regex("^(?:\\([^\\[\\]()]+\\))?\\s*\\[([^\\[\\]]+)]")
        private val PATTERN_NAME = Regex("^(?:\\([^\\[\\]()]+\\))?\\s*(?:\\[[^\\[\\]]+])?\\s*(.+)")
        private const val KEY_LABEL = "label"
        private const val LABEL_OFFSET = 2
        private const val PAYLOAD_STATE = 0

        private fun getAuthor(title: String): String {
            val matcher = PATTERN_AUTHOR.find(title) ?: return ""
            return matcher.groupValues[1].trim { it <= ' ' }
        }

        private fun getName(title: String): String {
            val matcher = PATTERN_NAME.find(title) ?: return title
            return matcher.groupValues[1].trim { it <= ' ' }
        }
    }
}
