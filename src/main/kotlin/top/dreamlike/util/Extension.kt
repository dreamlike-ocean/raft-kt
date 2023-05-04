package top.dreamlike.util

import io.vertx.core.Vertx
import top.dreamlike.configurarion.singleVertxConfig


fun SingleThreadVertx() = Vertx.vertx(singleVertxConfig())

/**
 * 从startIndex开始删除元素
 * @param startIndex 左闭
 */
fun <E> MutableList<E>.removeAll(startIndex: Int): List<E> {
    if (startIndex < 0) throw IllegalArgumentException("index为负数")
    if (startIndex >= size) return listOf()
    val list = mutableListOf<E>()
    for (i in (this.size - 1) downTo startIndex) {
        list.add(removeAt(i))
    }
    return list
}

fun Vertx.countEventLoop() = this.nettyEventLoopGroup().count()

fun main() {
    println(Vertx.vertx().countEventLoop())
}