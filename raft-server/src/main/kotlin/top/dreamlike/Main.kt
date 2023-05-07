package top.dreamlike

import io.vertx.core.CompositeFuture
import io.vertx.core.Vertx
import io.vertx.core.net.SocketAddress
import top.dreamlike.base.util.block
import top.dreamlike.base.util.initJacksonMapper
import top.dreamlike.configurarion.Configuration
import top.dreamlike.server.RaftServer

fun main() {
    initJacksonMapper()
    val httpPort = 8080
    val configurations = mutableListOf<Configuration>()
    val vertx = Vertx.vertx()
    val nodes = mapOf(
        "raft-0" to SocketAddress.inetSocketAddress(80, "localhost"),
        "raft-1" to SocketAddress.inetSocketAddress(81, "localhost"),
        "raft-2" to SocketAddress.inetSocketAddress(82, "localhost"),
    )
    for (i in (0..2)) {
        val nodeId = "raft-$i"
        configurations.add(
            Configuration(
                nodeId,
                nodes[nodeId]!!.port(),
                httpPort + i,
                vertx,
                HashMap(nodes).apply { remove(nodeId) })
        )
    }
    val servers = configurations
        .map { RaftServer(it) }

    CompositeFuture.all(servers.map(RaftServer::start)).block()
}

