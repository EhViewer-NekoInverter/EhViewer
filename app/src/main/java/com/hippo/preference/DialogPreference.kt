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
package com.hippo.preference

import android.app.Dialog
import android.content.Context
import android.content.DialogInterface
import android.content.SharedPreferences
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.os.Parcel
import android.os.Parcelable
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.preference.Preference
import com.hippo.ehviewer.R

/**
 * A base class for [Preference] objects that are
 * dialog-based. These preferences will, when clicked, open a dialog showing the
 * actual preference controls.
 */
abstract class DialogPreference(
    context: Context,
    attrs: AttributeSet? = null,
) : Preference(
    context,
    attrs,
),
    DialogInterface.OnClickListener,
    DialogInterface.OnDismissListener {
    private var mBuilder: AlertDialog.Builder? = null
    private var mDialogTitle: CharSequence? = null
    private var mDialogIcon: Drawable? = null
    private var mPositiveButtonText: CharSequence? = null
    private var mNegativeButtonText: CharSequence? = null
    private var mDialogLayoutResId: Int = 0

    /**
     * The dialog, if it is showing.
     */
    private var mDialog: AlertDialog? = null

    /**
     * Which button was clicked.
     */
    private var mWhichButtonClicked = 0

    init {
        val a = context.obtainStyledAttributes(attrs, R.styleable.DialogPreference, 0, 0)
        mDialogTitle = a.getString(R.styleable.DialogPreference_dialogTitle)
        if (mDialogTitle == null) {
            // Fallback on the regular title of the preference
            // (the one that is seen in the list)
            mDialogTitle = title
        }
        mDialogIcon = a.getDrawable(R.styleable.DialogPreference_dialogIcon)
        mPositiveButtonText = a.getString(R.styleable.DialogPreference_positiveButtonText)
        mNegativeButtonText = a.getString(R.styleable.DialogPreference_negativeButtonText)
        mDialogLayoutResId =
            a.getResourceId(R.styleable.DialogPreference_dialogLayout, mDialogLayoutResId)
        a.recycle()
    }

    var dialogTitle: CharSequence?
        /**
         * Returns the title to be shown on subsequent dialogs.
         *
         * @return The title.
         */
        get() = mDialogTitle

        /**
         * Sets the title of the dialog. This will be shown on subsequent dialogs.
         *
         * @param dialogTitle The title.
         */
        set(dialogTitle) {
            mDialogTitle = dialogTitle
        }

    var dialogIcon: Drawable?
        /**
         * Returns the icon to be shown on subsequent dialogs.
         *
         * @return The icon, as a [Drawable].
         */
        get() = mDialogIcon

        /**
         * Sets the icon of the dialog. This will be shown on subsequent dialogs.
         *
         * @param dialogIcon The icon, as a [Drawable].
         */
        set(dialogIcon) {
            mDialogIcon = dialogIcon
        }

    var positiveButtonText: CharSequence?
        /**
         * Returns the text of the negative button to be shown on subsequent
         * dialogs.
         *
         * @return The text of the positive button.
         */
        get() = mPositiveButtonText

        /**
         * Sets the text of the negative button of the dialog. This will be shown on
         * subsequent dialogs.
         *
         * @param positiveButtonText The text of the negative button.
         */
        set(positiveButtonText) {
            mPositiveButtonText = positiveButtonText
        }

    var negativeButtonText: CharSequence?
        /**
         * Returns the text of the negative button to be shown on subsequent
         * dialogs.
         *
         * @return The text of the negative button.
         */
        get() = mNegativeButtonText

        /**
         * Sets the text of the negative button of the dialog. This will be shown on
         * subsequent dialogs.
         *
         * @param negativeButtonText The text of the negative button.
         */
        set(negativeButtonText) {
            mNegativeButtonText = negativeButtonText
        }

    var dialogLayoutResource: Int
        /**
         * Returns the layout resource that is used as the content View for
         * subsequent dialogs.
         *
         * @return The layout resource.
         */
        get() = mDialogLayoutResId

        /**
         * Sets the layout resource that is inflated as the [View] to be shown
         * as the content View of subsequent dialogs.
         *
         * @param dialogLayoutResource The layout resource ID to be inflated.
         */
        set(dialogLayoutResource) {
            mDialogLayoutResId = dialogLayoutResource
        }

    /**
     * @param dialogTitleResId The dialog title as a resource.
     * @see .setDialogTitle
     */
    fun setDialogTitle(dialogTitleResId: Int) {
        mDialogTitle = context.getString(dialogTitleResId)
    }

    /**
     * Sets the icon (resource ID) of the dialog. This will be shown on
     * subsequent dialogs.
     *
     * @param dialogIconRes The icon, as a resource ID.
     */
    fun setDialogIcon(@DrawableRes dialogIconRes: Int) {
        mDialogIcon = ContextCompat.getDrawable(context, dialogIconRes)
    }

    /**
     * @param positiveButtonTextResId The positive button text as a resource.
     * @see .setPositiveButtonText
     */
    fun setPositiveButtonText(@StringRes positiveButtonTextResId: Int) {
        mPositiveButtonText = context.getString(positiveButtonTextResId)
    }

    /**
     * @param negativeButtonTextResId The negative button text as a resource.
     * @see .setNegativeButtonText
     */
    fun setNegativeButtonText(@StringRes negativeButtonTextResId: Int) {
        mNegativeButtonText = context.getString(negativeButtonTextResId)
    }

    /**
     * Prepares the dialog builder to be shown when the preference is clicked.
     * Use this to set custom properties on the dialog.
     *
     * Do not [AlertDialog.Builder.create] or
     * [AlertDialog.Builder.show].
     */
    protected open fun onPrepareDialogBuilder(builder: AlertDialog.Builder) {}

    override fun onClick() {
        if (mDialog != null && mDialog!!.isShowing) return
        showDialog(null)
    }

    /**
     * Shows the dialog associated with this Preference. This is normally initiated
     * automatically on clicking on the preference. Call this method if you need to
     * show the dialog on some other event.
     *
     * @param state Optional instance state to restore on the dialog
     */
    protected open fun showDialog(state: Bundle?) {
        val context = context

        mWhichButtonClicked = DialogInterface.BUTTON_NEGATIVE
        mBuilder = AlertDialog.Builder(context)
            .setTitle(mDialogTitle)
            .setIcon(mDialogIcon)
            .setPositiveButton(mPositiveButtonText, this)
            .setNegativeButton(mNegativeButtonText, this)

        onCreateDialogView()?.let { view ->
            view.parent?.let {
                (it as ViewGroup).removeView(view)
            }
            onBindDialogView(view)
            mBuilder!!.setView(view)
        }

        onPrepareDialogBuilder(mBuilder!!)
        // PreferenceUtils.registerOnActivityDestroyListener(this, this);

        // Create the dialog
        mDialog = mBuilder!!.create()
        val dialog = mDialog!!
        state?.let { dialog.onRestoreInstanceState(it) }
        if (needInputMethod) {
            requestInputMethod(dialog)
        }
        dialog.setOnDismissListener(this)
        dialog.show()

        onDialogCreated(dialog)
    }

    /**
     * Returns whether the preference needs to display a soft input method when the dialog
     * is displayed. Default is false. Subclasses should override this method if they need
     * the soft input method brought up automatically.
     */
    open val needInputMethod: Boolean = false

    /**
     * Sets the required flags on the dialog window to enable input method window to show up.
     */
    private fun requestInputMethod(dialog: Dialog) {
        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)
    }

    /**
     * Creates the content view for the dialog (if a custom content view is
     * required). By default, it inflates the dialog layout resource if it is
     * set.
     *
     * @return The content View for the dialog.
     * @see .setLayoutResource
     */
    protected open fun onCreateDialogView(): View? {
        if (mDialogLayoutResId == 0) return null
        val inflater = LayoutInflater.from(mBuilder!!.context)
        return inflater.inflate(mDialogLayoutResId, null)
    }

    /**
     * Binds views in the content View of the dialog to data.
     *
     * @param view The content View of the dialog, if it is custom.
     */
    protected open fun onBindDialogView(view: View) {}

    protected open fun onDialogCreated(dialog: AlertDialog) {}

    override fun onClick(dialog: DialogInterface, which: Int) {
        mWhichButtonClicked = which
    }

    override fun onDismiss(dialog: DialogInterface) {
        mDialog = null
        onDialogClosed(mWhichButtonClicked == DialogInterface.BUTTON_POSITIVE)
    }

    /**
     * Called when the dialog is dismissed and should be used to save data to
     * the [SharedPreferences].
     *
     * @param positiveResult Whether the positive button was clicked (true), or
     * the negative button was clicked or the dialog was canceled (false).
     */
    protected open fun onDialogClosed(positiveResult: Boolean) {}

    val dialog: Dialog?
        /**
         * Gets the dialog that is shown by this preference.
         *
         * @return The dialog, or null if a dialog is not being shown.
         */
        get() = mDialog

    override fun onSaveInstanceState(): Parcelable? {
        val superState = super.onSaveInstanceState()
        if (mDialog == null || !mDialog!!.isShowing) {
            return superState
        }

        val myState = SavedState(superState)
        myState.isDialogShowing = true
        myState.dialogBundle = mDialog!!.onSaveInstanceState()
        return myState
    }

    override fun onRestoreInstanceState(state: Parcelable?) {
        if (state == null || state.javaClass != SavedState::class.java) {
            // Didn't save state for us in onSaveInstanceState
            super.onRestoreInstanceState(state)
            return
        }

        val myState = state as SavedState
        super.onRestoreInstanceState(myState.superState)
        if (myState.isDialogShowing) {
            showDialog(myState.dialogBundle)
        }
    }

    private class SavedState : BaseSavedState {
        var isDialogShowing: Boolean = false
        var dialogBundle: Bundle? = null

        constructor(source: Parcel) : super(source) {
            isDialogShowing = source.readInt() == 1
            dialogBundle = source.readBundle(DialogPreference::class.java.classLoader)
        }

        constructor(superState: Parcelable?) : super(superState)

        override fun writeToParcel(dest: Parcel, flags: Int) {
            super.writeToParcel(dest, flags)
            dest.writeInt(if (isDialogShowing) 1 else 0)
            dest.writeBundle(dialogBundle)
        }

        companion object CREATOR : Parcelable.Creator<SavedState> {
            override fun createFromParcel(`in`: Parcel): SavedState = SavedState(`in`)

            override fun newArray(size: Int): Array<SavedState?> = arrayOfNulls(size)
        }
    }
}
