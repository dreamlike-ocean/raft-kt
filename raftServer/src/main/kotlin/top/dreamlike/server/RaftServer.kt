package top.dreamlike.server

import io.vertx.core.DeploymentOptions
import io.vertx.core.Vertx
import top.dreamlike.configurarion.Configuration
import top.dreamlike.raft.Raft
import top.dreamlike.util.countEventLoop

class RaftServer(val configuration: Configuration) {
    companion object {
        const val SUCCESS = 200
        const val FAIL = 500
        const val COMMAND_PATH = "/command"
        const val PEEK_PATH = "/peek"
    }
    val raft : Raft
    val raftServerVerticleFactory : () -> RaftServerVerticle
    init {
        var raftOption = configuration.raftVertxOptions
        val raftVertx = Vertx.vertx(raftOption)
        raft = Raft(raftVertx, configuration.initPeer, configuration.raftPort, configuration.me)
        raftServerVerticleFactory = { RaftServerVerticle(configuration, raft) }
    }
    fun start()  = Vertx.vertx().let { serverVertx ->
        raft.start()
            .flatMap{serverVertx.deployVerticle(raftServerVerticleFactory, DeploymentOptions().setInstances(serverVertx.countEventLoop())) }
            .map { }
    }
}