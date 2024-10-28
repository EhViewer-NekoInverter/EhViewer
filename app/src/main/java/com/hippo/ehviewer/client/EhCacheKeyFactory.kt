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
package com.hippo.ehviewer.client

private val NormalPreviewKeyRegex = Regex("(/\\d+-\\d+)\\.\\w+$")

fun getThumbKey(gid: Long): String = "preview:large:$gid:0"

fun getNormalPreviewKey(url: String) = NormalPreviewKeyRegex.find(url)?.groupValues[1] ?: url

fun getLargePreviewKey(gid: Long, index: Int) = "preview:large:$gid:$index"

fun getImageKey(gid: Long, index: Int) = "image:$gid:$index"

val String.isNormalPreviewKey
    get() = startsWith('/')
