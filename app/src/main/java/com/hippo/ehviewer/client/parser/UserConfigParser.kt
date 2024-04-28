package com.hippo.ehviewer.client.parser

import com.hippo.ehviewer.Settings
import com.hippo.yorozuya.unescapeXml

object UserConfigParser {
    private const val U_CONFIG_TEXT = "Selected Profile"
    private val FAV_CAT_PATTERN = Regex("<input type=\"text\" name=\"favorite_\\d\" value=\"([^\"]+)\"")

    fun parse(body: String) {
        check(U_CONFIG_TEXT in body) { "Unable to load user config!" }
        val iterator = FAV_CAT_PATTERN.findAll(body).iterator()
        val favCat = Array(10) { iterator.next().groupValues[1].unescapeXml() }
        Settings.favCat = favCat
    }
}
