package top.dreamlike.base.raft

import io.vertx.core.net.SocketAddress

data class RaftAddress(val port: Int, val host: String) {
    fun SocketAddress() = SocketAddress.inetSocketAddress(port, host)
}
