/*
 * Copyright 2015 Hippo Seven
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

import android.animation.Animator
import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Bundle
import android.os.Parcelable
import android.text.Editable
import android.text.TextWatcher
import android.util.AttributeSet
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.Keep
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import com.hippo.easyrecyclerview.EasyRecyclerView
import com.hippo.easyrecyclerview.LinearDividerItemDecoration
import com.hippo.ehviewer.R
import com.hippo.ehviewer.Settings
import com.hippo.ehviewer.client.EhTagDatabase
import com.hippo.util.getParcelableCompat
import com.hippo.util.launchIO
import com.hippo.util.withUIContext
import com.hippo.view.ViewTransition
import com.hippo.yorozuya.AnimationUtils
import com.hippo.yorozuya.LayoutUtils
import com.hippo.yorozuya.MathUtils
import com.hippo.yorozuya.SimpleAnimatorListener
import com.hippo.yorozuya.ViewUtils
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import rikka.core.res.resolveColor

class SearchBar @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : MaterialCardView(context, attrs),
    View.OnClickListener,
    TextView.OnEditorActionListener,
    TextWatcher,
    SearchEditText.SearchEditTextListener {
    private val mRect = Rect()
    private val mSearchDatabase by lazy { SearchDatabase.getInstance(context) }
    private var mState = STATE_NORMAL
    private var mBaseHeight = 0
    private var mWidth = 0
    private var mHeight = 0
    private var mProgress = 0f
    private var mMenuButton: ImageView? = null
    private var mTitleTextView: TextView? = null
    private var mActionButton: ImageView? = null
    private var mEditText: SearchEditText? = null
    private var mListContainer: View? = null
    private var mListView: EasyRecyclerView? = null
    private var mListHeader: View? = null
    private var mViewTransition: ViewTransition? = null
    private var mSuggestionList: List<Suggestion>? = null
    private var mSuggestionAdapter: SuggestionAdapter? = null
    private var mHelper: Helper? = null
    private var mSuggestionProvider: SuggestionProvider? = null
    private var mOnStateChangeListener: OnStateChangeListener? = null
    private var mAllowEmptySearch = true
    private var mInAnimation = false
    private val suggestionLock = Mutex()

    init {
        val inflater = LayoutInflater.from(context)
        inflater.inflate(R.layout.widget_search_bar, this)
        mMenuButton = ViewUtils.`$$`(this, R.id.search_menu) as ImageView
        mTitleTextView = ViewUtils.`$$`(this, R.id.search_title) as TextView
        mActionButton = ViewUtils.`$$`(this, R.id.search_action) as ImageView
        mEditText = ViewUtils.`$$`(this, R.id.search_edit_text) as SearchEditText
        mListContainer = ViewUtils.`$$`(this, R.id.list_container)
        mListView = ViewUtils.`$$`(mListContainer, R.id.search_bar_list) as EasyRecyclerView
        mListHeader = ViewUtils.`$$`(mListContainer, R.id.list_header)
        mViewTransition = ViewTransition(mTitleTextView, mEditText)
        mTitleTextView!!.setOnClickListener(this)
        mMenuButton!!.setOnClickListener(this)
        mActionButton!!.setOnClickListener(this)
        mEditText!!.setSearchEditTextListener(this)
        mEditText!!.setOnEditorActionListener(this)
        mEditText!!.addTextChangedListener(this)

        // Get base height
        ViewUtils.measureView(
            this,
            LayoutParams.WRAP_CONTENT,
            LayoutParams.WRAP_CONTENT,
        )
        mBaseHeight = measuredHeight

        mSuggestionList = ArrayList()
        mSuggestionAdapter = SuggestionAdapter(LayoutInflater.from(context))
        mListView!!.adapter = mSuggestionAdapter
        val decoration = LinearDividerItemDecoration(
            LinearDividerItemDecoration.VERTICAL,
            context.theme.resolveColor(R.attr.dividerColor),
            LayoutUtils.dp2pix(context, 1f),
        )
        decoration.setShowLastDivider(false)
        mListView!!.addItemDecoration(decoration)
        mListView!!.layoutManager = LinearLayoutManager(context)
    }

    private fun addListHeader() {
        mListHeader!!.visibility = VISIBLE
    }

    private fun removeListHeader() {
        mListHeader!!.visibility = GONE
    }

    @SuppressLint("NotifyDataSetChanged")
    @OptIn(DelicateCoroutinesApi::class)
    private fun updateSuggestions(scrollToTop: Boolean = true) {
        launchIO {
            suggestionLock.withLock {
                val suggestions = ArrayList<Suggestion>()
                mergedSuggestionFlow().collect {
                    suggestions.add(it)
                }
                withUIContext {
                    mSuggestionList = suggestions
                    if (mSuggestionList?.size == 0) {
                        removeListHeader()
                    } else {
                        addListHeader()
                    }
                    mSuggestionAdapter?.notifyDataSetChanged()
                }
            }
        }
        if (scrollToTop) {
            mListView!!.scrollToPosition(0)
        }
    }

    private fun mergedSuggestionFlow(): Flow<Suggestion> = flow {
        val text = mEditText!!.text.toString()
        mSuggestionProvider?.run { providerSuggestions(text)?.forEach { emit(it) } }
        mSearchDatabase.getSuggestions(text, 128).forEach { emit(KeywordSuggestion(it)) }
        EhTagDatabase.takeIf { it.isInitialized() }?.run {
            if (text.isNotEmpty() && !text.endsWith(" ")) {
                val keyword = text.substringAfterLast(" ")
                val translate =
                    Settings.showTagTranslations && isTranslatable(context)
                arrayOf(TYPE_EQUAL, TYPE_START, TYPE_CONTAIN).forEach { type ->
                    suggestFlow(keyword, translate, type).collect {
                        emit(TagSuggestion(it.first, it.second))
                    }
                }
            }
        }
    }

    fun setAllowEmptySearch(allowEmptySearch: Boolean) {
        mAllowEmptySearch = allowEmptySearch
    }

    fun setEditTextHint(hint: CharSequence) {
        mEditText!!.hint = hint
    }

    fun setHelper(helper: Helper) {
        mHelper = helper
    }

    fun setOnStateChangeListener(listener: OnStateChangeListener) {
        mOnStateChangeListener = listener
    }

    fun setSuggestionProvider(suggestionProvider: SuggestionProvider) {
        mSuggestionProvider = suggestionProvider
    }

    fun setText(text: String) {
        mEditText!!.setText(text)
    }

    fun cursorToEnd() {
        mEditText!!.setSelection(mEditText!!.text!!.length)
    }

    fun setTitle(title: String) {
        mTitleTextView!!.text = title
    }

    fun setLeftDrawable(drawable: Drawable) {
        mMenuButton!!.setImageDrawable(drawable)
    }

    fun setRightDrawable(drawable: Drawable) {
        mActionButton!!.setImageDrawable(drawable)
    }

    fun applySearch() {
        val query = mEditText!!.text.toString().trim { it <= ' ' }
        if (!mAllowEmptySearch && query.isEmpty()) {
            return
        }
        // Put it into db
        mSearchDatabase.addQuery(query)
        // Callback
        mHelper?.onApplySearch(query)
    }

    override fun onClick(v: View) {
        if (v === mTitleTextView) {
            mHelper?.onClickTitle()
        } else if (v === mMenuButton) {
            mHelper?.onClickLeftIcon()
        } else if (v === mActionButton) {
            mHelper?.onClickRightIcon()
        }
    }

    override fun onEditorAction(v: TextView, actionId: Int, event: KeyEvent?): Boolean {
        if (v === mEditText) {
            if (actionId == EditorInfo.IME_ACTION_SEARCH || actionId == EditorInfo.IME_NULL) {
                applySearch()
                return true
            }
        }
        return false
    }

    fun getState(): Int = mState

    fun setState(state: Int, animation: Boolean = true) {
        if (mState != state) {
            val oldState = mState
            mState = state
            when (oldState) {
                STATE_NORMAL -> {
                    mViewTransition!!.showView(1, animation)
                    mEditText!!.requestFocus()
                    if (state == STATE_SEARCH_LIST) {
                        showImeAndSuggestionsList(animation)
                    }
                    mOnStateChangeListener?.onStateChange(this, state, oldState, animation)
                }

                STATE_SEARCH -> {
                    if (state == STATE_NORMAL) {
                        mViewTransition!!.showView(0, animation)
                    } else if (state == STATE_SEARCH_LIST) {
                        showImeAndSuggestionsList(animation)
                    }
                    mOnStateChangeListener?.onStateChange(this, state, oldState, animation)
                }

                STATE_SEARCH_LIST -> {
                    hideImeAndSuggestionsList(animation)
                    if (state == STATE_NORMAL) {
                        mViewTransition!!.showView(0, animation)
                    }
                    mOnStateChangeListener?.onStateChange(this, state, oldState, animation)
                }
            }
        }
    }

    @Keep
    private fun showImeAndSuggestionsList(animation: Boolean) {
        // Show ime
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showSoftInput(mEditText, 0)
        // update suggestion for show suggestions list
        updateSuggestions()
        // Show suggestions list
        if (animation) {
            val oa = ObjectAnimator.ofFloat(this, "progress", 1f)
            oa.duration = ANIMATE_TIME
            oa.interpolator = AnimationUtils.FAST_SLOW_INTERPOLATOR
            oa.addListener(object : SimpleAnimatorListener() {
                override fun onAnimationStart(animation: Animator) {
                    mListContainer!!.visibility = VISIBLE
                    mInAnimation = true
                }

                override fun onAnimationEnd(animation: Animator) {
                    mInAnimation = false
                }
            })
            oa.setAutoCancel(true)
            oa.start()
        } else {
            mListContainer!!.visibility = VISIBLE
            progress = 1f
        }
    }

    private fun hideImeAndSuggestionsList(animation: Boolean) {
        // Hide ime
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(windowToken, 0)
        // Hide suggestions list
        if (animation) {
            val oa = ObjectAnimator.ofFloat(this, "progress", 0f)
            oa.duration = ANIMATE_TIME
            oa.interpolator = AnimationUtils.SLOW_FAST_INTERPOLATOR
            oa.addListener(object : SimpleAnimatorListener() {
                override fun onAnimationStart(animation: Animator) {
                    mInAnimation = true
                }

                override fun onAnimationEnd(animation: Animator) {
                    mListContainer!!.visibility = GONE
                    mInAnimation = false
                }
            })
            oa.setAutoCancel(true)
            oa.start()
        } else {
            progress = 0f
            mListContainer!!.visibility = GONE
        }
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        if (mListContainer!!.visibility == VISIBLE && (!mInAnimation || mHeight == 0)) {
            mWidth = right - left
            mHeight = bottom - top
        }
    }

    override fun getProgress(): Float = mProgress

    @Keep
    override fun setProgress(progress: Float) {
        mProgress = progress
        invalidate()
    }

    fun getEditText(): SearchEditText = mEditText!!

    override fun draw(canvas: Canvas) {
        if (mInAnimation && mHeight != 0) {
            val state = canvas.save()
            val bottom = MathUtils.lerp(mBaseHeight, mHeight, mProgress)
            mRect.set(0, 0, mWidth, bottom)
            clipBounds = mRect
            canvas.clipRect(mRect)
            super.draw(canvas)
            canvas.restoreToCount(state)
        } else {
            clipBounds = null
            super.draw(canvas)
        }
    }

    override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {
        // Empty
    }

    override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
        // Empty
    }

    override fun afterTextChanged(s: Editable) {
        updateSuggestions()
    }

    override fun onClick() {
        mHelper?.onSearchEditTextClick()
    }

    override fun onBackPressed() {
        mHelper?.onSearchEditTextBackPressed()
    }

    override fun onReceiveContent(uri: Uri?) {
        mHelper?.onReceiveContent(uri)
    }

    override fun onSaveInstanceState(): Parcelable {
        val state = Bundle()
        state.putParcelable(STATE_KEY_SUPER, super.onSaveInstanceState())
        state.putInt(STATE_KEY_STATE, mState)
        return state
    }

    override fun onRestoreInstanceState(state: Parcelable) {
        if (state is Bundle) {
            super.onRestoreInstanceState(state.getParcelableCompat(STATE_KEY_SUPER))
            setState(state.getInt(STATE_KEY_STATE), false)
        }
    }

    private fun wrapTagKeyword(keyword: String): String = if (keyword.endsWith(':')) {
        keyword
    } else if (keyword.contains(" ")) {
        val tag = keyword.substringAfter(':')
        val prefix = keyword.dropLast(tag.length)
        "$prefix\"$tag$\" "
    } else {
        "$keyword$ "
    }

    interface Helper {
        fun onClickTitle()
        fun onClickLeftIcon()
        fun onClickRightIcon()
        fun onSearchEditTextClick()
        fun onApplySearch(query: String)
        fun onSearchEditTextBackPressed()
        fun onReceiveContent(uri: Uri?)
    }

    interface OnStateChangeListener {
        fun onStateChange(searchBar: SearchBar, newState: Int, oldState: Int, animation: Boolean)
    }

    interface SuggestionProvider {
        fun providerSuggestions(text: String): List<Suggestion>?
    }

    abstract class Suggestion {
        abstract fun getText(textView: TextView): CharSequence?
        abstract fun onClick()
        open fun onLongClick(): Boolean = false
    }

    private class SuggestionHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val text1: TextView = itemView.findViewById(android.R.id.text1)
        val text2: TextView = itemView.findViewById(android.R.id.text2)
    }

    private inner class SuggestionAdapter(
        private val mInflater: LayoutInflater,
    ) : RecyclerView.Adapter<SuggestionHolder>() {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SuggestionHolder = SuggestionHolder(mInflater.inflate(R.layout.item_simple_list_2, parent, false))

        override fun onBindViewHolder(holder: SuggestionHolder, position: Int) {
            val suggestion = mSuggestionList?.get(position)
            val text1 = suggestion?.getText(holder.text1)
            val text2 = suggestion?.getText(holder.text2)
            holder.text1.text = text1
            if (text2 == null) {
                holder.text2.visibility = GONE
                holder.text2.text = ""
            } else {
                holder.text2.visibility = VISIBLE
                holder.text2.text = text2
            }

            holder.itemView.setOnClickListener {
                mSuggestionList?.run {
                    if (position < size) {
                        this[position].onClick()
                    }
                }
            }
            holder.itemView.setOnLongClickListener {
                mSuggestionList?.run {
                    if (position < size) {
                        return@setOnLongClickListener this[position].onLongClick()
                    }
                }
                return@setOnLongClickListener false
            }
        }

        override fun getItemId(position: Int): Long = position.toLong()

        override fun getItemCount(): Int = mSuggestionList?.size ?: 0
    }

    inner class TagSuggestion(
        private var mHint: String?,
        private var mKeyword: String,
    ) : Suggestion() {
        override fun getText(textView: TextView): CharSequence? = if (textView.id == android.R.id.text1) {
            mKeyword
        } else {
            mHint
        }

        override fun onClick() {
            val editable = mEditText!!.text as Editable
            val keywords = editable.toString().substringBeforeLast(" ", "")
            val keyword = wrapTagKeyword(mKeyword)
            val newKeywords = if (keywords.isNotEmpty()) "$keywords $keyword" else keyword
            mEditText!!.setText(newKeywords)
            mEditText!!.setSelection(newKeywords.length)
        }
    }

    inner class KeywordSuggestion(
        private val mKeyword: String,
    ) : Suggestion() {
        override fun getText(textView: TextView): CharSequence? = if (textView.id == android.R.id.text1) {
            mKeyword
        } else {
            null
        }

        override fun onClick() {
            mEditText!!.setText(mKeyword)
            mEditText!!.setSelection(mEditText!!.length())
        }

        override fun onLongClick(): Boolean {
            mSearchDatabase.deleteQuery(mKeyword)
            updateSuggestions(false)
            return true
        }
    }

    companion object {
        const val STATE_NORMAL = 0
        const val STATE_SEARCH = 1
        const val STATE_SEARCH_LIST = 2
        private const val STATE_KEY_SUPER = "super"
        private const val STATE_KEY_STATE = "state"
        private const val ANIMATE_TIME = 300L
    }
}
