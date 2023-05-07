import top.dreamlike.base.KV.DelCommand
import top.dreamlike.base.KV.SetCommand

class CommandExtension {
}

fun SetCommandCreate(key: String, value: String): SetCommand {
    val keyB = key.toByteArray()
    val valueB = value.toByteArray()
    val keySize = keyB.size
    val array = ByteArray(1 + 4 + keySize + valueB.size)
    array[0] = 1
    array[1] = ((keySize shl 24) and 0xFF).toByte()
    array[2] = ((keySize shl 16) and 0xFF).toByte()
    array[3] = ((keySize shl 8) and 0xFF).toByte()
    array[4] = ((keySize shl 0) and 0xFF).toByte()
    keyB.copyInto(array, 5)
    valueB.copyInto(array, 5 + keySize)
    return SetCommand(array)
}

fun DelCommandCreate(key: String): DelCommand {
    val keyB = key.toByteArray()
    val array = ByteArray(1 + keyB.size)
    array[0] = 2
    keyB.copyInto(array, 1)
    return DelCommand(array)
}