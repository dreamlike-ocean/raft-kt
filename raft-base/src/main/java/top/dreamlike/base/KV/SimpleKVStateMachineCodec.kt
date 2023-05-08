package top.dreamlike.base.KV

import io.vertx.core.buffer.Buffer

// n*[总长度][当前key长度][key][value]
class SimpleKVStateMachineCodec {
    companion object {
        fun encode(stateMachineSnap: Map<ByteArrayKey, ByteArray>): Buffer {
            var buffer = Buffer.buffer()
            stateMachineSnap.forEach { key, value ->
                buffer =
                    buffer
                        .appendInt(key.byteArray.size + value.size)
                        .appendInt(key.size())
                        .appendBytes(key.byteArray)
                        .appendBytes(value)
            }
            return buffer
        }

        fun decode(buffer: Buffer): Map<ByteArrayKey, ByteArray> {
            var hasRead = 0;
            val res = mutableMapOf<ByteArrayKey, ByteArray>()
            while (hasRead < buffer.length()) {
                val totalLength = buffer.getInt(hasRead)
                val keyLength = buffer.getInt(hasRead + 4)
                val valueLength = totalLength - keyLength
                hasRead += 8
                val key = buffer.getBytes(hasRead, hasRead + keyLength)
                hasRead += keyLength
                val value = buffer.getBytes(hasRead, hasRead + valueLength)
                res[ByteArrayKey(key)] = value
                hasRead += valueLength
            }
            return res
        }
    }
}