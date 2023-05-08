package top.dreamlike.server

import io.vertx.core.AbstractVerticle
import io.vertx.core.Future
import io.vertx.core.Promise
import io.vertx.core.buffer.Buffer
import io.vertx.core.impl.ContextInternal
import io.vertx.ext.web.Router
import io.vertx.kotlin.coroutines.await
import top.dreamlike.base.COMMAND_PATH
import top.dreamlike.base.FAIL
import top.dreamlike.base.KV.Command
import top.dreamlike.base.KV.DelCommand
import top.dreamlike.base.KV.ReadCommand
import top.dreamlike.base.KV.SetCommand
import top.dreamlike.base.PEEK_PATH
import top.dreamlike.base.SUCCESS
import top.dreamlike.base.raft.RaftSnap
import top.dreamlike.base.util.EMPTY_BUFFER
import top.dreamlike.base.util.suspendHandle
import top.dreamlike.configurarion.Configuration
import top.dreamlike.exception.UnknownCommandException
import top.dreamlike.raft.Raft

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
        router.post(COMMAND_PATH)
            .suspendHandle {
                val body = it.request().body().await()
                try {
                    val response = handleCommandRequest(body).await()
                    it.response().statusCode = SUCCESS
                    it.end(response.result)
                } catch (t : Throwable) {
                    it.response().statusCode = FAIL
                    it.end(t.message)
                }
            }
        router.get(PEEK_PATH)
            .suspendHandle {
                val snap = peekRaft().await()
                it.json(snap)
            }

        vertx.createHttpServer()
            .requestHandler(router)
            .listen(configuration.httpPort)
            .onComplete {
                if (it.succeeded()) {
                    startPromise.complete()
                } else {
                    startPromise.fail(it.cause())
                }
            }
    }

    private fun peekRaft(): Future<RaftSnap> {
        val promise = internalContext.promise<RaftSnap>()
        raft.raftSnap(promise::complete)
        return promise.future()
    }

    private fun handleCommandRequest(body: Buffer): Future<CommandResponse> {

        val request = CommandRequest.decode(body)
        val res = when (val command = request.command) {
            is DelCommand, is SetCommand -> {
                val promise = internalContext.promise<Unit>()
                raft.addLog(command, promise)
                promise.future()
                    .map { CommandResponse() }
            }

            is ReadCommand -> {
                val promise = internalContext.promise<Buffer>()
                raft.lineRead(command.key, promise)
                promise.future()
                    .map {
                        CommandResponse(it)
                    }
            }

            else -> {
                val promise = internalContext.promise<CommandResponse>()
                promise.fail(UnknownCommandException(command::class.simpleName))
                promise.future()
            }
        }

        return res
    }

    @JvmInline
    value class CommandResponse(val result: Buffer = EMPTY_BUFFER) {
    }

    class CommandRequest(val command: Command) {
        companion object {
            fun decode(body: Buffer): CommandRequest {
                val rawCommand = body.bytes
                return CommandRequest(Command.transToCommand(rawCommand))
            }
        }
    }


}