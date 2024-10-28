/*
 * Copyright 2022 Moedog
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
package com.hippo.app

import android.annotation.SuppressLint
import android.content.Context
import android.content.DialogInterface
import android.view.KeyEvent
import android.view.LayoutInflater
import android.widget.CheckBox
import android.widget.EditText
import android.widget.TextView
import android.widget.TextView.OnEditorActionListener
import androidx.appcompat.app.AlertDialog
import com.google.android.material.textfield.TextInputLayout
import com.hippo.ehviewer.R

@SuppressLint("InflateParams")
class EditTextCheckBoxDialogBuilder(
    context: Context,
    text: String?,
    hint: String?,
    checkText: String?,
    checked: Boolean,
) : AlertDialog.Builder(
    context,
),
    OnEditorActionListener {
    private val mCheckBox: CheckBox
    val isChecked: Boolean
        get() = mCheckBox.isChecked
    private val mTextInputLayout: TextInputLayout
    private val editText: EditText
    private var mDialog: AlertDialog? = null
    val text: String
        get() = editText.text.toString()

    fun setError(error: CharSequence?) {
        mTextInputLayout.error = error
    }

    override fun create(): AlertDialog {
        mDialog = super.create()
        return mDialog as AlertDialog
    }

    override fun onEditorAction(v: TextView?, p1: Int, event: KeyEvent?): Boolean = if (mDialog != null) {
        val button = mDialog!!.getButton(DialogInterface.BUTTON_POSITIVE)
        button?.performClick()
        true
    } else {
        false
    }

    init {
        val view = LayoutInflater.from(getContext()).inflate(R.layout.dialog_edittextcheckbox_builder, null)
        setView(view)
        mCheckBox = view.findViewById(R.id.checkbox)
        mCheckBox.text = checkText
        mCheckBox.isChecked = checked
        view.setOnClickListener { mCheckBox.toggle() }
        editText = view.findViewById(R.id.edit_text)
        editText.setText(text)
        editText.setSelection(editText.text.length)
        editText.setOnEditorActionListener(this)
        mTextInputLayout = view.findViewById(R.id.text_input_layout)
        mTextInputLayout.hint = hint
    }
}
