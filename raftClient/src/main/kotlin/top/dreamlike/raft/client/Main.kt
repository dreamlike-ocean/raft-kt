package top.dreamlike.raft.client

import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateTimeDeserializer
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer
import com.fasterxml.jackson.module.kotlin.KotlinFeature
import com.fasterxml.jackson.module.kotlin.KotlinModule
import io.vertx.core.Vertx
import io.vertx.core.json.jackson.DatabindCodec
import io.vertx.ext.web.client.WebClient
import io.vertx.ext.web.codec.BodyCodec
import io.vertx.kotlin.coroutines.await
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.reflect.full.createType

suspend fun main() {
    Unit::class.createType()

    initJacksonMapper()
    val vertx = Vertx.vertx()
    val client = WebClient.create(vertx)
    val s = client.getAbs("http://www.baidu.com")
        .`as`(BodyCodec.string())
        .send().await().body()
    println(s)
}

fun initJacksonMapper() {
    val javaTimeModule = JavaTimeModule()
    javaTimeModule.addSerializer(
        LocalDateTime::class.java, LocalDateTimeSerializer(
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
        )
    );
    javaTimeModule.addDeserializer(
        LocalDateTime::class.java,
        LocalDateTimeDeserializer(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
    )
    DatabindCodec.mapper().registerModule(javaTimeModule)
    DatabindCodec.prettyMapper().registerModule(javaTimeModule)

    val kotlinModule = KotlinModule.Builder()
        .withReflectionCacheSize(512)
        .configure(KotlinFeature.NullToEmptyCollection, true)
        .configure(KotlinFeature.NullToEmptyMap, true)
        .configure(KotlinFeature.NullIsSameAsDefault, false)
        .configure(KotlinFeature.SingletonSupport, false)
        .configure(KotlinFeature.StrictNullChecks, false)
        .build()
    DatabindCodec.mapper().registerModule(kotlinModule)
    DatabindCodec.prettyMapper().registerModule(kotlinModule)
}
