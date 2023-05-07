import io.vertx.core.net.SocketAddress
import org.junit.Test
import top.dreamlike.base.util.SingleThreadVertx
import top.dreamlike.raft.Raft
import java.util.concurrent.CountDownLatch

class RecoverTest {
    @Test
    fun recover() {
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
        rafts.forEach { rf -> rf.start().onSuccess { println("${rf.me} start"); downLatch.countDown() } }
        downLatch.await()
    }
}