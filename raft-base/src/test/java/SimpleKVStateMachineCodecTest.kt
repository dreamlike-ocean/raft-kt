import org.junit.Assert
import org.junit.Test
import top.dreamlike.base.KV.ByteArrayKey
import top.dreamlike.base.KV.SimpleKVStateMachineCodec
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
}