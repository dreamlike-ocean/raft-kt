package top.dreamlike.raft

import io.vertx.core.buffer.Buffer

// 其在buffer里面是这样的 index term length bytearray
data class Log(val index: Int, val term: Int, val command: ByteArray) {

    fun toBuffer() = Buffer.buffer().appendInt(index)
        .appendInt(term)
        .appendInt(command.size)
        .appendBytes(command)


}
