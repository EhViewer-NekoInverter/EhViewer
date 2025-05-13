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
package com.hippo.util

import android.text.Html
import android.text.Spanned
import com.hippo.text.URLImageGetter
import com.hippo.widget.ObservedTextView

@Suppress("DEPRECATION")
fun loadHtml(source: String): Spanned = if (isAtLeastN) {
    Html.fromHtml(source, Html.FROM_HTML_MODE_LEGACY)
} else {
    Html.fromHtml(source)
}

@Suppress("DEPRECATION")
fun loadHtml(source: String?, textView: ObservedTextView): Spanned = if (isAtLeastN) {
    Html.fromHtml(source, Html.FROM_HTML_MODE_LEGACY, URLImageGetter(textView), null)
} else {
    Html.fromHtml(source, URLImageGetter(textView), null)
}
