package top.dreamlike.raft.client

data class DataResult<T>(val hasError: Boolean, val value: T, val errorMessage: String = "")
