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

import androidx.collection.MutableScatterMap
import androidx.collection.mutableScatterMapOf
import io.ktor.utils.io.pool.DefaultPool
import kotlinx.coroutines.sync.Mutex

class MutexTracker(mutex: Mutex = Mutex(), private var count: Int = 0) : Mutex by mutex {
    operator fun inc() = apply { count++ }
    operator fun dec() = apply { count-- }
    val isFree
        get() = count == 0
}

object MutexPool : DefaultPool<MutexTracker>(capacity = 32) {
    override fun produceInstance() = MutexTracker()
    override fun validateInstance(mutex: MutexTracker) {
        check(!mutex.isLocked)
        check(mutex.isFree)
    }
}

class NamedMutex<K>(val active: MutableScatterMap<K, MutexTracker> = mutableScatterMapOf()) : LockPool<MutexTracker, K> {
    override fun acquire(key: K) = synchronized(active) { active.getOrPut(key) { MutexPool.borrow() }.inc() }
    override fun release(key: K, lock: MutexTracker) = synchronized(active) {
        lock.dec()
        if (lock.isFree) {
            active.remove(key)
            MutexPool.recycle(lock)
        }
    }
    override suspend fun MutexTracker.lock() = lock()
    override fun MutexTracker.tryLock() = tryLock()
    override fun MutexTracker.unlock() = unlock()
}
