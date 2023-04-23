package top.dreamlike.KV

import io.vertx.core.Future
import io.vertx.core.Promise
import io.vertx.core.Vertx
import io.vertx.core.buffer.impl.VertxByteBufAllocator
import top.dreamlike.raft.Log
import top.dreamlike.raft.Raft
import top.dreamlike.util.NonBlocking
import top.dreamlike.util.SwitchThread
import top.dreamlike.util.removeAll
import java.nio.channels.FileChannel
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.util.concurrent.Executors
import kotlin.concurrent.thread

/**
 * 它和raft共享同一个线程
 * 而其内部的file则是另外一个线程，而这个线程[logDispatcher]的任务顺序由raft驱动线程控制
 * 所以raft和statemachine的log视图最终和文件一致
 */
class KVStateMachine(private val vertx: Vertx, private val rf: Raft) {
    //这里的logBase指的是已经被应用到状态机上面的最小logIndex
    //此index之后的log是无空洞的连续log
    var logs = mutableListOf<Log>()
    private val logBase = 0
    private val termBase = 0
    private val logDispatcher = LogDispatcher(rf.me)

    val db = mutableMapOf<ByteArrayKey, ByteArray>()

    val queue = ArrayDeque<Triple<Int,ByteArray,Promise<ByteArray>>>()



    /**
     * 在 [top.dreamlike.raft.Raft.start] 中调用
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


    fun get(key: ByteArray):ByteArray?{
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
                else -> {}
            }
            rf.lastApplied ++
            while (!queue.isEmpty() && rf.lastApplied >= queue.firstOrNull()!!.first){
                var (_, key, promise) = queue.removeFirst()
                promise.complete(get(key))
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

    @NonBlocking
    @SwitchThread(Raft::class)
    fun addLog(command: Command) {
        vertx.runOnContext {
            val log = Log(getNowLogIndex() + 1, rf.currentTerm, command.toByteArray())
            logs.add(log)
            logDispatcher.appendLogs(listOf(log))
        }
    }





    inner class LogDispatcher(private val logFileName: String) {
        private val executor = Executors.newSingleThreadExecutor { r ->
            Thread(r, "raft-$logFileName-log-Thread")
        }

        init {
            Runtime.getRuntime().addShutdownHook(thread(start = false, name = "LogDispatcher-close") { this.close() })
        }

        val logFile: FileChannel =
            FileChannel.open(Path.of(logFileName), StandardOpenOption.APPEND, StandardOpenOption.CREATE)

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
    }
}