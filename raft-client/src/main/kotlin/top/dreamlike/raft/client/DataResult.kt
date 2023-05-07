package top.dreamlike.raft.client

import io.vertx.core.buffer.Buffer

data class DataResult(val hasError :Boolean, val value : Buffer, val errorMessage:String = "")
