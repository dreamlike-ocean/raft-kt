package top.dreamlike.configurarion

import io.vertx.core.Vertx

import io.vertx.core.net.SocketAddress
import top.dreamlike.base.ServerId
import top.dreamlike.base.util.singleVertxConfig


data class Configuration(
    val me: ServerId,
    val raftPort: Int = 8081,
    val httpPort: Int = 8080,
    val httpVertx: Vertx,
    val initPeer: Map<ServerId, SocketAddress>
) {
    val raftVertxOptions = singleVertxConfig()
}
