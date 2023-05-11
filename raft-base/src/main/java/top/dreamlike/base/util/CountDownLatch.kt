package top.dreamlike.base.util

import io.vertx.core.Promise
import io.vertx.kotlin.coroutines.await
import java.util.concurrent.atomic.AtomicInteger

class CountDownLatch(count: Int) {
    private var waiter: Promise<Unit> = Promise.promise()
    private val count = AtomicInteger(count)

    init {
        if (count == 0) {
            waiter.complete()
        }
    }

    suspend fun wait() = waiter.future().await()

    fun future() = waiter.future()

    fun countDown() {
        val decrementAndGet = count.decrementAndGet()
        if (decrementAndGet == 0) {
            waiter.complete()
        }
    }

}