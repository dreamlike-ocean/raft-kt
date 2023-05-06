package top.dreamlike.configurarion

import io.vertx.core.Vertx
import io.vertx.core.VertxOptions
import io.vertx.core.net.SocketAddress
import top.dreamlike.raft.ServerId
import java.util.concurrent.TimeUnit

data class Configuration(val me :ServerId,val raftPort: Int = 8081, val httpPort: Int = 8080, val httpVertx : Vertx, val initPeer : Map<ServerId, SocketAddress>) {
    val raftVertxOptions = singleVertxConfig()
}
fun singleVertxConfig(): VertxOptions {
    return VertxOptions()
        .setBlockedThreadCheckInterval(10000000L)
        .setBlockedThreadCheckIntervalUnit(TimeUnit.DAYS)
        .setEventLoopPoolSize(1)
        .setWorkerPoolSize(1)
        .setInternalBlockingPoolSize(1)
}