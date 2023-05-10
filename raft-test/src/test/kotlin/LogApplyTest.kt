import io.vertx.core.net.SocketAddress
import org.junit.Assert
import org.junit.Test
import top.dreamlike.base.KV.ByteArrayKey
import top.dreamlike.base.KV.NoopCommand
import top.dreamlike.base.raft.RaftStatus
import top.dreamlike.base.util.SingleThreadVertx
import top.dreamlike.raft.Raft
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class LogApplyTest {
    @Test
    fun TestExtension() {
        val key = UUID.randomUUID().toString()
        val value = UUID.randomUUID().toString()
        val setCommand = SetCommandCreate(key, value)
        Assert.assertEquals(key, String(setCommand.key))
        Assert.assertEquals(value, String(setCommand.value))

        val delKey = UUID.randomUUID().toString()
        val delCommand = DelCommandCreate(delKey)
        Assert.assertEquals(delKey, String(delCommand.key))
    }

    @Test
    fun testAppply() {
        val rf0 = Raft(
            SingleThreadVertx(), mapOf(
                "raft-1" to SocketAddress.inetSocketAddress(81, "localhost"),
                "raft-2" to SocketAddress.inetSocketAddress(82, "localhost"),
            ), 80, "raft-0"
        )

        val rf1 = Raft(
            SingleThreadVertx(), mapOf(
                "raft-0" to SocketAddress.inetSocketAddress(80, "localhost"),
                "raft-2" to SocketAddress.inetSocketAddress(82, "localhost"),
            ), 81, "raft-1"
        )

        val rf2 = Raft(
            SingleThreadVertx(), mapOf(
                "raft-1" to SocketAddress.inetSocketAddress(81, "localhost"),
                "raft-0" to SocketAddress.inetSocketAddress(80, "localhost"),
            ), 82, "raft-2"
        )

        val rafts = listOf(rf0, rf1, rf2)
        val downLatch = CountDownLatch(3)
        rafts.forEach { rf ->
            rf.startRaft().onSuccess { println("${rf.me} start"); downLatch.countDown() }
        }
        downLatch.await()
        //——————————————第一次选举————————————————
        TimeUnit.SECONDS.sleep(1)

        var leader = mutableListOf<Raft>()
        rafts.forEach {
            if (it.status == RaftStatus.lead) {
                leader.add(it)
            } else {
                Assert.assertEquals(it.getNowLogIndex(), 1)
            }
        }
        //第一次选举结果
        Assert.assertEquals(1, leader.size)
        val oldLeader = leader[0]
        println("add log")


        oldLeader.addLog(NoopCommand())
        TimeUnit.SECONDS.sleep(1)
        //第一次选举后同步第一个log
        rafts.forEach {
            //选举空日志和之前add的
            Assert.assertEquals(2, it.getNowLogIndex())
        }

        //leader下线
        oldLeader.close()
        TimeUnit.SECONDS.sleep(1)
        //等待第二次选举
        leader = mutableListOf<Raft>()
        rafts.filter { it != oldLeader }.forEach {
            if (it.status == RaftStatus.lead) {
                leader.add(it)
            } else {
                //2个选举空日志和之前add的
                Assert.assertEquals(it.getNowLogIndex(), 3)
            }
        }
        Assert.assertEquals(1, leader.size)
        //第二次提交日志
        val key = UUID.randomUUID().toString()
        val value = UUID.randomUUID().toString()
        leader[0].addLog(SetCommandCreate(key, value))
        TimeUnit.SECONDS.sleep(1)
        rafts.filter { it != oldLeader }.forEach {
            if (it.status == RaftStatus.lead) {
                leader.add(it)
            } else {
                Assert.assertEquals(it.getNowLogIndex(), 4)
            }
            Assert.assertEquals(4, it.commitIndex)
            val keyB = it.stateMachine.db[ByteArrayKey(key.toByteArray())]
            Assert.assertNotNull(keyB)
            Assert.assertEquals(value, String(keyB!!))
        }

    }
    
}