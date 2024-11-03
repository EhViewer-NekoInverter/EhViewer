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

import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime

fun LocalDate.toEpochMillis(timeZone: TimeZone = TimeZone.UTC): Long = atStartOfDayIn(timeZone).toEpochMilliseconds()

fun LocalDateTime.toEpochMillis(timeZone: TimeZone = TimeZone.UTC): Long = toInstant(timeZone).toEpochMilliseconds()

fun Long.toLocalDateTime(timeZone: TimeZone = TimeZone.UTC): LocalDateTime = Instant.fromEpochMilliseconds(this).toLocalDateTime(timeZone)
