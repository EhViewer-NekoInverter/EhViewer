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
package com.hippo.easyrecyclerview

import android.content.Context
import android.os.Parcel
import android.os.Parcelable
import android.util.AttributeSet
import android.util.SparseBooleanArray
import android.view.ActionMode
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Checkable
import androidx.collection.LongSparseArray
import androidx.core.util.isEmpty
import androidx.core.util.size
import androidx.recyclerview.widget.RecyclerView
import com.hippo.util.readParcelableCompat
import com.hippo.yorozuya.NumberUtils

/**
 * Add setChoiceMode for RecyclerView
 */
// Get some code from twoway-view and AbsListView.
open class EasyRecyclerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0,
) : RecyclerView(
    context,
    attrs,
    defStyle,
) {
    /**
     * Wrapper for the multiple choice mode callback; AbsListView needs to perform
     * a few extra actions around what application code does.
     */
    private var mMultiChoiceModeCallback: MultiChoiceModeWrapper? = null

    /**
     * Controls if/how the user may choose/check items in the list
     */
    private var mChoiceMode = CHOICE_MODE_NONE

    /**
     * Controls CHOICE_MODE_MULTIPLE_MODAL. null when inactive.
     */
    private var mChoiceActionMode: ActionMode? = null

    /**
     * Listener for custom multiple choices
     */
    private var mCustomChoiceListener: CustomChoiceListener? = null
    var isInCustomChoice = false
        private set

    /**
     * A lock, avoid OutOfCustomChoiceMode when doing OutOfCustomChoiceMode
     */
    private var mOutOfCustomChoiceModing = false
    private var mTempCheckStates: SparseBooleanArray? = null

    /**
     * Running count of how many items are currently checked
     */
    var checkedItemCount = 0
        private set

    /**
     * Running state of which positions are currently checked
     */
    private var mCheckStates: SparseBooleanArray? = null

    /**
     * Running state of which IDs are currently checked.
     * If there is a value for a given key, the checked state for that ID is true
     * and the value holds the last known position in the adapter for that id.
     */
    private var mCheckedIdStates: LongSparseArray<Int>? = null
    private var mAdapter: Adapter<*>? = null

    /**
     * {@inheritDoc}
     */
    override fun setAdapter(adapter: Adapter<*>?) {
        super.setAdapter(adapter)
        mAdapter = adapter
        if (adapter != null &&
            adapter.hasStableIds() &&
            mChoiceMode != CHOICE_MODE_NONE &&
            mCheckedIdStates == null
        ) {
            mCheckedIdStates = LongSparseArray()
        }
        mCheckStates?.clear()
        mCheckedIdStates?.clear()
    }

    override fun onChildAttachedToWindow(child: View) {
        super.onChildAttachedToWindow(child)
        if (mCheckStates != null) {
            val position = getChildAdapterPosition(child)
            if (position >= 0) {
                setViewChecked(child, mCheckStates!![position])
            }
        }
    }

    /**
     * Returns the checked state of the specified position. The result is only
     * valid if the choice mode has been set to [.CHOICE_MODE_SINGLE]
     * or [.CHOICE_MODE_MULTIPLE].
     *
     * @param position The item whose checked state to return
     * @return The item's checked state or `false` if choice mode
     * is invalid
     * @see .setChoiceMode
     */
    private fun isItemChecked(position: Int): Boolean = if (mChoiceMode != CHOICE_MODE_NONE && mCheckStates != null) {
        mCheckStates!![position]
    } else {
        false
    }

    val checkedItemPositions: SparseBooleanArray?
        /**
         * Returns the set of checked items in the list. The result is only valid if
         * the choice mode has not been set to [.CHOICE_MODE_NONE].
         *
         * @return A SparseBooleanArray which will return true for each call to
         * get(int position) where position is a checked position in the
         * list and false otherwise, or `null` if the choice
         * mode is set to [.CHOICE_MODE_NONE].
         */
        get() = if (mChoiceMode != CHOICE_MODE_NONE) {
            mCheckStates
        } else {
            null
        }

    fun intoCustomChoiceMode() {
        if (mChoiceMode == CHOICE_MODE_MULTIPLE_CUSTOM && !isInCustomChoice) {
            isInCustomChoice = true
            mCustomChoiceListener!!.onIntoCustomChoice(this)
        }
    }

    fun outOfCustomChoiceMode() {
        if (mChoiceMode == CHOICE_MODE_MULTIPLE_CUSTOM && isInCustomChoice && !mOutOfCustomChoiceModing) {
            mOutOfCustomChoiceModing = true
            // Copy mCheckStates
            mTempCheckStates!!.clear()
            run {
                for (i in 0 until mCheckStates!!.size) {
                    mTempCheckStates!!.put(mCheckStates!!.keyAt(i), mCheckStates!!.valueAt(i))
                }
            }
            // Uncheck remain checked items
            for (i in 0 until mTempCheckStates!!.size) {
                if (mTempCheckStates!!.valueAt(i)) {
                    setItemChecked(mTempCheckStates!!.keyAt(i), false)
                }
            }
            isInCustomChoice = false
            mCustomChoiceListener!!.onOutOfCustomChoice(this)
            mOutOfCustomChoiceModing = false
        }
    }

    /**
     * Clear any choices previously set
     */
    private fun clearChoices() {
        mCheckStates?.clear()
        mCheckedIdStates?.clear()
        checkedItemCount = 0
        updateOnScreenCheckedViews()
    }

    /**
     * Sets all items checked.
     */
    fun checkAll() {
        if (mChoiceMode == CHOICE_MODE_NONE || mChoiceMode == CHOICE_MODE_SINGLE) {
            return
        }

        // Check is intoCheckMode
        check(!(mChoiceMode == CHOICE_MODE_MULTIPLE_CUSTOM && !isInCustomChoice)) { "Call intoCheckMode first" }

        // Start selection mode if needed. We don't need to if we're unchecking something.
        if (mChoiceMode == CHOICE_MODE_MULTIPLE_MODAL && mChoiceActionMode == null) {
            check(
                !(
                    mMultiChoiceModeCallback == null ||
                        !mMultiChoiceModeCallback!!.hasWrappedCallback()
                    ),
            ) {
                "EasyRecyclerView: attempted to start selection mode " +
                    "for CHOICE_MODE_MULTIPLE_MODAL but no choice mode callback was " +
                    "supplied. Call setMultiChoiceModeListener to set a callback."
            }
            mChoiceActionMode = startActionMode(mMultiChoiceModeCallback)
        }
        for (i in 0 until mAdapter!!.itemCount) {
            val oldValue = mCheckStates!![i]
            mCheckStates!!.put(i, true)
            if (mCheckedIdStates != null && mAdapter!!.hasStableIds()) {
                mCheckedIdStates!!.put(mAdapter!!.getItemId(i), i)
            }
            if (!oldValue) {
                checkedItemCount++
            }
            if (mChoiceActionMode != null) {
                val id = mAdapter!!.getItemId(i)
                mMultiChoiceModeCallback!!.onItemCheckedStateChanged(
                    mChoiceActionMode!!,
                    i,
                    id,
                    true,
                )
            }
            if (mChoiceMode == CHOICE_MODE_MULTIPLE_CUSTOM) {
                val id = mAdapter!!.getItemId(i)
                mCustomChoiceListener!!.onItemCheckedStateChanged(this, i, id, true)
            }
        }
        updateOnScreenCheckedViews()
    }

    fun toggleItemChecked(position: Int) {
        if (mCheckStates != null) {
            setItemChecked(position, !mCheckStates!![position])
        }
    }

    /**
     * Sets the checked state of the specified position. The is only valid if
     * the choice mode has been set to [.CHOICE_MODE_SINGLE] or
     * [.CHOICE_MODE_MULTIPLE].
     *
     * @param position The item whose checked state is to be checked
     * @param value    The new checked state for the item
     */
    private fun setItemChecked(position: Int, value: Boolean) {
        if (mChoiceMode == CHOICE_MODE_NONE) {
            return
        }

        // Check is intoCheckMode
        check(!(mChoiceMode == CHOICE_MODE_MULTIPLE_CUSTOM && !isInCustomChoice)) { "Call intoCheckMode first" }

        // Start selection mode if needed. We don't need to if we're unchecking something.
        if (value && mChoiceMode == CHOICE_MODE_MULTIPLE_MODAL && mChoiceActionMode == null) {
            check(
                !(
                    mMultiChoiceModeCallback == null ||
                        !mMultiChoiceModeCallback!!.hasWrappedCallback()
                    ),
            ) {
                "EasyRecyclerView: attempted to start selection mode " +
                    "for CHOICE_MODE_MULTIPLE_MODAL but no choice mode callback was " +
                    "supplied. Call setMultiChoiceModeListener to set a callback."
            }
            mChoiceActionMode = startActionMode(mMultiChoiceModeCallback)
        }
        if (mChoiceMode == CHOICE_MODE_MULTIPLE || mChoiceMode == CHOICE_MODE_MULTIPLE_MODAL || mChoiceMode == CHOICE_MODE_MULTIPLE_CUSTOM) {
            val oldValue = mCheckStates!![position]
            mCheckStates!!.put(position, value)
            if (mCheckedIdStates != null && mAdapter!!.hasStableIds()) {
                if (value) {
                    mCheckedIdStates!!.put(mAdapter!!.getItemId(position), position)
                } else {
                    mCheckedIdStates!!.remove(mAdapter!!.getItemId(position))
                }
            }
            if (oldValue != value) {
                if (value) {
                    checkedItemCount++
                } else {
                    checkedItemCount--
                }
            }
            if (mChoiceActionMode != null) {
                val id = mAdapter!!.getItemId(position)
                mMultiChoiceModeCallback!!.onItemCheckedStateChanged(
                    mChoiceActionMode!!,
                    position,
                    id,
                    value,
                )
            }
            if (mChoiceMode == CHOICE_MODE_MULTIPLE_CUSTOM) {
                val id = mAdapter!!.getItemId(position)
                mCustomChoiceListener!!.onItemCheckedStateChanged(this, position, id, value)
            }
        } else {
            val updateIds = mCheckedIdStates != null && mAdapter!!.hasStableIds()
            // Clear all values if we're checking something, or unchecking the currently
            // selected item
            if (value || isItemChecked(position)) {
                mCheckStates!!.clear()
                if (updateIds) {
                    mCheckedIdStates!!.clear()
                }
            }
            // this may end up selecting the value we just cleared but this way
            // we ensure length of mCheckStates is 1, a fact getCheckedItemPosition relies on
            if (value) {
                mCheckStates!!.put(position, true)
                if (updateIds) {
                    mCheckedIdStates!!.put(mAdapter!!.getItemId(position), position)
                }
                checkedItemCount = 1
            } else if (mCheckStates!!.isEmpty() || !mCheckStates!!.valueAt(0)) {
                checkedItemCount = 0
            }
        }
        updateOnScreenCheckedViews()
    }

    /**
     * Defines the choice behavior for the List. By default, Lists do not have any choice behavior
     * ([.CHOICE_MODE_NONE]). By setting the choiceMode to [.CHOICE_MODE_SINGLE], the
     * List allows up to one item to  be in a chosen state. By setting the choiceMode to
     * [.CHOICE_MODE_MULTIPLE], the list allows any number of items to be chosen.
     *
     * @param choiceMode One of [.CHOICE_MODE_NONE], [.CHOICE_MODE_SINGLE], or
     * [.CHOICE_MODE_MULTIPLE]
     */
    fun setChoiceMode(choiceMode: Int) {
        mChoiceMode = choiceMode
        if (mChoiceActionMode != null) {
            mChoiceActionMode!!.finish()
            mChoiceActionMode = null
        }
        if (mChoiceMode != CHOICE_MODE_NONE) {
            if (mCheckStates == null) {
                mCheckStates = SparseBooleanArray(0)
            }
            if (mCheckedIdStates == null && mAdapter != null && mAdapter!!.hasStableIds()) {
                mCheckedIdStates = LongSparseArray(0)
            }
            // Modal multi-choice mode only has choices when the mode is active. Clear them.
            if (mChoiceMode == CHOICE_MODE_MULTIPLE_MODAL) {
                clearChoices()
                isLongClickable = true
            } else if (mChoiceMode == CHOICE_MODE_MULTIPLE_CUSTOM) {
                if (mTempCheckStates == null) {
                    mTempCheckStates = SparseBooleanArray(0)
                }
                clearChoices()
            }
        }
    }

    fun setCustomCheckedListener(listener: CustomChoiceListener?) {
        mCustomChoiceListener = listener
    }

    /**
     * Perform a quick, in-place update of the checked or activated state
     * on all visible item views. This should only be called when a valid
     * choice mode is active.
     */
    private fun updateOnScreenCheckedViews() {
        for (i in 0 until childCount) {
            val child = getChildAt(i)
            val position = getChildAdapterPosition(child)
            setViewChecked(child, mCheckStates!![position])
        }
    }

    public override fun onSaveInstanceState(): Parcelable {
        val ss = SavedState(super.onSaveInstanceState())
        ss.choiceMode = mChoiceMode
        ss.customChoice = isInCustomChoice
        ss.checkedItemCount = checkedItemCount
        ss.checkState = mCheckStates
        ss.checkIdState = mCheckedIdStates
        return ss
    }

    public override fun onRestoreInstanceState(state: Parcelable) {
        val ss = state as SavedState
        super.onRestoreInstanceState(ss.superState)
        setChoiceMode(ss.choiceMode)
        isInCustomChoice = ss.customChoice
        checkedItemCount = ss.checkedItemCount
        if (ss.checkState != null) {
            mCheckStates = ss.checkState
        }
        if (ss.checkIdState != null) {
            mCheckedIdStates = ss.checkIdState
        }
        if (mChoiceMode == CHOICE_MODE_MULTIPLE_MODAL && checkedItemCount > 0) {
            mChoiceActionMode = startActionMode(mMultiChoiceModeCallback)
        }
        updateOnScreenCheckedViews()
    }

    /**
     * A MultiChoiceModeListener receives events for [android.widget.AbsListView.CHOICE_MODE_MULTIPLE_MODAL].
     * It acts as the [android.view.ActionMode.Callback] for the selection mode and also receives
     * [.onItemCheckedStateChanged] events when the user
     * selects and deselects list items.
     */
    interface MultiChoiceModeListener : ActionMode.Callback {
        /**
         * Called when an item is checked or unchecked during selection mode.
         *
         * @param mode     The [android.view.ActionMode] providing the selection mode
         * @param position Adapter position of the item that was checked or unchecked
         * @param id       Adapter ID of the item that was checked or unchecked
         * @param checked  `true` if the item is now checked, `false`
         * if the item is now unchecked.
         */
        fun onItemCheckedStateChanged(
            mode: ActionMode,
            position: Int,
            id: Long,
            checked: Boolean,
        )
    }

    /**
     * Custom checked
     */
    interface CustomChoiceListener {
        fun onIntoCustomChoice(view: EasyRecyclerView)
        fun onOutOfCustomChoice(view: EasyRecyclerView)
        fun onItemCheckedStateChanged(
            view: EasyRecyclerView,
            position: Int,
            id: Long,
            checked: Boolean,
        )
    }

    /**
     * This saved state class is a Parcelable and should not extend
     * [android.view.View.BaseSavedState] nor [android.view.AbsSavedState]
     * because its super class AbsSavedState's constructor
     * currently passes null
     * as a class loader to read its superstate from Parcelable.
     * This causes [android.os.BadParcelableException] when restoring saved states.
     *
     *
     * The super class "RecyclerView" is a part of the support library,
     * and restoring its saved state requires the class loader that loaded the RecyclerView.
     * It seems that the class loader is not required when restoring from RecyclerView itself,
     * but it is required when restoring from RecyclerView's subclasses.
     */
    internal open class SavedState : Parcelable {
        var choiceMode = 0
        var customChoice = false
        var checkedItemCount = 0
        var checkState: SparseBooleanArray? = null
        var checkIdState: LongSparseArray<Int>? = null

        // This keeps the parent(RecyclerView)'s state
        var superState: Parcelable?

        constructor() {
            superState = null
        }

        /**
         * Constructor called from [.onSaveInstanceState]
         */
        constructor(superState: Parcelable?) {
            this.superState = if (superState !== EMPTY_STATE) superState else null
        }

        /**
         * Constructor called from [.CREATOR]
         */
        private constructor(`in`: Parcel) {
            // Parcel 'in' has its parent(RecyclerView)'s saved state.
            // To restore it, class loader that loaded RecyclerView is required.
            val superState =
                `in`.readParcelableCompat<Parcelable>(RecyclerView::class.java.getClassLoader())
            this.superState = superState ?: EMPTY_STATE
            choiceMode = `in`.readInt()
            customChoice = NumberUtils.int2boolean(`in`.readInt())
            checkedItemCount = `in`.readInt()
            checkState = `in`.readSparseBooleanArray()
            val n = `in`.readInt()
            if (n > 0) {
                checkIdState = LongSparseArray()
                (0 until n).forEach { i ->
                    val key = `in`.readLong()
                    val value = `in`.readInt()
                    checkIdState!!.put(key, value)
                }
            }
        }

        override fun describeContents(): Int = 0

        override fun writeToParcel(out: Parcel, flags: Int) {
            out.writeParcelable(superState, flags)
            out.writeInt(choiceMode)
            out.writeInt(NumberUtils.boolean2int(customChoice))
            out.writeInt(checkedItemCount)
            out.writeSparseBooleanArray(checkState)
            val n = if (checkIdState != null) checkIdState!!.size() else 0
            out.writeInt(n)
            for (i in 0 until n) {
                out.writeLong(checkIdState!!.keyAt(i))
                out.writeInt(checkIdState!!.valueAt(i))
            }
        }

        companion object CREATOR : Parcelable.Creator<SavedState> {
            override fun createFromParcel(`in`: Parcel): SavedState = SavedState(`in`)

            override fun newArray(size: Int): Array<SavedState?> = arrayOfNulls(size)
        }
    }

    inner class MultiChoiceModeWrapper : MultiChoiceModeListener {
        private val mWrapped: MultiChoiceModeListener? = null
        fun hasWrappedCallback(): Boolean = mWrapped != null

        override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
            if (mWrapped!!.onCreateActionMode(mode, menu)) {
                // Initialize checked graphic state?
                isLongClickable = false
                return true
            }
            return false
        }

        override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean = mWrapped!!.onPrepareActionMode(mode, menu)

        override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean = mWrapped!!.onActionItemClicked(mode, item)

        override fun onDestroyActionMode(mode: ActionMode) {
            mWrapped!!.onDestroyActionMode(mode)
            mChoiceActionMode = null

            // Ending selection mode means deselecting everything.
            clearChoices()
            requestLayout()
            isLongClickable = true
        }

        override fun onItemCheckedStateChanged(
            mode: ActionMode,
            position: Int,
            id: Long,
            checked: Boolean,
        ) {
            mWrapped!!.onItemCheckedStateChanged(mode, position, id, checked)

            // If there are no items selected we no longer need the selection mode.
            if (checkedItemCount == 0) {
                mode.finish()
            }
        }
    }

    companion object {
        /**
         * Normal list that does not indicate choices
         */
        const val CHOICE_MODE_NONE = 0

        /**
         * The list allows up to one choice
         */
        const val CHOICE_MODE_SINGLE = 1

        /**
         * The list allows multiple choices
         */
        const val CHOICE_MODE_MULTIPLE = 2

        /**
         * The list allows multiple choices in a modal selection mode
         */
        const val CHOICE_MODE_MULTIPLE_MODAL = 3

        /**
         * The list allows multiple choices in custom action
         */
        const val CHOICE_MODE_MULTIPLE_CUSTOM = 4

        private val EMPTY_STATE: SavedState = object : SavedState() {}

        private fun setViewChecked(view: View, checked: Boolean) {
            if (view is Checkable) {
                (view as Checkable).isChecked = checked
            } else {
                view.isActivated = checked
            }
        }
    }
}
