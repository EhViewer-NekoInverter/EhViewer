/*
 * Copyright 2023 Moedog
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

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import java.io.InterruptedIOException
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

/**
 * Think twice before using this. This is a delicate API. It is easy to accidentally create resource or memory leaks when GlobalScope is used.
 *
 * **Possible replacements**
 * - suspend function
 * - custom scope like view or presenter scope
 */
@DelicateCoroutinesApi
fun launchUI(block: suspend CoroutineScope.() -> Unit): Job = GlobalScope.launch(Dispatchers.Main, CoroutineStart.DEFAULT, block)

/**
 * Think twice before using this. This is a delicate API. It is easy to accidentally create resource or memory leaks when GlobalScope is used.
 *
 * **Possible replacements**
 * - suspend function
 * - custom scope like view or presenter scope
 */
@DelicateCoroutinesApi
fun launchIO(block: suspend CoroutineScope.() -> Unit): Job = GlobalScope.launch(Dispatchers.IO, CoroutineStart.DEFAULT, block)

/**
 * Think twice before using this. This is a delicate API. It is easy to accidentally create resource or memory leaks when GlobalScope is used.
 *
 * **Possible replacements**
 * - suspend function
 * - custom scope like view or presenter scope
 */
@DelicateCoroutinesApi
fun launchNow(block: suspend CoroutineScope.() -> Unit): Job = GlobalScope.launch(Dispatchers.Main, CoroutineStart.UNDISPATCHED, block)

fun CoroutineScope.launchUI(block: suspend CoroutineScope.() -> Unit): Job = launch(Dispatchers.Main, block = block)

fun CoroutineScope.launchIO(block: suspend CoroutineScope.() -> Unit): Job = launch(Dispatchers.IO, block = block)

fun CoroutineScope.launchNonCancellable(block: suspend CoroutineScope.() -> Unit): Job = launchIO { withContext(NonCancellable, block) }

suspend fun <T> withUIContext(block: suspend CoroutineScope.() -> T) = withContext(Dispatchers.Main, block)

suspend fun <T> withIOContext(block: suspend CoroutineScope.() -> T) = withContext(Dispatchers.IO, block)

suspend fun <T> withNonCancellableContext(block: suspend CoroutineScope.() -> T) = withContext(NonCancellable, block)

// moe.tarsin.coroutines
inline fun <reified T : Throwable> Result<*>.except(): Result<*> = onFailure { if (it is T) throw it }

inline fun <R> runSuspendCatching(block: () -> R): Result<R> = runCatching(block).apply { except<CancellationException>() }

inline fun <T, R> T.runSuspendCatching(block: T.() -> R): Result<R> = runCatching(block).apply { except<CancellationException>() }

// See https://github.com/Kotlin/kotlinx.coroutines/issues/3551
suspend inline fun <T> runInterruptibleOkio(
    context: CoroutineContext = EmptyCoroutineContext,
    crossinline block: () -> T,
): T = runInterruptible(context) {
    try {
        block()
    } catch (e: InterruptedIOException) {
        if (Thread.currentThread().isInterrupted) {
            // Coroutine cancelled
            throw InterruptedException().initCause(e)
        } else {
            // AsyncTimeout reached
            throw e
        }
    }
}
