package top.dreamlike.server

import io.vertx.core.AbstractVerticle
import io.vertx.core.Promise
import io.vertx.core.buffer.Buffer
import io.vertx.core.net.NetSocket
import top.dreamlike.KV.Command
import top.dreamlike.KV.DelCommand
import top.dreamlike.KV.NoopCommand
import top.dreamlike.KV.ReadCommand
import top.dreamlike.KV.SetCommand
import top.dreamlike.KV.UnknownCommand
import top.dreamlike.configurarion.Configuration
import top.dreamlike.raft.Raft

/**
 * 通讯格式经典四个字节长度+command内容
 */
class RaftServerVerticle(val configuration: Configuration,val raft: Raft) : AbstractVerticle() {
    override fun start(startPromise: Promise<Void>) {

    }

    private fun handleRequest(body: Buffer,connect : NetSocket) {
        var request = CommandRequest.decode(body)
        when (request.command) {
            is DelCommand -> TODO()
            is NoopCommand -> TODO()
            is SetCommand -> TODO()
            is ReadCommand -> TODO()
            is UnknownCommand -> TODO()
        }
    }

    open class CommandResponse(val sequence: Int, val result: ByteArray) {
        /**
         * 返回一个可以直接写入的buffer
         */
        fun encode() = Buffer.buffer()
            .appendInt(4 + result.size)
            .appendInt(sequence)
            .appendBytes(result)
    }

    data class CommandRequest(val sequence: Int,val command: Command) {
        companion object {
            fun decode(body: Buffer) : CommandRequest {
                val sequence = body.getInt(0)
                body.slice(4, body.length())
                val rawCommand = body.bytes
                return CommandRequest(sequence, Command.transToCommand(rawCommand))
            }
        }
    }


}