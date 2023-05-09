package top.dreamlike.server

import io.vertx.core.DeploymentOptions
import io.vertx.core.Vertx
import top.dreamlike.base.util.countEventLoop
import top.dreamlike.configurarion.Configuration
import top.dreamlike.raft.Raft

class RaftServer(val configuration: Configuration) {

    val raft : Raft
    val raftServerVerticleFactory : () -> RaftServerVerticle
    init {
        var raftOption = configuration.raftVertxOptions
        val raftVertx = Vertx.vertx(raftOption)
        raft = Raft(raftVertx, configuration.initPeer, configuration.raftPort, configuration.me)
        raftServerVerticleFactory = { RaftServerVerticle(configuration, raft) }
    }
    fun start() = configuration.httpVertx.let { serverVertx ->
        raft.startRaft()
            .flatMap {
                serverVertx.deployVerticle(
                    raftServerVerticleFactory,
                    DeploymentOptions().setInstances(serverVertx.countEventLoop())
                )
            }
            .onFailure {
                raft.raftLog("start error! error message:${it.message}")
            }
            .map { }
    }
}