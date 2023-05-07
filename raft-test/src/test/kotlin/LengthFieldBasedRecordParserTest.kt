import io.vertx.core.buffer.Buffer
import org.junit.Assert
import org.junit.Test
import top.dreamlike.base.util.LengthFieldBasedRecordParser

class LengthFieldBasedRecordParserTest {

    @Test
    fun plainTest() {
        val list = mutableListOf<String>()
        val fn : (Buffer) -> Unit = { list.add(it.toString()) }
        val recordParser = LengthFieldBasedRecordParser(fn)
        val first = "first".toByteArray()
        val second = "second".toByteArray()
        val buffer = Buffer.buffer()
            .appendInt(first.size)
            .appendBytes(first)
            .appendInt(second.size)
            .appendBytes(second)
        recordParser.handle(buffer)
        Assert.assertEquals(2, list.size)
        Assert.assertArrayEquals(first, list[0].toByteArray())
        Assert.assertArrayEquals(second, list[1].toByteArray())
    }

    @Test
    fun complexTest() {
        val list = mutableListOf<String>()
        val fn : (Buffer) -> Unit = { list.add(it.toString()) }
        val recordParser = LengthFieldBasedRecordParser(fn)
        val first = "first".toByteArray()
        val second = "second".toByteArray()
        var buffer = Buffer.buffer()
            .appendInt(first.size)
            .appendBytes(first.sliceArray(0..first.size/2))

        recordParser.handle(buffer)
        buffer = Buffer.buffer()
            .appendBytes(first.sliceArray((first.size/2 + 1) until first.size))
            .appendInt(second.size)
        recordParser.handle(buffer)
        buffer = Buffer.buffer()
            .appendBytes(second)
        recordParser.handle(buffer)

        Assert.assertEquals(2, list.size)
        Assert.assertArrayEquals(first, list[0].toByteArray())
        Assert.assertArrayEquals(second, list[1].toByteArray())
    }
}