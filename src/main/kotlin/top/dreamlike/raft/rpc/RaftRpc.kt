package top.dreamlike.raft.rpc

import io.vertx.core.Future
import io.vertx.core.net.SocketAddress
import io.vertx.kotlin.coroutines.await
import top.dreamlike.raft.rpc.entity.AppendReply
import top.dreamlike.raft.rpc.entity.AppendRequest
import top.dreamlike.raft.rpc.entity.RequestVote
import top.dreamlike.raft.rpc.entity.RequestVoteReply

interface RaftRpc {
    suspend fun requestVoteSuspend(remote: SocketAddress, requestVote: RequestVote): RequestVoteReply

    fun requestVote(remote: SocketAddress, requestVote: RequestVote): Future<RequestVoteReply>

    suspend fun appendRequestSuspend(remote: SocketAddress, appendRequest: AppendRequest): AppendReply
    fun appendRequest(remote: SocketAddress, appendRequest: AppendRequest): Future<AppendReply>

    fun test(remote: SocketAddress): Future<Unit>

    suspend fun testSuspend(remote: SocketAddress) = test(remote).await()
}