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
package com.hippo.ehviewer.ui

import android.Manifest
import android.animation.Animator
import android.animation.ObjectAnimator
import android.animation.ValueAnimator.AnimatorUpdateListener
import android.annotation.SuppressLint
import android.app.assist.AssistContent
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.os.ParcelFileDescriptor.MODE_READ_ONLY
import android.provider.MediaStore
import android.text.TextUtils
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.webkit.MimeTypeMap
import android.widget.CompoundButton
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.SeekBar
import android.widget.SeekBar.OnSeekBarChangeListener
import android.widget.Spinner
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.contract.ActivityResultContracts.CreateDocument
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.hippo.app.EditTextDialogBuilder
import com.hippo.ehviewer.AppConfig
import com.hippo.ehviewer.BuildConfig
import com.hippo.ehviewer.R
import com.hippo.ehviewer.Settings
import com.hippo.ehviewer.client.EhUrl
import com.hippo.ehviewer.client.data.GalleryInfo
import com.hippo.ehviewer.gallery.ArchiveGalleryProvider
import com.hippo.ehviewer.gallery.EhGalleryProvider
import com.hippo.ehviewer.gallery.GalleryProvider2
import com.hippo.ehviewer.widget.GalleryGuideView
import com.hippo.ehviewer.widget.GalleryHeader
import com.hippo.ehviewer.widget.ReversibleSeekBar
import com.hippo.glgallery.GalleryProvider
import com.hippo.glgallery.GalleryView
import com.hippo.glgallery.SimpleAdapter
import com.hippo.glview.view.GLRootView
import com.hippo.unifile.UniFile
import com.hippo.util.ExceptionUtils
import com.hippo.util.getParcelableCompat
import com.hippo.util.getParcelableExtraCompat
import com.hippo.util.launchIO
import com.hippo.util.sendTo
import com.hippo.util.withUIContext
import com.hippo.widget.ColorView
import com.hippo.yorozuya.AnimationUtils
import com.hippo.yorozuya.ConcurrentPool
import com.hippo.yorozuya.FileUtils
import com.hippo.yorozuya.MathUtils
import com.hippo.yorozuya.ResourcesUtils
import com.hippo.yorozuya.SimpleAnimatorListener
import com.hippo.yorozuya.SimpleHandler
import com.hippo.yorozuya.ViewUtils
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import rikka.core.res.isNight
import rikka.core.res.resolveColor
import java.io.File
import java.io.IOException
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume

class GalleryActivity :
    EhActivity(),
    OnSeekBarChangeListener,
    GalleryView.Listener {
    private val requestStoragePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { result ->
        if (result && mSavingPage != -1) {
            saveImage(mSavingPage)
        } else {
            Toast.makeText(this, R.string.error_cant_save_image, Toast.LENGTH_SHORT).show()
        }
        mSavingPage = -1
    }
    private val saveImageToLauncher = registerForActivityResult(
        CreateDocument("todo/todo"),
    ) { uri ->
        if (uri != null) {
            val filepath = AppConfig.getExternalTempDir().toString() + File.separator + mCacheFileName
            val cacheFile = File(filepath)
            lifecycleScope.launchIO {
                try {
                    ParcelFileDescriptor.open(cacheFile, MODE_READ_ONLY).use { from ->
                        contentResolver.openFileDescriptor(uri, "w")!!.use {
                            from sendTo it
                        }
                    }
                } catch (e: IOException) {
                    e.printStackTrace()
                } finally {
                    runOnUiThread {
                        Toast.makeText(
                            this@GalleryActivity,
                            getString(R.string.image_saved, uri.path),
                            Toast.LENGTH_SHORT,
                        ).show()
                    }
                }
                cacheFile.delete()
            }
        }
    }
    private val mHideSliderRunnable = Runnable {
        mSeekBarPanel?.let { hideSlider(it) }
    }
    private val mHideSliderListener: SimpleAnimatorListener = object : SimpleAnimatorListener() {
        override fun onAnimationEnd(animation: Animator) {
            mSeekBarPanelAnimator = null
            mSeekBarPanel?.visibility = View.INVISIBLE
        }
    }
    private val mUpdateSliderListener = AnimatorUpdateListener {
        mSeekBarPanel?.requestLayout()
    }
    private val mShowSliderListener: SimpleAnimatorListener = object : SimpleAnimatorListener() {
        override fun onAnimationEnd(animation: Animator) {
            mSeekBarPanelAnimator = null
        }
    }
    private val mNotifyTaskPool = ConcurrentPool<NotifyTask>(3)
    private var mAction: String? = null
    private var mFilename: String? = null
    private var mUri: Uri? = null
    private var mGalleryInfo: GalleryInfo? = null
    private var mPage = 0
    private var mCacheFileName: String? = null
    private var mGLRootView: GLRootView? = null
    private var mGalleryView: GalleryView? = null
    private var mGalleryProvider: GalleryProvider2? = null
    private var mGalleryAdapter: GalleryAdapter? = null
    private var insetsController: WindowInsetsControllerCompat? = null
    private var mMaskView: ColorView? = null
    private var mClock: View? = null
    private var mProgress: TextView? = null
    private var mBattery: View? = null
    private var mSeekBarPanel: View? = null
    private var mGLLoading: View? = null
    private var mLeftText: TextView? = null
    private var mRightText: TextView? = null
    private var mSeekBar: ReversibleSeekBar? = null
    private var mAutoTransfer: ImageView? = null
    private var mSeekBarPanelAnimator: ObjectAnimator? = null
    private var mLayoutMode = 0
    private var mSize = 0
    private var mCurrentIndex = 0
    private var mSavingPage = -1
    private lateinit var builder: EditTextDialogBuilder
    private lateinit var dialog: AlertDialog
    private var dialogShown = false
    private var mAutoTransferJob: Job? = null
    private var mTurnPageIntervalVal = Settings.turnPageInterval

    private val galleryDetailUrl: String?
        get() {
            val gid: Long
            val token: String
            if (mGalleryInfo != null) {
                gid = mGalleryInfo!!.gid
                token = mGalleryInfo!!.token!!
            } else {
                return null
            }
            return EhUrl.getGalleryDetailUrl(gid, token, 0, false)
        }

    private fun buildProvider(replace: Boolean = false) {
        if (mGalleryProvider != null) {
            if (replace) mGalleryProvider!!.stop() else return
        }
        if (ACTION_EH == mAction) {
            mGalleryInfo?.let { mGalleryProvider = EhGalleryProvider(it) }
        } else if (Intent.ACTION_VIEW == mAction) {
            if (mUri != null) {
                try {
                    grantUriPermission(
                        BuildConfig.APPLICATION_ID,
                        mUri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION,
                    )
                } catch (_: Exception) {
                    Toast.makeText(this, R.string.error_reading_failed, Toast.LENGTH_SHORT).show()
                }
                val continuation: AtomicReference<Continuation<String>?> = AtomicReference(null)
                mGalleryProvider = ArchiveGalleryProvider(
                    this,
                    mUri!!,
                    flow {
                        if (!dialogShown) {
                            withUIContext {
                                dialogShown = true
                                dialog.run {
                                    show()
                                    getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                                        val passwd = builder.text
                                        if (passwd.isEmpty()) {
                                            builder.setError(getString(R.string.passwd_cannot_be_empty))
                                        } else {
                                            continuation.getAndSet(null)?.resume(passwd)
                                        }
                                    }
                                    setOnCancelListener {
                                        finish()
                                    }
                                }
                            }
                        }
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            val r = suspendCancellableCoroutine {
                                continuation.set(it)
                                it.invokeOnCancellation { dialog.dismiss() }
                            }
                            emit(r)
                            withUIContext {
                                builder.setError(getString(R.string.passwd_wrong))
                            }
                        }
                    },
                )
            }
        }
    }

    private fun handleIntent(intent: Intent?) {
        intent ?: return
        mAction = intent.action
        mFilename = intent.getStringExtra(KEY_FILENAME)
        mUri = intent.data
        mGalleryInfo = intent.getParcelableExtraCompat(KEY_GALLERY_INFO)
        mPage = intent.getIntExtra(KEY_PAGE, -1)
    }

    private fun onInit() {
        handleIntent(intent)
        buildProvider()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
        buildProvider(true)
        mGalleryProvider?.let {
            lifecycleScope.launchIO {
                it.start()
                if (it.awaitReady()) {
                    withUIContext {
                        mCurrentIndex = 0
                        setGallery()
                    }
                }
            }
        }
    }

    private fun onRestore(savedInstanceState: Bundle) {
        mAction = savedInstanceState.getString(KEY_ACTION)
        mFilename = savedInstanceState.getString(KEY_FILENAME)
        mUri = savedInstanceState.getParcelableCompat(KEY_URI)
        mGalleryInfo = savedInstanceState.getParcelableCompat(KEY_GALLERY_INFO)
        mPage = savedInstanceState.getInt(KEY_PAGE, -1)
        mCurrentIndex = savedInstanceState.getInt(KEY_CURRENT_INDEX)
        buildProvider()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(KEY_ACTION, mAction)
        outState.putString(KEY_FILENAME, mFilename)
        outState.putParcelable(KEY_URI, mUri)
        if (mGalleryInfo != null) {
            outState.putParcelable(KEY_GALLERY_INFO, mGalleryInfo)
        }
        outState.putInt(KEY_PAGE, mPage)
        outState.putInt(KEY_CURRENT_INDEX, mCurrentIndex)
    }

    override fun attachBaseContext(newBase: Context) {
        delegate.localNightMode = when (Settings.readTheme) {
            1 -> AppCompatDelegate.MODE_NIGHT_YES
            2 -> AppCompatDelegate.MODE_NIGHT_NO
            else -> Settings.theme
        }
        super.attachBaseContext(newBase)
    }

    @Suppress("DEPRECATION")
    override fun onCreate(savedInstanceState: Bundle?) {
        if (Settings.readingFullscreen) {
            window.addFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS)
        }
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) {
            onInit()
        } else {
            onRestore(savedInstanceState)
        }
        builder = EditTextDialogBuilder(this, null, getString(R.string.archive_passwd))
        builder.setTitle(getString(R.string.archive_need_passwd))
        builder.setPositiveButton(getString(android.R.string.ok), null)
        dialog = builder.create()
        dialog.setCanceledOnTouchOutside(false)

        mGalleryProvider.let {
            if (it == null) {
                finish()
                return
            }
            initializeGallery()
            lifecycleScope.launchIO {
                it.start()
                if (it.awaitReady()) withUIContext { setGallery() }
            }
        }
    }

    private fun initializeGallery() {
        setContentView(R.layout.activity_gallery)
        mGLRootView = ViewUtils.`$$`(this, R.id.gl_root_view) as GLRootView
        mMaskView = ViewUtils.`$$`(this, R.id.mask) as ColorView
        mClock = ViewUtils.`$$`(this, R.id.clock)
        mProgress = ViewUtils.`$$`(this, R.id.progress) as TextView
        mBattery = ViewUtils.`$$`(this, R.id.battery)
        mSeekBarPanel = ViewUtils.`$$`(this, R.id.seek_bar_panel)
        mGLLoading = ViewUtils.`$$`(this, R.id.gl_loading)
        mLeftText = ViewUtils.`$$`(mSeekBarPanel, R.id.left) as TextView
        mRightText = ViewUtils.`$$`(mSeekBarPanel, R.id.right) as TextView
        mSeekBar = ViewUtils.`$$`(mSeekBarPanel, R.id.seek_bar) as ReversibleSeekBar
        mAutoTransfer = ViewUtils.`$$`(mSeekBarPanel, R.id.auto_transfer) as ImageView
        mClock!!.visibility = if (Settings.showClock) View.VISIBLE else View.GONE
        mProgress!!.visibility = if (Settings.showProgress) View.VISIBLE else View.GONE
        mBattery!!.visibility = if (Settings.showBattery) View.VISIBLE else View.GONE
        mMaskView!!.setOnGenericMotionListener { _: View?, event: MotionEvent ->
            if (mGalleryView == null) {
                return@setOnGenericMotionListener false
            }
            if (event.action == MotionEvent.ACTION_SCROLL) {
                val scroll = event.getAxisValue(MotionEvent.AXIS_VSCROLL) * 300
                if (scroll < 0.0f) {
                    when (mLayoutMode) {
                        GalleryView.LAYOUT_RIGHT_TO_LEFT -> {
                            mGalleryView!!.pageLeft()
                        }
                        GalleryView.LAYOUT_LEFT_TO_RIGHT -> {
                            mGalleryView!!.pageRight()
                        }
                        GalleryView.LAYOUT_TOP_TO_BOTTOM -> {
                            mGalleryView!!.onScroll(0f, -scroll, 0f, -scroll, 0f, -scroll)
                        }
                    }
                } else {
                    when (mLayoutMode) {
                        GalleryView.LAYOUT_RIGHT_TO_LEFT -> {
                            mGalleryView!!.pageRight()
                        }
                        GalleryView.LAYOUT_LEFT_TO_RIGHT -> {
                            mGalleryView!!.pageLeft()
                        }
                        GalleryView.LAYOUT_TOP_TO_BOTTOM -> {
                            mGalleryView!!.onScroll(0f, -scroll, 0f, -scroll, 0f, -scroll)
                        }
                    }
                }
            }
            false
        }
        mSeekBar!!.setOnSeekBarChangeListener(this)
        mAutoTransfer!!.setOnClickListener { autoTransfer() }

        WindowCompat.setDecorFitsSystemWindows(window, false)
        insetsController = WindowCompat.getInsetsController(window, window.decorView)
        if (Settings.readingFullscreen) {
            insetsController!!.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            insetsController!!.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            insetsController!!.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_DEFAULT
            insetsController!!.show(WindowInsetsCompat.Type.systemBars())
        }
        val night = resources.configuration.isNight()
        insetsController!!.isAppearanceLightStatusBars = !night

        // Cutout
        window.attributes.layoutInDisplayCutoutMode =
            WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        val galleryHeader = findViewById<GalleryHeader>(R.id.gallery_header)
        ViewCompat.setOnApplyWindowInsetsListener(galleryHeader) { _: View?, insets: WindowInsetsCompat ->
            if (!Settings.readingFullscreen) {
                galleryHeader.setTopInsets(insets.getInsets(WindowInsetsCompat.Type.statusBars()).top)
            } else {
                galleryHeader.setDisplayCutout(insets.displayCutout)
            }
            WindowInsetsCompat.CONSUMED
        }

        // Screen lightness
        setScreenLightness(Settings.customScreenLightness, Settings.screenLightness)

        // Update keep screen on
        if (Settings.keepScreenOn) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }

        // Orientation
        requestedOrientation = when (Settings.screenRotation) {
            0 -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            1 -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
            2 -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            3 -> ActivityInfo.SCREEN_ORIENTATION_SENSOR
            else -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }

        // Guide
        if (Settings.guideGallery) {
            val mainLayout = ViewUtils.`$$`(this, R.id.main) as FrameLayout
            mainLayout.addView(GalleryGuideView(this))
        }
    }

    private fun setGallery() {
        if (mGalleryProvider?.isReady != true) return

        // TODO: Not well place to call it
        dialog.dismiss()

        mGLLoading?.visibility = View.GONE
        mGLRootView?.visibility = View.VISIBLE
        // Get start page
        if (mCurrentIndex == 0) mCurrentIndex = if (mPage >= 0) mPage else mGalleryProvider!!.startPage
        mGalleryAdapter = GalleryAdapter(mGLRootView!!, mGalleryProvider!!)
        val resources = resources
        mGalleryView = GalleryView.Builder(this, mGalleryAdapter!!)
            .setListener(this)
            .setLayoutMode(Settings.readingDirection)
            .setScaleMode(Settings.pageScaling)
            .setStartPosition(Settings.startPosition)
            .setStartPage(mCurrentIndex)
            .setBackgroundColor(theme.resolveColor(android.R.attr.colorBackground))
            .setPagerInterval(if (Settings.showPageInterval) resources.getDimensionPixelOffset(R.dimen.gallery_pager_interval) else 0)
            .setScrollInterval(if (Settings.showPageInterval) resources.getDimensionPixelOffset(R.dimen.gallery_scroll_interval) else 0)
            .setPageMinHeight(resources.getDimensionPixelOffset(R.dimen.gallery_page_min_height))
            .setPageInfoInterval(resources.getDimensionPixelOffset(R.dimen.gallery_page_info_interval))
            .setProgressColor(ResourcesUtils.getAttrColor(this, androidx.appcompat.R.attr.colorPrimary))
            .setProgressSize(resources.getDimensionPixelOffset(R.dimen.gallery_progress_size))
            .setPageTextColor(theme.resolveColor(android.R.attr.textColorSecondary))
            .setPageTextSize(resources.getDimensionPixelOffset(R.dimen.gallery_page_text_size))
            .setPageTextTypeface(Typeface.DEFAULT)
            .setErrorTextColor(this@GalleryActivity.getColor(R.color.red_500))
            .setErrorTextSize(resources.getDimensionPixelOffset(R.dimen.gallery_error_text_size))
            .setEmptyString(resources.getString(R.string.error_empty))
            .build()
        mGLRootView!!.setContentPane(mGalleryView)
        mGalleryProvider!!.setListener(mGalleryAdapter)
        mGalleryProvider!!.setGLRoot(mGLRootView!!)
        if (mGalleryView != null) {
            mLayoutMode = mGalleryView!!.layoutMode
        }
        mSize = mGalleryProvider!!.size
        updateSlider()
    }

    private fun autoTransfer() {
        if (mAutoTransferJob == null && mCurrentIndex + 1 != mSize) {
            mAutoTransfer?.setImageResource(R.drawable.v_pause_x24)
            mAutoTransferJob = lifecycleScope.launch {
                repeatOnLifecycle(Lifecycle.State.RESUMED) {
                    repeat(Int.MAX_VALUE) {
                        delay(mTurnPageIntervalVal * 1000L)
                        if (mLayoutMode == GalleryView.LAYOUT_RIGHT_TO_LEFT) {
                            mGalleryView!!.pageLeft()
                        } else {
                            mGalleryView!!.pageRight()
                        }
                    }
                }
            }
        } else if (mAutoTransferJob != null) {
            mAutoTransfer?.setImageResource(R.drawable.v_play_x24)
            if (mAutoTransferJob!!.isActive) mAutoTransferJob!!.cancel()
            mAutoTransferJob = null
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        mGLRootView = null
        mGalleryView = null
        if (mGalleryAdapter != null) {
            mGalleryAdapter!!.clearUploader()
            mGalleryAdapter = null
        }
        if (mGalleryProvider != null) {
            mGalleryProvider!!.setListener(null)
            mGalleryProvider!!.stop()
            mGalleryProvider = null
        }
        mMaskView = null
        mClock = null
        mProgress = null
        mBattery = null
        mSeekBarPanel = null
        mGLLoading = null
        mLeftText = null
        mRightText = null
        mSeekBar = null
        mAutoTransfer = null
        mAutoTransferJob = null
        SimpleHandler.getInstance().removeCallbacks(mHideSliderRunnable)
    }

    override fun onPause() {
        super.onPause()
        mGLRootView?.onPause()
    }

    override fun onResume() {
        super.onResume()
        mGLRootView?.onResume()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        var mKeyCode = keyCode
        if (mGalleryView == null) {
            return super.onKeyDown(mKeyCode, event)
        }

        // Check volume
        if (Settings.volumePage) {
            if (Settings.reverseVolumePage) {
                if (mKeyCode == KeyEvent.KEYCODE_VOLUME_UP) {
                    mKeyCode = KeyEvent.KEYCODE_VOLUME_DOWN
                } else if (mKeyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
                    mKeyCode = KeyEvent.KEYCODE_VOLUME_UP
                }
            }
            if (mKeyCode == KeyEvent.KEYCODE_VOLUME_UP) {
                if (mLayoutMode == GalleryView.LAYOUT_RIGHT_TO_LEFT) {
                    mGalleryView!!.pageRight()
                } else {
                    mGalleryView!!.pageLeft()
                }
                return true
            } else if (mKeyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
                if (mLayoutMode == GalleryView.LAYOUT_RIGHT_TO_LEFT) {
                    mGalleryView!!.pageLeft()
                } else {
                    mGalleryView!!.pageRight()
                }
                return true
            }
        }
        when (mKeyCode) {
            KeyEvent.KEYCODE_PAGE_UP, KeyEvent.KEYCODE_DPAD_UP -> {
                if (mLayoutMode == GalleryView.LAYOUT_RIGHT_TO_LEFT) {
                    mGalleryView!!.pageRight()
                } else {
                    mGalleryView!!.pageLeft()
                }
                return true
            }

            KeyEvent.KEYCODE_DPAD_LEFT -> {
                mGalleryView!!.pageLeft()
                return true
            }

            KeyEvent.KEYCODE_PAGE_DOWN, KeyEvent.KEYCODE_DPAD_DOWN -> {
                if (mLayoutMode == GalleryView.LAYOUT_RIGHT_TO_LEFT) {
                    mGalleryView!!.pageLeft()
                } else {
                    mGalleryView!!.pageRight()
                }
                return true
            }

            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                mGalleryView!!.pageRight()
                return true
            }

            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_SPACE, KeyEvent.KEYCODE_MENU -> {
                onTapMenuArea()
                return true
            }
        }
        return super.onKeyDown(mKeyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        // Check volume
        if (Settings.volumePage) {
            if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN ||
                keyCode == KeyEvent.KEYCODE_VOLUME_UP
            ) {
                return true
            }
        }

        // Check keyboard and Dpad
        return if (keyCode == KeyEvent.KEYCODE_PAGE_UP ||
            keyCode == KeyEvent.KEYCODE_PAGE_DOWN ||
            keyCode == KeyEvent.KEYCODE_DPAD_LEFT ||
            keyCode == KeyEvent.KEYCODE_DPAD_UP ||
            keyCode == KeyEvent.KEYCODE_DPAD_RIGHT ||
            keyCode == KeyEvent.KEYCODE_DPAD_DOWN ||
            keyCode == KeyEvent.KEYCODE_DPAD_CENTER ||
            keyCode == KeyEvent.KEYCODE_SPACE ||
            keyCode == KeyEvent.KEYCODE_MENU
        ) {
            true
        } else {
            super.onKeyUp(keyCode, event)
        }
    }

    @SuppressLint("SetTextI18n")
    private fun updateProgress() {
        if (mCurrentIndex + 1 == mSize) autoTransfer()
        mProgress?.text =
            if (mSize <= 0 || mCurrentIndex < 0) null else (mCurrentIndex + 1).toString() + "/" + mSize
    }

    @SuppressLint("SetTextI18n")
    private fun updateSlider() {
        if (mSeekBar == null || mRightText == null || mLeftText == null || mSize <= 0 || mCurrentIndex < 0) {
            return
        }
        val start: TextView
        val end: TextView
        if (mLayoutMode == GalleryView.LAYOUT_RIGHT_TO_LEFT) {
            start = mRightText!!
            end = mLeftText!!
            mSeekBar!!.setReverse(true)
        } else {
            start = mLeftText!!
            end = mRightText!!
            mSeekBar!!.setReverse(false)
        }
        start.text = (mCurrentIndex + 1).toString()
        end.text = mSize.toString()
        mSeekBar!!.max = mSize - 1
        mSeekBar!!.progress = mCurrentIndex
    }

    @SuppressLint("SetTextI18n")
    override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
        val start = if (mLayoutMode == GalleryView.LAYOUT_RIGHT_TO_LEFT) {
            mRightText
        } else {
            mLeftText
        }
        if (fromUser && null != start) {
            start.text = (progress + 1).toString()
        }
        if (fromUser && null != mGalleryView) {
            mGalleryView!!.setCurrentPage(progress)
        }
    }

    override fun onStartTrackingTouch(seekBar: SeekBar) {
        SimpleHandler.getInstance().removeCallbacks(mHideSliderRunnable)
    }

    override fun onStopTrackingTouch(seekBar: SeekBar) {
        SimpleHandler.getInstance().postDelayed(mHideSliderRunnable, HIDE_SLIDER_DELAY)
    }

    override fun onUpdateCurrentIndex(index: Int) {
        mGalleryProvider?.putStartPage(index)
        val task = mNotifyTaskPool.pop() ?: NotifyTask()
        task.setData(NOTIFY_KEY_CURRENT_INDEX, index)
        SimpleHandler.getInstance().post(task)
    }

    override fun onTapSliderArea() {
        val task = mNotifyTaskPool.pop() ?: NotifyTask()
        task.setData(NOTIFY_KEY_TAP_SLIDER_AREA, 0)
        SimpleHandler.getInstance().post(task)
    }

    override fun onTapMenuArea() {
        val task = mNotifyTaskPool.pop() ?: NotifyTask()
        task.setData(NOTIFY_KEY_TAP_MENU_AREA, 0)
        SimpleHandler.getInstance().post(task)
    }

    override fun onTapErrorText(index: Int) {
        val task = mNotifyTaskPool.pop() ?: NotifyTask()
        task.setData(NOTIFY_KEY_TAP_ERROR_TEXT, index)
        SimpleHandler.getInstance().post(task)
    }

    override fun onLongPressPage(index: Int) {
        val task = mNotifyTaskPool.pop() ?: NotifyTask()
        task.setData(NOTIFY_KEY_LONG_PRESS_PAGE, index)
        SimpleHandler.getInstance().post(task)
    }

    private fun showSlider(sliderPanel: View) {
        if (null != mSeekBarPanelAnimator) {
            mSeekBarPanelAnimator!!.cancel()
            mSeekBarPanelAnimator = null
        }
        sliderPanel.translationY = sliderPanel.height.toFloat()
        sliderPanel.visibility = View.VISIBLE
        mSeekBarPanelAnimator = ObjectAnimator.ofFloat(sliderPanel, "translationY", 0.0f)
        mSeekBarPanelAnimator!!.duration = SLIDER_ANIMATION_DURING
        mSeekBarPanelAnimator!!.interpolator = AnimationUtils.FAST_SLOW_INTERPOLATOR
        mSeekBarPanelAnimator!!.addUpdateListener(mUpdateSliderListener)
        mSeekBarPanelAnimator!!.addListener(mShowSliderListener)
        mSeekBarPanelAnimator!!.start()
        if (Settings.readingFullscreen) insetsController?.show(WindowInsetsCompat.Type.systemBars())
    }

    private fun hideSlider(sliderPanel: View) {
        if (null != mSeekBarPanelAnimator) {
            mSeekBarPanelAnimator!!.cancel()
            mSeekBarPanelAnimator = null
        }
        mSeekBarPanelAnimator =
            ObjectAnimator.ofFloat(sliderPanel, "translationY", sliderPanel.height.toFloat())
        mSeekBarPanelAnimator!!.duration = SLIDER_ANIMATION_DURING
        mSeekBarPanelAnimator!!.interpolator = AnimationUtils.SLOW_FAST_INTERPOLATOR
        mSeekBarPanelAnimator!!.addUpdateListener(mUpdateSliderListener)
        mSeekBarPanelAnimator!!.addListener(mHideSliderListener)
        mSeekBarPanelAnimator!!.start()
        if (Settings.readingFullscreen) insetsController?.hide(WindowInsetsCompat.Type.systemBars())
    }

    /**
     * @param lightness 0 - 200
     */
    private fun setScreenLightness(enable: Boolean, lightness: Int) {
        var mLightness = lightness
        if (null == mMaskView) {
            return
        }
        val w = window
        val lp = w.attributes
        if (enable) {
            mLightness = MathUtils.clamp(mLightness, 0, 200)
            if (mLightness > 100) {
                mMaskView!!.setColor(0)
                // Avoid BRIGHTNESS_OVERRIDE_OFF,
                // screen may be off when lp.screenBrightness is 0.0f
                lp.screenBrightness = ((mLightness - 100) / 100.0f).coerceAtLeast(0.01f)
            } else {
                mMaskView!!.setColor(MathUtils.lerp(0xde, 0x00, mLightness / 100.0f) shl 24)
                lp.screenBrightness = 0.01f
            }
        } else {
            mMaskView!!.setColor(0)
            lp.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
        }
        w.attributes = lp
    }

    private fun shareImage(page: Int) {
        if (null == mGalleryProvider) {
            return
        }
        val dir = AppConfig.getExternalTempDir()
        if (null == dir) {
            Toast.makeText(this, R.string.error_cant_create_temp_file, Toast.LENGTH_SHORT).show()
            return
        }
        val file = mGalleryProvider!!.save(
            page,
            UniFile.fromFile(dir)!!,
            mGalleryProvider!!.getImageFilename(page),
        )
        if (file == null) {
            Toast.makeText(this, R.string.error_cant_save_image, Toast.LENGTH_SHORT).show()
            return
        }
        val filename = file.name
        if (filename == null) {
            Toast.makeText(this, R.string.error_cant_save_image, Toast.LENGTH_SHORT).show()
            return
        }
        var mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(
            MimeTypeMap.getFileExtensionFromUrl(filename),
        )
        if (TextUtils.isEmpty(mimeType)) {
            mimeType = "image/jpeg"
        }
        val uri = FileProvider.getUriForFile(
            this,
            BuildConfig.APPLICATION_ID + ".fileprovider",
            File(dir, filename),
        )
        val intent = Intent()
        intent.action = Intent.ACTION_SEND
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        intent.putExtra(Intent.EXTRA_STREAM, uri)
        if (mGalleryInfo != null) {
            intent.putExtra(
                Intent.EXTRA_TEXT,
                EhUrl.getGalleryDetailUrl(mGalleryInfo!!.gid, mGalleryInfo!!.token),
            )
        }
        intent.setDataAndType(uri, mimeType)
        try {
            startActivity(Intent.createChooser(intent, getString(R.string.share_image)))
        } catch (e: Throwable) {
            ExceptionUtils.throwIfFatal(e)
            Toast.makeText(this, R.string.error_cant_find_activity, Toast.LENGTH_SHORT).show()
        }
    }

    private fun copyImage(page: Int) {
        if (null == mGalleryProvider) {
            return
        }
        val dir = AppConfig.getExternalCopyTempDir()
        if (null == dir) {
            Toast.makeText(this, R.string.error_cant_create_temp_file, Toast.LENGTH_SHORT).show()
            return
        }
        val file = mGalleryProvider!!.save(
            page,
            UniFile.fromFile(dir)!!,
            mGalleryProvider!!.getImageFilename(page),
        )
        if (file == null) {
            Toast.makeText(this, R.string.error_cant_save_image, Toast.LENGTH_SHORT).show()
            return
        }
        val filename = file.name
        if (filename == null) {
            Toast.makeText(this, R.string.error_cant_save_image, Toast.LENGTH_SHORT).show()
            return
        }
        val uri = FileProvider.getUriForFile(
            this,
            BuildConfig.APPLICATION_ID + ".fileprovider",
            File(dir, filename),
        )
        val clipboardManager = getSystemService(ClipboardManager::class.java)
        if (clipboardManager != null) {
            val clipData = ClipData.newUri(contentResolver, "ehviewer", uri)
            clipboardManager.setPrimaryClip(clipData)
            Toast.makeText(this, getString(R.string.copied_to_clipboard), Toast.LENGTH_SHORT).show()
        }
    }

    private fun saveImage(page: Int) {
        if (null == mGalleryProvider) {
            return
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q &&
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            mSavingPage = page
            requestStoragePermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            return
        }
        val filename = mGalleryProvider!!.getImageFilenameWithExtension(page)
        var mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(
            MimeTypeMap.getFileExtensionFromUrl(filename),
        )
        if (TextUtils.isEmpty(mimeType)) {
            mimeType = "image/jpeg"
        }
        val realPath: String
        val resolver = contentResolver
        val values = ContentValues()
        values.put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
        values.put(MediaStore.Images.Media.DATE_ADDED, System.currentTimeMillis())
        values.put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            values.put(
                MediaStore.MediaColumns.RELATIVE_PATH,
                Environment.DIRECTORY_PICTURES + File.separator + AppConfig.APP_DIRNAME,
            )
            values.put(MediaStore.MediaColumns.IS_PENDING, 1)
            realPath = Environment.DIRECTORY_PICTURES + File.separator + AppConfig.APP_DIRNAME
        } else {
            val path = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
                AppConfig.APP_DIRNAME,
            )
            realPath = path.toString()
            if (!FileUtils.ensureDirectory(path)) {
                Toast.makeText(this, R.string.error_cant_save_image, Toast.LENGTH_SHORT).show()
                return
            }
            values.put(MediaStore.MediaColumns.DATA, path.toString() + File.separator + filename)
        }
        val imageUri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
        if (imageUri == null) {
            Toast.makeText(this, R.string.error_cant_save_image, Toast.LENGTH_SHORT).show()
            return
        }
        if (!mGalleryProvider!!.save(page, UniFile.fromMediaUri(this, imageUri))) {
            try {
                resolver.delete(imageUri, null, null)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            Toast.makeText(this, R.string.error_cant_save_image, Toast.LENGTH_SHORT).show()
            return
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val contentValues = ContentValues()
            contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
            resolver.update(imageUri, contentValues, null, null)
        }
        Toast.makeText(
            this,
            getString(R.string.image_saved, realPath + File.separator + filename),
            Toast.LENGTH_SHORT,
        ).show()
    }

    private fun saveImageTo(page: Int, original: Boolean = false) {
        lifecycleScope.launchIO {
            if (null == mGalleryProvider) {
                return@launchIO
            }
            val dir = AppConfig.getExternalTempDir()
            if (null == dir) {
                withUIContext {
                    Toast.makeText(
                        this@GalleryActivity,
                        R.string.error_cant_create_temp_file,
                        Toast.LENGTH_SHORT,
                    ).show()
                }
                return@launchIO
            }
            val file = if (original) {
                withUIContext {
                    Toast.makeText(
                        this@GalleryActivity,
                        R.string.start_download_original,
                        Toast.LENGTH_SHORT,
                    ).show()
                }
                mGalleryProvider!!.downloadOriginal(
                    page,
                    UniFile.fromFile(dir)!!,
                    mGalleryProvider!!.getImageFilename(page),
                )
            } else {
                mGalleryProvider!!.save(
                    page,
                    UniFile.fromFile(dir)!!,
                    mGalleryProvider!!.getImageFilename(page),
                )
            }
            if (file == null) {
                withUIContext {
                    Toast.makeText(
                        this@GalleryActivity,
                        R.string.error_cant_save_image,
                        Toast.LENGTH_SHORT,
                    ).show()
                }
                return@launchIO
            }
            val filename = file.name
            if (filename == null) {
                withUIContext {
                    Toast.makeText(
                        this@GalleryActivity,
                        R.string.error_cant_save_image,
                        Toast.LENGTH_SHORT,
                    ).show()
                }
                return@launchIO
            }
            mCacheFileName = filename
            try {
                saveImageToLauncher.launch(filename)
            } catch (e: Throwable) {
                ExceptionUtils.throwIfFatal(e)
                withUIContext {
                    Toast.makeText(
                        this@GalleryActivity,
                        R.string.error_cant_find_activity,
                        Toast.LENGTH_SHORT,
                    ).show()
                }
            }
        }
    }

    private fun showPageDialog(page: Int) {
        val resources = this@GalleryActivity.resources
        val builder = AlertDialog.Builder(this@GalleryActivity)
        builder.setTitle(resources.getString(R.string.page_menu_title, page + 1))
        val items = arrayListOf<CharSequence>(
            getString(R.string.page_menu_refresh),
            getString(R.string.page_menu_share),
            getString(android.R.string.copy),
            getString(R.string.page_menu_save),
            getString(R.string.page_menu_save_to),
        )
        if (ACTION_EH == mAction && !Settings.getDownloadOriginImage(false)) {
            items.add(getString(R.string.page_menu_download_original))
        }
        pageDialogListener(builder, items.toTypedArray(), page)
        builder.show()
    }

    private fun pageDialogListener(
        builder: AlertDialog.Builder,
        items: Array<CharSequence>,
        page: Int,
    ) {
        builder.setItems(items) { _: DialogInterface?, which: Int ->
            if (mGalleryProvider == null) {
                return@setItems
            }
            when (which) {
                0 -> {
                    mGalleryProvider!!.removeCache(page)
                    mGalleryProvider!!.forceRequest(page)
                }
                1 -> shareImage(page)
                2 -> copyImage(page)
                3 -> saveImage(page)
                4 -> saveImageTo(page)
                5 -> saveImageTo(page, true)
            }
        }
    }

    override fun onProvideAssistContent(outContent: AssistContent) {
        super.onProvideAssistContent(outContent)
        galleryDetailUrl?.let {
            outContent.webUri = Uri.parse(it)
        }
    }

    @SuppressLint("InflateParams", "UseSwitchCompatOrMaterialCode")
    private inner class GalleryMenuHelper(context: Context?) : DialogInterface.OnClickListener {
        val view: View = LayoutInflater.from(context).inflate(R.layout.dialog_gallery_menu, null)
        private val mScreenRotation: Spinner = view.findViewById(R.id.screen_rotation)
        private val mReadingDirection: Spinner = view.findViewById(R.id.reading_direction)
        private val mScaleMode: Spinner = view.findViewById(R.id.page_scaling)
        private val mStartPosition: Spinner = view.findViewById(R.id.start_position)
        private val mReadTheme: Spinner = view.findViewById(R.id.read_theme)
        private val mKeepScreenOn: Switch = view.findViewById(R.id.keep_screen_on)
        private val mShowClock: Switch = view.findViewById(R.id.show_clock)
        private val mShowProgress: Switch = view.findViewById(R.id.show_progress)
        private val mShowBattery: Switch = view.findViewById(R.id.show_battery)
        private val mShowPageInterval: Switch = view.findViewById(R.id.show_page_interval)
        private val mTurnPageInterval: SeekBar = view.findViewById(R.id.turn_page_interval)
        private val mVolumePage: Switch = view.findViewById(R.id.volume_page)
        private val mReverseVolumePage: Switch = view.findViewById(R.id.reverse_volume_page)
        private val mReadingFullscreen: Switch = view.findViewById(R.id.reading_fullscreen)
        private val mCustomScreenLightness: Switch = view.findViewById(R.id.custom_screen_lightness)
        private val mScreenLightness: SeekBar = view.findViewById(R.id.screen_lightness)

        init {
            mScreenRotation.setSelection(Settings.screenRotation)
            mReadingDirection.setSelection(Settings.readingDirection)
            mScaleMode.setSelection(Settings.pageScaling)
            mStartPosition.setSelection(Settings.startPosition)
            mReadTheme.setSelection(Settings.readTheme)
            mKeepScreenOn.isChecked = Settings.keepScreenOn
            mShowClock.isChecked = Settings.showClock
            mShowProgress.isChecked = Settings.showProgress
            mShowBattery.isChecked = Settings.showBattery
            mShowPageInterval.isChecked = Settings.showPageInterval
            mTurnPageInterval.progress = Settings.turnPageInterval
            mVolumePage.isChecked = Settings.volumePage
            mVolumePage.setOnCheckedChangeListener { _: CompoundButton?, isChecked: Boolean ->
                mReverseVolumePage.visibility = if (isChecked) View.VISIBLE else View.GONE
            }
            mReverseVolumePage.visibility = if (Settings.volumePage) View.VISIBLE else View.GONE
            mReverseVolumePage.isChecked = Settings.reverseVolumePage
            mReadingFullscreen.isChecked = Settings.readingFullscreen
            mCustomScreenLightness.isChecked = Settings.customScreenLightness
            mCustomScreenLightness.setOnCheckedChangeListener { _: CompoundButton?, isChecked: Boolean ->
                mScreenLightness.visibility = if (isChecked) View.VISIBLE else View.GONE
            }
            mScreenLightness.progress = Settings.screenLightness
            mScreenLightness.visibility = if (Settings.customScreenLightness) View.VISIBLE else View.GONE
        }

        override fun onClick(dialog: DialogInterface, which: Int) {
            if (mGalleryView == null) {
                return
            }
            val screenRotation = mScreenRotation.selectedItemPosition
            val layoutMode = GalleryView.sanitizeLayoutMode(mReadingDirection.selectedItemPosition)
            val scaleMode = GalleryView.sanitizeScaleMode(mScaleMode.selectedItemPosition)
            val startPosition =
                GalleryView.sanitizeStartPosition(mStartPosition.selectedItemPosition)
            val readTheme = mReadTheme.selectedItemPosition
            val keepScreenOn = mKeepScreenOn.isChecked
            val showClock = mShowClock.isChecked
            val showProgress = mShowProgress.isChecked
            val showBattery = mShowBattery.isChecked
            val showPageInterval = mShowPageInterval.isChecked
            val turnPageInterval = mTurnPageInterval.progress
            val volumePage = mVolumePage.isChecked
            val reverseVolumePage = mReverseVolumePage.isChecked
            val readingFullscreen = mReadingFullscreen.isChecked
            val customScreenLightness = mCustomScreenLightness.isChecked
            val screenLightness = mScreenLightness.progress
            val oldReadingFullscreen = Settings.readingFullscreen
            val oldReadTheme = Settings.readTheme
            Settings.putScreenRotation(screenRotation)
            Settings.putReadingDirection(layoutMode)
            Settings.putPageScaling(scaleMode)
            Settings.putStartPosition(startPosition)
            Settings.putReadTheme(readTheme)
            Settings.putKeepScreenOn(keepScreenOn)
            Settings.putShowClock(showClock)
            Settings.putShowProgress(showProgress)
            Settings.putShowBattery(showBattery)
            Settings.putShowPageInterval(showPageInterval)
            Settings.putTurnPageInterval(turnPageInterval)
            Settings.putVolumePage(volumePage)
            Settings.putReverseVolumePage(reverseVolumePage)
            Settings.putReadingFullscreen(readingFullscreen)
            Settings.putCustomScreenLightness(customScreenLightness)
            Settings.putScreenLightness(screenLightness)
            requestedOrientation = when (screenRotation) {
                0 -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                1 -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
                2 -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                3 -> ActivityInfo.SCREEN_ORIENTATION_SENSOR
                else -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            }
            mGalleryView!!.layoutMode = layoutMode
            mGalleryView!!.setScaleMode(scaleMode)
            mGalleryView!!.setStartPosition(startPosition)
            if (keepScreenOn) {
                window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            } else {
                window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
            mClock?.visibility = if (showClock) View.VISIBLE else View.GONE
            mProgress?.visibility = if (showProgress) View.VISIBLE else View.GONE
            mBattery?.visibility = if (showBattery) View.VISIBLE else View.GONE
            mGalleryView!!.setPagerInterval(
                if (showPageInterval) {
                    resources.getDimensionPixelOffset(
                        R.dimen.gallery_pager_interval,
                    )
                } else {
                    0
                },
            )
            mGalleryView!!.setScrollInterval(
                if (showPageInterval) {
                    resources.getDimensionPixelOffset(
                        R.dimen.gallery_scroll_interval,
                    )
                } else {
                    0
                },
            )
            mTurnPageIntervalVal = turnPageInterval
            setScreenLightness(customScreenLightness, screenLightness)
            // Update slider
            mLayoutMode = layoutMode
            updateSlider()
            if (oldReadingFullscreen != readingFullscreen || oldReadTheme != readTheme) {
                recreate()
            }
        }
    }

    private inner class NotifyTask : Runnable {
        private var mKey = 0
        private var mValue = 0

        fun setData(key: Int, value: Int) {
            mKey = key
            mValue = value
        }

        private fun onTapMenuArea() {
            val builder = AlertDialog.Builder(this@GalleryActivity)
            val helper = GalleryMenuHelper(builder.context)
            builder.setTitle(R.string.gallery_menu_title)
                .setView(helper.view)
                .setPositiveButton(android.R.string.ok, helper).show()
        }

        private fun onTapSliderArea() {
            if (mSeekBarPanel == null || mSize <= 0 || mCurrentIndex < 0) {
                return
            }
            SimpleHandler.getInstance().removeCallbacks(mHideSliderRunnable)
            if (mSeekBarPanel!!.visibility == View.VISIBLE) {
                hideSlider(mSeekBarPanel!!)
            } else {
                showSlider(mSeekBarPanel!!)
                SimpleHandler.getInstance().postDelayed(mHideSliderRunnable, HIDE_SLIDER_DELAY)
            }
        }

        private fun onTapErrorText(index: Int) {
            if (mGalleryProvider != null) {
                mGalleryProvider!!.forceRequest(index)
            }
        }

        private fun onLongPressPage(index: Int) {
            showPageDialog(index)
        }

        override fun run() {
            when (mKey) {
                NOTIFY_KEY_LAYOUT_MODE -> {
                    mLayoutMode = mValue
                    updateSlider()
                }
                NOTIFY_KEY_SIZE -> {
                    mSize = mValue
                    updateSlider()
                    updateProgress()
                }
                NOTIFY_KEY_CURRENT_INDEX -> {
                    mCurrentIndex = mValue
                    updateSlider()
                    updateProgress()
                }
                NOTIFY_KEY_TAP_MENU_AREA -> onTapMenuArea()
                NOTIFY_KEY_TAP_SLIDER_AREA -> onTapSliderArea()
                NOTIFY_KEY_TAP_ERROR_TEXT -> onTapErrorText(mValue)
                NOTIFY_KEY_LONG_PRESS_PAGE -> onLongPressPage(mValue)
            }
            mNotifyTaskPool.push(this)
        }
    }

    private inner class GalleryAdapter(glRootView: GLRootView, provider: GalleryProvider) : SimpleAdapter(glRootView, provider) {
        override fun onDataChanged() {
            super.onDataChanged()
            if (mGalleryProvider != null) {
                val size = mGalleryProvider!!.size
                val task = mNotifyTaskPool.pop() ?: NotifyTask()
                task.setData(NOTIFY_KEY_SIZE, size)
                SimpleHandler.getInstance().post(task)
            }
        }
    }

    companion object {
        const val ACTION_EH = "eh"
        const val KEY_ACTION = "action"
        const val KEY_FILENAME = "filename"
        const val KEY_URI = "uri"
        const val KEY_GALLERY_INFO = "gallery_info"
        const val KEY_PAGE = "page"
        const val KEY_CURRENT_INDEX = "current_index"
        private const val SLIDER_ANIMATION_DURING: Long = 150
        private const val HIDE_SLIDER_DELAY: Long = 3000
        private const val NOTIFY_KEY_LAYOUT_MODE = 0
        private const val NOTIFY_KEY_SIZE = 1
        private const val NOTIFY_KEY_CURRENT_INDEX = 2
        private const val NOTIFY_KEY_TAP_SLIDER_AREA = 3
        private const val NOTIFY_KEY_TAP_MENU_AREA = 4
        private const val NOTIFY_KEY_TAP_ERROR_TEXT = 5
        private const val NOTIFY_KEY_LONG_PRESS_PAGE = 6
    }
}
