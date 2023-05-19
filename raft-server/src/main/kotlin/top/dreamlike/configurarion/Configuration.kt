package top.dreamlike.configurarion

import io.vertx.core.Vertx
import io.vertx.core.VertxOptions
import io.vertx.core.json.JsonObject
import top.dreamlike.base.RandomServerId
import top.dreamlike.base.ServerId
import top.dreamlike.base.raft.RaftAddress
import top.dreamlike.base.util.singleVertxConfig

class Configuration(
    val me: ServerId = RandomServerId(),
    val raftPort: Int = 8080,
    val httpPort: Int = 80,
    httpVertxOption: Map<String, Any> = mutableMapOf(),
    initPeerArgs: Map<ServerId, RaftAddress> = mapOf(),
    val connectNode: RaftAddress? = null
) {
    val initPeer = initPeerArgs
    val raftVertxOptions = singleVertxConfig()
    val httpVertx = Vertx.vertx(VertxOptions(JsonObject(httpVertxOption)))
    override fun toString(): String {
        return "Configuration(me='$me', raftPort=$raftPort, httpPort=$httpPort, initPeer=$initPeer,\n raftVertxOptions=$raftVertxOptions,\n httpVertx=$httpVertx)"
    }

}
