package top.dreamlike.configurarion

import io.vertx.core.Vertx
import io.vertx.core.VertxOptions

class Configuration(val raftPort: Int = 8081, val httpPort: Int = 8080, val httpVertx : Vertx) {
    val raftVertxOptions = singleVertxConfig()
}
fun singleVertxConfig(): VertxOptions {
    return VertxOptions()
        .setEventLoopPoolSize(1)
        .setWorkerPoolSize(1)
        .setInternalBlockingPoolSize(1)
}