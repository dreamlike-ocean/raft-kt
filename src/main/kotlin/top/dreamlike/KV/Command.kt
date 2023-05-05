package top.dreamlike.KV

import io.netty.buffer.ByteBuf
import io.netty.buffer.ByteBufUtil
import io.netty.buffer.Unpooled
import io.vertx.core.buffer.Buffer
import top.dreamlike.util.Util
import top.dreamlike.util.wrap

sealed class Command {
    companion object {
        fun transToCommand(command: ByteArray): Command {
            return when (command[0]) {
                0.toByte() -> NoopCommand()
                1.toByte() -> SetCommand(command)
                2.toByte() -> DelCommand(command)
                3.toByte() -> ReadCommand(command)
                else -> UnknownCommand(command)
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
class SetCommand(command: ByteArray) : Command() {
    val key: ByteArray
    val value: ByteArray

    companion object {
        fun create(key: ByteArray, value : ByteArray) : SetCommand {
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
        val length = Util.fromByteArray(command, 1)
        key = command.sliceArray(5 until 5 + length)
        value = command.sliceArray(5 + length until command.size)
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

class DelCommand(command: ByteArray) : Command() {

    val key: ByteArray

    companion object {
        fun create(key: ByteArray) : DelCommand {
            val command = ByteArray(key.size + 1)
            key.copyInto(command, 1, 0)
            return DelCommand(command)
        }
    }
    init {
        key = ByteArray(command.size - 1)
        command.copyInto(key, 0, 1)
    }

    override fun toByteArray(): ByteArray {
        val array = ByteArray(1 + key.size)
        array[0] = 2
        key.copyInto(array, 1)
        return array
    }
}

class ReadCommand(command : ByteArray) :Command() {
    val key : ByteArray
    init {
        key = ByteArray(command.size - 1)
        command.copyInto(key, 0, 1)
    }

    companion object {
        fun create(key: ByteArray) : ReadCommand {
            val command = ByteArray(key.size + 1)
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


class UnknownCommand(val rawCommand : ByteArray) : Command() {
    override fun toByteArray() = rawCommand
}