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

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.Dialog
import android.app.assist.AssistContent
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.text.Html
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.View.OnLongClickListener
import android.view.ViewAnimationUtils
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.RatingBar
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
import androidx.annotation.IntDef
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.content.res.AppCompatResources
import androidx.appcompat.widget.PopupMenu
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import androidx.core.view.ViewCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentTransaction
import androidx.lifecycle.lifecycleScope
import androidx.transition.TransitionInflater
import coil.Coil.imageLoader
import com.google.android.material.progressindicator.CircularProgressIndicator
import com.google.android.material.snackbar.Snackbar
import com.hippo.app.CheckBoxDialogBuilder
import com.hippo.app.EditTextDialogBuilder
import com.hippo.ehviewer.EhApplication
import com.hippo.ehviewer.EhApplication.Companion.galleryDetailCache
import com.hippo.ehviewer.EhDB
import com.hippo.ehviewer.R
import com.hippo.ehviewer.Settings
import com.hippo.ehviewer.UrlOpener
import com.hippo.ehviewer.client.EhCacheKeyFactory
import com.hippo.ehviewer.client.EhClient
import com.hippo.ehviewer.client.EhCookieStore
import com.hippo.ehviewer.client.EhFilter
import com.hippo.ehviewer.client.EhRequest
import com.hippo.ehviewer.client.EhTagDatabase
import com.hippo.ehviewer.client.EhTagDatabase.isTranslatable
import com.hippo.ehviewer.client.EhTagDatabase.namespaceToPrefix
import com.hippo.ehviewer.client.EhUrl
import com.hippo.ehviewer.client.EhUtils
import com.hippo.ehviewer.client.data.GalleryComment
import com.hippo.ehviewer.client.data.GalleryCommentList
import com.hippo.ehviewer.client.data.GalleryDetail
import com.hippo.ehviewer.client.data.GalleryInfo
import com.hippo.ehviewer.client.data.GalleryTagGroup
import com.hippo.ehviewer.client.data.ListUrlBuilder
import com.hippo.ehviewer.client.exception.EhException
import com.hippo.ehviewer.client.parser.ArchiveParser
import com.hippo.ehviewer.client.parser.HomeParser
import com.hippo.ehviewer.client.parser.RateGalleryParser
import com.hippo.ehviewer.client.parser.TorrentParser
import com.hippo.ehviewer.dao.DownloadInfo
import com.hippo.ehviewer.dao.Filter
import com.hippo.ehviewer.download.DownloadManager
import com.hippo.ehviewer.download.DownloadManager.DownloadInfoListener
import com.hippo.ehviewer.spider.SpiderDen
import com.hippo.ehviewer.spider.SpiderQueen
import com.hippo.ehviewer.spider.SpiderQueen.Companion.MODE_READ
import com.hippo.ehviewer.ui.CommonOperations
import com.hippo.ehviewer.ui.GalleryActivity
import com.hippo.ehviewer.widget.GalleryRatingBar
import com.hippo.ehviewer.widget.GalleryRatingBar.OnUserRateListener
import com.hippo.scene.Announcer
import com.hippo.scene.TransitionHelper
import com.hippo.text.URLImageGetter
import com.hippo.util.AppHelper
import com.hippo.util.ExceptionUtils
import com.hippo.util.ReadableTime
import com.hippo.util.addTextToClipboard
import com.hippo.util.getParcelableCompat
import com.hippo.util.launchIO
import com.hippo.util.withUIContext
import com.hippo.view.ViewTransition
import com.hippo.widget.AutoWrapLayout
import com.hippo.widget.LoadImageView
import com.hippo.widget.ObservedTextView
import com.hippo.widget.SimpleGridAutoSpanLayout
import com.hippo.yorozuya.FileUtils
import com.hippo.yorozuya.IntIdGenerator
import com.hippo.yorozuya.SimpleHandler
import com.hippo.yorozuya.ViewUtils
import com.hippo.yorozuya.collect.IntList
import okhttp3.HttpUrl.Companion.toHttpUrl
import rikka.core.res.resolveBoolean
import rikka.core.res.resolveColor
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.roundToInt
import android.app.DownloadManager as AndroidDownloadManager

class GalleryDetailScene :
    BaseScene(),
    View.OnClickListener,
    DownloadInfoListener,
    OnLongClickListener {
    private val mDownloadManager = DownloadManager
    private var mTip: TextView? = null
    private var mViewTransition: ViewTransition? = null

    // Header
    private var mHeader: FrameLayout? = null
    private var mColorBg: View? = null
    private var mThumb: LoadImageView? = null
    private var mTitle: TextView? = null
    private var mUploader: TextView? = null
    private var mCategory: TextView? = null
    private var mBackAction: ImageView? = null
    private var mOtherActions: ImageView? = null
    private var mActionGroup: ViewGroup? = null
    private var mDownload: TextView? = null
    private var mRead: TextView? = null

    // Below header
    private var mBelowHeader: View? = null

    // Info
    private var mInfo: View? = null
    private var mLanguage: TextView? = null
    private var mPages: TextView? = null
    private var mSize: TextView? = null
    private var mPosted: TextView? = null
    private var mFavoredTimes: TextView? = null
    private var mNewerVersion: TextView? = null

    // Actions
    private var mActions: View? = null
    private var mRatingText: TextView? = null
    private var mRating: RatingBar? = null
    private var mHeartGroup: View? = null
    private var mHeart: TextView? = null
    private var mHeartOutline: TextView? = null
    private var mTorrent: TextView? = null
    private var mArchive: TextView? = null
    private var mShare: TextView? = null
    private var mRate: View? = null
    private var mSimilar: TextView? = null
    private var mSearchCover: TextView? = null

    // Tags
    private var mTags: LinearLayout? = null
    private var mNoTags: TextView? = null

    // Comments
    private var mComments: LinearLayout? = null
    private var mCommentsText: TextView? = null

    // Previews
    private var mPreviews: View? = null
    private var mGridLayout: SimpleGridAutoSpanLayout? = null
    private var mPreviewText: TextView? = null

    // Progress
    private var mProgress: View? = null
    private var mViewTransition2: ViewTransition? = null
    private var mPopupMenu: PopupMenu? = null
    private var mDownloadState = 0
    private var mAction: String? = null
    private var mGalleryInfo: GalleryInfo? = null
    private var mGid: Long = 0
    private var mToken: String? = null
    private var mPage = 0
    private var mGalleryDetail: GalleryDetail? = null
    private var mRequestId = IntIdGenerator.INVALID_ID
    private var mTorrentList: List<TorrentParser.Result>? = null
    private var requestStoragePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { result: Boolean ->
        if (result && mGalleryDetail != null) {
            val helper = TorrentListDialogHelper()
            val dialog = AlertDialog.Builder(requireActivity())
                .setTitle(R.string.torrents)
                .setView(R.layout.dialog_torrent_list)
                .setOnDismissListener(helper)
                .show()
            helper.setDialog(dialog, mGalleryDetail!!.torrentUrl)
        }
    }
    private var mArchiveFormParamOr: String? = null
    private var mArchiveList: List<ArchiveParser.Archive>? = null
    private var mCurrentFunds: HomeParser.Funds? = null

    @State
    private var mState = STATE_INIT
    private var mModifyingFavorites = false

    @StringRes
    private fun getRatingText(rating: Float): Int {
        return when ((rating * 2).roundToInt()) {
            0 -> R.string.rating0
            1 -> R.string.rating1
            2 -> R.string.rating2
            3 -> R.string.rating3
            4 -> R.string.rating4
            5 -> R.string.rating5
            6 -> R.string.rating6
            7 -> R.string.rating7
            8 -> R.string.rating8
            9 -> R.string.rating9
            10 -> R.string.rating10
            else -> R.string.rating_none
        }
    }

    private fun handleArgs(args: Bundle?) {
        val action = args?.getString(KEY_ACTION) ?: return
        mAction = action
        if (ACTION_GALLERY_INFO == action) {
            mGalleryInfo = args.getParcelableCompat(KEY_GALLERY_INFO)
            // Add history
            // DB Actions
            mGalleryInfo?.let { EhDB.putHistoryInfo(it) }
        } else if (ACTION_GID_TOKEN == action) {
            mGid = args.getLong(KEY_GID)
            mToken = args.getString(KEY_TOKEN)
            mPage = args.getInt(KEY_PAGE)
        }
    }

    private val galleryDetailUrl: String?
        get() {
            val gid: Long
            val token: String?
            if (mGalleryDetail != null) {
                gid = mGalleryDetail!!.gid
                token = mGalleryDetail!!.token
            } else if (mGalleryInfo != null) {
                gid = mGalleryInfo!!.gid
                token = mGalleryInfo!!.token
            } else if (ACTION_GID_TOKEN == mAction) {
                gid = mGid
                token = mToken
            } else {
                return null
            }
            return EhUrl.getGalleryDetailUrl(gid, token, 0, false)
        }

    // -1 for error
    private val gid: Long
        get() = if (mGalleryDetail != null) {
            mGalleryDetail!!.gid
        } else if (mGalleryInfo != null) {
            mGalleryInfo!!.gid
        } else if (ACTION_GID_TOKEN == mAction) {
            mGid
        } else {
            -1
        }

    private val uploader: String?
        get() = if (mGalleryDetail != null) {
            mGalleryDetail!!.uploader
        } else if (mGalleryInfo != null) {
            mGalleryInfo!!.uploader
        } else {
            null
        }

    // Judging by the uploader to exclude the cooldown period
    private val disowned: Boolean
        get() = uploader == "(Disowned)"

    // -1 for error
    private val category: Int
        get() = if (mGalleryDetail != null) {
            mGalleryDetail!!.category
        } else if (mGalleryInfo != null) {
            mGalleryInfo!!.category
        } else {
            -1
        }

    private val galleryInfo: GalleryInfo?
        get() = if (null != mGalleryDetail) {
            mGalleryDetail
        } else if (null != mGalleryInfo) {
            mGalleryInfo
        } else {
            null
        }

    override var needWhiteStatusBar = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) {
            onInit()
        } else {
            onRestore(savedInstanceState)
        }
    }

    override fun onResume() {
        super.onResume()
        mRead ?: return
        mGalleryInfo?.let {
            // Other Actions
            viewLifecycleOwner.lifecycleScope.launchIO {
                runCatching {
                    val queen = SpiderQueen.obtainSpiderQueen(it, MODE_READ)
                    val startPage = queen.awaitStartPage()
                    SpiderQueen.releaseSpiderQueen(queen, MODE_READ)
                    withUIContext {
                        mRead!!.text = if (startPage == 0) {
                            getString(R.string.read)
                        } else {
                            getString(R.string.read_from, startPage + 1)
                        }
                    }
                }.onFailure {
                    it.printStackTrace()
                }
            }
        }
    }

    private fun onInit() {
        handleArgs(arguments)
    }

    private fun onRestore(savedInstanceState: Bundle) {
        mAction = savedInstanceState.getString(KEY_ACTION)
        mGalleryInfo = savedInstanceState.getParcelableCompat(KEY_GALLERY_INFO)
        mGid = savedInstanceState.getLong(KEY_GID)
        mToken = savedInstanceState.getString(KEY_TOKEN)
        mGalleryDetail = savedInstanceState.getParcelableCompat(KEY_GALLERY_DETAIL)
        mRequestId = savedInstanceState.getInt(KEY_REQUEST_ID)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        if (mAction != null) {
            outState.putString(KEY_ACTION, mAction)
        }
        if (mGalleryInfo != null) {
            outState.putParcelable(KEY_GALLERY_INFO, mGalleryInfo)
        }
        outState.putLong(KEY_GID, mGid)
        if (mToken != null) {
            outState.putString(KEY_TOKEN, mAction)
        }
        if (mGalleryDetail != null) {
            outState.putParcelable(KEY_GALLERY_DETAIL, mGalleryDetail)
        }
        outState.putInt(KEY_REQUEST_ID, mRequestId)
    }

    private fun ensurePopMenu() {
        if (mPopupMenu != null || mOtherActions == null) {
            return
        }
        val popup = PopupMenu(requireContext(), mOtherActions!! as View)
        mPopupMenu = popup
        popup.menuInflater.inflate(R.menu.scene_gallery_detail, popup.menu)
        popup.setOnMenuItemClickListener(
            object : PopupMenu.OnMenuItemClickListener {
                override fun onMenuItemClick(item: MenuItem): Boolean {
                    when (item.itemId) {
                        R.id.action_open_in_other_app -> {
                            val url = galleryDetailUrl
                            val activity: Activity? = mainActivity
                            if (null != url && null != activity) {
                                UrlOpener.openUrl(activity, url, false)
                            }
                            return true
                        }
                        R.id.action_refresh -> {
                            if (mState != STATE_REFRESH && mState != STATE_REFRESH_HEADER) {
                                adjustViewVisibility(STATE_REFRESH, true)
                                request()
                            }
                            return true
                        }
                        R.id.action_add_tag -> {
                            if (mGalleryDetail == null) {
                                return false
                            }
                            if (mGalleryDetail!!.apiUid < 0) {
                                showTip(R.string.error_please_login_first, LENGTH_LONG)
                                return false
                            }
                            val builder =
                                EditTextDialogBuilder(requireContext(), "", getString(R.string.action_add_tag_tip))
                            builder.setPositiveButton(android.R.string.ok, null)
                            val dialog = builder.setTitle(R.string.action_add_tag)
                                .show()
                            dialog.getButton(DialogInterface.BUTTON_POSITIVE)
                                .setOnClickListener {
                                    voteTag(builder.text.trim { it <= ' ' }, 1)
                                    dialog.dismiss()
                                }
                            return true
                        }
                    }
                    return false
                }
            },
        )
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        // Get download state
        val gid = gid
        mDownloadState = if (gid != -1L) {
            mDownloadManager.getDownloadState(gid)
        } else {
            DownloadInfo.STATE_INVALID
        }
        val view = inflater.inflate(R.layout.scene_gallery_detail, container, false)
        val main = ViewUtils.`$$`(view, R.id.main) as ViewGroup
        val mainView = ViewUtils.`$$`(main, R.id.scroll_view) as ScrollView
        mainView.setOnScrollChangeListener { _, _, scrollY, _, _ ->
            if (mActionGroup != null && mHeader != null) {
                setLightStatusBar(
                    (
                        mActionGroup!!.y - mHeader!!.findViewById<View>(R.id.header_content)
                            .paddingTop / 2f
                        ).toInt() < scrollY,
                )
            }
        }
        val progressView = ViewUtils.`$$`(main, R.id.progress_view)
        mTip = ViewUtils.`$$`(main, R.id.tip) as TextView
        mViewTransition = ViewTransition(mainView, progressView, mTip)
        val drawable = AppCompatResources.getDrawable(requireContext(), R.drawable.big_sad_pandroid)
        drawable!!.setBounds(0, 0, drawable.intrinsicWidth, drawable.intrinsicHeight)
        mTip!!.setCompoundDrawables(null, drawable, null, null)
        mTip!!.setOnClickListener(this)
        mHeader = ViewUtils.`$$`(mainView, R.id.header) as FrameLayout
        mColorBg = ViewUtils.`$$`(mHeader, R.id.color_bg)
        mThumb = ViewUtils.`$$`(mHeader, R.id.thumb) as LoadImageView
        mTitle = ViewUtils.`$$`(mHeader, R.id.title) as TextView
        mUploader = ViewUtils.`$$`(mHeader, R.id.uploader) as TextView
        mCategory = ViewUtils.`$$`(mHeader, R.id.category) as TextView
        mBackAction = ViewUtils.`$$`(mHeader, R.id.back_action) as ImageView
        mOtherActions = ViewUtils.`$$`(mHeader, R.id.other_actions) as ImageView
        mActionGroup = ViewUtils.`$$`(mHeader, R.id.action_card) as ViewGroup
        mDownload = ViewUtils.`$$`(mActionGroup, R.id.download) as TextView
        mRead = ViewUtils.`$$`(mActionGroup, R.id.read) as TextView
        mUploader!!.setOnClickListener(this)
        mCategory!!.setOnClickListener(this)
        mBackAction!!.setOnClickListener(this)
        mOtherActions!!.setOnClickListener(this)
        mDownload!!.setOnClickListener(this)
        mDownload!!.setOnLongClickListener(this)
        mRead!!.setOnClickListener(this)
        mUploader!!.setOnLongClickListener(this)
        mBelowHeader = mainView.findViewById(R.id.below_header)
        val belowHeader = mBelowHeader
        mInfo = ViewUtils.`$$`(belowHeader, R.id.info)
        mLanguage = ViewUtils.`$$`(mInfo, R.id.language) as TextView
        mPages = ViewUtils.`$$`(mInfo, R.id.pages) as TextView
        mSize = ViewUtils.`$$`(mInfo, R.id.size) as TextView
        mPosted = ViewUtils.`$$`(mInfo, R.id.posted) as TextView
        mFavoredTimes = ViewUtils.`$$`(mInfo, R.id.favoredTimes) as TextView
        mInfo!!.setOnClickListener(this)
        mActions = ViewUtils.`$$`(belowHeader, R.id.actions)
        mNewerVersion = ViewUtils.`$$`(mActions, R.id.newerVersion) as TextView
        mRatingText = ViewUtils.`$$`(mActions, R.id.rating_text) as TextView
        mRating = ViewUtils.`$$`(mActions, R.id.rating) as RatingBar
        mHeartGroup = ViewUtils.`$$`(mActions, R.id.heart_group)
        mHeart = ViewUtils.`$$`(mHeartGroup, R.id.heart) as TextView
        mHeartOutline = ViewUtils.`$$`(mHeartGroup, R.id.heart_outline) as TextView
        mTorrent = ViewUtils.`$$`(mActions, R.id.torrent) as TextView
        mArchive = ViewUtils.`$$`(mActions, R.id.archive) as TextView
        mShare = ViewUtils.`$$`(mActions, R.id.share) as TextView
        mRate = ViewUtils.`$$`(mActions, R.id.rate)
        mSimilar = ViewUtils.`$$`(mActions, R.id.similar) as TextView
        mSearchCover = ViewUtils.`$$`(mActions, R.id.search_cover) as TextView
        mNewerVersion!!.setOnClickListener(this)
        mHeartGroup!!.setOnClickListener(this)
        mHeartGroup!!.setOnLongClickListener(this)
        mTorrent!!.setOnClickListener(this)
        mArchive!!.setOnClickListener(this)
        mShare!!.setOnClickListener(this)
        mRate!!.setOnClickListener(this)
        mSimilar!!.setOnClickListener(this)
        mSearchCover!!.setOnClickListener(this)
        ensureActionDrawable()
        mTags = ViewUtils.`$$`(belowHeader, R.id.tags) as LinearLayout
        mNoTags = ViewUtils.`$$`(mTags, R.id.no_tags) as TextView
        mComments = ViewUtils.`$$`(belowHeader, R.id.comments) as LinearLayout
        if (Settings.showComments) {
            mCommentsText = ViewUtils.`$$`(mComments, R.id.comments_text) as TextView
            mComments!!.setOnClickListener(this)
        } else {
            mComments!!.visibility = View.GONE
        }
        mPreviews = ViewUtils.`$$`(belowHeader, R.id.previews)
        mGridLayout = ViewUtils.`$$`(mPreviews, R.id.grid_layout) as SimpleGridAutoSpanLayout
        mPreviewText = ViewUtils.`$$`(mPreviews, R.id.preview_text) as TextView
        mPreviews!!.setOnClickListener(this)
        mProgress = ViewUtils.`$$`(mainView, R.id.progress)
        mViewTransition2 = ViewTransition(mBelowHeader, mProgress)
        if (prepareData()) {
            if (mGalleryDetail != null) {
                bindViewSecond()
                setTransitionName()
                adjustViewVisibility(STATE_NORMAL, false)
            } else if (mGalleryInfo != null) {
                bindViewFirst()
                setTransitionName()
                adjustViewVisibility(STATE_REFRESH_HEADER, false)
            } else {
                adjustViewVisibility(STATE_REFRESH, false)
            }
        } else {
            mTip!!.setText(R.string.error_cannot_find_gallery)
            adjustViewVisibility(STATE_FAILED, false)
        }
        mDownloadManager.addDownloadInfoListener(this)
        return view
    }

    override fun onDestroyView() {
        super.onDestroyView()
        mDownloadManager.removeDownloadInfoListener(this)
        mTip = null
        mViewTransition = null
        mHeader = null
        mColorBg = null
        mThumb = null
        mTitle = null
        mUploader = null
        mCategory = null
        mBackAction = null
        mOtherActions = null
        mActionGroup = null
        mDownload = null
        mRead = null
        mBelowHeader = null
        mInfo = null
        mLanguage = null
        mPages = null
        mSize = null
        mPosted = null
        mFavoredTimes = null
        mActions = null
        mNewerVersion = null
        mRatingText = null
        mRating = null
        mHeartGroup = null
        mHeart = null
        mHeartOutline = null
        mTorrent = null
        mArchive = null
        mShare = null
        mRate = null
        mSimilar = null
        mSearchCover = null
        mTags = null
        mNoTags = null
        mComments = null
        mCommentsText = null
        mPreviews = null
        mGridLayout = null
        mPreviewText = null
        mProgress = null
        mViewTransition2 = null
        mPopupMenu = null
    }

    private fun prepareData(): Boolean {
        if (mGalleryDetail != null) {
            return true
        }
        val gid = gid
        if (gid == -1L) {
            return false
        }
        // Get from cache
        mGalleryDetail = galleryDetailCache[gid]
        if (mGalleryDetail != null) {
            return true
        }
        val application = requireContext().applicationContext as EhApplication
        return if (application.containGlobalStuff(mRequestId)) {
            // request exist
            true
        } else {
            request()
        }
    }

    private fun request(): Boolean {
        val context = context
        val activity = mainActivity
        val url = galleryDetailUrl
        if (null == context || null == activity || null == url) {
            return false
        }
        val callback: EhClient.Callback<*> = GetGalleryDetailListener(context)
        mRequestId = (context.applicationContext as EhApplication).putGlobalStuff(callback)
        val request = EhRequest()
            .setMethod(EhClient.METHOD_GET_GALLERY_DETAIL)
            .setArgs(url)
            .setCallback(callback)
        request.enqueue(this)
        return true
    }

    private fun setActionDrawable(text: TextView?, @DrawableRes resId: Int) {
        text ?: return
        val drawable = AppCompatResources.getDrawable(text.context, resId) ?: return
        drawable.setBounds(0, 0, drawable.intrinsicWidth, drawable.intrinsicHeight)
        text.setCompoundDrawables(null, drawable, null, null)
    }

    private fun ensureActionDrawable() {
        setActionDrawable(mHeart, R.drawable.v_heart_primary_x48)
        setActionDrawable(mHeartOutline, R.drawable.v_heart_outline_primary_x48)
        setActionDrawable(mTorrent, R.drawable.v_utorrent_primary_x48)
        setActionDrawable(mArchive, R.drawable.v_archive_primary_x48)
        setActionDrawable(mShare, R.drawable.v_share_primary_x48)
        setActionDrawable(mSimilar, R.drawable.v_similar_primary_x48)
        setActionDrawable(mSearchCover, R.drawable.v_file_find_primary_x48)
    }

    private fun createCircularReveal(): Boolean {
        val context = context
        if (null == context || null == mColorBg) {
            return false
        }
        val w = mColorBg!!.width
        val h = mColorBg!!.height
        return if (ViewCompat.isAttachedToWindow(mColorBg!!) && w != 0 && h != 0) {
            val resources = context.resources
            val keylineMargin = resources.getDimensionPixelSize(R.dimen.keyline_margin)
            val thumbWidth = resources.getDimensionPixelSize(R.dimen.gallery_detail_thumb_width)
            val thumbHeight = resources.getDimensionPixelSize(R.dimen.gallery_detail_thumb_height)
            val x = thumbWidth / 2 + keylineMargin
            val y = thumbHeight / 2 + keylineMargin
            val radiusX = max(abs(x), abs(w - x)).toDouble()
            val radiusY = max(abs(y), abs(h - y)).toDouble()
            val radius = hypot(radiusX, radiusY).toFloat()
            ViewAnimationUtils.createCircularReveal(mColorBg!!, x, y, 0f, radius).setDuration(300).start()
            true
        } else {
            false
        }
    }

    private fun adjustViewVisibility(state: Int, animation: Boolean) {
        if (state == mState || mViewTransition == null || mViewTransition2 == null) {
            return
        }
        val oldState = mState
        mState = state
        val doAnimation = !TRANSITION_ANIMATION_DISABLED && animation
        when (state) {
            STATE_NORMAL -> {
                setLightStatusBar(false)
                // Show mMainView
                mViewTransition!!.showView(0, doAnimation)
                // Show mBelowHeader
                mViewTransition2!!.showView(0, doAnimation)
            }
            STATE_REFRESH -> {
                setLightStatusBar(true)
                // Show mProgressView
                mViewTransition!!.showView(1, doAnimation)
            }
            STATE_REFRESH_HEADER -> {
                setLightStatusBar(false)
                // Show mMainView
                mViewTransition!!.showView(0, doAnimation)
                // Show mProgress
                mViewTransition2!!.showView(1, doAnimation)
            }
            STATE_INIT, STATE_FAILED -> {
                setLightStatusBar(true)
                // Show mFailedView
                mViewTransition!!.showView(2, doAnimation)
            }
        }
        if ((oldState == STATE_INIT || oldState == STATE_FAILED || oldState == STATE_REFRESH) &&
            (state == STATE_NORMAL || state == STATE_REFRESH_HEADER) && theme.resolveBoolean(androidx.appcompat.R.attr.isLightTheme, false)
        ) {
            if (!createCircularReveal()) {
                SimpleHandler.getInstance().post(this::createCircularReveal)
            }
        }
    }

    private fun bindViewFirst() {
        if (mGalleryDetail != null || mThumb == null || mTitle == null || mUploader == null || mCategory == null) {
            return
        }
        if (ACTION_GALLERY_INFO == mAction && mGalleryInfo != null) {
            val gi: GalleryInfo = mGalleryInfo!!
            mThumb!!.load(EhCacheKeyFactory.getThumbKey(gi.gid), gi.thumb!!)
            mTitle!!.text = EhUtils.getSuitableTitle(gi)
            mUploader!!.text = gi.uploader
            mUploader!!.alpha = if (gi.disowned) .5f else 1f
            mCategory!!.text = EhUtils.getCategory(gi.category)
            mCategory!!.setTextColor(EhUtils.getCategoryColor(gi.category))
            updateDownloadText()
        }
    }

    private fun updateFavoriteDrawable() {
        val gd = mGalleryDetail ?: return
        if (mHeart == null || mHeartOutline == null) {
            return
        }
        // DB Actions
        if (gd.isFavorited || EhDB.containLocalFavorites(gd.gid)) {
            mHeart!!.visibility = View.VISIBLE
            if (gd.favoriteName == null) {
                mHeart!!.setText(R.string.local_favorites)
            } else {
                mHeart!!.text = gd.favoriteName
            }
            mHeartOutline!!.visibility = View.GONE
        } else {
            mHeart!!.visibility = View.GONE
            mHeartOutline!!.visibility = View.VISIBLE
        }
    }

    private fun bindViewSecond() {
        context ?: return
        val gd = mGalleryDetail ?: return
        if (mPage != 0) {
            Snackbar.make(
                requireActivity().findViewById(R.id.snackbar),
                getString(R.string.read_from, mPage + 1),
                Snackbar.LENGTH_LONG,
            )
                .setAction(R.string.read) {
                    val intent = Intent(context, GalleryActivity::class.java)
                    intent.action = GalleryActivity.ACTION_EH
                    intent.putExtra(GalleryActivity.KEY_GALLERY_INFO, mGalleryDetail)
                    intent.putExtra(GalleryActivity.KEY_PAGE, mPage)
                    startActivity(intent)
                }
                .show()
        }
        if (mThumb == null || mTitle == null || mUploader == null || mCategory == null || mLanguage == null || mPages == null || mSize == null || mPosted == null || mFavoredTimes == null || mRatingText == null || mRating == null || mTorrent == null || mNewerVersion == null) {
            return
        }
        val resources = resources
        mThumb!!.load(EhCacheKeyFactory.getThumbKey(gd.gid), gd.thumb!!, false)
        mTitle!!.text = EhUtils.getSuitableTitle(gd)
        mUploader!!.text = gd.uploader
        mUploader!!.alpha = if (gd.disowned) .5f else 1f
        mCategory!!.text = EhUtils.getCategory(gd.category)
        mCategory!!.setTextColor(EhUtils.getCategoryColor(gd.category))
        updateDownloadText()
        mLanguage!!.text = gd.language
        mPages!!.text = resources.getQuantityString(
            R.plurals.page_count,
            gd.pages,
            gd.pages,
        )
        mSize!!.text = gd.size
        mPosted!!.text = gd.posted
        mFavoredTimes!!.text = resources.getString(R.string.favored_times, gd.favoriteCount)
        if (gd.newerVersions.size != 0) {
            mNewerVersion!!.visibility = View.VISIBLE
        }
        mRatingText!!.text = getAllRatingText(gd.rating, gd.ratingCount)
        mRating!!.rating = gd.rating
        updateFavoriteDrawable()
        mTorrent!!.text = resources.getString(R.string.torrent_count, gd.torrentCount)
        bindTags(gd.tags)
        bindComments(gd.comments!!.comments)
        bindPreviews(gd)
    }

    private fun bindTags(tagGroups: Array<GalleryTagGroup>?) {
        val context = context
        val inflater = layoutInflater
        if (null == context || null == mTags || null == mNoTags) {
            return
        }
        mTags!!.removeViews(1, mTags!!.childCount - 1)
        if (tagGroups.isNullOrEmpty()) {
            mNoTags!!.visibility = View.VISIBLE
            return
        } else {
            mNoTags!!.visibility = View.GONE
        }
        val ehTags =
            if (Settings.showTagTranslations && isTranslatable(context)) EhTagDatabase else null
        val colorTag = theme.resolveColor(R.attr.tagBackgroundColor)
        val colorName = theme.resolveColor(R.attr.tagGroupBackgroundColor)
        for (tgs in tagGroups) {
            val ll = inflater.inflate(R.layout.gallery_tag_group, mTags, false) as LinearLayout
            ll.orientation = LinearLayout.HORIZONTAL
            mTags!!.addView(ll)
            var readableTagName: String? = null
            if (ehTags != null && ehTags.isInitialized()) {
                readableTagName = ehTags.getTranslation(tag = tgs.groupName)
            }
            val tgName = inflater.inflate(R.layout.item_gallery_tag, ll, false) as TextView
            ll.addView(tgName)
            tgName.text = readableTagName ?: tgs.groupName
            tgName.backgroundTintList = ColorStateList.valueOf(colorName)
            val prefix = namespaceToPrefix(tgs.groupName!!)
            val awl = AutoWrapLayout(context)
            ll.addView(
                awl,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            )
            for (tg in tgs) {
                val tag = inflater.inflate(R.layout.item_gallery_tag, awl, false) as TextView
                awl.addView(tag)
                var tagStr = tg
                while (tagStr.startsWith("_")) {
                    when (tagStr.substring(1, 2)) {
                        "W" -> tag.alpha = 0.5f
                        "U" -> tag.setTextColor(TAG_COLOR_UP.toInt())
                        "D" -> tag.setTextColor(TAG_COLOR_DN.toInt())
                    }
                    tagStr = tagStr.substring(2)
                }
                var readableTag: String? = null
                if (ehTags != null && ehTags.isInitialized()) {
                    readableTag = ehTags.getTranslation(prefix, tagStr)
                }
                tag.text = readableTag ?: tagStr
                tag.backgroundTintList = ColorStateList.valueOf(colorTag)
                tag.setTag(R.id.tag, tgs.groupName + ":" + tagStr)
                tag.setOnClickListener(this)
                tag.setOnLongClickListener(this)
            }
        }
    }

    private fun bindComments(comments: Array<GalleryComment>?) {
        val context = context
        val inflater = layoutInflater
        if (null == context || null == mComments || null == mCommentsText) {
            return
        }
        mComments!!.removeViews(0, mComments!!.childCount - 1)
        val maxShowCount = 2
        if (comments.isNullOrEmpty()) {
            mCommentsText!!.setText(R.string.no_comments)
            return
        } else if (comments.size <= maxShowCount) {
            mCommentsText!!.setText(R.string.no_more_comments)
        } else {
            mCommentsText!!.setText(R.string.more_comment)
        }
        val length = maxShowCount.coerceAtMost(comments.size)
        for (i in 0 until length) {
            val comment = comments[i]
            val v = inflater.inflate(R.layout.item_gallery_comment, mComments, false)
            mComments!!.addView(v, i)
            val user = v.findViewById<TextView>(R.id.user)
            user.text = comment.user
            user.setBackgroundColor(Color.TRANSPARENT)
            val time = v.findViewById<TextView>(R.id.time)
            time.text = ReadableTime.getTimeAgo(comment.time)
            val c = v.findViewById<ObservedTextView>(R.id.comment)
            c.maxLines = 5
            c.text = Html.fromHtml(
                comment.comment,
                Html.FROM_HTML_MODE_LEGACY,
                URLImageGetter(c),
                null,
            )
            v.setBackgroundColor(Color.TRANSPARENT)
        }
    }

    @SuppressLint("SetTextI18n")
    private fun bindPreviews(gd: GalleryDetail) {
        val inflater = layoutInflater
        val resources = resourcesOrNull
        if (null == resources || null == mGridLayout || null == mPreviewText) {
            return
        }
        mGridLayout!!.removeAllViews()
        val previewSet = gd.previewSet
        val previewNum = Settings.previewNum
        if (gd.previewPages <= 0 || previewSet == null || previewSet.size() == 0) {
            mPreviewText!!.setText(R.string.no_previews)
            return
        } else if (gd.previewPages == 1 && previewSet.size() <= previewNum) {
            mPreviewText!!.setText(R.string.no_more_previews)
        } else {
            mPreviewText!!.setText(R.string.more_previews)
        }
        mGridLayout!!.setColumnSize(Settings.previewSize)
        mGridLayout!!.setStrategy(SimpleGridAutoSpanLayout.STRATEGY_SUITABLE_SIZE)
        val size = previewNum.coerceAtMost(previewSet.size())
        for (i in 0 until size) {
            val view = inflater.inflate(R.layout.item_gallery_preview, mGridLayout, false)
            val image = view.findViewById<LoadImageView>(R.id.image)
            mGridLayout!!.addView(view)
            image.setTag(R.id.index, i)
            image.setOnClickListener(this)
            val text = view.findViewById<TextView>(R.id.text)
            text.text = (previewSet.getPosition(i) + 1).toString()
            previewSet.load(image, gd.gid, i)
        }
    }

    private fun getAllRatingText(rating: Float, ratingCount: Int): String {
        return getString(
            R.string.rating_text,
            getString(getRatingText(rating)),
            rating,
            ratingCount,
        )
    }

    private fun setTransitionName() {
        val gid = gid
        if (gid != -1L && mThumb != null &&
            mTitle != null && mUploader != null && mCategory != null
        ) {
            ViewCompat.setTransitionName(mThumb!!, TransitionNameFactory.getThumbTransitionName(gid))
            ViewCompat.setTransitionName(mTitle!!, TransitionNameFactory.getTitleTransitionName(gid))
            ViewCompat.setTransitionName(mUploader!!, TransitionNameFactory.getUploaderTransitionName(gid))
            ViewCompat.setTransitionName(mCategory!!, TransitionNameFactory.getCategoryTransitionName(gid))
        }
    }

    private fun showSimilarGalleryList() {
        val gd = mGalleryDetail ?: return
        val keyword = EhUtils.extractTitle(gd.title)
        if (null != keyword) {
            val lub = ListUrlBuilder()
            lub.mode = ListUrlBuilder.MODE_NORMAL
            lub.keyword = "\"" + keyword + "\""
            GalleryListScene.startScene(this, lub)
            return
        }
        val artist = getArtist(gd.tags)
        if (null != artist) {
            val lub = ListUrlBuilder()
            lub.mode = ListUrlBuilder.MODE_TAG
            lub.keyword = "artist:$artist"
            GalleryListScene.startScene(this, lub)
            return
        }
        if (null != gd.uploader) {
            val lub = ListUrlBuilder()
            lub.mode = ListUrlBuilder.MODE_UPLOADER
            lub.keyword = gd.uploader
            GalleryListScene.startScene(this, lub)
        }
    }

    private fun showCoverGalleryList() {
        val context = context ?: return
        val gid = gid
        if (-1L == gid) {
            return
        }
        try {
            val key = EhCacheKeyFactory.getThumbKey(gid)
            val path = imageLoader(context).diskCache!!.openSnapshot(key)!!.use { it.data }
            val lub = ListUrlBuilder()
            lub.mode = ListUrlBuilder.MODE_IMAGE_SEARCH
            lub.imagePath = path.toString()
            lub.isUseSimilarityScan = true
            GalleryListScene.startScene(this, lub)
        } catch (e: Throwable) {
            e.printStackTrace()
        }
    }

    override fun onClick(v: View) {
        val context = context
        val activity = mainActivity
        if (null == context || null == activity) {
            return
        }
        val galleryDetail = mGalleryDetail ?: return
        when (v) {
            mTip -> {
                if (request()) {
                    adjustViewVisibility(STATE_REFRESH, true)
                }
            }
            mBackAction -> {
                onBackPressed()
            }
            mOtherActions -> {
                ensurePopMenu()
                mPopupMenu?.show()
            }
            mUploader -> {
                if (uploader.isNullOrEmpty() || disowned) {
                    return
                }
                val lub = ListUrlBuilder()
                lub.mode = ListUrlBuilder.MODE_UPLOADER
                lub.keyword = uploader
                GalleryListScene.startScene(this, lub)
            }
            mCategory -> {
                val category = category
                if (category == EhUtils.NONE || category == EhUtils.PRIVATE || category == EhUtils.UNKNOWN) {
                    return
                }
                val lub = ListUrlBuilder()
                lub.category = category
                GalleryListScene.startScene(this, lub)
            }
            mDownload -> {
                if (mDownloadManager.getDownloadState(galleryDetail.gid) == DownloadInfo.STATE_INVALID) {
                    // CommonOperations Actions
                    CommonOperations.startDownload(activity, galleryDetail, false)
                } else {
                    val builder = CheckBoxDialogBuilder(
                        context,
                        getString(R.string.download_remove_dialog_message, galleryDetail.title),
                        getString(R.string.download_remove_dialog_check_text),
                        Settings.removeImageFiles,
                    )
                    val helper = DeleteDialogHelper(galleryDetail, builder)
                    builder.setTitle(R.string.download_remove_dialog_title)
                        .setPositiveButton(android.R.string.ok, helper)
                        .show()
                }
            }
            mRead -> {
                val intent = Intent(activity, GalleryActivity::class.java)
                intent.action = GalleryActivity.ACTION_EH
                intent.putExtra(GalleryActivity.KEY_GALLERY_INFO, galleryDetail)
                startActivity(intent)
            }
            mNewerVersion -> {
                val titles = ArrayList<CharSequence>()
                for (newerVersion in galleryDetail.newerVersions) {
                    titles.add(
                        getString(
                            R.string.newer_version_title,
                            newerVersion.title,
                            newerVersion.posted,
                        ),
                    )
                }
                AlertDialog.Builder(requireContext())
                    .setItems(titles.toTypedArray()) { _: DialogInterface?, which: Int ->
                        val newerVersion = galleryDetail.newerVersions[which]
                        val args = Bundle()
                        args.putString(KEY_ACTION, ACTION_GID_TOKEN)
                        args.putLong(KEY_GID, newerVersion.gid)
                        args.putString(KEY_TOKEN, newerVersion.token)
                        startScene(Announcer(GalleryDetailScene::class.java).setArgs(args))
                    }
                    .show()
            }
            mInfo -> {
                val args = Bundle()
                args.putParcelable(GalleryInfoScene.KEY_GALLERY_DETAIL, galleryDetail)
                startScene(Announcer(GalleryInfoScene::class.java).setArgs(args))
            }
            mHeartGroup -> {
                // DB Actions
                // CommonOperations Actions
                if (!mModifyingFavorites) {
                    var remove = false
                    val isLocalFavorites = EhDB.containLocalFavorites(galleryDetail.gid)
                    val isOnlineFavorites = galleryDetail.isFavorited
                    if (isLocalFavorites || isOnlineFavorites) {
                        mModifyingFavorites = true
                        CommonOperations.removeFromFavorites(
                            activity,
                            galleryDetail,
                            ModifyFavoritesListener(context, true),
                            isLocalFavorites && !isOnlineFavorites,
                        )
                        remove = true
                    }
                    if (!remove) {
                        mModifyingFavorites = true
                        CommonOperations.addToFavorites(
                            activity,
                            galleryDetail,
                            ModifyFavoritesListener(context, false),
                            false,
                        )
                    }
                    // Update UI
                    updateFavoriteDrawable()
                }
            }
            mShare -> {
                galleryDetailUrl?.let {
                    AppHelper.share(activity, it)
                }
            }
            mTorrent -> {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q && ContextCompat.checkSelfPermission(
                        activity,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE,
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    requestStoragePermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                } else {
                    val helper = TorrentListDialogHelper()
                    val dialog = AlertDialog.Builder(context)
                        .setTitle(R.string.torrents)
                        .setView(R.layout.dialog_torrent_list)
                        .setOnDismissListener(helper)
                        .show()
                    helper.setDialog(dialog, galleryDetail.torrentUrl)
                }
            }
            mArchive -> {
                if (galleryDetail.apiUid < 0) {
                    showTip(R.string.error_please_login_first, LENGTH_LONG)
                    return
                }
                val helper = ArchiveListDialogHelper()
                val dialog = AlertDialog.Builder(context)
                    .setTitle(R.string.settings_download)
                    .setView(R.layout.dialog_archive_list)
                    .setOnDismissListener(helper)
                    .show()
                helper.setDialog(dialog, galleryDetail.archiveUrl)
            }
            mRate -> {
                if (galleryDetail.apiUid < 0) {
                    showTip(R.string.error_please_login_first, LENGTH_LONG)
                    return
                }
                val helper = RateDialogHelper()
                val dialog = AlertDialog.Builder(context)
                    .setTitle(R.string.rate)
                    .setView(R.layout.dialog_rate)
                    .setNegativeButton(android.R.string.cancel, null)
                    .setPositiveButton(android.R.string.ok, helper)
                    .show()
                helper.setDialog(dialog, galleryDetail.rating)
            }
            mSimilar -> {
                showSimilarGalleryList()
            }
            mSearchCover -> {
                showCoverGalleryList()
            }
            mComments -> {
                val args = Bundle()
                args.putLong(GalleryCommentsScene.KEY_API_UID, galleryDetail.apiUid)
                args.putString(GalleryCommentsScene.KEY_API_KEY, galleryDetail.apiKey)
                args.putLong(GalleryCommentsScene.KEY_GID, galleryDetail.gid)
                args.putString(GalleryCommentsScene.KEY_TOKEN, galleryDetail.token)
                args.putParcelable(GalleryCommentsScene.KEY_COMMENT_LIST, galleryDetail.comments)
                args.putParcelable(GalleryCommentsScene.KEY_GALLERY_DETAIL, galleryDetail)
                startScene(
                    Announcer(GalleryCommentsScene::class.java)
                        .setArgs(args)
                        .setRequestCode(this, REQUEST_CODE_COMMENT_GALLERY),
                )
            }
            mPreviews -> {
                val previewNum = Settings.previewNum
                var scrollTo = 0
                if (previewNum < (galleryDetail.previewSet?.size() ?: 0)) {
                    scrollTo = previewNum
                } else if (galleryDetail.previewPages > 1) {
                    scrollTo = -1
                }
                val args = Bundle()
                args.putParcelable(GalleryPreviewsScene.KEY_GALLERY_INFO, galleryDetail)
                args.putInt(GalleryPreviewsScene.KEY_SCROLL_TO, scrollTo)
                startScene(Announcer(GalleryPreviewsScene::class.java).setArgs(args))
            }
            else -> {
                var o = v.getTag(R.id.tag)
                if (o is String) {
                    val lub = ListUrlBuilder()
                    lub.mode = ListUrlBuilder.MODE_TAG
                    lub.keyword = o
                    GalleryListScene.startScene(this, lub)
                    return
                }
                o = v.getTag(R.id.index)
                if (o is Int) {
                    val intent = Intent(context, GalleryActivity::class.java)
                    intent.action = GalleryActivity.ACTION_EH
                    intent.putExtra(GalleryActivity.KEY_GALLERY_INFO, galleryDetail)
                    intent.putExtra(GalleryActivity.KEY_PAGE, o)
                    startActivity(intent)
                }
            }
        }
    }

    private fun showFilterUploaderDialog() {
        val context = context
        val uploader = uploader
        if (context == null || uploader == null) {
            return
        }
        AlertDialog.Builder(context)
            .setMessage(getString(R.string.filter_the_uploader, uploader))
            .setPositiveButton(android.R.string.ok) { _: DialogInterface?, _: Int ->
                val filter = Filter()
                filter.mode = EhFilter.MODE_UPLOADER
                filter.text = uploader
                EhFilter.addFilter(filter)
                showTip(R.string.filter_added, LENGTH_SHORT)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showFilterTagDialog(tag: String) {
        val context = context ?: return
        AlertDialog.Builder(context)
            .setMessage(getString(R.string.filter_the_tag, tag))
            .setPositiveButton(android.R.string.ok) { _: DialogInterface?, _: Int ->
                val filter = Filter()
                filter.mode = EhFilter.MODE_TAG
                filter.text = tag
                EhFilter.addFilter(filter)
                showTip(R.string.filter_added, LENGTH_SHORT)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showTagDialog(tv: TextView, tag: String) {
        val context = context ?: return
        val temp: String
        val index = tag.indexOf(':')
        temp = if (index >= 0) {
            tag.substring(index + 1)
        } else {
            tag
        }
        val menu: MutableList<String> = ArrayList()
        val menuId = IntList()
        val resources = context.resources
        menu.add(resources.getString(android.R.string.copy))
        menuId.add(R.id.copy)
        if (temp != tv.text.toString()) {
            menu.add(resources.getString(R.string.copy_trans))
            menuId.add(R.id.copy_trans)
        }
        menu.add(resources.getString(R.string.show_definition))
        menuId.add(R.id.show_definition)
        menu.add(resources.getString(R.string.add_filter))
        menuId.add(R.id.add_filter)
        if (mGalleryDetail != null && mGalleryDetail!!.apiUid >= 0) {
            val textColor = tv.textColors.defaultColor
            val isVoted = textColor != Color.WHITE
            if (textColor != TAG_COLOR_UP.toInt()) {
                menu.add(resources.getString(if (isVoted) R.string.tag_vote_down_cancel else R.string.tag_vote_up))
                menuId.add(R.id.vote_up)
            }
            if (textColor != TAG_COLOR_DN.toInt()) {
                menu.add(resources.getString(if (isVoted) R.string.tag_vote_up_cancel else R.string.tag_vote_down))
                menuId.add(R.id.vote_down)
            }
        }
        AlertDialog.Builder(context)
            .setTitle(tag)
            .setItems(menu.toTypedArray()) { _: DialogInterface?, which: Int ->
                if (which < 0 || which >= menuId.size) {
                    return@setItems
                }
                when (menuId[which]) {
                    R.id.vote_up -> {
                        voteTag(tag, 1)
                    }
                    R.id.vote_down -> {
                        voteTag(tag, -1)
                    }
                    R.id.show_definition -> {
                        UrlOpener.openUrl(context, EhUrl.getTagDefinitionUrl(temp), false)
                    }
                    R.id.add_filter -> {
                        showFilterTagDialog(tag)
                    }
                    R.id.copy -> {
                        requireActivity().addTextToClipboard(tag, false)
                    }
                    R.id.copy_trans -> {
                        requireActivity().addTextToClipboard(tv.text.toString(), false)
                    }
                }
            }.show()
    }

    private fun voteTag(tag: String, vote: Int) {
        val context = context
        val activity = mainActivity
        if (null == context || null == activity) {
            return
        }
        val request = EhRequest()
            .setMethod(EhClient.METHOD_VOTE_TAG)
            .setArgs(
                mGalleryDetail!!.apiUid,
                mGalleryDetail!!.apiKey!!,
                mGalleryDetail!!.gid,
                mGalleryDetail!!.token!!,
                tag,
                vote,
            )
            .setCallback(VoteTagListener(context))
        request.enqueue(this)
    }

    override fun onLongClick(v: View): Boolean {
        val activity = mainActivity ?: return false
        if (mUploader === v) {
            if (uploader.isNullOrEmpty() || disowned) {
                return false
            }
            showFilterUploaderDialog()
        } else if (mDownload === v) {
            val galleryInfo = galleryInfo
            if (galleryInfo != null) {
                // CommonOperations Actions
                CommonOperations.startDownload(activity, galleryInfo, true)
            }
            return true
        } else if (mHeartGroup == v) {
            // DB Actions
            // CommonOperations Actions
            if (mGalleryDetail != null && !mModifyingFavorites) {
                var removeOrEdit = false
                if (EhDB.containLocalFavorites(mGalleryDetail!!.gid)) {
                    mModifyingFavorites = true
                    CommonOperations.removeFromFavorites(
                        activity,
                        mGalleryDetail!!,
                        ModifyFavoritesListener(activity, true),
                        true,
                    )
                    removeOrEdit = true
                } else if (mGalleryDetail!!.isFavorited) {
                    mModifyingFavorites = true
                    CommonOperations.doAddToFavorites(
                        activity,
                        mGalleryDetail!!,
                        mGalleryDetail!!.favoriteSlot,
                        ModifyFavoritesListener(activity, false),
                        true,
                    )
                    removeOrEdit = true
                }
                if (!removeOrEdit) {
                    mModifyingFavorites = true
                    CommonOperations.addToFavorites(
                        activity,
                        mGalleryDetail!!,
                        ModifyFavoritesListener(activity, false),
                        true,
                    )
                }
                // Update UI
                updateFavoriteDrawable()
            }
        } else {
            val tag = v.getTag(R.id.tag) as? String
            if (null != tag) {
                showTagDialog(v as TextView, tag)
                return true
            }
        }
        return false
    }

    override fun onBackPressed() {
        if (mViewTransition != null && mThumb != null &&
            mViewTransition!!.shownViewIndex == 0 && mThumb!!.isShown
        ) {
            val location = IntArray(2)
            mThumb!!.getLocationInWindow(location)
            // Only show transaction when thumb can be seen
            if (location[1] + mThumb!!.height > 0) {
                setTransitionName()
                finish(ExitTransaction(mThumb!!))
                return
            }
        }
        finish()
    }

    override fun onSceneResult(requestCode: Int, resultCode: Int, data: Bundle?) {
        if (requestCode == REQUEST_CODE_COMMENT_GALLERY) {
            if (resultCode != RESULT_OK || data == null) {
                return
            }
            val comments = data.getParcelableCompat<GalleryCommentList>(GalleryCommentsScene.KEY_COMMENT_LIST)
            if (mGalleryDetail == null && comments == null) {
                return
            }
            mGalleryDetail!!.comments = comments
            bindComments(comments!!.comments)
        } else {
            super.onSceneResult(requestCode, resultCode, data)
        }
    }

    private fun updateDownloadText() {
        mDownload?.run {
            when (mDownloadState) {
                DownloadInfo.STATE_INVALID -> setText(R.string.download)
                DownloadInfo.STATE_NONE -> setText(R.string.download_state_none)
                DownloadInfo.STATE_WAIT -> setText(R.string.download_state_wait)
                DownloadInfo.STATE_DOWNLOAD -> setText(R.string.download_state_downloading)
                DownloadInfo.STATE_FINISH -> setText(R.string.download_state_downloaded)
                DownloadInfo.STATE_FAILED -> setText(R.string.download_state_failed)
            }
        }
    }

    private fun updateDownloadState() {
        val context = context
        val gid = gid
        if (null == context || -1L == gid) {
            return
        }
        val downloadState = mDownloadManager.getDownloadState(gid)
        if (downloadState == mDownloadState) {
            return
        }
        mDownloadState = downloadState
        updateDownloadText()
    }

    override fun onAdd(info: DownloadInfo, list: List<DownloadInfo>, position: Int) {
        updateDownloadState()
    }

    override fun onUpdate(info: DownloadInfo, list: List<DownloadInfo>) {
        updateDownloadState()
    }

    override fun onUpdateAll() {
        updateDownloadState()
    }

    override fun onReload() {
        updateDownloadState()
    }

    override fun onChange() {
        updateDownloadState()
    }

    override fun onRemove(info: DownloadInfo, list: List<DownloadInfo>, position: Int) {
        updateDownloadState()
    }

    override fun onRenameLabel(from: String, to: String) {}

    override fun onUpdateLabels() {}

    private fun onGetGalleryDetailSuccess(result: GalleryDetail) {
        mGalleryDetail = result
        updateDownloadState()
        adjustViewVisibility(STATE_NORMAL, true)
        bindViewSecond()
    }

    private fun onGetGalleryDetailFailure(e: Exception) {
        e.printStackTrace()
        if (null != mTip) {
            val error = ExceptionUtils.getReadableString(e)
            mTip!!.text = error
            adjustViewVisibility(STATE_FAILED, true)
        }
    }

    private fun onRateGallerySuccess(result: RateGalleryParser.Result) {
        if (mGalleryDetail != null) {
            mGalleryDetail!!.rating = result.rating
            mGalleryDetail!!.ratingCount = result.ratingCount
        }
        // Update UI
        if (mRatingText != null && mRating != null) {
            mRatingText!!.text = getAllRatingText(result.rating, result.ratingCount)
            mRating!!.rating = result.rating
        }
    }

    private fun onModifyFavoritesSuccess(addOrRemove: Boolean) {
        mModifyingFavorites = false
        if (mGalleryDetail != null) {
            mGalleryDetail!!.isFavorited = !addOrRemove && mGalleryDetail!!.favoriteName != null
            updateFavoriteDrawable()
        }
    }

    private fun onModifyFavoritesFailure() {
        mModifyingFavorites = false
    }

    private fun onModifyFavoritesCancel() {
        mModifyingFavorites = false
    }

    override fun onProvideAssistContent(outContent: AssistContent) {
        super.onProvideAssistContent(outContent)
        val url = galleryDetailUrl
        if (url != null) {
            outContent.webUri = Uri.parse(url)
        }
    }

    @IntDef(STATE_INIT, STATE_NORMAL, STATE_REFRESH, STATE_REFRESH_HEADER, STATE_FAILED)
    @Retention(AnnotationRetention.SOURCE)
    private annotation class State

    private class ExitTransaction(
        private val mThumb: View,
    ) : TransitionHelper {
        override fun onTransition(
            context: Context,
            transaction: FragmentTransaction,
            exit: Fragment,
            enter: Fragment,
        ): Boolean {
            if (enter !is GalleryListScene && enter !is DownloadsScene &&
                enter !is FavoritesScene && enter !is HistoryScene
            ) {
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

    private class VoteTagListener(context: Context) :
        EhCallback<GalleryDetailScene?, String>(context) {
        override fun onSuccess(result: String) {
            if (result.isNotEmpty()) {
                showTip(result, LENGTH_SHORT)
            } else {
                showTip(R.string.tag_vote_successfully, LENGTH_SHORT)
            }
        }

        override fun onFailure(e: Exception) {
            showTip(R.string.vote_failed, LENGTH_LONG)
        }

        override fun onCancel() {}
    }

    private class DownloadArchiveListener(
        context: Context,
        private val info: GalleryInfo,
    ) : EhCallback<GalleryDetailScene?, String?>(context) {
        override fun onSuccess(result: String?) {
            result?.let {
                // TODO: Don't use buggy system download service
                val r = AndroidDownloadManager.Request(Uri.parse(result))
                val name = "${info.gid}-${EhUtils.getSuitableTitle(info)}.zip"
                r.setDestinationInExternalPublicDir(
                    Environment.DIRECTORY_DOWNLOADS,
                    FileUtils.sanitizeFilename(name),
                )
                r.setNotificationVisibility(AndroidDownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                val dm = application.getSystemService<AndroidDownloadManager>()!!
                try {
                    dm.enqueue(r)
                } catch (e: Throwable) {
                    e.printStackTrace()
                    ExceptionUtils.throwIfFatal(e)
                }
            }
            showTip(R.string.download_archive_started, LENGTH_SHORT)
        }

        override fun onFailure(e: Exception) {
            if (e is EhException) {
                showTip(ExceptionUtils.getReadableString(e), LENGTH_LONG)
            } else {
                showTip(R.string.download_archive_failure, LENGTH_LONG)
                e.printStackTrace()
            }
        }

        override fun onCancel() {}
    }

    private inner class DeleteDialogHelper(
        private val mGalleryInfo: GalleryInfo,
        private val mBuilder: CheckBoxDialogBuilder,
    ) : DialogInterface.OnClickListener {
        override fun onClick(dialog: DialogInterface, which: Int) {
            if (which != DialogInterface.BUTTON_POSITIVE) {
                return
            }
            // Delete
            // DownloadManager Actions
            mDownloadManager.deleteDownload(mGalleryInfo.gid)
            // Delete image files
            val checked = mBuilder.isChecked
            Settings.putRemoveImageFiles(checked)
            if (checked) {
                val file = SpiderDen.getGalleryDownloadDir(mGalleryInfo.gid)
                // DB Actions
                EhDB.removeDownloadDirname(mGalleryInfo.gid)
                // Other Actions
                lifecycleScope.launchIO {
                    runCatching {
                        file?.delete()
                    }
                }
            }
        }
    }

    private inner class GetGalleryDetailListener(context: Context) :
        EhCallback<GalleryDetailScene?, GalleryDetail>(context) {
        override fun onSuccess(result: GalleryDetail) {
            application.removeGlobalStuff(this)
            // Put gallery detail to cache
            galleryDetailCache.put(result.gid, result)
            // Add history
            // DB Actions
            EhDB.putHistoryInfo(result)
            // Notify success
            val scene = this@GalleryDetailScene
            scene.onGetGalleryDetailSuccess(result)
        }

        override fun onFailure(e: Exception) {
            application.removeGlobalStuff(this)
            val scene = this@GalleryDetailScene
            scene.onGetGalleryDetailFailure(e)
        }

        override fun onCancel() {
            application.removeGlobalStuff(this)
        }
    }

    private inner class RateGalleryListener(
        context: Context,
    ) : EhCallback<GalleryDetailScene?, RateGalleryParser.Result>(context) {
        override fun onSuccess(result: RateGalleryParser.Result) {
            showTip(R.string.rate_successfully, LENGTH_SHORT)
            val scene = this@GalleryDetailScene
            scene.onRateGallerySuccess(result)
        }

        override fun onFailure(e: Exception) {
            e.printStackTrace()
            showTip(R.string.rate_failed, LENGTH_LONG)
        }

        override fun onCancel() {}
    }

    private inner class ModifyFavoritesListener(
        context: Context,
        private val mAddOrRemove: Boolean,
    ) :
        EhCallback<GalleryDetailScene?, Unit>(context) {
        override fun onSuccess(result: Unit) {
            showTip(
                if (mAddOrRemove) R.string.remove_from_favorite_success else R.string.add_to_favorite_success,
                LENGTH_SHORT,
            )
            val scene = this@GalleryDetailScene
            scene.onModifyFavoritesSuccess(mAddOrRemove)
        }

        override fun onFailure(e: Exception) {
            showTip(
                if (mAddOrRemove) R.string.remove_from_favorite_failure else R.string.add_to_favorite_failure,
                LENGTH_LONG,
            )
            val scene = this@GalleryDetailScene
            scene.onModifyFavoritesFailure()
        }

        override fun onCancel() {
            val scene = this@GalleryDetailScene
            scene.onModifyFavoritesCancel()
        }
    }

    private inner class ArchiveListDialogHelper :
        AdapterView.OnItemClickListener,
        DialogInterface.OnDismissListener,
        EhClient.Callback<ArchiveParser.Result> {
        private var mProgressView: CircularProgressIndicator? = null
        private var mErrorText: TextView? = null
        private var mListView: ListView? = null
        private var mRequest: EhRequest? = null
        private var mDialog: Dialog? = null
        fun setDialog(dialog: Dialog?, url: String?) {
            mDialog = dialog
            mProgressView = ViewUtils.`$$`(dialog, R.id.progress) as CircularProgressIndicator
            mErrorText = ViewUtils.`$$`(dialog, R.id.text) as TextView
            mListView = ViewUtils.`$$`(dialog, R.id.list_view) as ListView
            mListView!!.onItemClickListener = this
            val context = context
            if (context != null) {
                if (mArchiveList == null) {
                    mErrorText!!.visibility = View.GONE
                    mListView!!.visibility = View.GONE
                    mRequest = EhRequest().setMethod(EhClient.METHOD_ARCHIVE_LIST)
                        .setArgs(url!!, mGid, mToken)
                        .setCallback(this)
                    mRequest!!.enqueue(this@GalleryDetailScene)
                } else {
                    bind(mArchiveList, mCurrentFunds)
                }
            }
        }

        private fun bind(data: List<ArchiveParser.Archive>?, funds: HomeParser.Funds?) {
            if (null == mDialog || null == mProgressView || null == mErrorText || null == mListView) {
                return
            }
            if (data.isNullOrEmpty()) {
                mProgressView!!.visibility = View.GONE
                mErrorText!!.visibility = View.VISIBLE
                mListView!!.visibility = View.GONE
                mErrorText!!.setText(R.string.no_archives)
            } else {
                val nameArray = data.map {
                    it.run {
                        if (isHAtH) {
                            val costStr =
                                if (cost == "Free") resources.getString(R.string.archive_free) else cost
                            "[H@H] $name [$size] [$costStr]"
                        } else {
                            val nameStr =
                                resources.getString(if (res == "org") R.string.archive_original else R.string.archive_resample)
                            val costStr =
                                if (cost == "Free!") resources.getString(R.string.archive_free) else cost
                            "$nameStr [$size] [$costStr]"
                        }
                    }
                }.toTypedArray()
                mProgressView!!.visibility = View.GONE
                mErrorText!!.visibility = View.GONE
                mListView!!.visibility = View.VISIBLE
                mListView!!.adapter =
                    ArrayAdapter(mDialog!!.context, R.layout.item_select_dialog, nameArray)
                if (funds != null) {
                    var fundsGP = funds.fundsGP.toString()
                    // Ex GP numbers are rounded down to the nearest thousand
                    if (EhUtils.isExHentai) {
                        fundsGP += "+"
                    }
                    mDialog!!.setTitle(getString(R.string.current_funds, fundsGP, funds.fundsC))
                }
            }
        }

        override fun onItemClick(parent: AdapterView<*>?, view: View, position: Int, id: Long) {
            val context = context
            val activity = mainActivity
            if (null != context && null != activity && null != mArchiveList && position < mArchiveList!!.size) {
                val res = mArchiveList!![position].res
                val isHAtH = mArchiveList!![position].isHAtH
                val request = EhRequest()
                request.setMethod(EhClient.METHOD_DOWNLOAD_ARCHIVE)
                request.setArgs(
                    mGalleryDetail!!.gid,
                    mGalleryDetail!!.token!!,
                    mArchiveFormParamOr!!,
                    res,
                    isHAtH,
                )
                request.setCallback(DownloadArchiveListener(context, mGalleryDetail!!))
                request.enqueue(this@GalleryDetailScene)
            }
            if (mDialog != null) {
                mDialog!!.dismiss()
                mDialog = null
            }
        }

        override fun onDismiss(dialog: DialogInterface) {
            if (mRequest != null) {
                mRequest!!.cancel()
                mRequest = null
            }
            mDialog = null
            mProgressView = null
            mErrorText = null
            mListView = null
        }

        override fun onSuccess(result: ArchiveParser.Result) {
            if (mRequest != null) {
                mRequest = null
                mArchiveFormParamOr = result.paramOr
                mArchiveList = result.archiveList
                mCurrentFunds = result.funds
                bind(result.archiveList, result.funds)
            }
        }

        override fun onFailure(e: Exception) {
            mRequest = null
            val context = context
            if (null != context && null != mProgressView && null != mErrorText && null != mListView) {
                mProgressView!!.visibility = View.GONE
                mErrorText!!.visibility = View.VISIBLE
                mListView!!.visibility = View.GONE
                mErrorText!!.text = ExceptionUtils.getReadableString(e)
            }
        }

        override fun onCancel() {
            mRequest = null
        }
    }

    private inner class TorrentListDialogHelper :
        AdapterView.OnItemClickListener,
        DialogInterface.OnDismissListener,
        EhClient.Callback<List<TorrentParser.Result>> {
        private var mProgressView: CircularProgressIndicator? = null
        private var mErrorText: TextView? = null
        private var mListView: ListView? = null
        private var mRequest: EhRequest? = null
        private var mDialog: Dialog? = null
        fun setDialog(dialog: Dialog?, url: String?) {
            mDialog = dialog
            mProgressView = ViewUtils.`$$`(dialog, R.id.progress) as CircularProgressIndicator
            mErrorText = ViewUtils.`$$`(dialog, R.id.text) as TextView
            mListView = ViewUtils.`$$`(dialog, R.id.list_view) as ListView
            mListView!!.onItemClickListener = this
            val context = context
            if (context != null) {
                if (mTorrentList == null) {
                    mErrorText!!.visibility = View.GONE
                    mListView!!.visibility = View.GONE
                    mRequest = EhRequest().setMethod(EhClient.METHOD_GET_TORRENT_LIST)
                        .setArgs(url!!, mGid, mToken)
                        .setCallback(this)
                    mRequest!!.enqueue(this@GalleryDetailScene)
                } else {
                    bind(mTorrentList)
                }
            }
        }

        private fun bind(data: List<TorrentParser.Result>?) {
            if (null == mDialog || null == mProgressView || null == mErrorText || null == mListView) {
                return
            }
            if (data.isNullOrEmpty()) {
                mProgressView!!.visibility = View.GONE
                mErrorText!!.visibility = View.VISIBLE
                mListView!!.visibility = View.GONE
                mErrorText!!.setText(R.string.no_torrents)
            } else {
                val nameArray = data.map { it.format() }.toTypedArray()
                mProgressView!!.visibility = View.GONE
                mErrorText!!.visibility = View.GONE
                mListView!!.visibility = View.VISIBLE
                mListView!!.adapter =
                    ArrayAdapter(mDialog!!.context, R.layout.item_select_dialog, nameArray)
            }
        }

        override fun onItemClick(parent: AdapterView<*>?, view: View, position: Int, id: Long) {
            val context = context
            if (null != context && null != mTorrentList && position < mTorrentList!!.size) {
                val url = mTorrentList!![position].url
                val name = mTorrentList!![position].name
                // TODO: Don't use buggy system download service
                val r =
                    AndroidDownloadManager.Request(Uri.parse(url.replace("exhentai.org", "ehtracker.org")))
                r.setDestinationInExternalPublicDir(
                    Environment.DIRECTORY_DOWNLOADS,
                    FileUtils.sanitizeFilename("$name.torrent"),
                )
                r.setNotificationVisibility(AndroidDownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                r.addRequestHeader("Cookie", EhCookieStore.getCookieHeader(url.toHttpUrl()))
                val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as AndroidDownloadManager
                try {
                    dm.enqueue(r)
                    showTip(R.string.download_torrent_started, LENGTH_SHORT)
                } catch (e: Throwable) {
                    e.printStackTrace()
                    ExceptionUtils.throwIfFatal(e)
                    showTip(R.string.download_torrent_failure, LENGTH_SHORT)
                }
            }
            if (mDialog != null) {
                mDialog!!.dismiss()
                mDialog = null
            }
        }

        override fun onDismiss(dialog: DialogInterface) {
            if (mRequest != null) {
                mRequest!!.cancel()
                mRequest = null
            }
            mDialog = null
            mProgressView = null
            mErrorText = null
            mListView = null
        }

        override fun onSuccess(result: List<TorrentParser.Result>) {
            if (mRequest != null) {
                mRequest = null
                mTorrentList = result
                bind(result)
            }
        }

        override fun onFailure(e: Exception) {
            mRequest = null
            val context = context
            if (null != context && null != mProgressView && null != mErrorText && null != mListView) {
                mProgressView!!.visibility = View.GONE
                mErrorText!!.visibility = View.VISIBLE
                mListView!!.visibility = View.GONE
                mErrorText!!.text = ExceptionUtils.getReadableString(e)
            }
        }

        override fun onCancel() {
            mRequest = null
        }
    }

    private inner class RateDialogHelper : OnUserRateListener, DialogInterface.OnClickListener {
        private var mRatingBar: GalleryRatingBar? = null
        private var mRatingText: TextView? = null
        fun setDialog(dialog: Dialog?, rating: Float) {
            mRatingText = ViewUtils.`$$`(dialog, R.id.rating_text) as TextView
            mRatingBar = ViewUtils.`$$`(dialog, R.id.rating_view) as GalleryRatingBar
            mRatingText!!.setText(getRatingText(rating))
            mRatingBar!!.rating = rating
            mRatingBar!!.setOnUserRateListener(this)
        }

        override fun onUserRate(rating: Float) {
            if (null != mRatingText) {
                mRatingText!!.setText(getRatingText(rating))
            }
        }

        override fun onClick(dialog: DialogInterface, which: Int) {
            val context = context
            val activity = mainActivity
            if (null == context || null == activity || which != DialogInterface.BUTTON_POSITIVE || null == mGalleryDetail || null == mRatingBar) {
                return
            }
            val request = EhRequest()
                .setMethod(EhClient.METHOD_GET_RATE_GALLERY)
                .setArgs(
                    mGalleryDetail!!.apiUid,
                    mGalleryDetail!!.apiKey!!,
                    mGalleryDetail!!.gid,
                    mGalleryDetail!!.token!!,
                    mRatingBar!!.rating,
                )
                .setCallback(
                    RateGalleryListener(context),
                )
            request.enqueue(this@GalleryDetailScene)
        }
    }

    companion object {
        const val KEY_ACTION = "action"
        const val ACTION_GALLERY_INFO = "action_gallery_info"
        const val ACTION_GID_TOKEN = "action_gid_token"
        const val KEY_GALLERY_INFO = "gallery_info"
        const val KEY_GID = "gid"
        const val KEY_TOKEN = "token"
        const val KEY_PAGE = "page"
        private const val REQUEST_CODE_COMMENT_GALLERY = 0
        private const val STATE_INIT = -1
        private const val STATE_NORMAL = 0
        private const val STATE_REFRESH = 1
        private const val STATE_REFRESH_HEADER = 2
        private const val STATE_FAILED = 3
        private const val TAG_COLOR_UP = 0xffffffa0u
        private const val TAG_COLOR_DN = 0xffddddddu
        private const val KEY_GALLERY_DETAIL = "gallery_detail"
        private const val KEY_REQUEST_ID = "request_id"
        private const val TRANSITION_ANIMATION_DISABLED = true
        private fun getArtist(tagGroups: Array<GalleryTagGroup>?): String? {
            if (null == tagGroups) {
                return null
            }
            for (tagGroup in tagGroups) {
                if ("artist" == tagGroup.groupName && tagGroup.size > 0) {
                    var tagStr = tagGroup[0]
                    while (tagStr.startsWith("_")) {
                        tagStr = tagStr.substring(2)
                    }
                    return tagStr
                }
            }
            return null
        }
    }
}
