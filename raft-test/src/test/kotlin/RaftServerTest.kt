import io.vertx.core.CompositeFuture
import io.vertx.core.Vertx
import io.vertx.core.net.SocketAddress
import org.junit.Assert
import org.junit.Test
import top.dreamlike.base.KV.ByteArrayKey
import top.dreamlike.base.raft.RaftAddress
import top.dreamlike.base.raft.RaftStatus
import top.dreamlike.base.util.block
import top.dreamlike.base.util.initJacksonMapper
import top.dreamlike.configurarion.Configuration
import top.dreamlike.raft.client.RaftClient
import top.dreamlike.server.RaftServer

class RaftServerTest {

    @Test
    fun testServer()  {
        initJacksonMapper()
        val httpPort = 8080
        val configurations = mutableListOf<Configuration>()
        val vertx = Vertx.vertx()
        val nodes = mapOf(
            "raft-0" to RaftAddress(80, "localhost"),
            "raft-1" to RaftAddress(81, "localhost"),
            "raft-2" to RaftAddress(82, "localhost"),
        )
        for (i in (0 .. 2)) {
            val nodeId = "raft-$i"
            configurations.add(
                Configuration(
                    nodeId,
                    nodes[nodeId]!!.port,
                    httpPort + i,
                    mapOf(),
                    HashMap(nodes).apply { remove(nodeId) })
            )
        }
        val servers = configurations
            .map { RaftServer(it)}

        CompositeFuture.all(servers.map(RaftServer::start)).block()
        Thread.sleep(2000)
        val leaders = servers.filter { it.raft.status == RaftStatus.lead }
        Assert.assertEquals(1, leaders.size)
        val leaderServer = leaders[0]
        val client = RaftClient(
            vertx,
            SocketAddress.inetSocketAddress(leaderServer.configuration.httpPort, "localhost")
        )
        val snap = client.peekRaft().block()
        val raft = leaderServer.raft
        Assert.assertEquals(raft.commitIndex, snap.raftState.commitIndex)

        //set测试
        val key = "key1".toByteArray()
        val value = "value1".toByteArray()
        val buffer = client.set(key, value).block()
        val db = raft.stateMachine.db
        Assert.assertArrayEquals(value, db[ByteArrayKey(key)])
        var dataResult = client.get(key).block()

        Assert.assertFalse(dataResult.hasError)
        Assert.assertArrayEquals(value, dataResult.value.bytes)

//        del测试
        val delRes = client.del(key).block()
        Assert.assertNull(db[ByteArrayKey(key)])
        dataResult = client.get(key).block()
        Assert.assertEquals(0, dataResult.value.length())
    }

    @Test
    fun testAddServer() {
        initJacksonMapper()
        val httpPort = 8080
        val configurations = mutableListOf<Configuration>()
        val vertx = Vertx.vertx()
        val nodes = mapOf(
            "raft-0" to RaftAddress(80, "localhost"),
            "raft-1" to RaftAddress(81, "localhost"),
            "raft-2" to RaftAddress(82, "localhost"),
        )
        for (i in (0..2)) {
            val nodeId = "raft-$i"
            configurations.add(
                Configuration(
                    nodeId,
                    nodes[nodeId]!!.port,
                    httpPort + i,
                    mapOf(),
                    HashMap(nodes).apply { remove(nodeId) })
            )
        }
        val servers = configurations
            .map { RaftServer(it) }

        CompositeFuture.all(servers.map(RaftServer::start)).block()
    }

}