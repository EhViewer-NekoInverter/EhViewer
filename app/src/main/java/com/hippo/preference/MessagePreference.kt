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

import android.content.Context
import android.text.Html
import android.text.method.LinkMovementMethod
import android.util.AttributeSet
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import com.hippo.ehviewer.R

open class MessagePreference(
    context: Context,
    attrs: AttributeSet? = null,
) : DialogPreference(
    context,
    attrs,
) {
    private var mDialogMessage: CharSequence? = null
    private var mDialogMessageLinkify = false

    init {
        val a = context.obtainStyledAttributes(attrs, R.styleable.MessagePreference, 0, 0)
        val message = a.getString(R.styleable.MessagePreference_dialogMessage)
        mDialogMessage = if (a.getBoolean(R.styleable.MessagePreference_dialogMessageHtml, false)) {
            Html.fromHtml(message, Html.FROM_HTML_MODE_LEGACY)
        } else {
            message
        }
        mDialogMessageLinkify =
            a.getBoolean(R.styleable.MessagePreference_dialogMessageLinkify, false)
        a.recycle()
    }

    fun setDialogMessage(message: CharSequence?) {
        mDialogMessage = message
    }

    override fun onPrepareDialogBuilder(builder: AlertDialog.Builder) {
        super.onPrepareDialogBuilder(builder)
        builder.setMessage(mDialogMessage)
        builder.setPositiveButton(android.R.string.ok, this)
        builder.setNegativeButton(null, null)
    }

    override fun onDialogCreated(dialog: AlertDialog) {
        super.onDialogCreated(dialog)

        if (mDialogMessageLinkify) {
            val messageView = dialog.findViewById<View>(android.R.id.message)
            if (messageView is TextView) {
                messageView.movementMethod = LinkMovementMethod.getInstance()
            }
        }
    }
}
