package top.dreamlike.KV

import io.vertx.core.Future
import io.vertx.core.Vertx
import top.dreamlike.raft.Log
import top.dreamlike.raft.Raft
import top.dreamlike.raft.ServerId
import top.dreamlike.util.removeAll
import java.nio.channels.FileChannel
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.util.concurrent.Executors

class KVStateMachine(private val vertx: Vertx, private val rf: Raft) {
    //这里的logBase指的是已经被应用到状态机上面的最小logIndex
    //此index之后的log是无空洞的连续log
    var logs = mutableListOf<Log>()
    private val logBase = 0
    private val termBase = 0
    private val logDispatcher = LogDispatcher(rf.me)

    /**
     * 在 [top.dreamlike.raft.Raft.start] 中调用
     * 所以其中跑在EventLoop中
     */
    fun init(): Future<Unit> {
        vertx.setTimer(5000) { logDispatcher.sync() }
        return Future.succeededFuture()
    }


    //10 - 5 = 5
    //从1开始
    fun getNowLogIndex(): Int {
        return logs.size + logBase
    }

    fun getLastLogTerm(): Int {
        return if (logs.isEmpty()) termBase else logs.last().term
    }

    fun insertLogs(prevIndex: Int, logs: List<Log>) {
        if (prevIndex > getNowLogIndex() || prevIndex < logBase) throw IllegalArgumentException("插入了高于当前index的日志")
        this.logs.removeAll(prevIndex - logBase)
        this.logs.addAll(logs)
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

    fun addLog(command: Command) {
        vertx.runOnContext {
            logs.add(Log(getNowLogIndex() + 1, rf.currentTerm, command.toByteArray()))
        }
    }

    class LogDispatcher(serverId: ServerId) {
        private val executor = Executors.newSingleThreadExecutor { r ->
            Thread(r, "raft-$serverId-log-Thread")
        }
        val logFile =
            FileChannel.open(Path.of("raft-${serverId}-log"), StandardOpenOption.APPEND, StandardOpenOption.CREATE)

        fun appendLog(log: Log, afterWriteToFile: () -> Unit) {
            executor.execute {
                log.writeToFile(logFile)
                afterWriteToFile()
            }
        }

        fun execute(command: Runnable) {
            executor.execute(command)
        }

        fun close() {
            executor.shutdown()
        }

        /**
         * 不阻塞当前线程
         */
        fun sync() {
            executor.execute {
                logFile.force(true)
            }
        }
    }
}