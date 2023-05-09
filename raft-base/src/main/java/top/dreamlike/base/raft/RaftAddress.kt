package top.dreamlike.base.raft

import io.vertx.core.net.SocketAddress

data class RaftAddress(val port: Int, val host: String) {
    constructor(address: SocketAddress?) : this(address?.port() ?: -1, address?.host() ?: "")

    fun SocketAddress() = SocketAddress.inetSocketAddress(port, host)
}
