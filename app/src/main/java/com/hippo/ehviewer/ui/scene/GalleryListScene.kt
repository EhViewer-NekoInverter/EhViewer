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

import android.animation.Animator
import android.annotation.SuppressLint
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.res.Resources
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.TextUtils
import android.text.style.ImageSpan
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.ViewPropertyAnimator
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly
import androidx.annotation.IntDef
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.content.res.AppCompatResources
import androidx.appcompat.widget.PopupMenu
import androidx.appcompat.widget.Toolbar
import androidx.core.view.GravityCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsAnimationCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.datepicker.CalendarConstraints
import com.google.android.material.datepicker.CalendarConstraints.DateValidator
import com.google.android.material.datepicker.CompositeDateValidator
import com.google.android.material.datepicker.DateValidatorPointBackward
import com.google.android.material.datepicker.DateValidatorPointForward
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.hippo.app.EditTextCheckBoxDialogBuilder
import com.hippo.app.EditTextDialogBuilder
import com.hippo.drawable.AddDeleteDrawable
import com.hippo.drawable.DrawerArrowDrawable
import com.hippo.easyrecyclerview.EasyRecyclerView
import com.hippo.easyrecyclerview.FastScroller.OnDragHandlerListener
import com.hippo.easyrecyclerview.LinearDividerItemDecoration
import com.hippo.ehviewer.EhApplication.Companion.favouriteStatusRouter
import com.hippo.ehviewer.EhDB
import com.hippo.ehviewer.FavouriteStatusRouter
import com.hippo.ehviewer.R
import com.hippo.ehviewer.Settings
import com.hippo.ehviewer.WindowInsetsAnimationHelper
import com.hippo.ehviewer.client.EhClient
import com.hippo.ehviewer.client.EhRequest
import com.hippo.ehviewer.client.EhUtils
import com.hippo.ehviewer.client.data.GalleryInfo
import com.hippo.ehviewer.client.data.ListUrlBuilder
import com.hippo.ehviewer.client.data.ListUrlBuilder.Companion.MODE_SUBSCRIPTION
import com.hippo.ehviewer.client.data.ListUrlBuilder.Companion.MODE_TOPLIST
import com.hippo.ehviewer.client.data.ListUrlBuilder.Companion.MODE_WHATS_HOT
import com.hippo.ehviewer.client.exception.EhException
import com.hippo.ehviewer.client.parser.GalleryDetailUrlParser
import com.hippo.ehviewer.client.parser.GalleryListParser
import com.hippo.ehviewer.client.parser.GalleryPageUrlParser
import com.hippo.ehviewer.dao.DownloadInfo
import com.hippo.ehviewer.dao.QuickSearch
import com.hippo.ehviewer.download.DownloadManager
import com.hippo.ehviewer.download.DownloadManager.DownloadInfoListener
import com.hippo.ehviewer.ui.CommonOperations
import com.hippo.ehviewer.ui.GalleryActivity
import com.hippo.ehviewer.ui.dialog.SelectItemWithIconAdapter
import com.hippo.ehviewer.widget.GalleryInfoContentHelper
import com.hippo.ehviewer.widget.SearchBar
import com.hippo.ehviewer.widget.SearchBar.OnStateChangeListener
import com.hippo.ehviewer.widget.SearchBar.Suggestion
import com.hippo.ehviewer.widget.SearchBar.SuggestionProvider
import com.hippo.ehviewer.widget.SearchLayout
import com.hippo.scene.Announcer
import com.hippo.scene.SceneFragment
import com.hippo.util.getParcelableCompat
import com.hippo.util.launchIO
import com.hippo.util.withUIContext
import com.hippo.view.BringOutTransition
import com.hippo.view.ViewTransition
import com.hippo.widget.ContentLayout
import com.hippo.widget.FabLayout
import com.hippo.widget.FabLayout.OnClickFabListener
import com.hippo.widget.FabLayout.OnExpandListener
import com.hippo.widget.SearchBarMover
import com.hippo.yorozuya.AnimationUtils
import com.hippo.yorozuya.LayoutUtils
import com.hippo.yorozuya.MathUtils
import com.hippo.yorozuya.SimpleAnimatorListener
import com.hippo.yorozuya.ViewUtils
import rikka.core.res.resolveColor
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset

class GalleryListScene :
    BaseScene(),
    OnDragHandlerListener,
    OnStateChangeListener,
    SearchLayout.Helper,
    SearchBarMover.Helper,
    SearchBar.Helper,
    View.OnClickListener,
    OnClickFabListener,
    OnExpandListener {
    private val mDownloadManager = DownloadManager

    @SuppressLint("NotifyDataSetChanged")
    private val mDownloadInfoListener: DownloadInfoListener = object : DownloadInfoListener {
        override fun onAdd(info: DownloadInfo, list: List<DownloadInfo>, position: Int) {
            mAdapter?.notifyDataSetChanged()
        }
        override fun onUpdate(info: DownloadInfo, list: List<DownloadInfo>) {}
        override fun onUpdateAll() {}
        override fun onReload() {
            mAdapter?.notifyDataSetChanged()
        }
        override fun onChange() {
            mAdapter?.notifyDataSetChanged()
        }
        override fun onRenameLabel(from: String, to: String) {}
        override fun onRemove(info: DownloadInfo, list: List<DownloadInfo>, position: Int) {
            mAdapter?.notifyDataSetChanged()
        }
        override fun onUpdateLabels() {}
    }

    private val mFavouriteStatusRouter: FavouriteStatusRouter = favouriteStatusRouter

    @SuppressLint("NotifyDataSetChanged")
    private val mFavouriteStatusRouterListener: FavouriteStatusRouter.Listener =
        FavouriteStatusRouter.Listener { _: Long, _: Int ->
            mAdapter?.notifyDataSetChanged()
        }

    private val mOnScrollListener: RecyclerView.OnScrollListener =
        object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {}
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                if (dy >= mHideActionFabSlop) {
                    hideActionFab()
                } else if (dy <= -mHideActionFabSlop / 2) {
                    showActionFab()
                }
            }
        }

    private val mActionFabAnimatorListener: Animator.AnimatorListener =
        object : SimpleAnimatorListener() {
            override fun onAnimationEnd(animation: Animator) {
                if (null != mFabLayout) {
                    (mFabLayout!!.primaryFab as View?)!!.visibility = View.INVISIBLE
                }
            }
        }

    private val mSearchFabAnimatorListener: Animator.AnimatorListener =
        object : SimpleAnimatorListener() {
            override fun onAnimationEnd(animation: Animator) {
                if (null != mSearchFab) {
                    mSearchFab!!.visibility = View.INVISIBLE
                }
            }
        }

    private val selectImageLauncher =
        registerForActivityResult<PickVisualMediaRequest, Uri>(
            ActivityResultContracts.PickVisualMedia(),
        ) { result: Uri? -> mSearchLayout?.setImageUri(result) }

    private lateinit var mUrlBuilder: ListUrlBuilder
    private lateinit var mQuickSearchList: MutableList<QuickSearch>
    private var mRecyclerView: EasyRecyclerView? = null
    private var mAdapter: GalleryListAdapter? = null
    private var mHelper: GalleryListHelper? = null
    private var mViewTransition: ViewTransition? = null
    private var mSearchBar: SearchBar? = null
    private var mSearchBarMover: SearchBarMover? = null
    private var mSearchFab: View? = null
    private var mSearchLayout: SearchLayout? = null
    private var mLeftDrawable: DrawerArrowDrawable? = null
    private var mRightDrawable: AddDeleteDrawable? = null
    private var fabAnimator: ViewPropertyAnimator? = null
    private var mActionFabDrawable: AddDeleteDrawable? = null
    private var mFabLayout: FabLayout? = null
    private var mHideActionFabSlop = 0
    private var mShowActionFab = true
    private var mDrawerViewTransition: ViewTransition? = null
    private var mItemTouchHelper: ItemTouchHelper? = null

    @State
    private var mState = STATE_NORMAL

    // Double click to exit
    private var mPressBackTime: Long = 0
    private var mNavCheckedId = 0
    private var mHasFirstRefresh = false
    private var mIsTopList = false

    override fun getNavCheckedItem(): Int = mNavCheckedId

    private fun handleArgs(args: Bundle?) {
        args ?: return
        mUrlBuilder = when (args.getString(KEY_ACTION)) {
            ACTION_HOMEPAGE -> ListUrlBuilder()
            ACTION_SUBSCRIPTION -> ListUrlBuilder(MODE_SUBSCRIPTION)
            ACTION_WHATS_HOT -> ListUrlBuilder(MODE_WHATS_HOT)
            ACTION_TOP_LIST -> ListUrlBuilder(MODE_TOPLIST, mKeyword = Settings.defaultTopList)
            ACTION_LIST_URL_BUILDER -> args.getParcelableCompat<ListUrlBuilder>(KEY_LIST_URL_BUILDER)
                ?.copy() ?: ListUrlBuilder()
            else -> throw IllegalStateException("Wrong KEY_ACTION:${args.getString(KEY_ACTION)} when handle args!")
        }
    }

    override fun onNewArguments(args: Bundle) {
        handleArgs(args)
        onUpdateUrlBuilder()
        mHelper?.refresh()
        setState(STATE_NORMAL)
        mSearchBarMover?.showSearchBar()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mDownloadManager.addDownloadInfoListener(mDownloadInfoListener)
        mFavouriteStatusRouter.addListener(mFavouriteStatusRouterListener)
        if (savedInstanceState == null) {
            onInit()
        } else {
            onRestore(savedInstanceState)
        }
    }

    override fun onResume() {
        super.onResume()
        mAdapter?.type = Settings.listMode
    }

    private fun onInit() {
        handleArgs(arguments)
    }

    private fun onRestore(savedInstanceState: Bundle) {
        mHasFirstRefresh = savedInstanceState.getBoolean(KEY_HAS_FIRST_REFRESH)
        mUrlBuilder = savedInstanceState.getParcelableCompat(KEY_LIST_URL_BUILDER)!!
        mState = savedInstanceState.getInt(KEY_STATE)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        val hasFirstRefresh: Boolean = if (mHelper != null && 1 == mHelper!!.shownViewIndex) {
            false
        } else {
            mHasFirstRefresh
        }
        outState.putBoolean(KEY_HAS_FIRST_REFRESH, hasFirstRefresh)
        outState.putParcelable(KEY_LIST_URL_BUILDER, mUrlBuilder)
        outState.putInt(KEY_STATE, mState)
    }

    override fun onDestroy() {
        super.onDestroy()
        mDownloadManager.removeDownloadInfoListener(mDownloadInfoListener)
        mFavouriteStatusRouter.removeListener(mFavouriteStatusRouterListener)
    }

    private fun setSearchBarHint(searchBar: SearchBar) {
        searchBar.setEditTextHint(getString(if (EhUtils.isExHentai) R.string.gallery_list_search_bar_hint_exhentai else R.string.gallery_list_search_bar_hint_e_hentai))
    }

    private fun setSearchBarSuggestionProvider(searchBar: SearchBar) {
        searchBar.setSuggestionProvider(object : SuggestionProvider {
            override fun providerSuggestions(text: String): List<Suggestion>? {
                val result1 = GalleryDetailUrlParser.parse(text, false)
                if (result1 != null) {
                    return listOf<Suggestion>(
                        GalleryDetailUrlSuggestion(
                            result1.gid,
                            result1.token,
                        ),
                    )
                }
                val result2 = GalleryPageUrlParser.parse(text, false)
                if (result2 != null) {
                    return listOf<Suggestion>(
                        GalleryPageUrlSuggestion(
                            result2.gid,
                            result2.pToken,
                            result2.page,
                        ),
                    )
                }
                return null
            }
        })
    }

    private fun wrapTagKeyword(keyword: String): String = if (keyword.endsWith(':')) {
        keyword
    } else if (keyword.contains(" ")) {
        val tag = keyword.substringAfter(':')
        val prefix = keyword.dropLast(tag.length)
        "$prefix\"$tag$\""
    } else {
        "$keyword$"
    }

    // Update search bar title, drawer checked item
    private fun onUpdateUrlBuilder() {
        val resources = resourcesOrNull
        if (resources == null || mSearchLayout == null || mFabLayout == null) {
            return
        }
        var keyword = mUrlBuilder.keyword
        val category = mUrlBuilder.category
        val mode = mUrlBuilder.mode
        val isPopular = mode == MODE_WHATS_HOT
        val isTopList = mode == MODE_TOPLIST

        if (isTopList != mIsTopList) {
            mIsTopList = isTopList
            recreateDrawerView()
            mFabLayout!!.getSecondaryFabAt(0)!!.setImageResource(if (isTopList) R.drawable.ic_baseline_format_list_numbered_24 else R.drawable.v_magnify_x24)
        }

        // Update fab visibility
        mFabLayout!!.setSecondaryFabVisibilityAt(1, !isPopular)
        mFabLayout!!.setSecondaryFabVisibilityAt(2, !isTopList && !isPopular)

        // Update normal search mode
        mSearchLayout!!.setNormalSearchMode(if (mode == MODE_SUBSCRIPTION) R.id.search_subscription_search else R.id.search_normal_search)

        // Update search edit text
        if (!TextUtils.isEmpty(keyword) && null != mSearchBar && !mIsTopList) {
            if (mode == ListUrlBuilder.MODE_TAG) {
                keyword = wrapTagKeyword(keyword!!)
            }
            mSearchBar!!.setText(keyword!!)
            mSearchBar!!.cursorToEnd()
        }

        // Update title
        val title = getSuitableTitleForUrlBuilder(resources, mUrlBuilder, true) ?: resources.getString(R.string.search)
        mSearchBar?.setTitle(title)

        // Update nav checked item
        val checkedItemId: Int = when (mode) {
            ListUrlBuilder.MODE_NORMAL -> if (EhUtils.NONE == category && TextUtils.isEmpty(keyword)) R.id.nav_homepage else 0
            MODE_SUBSCRIPTION -> R.id.nav_subscription
            MODE_WHATS_HOT -> R.id.nav_whats_hot
            MODE_TOPLIST -> R.id.nav_toplist
            ListUrlBuilder.MODE_TAG, ListUrlBuilder.MODE_UPLOADER, ListUrlBuilder.MODE_IMAGE_SEARCH -> 0
            else -> throw IllegalStateException("Unexpected value: $mode")
        }
        setNavCheckedItem(checkedItemId)
        mNavCheckedId = checkedItemId
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        val view = inflater.inflate(R.layout.scene_gallery_list, container, false)
        val context = requireContext()
        mHideActionFabSlop = ViewConfiguration.get(context).scaledTouchSlop
        mShowActionFab = true
        val mainLayout = ViewUtils.`$$`(view, R.id.main_layout)
        val mContentLayout = ViewUtils.`$$`(mainLayout, R.id.content_layout) as ContentLayout
        mRecyclerView = mContentLayout.recyclerView
        val fastScroller = mContentLayout.fastScroller
        mSearchLayout = ViewUtils.`$$`(mainLayout, R.id.search_layout) as SearchLayout
        mSearchBar = ViewUtils.`$$`(mainLayout, R.id.search_bar) as SearchBar
        mFabLayout = ViewUtils.`$$`(mainLayout, R.id.fab_layout) as FabLayout
        mSearchFab = ViewUtils.`$$`(mainLayout, R.id.search_fab)
        ViewCompat.setWindowInsetsAnimationCallback(
            view,
            WindowInsetsAnimationHelper(
                WindowInsetsAnimationCompat.Callback.DISPATCH_MODE_STOP,
                mFabLayout,
                mSearchFab!!.parent as View,
            ),
        )
        val paddingTopSB = resources.getDimensionPixelOffset(R.dimen.gallery_padding_top_search_bar)
        val paddingBottomFab = resources.getDimensionPixelOffset(R.dimen.gallery_padding_bottom_fab)
        mViewTransition = BringOutTransition(mContentLayout, mSearchLayout)
        mHelper = GalleryListHelper()
        mContentLayout.setHelper(mHelper!!)
        mContentLayout.fastScroller.setOnDragHandlerListener(this)
        mContentLayout.setFitPaddingTop(paddingTopSB)
        mAdapter = GalleryListAdapter(
            inflater,
            resources,
            mRecyclerView!!,
            Settings.listMode,
        )
        mRecyclerView!!.clipToPadding = false
        mRecyclerView!!.clipChildren = false
        mRecyclerView!!.addOnScrollListener(mOnScrollListener)
        fastScroller.setPadding(
            fastScroller.paddingLeft,
            fastScroller.paddingTop + paddingTopSB,
            fastScroller.paddingRight,
            fastScroller.paddingBottom,
        )
        mLeftDrawable = DrawerArrowDrawable(context, theme.resolveColor(android.R.attr.colorControlNormal))
        mRightDrawable = AddDeleteDrawable(context, theme.resolveColor(android.R.attr.colorControlNormal))
        mSearchBar!!.setLeftDrawable(mLeftDrawable!!)
        mSearchBar!!.setRightDrawable(mRightDrawable!!)
        mSearchBar!!.setHelper(this)
        mSearchBar!!.setOnStateChangeListener(this)
        setSearchBarHint(mSearchBar!!)
        setSearchBarSuggestionProvider(mSearchBar!!)
        mSearchLayout!!.setHelper(this)
        mSearchLayout!!.setPadding(
            mSearchLayout!!.paddingLeft,
            mSearchLayout!!.paddingTop + paddingTopSB,
            mSearchLayout!!.paddingRight,
            mSearchLayout!!.paddingBottom + paddingBottomFab,
        )
        mFabLayout!!.setAutoCancel(true)
        mFabLayout!!.isExpanded = false
        mFabLayout!!.setHidePrimaryFab(false)
        mFabLayout!!.setOnClickFabListener(this)
        mFabLayout!!.setOnExpandListener(this)
        addAboveSnackView(mFabLayout!!)
        mActionFabDrawable = AddDeleteDrawable(context, context.getColor(R.color.primary_drawable_dark))
        mFabLayout!!.primaryFab!!.setImageDrawable(mActionFabDrawable)
        mSearchFab!!.setOnClickListener(this)
        mSearchBarMover = SearchBarMover(this, mSearchBar, mRecyclerView, mSearchLayout)

        // Update list url builder
        onUpdateUrlBuilder()

        // Restore state
        val newState = mState
        mState = STATE_NORMAL
        setState(newState, false)

        // Only refresh for the first time
        if (!mHasFirstRefresh) {
            mHasFirstRefresh = true
            mHelper!!.firstRefresh()
        }
        return view
    }

    override fun onDestroyView() {
        super.onDestroyView()
        if (null != mSearchBarMover) {
            mSearchBarMover!!.cancelAnimation()
            mSearchBarMover = null
        }
        if (null != mHelper) {
            mHelper!!.destroy()
            if (1 == mHelper!!.shownViewIndex) {
                mHasFirstRefresh = false
            }
        }
        if (null != mRecyclerView) {
            mRecyclerView!!.stopScroll()
            mRecyclerView = null
        }
        if (null != mFabLayout) {
            removeAboveSnackView(mFabLayout!!)
            mFabLayout = null
        }
        mAdapter = null
        mSearchLayout = null
        mSearchBar = null
        mSearchFab = null
        mViewTransition = null
        mLeftDrawable = null
        mRightDrawable = null
        mActionFabDrawable = null
    }

    private fun updateDrawerView(animation: Boolean) {
        if (null == mDrawerViewTransition) {
            return
        }
        if (mIsTopList || mQuickSearchList.isNotEmpty()) {
            mDrawerViewTransition!!.showView(0, animation)
        } else {
            mDrawerViewTransition!!.showView(1, animation)
        }
    }

    private fun showQuickSearchTipDialog() {
        val context = context ?: return
        AlertDialog.Builder(context)
            .setTitle(R.string.readme)
            .setMessage(R.string.add_quick_search_tip)
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    private fun showAddQuickSearchDialog(adapter: QsDrawerAdapter) {
        val context = context
        if (null == context || null == mHelper) {
            return
        }

        // Can't add image search as quick search
        if (ListUrlBuilder.MODE_IMAGE_SEARCH == mUrlBuilder.mode) {
            showTip(R.string.image_search_not_quick_search, LENGTH_LONG)
            return
        }

        // Get next gid
        val gi = mHelper!!.firstVisibleItem
        val next = if (gi != null) "@" + (gi.gid + 1) else null

        // Check duplicate
        for (q in mQuickSearchList) {
            if (mUrlBuilder.equalsQuickSearch(q)) {
                val i = q.name!!.lastIndexOf("@")
                if (i != -1 && q.name!!.substring(i) == next) {
                    showTip(getString(R.string.duplicate_quick_search, q.name), LENGTH_LONG)
                    return
                }
            }
        }

        val builder = EditTextCheckBoxDialogBuilder(
            context,
            getSuitableTitleForUrlBuilder(context.resources, mUrlBuilder, false),
            getString(R.string.quick_search),
            getString(R.string.save_progress),
            Settings.qSSaveProgress,
        )
        builder.setTitle(R.string.add_quick_search_dialog_title)
        builder.setPositiveButton(android.R.string.ok, null)
        val dialog = builder.show()
        dialog.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener {
            lifecycleScope.launchIO {
                var text = builder.text.trim { it <= ' ' }

                // Check name empty
                if (TextUtils.isEmpty(text)) {
                    withUIContext {
                        builder.setError(getString(R.string.name_is_empty))
                    }
                    return@launchIO
                }

                // Add gid
                val checked = builder.isChecked
                Settings.putQSSaveProgress(checked)
                if (checked && next != null) {
                    text += next
                }

                // Check name duplicate
                for ((_, name) in mQuickSearchList) {
                    if (text == name) {
                        withUIContext {
                            builder.setError(getString(R.string.duplicate_name))
                        }
                        return@launchIO
                    }
                }
                withUIContext {
                    builder.setError(null)
                }
                dialog.dismiss()
                val quickSearch = mUrlBuilder.toQuickSearch()
                quickSearch.name = text
                mQuickSearchList.add(quickSearch)
                // DB Actions
                EhDB.insertQuickSearch(quickSearch)
                withUIContext {
                    adapter.notifyItemInserted(mQuickSearchList.size - 1)
                    updateDrawerView(true)
                }
            }
        }
    }

    override fun onCreateDrawerView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        val view = inflater.inflate(R.layout.drawer_list_rv, container, false)
        val toolbar = ViewUtils.`$$`(view, R.id.toolbar) as Toolbar
        val tip = ViewUtils.`$$`(view, R.id.tip) as TextView
        val recyclerView = view.findViewById<EasyRecyclerView>(R.id.recycler_view_drawer)
        mDrawerViewTransition = ViewTransition(recyclerView, tip)
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        val decoration = LinearDividerItemDecoration(
            LinearDividerItemDecoration.VERTICAL,
            theme.resolveColor(R.attr.dividerColor),
            LayoutUtils.dp2pix(requireContext(), 1f),
        )
        decoration.setShowLastDivider(true)
        recyclerView.addItemDecoration(decoration)
        val qsDrawerAdapter = QsDrawerAdapter(inflater)
        qsDrawerAdapter.setHasStableIds(true)
        mItemTouchHelper = ItemTouchHelper(GalleryListQSItemTouchHelperCallback(qsDrawerAdapter))
        mItemTouchHelper!!.attachToRecyclerView(recyclerView)
        lifecycleScope.launchIO {
            // DB Actions
            mQuickSearchList = EhDB.allQuickSearch.toMutableList()
            withUIContext {
                recyclerView.adapter = qsDrawerAdapter
                updateDrawerView(false)
            }
        }
        tip.setText(R.string.quick_search_tip)
        toolbar.setTitle(if (mIsTopList) R.string.toplist else R.string.quick_search)
        if (!mIsTopList) toolbar.inflateMenu(R.menu.drawer_gallery_list)
        toolbar.setOnMenuItemClickListener { item: MenuItem ->
            when (item.itemId) {
                R.id.action_add -> showAddQuickSearchDialog(qsDrawerAdapter)
                R.id.action_help -> showQuickSearchTipDialog()
            }
            true
        }
        return view
    }

    private fun checkDoubleClickExit(): Boolean {
        if (stackIndex != 0) {
            return false
        }
        val time = System.currentTimeMillis()
        return if (time - mPressBackTime > BACK_PRESSED_INTERVAL) {
            // It is the last scene
            mPressBackTime = time
            showTip(R.string.press_twice_exit, LENGTH_SHORT)
            true
        } else {
            false
        }
    }

    override fun onBackPressed() {
        if (null != mFabLayout && mFabLayout!!.isExpanded) {
            mFabLayout!!.setExpanded(expanded = false, animation = true)
            return
        }
        var handle = false
        when (mState) {
            STATE_NORMAL -> handle = checkDoubleClickExit()
            STATE_SIMPLE_SEARCH, STATE_SEARCH -> {
                setState(STATE_NORMAL)
                handle = true
            }
            STATE_SEARCH_SHOW_LIST -> {
                setState(STATE_SEARCH)
                handle = true
            }
        }
        if (!handle) {
            finish()
        }
    }

    fun onItemClick(view: View, position: Int) {
        if (null == mHelper || null == mRecyclerView) {
            return
        }
        val gi = mHelper!!.getDataAtEx(position) ?: return
        val args = Bundle()
        args.putString(GalleryDetailScene.KEY_ACTION, GalleryDetailScene.ACTION_GALLERY_INFO)
        args.putParcelable(GalleryDetailScene.KEY_GALLERY_INFO, gi)
        val announcer = Announcer(GalleryDetailScene::class.java).setArgs(args)
        view.findViewById<View>(R.id.thumb)?.let {
            announcer.setTranHelper(EnterGalleryDetailTransaction(it))
        }
        startScene(announcer)
    }

    override fun onClick(v: View) {
        if (STATE_NORMAL != mState && null != mSearchBar) {
            mSearchBar!!.applySearch()
            hideSoftInput()
        }
    }

    override fun onClickPrimaryFab(view: FabLayout, fab: FloatingActionButton) {
        if (STATE_NORMAL == mState) {
            view.toggle()
        }
    }

    private fun showGoToDialog() {
        val context = context
        if (null == context || null == mHelper) {
            return
        }
        if (mIsTopList) {
            val page = mHelper!!.pageForTop + 1
            val pages = mHelper!!.pages
            val hint = getString(R.string.go_to_hint, page, pages)
            val builder = EditTextDialogBuilder(context, null, hint)
            builder.editText.inputType =
                InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            val dialog = builder.setTitle(R.string.go_to)
                .setPositiveButton(android.R.string.ok, null)
                .show()
            dialog.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener {
                val text = builder.text.trim { it <= ' ' }
                val goTo: Int = try {
                    text.toInt() - 1
                } catch (_: NumberFormatException) {
                    builder.setError(getString(R.string.error_invalid_number))
                    return@setOnClickListener
                }
                if (goTo < 0 || goTo >= pages) {
                    builder.setError(getString(R.string.error_out_of_range))
                    return@setOnClickListener
                }
                builder.setError(null)
                mHelper!!.goTo(goTo)
                dialog.dismiss()
            }
        } else {
            val local = LocalDateTime.of(2007, 3, 21, 0, 0)
            val fromDate =
                local.atZone(ZoneId.ofOffset("UTC", ZoneOffset.UTC)).toInstant().toEpochMilli()
            val toDate = MaterialDatePicker.todayInUtcMilliseconds()
            val listValidators = ArrayList<DateValidator>()
            listValidators.add(DateValidatorPointForward.from(fromDate))
            listValidators.add(DateValidatorPointBackward.before(toDate))
            val constraintsBuilder = CalendarConstraints.Builder()
                .setStart(fromDate)
                .setEnd(toDate)
                .setValidator(CompositeDateValidator.allOf(listValidators))
            val datePicker = MaterialDatePicker.Builder.datePicker()
                .setCalendarConstraints(constraintsBuilder.build())
                .setTitleText(R.string.go_to)
                .setSelection(toDate)
                .build()
            datePicker.show(requireActivity().supportFragmentManager, "date-picker")
            datePicker.addOnPositiveButtonClickListener { v: Long? ->
                mHelper!!.goTo(
                    v!!,
                    true,
                )
            }
        }
    }

    private fun showGidDialog() {
        val context = context
        if (null == context || null == mHelper) {
            return
        }
        val builder = EditTextDialogBuilder(context, null, getString(R.string.go_to_gid))
        val dialog = builder.setTitle(R.string.go_to)
            .setPositiveButton(android.R.string.ok, null)
            .show()
        dialog.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener {
            var text = builder.text.trim { it <= ' ' }
            if (TextUtils.isEmpty(text)) text = "0"
            val goTo: Int = try {
                text.toInt() + 1
            } catch (_: NumberFormatException) {
                builder.setError(getString(R.string.error_invalid_number))
                return@setOnClickListener
            }
            if (goTo < 1) {
                builder.setError(getString(R.string.error_out_of_range))
                return@setOnClickListener
            }
            builder.setError(null)
            mHelper!!.goTo(goTo.toString(), goTo != 1)
            dialog.dismiss()
        }
    }

    override fun onClickSecondaryFab(view: FabLayout, fab: FloatingActionButton, position: Int) {
        if (null == mHelper) {
            return
        }
        when (position) {
            // Open right
            0 -> openDrawer(GravityCompat.END)
            // Go to
            1 -> {
                if (!mIsTopList || mHelper!!.canGoTo()) showGoToDialog()
            }
            // Last page
            2 -> showGidDialog()
            // Refresh
            3 -> mHelper!!.refresh()
        }
        view.isExpanded = false
    }

    override fun onExpand(expanded: Boolean) {
        if (null == mActionFabDrawable) {
            return
        }
        if (expanded) {
            setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED, GravityCompat.START)
            setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED, GravityCompat.END)
            mActionFabDrawable!!.setDelete(ANIMATE_TIME)
        } else {
            setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED, GravityCompat.START)
            setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED, GravityCompat.END)
            mActionFabDrawable!!.setAdd(ANIMATE_TIME)
        }
    }

    fun onItemLongClick(position: Int): Boolean {
        val context = context
        val activity = mainActivity
        if (null == context || null == activity || null == mHelper) {
            return false
        }
        val gi = mHelper!!.getDataAtEx(position) ?: return true
        val downloaded = mDownloadManager.getDownloadState(gi.gid) != DownloadInfo.STATE_INVALID
        val favourited = gi.favoriteSlot != -2
        val items = if (downloaded) {
            arrayOf<CharSequence>(
                context.getString(R.string.read),
                context.getString(R.string.delete_downloads),
                context.getString(if (favourited) R.string.remove_from_favourites else R.string.add_to_favourites),
                context.getString(R.string.download_move_dialog_title),
            )
        } else {
            arrayOf<CharSequence>(
                context.getString(R.string.read),
                context.getString(R.string.download),
                context.getString(if (favourited) R.string.remove_from_favourites else R.string.add_to_favourites),
            )
        }
        val icons = if (downloaded) {
            intArrayOf(
                R.drawable.v_book_open_x24,
                R.drawable.v_delete_x24,
                if (favourited) R.drawable.v_heart_broken_x24 else R.drawable.v_heart_x24,
                R.drawable.v_folder_move_x24,
            )
        } else {
            intArrayOf(
                R.drawable.v_book_open_x24,
                R.drawable.v_download_x24,
                if (favourited) R.drawable.v_heart_broken_x24 else R.drawable.v_heart_x24,
            )
        }
        AlertDialog.Builder(context)
            .setTitle(EhUtils.getSuitableTitle(gi))
            .setAdapter(
                SelectItemWithIconAdapter(
                    context,
                    items,
                    icons,
                ),
            ) { _: DialogInterface?, which: Int ->
                when (which) {
                    0 -> {
                        val intent = Intent(activity, GalleryActivity::class.java)
                        intent.action = GalleryActivity.ACTION_EH
                        intent.putExtra(GalleryActivity.KEY_GALLERY_INFO, gi)
                        startActivity(intent)
                    }

                    1 -> if (downloaded) {
                        AlertDialog.Builder(context)
                            .setTitle(R.string.download_remove_dialog_title)
                            .setMessage(
                                getString(
                                    R.string.download_remove_dialog_message,
                                    gi.title,
                                ),
                            )
                            .setPositiveButton(android.R.string.ok) { _: DialogInterface?, _: Int ->
                                // DownloadManager Actions
                                mDownloadManager.deleteDownload(
                                    gi.gid,
                                )
                            }
                            .show()
                    } else {
                        // CommonOperations Actions
                        CommonOperations.startDownload(activity, gi, false)
                    }

                    2 -> if (favourited) {
                        // CommonOperations Actions
                        CommonOperations.removeFromFavorites(
                            activity,
                            gi,
                            RemoveFromFavoriteListener(context),
                        )
                    } else {
                        // CommonOperations Actions
                        CommonOperations.addToFavorites(
                            activity,
                            gi,
                            AddToFavoriteListener(context),
                            false,
                        )
                    }

                    3 -> {
                        val labelRawList = mDownloadManager.labelList
                        val labelList: MutableList<String> = ArrayList(labelRawList.size + 1)
                        labelList.add(getString(R.string.default_download_label_name))
                        var i = 0
                        val n = labelRawList.size
                        while (i < n) {
                            labelRawList[i].label?.let { labelList.add(it) }
                            i++
                        }
                        val labels = labelList.toTypedArray()
                        val helper = MoveDialogHelper(labels, gi)
                        AlertDialog.Builder(context)
                            .setTitle(R.string.download_move_dialog_title)
                            .setItems(labels, helper)
                            .show()
                    }
                }
            }.show()
        return true
    }

    private fun showActionFab() {
        if (null != mFabLayout && STATE_NORMAL == mState && !mShowActionFab) {
            mShowActionFab = true
            val fab: View? = mFabLayout!!.primaryFab
            fabAnimator?.cancel()
            fab!!.visibility = View.VISIBLE
            fab.rotation = -45.0f
            fabAnimator = fab.animate().scaleX(1.0f).scaleY(1.0f).rotation(0.0f).setListener(null)
                .setDuration(ANIMATE_TIME).setStartDelay(0L)
                .setInterpolator(AnimationUtils.FAST_SLOW_INTERPOLATOR)
            fabAnimator!!.start()
        }
    }

    private fun hideActionFab() {
        if (null != mFabLayout && STATE_NORMAL == mState && mShowActionFab) {
            mShowActionFab = false
            val fab: View? = mFabLayout!!.primaryFab
            fabAnimator?.cancel()
            fabAnimator =
                fab!!.animate().scaleX(0.0f).scaleY(0.0f).setListener(mActionFabAnimatorListener)
                    .setDuration(ANIMATE_TIME).setStartDelay(0L)
                    .setInterpolator(AnimationUtils.SLOW_FAST_INTERPOLATOR)
            fabAnimator!!.start()
        }
    }

    private fun selectSearchFab(animation: Boolean) {
        if (null == mFabLayout || null == mSearchFab) {
            return
        }
        mShowActionFab = false
        if (animation) {
            val fab: View? = mFabLayout!!.primaryFab
            val delay: Long
            if (View.INVISIBLE == fab!!.visibility) {
                delay = 0L
            } else {
                delay = ANIMATE_TIME
                mFabLayout!!.setExpanded(expanded = false, animation = true)
                fab.animate().scaleX(0.0f).scaleY(0.0f).setListener(mActionFabAnimatorListener)
                    .setDuration(ANIMATE_TIME).setStartDelay(0L)
                    .setInterpolator(AnimationUtils.SLOW_FAST_INTERPOLATOR).start()
            }
            mSearchFab!!.visibility = View.VISIBLE
            mSearchFab!!.rotation = -45.0f
            mSearchFab!!.animate().scaleX(1.0f).scaleY(1.0f).rotation(0.0f).setListener(null)
                .setDuration(ANIMATE_TIME).setStartDelay(delay)
                .setInterpolator(AnimationUtils.FAST_SLOW_INTERPOLATOR).start()
        } else {
            mFabLayout!!.setExpanded(expanded = false, animation = false)
            val fab: View? = mFabLayout!!.primaryFab
            fab!!.visibility = View.INVISIBLE
            fab.scaleX = 0.0f
            fab.scaleY = 0.0f
            mSearchFab!!.visibility = View.VISIBLE
            mSearchFab!!.scaleX = 1.0f
            mSearchFab!!.scaleY = 1.0f
        }
    }

    private fun selectActionFab(animation: Boolean) {
        if (null == mFabLayout || null == mSearchFab) {
            return
        }
        mShowActionFab = true
        if (animation) {
            val delay: Long
            if (View.INVISIBLE == mSearchFab!!.visibility) {
                delay = 0L
            } else {
                delay = ANIMATE_TIME
                mSearchFab!!.animate().scaleX(0.0f).scaleY(0.0f)
                    .setListener(mSearchFabAnimatorListener)
                    .setDuration(ANIMATE_TIME).setStartDelay(0L)
                    .setInterpolator(AnimationUtils.SLOW_FAST_INTERPOLATOR).start()
            }
            val fab: View? = mFabLayout!!.primaryFab
            fab!!.visibility = View.VISIBLE
            fab.rotation = -45.0f
            fab.animate().scaleX(1.0f).scaleY(1.0f).rotation(0.0f).setListener(null)
                .setDuration(ANIMATE_TIME).setStartDelay(delay)
                .setInterpolator(AnimationUtils.FAST_SLOW_INTERPOLATOR).start()
        } else {
            mFabLayout!!.setExpanded(expanded = false, animation = false)
            val fab: View? = mFabLayout!!.primaryFab
            fab!!.visibility = View.VISIBLE
            fab.scaleX = 1.0f
            fab.scaleY = 1.0f
            mSearchFab!!.visibility = View.INVISIBLE
            mSearchFab!!.scaleX = 0.0f
            mSearchFab!!.scaleY = 0.0f
        }
    }

    private fun setState(@State state: Int) {
        setState(state, true)
    }

    @SuppressLint("SwitchIntDef")
    private fun setState(@State state: Int, animation: Boolean) {
        if (null == mSearchBar || null == mSearchBarMover || null == mViewTransition || null == mSearchLayout) {
            return
        }
        if (mState != state) {
            val oldState = mState
            mState = state
            when (oldState) {
                STATE_NORMAL -> when (state) {
                    STATE_SIMPLE_SEARCH -> {
                        mSearchBar!!.setState(SearchBar.STATE_SEARCH_LIST, animation)
                        mSearchBarMover!!.returnSearchBarPosition()
                        selectSearchFab(animation)
                    }
                    STATE_SEARCH -> {
                        mViewTransition!!.showView(1, animation)
                        mSearchLayout!!.scrollSearchContainerToTop()
                        mSearchBar!!.setState(SearchBar.STATE_SEARCH, animation)
                        mSearchBarMover!!.returnSearchBarPosition()
                        selectSearchFab(animation)
                    }
                    STATE_SEARCH_SHOW_LIST -> {
                        mViewTransition!!.showView(1, animation)
                        mSearchLayout!!.scrollSearchContainerToTop()
                        mSearchBar!!.setState(SearchBar.STATE_SEARCH_LIST, animation)
                        mSearchBarMover!!.returnSearchBarPosition()
                        selectSearchFab(animation)
                    }
                }

                STATE_SIMPLE_SEARCH -> when (state) {
                    STATE_NORMAL -> {
                        mSearchBar!!.setState(SearchBar.STATE_NORMAL, animation)
                        mSearchBarMover!!.returnSearchBarPosition()
                        selectActionFab(animation)
                    }
                    STATE_SEARCH -> {
                        mViewTransition!!.showView(1, animation)
                        mSearchLayout!!.scrollSearchContainerToTop()
                        mSearchBar!!.setState(SearchBar.STATE_SEARCH, animation)
                        mSearchBarMover!!.returnSearchBarPosition()
                    }
                    STATE_SEARCH_SHOW_LIST -> {
                        mViewTransition!!.showView(1, animation)
                        mSearchLayout!!.scrollSearchContainerToTop()
                        mSearchBar!!.setState(SearchBar.STATE_SEARCH_LIST, animation)
                        mSearchBarMover!!.returnSearchBarPosition()
                    }
                }

                STATE_SEARCH -> when (state) {
                    STATE_NORMAL -> {
                        mViewTransition!!.showView(0, animation)
                        mSearchBar!!.setState(SearchBar.STATE_NORMAL, animation)
                        mSearchBarMover!!.returnSearchBarPosition()
                        selectActionFab(animation)
                    }
                    STATE_SIMPLE_SEARCH -> {
                        mViewTransition!!.showView(0, animation)
                        mSearchBar!!.setState(SearchBar.STATE_SEARCH_LIST, animation)
                        mSearchBarMover!!.returnSearchBarPosition()
                    }
                    STATE_SEARCH_SHOW_LIST -> {
                        mSearchBar!!.setState(SearchBar.STATE_SEARCH_LIST, animation)
                        mSearchBarMover!!.returnSearchBarPosition()
                    }
                }

                STATE_SEARCH_SHOW_LIST -> when (state) {
                    STATE_NORMAL -> {
                        mViewTransition!!.showView(0, animation)
                        mSearchBar!!.setState(SearchBar.STATE_NORMAL, animation)
                        mSearchBarMover!!.returnSearchBarPosition()
                        selectActionFab(animation)
                    }
                    STATE_SIMPLE_SEARCH -> {
                        mViewTransition!!.showView(0, animation)
                        mSearchBar!!.setState(SearchBar.STATE_SEARCH_LIST, animation)
                        mSearchBarMover!!.returnSearchBarPosition()
                    }
                    STATE_SEARCH -> {
                        mSearchBar!!.setState(SearchBar.STATE_SEARCH, animation)
                        mSearchBarMover!!.returnSearchBarPosition()
                    }
                }
            }
        }
    }

    override fun onClickTitle() {
        if (mState == STATE_NORMAL) {
            setState(STATE_SIMPLE_SEARCH)
        }
    }

    override fun onClickLeftIcon() {
        if (null == mSearchBar) {
            return
        }
        if (mSearchBar!!.getState() == SearchBar.STATE_NORMAL) {
            toggleDrawer(GravityCompat.START)
        } else {
            setState(STATE_NORMAL)
        }
    }

    override fun onClickRightIcon() {
        if (null == mSearchBar) {
            return
        }
        if (mSearchBar!!.getState() == SearchBar.STATE_NORMAL) {
            setState(STATE_SEARCH)
        } else {
            if (mSearchBar!!.getEditText().length() == 0) {
                setState(STATE_NORMAL)
            } else {
                // Clear
                mSearchBar!!.setText("")
            }
        }
    }

    override fun onSearchEditTextClick() {
        if (mState == STATE_SEARCH) {
            setState(STATE_SEARCH_SHOW_LIST)
        }
    }

    override fun onApplySearch(query: String) {
        if (null == mHelper || null == mSearchLayout) {
            return
        }
        if (mState == STATE_SEARCH || mState == STATE_SEARCH_SHOW_LIST) {
            try {
                mSearchLayout!!.formatListUrlBuilder(mUrlBuilder, query)
            } catch (e: EhException) {
                showTip(e.message, LENGTH_LONG)
                return
            }
        } else {
            val oldMode = mUrlBuilder.mode
            // If it's MODE_SUBSCRIPTION, keep it
            val newMode =
                if (oldMode == MODE_SUBSCRIPTION) MODE_SUBSCRIPTION else ListUrlBuilder.MODE_NORMAL
            mUrlBuilder.reset()
            mUrlBuilder.mode = newMode
            mUrlBuilder.keyword = query
        }
        onUpdateUrlBuilder()
        mHelper!!.refresh()
        setState(STATE_NORMAL)
    }

    override fun onSearchEditTextBackPressed() {
        onBackPressed()
    }

    override fun onReceiveContent(uri: Uri?) {
        if (null == mSearchLayout || null == uri) {
            return
        }
        mSearchLayout!!.setSearchMode(SearchLayout.SEARCH_MODE_IMAGE)
        mSearchLayout!!.setImageUri(uri)
        setState(STATE_SEARCH)
    }

    override fun onStartDragHandler() {
        // Lock right drawer
        setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED, GravityCompat.END)
    }

    override fun onEndDragHandler() {
        // Restore right drawer
        setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED, GravityCompat.END)
        if (null != mSearchBarMover) {
            mSearchBarMover!!.returnSearchBarPosition()
        }
    }

    override fun onStateChange(searchBar: SearchBar, newState: Int, oldState: Int, animation: Boolean) {
        if (null == mLeftDrawable || null == mRightDrawable) {
            return
        }

        when (oldState) {
            SearchBar.STATE_NORMAL -> {
                mLeftDrawable!!.setArrow(if (animation) ANIMATE_TIME else 0)
                mRightDrawable!!.setDelete(if (animation) ANIMATE_TIME else 0)
            }
            SearchBar.STATE_SEARCH -> if (newState == SearchBar.STATE_NORMAL) {
                mLeftDrawable!!.setMenu(if (animation) ANIMATE_TIME else 0)
                mRightDrawable!!.setAdd(if (animation) ANIMATE_TIME else 0)
            }
            SearchBar.STATE_SEARCH_LIST -> if (newState == SearchBar.STATE_NORMAL) {
                mLeftDrawable!!.setMenu(if (animation) ANIMATE_TIME else 0)
                mRightDrawable!!.setAdd(if (animation) ANIMATE_TIME else 0)
            }
        }

        if (newState == STATE_NORMAL || newState == STATE_SIMPLE_SEARCH) {
            setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED, GravityCompat.START)
            setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED, GravityCompat.END)
        } else {
            setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED, GravityCompat.START)
            setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED, GravityCompat.END)
        }
    }

    override fun onChangeSearchMode() {
        if (null != mSearchBarMover) {
            mSearchBarMover!!.showSearchBar()
        }
    }

    override fun onSelectImage() {
        val builder = PickVisualMediaRequest.Builder()
        builder.setMediaType(ImageOnly)
        selectImageLauncher.launch(builder.build())
    }

    // SearchBarMover.Helper
    override fun isValidView(recyclerView: RecyclerView): Boolean = (mState == STATE_NORMAL && recyclerView == mRecyclerView) ||
        (mState == STATE_SEARCH && recyclerView == mSearchLayout)

    // SearchBarMover.Helper
    override fun getValidRecyclerView(): RecyclerView? = if (mState == STATE_NORMAL || mState == STATE_SIMPLE_SEARCH) {
        mRecyclerView
    } else {
        mSearchLayout
    }

    // SearchBarMover.Helper
    override fun forceShowSearchBar(): Boolean = mState == STATE_SIMPLE_SEARCH || mState == STATE_SEARCH_SHOW_LIST

    private fun onGetGalleryListSuccess(result: GalleryListParser.Result, taskId: Int) {
        if (mHelper != null && mHelper!!.isCurrentTask(taskId)) {
            val emptyString =
                getString(if (mUrlBuilder.mode == MODE_SUBSCRIPTION && result.noWatchedTags) R.string.gallery_list_empty_hit_subscription else R.string.gallery_list_empty_hit)
            mHelper!!.setEmptyString(emptyString)
            if (mIsTopList) {
                mHelper!!.onGetPageData(
                    taskId,
                    result.pages,
                    result.nextPage,
                    null,
                    null,
                    result.galleryInfoList,
                )
            } else {
                mHelper!!.onGetPageData(
                    taskId,
                    0,
                    0,
                    result.prev,
                    result.next,
                    result.galleryInfoList,
                )
            }
        }
    }

    private fun onGetGalleryListFailure(e: Exception, taskId: Int) {
        if (mHelper != null && mHelper!!.isCurrentTask(taskId)) {
            mHelper!!.onGetException(taskId, e)
        }
    }

    @IntDef(STATE_NORMAL, STATE_SIMPLE_SEARCH, STATE_SEARCH, STATE_SEARCH_SHOW_LIST)
    @Retention(AnnotationRetention.SOURCE)
    private annotation class State
    private inner class GetGalleryListListener(
        context: Context,
        private val mTaskId: Int,
    ) : EhCallback<GalleryListScene, GalleryListParser.Result>(context) {
        override fun onSuccess(result: GalleryListParser.Result) {
            val scene = this@GalleryListScene
            scene.onGetGalleryListSuccess(result, mTaskId)
        }

        override fun onFailure(e: Exception) {
            val scene = this@GalleryListScene
            scene.onGetGalleryListFailure(e, mTaskId)
        }

        override fun onCancel() {}
    }

    private class AddToFavoriteListener(context: Context) : EhCallback<GalleryListScene, Unit>(context) {
        override fun onSuccess(result: Unit) {
            showTip(R.string.add_to_favorite_success, LENGTH_SHORT)
        }

        override fun onFailure(e: Exception) {
            showTip(R.string.add_to_favorite_failure, LENGTH_LONG)
        }

        override fun onCancel() {}
    }

    private class RemoveFromFavoriteListener(context: Context) : EhCallback<GalleryListScene, Unit>(context) {
        override fun onSuccess(result: Unit) {
            showTip(R.string.remove_from_favorite_success, LENGTH_SHORT)
        }

        override fun onFailure(e: Exception) {
            showTip(R.string.remove_from_favorite_failure, LENGTH_LONG)
        }

        override fun onCancel() {}
    }

    @SuppressLint("ClickableViewAccessibility")
    private inner class QsDrawerHolder(
        itemView: View,
    ) : RecyclerView.ViewHolder(itemView),
        View.OnTouchListener {
        val key: TextView = ViewUtils.`$$`(itemView, R.id.tv_key) as TextView
        val option: ImageView = ViewUtils.`$$`(itemView, R.id.iv_option) as ImageView

        init {
            option.setOnTouchListener(this)
        }

        override fun onTouch(v: View, event: MotionEvent): Boolean {
            if (mItemTouchHelper != null && event.action == MotionEvent.ACTION_DOWN) {
                mItemTouchHelper!!.startDrag(this)
            }
            return false
        }
    }

    private inner class MoveDialogHelper(
        private val mLabels: Array<String>,
        private val mGi: GalleryInfo,
    ) : DialogInterface.OnClickListener {
        override fun onClick(dialog: DialogInterface, which: Int) {
            // Cancel check mode
            context ?: return
            mRecyclerView?.outOfCustomChoiceMode()
            val downloadInfo = mDownloadManager.getDownloadInfo(mGi.gid) ?: return
            val label = if (which == 0) null else mLabels[which]
            // DownloadManager Actions
            mDownloadManager.changeLabel(listOf(downloadInfo), label)
        }
    }

    private inner class QsDrawerAdapter(private val mInflater: LayoutInflater) : RecyclerView.Adapter<QsDrawerHolder>() {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): QsDrawerHolder {
            val holder = QsDrawerHolder(mInflater.inflate(R.layout.item_drawer_list, parent, false))
            if (!mIsTopList) {
                holder.itemView.setOnClickListener {
                    if (null == mHelper) {
                        return@setOnClickListener
                    }
                    val quickSearch = mQuickSearchList[holder.bindingAdapterPosition]
                    mUrlBuilder.set(quickSearch)
                    onUpdateUrlBuilder()
                    val i = quickSearch.name!!.lastIndexOf("@")
                    mHelper!!.goTo(if (i != -1) quickSearch.name!!.substring(i + 1) else null, true)
                    setState(STATE_NORMAL)
                    closeDrawer(GravityCompat.END)
                }
                holder.itemView.setOnLongClickListener {
                    val index = holder.bindingAdapterPosition
                    val quickSearch = mQuickSearchList[index]
                    val popupMenu = PopupMenu(requireContext(), holder.option)
                    popupMenu.inflate(R.menu.quicksearch_option)
                    popupMenu.show()
                    popupMenu.setOnMenuItemClickListener(
                        object : PopupMenu.OnMenuItemClickListener {
                            override fun onMenuItemClick(item: MenuItem): Boolean {
                                if (item.itemId == R.id.menu_qs_remove) {
                                    AlertDialog.Builder(requireContext())
                                        .setTitle(R.string.delete_quick_search_title)
                                        .setMessage(getString(R.string.delete_quick_search_message, quickSearch.name))
                                        .setPositiveButton(R.string.delete) { _: DialogInterface?, _: Int ->
                                            mQuickSearchList.removeAt(index)
                                            notifyItemRemoved(index)
                                            updateDrawerView(true)
                                            lifecycleScope.launchIO {
                                                // DB Actions
                                                EhDB.deleteQuickSearch(quickSearch)
                                            }
                                        }
                                        .setNegativeButton(android.R.string.cancel, null)
                                        .show()
                                    return true
                                }
                                return false
                            }
                        },
                    )
                    return@setOnLongClickListener true
                }
            } else {
                val keywords = intArrayOf(15, 13, 12, 11)
                holder.itemView.setOnClickListener {
                    if (null == mHelper) {
                        return@setOnClickListener
                    }
                    val keyword = keywords[holder.bindingAdapterPosition].toString()
                    Settings.putDefaultTopList(keyword)
                    mUrlBuilder.keyword = keyword
                    onUpdateUrlBuilder()
                    mHelper!!.refresh()
                    setState(STATE_NORMAL)
                    closeDrawer(GravityCompat.END)
                }
            }
            return holder
        }

        override fun onBindViewHolder(holder: QsDrawerHolder, position: Int) {
            if (!mIsTopList) {
                holder.key.text = mQuickSearchList[position].name
            } else {
                val toplists = intArrayOf(
                    R.string.toplist_yesterday,
                    R.string.toplist_pastmonth,
                    R.string.toplist_pastyear,
                    R.string.toplist_alltime,
                )
                holder.key.text = getString(toplists[position])
                holder.option.visibility = View.GONE
            }
        }

        override fun getItemId(position: Int): Long = if (mIsTopList) position.toLong() else mQuickSearchList[position].id!!

        override fun getItemCount(): Int = if (mIsTopList) 4 else mQuickSearchList.size
    }

    private abstract inner class UrlSuggestion : Suggestion() {
        override fun getText(textView: TextView): CharSequence? = if (textView.id == android.R.id.text1) {
            val bookImage =
                AppCompatResources.getDrawable(textView.context, R.drawable.v_book_open_x24)
            val ssb = SpannableStringBuilder("    ")
            ssb.append(getString(R.string.gallery_list_search_bar_open_gallery))
            val imageSize = (textView.textSize * 1.25).toInt()
            if (bookImage != null) {
                bookImage.setBounds(0, 0, imageSize, imageSize)
                ssb.setSpan(ImageSpan(bookImage), 1, 2, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            ssb
        } else {
            null
        }

        override fun onClick() {
            startScene(createAnnouncer())
            if (mState == STATE_SIMPLE_SEARCH) {
                setState(STATE_NORMAL)
            } else if (mState == STATE_SEARCH_SHOW_LIST) {
                setState(STATE_SEARCH)
            }
        }

        abstract fun createAnnouncer(): Announcer
    }

    private inner class GalleryDetailUrlSuggestion(
        private val mGid: Long,
        private val mToken: String,
    ) : UrlSuggestion() {
        override fun createAnnouncer(): Announcer {
            val args = Bundle()
            args.putString(GalleryDetailScene.KEY_ACTION, GalleryDetailScene.ACTION_GID_TOKEN)
            args.putLong(GalleryDetailScene.KEY_GID, mGid)
            args.putString(GalleryDetailScene.KEY_TOKEN, mToken)
            return Announcer(GalleryDetailScene::class.java).setArgs(args)
        }
    }

    private inner class GalleryPageUrlSuggestion(
        private val mGid: Long,
        private val mPToken: String,
        private val mPage: Int,
    ) : UrlSuggestion() {
        override fun createAnnouncer(): Announcer {
            val args = Bundle()
            args.putString(ProgressScene.KEY_ACTION, ProgressScene.ACTION_GALLERY_TOKEN)
            args.putLong(ProgressScene.KEY_GID, mGid)
            args.putString(ProgressScene.KEY_PTOKEN, mPToken)
            args.putInt(ProgressScene.KEY_PAGE, mPage)
            return Announcer(ProgressScene::class.java).setArgs(args)
        }
    }

    private inner class GalleryListAdapter(
        inflater: LayoutInflater,
        resources: Resources,
        recyclerView: RecyclerView,
        type: Int,
    ) : GalleryAdapter(inflater, resources, recyclerView, type, true) {
        override fun getItemCount(): Int = mHelper?.size() ?: 0

        override fun onItemClick(view: View, position: Int) {
            this@GalleryListScene.onItemClick(view, position)
        }

        override fun onItemLongClick(view: View, position: Int): Boolean = this@GalleryListScene.onItemLongClick(position)

        override fun getDataAt(position: Int): GalleryInfo? = mHelper?.getDataAtEx(position)
    }

    private inner class GalleryListHelper : GalleryInfoContentHelper() {
        override fun getPageData(
            taskId: Int,
            type: Int,
            page: Int,
            index: String?,
            isNext: Boolean,
        ) {
            val activity = mainActivity
            if (null == activity || null == mHelper) {
                return
            }
            if (mIsTopList) {
                mUrlBuilder.setJumpTo(page.toString())
            } else {
                mUrlBuilder.setIndex(index, isNext)
                mUrlBuilder.setJumpTo(jumpTo)
            }
            val url = mUrlBuilder.build()
            val request = EhRequest()
            request.setMethod(EhClient.METHOD_GET_GALLERY_LIST)
            request.setCallback(
                GetGalleryListListener(context, taskId),
            )
            request.setArgs(url)
            request.enqueue(this@GalleryListScene)
        }

        override val context
            get() = requireContext()

        @SuppressLint("NotifyDataSetChanged")
        override fun notifyDataSetChanged() {
            mAdapter?.notifyDataSetChanged()
        }

        override fun notifyItemRangeInserted(positionStart: Int, itemCount: Int) {
            mAdapter?.notifyItemRangeInserted(positionStart, itemCount)
        }

        override fun onShowView(hiddenView: View, shownView: View) {
            mSearchBarMover?.showSearchBar()
            showActionFab()
        }

        override fun isDuplicate(d1: GalleryInfo?, d2: GalleryInfo?): Boolean = d1?.gid == d2?.gid && d1 != null && d2 != null

        override fun onScrollToPosition(position: Int) {
            if (0 == position) {
                mSearchBarMover?.showSearchBar()
                showActionFab()
            }
        }
    }

    private inner class GalleryListQSItemTouchHelperCallback(private val mAdapter: QsDrawerAdapter) : ItemTouchHelper.Callback() {
        override fun getMovementFlags(
            recyclerView: RecyclerView,
            viewHolder: RecyclerView.ViewHolder,
        ): Int = makeMovementFlags(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN,
            0,
        )

        override fun isLongPressDragEnabled(): Boolean = false

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
            val item = mQuickSearchList.removeAt(fromPosition)
            mQuickSearchList.add(toPosition, item)
            mAdapter.notifyItemMoved(fromPosition, toPosition)
            lifecycleScope.launchIO {
                // DB Actions
                EhDB.moveQuickSearch(fromPosition, toPosition)
            }
            return true
        }

        override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {}
    }

    companion object {
        const val KEY_ACTION = "action"
        const val ACTION_HOMEPAGE = "action_homepage"
        const val ACTION_SUBSCRIPTION = "action_subscription"
        const val ACTION_WHATS_HOT = "action_whats_hot"
        const val ACTION_TOP_LIST = "action_top_list"
        const val ACTION_LIST_URL_BUILDER = "action_list_url_builder"
        const val KEY_LIST_URL_BUILDER = "list_url_builder"
        const val KEY_HAS_FIRST_REFRESH = "has_first_refresh"
        const val KEY_STATE = "state"
        private const val BACK_PRESSED_INTERVAL = 2000
        private const val STATE_NORMAL = 0
        private const val STATE_SIMPLE_SEARCH = 1
        private const val STATE_SEARCH = 2
        private const val STATE_SEARCH_SHOW_LIST = 3
        private const val ANIMATE_TIME = 300L

        private fun getSuitableTitleForUrlBuilder(
            resources: Resources,
            urlBuilder: ListUrlBuilder,
            appName: Boolean,
        ): String? {
            val keyword = urlBuilder.keyword
            val category = urlBuilder.category
            return if (ListUrlBuilder.MODE_NORMAL == urlBuilder.mode &&
                EhUtils.NONE == category &&
                TextUtils.isEmpty(keyword) &&
                urlBuilder.advanceSearch == -1 &&
                urlBuilder.minRating == -1 &&
                urlBuilder.pageFrom == -1 &&
                urlBuilder.pageTo == -1
            ) {
                resources.getString(if (appName) R.string.app_name else R.string.homepage)
            } else if (MODE_SUBSCRIPTION == urlBuilder.mode &&
                EhUtils.NONE == category &&
                TextUtils.isEmpty(keyword) &&
                urlBuilder.advanceSearch == -1 &&
                urlBuilder.minRating == -1 &&
                urlBuilder.pageFrom == -1 &&
                urlBuilder.pageTo == -1
            ) {
                resources.getString(R.string.subscription)
            } else if (MODE_WHATS_HOT == urlBuilder.mode) {
                resources.getString(R.string.whats_hot)
            } else if (MODE_TOPLIST == urlBuilder.mode) {
                when (urlBuilder.keyword) {
                    "11" -> resources.getString(R.string.toplist_alltime)
                    "12" -> resources.getString(R.string.toplist_pastyear)
                    "13" -> resources.getString(R.string.toplist_pastmonth)
                    "15" -> resources.getString(R.string.toplist_yesterday)
                    else -> null
                }
            } else if (!TextUtils.isEmpty(keyword)) {
                keyword
            } else if (MathUtils.hammingWeight(category) == 1) {
                EhUtils.getCategory(category)
            } else {
                null
            }
        }

        @JvmStatic
        fun startScene(scene: SceneFragment, lub: ListUrlBuilder?) {
            scene.startScene(getStartAnnouncer(lub))
        }

        fun getStartAnnouncer(lub: ListUrlBuilder?): Announcer {
            val args = Bundle()
            args.putString(KEY_ACTION, ACTION_LIST_URL_BUILDER)
            args.putParcelable(KEY_LIST_URL_BUILDER, lub)
            return Announcer(GalleryListScene::class.java).setArgs(args)
        }
    }
}
