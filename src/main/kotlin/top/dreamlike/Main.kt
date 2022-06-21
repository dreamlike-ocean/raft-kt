package top.dreamlike

import top.dreamlike.raft.ServerId
import java.nio.channels.FileChannel
import java.nio.file.StandardOpenOption
import kotlin.io.path.Path

fun main() {

}

class Test(me: String) {
    val metaInfo = FileChannel.open(
        Path("raft-$me-meta"),
        StandardOpenOption.CREATE,
        StandardOpenOption.WRITE,
        StandardOpenOption.READ
    ).map(FileChannel.MapMode.READ_WRITE, 0, 4 + 4 + 1 + Byte.MAX_VALUE.toLong())
    var votedFor: ServerId? = null
        set(value) {
            field = value
            if (value == null) metaInfo.put(8, 0)
            else {
                val array = value.toByteArray()
                metaInfo.put(8, array.size.toByte())
                metaInfo.put(9, array)
            }
        }
        get() {
            val length = metaInfo.get(8)
            return if (length == 0.toByte())
                null
            else {
                val array = ByteArray(length.toInt())
                metaInfo.get(9, array)
                String(array)
            }
        }
}