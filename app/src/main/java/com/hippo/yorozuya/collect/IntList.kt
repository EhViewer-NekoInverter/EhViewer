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
package com.hippo.yorozuya.collect

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Suppress("JavaDefaultMethodsNotOverriddenByDelegation")
@Parcelize
class IntList @JvmOverloads constructor(
    private val delegate: MutableList<Int> = mutableListOf(),
) : Parcelable, MutableList<Int> by delegate {
    override val size: Int
        get() = delegate.size

    override fun isEmpty(): Boolean = delegate.isEmpty()

    override fun clear() = delegate.clear()

    override fun add(element: Int): Boolean = delegate.add(element)

    override fun removeAt(index: Int): Int = delegate.removeAt(index)

    fun getInternalArray(): IntArray = delegate.toIntArray()
}
