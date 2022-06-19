package top.dreamlike.util

import io.vertx.core.Vertx
import io.vertx.kotlin.core.vertxOptionsOf
import kotlinx.coroutines.coroutineScope

suspend fun async(fn: suspend () -> Unit) {
    coroutineScope {
        async {
            fn()
        }
    }
}

//提交一个空方法直接启动线程
fun SingleThreadVertx() = Vertx.vertx(vertxOptionsOf(eventLoopPoolSize = 1)).apply {
    exceptionHandler { println(it.message) }
    runOnContext { }
}

/**
 * 从startIndex开始删除元素
 * @param startIndex 左闭
 */
fun <E> MutableList<E>.removeAll(startIndex: Int) {
    if (startIndex < 0) throw IllegalArgumentException("index为负数")
    if (startIndex >= size) return
    for (i in (this.size - 1) downTo startIndex) {
        removeAt(i)
    }
}