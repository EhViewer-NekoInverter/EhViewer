/*
 * Copyright 2024 Moedog
 *
 * This file is part of EhViewer
 *
 * EhViewer is free software: you can redistribute it and/or
 * modify it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * EhViewer is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with EhViewer.
 * If not, see <https://www.gnu.org/licenses/>.
 */
package com.hippo.ehviewer.preference

import android.annotation.SuppressLint
import android.content.Context
import android.content.DialogInterface
import android.util.AttributeSet
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.TextView.OnEditorActionListener
import androidx.appcompat.app.AlertDialog
import com.hippo.ehviewer.R
import com.hippo.ehviewer.Settings
import com.hippo.okhttp.CHROME_USER_AGENT
import com.hippo.preference.DialogPreference

@SuppressLint("InflateParams")
class UserAgentPreference(
    context: Context,
    attrs: AttributeSet? = null,
) : DialogPreference(context, attrs),
    View.OnClickListener,
    OnEditorActionListener {
    private lateinit var mDialog: AlertDialog
    private lateinit var mButton: Button
    private var mUserAgent: String? = Settings.userAgent
    private val view: View =
        LayoutInflater.from(context).inflate(R.layout.dialog_edittext_builder, null)
    private val editText: EditText = view.findViewById(R.id.edit_text)

    init {
        updateSummary()
    }

    private fun updateSummary() {
        summary = mUserAgent
    }

    override fun onCreateDialogView(): View = view

    override fun onDialogCreated(dialog: AlertDialog) {
        super.onDialogCreated(dialog)
        mDialog = dialog
        mButton = dialog.getButton(DialogInterface.BUTTON_POSITIVE)
        mButton.setOnClickListener(this)
        editText.isSingleLine = false
        editText.setText(mUserAgent)
        editText.setSelection(editText.text.length)
        editText.setOnEditorActionListener(this)
    }

    override fun onClick(v: View) {
        mUserAgent = editText.text.toString().trim().ifBlank { null } ?: CHROME_USER_AGENT
        Settings.putUserAgent(mUserAgent)
        updateSummary()
        mDialog.dismiss()
    }

    override fun onEditorAction(v: TextView?, p1: Int, event: KeyEvent?): Boolean {
        mButton.performClick()
        return true
    }
}
