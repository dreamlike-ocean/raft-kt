package top.dreamlike.raft

import io.vertx.core.buffer.Buffer
import java.nio.ByteBuffer
import java.nio.channels.FileChannel

// 其在buffer里面是这样的 index term length bytearray
data class Log(val index: Int, val term: Int, val command: ByteArray) {
    val size = 12 + command.size

    fun toBuffer() = Buffer.buffer().appendInt(index)
        .appendInt(term)
        .appendInt(command.size)
        .appendBytes(command)

    fun writeToFile(fileChannel: FileChannel) {
        val allocate = ByteBuffer.allocate(4 + 4 + 4 + command.size)
        val buffer = allocate.putInt(index)
            .putInt(term)
            .putInt(command.size)
            .put(command)
        buffer.flip()
        fileChannel.write(allocate)
    }


}
