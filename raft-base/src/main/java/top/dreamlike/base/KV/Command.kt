package top.dreamlike.base.KV

import io.vertx.core.json.Json
import io.vertx.core.json.JsonObject
import top.dreamlike.base.raft.RaftServerInfo
import top.dreamlike.base.util.Util
import top.dreamlike.base.util.wrap
import top.dreamlike.base.util.wrapSlice


sealed class Command {
    companion object {
        fun transToCommand(rawCommand: ByteArray): Command {
            return when (rawCommand[0]) {
                0.toByte() -> NoopCommand()
                1.toByte() -> SetCommand(rawCommand)
                2.toByte() -> DelCommand(rawCommand)
                3.toByte() -> ReadCommand(rawCommand)
                else -> UnknownCommand(rawCommand)
            }
        }
    }

    abstract fun toByteArray(): ByteArray

    fun toBuffer() = wrap(toByteArray())

}

class NoopCommand : Command() {
    override fun toByteArray() = byteArrayOf(0)
}

// 1 keySize:Int key value
class SetCommand(rawCommand: ByteArray) : Command() {
    val key: ByteArray
    val value: ByteArray

    companion object {
        fun create(key: ByteArray, value: ByteArray): SetCommand {
            val keySize = key.size
            val array = ByteArray(1 + 4 + keySize + value.size)
            array[0] = 1
            array[1] = ((keySize shl 24) and 0xFF).toByte()
            array[2] = ((keySize shl 16) and 0xFF).toByte()
            array[3] = ((keySize shl 8) and 0xFF).toByte()
            array[4] = ((keySize shl 0) and 0xFF).toByte()
            key.copyInto(array, 5)
            value.copyInto(array, 5 + keySize)
            return SetCommand(array)
        }
    }

    init {
        val length = Util.fromByteArray(rawCommand, 1)
        key = rawCommand.sliceArray(5 until 5 + length)
        value = rawCommand.sliceArray(5 + length until rawCommand.size)
        // 0 1 2 3 4 5 6
    }

    override fun toByteArray(): ByteArray {
        val keySize = key.size
        val array = ByteArray(1 + 4 + keySize + value.size)
        array[0] = 1
        array[1] = ((keySize shl 24) and 0xFF).toByte()
        array[2] = ((keySize shl 16) and 0xFF).toByte()
        array[3] = ((keySize shl 8) and 0xFF).toByte()
        array[4] = ((keySize shl 0) and 0xFF).toByte()
        key.copyInto(array, 5)
        value.copyInto(array, 5 + key.size)
        return array
    }
}

class DelCommand(rawCommand: ByteArray) : Command() {

    val key: ByteArray

    companion object {
        fun create(key: ByteArray): DelCommand {
            val command = ByteArray(key.size + 1)
            key.copyInto(command, 1, 0)
            return DelCommand(command)
        }
    }
    init {
        key = ByteArray(rawCommand.size - 1)
        rawCommand.copyInto(key, 0, 1)
    }

    override fun toByteArray(): ByteArray {
        val array = ByteArray(1 + key.size)
        array[0] = 2
        key.copyInto(array, 1)
        return array
    }
}

class ReadCommand(rawCommand: ByteArray) : Command() {
    val key: ByteArray

    init {
        key = ByteArray(rawCommand.size - 1)
        rawCommand.copyInto(key, 0, 1)
    }

    companion object {
        fun create(key: ByteArray): ReadCommand {
            val command = ByteArray(key.size + 1)
            command[0] = 3
            key.copyInto(command, 1, 0)
            return ReadCommand(command)
        }
    }
    override fun toByteArray() :ByteArray {
        val array = ByteArray(1 + key.size)
        array[0] = 3
        key.copyInto(array, 1)
        return array
    }

}

class ServerConfigChangeCommand(rawCommand: ByteArray) : Command() {
    val serverInfo: RaftServerInfo

    init {
        serverInfo = JsonObject(wrapSlice(rawCommand, 1)).mapTo(RaftServerInfo::class.java)
    }

    companion object {
        fun create(serverInfo: RaftServerInfo): ServerConfigChangeCommand {
            val buffer = Json.encodeToBuffer(serverInfo)
            val res = ByteArray(buffer.length() + 1)
            res[0] = 4
            buffer.getBytes(res, 1)
            return ServerConfigChangeCommand(res)
        }
    }

    override fun toByteArray(): ByteArray {
        val buffer = Json.encodeToBuffer(serverInfo)
        val res = ByteArray(buffer.length() + 1)
        res[0] = 4
        buffer.getBytes(res, 1)
        return res
    }

}

class UnknownCommand(val rawCommand : ByteArray) : Command() {
    override fun toByteArray() = rawCommand
}