package top.dreamlike.raft.rpc

import io.vertx.core.Future
import io.vertx.core.Vertx


interface RaftRpcHandler {
    fun init(vertx: Vertx, raftPort: Int): Future<Unit>

}