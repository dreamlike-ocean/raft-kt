package top.dreamlike.util

import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

class CountDownLatch(count: Int) {
    private var waiter: Continuation<Unit>? = null
    private val count = AtomicInteger(count)
    suspend fun wait() = suspendCoroutine<Unit> {
        waiter = it
    }

    fun countDown() {
        val decrementAndGet = count.decrementAndGet()
        if (decrementAndGet == 0) {
            waiter?.resume(Unit)
        }
    }

}