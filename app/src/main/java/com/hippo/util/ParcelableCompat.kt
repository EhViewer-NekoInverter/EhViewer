/*
 * Copyright 2023 Tarsin Norbin
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

import android.content.Intent
import android.os.Bundle
import android.os.Parcel
import android.os.Parcelable
import android.util.SparseArray
import androidx.core.content.IntentCompat
import androidx.core.os.BundleCompat
import androidx.core.os.ParcelCompat

inline fun <reified T : Parcelable> Bundle.getParcelableCompat(key: String?): T? = BundleCompat.getParcelable(this, key, T::class.java)

inline fun <reified T : Parcelable> Bundle.getSparseParcelableArrayCompat(key: String?): SparseArray<T?>? = BundleCompat.getSparseParcelableArray(this, key, T::class.java)

inline fun <reified T : Parcelable> Intent.getParcelableExtraCompat(key: String?): T? = IntentCompat.getParcelableExtra(this, key, T::class.java)

inline fun <reified T : Parcelable> Parcel.readParcelableCompat(key: ClassLoader?): T? = ParcelCompat.readParcelable(this, key, T::class.java)
