package top.dreamlike.raft.client

import io.vertx.core.Vertx
import io.vertx.core.buffer.Buffer
import io.vertx.core.net.SocketAddress
import io.vertx.ext.web.client.WebClient
import io.vertx.ext.web.codec.BodyCodec
import top.dreamlike.base.COMMAND_PATH
import top.dreamlike.base.KV.DelCommand
import top.dreamlike.base.KV.ReadCommand
import top.dreamlike.base.KV.SetCommand
import top.dreamlike.base.PEEK_PATH
import top.dreamlike.base.raft.RaftSnap


class RaftClient(val vertx: Vertx, val address: SocketAddress) {
    val webClient = WebClient.create(vertx)

    fun peekRaft() = webClient
        .get(address.port(), address.host(), PEEK_PATH)
        .`as`(BodyCodec.json(RaftSnap::class.java))
        .send()
        .map { it.body() }

    fun del(key :ByteArray) = webClient
        .post(address.port(), address.host(), COMMAND_PATH)
        .`as`(BodyCodec.buffer())
        .sendBuffer(DelCommand.create(key).toBuffer())
        .map{it.body()}

    fun set(key :ByteArray, value :ByteArray) = webClient
        .post(address.port(), address.host(), COMMAND_PATH)
        .`as`(BodyCodec.buffer())
        .sendBuffer(SetCommand.create(key, value).toBuffer())
        .map{it.body()}

    fun get(key :ByteArray) = webClient
        .post(address.port(), address.host(), COMMAND_PATH)
        .`as`(BodyCodec.buffer())
        .sendBuffer(ReadCommand.create(key).toBuffer())
        .map {
            val hasError = it.statusCode() == 500
            var body = it.body() ?: Buffer.buffer()
            if(hasError) {
                DataResult(hasError, body, body.toString())
            } else {
                DataResult(hasError, body)
            }
        }

}