package top.dreamlike.raft.rpc

import io.vertx.core.CompositeFuture
import io.vertx.core.Future
import io.vertx.core.Promise
import io.vertx.core.Vertx
import io.vertx.core.json.Json
import io.vertx.core.json.JsonObject
import io.vertx.core.net.SocketAddress
import io.vertx.ext.web.Router
import io.vertx.ext.web.client.WebClient
import io.vertx.ext.web.client.WebClientOptions
import io.vertx.ext.web.codec.BodyCodec
import io.vertx.ext.web.handler.BodyHandler
import io.vertx.kotlin.coroutines.await
import top.dreamlike.base.ADD_SERVER_PATH
import top.dreamlike.base.ServerId
import top.dreamlike.base.raft.RaftAddress
import top.dreamlike.base.raft.RaftServerInfo
import top.dreamlike.base.raft.RaftStatus
import top.dreamlike.base.util.suspendHandle
import top.dreamlike.raft.Raft
import top.dreamlike.raft.rpc.entity.AddServerRequest
import top.dreamlike.raft.rpc.entity.AdderServerResponse
import top.dreamlike.raft.rpc.entity.AppendReply
import top.dreamlike.raft.rpc.entity.AppendRequest
import top.dreamlike.raft.rpc.entity.RequestVote
import top.dreamlike.raft.rpc.entity.RequestVoteReply
import top.dreamlike.server.NotLeaderException

class RaftRpcImpl(private val vertx: Vertx, private val rf: Raft) : RaftRpc, RaftRpcHandler {

    private val raftRpcClient = WebClient.create(
        vertx,
        WebClientOptions()
            .setMaxPoolSize(1)
            .setIdleTimeout(0)
    )
    private var port = -1

    companion object {
        const val appendRequest_path = "/appendRequest"
        const val requestVoteReply_path = "/requestVote"
        const val test_path = "/test"
        const val server_id_header = "raft_server_id"
    }

    override fun requestVote(
        remote: SocketAddress,
        requestVote: RequestVote
    ): Future<RequestVoteReply> {
        return try {
            raftRpcClient.post(remote.port(), remote.host(), requestVoteReply_path)
                .putHeader(server_id_header, rf.me)
                .`as`(BodyCodec.buffer())
                .sendBuffer(requestVote.toBuffer())
                .map {
                    RequestVoteReply(it.body())
                }
        } catch (e: Exception) {
            Future.failedFuture(e)
        }
    }

    override fun test(remote: SocketAddress): Future<Unit> {
        return try {
            raftRpcClient.post(remote.port(), remote.host(), test_path)
                .putHeader(server_id_header, rf.me)
                .`as`(BodyCodec.buffer())
                .send()
                .flatMap {
                    if (it.statusCode() != 200) {
                        Future.failedFuture("PING fail")
                    } else {
                        Future.succeededFuture()
                    }
                }
        } catch (e: Exception) {
            Future.failedFuture(e)
        }
    }

    override fun appendRequest(
        remote: SocketAddress,
        appendRequest: AppendRequest
    ): Future<AppendReply> {
        return try {
            raftRpcClient.post(remote.port(), remote.host(), appendRequest_path)
                .putHeader(server_id_header, rf.me)
                .`as`(BodyCodec.buffer())
                .sendBuffer(appendRequest.toBuffer())
                .map {
                    AppendReply(it.body())
                }
        } catch (e: Exception) {
            Future.failedFuture(e)
        }
    }

    override fun addServer(
        remote: SocketAddress,
        request: AddServerRequest
    ): Future<AdderServerResponse> {
        return try {
            raftRpcClient.post(remote.port(), remote.host(), ADD_SERVER_PATH)
                .putHeader(server_id_header, rf.me)
                .`as`(BodyCodec.json(AdderServerResponse::class.java))
                .sendBuffer(Json.encodeToBuffer(request))
                .map {
                    it.body()
                }
        } catch (e: Exception) {
            Future.failedFuture(e)
        }
    }

    override suspend fun requestVoteSuspend(
        remote: SocketAddress,
        requestVote: RequestVote
    ): RequestVoteReply {
        return requestVote(remote, requestVote)
            .await()
    }


    override fun init(vertx: Vertx, raftPort: Int): Future<Unit> {
        this.port = raftPort
        val router = Router.router(vertx)
        router.post(appendRequest_path)
            .handler(BodyHandler.create(false))
            .handler {
                val body = it.body().buffer()
                val appendRequest =
                    AppendRequest(body, it.request().getHeader(server_id_header))
                it.end(appendRequest(appendRequest).toBuffer())
            }

        router.post(requestVoteReply_path)
            .handler(BodyHandler.create(false))
            .handler {
                it.end(
                    requestVote(
                        RequestVote(
                            it.request().getHeader(server_id_header),
                            it.body().buffer()
                        )
                    ).toBuffer()
                )
            }

        router.post(test_path)
            .handler {
                it.response().statusCode = 200
                it.end()
            }

        router.post(ADD_SERVER_PATH)
            .suspendHandle {
                val body = JsonObject(it.request().body().await())
                    .mapTo(RaftServerInfo::class.java)
                rf.raftLog("recv add Server request: $body")
                body.raftAddress =
                    RaftAddress(body.raftAddress.port, it.request().remoteAddress().host(), body.raftAddress.httpPort)
                val apply = Promise.promise<Map<ServerId, RaftAddress>>()
                rf.addServer(body, apply)
                val res = try {
                    val peerInfo = apply.future().await()
                    AdderServerResponse(true, RaftAddress(rf.raftPort, "localhost", rf.httpPort), rf.me, peerInfo)
                } catch (t: NotLeaderException) {
                    val leaderInfo = t.leaderInfo
                    AdderServerResponse(false, leaderInfo, rf.leadId!!, mapOf())
                } catch (t: Exception) {
                    t.printStackTrace()
                }
                it.json(res)
            }


        return vertx.createHttpServer()
            .requestHandler(router)
            .listen(raftPort)
            .flatMap { httpServer ->
                rf.raftLog("raft core is listening on ${httpServer.actualPort()}")
                CompositeFuture.all(rf.peers.map { test(it.value.SocketAddress()) })
            }
            .map(Unit)
    }

    private fun appendRequest(msg: AppendRequest): AppendReply {
        rf.lastHearBeat = System.currentTimeMillis()
        if (msg.term < rf.currentTerm) {
            return AppendReply(rf.currentTerm, false)
        }
        if (rf.status != RaftStatus.follower) {
            rf.becomeFollower(msg.term)
        }
        //产生了新leader
        if (rf.leadId != msg.leaderId) {
            rf.votedFor = null
            rf.leadId = msg.leaderId
        }
        rf.commitIndex = msg.leaderCommit
        rf.currentTerm = msg.term
        val nowIndex = rf.getNowLogIndex()

        //leader比他长
        if (nowIndex < msg.prevLogIndex) {
            return AppendReply(rf.currentTerm, false)
        }

        val term = rf.getTermByIndex(msg.prevLogIndex)
        rf.raftLog("leader{${msg.leaderId}}:${msg.prevLogIndex} ${msg.prevLogTerm} follower$nowIndex $term log_count:${msg.entries.size}")
        if (term == msg.prevLogTerm) {
            rf.insertLogs(msg.prevLogIndex, msg.entries)
            rf.stateMachine.applyLog(rf.commitIndex)
            return AppendReply(rf.currentTerm, true)
        } else {
            return AppendReply(rf.currentTerm, false)
        }
    }

    private fun requestVote(msg: RequestVote): RequestVoteReply {

        rf.lastHearBeat = System.currentTimeMillis()

        rf.raftLog("receive request vote, msg :${msg}")
        if (msg.term < rf.currentTerm) {
            return RequestVoteReply(rf.currentTerm, false)
        }
        val lastLogTerm = rf.getLastLogTerm()
        val lastLogIndex = rf.getNowLogIndex()
        rf.currentTerm = msg.term
        //若voteFor为空或者已经投给他了
        //如果 votedFor 为空或者为 candidateId，并且候选人的日志至少和自己一样新，那么就投票给他（5.2 节，5.4 节）
        if ((rf.votedFor == null || rf.votedFor == msg.candidateId) && msg.lastLogTerm >= lastLogTerm) {
            if (msg.lastLogTerm == lastLogTerm && msg.lastLogIndex < lastLogIndex) {
                return RequestVoteReply(rf.currentTerm, false)
            }
            rf.votedFor = msg.candidateId
            rf.raftLog("vote to ${msg.candidateId}")
            return RequestVoteReply(rf.currentTerm, true)
        }
        return RequestVoteReply(rf.currentTerm, false)
    }


}