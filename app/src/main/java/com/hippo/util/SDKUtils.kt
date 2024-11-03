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

import android.os.Build
import android.os.ext.SdkExtensions

val isAtLeastN = Build.VERSION.SDK_INT >= Build.VERSION_CODES.N
val isAtLeastO = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
val isAtLeastOMR1 = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1
val isAtLeastP = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
val isAtLeastQ = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
val isAtLeastR = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
val isAtLeastS = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
val isAtLeastT = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
val isAtLeastU = Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE
val isAtLeastSExtension7 = isAtLeastR && SdkExtensions.getExtensionVersion(Build.VERSION_CODES.S) >= 7
