import io.vertx.core.net.SocketAddress
import org.junit.Assert
import org.junit.Test
import top.dreamlike.base.util.SingleThreadVertx
import top.dreamlike.raft.Raft
import java.time.LocalDateTime
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class RaftElectonTest {


    @Test
    fun test() {

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
        println("waiting election ${LocalDateTime.now()}")
        TimeUnit.SECONDS.sleep(5)
        println("check election ${LocalDateTime.now()}")
        var leader = mutableListOf<Raft>()
        rafts.forEach {
            if (it.status == Raft.RaftStatus.lead) {
                leader.add(it)
            } else {
//                Assert.assertEquals(it.getNowLogIndex(), 1)
            }
        }
        Assert.assertEquals(1, leader.size)
        val olderLeader = leader[0]
        olderLeader.close()
        //二次选举
        println("kill ${olderLeader.me},waiting election ${LocalDateTime.now()}")
        TimeUnit.SECONDS.sleep(5)
        println("check election ${LocalDateTime.now()}")
        leader = mutableListOf<Raft>()
        rafts.filter { it != olderLeader }.forEach {
            if (it.status == Raft.RaftStatus.lead) {
                leader.add(it)
            } else {
//                Assert.assertEquals(it.getNowLogIndex(), 2)
            }
        }
        Assert.assertEquals(1, leader.size)

    }
}