package org.hyperledger.ariesframework.util

import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll

suspend inline fun <T, R> Iterable<T>.concurrentMap(
    crossinline transform: suspend (T) -> R,
): List<R> {
    return this.map {
        GlobalScope.async {
            transform(it)
        }
    }.awaitAll()
}

suspend inline fun <T> Iterable<T>.concurrentForEach(
    crossinline action: suspend (T) -> Unit,
) {
    this.map {
        GlobalScope.async {
            action(it)
        }
    }.awaitAll()
}

suspend inline fun <K, V> Map<out K, V>.concurrentForEach(
    crossinline action: suspend (Map.Entry<K, V>) -> Unit,
) {
    this.map {
        GlobalScope.async {
            action(it)
        }
    }.awaitAll()
}
