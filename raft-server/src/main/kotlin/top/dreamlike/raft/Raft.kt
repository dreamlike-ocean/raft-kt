package top.dreamlike.raft

import io.vertx.core.AbstractVerticle
import io.vertx.core.Future
import io.vertx.core.Promise
import io.vertx.core.Vertx
import io.vertx.core.buffer.Buffer
import io.vertx.kotlin.coroutines.await
import io.vertx.kotlin.coroutines.dispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import top.dreamlike.KV.KVStateMachine
import top.dreamlike.base.KV.Command
import top.dreamlike.base.KV.NoopCommand
import top.dreamlike.base.KV.ServerConfigChangeCommand
import top.dreamlike.base.KV.SimpleKVStateMachineCodec
import top.dreamlike.base.ServerId
import top.dreamlike.base.raft.RaftAddress
import top.dreamlike.base.raft.RaftSnap
import top.dreamlike.base.raft.RaftState
import top.dreamlike.base.raft.RaftStatus
import top.dreamlike.base.util.CountDownLatch
import top.dreamlike.base.util.IntAdder
import top.dreamlike.base.util.NonBlocking
import top.dreamlike.base.util.SwitchThread
import top.dreamlike.base.util.wrap
import top.dreamlike.raft.rpc.RaftRpc
import top.dreamlike.raft.rpc.RaftRpcHandler
import top.dreamlike.raft.rpc.RaftRpcImpl
import top.dreamlike.raft.rpc.entity.AddServerRequest
import top.dreamlike.raft.rpc.entity.AppendReply
import top.dreamlike.raft.rpc.entity.AppendRequest
import top.dreamlike.raft.rpc.entity.RequestVote
import top.dreamlike.raft.rpc.entity.RequestVoteReply
import top.dreamlike.server.NotLeaderException
import java.nio.channels.FileChannel
import java.nio.file.StandardOpenOption
import java.time.LocalDateTime
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.CoroutineContext
import kotlin.io.path.Path
import kotlin.random.Random
import kotlin.system.exitProcess


/**
 * Raft的核心类设计 包含论文要求的全部数据+工业实践下来的额外字段，比如说commitIndex之类的
 * 要求与RPC运行在单线程之中 以保护多个属性修改时的原子性
 */
class Raft(
    private val singleThreadVertx: Vertx,
    peer: Map<ServerId, RaftAddress>,
    val raftPort: Int,
    val me: ServerId,
    val addModeConfig: RaftAddress? = null,
    val httpPort :Int = -1
) : AbstractVerticle() {

    companion object {
        const val ElectronInterval = 300
        const val heartBeat = 100L
    }

    var enablePrintInfo = true

    private val metaInfoPath = Path("raft-$me-meta")
    val metaInfo = FileChannel.open(
        metaInfoPath,
        StandardOpenOption.CREATE,
        StandardOpenOption.WRITE,
        StandardOpenOption.READ
    ).map(FileChannel.MapMode.READ_WRITE, 0, 4 + 4 + 1 + Byte.MAX_VALUE.toLong())

    //三个持久化状态 委托给状态机持久化
    var currentTerm: Int = 0
        set(value) {
            field = value
            metaInfo.putInt(0, value)
        }
        get() = metaInfo.getInt(0)

    var votedFor: ServerId? = null
        set(value) {
            field = value
            if (value == null) metaInfo.put(8, 0)
            else {
                val array = value.toByteArray()
                metaInfo.put(8, array.size.toByte())
                metaInfo.put(9, array)
            }
        }
        get() {
            val length = metaInfo.get(8)
            return if (length == 0.toByte())
                null
            else {
                val array = ByteArray(length.toInt())
                metaInfo.get(9, array)
                String(array)
            }
        }

    //方便快速恢复 也持久化
    var commitIndex: Int = 0
        set(value) {
            field = value
            metaInfo.putInt(4, value)
        }
        get() = metaInfo.getInt(4)


    var lastApplied: Int = 0
    var lastHearBeat = 0L
    var status: RaftStatus = RaftStatus.follower
    var leadId: ServerId? = null

    //leader
    //要确保这三个视图一致
    //nextIndex 是用来确认发送给 follower 的日志的下标，而 matchIndex 是用来给 Leader 计算出 commitIndex 的
    var nextIndexes = mutableMapOf<ServerId, IntAdder>()
    var matchIndexes = mutableMapOf<ServerId, IntAdder>()

    val peers = mutableMapOf<ServerId, RaftAddress>().apply { putAll(peer) }

    private val rpc: RaftRpc
    private val rpcHandler: RaftRpcHandler
    val stateMachine = KVStateMachine(singleThreadVertx, this)

    init {
        if (me.toByteArray().size > Byte.MAX_VALUE) {
            throw IllegalArgumentException("raft-me长度超过Byte.MAX_VALUE")
        }
        val rpcImpl = RaftRpcImpl(singleThreadVertx, this)
        rpc = rpcImpl
        rpcHandler = rpcImpl
    }

    fun pingRaftNode(address: RaftAddress) = rpc.test(address.SocketAddress())


    /**
     * 注意这里设计有问题，直接用了对应的实现
     * 只用于特殊情况
     */

    /**
     * 回调跑在Raft实例绑定的EventLoop上面
     */
    @NonBlocking
    @SwitchThread(Raft::class)
    fun startRaft(): Future<Unit> {
        return singleThreadVertx.deployVerticle(this)
            .map {}
    }

    override fun start(startPromise: Promise<Void>) {
        //预先触发缺页中断
        raftLog("node recover")
        CoroutineScope(context.dispatcher()).launch {
            try {
                if (addModeConfig != null) {
                    raftLog("start in addServerMode")
//                    rpc.test(addModeConfig.SocketAddress()).await()
                    var targetAddress = addModeConfig.SocketAddress()
                    //fast path先试一下
                    var response = rpc.addServer(
                        targetAddress,
                        AddServerRequest(RaftAddress(raftPort, "localhost", httpPort), me)
                    ).await()
                    while (!response.ok) {
                        targetAddress = response.leader.SocketAddress()
                        response = rpc.addServer(
                            targetAddress,
                            AddServerRequest(RaftAddress(raftPort, "localhost", httpPort), me)
                        ).await()
                    }
                    raftLog("get now leader info $response")
                    leadId = response.leaderId
                    peers.putAll(response.peer)
                    peers[response.leaderId] = RaftAddress(targetAddress).apply { httpPort = response.leader.httpPort }
                }
            } catch (t: Throwable) {
                raftLog("addServerMode start error")
                startPromise.fail(t)
                exitProcess(1)
            }
            stateMachine.init()
                .compose { rpcHandler.init(singleThreadVertx, raftPort) }
                .onSuccess { startTimeoutCheck() }
                .onSuccess { startPromise.complete() }
                .onFailure(startPromise::fail)
        }
    }

    private fun startTimeoutCheck() {
        CoroutineScope(context.dispatcher() as CoroutineContext).launch {
            while (true) {
                val timeout = (ElectronInterval + Random.nextInt(150)).toLong()
                val start = System.currentTimeMillis()
                delay(timeout)
                if (lastHearBeat < start && status != RaftStatus.lead) {
                    startElection()
                }
            }
        }

    }


    internal fun becomeFollower(term: Int) {
        status = RaftStatus.follower
        currentTerm = term
        votedFor = null
        lastHearBeat = System.currentTimeMillis()
    }

    fun becomeCandidate() {
        currentTerm++
        votedFor = me
        lastHearBeat = System.currentTimeMillis()
        status = RaftStatus.candidate
    }

    fun becomeLead() {
        status = RaftStatus.lead
        lastHearBeat = System.currentTimeMillis()
        nextIndexes = mutableMapOf()
        matchIndexes = mutableMapOf()
        peers.forEach {
            nextIndexes[it.key] = IntAdder(stateMachine.getNowLogIndex() + 1)
            matchIndexes[it.key] = IntAdder(0)
        }
        leadId = me;

        //添加一个空日志 论文要求的
        addLog(NoopCommand())
        //不断心跳
        CoroutineScope(context.dispatcher() as CoroutineContext).launch {
            while (status == RaftStatus.lead) {
                broadcastLog()
                delay(heartBeat)
            }
        }

    }

    private fun broadcastLog(): MutableList<Future<AppendReply>> {
        val list = mutableListOf<Future<AppendReply>>()
        if (peers.isEmpty()) {
            val oldCommitindex = commitIndex
            calCommitIndex()
            if (oldCommitindex != commitIndex) {
                stateMachine.applyLog(commitIndex)
            }
            return list
        }
        for (peer in peers) {
            val peerServerId = peer.key
            val nextIndex = nextIndexes[peerServerId] ?: continue
            list.add(appendLogsToPeer(nextIndex, peer, peerServerId))
        }
        return list
    }

    private fun appendLogsToPeer(
        nextIndex: IntAdder,
        peer: MutableMap.MutableEntry<ServerId, RaftAddress>,
        peerServerId: ServerId
    ): Future<AppendReply> {
        val nextIndexValue = nextIndex.value
        val logIndexSnap = getNowLogIndex()
        val ar = if (nextIndexValue > logIndexSnap) {
            AppendRequest(
                currentTerm,
                logIndexSnap,
                stateMachine.getLastLogTerm(),
                commitIndex,
                listOf()
            )
        } else {
            // next rf0->1
            //从哪开始即左边界（包含）
            val slice = stateMachine.sliceLogs(nextIndexValue)
            val term = getTermByIndex(nextIndexValue - 1)
            AppendRequest(currentTerm, nextIndexValue - 1, term, commitIndex, slice)
        }

        return rpc.appendRequest(peer.value.SocketAddress(), ar).onSuccess {
            if (it.isSuccess) {
                matchIndexes[peerServerId]?.value = logIndexSnap
                nextIndexes[peerServerId]?.value = logIndexSnap + 1
                val oldCommitindex = commitIndex
                calCommitIndex()
                if (oldCommitindex != commitIndex) {
                    stateMachine.applyLog(commitIndex)
                }
            } else {
                if (it.term > currentTerm) {
                    becomeFollower(it.term)
                } else {
                    adjustNextIndex(peerServerId, it)
                }
            }
        }
    }

    //1 2 3  -> 2
    //1 2 -> 1
    private fun calCommitIndex() {
        val values = matchIndexes.values.toMutableList()
        values.add(IntAdder(stateMachine.getNowLogIndex()))
        val list = values.sortedBy(IntAdder::value)
        val index = if (list.size % 2 == 0) {
            list.size / 2 - 1
        } else {
            list.size / 2
        }
        commitIndex = list[index].value
    }


    private fun adjustNextIndex(serverId: ServerId, _reply: AppendReply) {
        //todo 完善一下
        nextIndexes[serverId]?.add(-1)
    }

    private fun startElection() {
        becomeCandidate()
        raftLog("start election")
        val buffer =
            RequestVote(currentTerm, stateMachine.getNowLogIndex(), stateMachine.getLastLogTerm())
        //法定人数为一半+1 而peer为不包含当前节点的集合 所以peer.size + 1为集群总数
        val quorum = (peers.size + 1) / 2 + 1
        //-1因为自己给自己投了一票
        val count = AtomicInteger(quorum - 1)
        val allowNextPromise = Promise.promise<Unit>()
        if (count.get() == 0) {
            allowNextPromise.complete()
        }
        val allowNext = AtomicBoolean(false)
        val allFutures = mutableListOf<Future<RequestVoteReply>>()
        for (address in peers) {
            val requestVoteReplyFuture = rpc.requestVote(address.value.SocketAddress(), buffer)
                .onSuccess {
                    val raft = this
                    if (it.isVoteGranted) {
                        raftLog("get vote from ${address.key}, count: ${count.get()} allowNex: ${allowNext}}")
                        if (count.decrementAndGet() == 0 && allowNext.compareAndSet(false, true)) {
                            allowNextPromise.complete()
                        }
                        return@onSuccess
                    }
                    if (it.term > currentTerm) {
                        becomeFollower(it.term)
                    }
                }

            allFutures += requestVoteReplyFuture
        }
        allowNextPromise.future()
            .onComplete {
                raftLog("allow to next, start checking status")
                val raft = this
                if (status == RaftStatus.candidate) {
                    raftLog("${me} become leader")
                    becomeLead()
                }
            }
    }

    fun getTermByIndex(index: Int) = stateMachine.getTermByIndex(index)

    fun insertLogs(prevIndex: Int, logs: List<Log>) = stateMachine.insertLogs(prevIndex, logs)

    fun getNowLogIndex(): Int {
        return stateMachine.getNowLogIndex()
    }

    fun getLastLogTerm(): Int {
        return stateMachine.getLastLogTerm()
    }

    fun close() {
        singleThreadVertx.close()
    }


    fun raftLog(msg: String) {
        if (!enablePrintInfo) {
            return
        }
        println("[${LocalDateTime.now()} serverId:$me term:$currentTerm index = ${stateMachine.getNowLogIndex()} status:${status} voteFor: ${votedFor}]: message:$msg")
    }


    /**
     * 外部调用的一个接口所以要确保线程安全
     */
    @NonBlocking
    @SwitchThread(Raft::class)
    fun addLog(command: Command, promise: Promise<Unit>) {
        if (leadId != me) {
            promise.fail(
                NotLeaderException(
                    "not leader!",
                    peers[leadId]
                )
            )
            return
        }
        stateMachine.addLog(command, promise::complete)
    }

    @NonBlocking
    @SwitchThread(Raft::class)
    fun addLog(command: Command) {
        val promise = Promise.promise<Unit>()
        addLog(command, promise)
    }

    fun addServer(request: AddServerRequest, promise: Promise<Map<ServerId, RaftAddress>>) {
        if (leadId != me) {
            promise.fail(
                NotLeaderException(
                    "not leader!",
                    peers[leadId]
                )
            )
            return
        }
        val applyConfigPromise = Promise.promise<Unit>()
        applyConfigPromise.future()
            .onComplete {
                val res = peers.toMutableMap()
                res.remove(request.serverId)
                promise.complete(res)
            }
        val serverConfigChangeCommand = ServerConfigChangeCommand.create(request)
        context.runOnContext {
            addLog(serverConfigChangeCommand, applyConfigPromise)
        }
    }


    @NonBlocking
    @SwitchThread(Raft::class)
    fun lineRead(key: ByteArray, promise: Promise<Buffer>) {
        if (leadId != me) {
            promise.fail(
                NotLeaderException(
                    "not leader!",
                    peers[leadId]
                )
            )
            return
        }
        context.runOnContext {
            val readIndex = commitIndex
            val waiters = broadcastLog()
            val downLatch = CountDownLatch(waiters.size / 2)
            waiters.forEach { f ->
                f.onComplete {
                    downLatch.countDown()
                }
            }
            CoroutineScope(context.dispatcher() as CoroutineContext).launch {
                downLatch.wait()
                if (status != RaftStatus.lead) {
                    promise.fail(
                        NotLeaderException(
                            "not leader!",
                           peers[leadId]
                        )
                    )
                    return@launch
                }
                val readAction = if (key.isEmpty()) {
                    { promise.complete(SimpleKVStateMachineCodec.encode(stateMachine.db)) }
                } else {
                    { promise.complete(wrap(stateMachine.getDirect(key))) }
                }
                if (readIndex <= lastApplied) {
                    readAction()
                } else {
                    stateMachine.applyEventWaitQueue.offer(readIndex to readAction)
                }

            }
        }
        return
    }

    @SwitchThread(Raft::class)
    fun raftSnap(fn: (RaftSnap) -> Unit) {
        context.runOnContext {
            val currentState =
                RaftState(currentTerm, votedFor, status, commitIndex, lastApplied, leadId)
            val raftSnap = RaftSnap(
                nextIndexes.mapValues { it.value.value },
                matchIndexes.mapValues { it.value.value },
                peers.toMap(),
                currentState
            )
            fn(raftSnap)
        }
    }

}