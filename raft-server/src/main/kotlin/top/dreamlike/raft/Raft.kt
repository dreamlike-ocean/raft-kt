package top.dreamlike.raft

import io.vertx.core.Future
import io.vertx.core.Promise
import io.vertx.core.Vertx
import io.vertx.core.buffer.Buffer
import io.vertx.core.net.SocketAddress
import io.vertx.kotlin.coroutines.dispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import top.dreamlike.KV.KVStateMachine
import top.dreamlike.base.KV.Command
import top.dreamlike.base.KV.NoopCommand
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


/**
 * Raft的核心类设计 包含论文要求的全部数据+工业实践下来的额外字段，比如说commitIndex之类的
 * 要求与RPC运行在单线程之中 以保护多个属性修改时的原子性
 */
class Raft(
    private val singleThreadVertx: Vertx,
    peer: Map<ServerId, SocketAddress>,
    private val raftPort: Int,
    val me: ServerId
) {

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

    val peers = mutableMapOf<ServerId, SocketAddress>().apply { putAll(peer) }

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


    /**
     * 注意这里设计有问题，直接用了对应的实现
     * 只用于特殊情况
     */

    /**
     * 回调跑在Raft实例绑定的EventLoop上面
     */
    @NonBlocking
    @SwitchThread(Raft::class)
    fun start(): Future<Unit> {
        //预先触发缺页中断
        raftLog("recover{voteFor ${votedFor}}")
        val promise = Promise.promise<Unit>()
        singleThreadVertx.runOnContext {
            stateMachine.init()
                .compose { rpcHandler.init(singleThreadVertx, raftPort) }
                .onSuccess { startTimeoutCheck() }
                .onSuccess(promise::complete)
                .onFailure(promise::fail)
        }
        return promise.future()
    }


    private fun startTimeoutCheck() {
        CoroutineScope(singleThreadVertx.dispatcher() as CoroutineContext).launch {
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
        CoroutineScope(singleThreadVertx.dispatcher() as CoroutineContext).launch {
            while (status == RaftStatus.lead) {
                broadcastLog()
                delay(heartBeat)
            }
        }

    }

    private fun broadcastLog(): MutableList<Future<AppendReply>> {
        val list = mutableListOf<Future<AppendReply>>()
        for (peer in peers) {
            val peerServerId = peer.key
            val nextIndex = nextIndexes[peerServerId]
            if (nextIndex == null) continue
            list.add(appendLogsToPeer(nextIndex, peer, peerServerId))
        }
        return list
    }

    private fun appendLogsToPeer(
        nextIndex: IntAdder,
        peer: MutableMap.MutableEntry<ServerId, SocketAddress>,
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

        return rpc.appendRequest(peer.value, ar).onSuccess {
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
                    adjustNextIndex(peerServerId)
                }
            }
        }
    }

    //1 2 3 | 4
    private fun calCommitIndex() {
        val list = matchIndexes.values.sortedBy(IntAdder::value)
        commitIndex = list[list.size / 2].value
    }


    private fun adjustNextIndex(serverId: ServerId) {
        //todo 完善一下
        nextIndexes[serverId]?.add(-1)
    }

    private fun startElection() {
        becomeCandidate()
        raftLog("start election")
        val buffer =
            RequestVote(currentTerm, stateMachine.getNowLogIndex(), stateMachine.getLastLogTerm())
        val count = AtomicInteger(peers.size / 2)
        val allowNextPromise = Promise.promise<Unit>()
        val allowNext = AtomicBoolean(false)
        val allFutures = mutableListOf<Future<RequestVoteReply>>()
        for (address in peers) {
            val requestVoteReplyFuture = rpc.requestVote(address.value, buffer)
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
        singleThreadVertx.runOnContext {
            val readIndex = commitIndex
            val waiters = broadcastLog()
            val downLatch = CountDownLatch(waiters.size / 2)
            waiters.forEach { f ->
                f.onComplete {
                    downLatch.countDown()
                }
            }
            CoroutineScope(singleThreadVertx.dispatcher() as CoroutineContext).launch {
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
                    stateMachine.queue.offer(readIndex to readAction)
                }

            }
        }
        return
    }

    @SwitchThread(Raft::class)
    fun raftSnap(fn: (RaftSnap) -> Unit) {
        singleThreadVertx.runOnContext {
            val currentState = RaftState(currentTerm, votedFor, status, commitIndex, lastApplied)
            val raftSnap = RaftSnap(
                nextIndexes.mapValues { it.value.value },
                matchIndexes.mapValues { it.value.value },
                peers.mapValues { RaftAddress(it.value.port(), it.value.host()) },
                currentState
            )
            fn(raftSnap)
        }
    }

}