package top.dreamlike.KV

import io.vertx.core.Future
import io.vertx.core.Promise
import io.vertx.core.Vertx
import io.vertx.core.buffer.impl.VertxByteBufAllocator
import top.dreamlike.base.KV.ByteArrayKey
import top.dreamlike.base.KV.Command
import top.dreamlike.base.KV.DelCommand
import top.dreamlike.base.KV.NoopCommand
import top.dreamlike.base.KV.ServerConfigChangeCommand
import top.dreamlike.base.KV.SetCommand
import top.dreamlike.base.util.IntAdder
import top.dreamlike.base.util.NonBlocking
import top.dreamlike.base.util.SwitchThread
import top.dreamlike.base.util.removeAll
import top.dreamlike.raft.Log
import top.dreamlike.raft.Log.Companion.mergeLogs
import top.dreamlike.raft.Raft
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.util.PriorityQueue
import java.util.concurrent.Executors
import kotlin.concurrent.thread

/**
 * 它和raft共享同一个线程
 * 而其内部的file则是另外一个线程，而这个线程[logDispatcher]的任务顺序由raft驱动线程控制
 * 所以raft和statemachine的log视图最终和文件一致
 */
private typealias LazyTask = () -> Unit
private typealias AfterComplete = () -> Unit

class KVStateMachine(private val vertx: Vertx, private val rf: Raft) {
    //这里的logBase指的是已经被应用到状态机上面的最小logIndex
    //此index之后的log是无空洞的连续log
    var logs = mutableListOf<Log>()
    private val logBase = 0
    private val termBase = 0
    private val logDispatcher = LogDispatcher(rf.me)
    private val serverChangeWaitQueue = ArrayDeque<LazyTask>()

    val db = mutableMapOf<ByteArrayKey, ByteArray>()

    /**
     * 等待需要被apply的index , key , value回调
     */
    val applyEventWaitQueue =
        PriorityQueue<Pair<Int, () -> Unit>>(Comparator.comparingInt { it.first })




    /**
     * 在 [top.dreamlike.raft.Raft.startRaft] 中调用
     * 所以其中跑在EventLoop中
     */
    fun init(): Future<Unit> {

        val promise = Promise.promise<Unit>()
        vertx.setTimer(5000) { logDispatcher.sync() }
        logDispatcher.recoverLog {
            logs = it
            vertx.runOnContext { promise.complete() }
        }
        return promise.future()
    }


    fun getDirect(key: ByteArray):ByteArray?{
        return db[ByteArrayKey(key)]
    }


    /**
     * @param endIndex 右端点（包含）
     */
    fun applyLog(endIndex: Int) {
        val applyIndexInLogs = rf.lastApplied - logBase - 1
        val endIndexInLogs = endIndex - logBase - 1
        for (i in applyIndexInLogs + 1..endIndexInLogs) {
            when (val command = Command.transToCommand(logs[i].command)) {
                is NoopCommand -> {}
                is SetCommand -> db[ByteArrayKey(command.key)] = command.value
                is DelCommand -> db.remove(ByteArrayKey(command.key))
                //不做任何事情以防止被continue直接忽略而不走推进apply
                is ServerConfigChangeCommand -> {}
                else -> continue
            }
            rf.lastApplied++
            while (!applyEventWaitQueue.isEmpty() && rf.lastApplied >= applyEventWaitQueue.firstOrNull()!!.first) {
                val (_, callback) = applyEventWaitQueue.poll()
                callback()
            }
        }

    }

    //10 - 5 = 5
    //从1开始
    fun getNowLogIndex(): Int {
        return logs.size + logBase
    }

    fun getLastLogTerm(): Int {
        return if (logs.isEmpty()) termBase else logs.last().term
    }

    /**
     * 作为接收者的日志
     */
    @NonBlocking
    fun insertLogs(prevIndex: Int, logs: List<Log>) {
        if (prevIndex > getNowLogIndex() || prevIndex < logBase) throw IllegalArgumentException("插入了高于当前index的日志")
        var logNeedRemove = this.logs.removeAll(prevIndex - logBase)
        val decreaseSize = logNeedRemove.sumOf(Log::size)
        //raft实际上是连续的index，
        // 所以修补日志的时候，对于已经持久化的log可以直接利用内存中的log的删除信息去truncate日志文件 再append就行了，
        // 由于raft的特性commit的log不会被删除
        if (decreaseSize != 0) logDispatcher.decreaseSize(decreaseSize)
        this.logs.addAll(logs)
        logDispatcher.appendLogs(logs)

        //这里特殊直接就apply
        logs.forEach {
            if (ServerConfigChangeCommand.isServerConfigChangeCommand(it.command)) {
                handleServerConfigChangeCommand(
                    ServerConfigChangeCommand(it.command),
                    {},
                    getNowLogIndex()
                )
            }
        }

    }

    fun getTermByIndex(index: Int): Int {
        if (index < 1) return 0
        val logIndex = index - logBase - 1
        return logs[logIndex].term
    }


    fun sliceLogs(startIndex: Int): List<Log> {
        val inLogs = if (startIndex < 1) 0 else startIndex - logBase - 1
        return logs.slice(inLogs until logs.size)
    }

    /**
     * leader状态时client请求附加日志
     */
    @NonBlocking
    @SwitchThread(Raft::class)
    fun addLog(command: Command, callback: () -> Unit) {
        vertx.runOnContext {
            val index = getNowLogIndex() + 1
            val log = Log(index, rf.currentTerm, command.toByteArray())
            logs.add(log)
            logDispatcher.appendLogs(listOf(log))
            if (command !is ServerConfigChangeCommand) {
                applyEventWaitQueue.offer(index to callback)
            } else {
                //这里特殊直接就apply
                handleServerConfigChangeCommand(command, callback, index)
            }
        }
    }

    private fun handleServerConfigChangeCommand(
        command: ServerConfigChangeCommand,
        callback: () -> Unit,
        currentIndex: Int
    ) {
        if (command.serverInfo.serverId == rf.me) {
            return
        }
        serverChangeWaitQueue.add {
            val serverId = command.serverInfo.serverId
            rf.peers[serverId] = command.serverInfo.raftAddress
            rf.raftLog("apply server add,new peer is ${command.serverInfo.raftAddress}")
            rf.nextIndexes[serverId] = IntAdder(getNowLogIndex() + 1)
            rf.matchIndexes[serverId] = IntAdder(0)
            applyEventWaitQueue.offer(currentIndex to {
                serverChangeWaitQueue.removeFirst()
                serverChangeWaitQueue.firstOrNull()?.invoke()
            })
            callback()
        }
        if (serverChangeWaitQueue.size == 1) {
            val task = serverChangeWaitQueue.first()
            task()
        }
    }


    inner class LogDispatcher(private val logFileName: String) {
        private val executor = Executors.newSingleThreadExecutor { r ->
            Thread(r, "raft-$logFileName-log-Thread")
        }

        init {
            Runtime.getRuntime().addShutdownHook(
                thread(
                    start = false,
                    name = "LogDispatcher-close"
                ) { this.close() })
        }

        val logFile: FileChannel =
            FileChannel.open(
                Path.of(logFileName),
                StandardOpenOption.APPEND,
                StandardOpenOption.CREATE,
                StandardOpenOption.SYNC
            )

        fun appendLog(log: Log, afterWriteToFile: () -> Unit) {
            executor.execute {
                log.writeToFile(logFile)
                afterWriteToFile()
            }
        }


        @NonBlocking
        @SwitchThread(LogDispatcher::class)
        fun recoverLog(callback: (MutableList<Log>) -> Unit) {
            executor.execute {
                val entries = mutableListOf<Log>()
                val buf = VertxByteBufAllocator.POOLED_ALLOCATOR.buffer()
                var logFile = FileChannel.open(Path.of(logFileName), StandardOpenOption.READ)
                logFile.run {
                    buf.writeBytes(logFile, 0L, logFile.size().toInt())
                    while (buf.readableBytes() != 0) {
                        val index = buf.readInt()
                        val term = buf.readInt()
                        val length = buf.readInt()
                        val command = ByteArray(length)
                        buf.readBytes(command)
                        entries.add(Log(index, term, command))
                    }
                    callback(entries)
                }
            }
        }

        fun execute(command: Runnable) {
            executor.execute(command)
        }

        fun close() {
            logFile.force(true)
            executor.shutdown()
        }

        @NonBlocking
        @SwitchThread(LogDispatcher::class)
        fun decreaseSize(size: Int) {
            executor.execute {
                logFile.truncate(logFile.size() - size)
            }
        }

        /**
         * 不阻塞当前线程
         */
        @NonBlocking
        @SwitchThread(LogDispatcher::class)
        fun sync() {
            executor.execute {
                rf.metaInfo.force()
                logFile.force(true)
            }
        }

        @NonBlocking
        @SwitchThread(LogDispatcher::class)
        fun appendLogs(log: List<Log>) {
            executor.execute {
                log.forEach {
                    it.writeToFile(logFile)
                }
            }
        }

        @NonBlocking
        @SwitchThread(LogDispatcher::class)
        fun appendLogs(logs: List<Log>, allowMerge: Boolean = rf.peers.size >= 4) {
            executor.execute {
                if (allowMerge) {
                    // LogFile的Open参数
                    // StandardOpenOption.APPEND, StandardOpenOption.CREATE, StandardOpenOption.SYNC
                    // )
                    val byteBuffer = logs.mergeLogs()
                    enqueueLogQueue(byteBuffer)
                } else {
                    logs.forEach {
                        it.writeToFile(logFile)
                    }
                }
            }
        }

        fun enqueueLogQueue(buffer: ByteBuffer) {

        }
    }
}