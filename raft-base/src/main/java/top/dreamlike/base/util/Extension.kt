package top.dreamlike.base.util

import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateTimeDeserializer
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer
import com.fasterxml.jackson.module.kotlin.KotlinFeature
import com.fasterxml.jackson.module.kotlin.KotlinModule
import io.netty.buffer.Unpooled
import io.netty.util.internal.EmptyArrays
import io.vertx.core.Future
import io.vertx.core.Vertx
import io.vertx.core.VertxOptions
import io.vertx.core.buffer.Buffer
import io.vertx.core.json.jackson.DatabindCodec
import io.vertx.ext.web.Route
import io.vertx.ext.web.RoutingContext
import io.vertx.kotlin.coroutines.dispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit


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

fun <T> Future<T>.block(): T =  toCompletionStage().toCompletableFuture().get()


fun Route.suspendHandle(fn : suspend (RoutingContext) -> Unit) {
    handler {
        val dispatcher = it.vertx().dispatcher()
        CoroutineScope(dispatcher).launch {
            fn(it)
        }
    }
}

fun initJacksonMapper() {
    val javaTimeModule = JavaTimeModule()
    javaTimeModule.addSerializer(LocalDateTime::class.java, LocalDateTimeSerializer(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
    javaTimeModule.addDeserializer(LocalDateTime::class.java,
        LocalDateTimeDeserializer(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
    )
    DatabindCodec.mapper().registerModule(javaTimeModule)
    DatabindCodec.prettyMapper().registerModule(javaTimeModule)

    val kotlinModule = KotlinModule.Builder()
        .withReflectionCacheSize(512)
        .configure(KotlinFeature.NullToEmptyCollection, false)
        .configure(KotlinFeature.NullToEmptyMap, false)
        .configure(KotlinFeature.NullIsSameAsDefault, false)
        .configure(KotlinFeature.SingletonSupport, false)
        .configure(KotlinFeature.StrictNullChecks, false)
        .build()
    DatabindCodec.mapper().registerModule(kotlinModule)
    DatabindCodec.prettyMapper().registerModule(kotlinModule)
}

fun wrap(array: ByteArray?) =
    Buffer.buffer(Unpooled.wrappedBuffer(array ?: EmptyArrays.EMPTY_BYTES))

fun wrapSlice(array: ByteArray, startIndex: Int = 0) =
    Buffer.buffer(Unpooled.wrappedBuffer(array).slice(startIndex, array.size - startIndex))

val EMPTY_BUFFER = Buffer.buffer()

fun singleVertxConfig(): VertxOptions {
    return VertxOptions()
        .setBlockedThreadCheckInterval(10000000L)
        .setBlockedThreadCheckIntervalUnit(TimeUnit.DAYS)
        .setEventLoopPoolSize(1)
        .setWorkerPoolSize(1)
        .setInternalBlockingPoolSize(1)
}
