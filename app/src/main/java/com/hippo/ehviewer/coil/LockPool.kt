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
package com.hippo.ehviewer.coil

import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

interface LockPool<Lock, K> {
    fun acquire(key: K): Lock
    fun release(key: K, lock: Lock)
    suspend fun Lock.lock()
    fun Lock.tryLock(): Boolean
    fun Lock.unlock()
}

suspend inline fun <Lock, K, R> LockPool<Lock, K>.withLock(key: K, action: () -> R): R {
    contract {
        callsInPlace(action, InvocationKind.EXACTLY_ONCE)
    }
    val lock = acquire(key)
    return try {
        lock.lock()
        return try {
            action()
        } finally {
            lock.unlock()
        }
    } finally {
        release(key, lock)
    }
}

suspend inline fun <Lock, K, R> LockPool<Lock, K>.withLockNeedSuspend(key: K, action: () -> R): Pair<R, Boolean> {
    contract {
        callsInPlace(action, InvocationKind.EXACTLY_ONCE)
    }
    val lock = acquire(key)
    return try {
        val mustSuspend = !lock.tryLock()
        if (mustSuspend) lock.lock()
        return try {
            action() to mustSuspend
        } finally {
            lock.unlock()
        }
    } finally {
        release(key, lock)
    }
}
