package com.safemode.llconnect.data

import kotlin.coroutines.cancellation.CancellationException

/**
 * Like [runCatching], but never swallows coroutine cancellation.
 *
 * In suspend code `runCatching` catches [CancellationException] too, so a cancelled job (the user
 * left the screen, the ViewModel was cleared) is turned into a normal failure result and the
 * cancelled work keeps running. This rethrows cancellation and catches everything else, so
 * structured concurrency still works.
 */
inline fun <T> runCatchingCancellable(block: () -> T): Result<T> =
    try {
        Result.success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        Result.failure(e)
    }
