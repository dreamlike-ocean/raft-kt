package top.dreamlike.KV

import io.vertx.core.Future
import io.vertx.core.Vertx
import top.dreamlike.raft.Log
import top.dreamlike.raft.Raft
import top.dreamlike.util.removeAll

class KVStateMachine(private val vertx: Vertx, private val rf: Raft) {
    //这里的logBase指的是已经被应用到状态机上面的最小logIndex
    //此index之后的log是无空洞的连续log
    private val logBase = 0
    private val termBase = 0

    fun init(): Future<Unit> {
        return Future.succeededFuture()
    }


    //10 - 5 = 5
    //从1开始
    fun getNowLogIndex(): Int {
        return rf.logs.size + logBase
    }

    fun getLastLogTerm(): Int {
        return if (rf.logs.isEmpty()) termBase else rf.logs.last().term
    }

    fun insertLogs(prevIndex: Int, logs: List<Log>) {
        if (prevIndex > getNowLogIndex() || prevIndex < logBase) throw IllegalArgumentException("插入了高于当前index的日志")
        rf.logs.removeAll(prevIndex - logBase)
        rf.logs.addAll(logs)
    }

    fun getTermByIndex(index: Int): Int {
        if (index < 1) return 0
        val logIndex = index - logBase - 1
        return rf.logs[logIndex].term
    }


    fun sliceLogs(startIndex: Int): List<Log> {
        val inLogs = if (startIndex < 1) 0 else startIndex - logBase - 1
        return rf.logs.slice(inLogs until rf.logs.size)
    }
}