package top.dreamlike.clinet

import io.vertx.core.Future
import io.vertx.core.Vertx
import io.vertx.core.net.SocketAddress
import io.vertx.ext.web.client.HttpResponse
import io.vertx.ext.web.client.WebClient
import io.vertx.ext.web.codec.BodyCodec
import top.dreamlike.KV.DelCommand
import top.dreamlike.KV.ReadCommand
import top.dreamlike.KV.SetCommand
import top.dreamlike.raft.Raft
import top.dreamlike.server.RaftServer

class RaftClient(val vertx: Vertx, val address : SocketAddress) {
    val webClient = WebClient.create(vertx)

    fun peekRaft()  = webClient
        .get(address.port(), address.host(), RaftServer.PEEK_PATH)
        .`as`(BodyCodec.json(Raft.RaftSnap::class.java))
        .send()
        .map{ it.body() }

    fun del(key :ByteArray) = webClient
        .post(address.port(), address.host(), RaftServer.COMMAND_PATH)
        .`as`(BodyCodec.buffer())
        .sendBuffer(DelCommand.create(key).toBuffer())
        .map{it.body()}

    fun set(key :ByteArray, value :ByteArray) = webClient
        .post(address.port(), address.host(), RaftServer.COMMAND_PATH)
        .`as`(BodyCodec.buffer())
        .sendBuffer(SetCommand.create(key, value).toBuffer())
        .map{it.body()}

    fun get(key :ByteArray) = webClient
        .post(address.port(), address.host(), RaftServer.COMMAND_PATH)
        .`as`(BodyCodec.buffer())
        .sendBuffer(ReadCommand.create(key).toBuffer())
        .map {
            val hasError = it.statusCode() == 500
            if(hasError) {
                DataResult(hasError, it.body(), it.body().toString())
            } else {
                DataResult(hasError, it.body())
            }
        }

}