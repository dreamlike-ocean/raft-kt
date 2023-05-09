import org.junit.Assert
import org.junit.Test
import top.dreamlike.base.KV.ByteArrayKey
import top.dreamlike.base.KV.ServerConfigChangeCommand
import top.dreamlike.base.KV.SimpleKVStateMachineCodec
import top.dreamlike.base.raft.RaftAddress
import top.dreamlike.base.raft.RaftServerInfo
import top.dreamlike.base.util.initJacksonMapper
import java.util.UUID
import kotlin.random.Random

class SimpleKVStateMachineCodecTest {
    @Test
    fun testCodec() {
        val snap = (0..10)
            .map {
                ByteArrayKey(
                    UUID.randomUUID().toString().repeat(Random.nextInt(10)).toByteArray()
                ) to UUID.randomUUID().toString().repeat(Random.nextInt(10)).toByteArray()
            }
            .toMap()
        val buffer = SimpleKVStateMachineCodec.encode(snap)
        val decodeRes = SimpleKVStateMachineCodec.decode(buffer) as MutableMap
        for (entry in snap) {
            val valueRes = decodeRes.remove(entry.key)
            Assert.assertFalse(valueRes == null)
            Assert.assertArrayEquals(entry.value, valueRes)
        }

    }

    @Test
    fun testSeverCommand() {
        initJacksonMapper()
        val serverInfo = RaftServerInfo(RaftAddress(80, "123"))
        val command = ServerConfigChangeCommand.create(serverInfo)
        val rawCommand = command.toByteArray()
        val serverConfigChangeCommand = ServerConfigChangeCommand(rawCommand).serverInfo
        Assert.assertEquals(serverInfo.raftAddress, serverConfigChangeCommand.raftAddress)
        Assert.assertEquals(serverInfo.serverId, serverConfigChangeCommand.serverId)
    }
}