package top.dreamlike.server

import io.netty.util.internal.EmptyArrays
import io.vertx.core.AbstractVerticle
import io.vertx.core.Future
import io.vertx.core.Promise
import io.vertx.core.buffer.Buffer
import io.vertx.core.impl.ContextInternal
import io.vertx.ext.web.Router
import io.vertx.kotlin.coroutines.await
import top.dreamlike.KV.Command
import top.dreamlike.KV.DelCommand
import top.dreamlike.KV.ReadCommand
import top.dreamlike.KV.SetCommand
import top.dreamlike.configurarion.Configuration
import top.dreamlike.exception.UnknownCommandException
import top.dreamlike.raft.Raft
import top.dreamlike.util.suspendHandle
import top.dreamlike.util.wrap

/**
 * requestCommand 带command在body中 成功与否看http的code 200/500
 *    1,DelCommand, SetCommand 成功时body为空
 *    2,read 成功时body为空则为null 反之为value值
 * snap 返回json类型的raft数据
 */
class RaftServerVerticle(val configuration: Configuration,val raft: Raft) : AbstractVerticle() {
    private lateinit var internalContext : ContextInternal

    override fun start(startPromise: Promise<Void>) {
        internalContext = context as ContextInternal
        val router = Router.router(vertx)
        router.post(RaftServer.COMMAND_PATH)
            .suspendHandle {
                val body = it.request().body().await()
                try {
                    val buffer = handleCommandRequest(body).await()
                    it.response().statusCode = RaftServer.SUCCESS
                    it.end(buffer)
                } catch (t : Throwable) {
                    it.response().statusCode = RaftServer.FAIL
                    it.end(t.message)
                }
            }
        router.get(RaftServer.PEEK_PATH)
            .suspendHandle {
                val snap = peekRaft().await()
                it.json(snap)
            }

        vertx.createHttpServer()
            .requestHandler(router)
            .listen(configuration.httpPort)
            .onComplete{
                if (it.succeeded()) {
                    startPromise.complete()
                }else{
                    startPromise.fail(it.cause())
                }
            }
    }

    private fun peekRaft() :Future<Raft.RaftSnap>{
        val promise = internalContext.promise<Raft.RaftSnap>()
        raft.raftSnap(promise::complete)
        return promise.future()
    }

    private fun handleCommandRequest(body: Buffer) :Future<Buffer> {

        val request = CommandRequest.decode(body)
        val res = when (val command = request.command) {
            is DelCommand, is SetCommand -> {
                val promise = internalContext.promise<Unit>()
                raft.addLog(command, promise)
                promise.future()
                    .map { CommandResponse(EmptyArrays.EMPTY_BYTES).encode() }
            }
            is ReadCommand -> {
                val promise = internalContext.promise<ByteArray?>()
                raft.lineRead(command.key, promise)
                promise.future()
                    .map {
                        CommandResponse(it).encode()
                    }
            }
            else -> {
                val promise = internalContext.promise<Buffer>()
                promise.fail(UnknownCommandException(command::class.simpleName))
                promise.future()
            }
        }

        return res
    }

    class CommandResponse(val result: ByteArray?) {
        /**
         * 返回一个可以直接写入的buffer
         */
        fun encode() = wrap(result ?: EmptyArrays.EMPTY_BYTES)
    }

    class CommandRequest(val command: Command) {
        companion object {
            fun decode(body: Buffer) : CommandRequest {
                val rawCommand = body.bytes
                return CommandRequest(Command.transToCommand(rawCommand))
            }
        }
    }


}